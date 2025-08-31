package com.penumbraos.sdk.api.types

data class HttpRequest(
    val path: String,
    val method: String,
    val pathParams: Map<String, String>,
    val headers: Map<String, String>,
    val queryParams: Map<String, String>,
    val body: String?
)

data class HttpResponse(
    val statusCode: Int = 200,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    val contentType: String = "application/json"
)

interface HttpEndpointHandler {
    suspend fun handleRequest(request: HttpRequest): HttpResponse
}
