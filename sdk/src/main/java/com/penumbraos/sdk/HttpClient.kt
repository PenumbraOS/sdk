package com.penumbraos.sdk

import com.penumbraos.bridge.IHttpCallback
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class HttpClient(private val sdk: PenumbraSDK) {
    private val pendingRequests = ConcurrentHashMap<String, IHttpCallback>()

    suspend fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): Response = suspendCoroutine { continuation ->
        val requestId = UUID.randomUUID().toString()
        val callback = object : IHttpCallback.Stub() {
            override fun onSuccess(statusCode: Int, headers: Map<String, List<String>>, body: String) {
                pendingRequests.remove(requestId)
                continuation.resume(Response(statusCode, headers, body))
            }

            override fun onError(error: String) {
                pendingRequests.remove(requestId)
                continuation.resumeWithException(RuntimeException(error))
            }
        }

        pendingRequests[requestId] = callback
        sdk.getService()?.httpRequest(requestId, url, method, headers, body, callback)
            ?: continuation.resumeWithException(RuntimeException("Not connected to service"))
    }

    data class Response(
        val statusCode: Int,
        val headers: Map<String, List<String>>,
        val body: String
    )
}