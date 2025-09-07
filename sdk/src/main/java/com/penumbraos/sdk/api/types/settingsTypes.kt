package com.penumbraos.sdk.api.types

import android.os.ParcelFileDescriptor

data class HttpRequest(
    val path: String,
    val method: String,
    val pathParams: Map<String, String>,
    val headers: Map<String, String>,
    val queryParams: Map<String, String>,
    val body: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HttpRequest

        if (path != other.path) return false
        if (method != other.method) return false
        if (pathParams != other.pathParams) return false
        if (headers != other.headers) return false
        if (queryParams != other.queryParams) return false
        if (!body.contentEquals(other.body)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + method.hashCode()
        result = 31 * result + pathParams.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + queryParams.hashCode()
        result = 31 * result + (body?.contentHashCode() ?: 0)
        return result
    }
}

data class HttpResponse(
    val statusCode: Int = 200,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val file: ParcelFileDescriptor? = null,
    val contentType: String = "application/json"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HttpResponse

        if (statusCode != other.statusCode) return false
        if (headers != other.headers) return false
        if (!body.contentEquals(other.body)) return false
        if (contentType != other.contentType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + headers.hashCode()
        result = 31 * result + (body?.contentHashCode() ?: 0)
        result = 31 * result + contentType.hashCode()
        return result
    }
}

interface HttpEndpointHandler {
    suspend fun handleRequest(request: HttpRequest): HttpResponse
}
