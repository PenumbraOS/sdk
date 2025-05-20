package com.penumbraos.sdk.api

import com.penumbraos.sdk.PenumbraClient

class WebSocketClient(private val sdk: PenumbraClient) {
//    private val activeSessions = ConcurrentHashMap<String, IMyFriendlyAPI.WebSocketMessageCallback>()
//
//    suspend fun connect(
//        url: String,
//        headers: Map<String, String> = emptyMap()
//    ): WebSocket = suspendCoroutine { continuation ->
//        val sessionId = UUID.randomUUID().toString()
//        val callback = object : IMyFriendlyAPI.WebSocketMessageCallback.Stub() {
//            override fun onMessage(message: String) {
//                activeSessions[sessionId]?.onMessage(message)
//            }
//
//            override fun onClose(code: Int, reason: String) {
//                activeSessions.remove(sessionId)?.onClose(code, reason)
//            }
//
//            override fun onError(error: String) {
//                activeSessions.remove(sessionId)?.onError(error)
//                continuation.resumeWithException(RuntimeException(error))
//            }
//        }
//
//        activeSessions[sessionId] = callback
//        sdk.getService()?.connectWebSocket(sessionId, url, headers, callback)
//            ?: continuation.resumeWithException(RuntimeException("Not connected to service"))
//
//        continuation.resume(WebSocket(sessionId, this))
//    }
//
//    internal fun sendMessage(sessionId: String, message: String): Boolean {
//        return sdk.getService()?.sendWebSocketMessage(sessionId, message) ?: false
//    }
//
//    internal fun close(sessionId: String) {
//        sdk.getService()?.closeWebSocket(sessionId)
//        activeSessions.remove(sessionId)
//    }
//
//    class WebSocket(
//        private val sessionId: String,
//        private val client: WebSocketClient
//    ) {
//        fun send(message: String): Boolean = client.sendMessage(sessionId, message)
//        fun close() = client.close(sessionId)
//    }
}