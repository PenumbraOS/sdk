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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class PrivClient(val scope: CoroutineScope, val delegate: ICallbackDelegate) {
    private var socket: Socket? = null
    private var output: DataOutputStream? = null
    private var input: DataInputStream? = null
    private val isConnected = AtomicBoolean(false)
    private val messageChannel = Channel<MessageLite>(Channel.UNLIMITED)

    private val httpCallbacks = ConcurrentHashMap<String, IHttpCallback>()

    suspend fun connect(host: String, port: Int) {
        withContext(Dispatchers.IO) {
            socket = Socket().also {
                output = DataOutputStream(it.getOutputStream())
                input = DataInputStream(it.getInputStream())
                isConnected.set(true)
                Log.d(TAG, "Connected to TCP server")
            }
            socket?.connect(InetSocketAddress(host, port))
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
        if (!isConnected.get()) throw IOException("Not connected")
        try {
            messageChannel.send(message)
        } catch (e: ClosedSendChannelException) {
            throw IOException("Channel closed", e)
        }
    }

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
                val length = input?.readInt() ?: break
                val buffer = ByteArray(length)
                input?.readFully(buffer)

                val message = ServerToClientMessage.parseFrom(buffer)
                try {
                    delegate.callback(message)
                } catch (e: Exception) {
                    Log.e(TAG, "Callback failed with message type ${message.payloadCase}, error:" , e)
                    try {
                        delegate.genericError(message.origin.id, "Callback failed with message type ${message.payloadCase}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Sending generic error failed. Terminating:" , e)
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