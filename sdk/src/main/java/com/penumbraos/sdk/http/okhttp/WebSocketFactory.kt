package com.penumbraos.sdk.http.okhttp

import com.penumbraos.sdk.PenumbraClient
import com.penumbraos.sdk.api.WebSocketClient
import com.penumbraos.sdk.api.WebSocketMessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class WebSocketFactory(private val penumbraClient: PenumbraClient) {

    fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
        val scope = CoroutineScope(Dispatchers.IO)
        val penumbraWebSocket =
            WebSocket(penumbraClient.websocket, request, listener)

        scope.launch {
            try {
                penumbraWebSocket.connect()
            } catch (e: Exception) {
                listener.onFailure(penumbraWebSocket, e, null)
            }
        }

        return penumbraWebSocket
    }
}

private class WebSocket(
    private val client: WebSocketClient,
    private val request: Request,
    private val listener: WebSocketListener,
) : WebSocket {

    private var sdkWebSocket: WebSocketClient.WebSocket? = null
    private var isConnected = false
    private var isClosed = false

    suspend fun connect() {
        val headers = request.headers.toMap()
        val websocket = client.connect(request.url.toString(), headers)

        sdkWebSocket = websocket

        websocket.onMessage { type, data ->
            when (type) {
                WebSocketMessageType.TEXT -> {
                    listener.onMessage(this, data.toString(Charsets.UTF_8))
                }

                WebSocketMessageType.BINARY -> {
                    listener.onMessage(this, ByteString.of(*data))
                }
            }
        }

        websocket.onClose {
            if (!isClosed) {
                isClosed = true
                listener.onClosed(this, 1000, "Connection closed")
            }
        }

        isConnected = true
        listener.onOpen(
            this, Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(101)
                .message("Switching Protocols")
                .build()
        )
    }

    override fun request(): Request = request

    override fun queueSize(): Long = 0

    override fun send(text: String): Boolean {
        return try {
            sdkWebSocket?.send(WebSocketMessageType.TEXT, text.toByteArray(Charsets.UTF_8))
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun send(bytes: ByteString): Boolean {
        return try {
            sdkWebSocket?.send(WebSocketMessageType.BINARY, bytes.toByteArray())
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun close(code: Int, reason: String?): Boolean {
        return if (!isClosed) {
            isClosed = true
            sdkWebSocket?.close()
            true
        } else {
            false
        }
    }

    override fun cancel() {
        close(1000, "Cancelled")
    }

    private fun Headers.toMap(): Map<String, String> {
        return names().associateWith { name ->
            values(name).joinToString(", ")
        }
    }
}