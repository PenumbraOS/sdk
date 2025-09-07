package com.penumbraos.bridge_settings.server

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.penumbraos.bridge.callback.IHttpEndpointCallback
import com.penumbraos.bridge.callback.IHttpResponseCallback
import com.penumbraos.bridge_settings.SettingsWebServer
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import kotlin.coroutines.resume

data class EndpointRequest(
    val path: String,
    val method: String,
    val headers: Map<String, String>,
    val queryParams: Map<String, String>,
    val pathParams: Map<String, String>,
    val body: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EndpointRequest

        if (path != other.path) return false
        if (method != other.method) return false
        if (headers != other.headers) return false
        if (queryParams != other.queryParams) return false
        if (pathParams != other.pathParams) return false
        if (!body.contentEquals(other.body)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + method.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + queryParams.hashCode()
        result = 31 * result + pathParams.hashCode()
        result = 31 * result + (body?.contentHashCode() ?: 0)
        return result
    }
}

data class EndpointResponse(
    val statusCode: Int = 200,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray?,
    val contentType: String = "application/json"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EndpointResponse

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
                    body: ByteArray?,
                    file: ParcelFileDescriptor?,
                    contentType: String?
                ) {
                    val headers = (headers?.mapKeys { it.key.toString() }
                        ?.mapValues { it.value.toString() } ?: emptyMap()).toMutableMap()

                    // Disable CORS
                    headers["Access-Control-Allow-Origin"] = "*"

                    if (file != null) {
                        try {
                            Os.lseek(
                                file.fileDescriptor,
                                0,
                                OsConstants.SEEK_SET
                            )
                            val fileBytes =
                                FileInputStream(file.fileDescriptor)
                            val byteArrayOutputStream = ByteArrayOutputStream()
                            val buffer = ByteArray(4096)
                            var bytesRead: Int
                            while (fileBytes.read(buffer).also { bytesRead = it } != -1) {
                                byteArrayOutputStream.write(buffer, 0, bytesRead)
                            }

                            val response = EndpointResponse(
                                statusCode = statusCode,
                                headers = headers,
                                body = byteArrayOutputStream.toByteArray(),
                                contentType = contentType ?: "application/json"
                            )
                            continuation.resume(response)
                        } catch (e: Exception) {
                            sendError(continuation, e.message ?: "Unknown error")
                        }
                    } else {
                        val response = EndpointResponse(
                            statusCode = statusCode,
                            headers = headers,
                            body = body,
                            contentType = contentType ?: "application/json"
                        )
                        continuation.resume(response)
                    }
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
                sendError(continuation, e.message ?: "Unknown error")
            }
        }
    }
}

fun sendError(continuation: CancellableContinuation<EndpointResponse>, message: String) {
    val errorResponse = EndpointResponse(
        statusCode = 500,
        body = "{\"error\": \"Internal server error: ${message}\"}".toByteArray(),
        contentType = "application/json"
    )
    continuation.resume(errorResponse)
}
