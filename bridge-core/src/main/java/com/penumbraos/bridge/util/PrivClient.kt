package com.penumbraos.bridge.util

import android.util.Log
import com.google.protobuf.MessageLite
import com.penumbraos.bridge.IHttpCallback
import com.penumbraos.bridge.TAG
import com.penumbraos.ipc.proxy.Ipc.ServerToClientMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class PrivClient(
    val scope: CoroutineScope,
    val delegate: ICallbackDelegate,
    val address: SocketAddress
) {
    private var socket: Socket? = null
    private var output: DataOutputStream? = null
    private var input: DataInputStream? = null
    private val isConnected = AtomicBoolean(false)
    private val messageChannel = Channel<MessageLite>(Channel.UNLIMITED)

    private val httpCallbacks = ConcurrentHashMap<String, IHttpCallback>()

    constructor(host: String, port: Int, scope: CoroutineScope, delegate: ICallbackDelegate) : this(
        scope,
        delegate,
        InetSocketAddress(host, port)
    )

    suspend fun connect() {
        withContext(Dispatchers.IO) {
            socket = Socket().also {
                it.connect(address)
                output = DataOutputStream(it.getOutputStream())
                input = DataInputStream(it.getInputStream())
                isConnected.set(true)
                Log.w(TAG, "Connected to TCP server")
            }
            startMessageLoop()
        }
    }

    fun disconnect() {
        isConnected.set(false)
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing socket", e)
        }
    }

    suspend fun sendMessage(message: MessageLite) {
        if (!isConnected.get()) {
            connect()
        }
        try {
            messageChannel.send(message)
        } catch (e: ClosedSendChannelException) {
            throw IOException("Channel closed", e)
        }
    }

    fun isConnected(): Boolean = isConnected.get()

    fun startMessageLoop() = scope.launch {
        launch {
            for (message in messageChannel) {
                try {
                    writeMessage(message, output!!)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send message", e)
                    disconnect()
                    break
                }
            }
        }

        while (isConnected.get()) {
            try {
                val bytes = ByteArray(4)
                val bytesRead = input?.read(bytes)
                if (bytesRead != 4) {
                    throw IOException("Could not read request length");
                }

                val length = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt()

                val buffer = ByteArray(length)
                input?.readFully(buffer)

                val message = ServerToClientMessage.parseFrom(buffer)
                try {
                    delegate.callback(message)
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Callback failed with message type ${message.payloadCase}, error:",
                        e
                    )
                    try {
                        delegate.genericError(
                            message.origin.id,
                            "Callback failed with message type ${message.payloadCase}"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Sending generic error failed. Terminating:", e)
                        disconnect()
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading message", e)
                disconnect()
                break
            }
        }
    }
}