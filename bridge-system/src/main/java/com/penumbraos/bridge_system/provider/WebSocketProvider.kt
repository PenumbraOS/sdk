package com.penumbraos.bridge_system.provider

import android.util.Log
import com.penumbraos.bridge.IWebSocketCallback
import com.penumbraos.bridge.IWebSocketProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.ConcurrentHashMap

class WebSocketProvider : IWebSocketProvider.Stub() {

    private val client = OkHttpClient()
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
                callback.onOpen(requestId, responseHeaders)
                webSockets[requestId] = webSocket
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                callback.onMessage(requestId, 1, text.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                callback.onMessage(requestId, 2, bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                callback.onClose(requestId)
                webSockets.remove(requestId)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                callback.onError(requestId, t.message ?: "Unknown error")
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