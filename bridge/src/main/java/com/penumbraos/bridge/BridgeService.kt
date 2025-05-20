package com.penumbraos.bridge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.penumbraos.bridge.util.PrivClient
import com.penumbraos.ipc.proxy.Ipc.ClientToServerMessage
import com.penumbraos.ipc.proxy.Ipc.RequestOrigin
import com.penumbraos.ipc.proxy.Ipc.HttpProxyRequest
import com.penumbraos.ipc.proxy.Ipc.HttpHeader
import kotlinx.coroutines.*
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

const val TAG = "BridgeService"

class BridgeService : Service() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private val httpCallbacks = ConcurrentHashMap<String, IHttpCallback>()
    private var client: PrivClient? = null

    private val binder = object : IBridge.Stub() {
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

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        client = PrivClient(serviceScope).also { client ->
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
        client?.disconnect()
    }

    fun asBinder(): IBinder = binder
}