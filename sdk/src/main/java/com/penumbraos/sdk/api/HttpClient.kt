package com.penumbraos.sdk.api

import com.penumbraos.bridge.IHttpCallback
import com.penumbraos.bridge.IHttpProvider
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

enum class HttpMethod(val value: String) {
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    DELETE("DELETE"),
    PATCH("PATCH"),
}

@Suppress("UNCHECKED_CAST")
class HttpClient(private val httpProvider: IHttpProvider) {
    private val pendingRequests = ConcurrentHashMap<String, IHttpCallback>()

    suspend fun request(
        url: String,
        method: HttpMethod,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): Response = suspendCoroutine { continuation ->
        val requestId = UUID.randomUUID().toString()
        var finalStatusCode = 500
        var finalHeaders = emptyMap<String, String>()
        var finalData = ByteArrayOutputStream()
        val callback = object : IHttpCallback.Stub() {
            override fun onHeaders(
                requestId: String,
                statusCode: Int,
                headers: Map<*, *>?
            ) {
                finalStatusCode = statusCode
                finalHeaders = headers as? Map<String, String>? ?: emptyMap()
            }

            override fun onData(requestId: String, chunk: ByteArray?) {
                if (chunk != null) {
                    finalData.write(chunk)
                }
            }

            override fun onComplete(requestId: String) {
                pendingRequests.remove(requestId)
                val body = finalData.toString("UTF-8")
                continuation.resume(Response(finalStatusCode, finalHeaders, body))
            }

            override fun onError(
                requestId: String,
                errorMessage: String?,
                errorCode: Int
            ) {
                pendingRequests.remove(requestId)
                continuation.resumeWithException(RuntimeException(errorMessage))
            }
        }

        pendingRequests[requestId] = callback
        httpProvider.makeHttpRequest(requestId, url, method.value, body, headers, callback)
    }

    data class Response(
        val statusCode: Int,
        val headers: Map<String, String>,
        val body: String
    )
}