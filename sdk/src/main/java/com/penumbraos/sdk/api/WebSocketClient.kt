package com.penumbraos.sdk.api

import com.penumbraos.bridge.IWebSocketCallback
import com.penumbraos.ipc.proxy.Ipc.WebSocketMessageType
import com.penumbraos.sdk.PenumbraClient
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

enum class MessageType(val value: Int) {
    TEXT(WebSocketMessageType.TEXT.number),
    BINARY(WebSocketMessageType.BINARY.number)
}

class WebSocketClient(private val sdk: PenumbraClient) {
    private val activeSessions = ConcurrentHashMap<String, WebSocketSession>()

    suspend fun connect(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): WebSocket = suspendCoroutine { continuation ->
        val sessionId = UUID.randomUUID().toString()
        val webSocket = WebSocket(sessionId, this)
        val session = WebSocketSession(sessionId, this)

        val callback = object : IWebSocketCallback.Stub() {
            override fun onOpen(requestId: String, headers: Map<*, *>?) {
                activeSessions[requestId]?.continuation?.resume(webSocket)
            }

            override fun onMessage(requestId: String, type: Int, data: ByteArray) {
                val messageType = when (type) {
                    WebSocketMessageType.BINARY.number -> MessageType.BINARY
                    else -> MessageType.TEXT
                }
                activeSessions[requestId]?.messageHandler?.invoke(messageType, data)
            }

            override fun onError(requestId: String, errorMessage: String) {
                activeSessions[requestId]?.continuation?.resumeWithException(
                    WebSocketException(errorMessage)
                )
                activeSessions.remove(requestId)
            }

            override fun onClose(requestId: String) {
                activeSessions[requestId]?.closeHandler?.invoke()
                activeSessions.remove(requestId)
            }
        }

        activeSessions[sessionId] = session.copy(
            continuation = continuation,
            callback = callback
        )

        try {
            sdk.getService().openWebSocket(sessionId, url, headers, callback)
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    internal fun sendMessage(sessionId: String, type: MessageType, data: ByteArray) {
        sdk.getService().sendWebSocketMessage(sessionId, type.value, data)
    }

    internal fun close(sessionId: String) {
        activeSessions.remove(sessionId)
        sdk.getService().closeWebSocket(sessionId)
    }

    private data class WebSocketSession(
        val sessionId: String,
        val client: WebSocketClient,
        var continuation: kotlin.coroutines.Continuation<WebSocket>? = null,
        var messageHandler: ((MessageType, ByteArray) -> Unit)? = null,
        var closeHandler: (() -> Unit)? = null,
        val callback: IWebSocketCallback? = null
    )

    class WebSocketException(message: String) : RuntimeException(message)

    class WebSocket(
        private val sessionId: String,
        private val client: WebSocketClient
    ) {
        suspend fun awaitOpen() = suspendCoroutine<WebSocket> { continuation ->
            client.activeSessions[sessionId]?.continuation = continuation
        }

        fun onMessage(handler: (type: MessageType, data: ByteArray) -> Unit) {
            client.activeSessions[sessionId]?.messageHandler = handler
        }

        fun onClose(handler: () -> Unit) {
            client.activeSessions[sessionId]?.closeHandler = handler
        }

        fun send(type: MessageType, data: ByteArray) = client.sendMessage(sessionId, type, data)

        fun close() = client.close(sessionId)
    }
}