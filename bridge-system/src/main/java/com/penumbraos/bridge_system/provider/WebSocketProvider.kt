package com.penumbraos.bridge_system.provider

import android.util.Log
import com.penumbraos.bridge.IWebSocketProvider
import com.penumbraos.bridge.callback.IWebSocketCallback
import com.penumbraos.bridge.external.safeCallback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "WebSocketProvider"

class WebSocketProvider(private val client: OkHttpClient) : IWebSocketProvider.Stub() {

    private val webSockets = ConcurrentHashMap<String, WebSocket>()

    override fun openWebSocket(
        requestId: String,
        url: String,
        headers: Map<*, *>,
        callback: IWebSocketCallback
    ) {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (key, value) ->
            requestBuilder.addHeader(key.toString(), value.toString())
        }

        client.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val responseHeaders =
                    response.headers.toMultimap().mapValues { it.value.joinToString() }
                safeCallback(TAG, {
                    callback.onOpen(requestId, responseHeaders)
                }, onDeadObject = { webSockets.remove(requestId) })
                webSockets[requestId] = webSocket
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                safeCallback(TAG, {
                    callback.onMessage(requestId, 0, text.toByteArray())
                }, onDeadObject = { webSockets.remove(requestId) })
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                safeCallback(TAG, {
                    callback.onMessage(requestId, 1, bytes.toByteArray())
                }, onDeadObject = { webSockets.remove(requestId) })
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                safeCallback(TAG, {
                    callback.onClose(requestId)
                })
                webSockets.remove(requestId)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                safeCallback(TAG, {
                    callback.onError(requestId, t.message ?: "Unknown error")
                })
                webSockets.remove(requestId)
            }
        })
    }

    override fun sendWebSocketMessage(requestId: String, type: Int, data: ByteArray) {
        val webSocket = webSockets[requestId]
        if (webSocket != null) {
            if (type == 0) {
                webSocket.send(String(data))
            } else {
                webSocket.send(ByteString.Companion.of(*data))
            }
        } else {
            Log.e("WebSocketProviderService", "WebSocket not found for requestId: $requestId")
        }
    }

    override fun closeWebSocket(requestId: String) {
        val webSocket = webSockets[requestId]
        webSocket?.close(1000, null)
    }
}