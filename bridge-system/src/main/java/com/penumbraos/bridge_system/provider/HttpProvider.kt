package com.penumbraos.bridge_system.provider

import android.util.Log
import com.penumbraos.bridge.IHttpProvider
import com.penumbraos.bridge.callback.IHttpCallback
import com.penumbraos.bridge.external.safeCallback
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "HttpProvider"

class HttpProvider(private val client: OkHttpClient) : IHttpProvider.Stub() {

    private data class StreamingRequest(
        val url: String,
        val method: String,
        val headers: Map<*, *>,
        val callback: IHttpCallback,
        val bodyStream: ByteArrayOutputStream = ByteArrayOutputStream()
    )

    private val streamingRequests = ConcurrentHashMap<String, StreamingRequest>()

    override fun makeHttpRequest(
        requestId: String,
        url: String,
        method: String,
        body: String?,
        headers: Map<*, *>,
        callback: IHttpCallback
    ) {
        Log.w(TAG, "Making HTTP request: $url")
        val bodyBytes = body?.toByteArray(Charsets.UTF_8)
        executeHttpRequest(requestId, url, method, bodyBytes, headers, callback)
    }

    override fun startStreamingHttpRequest(
        requestId: String,
        url: String,
        method: String,
        headers: Map<*, *>,
        callback: IHttpCallback
    ) {
        Log.w(TAG, "Starting streaming HTTP request: $url")
        val streamingRequest = StreamingRequest(url, method, headers, callback)
        streamingRequests[requestId] = streamingRequest
    }

    override fun sendBodyChunk(requestId: String, chunk: ByteArray) {
        val streamingRequest = streamingRequests[requestId]
        if (streamingRequest != null) {
            streamingRequest.bodyStream.write(chunk)
        } else {
            Log.e(TAG, "No streaming request found for ID: $requestId")
        }
    }

    override fun endBodyStream(requestId: String) {
        val streamingRequest = streamingRequests.remove(requestId)
        if (streamingRequest != null) {
            val bodyBytes = streamingRequest.bodyStream.toByteArray()
            executeHttpRequest(
                requestId,
                streamingRequest.url,
                streamingRequest.method,
                bodyBytes,
                streamingRequest.headers,
                streamingRequest.callback
            )
        } else {
            Log.e(TAG, "No streaming request found for ID when ending stream: $requestId")
        }
    }

    private fun executeHttpRequest(
        requestId: String,
        url: String,
        method: String,
        bodyBytes: ByteArray?,
        headers: Map<*, *>,
        callback: IHttpCallback
    ) {
        val requestBuilder = Request.Builder().url(url)

        headers.forEach { (key, value) ->
            requestBuilder.addHeader(key.toString(), value.toString())
        }

        val requestBody = bodyBytes?.toRequestBody()
        requestBuilder.method(method, requestBody)

        client.newCall(requestBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                safeCallback(TAG, {
                    callback.onError(requestId, e.message ?: "Unknown error", -1)
                })
            }

            override fun onResponse(call: Call, response: Response) {
                Log.w(TAG, "Received response")
                val responseHeaders =
                    response.headers.toMultimap().mapValues { it.value.joinToString() }

                if (!safeCallback(TAG, {
                        callback.onHeaders(requestId, response.code, responseHeaders)
                    })) {
                    return
                }

                val responseBody = response.body
                if (responseBody != null) {
                    val buffer = ByteArray(8192)
                    val inputStream = responseBody.byteStream()
                    var bytesRead = 0
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (!safeCallback(TAG, {
                                callback.onData(requestId, buffer.copyOf(bytesRead))
                            })) {
                            return
                        }
                    }
                }
                safeCallback(TAG, {
                    callback.onComplete(requestId)
                })
            }
        })
    }
}