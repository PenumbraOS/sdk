package com.penumbraos.bridge_system.provider

import android.util.Log
import com.penumbraos.bridge.IHttpCallback
import com.penumbraos.bridge.IHttpProvider
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

private const val TAG = "HttpProvider"

class HttpProvider(private val client: OkHttpClient) : IHttpProvider.Stub() {
    
    override fun makeHttpRequest(
        requestId: String,
        url: String,
        method: String,
        body: String?,
        headers: Map<*, *>,
        callback: IHttpCallback
    ) {
        Log.w("HttpProvider", "Making HTTP request: $url")
        val requestBuilder = Request.Builder().url(url)

        headers.forEach { (key, value) ->
            requestBuilder.addHeader(key.toString(), value.toString())
        }

        val requestBody = body?.toRequestBody()
        requestBuilder.method(method, requestBody)

        client.newCall(requestBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                safeCallback(TAG) {
                    callback.onError(requestId, e.message ?: "Unknown error", -1)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                Log.w("HttpClient", "Received data")
                val responseHeaders =
                    response.headers.toMultimap().mapValues { it.value.joinToString() }

                if (!safeCallback(TAG) {
                        callback.onHeaders(
                            requestId,
                            response.code,
                            responseHeaders
                        )
                    }) {
                    return
                }

                val responseBody = response.body
                if (responseBody != null) {
                    val buffer = ByteArray(8192)
                    val inputStream = responseBody.byteStream()
                    var bytesRead = 0
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (!safeCallback(TAG) {
                                callback.onData(
                                    requestId,
                                    buffer.copyOf(bytesRead)
                                )
                            }) {
                            return
                        }
                    }
                }
                safeCallback(TAG) {
                    callback.onComplete(requestId)
                }
            }
        })
    }
}