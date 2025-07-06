package com.penumbraos.sdk.api

import android.util.Log
import com.penumbraos.bridge.IHttpCallback
import com.penumbraos.bridge.IHttpProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

    fun requestStream(
        url: String,
        method: HttpMethod,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): Flow<StreamResponse> {
        Log.w("HttpClient", "Creating flow for: $url")
        Log.w("HttpClient", "Current pending requests: ${pendingRequests.size}")
        return callbackFlow {
            Log.w("HttpClient", "Started HTTP request: $url")
            val requestId = UUID.randomUUID().toString()
            Log.w("HttpClient", "Generated request ID: $requestId")

            val callback = object : IHttpCallback.Stub() {
                override fun onHeaders(
                    requestId: String,
                    statusCode: Int,
                    headers: Map<*, *>?
                ) {
                    val headersMap = headers as? Map<String, String>? ?: emptyMap()
                    trySendBlocking(StreamResponse.Headers(statusCode, headersMap))
                }

                override fun onData(requestId: String, chunk: ByteArray?) {
                    if (chunk != null) {
                        val data = String(chunk, Charsets.UTF_8)
                        trySendBlocking(StreamResponse.Data(data))
                    }
                }

                override fun onComplete(requestId: String) {
                    pendingRequests.remove(requestId)
                    trySendBlocking(StreamResponse.Complete)
                    close()
                }

                override fun onError(
                    requestId: String,
                    errorMessage: String?,
                    errorCode: Int
                ) {
                    pendingRequests.remove(requestId)
                    trySendBlocking(
                        StreamResponse.Error(
                            errorMessage ?: "Unknown error",
                            errorCode
                        )
                    )
                    close()
                }
            }

            pendingRequests[requestId] = callback
            Log.w("HttpClient", "Added request to pending map, size now: ${pendingRequests.size}")
            Log.w("HttpClient", "Making HTTP request: $url")
            httpProvider.makeHttpRequest(requestId, url, method.value, body, headers, callback)
            Log.w("HttpClient", "Called httpProvider.makeHttpRequest")

            awaitClose {
                Log.w("HttpClient", "Finished iterating over channel responses")
                Log.w("HttpClient", "Finished making HTTP request: $url")
            }
//
//            // Emit stream responses
//            Log.w("HttpClient", "Starting to iterate over channel responses")
//            for (response in channel) {
//                Log.w("HttpClient", "Emitting response: ${response::class.simpleName}")
//                emit(response)
//            }
//            Log.w("HttpClient", "Finished iterating over channel responses")
//            Log.w("HttpClient", "Finished making HTTP request: $url")
        }

    }

}

data class Response(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: String
)


sealed class StreamResponse {
    data class Headers(val statusCode: Int, val headers: Map<String, String>) : StreamResponse()
    data class Data(val chunk: String) : StreamResponse()
    object Complete : StreamResponse()
    data class Error(val message: String, val code: Int) : StreamResponse()
}
