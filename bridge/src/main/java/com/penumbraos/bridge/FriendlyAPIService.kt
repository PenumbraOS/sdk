package com.penumbraos.bridge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.google.protobuf.MessageLite
import com.penumbraos.bridge.IHttpCallback
import com.penumbraos.bridge.IMyFriendlyAPI
import com.penumbraos.ipc.proxy.Ipc.ClientToServerMessage
import com.penumbraos.ipc.proxy.Ipc.RequestOrigin
import com.penumbraos.ipc.proxy.Ipc.HttpProxyRequest
import com.penumbraos.ipc.proxy.Ipc.HttpHeader
import com.penumbraos.ipc.proxy.Ipc.ServerToClientMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class FriendlyAPIService : Service() {
    private val TAG = "FriendlyAPIService"
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private val httpCallbacks = ConcurrentHashMap<Int, IHttpCallback>()
    private var tcpClient: TcpClient? = null

    private val binder = object : IMyFriendlyAPI.Stub() {
        @Throws(RemoteException::class)
        override fun makeHttpRequest(
            requestId: Int,
            url: String,
            headers: Map<*, *>,
            callback: IHttpCallback
        ) {
            Log.d(TAG, "HTTP request $requestId for $url")
            httpCallbacks[requestId] = callback
            
            serviceScope.launch {
                try {
                    val client = tcpClient ?: throw IOException("TCP client not connected")
                    
                    val request = ClientToServerMessage.newBuilder()
                        .setOrigin(RequestOrigin.newBuilder().setId(requestId).build())
                        .setHttpRequest(
                            HttpProxyRequest.newBuilder()
                                .setUrl(url)
                                .setMethod("GET")
                                .addAllHeaders(headers.map { (k, v) ->
                                    HttpHeader.newBuilder()
                                        .setKey(k.toString())
                                        .setValue(v.toString())
                                        .build()
                                })
                                .build()
                        )
                        .build()
                    
                    client.sendMessage(request)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send request $requestId", e)
                    try {
                        callback.onError(requestId, e.message ?: "Unknown error", -1)
                    } catch (re: RemoteException) {
                        Log.e(TAG, "Failed to send error callback", re)
                    }
                    httpCallbacks.remove(requestId)
                }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        tcpClient = TcpClient().also { client ->
            serviceScope.launch {
                try {
                    client.connect("127.0.0.1", 12345)
                    client.startMessageLoop()
                } catch (e: Exception) {
                    Log.e(TAG, "TCP connection failed", e)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        serviceJob.cancel()
        tcpClient?.disconnect()
    }

    private inner class TcpClient {
        private var socket: Socket? = null
        private var output: DataOutputStream? = null
        private var input: DataInputStream? = null
        private val isConnected = AtomicBoolean(false)
        private val messageChannel = Channel<MessageLite>(Channel.UNLIMITED)

        suspend fun connect(host: String, port: Int) {
            withContext(Dispatchers.IO) {
                socket = Socket(host, port).also {
                    output = DataOutputStream(it.getOutputStream())
                    input = DataInputStream(it.getInputStream())
                    isConnected.set(true)
                    Log.d(TAG, "Connected to TCP server")
                }
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

        fun startMessageLoop() = serviceScope.launch {
            launch {
                for (message in messageChannel) {
                    try {
                        val bytes = message.toByteArray()
                        output?.writeInt(bytes.size)
                        output?.write(bytes)
                        output?.flush()
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
                    when (message.payloadCase) {
                        ServerToClientMessage.PayloadCase.HTTP_HEADERS -> {
                            val cb = httpCallbacks[message.origin.id]
                            cb?.onHeaders(
                                message.origin.id,
                                message.httpHeaders.statusCode,
                                message.httpHeaders.headersList.associate { h -> h.key to h.value }
                            )
                        }
                        ServerToClientMessage.PayloadCase.HTTP_BODY_CHUNK -> {
                            val cb = httpCallbacks[message.origin.id]
                            cb?.onData(message.origin.id, message.httpBodyChunk.chunk.toByteArray())
                        }
                        ServerToClientMessage.PayloadCase.HTTP_RESPONSE_COMPLETE -> {
                            val cb = httpCallbacks.remove(message.origin.id)
                            cb?.onComplete(message.origin.id)
                        }
                        ServerToClientMessage.PayloadCase.HTTP_ERROR -> {
                            val cb = httpCallbacks.remove(message.origin.id)
                            cb?.onError(
                                message.origin.id,
                                message.httpError.errorMessage,
                                message.httpError.errorCode
                            )
                        }
                        else -> Log.w(TAG, "Unknown message type")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading message", e)
                    disconnect()
                    break
                }
            }
        }
    }
}