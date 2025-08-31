package com.penumbraos.bridge_settings.server

import com.penumbraos.bridge.callback.IHttpEndpointCallback
import com.penumbraos.bridge.callback.IHttpResponseCallback
import com.penumbraos.bridge_settings.SettingsWebServer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class EndpointRequest(
    val path: String,
    val method: String,
    val headers: Map<String, String>,
    val queryParams: Map<String, String>,
    val pathParams: Map<String, String>,
    val body: String?
)

data class EndpointResponse(
    val statusCode: Int = 200,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    val contentType: String = "application/json"
)

interface EndpointCallback {
    suspend fun handle(request: EndpointRequest): EndpointResponse
}

interface EndpointProvider {
    fun registerEndpoints(server: SettingsWebServer)
    fun unregisterEndpoints(server: SettingsWebServer)
}

data class RegisteredEndpoint(
    val path: String,
    val method: String,
    val callback: EndpointCallback,
    val providerId: String
) {
    fun matchesPath(requestPath: String): Map<String, String>? {
        return PathParser.matchPath(this.path, requestPath)
    }
}

class AidlEndpointCallback(
    private val aidlCallback: IHttpEndpointCallback
) : EndpointCallback {
    override suspend fun handle(request: EndpointRequest): EndpointResponse {
        return suspendCancellableCoroutine { continuation ->
            val responseCallback = object : IHttpResponseCallback.Stub() {
                override fun sendResponse(
                    statusCode: Int,
                    headers: MutableMap<Any?, Any?>?,
                    body: String?,
                    contentType: String?
                ) {
                    val response = EndpointResponse(
                        statusCode = statusCode,
                        headers = headers?.mapKeys { it.key.toString() }
                            ?.mapValues { it.value.toString() } ?: emptyMap(),
                        body = body ?: "",
                        contentType = contentType ?: "application/json"
                    )
                    continuation.resume(response)
                }
            }

            try {
                aidlCallback.onHttpRequest(
                    request.path,
                    request.method,
                    request.pathParams,
                    request.headers,
                    request.queryParams,
                    request.body,
                    responseCallback
                )
            } catch (e: Exception) {
                val errorResponse = EndpointResponse(
                    statusCode = 500,
                    body = "{\"error\": \"Internal server error: ${e.message}\"}",
                    contentType = "application/json"
                )
                continuation.resume(errorResponse)
            }
        }
    }
}
