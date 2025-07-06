package com.penumbraos.sdk.internal

import com.penumbraos.sdk.PenumbraClient
import com.penumbraos.sdk.api.HttpMethod
import com.penumbraos.sdk.api.StreamResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Core HTTP interceptor logic that can be shared between OkHttp and Ktor implementations
 */
class PenumbraHttpInterceptor(private val penumbraClient: PenumbraClient) {

    /**
     * Intercepts and processes HTTP requests through the Penumbra tunnel.
     */
    fun intercept(request: HttpRequest): Flow<HttpStreamResponse> {
        return try {
            penumbraClient.http.requestStream(
                url = request.url,
                method = request.method.toPenumbraMethod(),
                headers = request.headers,
                body = request.body
            ).map { streamResponse: StreamResponse ->
                when (streamResponse) {
                    is StreamResponse.Headers -> {
                        HttpStreamResponse.Headers(
                            statusCode = streamResponse.statusCode,
                            headers = streamResponse.headers
                        )
                    }

                    is StreamResponse.Data -> {
                        HttpStreamResponse.Data(streamResponse.chunk)
                    }

                    is StreamResponse.Complete -> {
                        HttpStreamResponse.Complete
                    }

                    is StreamResponse.Error -> {
                        HttpStreamResponse.Error(streamResponse.message, streamResponse.code)
                    }
                }
            }
        } catch (e: Exception) {
            throw IOException("Penumbra tunnel request failed: ${e.message}", e)
        }
    }

    private fun String.toPenumbraMethod(): HttpMethod {
        return when (this.uppercase()) {
            "GET" -> HttpMethod.GET
            "POST" -> HttpMethod.POST
            "PUT" -> HttpMethod.PUT
            "DELETE" -> HttpMethod.DELETE
            "PATCH" -> HttpMethod.PATCH
            else -> HttpMethod.GET
        }
    }
}

/**
 * Generic HTTP request representation
 */
data class HttpRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: String?
)

/**
 * Generic HTTP response representation
 */
data class HttpResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: String
)

/**
 * Generic HTTP streaming response representation
 */
sealed class HttpStreamResponse {
    data class Headers(val statusCode: Int, val headers: Map<String, String>) : HttpStreamResponse()
    data class Data(val chunk: String) : HttpStreamResponse()
    object Complete : HttpStreamResponse()
    data class Error(val message: String, val code: Int) : HttpStreamResponse()
}

fun getReasonPhrase(code: Int): String {
    return when (code) {
        200 -> "OK"
        201 -> "Created"
        204 -> "No Content"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        500 -> "Internal Server Error"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        else -> "Unknown"
    }
}