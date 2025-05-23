package com.penumbraos.bridge

import android.os.RemoteException
import android.util.Log
import com.penumbraos.bridge.util.ICallbackDelegate
import com.penumbraos.bridge.util.PrivClient
import com.penumbraos.ipc.proxy.Ipc.ClientToServerMessage
import com.penumbraos.ipc.proxy.Ipc.HttpHeader
import com.penumbraos.ipc.proxy.Ipc.HttpProxyRequest
import com.penumbraos.ipc.proxy.Ipc.RequestOrigin
import com.penumbraos.ipc.proxy.Ipc.ServerToClientMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

const val TAG = "BridgeService"

class BridgeService : ICallbackDelegate {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val httpCallbacks = ConcurrentHashMap<String, IHttpCallback>()
    internal var client: PrivClient? = null

    constructor() {
        Log.w(TAG, "Service created")
        client = PrivClient("127.0.0.1", 1720, serviceScope, this)
        runBlocking {
            try {
                client?.connect()
            } catch (e: Exception) {
                Log.e(TAG, "TCP connection failed", e)
            }
        }
    }

    private val binder = object : IBridge.Stub() {
        @Throws(RemoteException::class)
        override fun pingBinder(): Boolean {
            return client?.isConnected() == true
        }

        @Throws(RemoteException::class)
        override fun makeHttpRequest(
            requestId: String,
            url: String,
            method: String,
            body: String?,
            headers: Map<*, *>,
            callback: IHttpCallback
        ) {
            Log.d(TAG, "HTTP request $requestId for $url")
            httpCallbacks[requestId] = callback

            serviceScope.launch {
                try {
                    val client = client ?: throw IOException("Privileged client not connected")

                    val request = ClientToServerMessage.newBuilder()
                        .setOrigin(RequestOrigin.newBuilder().setId(requestId).build())
                        .setHttpRequest(
                            HttpProxyRequest.newBuilder()
                                .setUrl(url)
                                .setMethod(method)
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

    fun asBinder(): IBridge = binder

    override fun callback(message: ServerToClientMessage) {
        when (message.payloadCase) {
            ServerToClientMessage.PayloadCase.HTTP_HEADERS -> {
                val callback = httpCallbacks[message.origin.id]
                callback?.onHeaders(
                    message.origin.id,
                    message.httpHeaders.statusCode,
                    message.httpHeaders.headersList.associate { h -> h.key to h.value }
                )
            }

            ServerToClientMessage.PayloadCase.HTTP_BODY_CHUNK -> {
                val callback = httpCallbacks[message.origin.id]
                callback?.onData(message.origin.id, message.httpBodyChunk.chunk.toByteArray())
            }

            ServerToClientMessage.PayloadCase.HTTP_RESPONSE_COMPLETE -> {
                val callback = httpCallbacks.remove(message.origin.id)
                callback?.onComplete(message.origin.id)
            }

            ServerToClientMessage.PayloadCase.HTTP_ERROR -> {
                val callback = httpCallbacks.remove(message.origin.id)
                callback?.onError(
                    message.origin.id,
                    message.httpError.errorMessage,
                    message.httpError.errorCode
                )
            }

            else -> Log.w(TAG, "Unknown message type")
        }
    }

    override fun genericError(requestId: String, errorMessage: String) {
        httpCallbacks.remove(requestId)
        Log.e(TAG, "Generic error: $errorMessage")
    }
}