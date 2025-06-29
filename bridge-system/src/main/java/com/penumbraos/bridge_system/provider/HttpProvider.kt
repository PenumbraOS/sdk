package com.penumbraos.bridge_system.provider

import com.penumbraos.bridge.IHttpCallback
import com.penumbraos.bridge.IHttpProvider
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class HttpProvider : IHttpProvider.Stub() {

    private val client = OkHttpClient()

    override fun makeHttpRequest(
        requestId: String,
        url: String,
        method: String,
        body: String?,
        headers: Map<*, *>,
        callback: IHttpCallback
    ) {
        val requestBuilder = Request.Builder().url(url)

        headers.forEach { (key, value) ->
            requestBuilder.addHeader(key.toString(), value.toString())
        }

        val requestBody = body?.toRequestBody()
        requestBuilder.method(method, requestBody)

        client.newCall(requestBuilder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(requestId, e.message ?: "Unknown error", -1)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseHeaders =
                    response.headers.toMultimap().mapValues { it.value.joinToString() }
                callback.onHeaders(requestId, response.code, responseHeaders)

                val responseBody = response.body
                if (responseBody != null) {
                    val buffer = ByteArray(8192)
                    val inputStream = responseBody.byteStream()
                    var bytesRead = 0
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        callback.onData(requestId, buffer.copyOf(bytesRead))
                    }
                }
                callback.onComplete(requestId)
            }
        })
    }
}