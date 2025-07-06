package com.penumbraos.sdk.http.okhttp

import com.penumbraos.sdk.PenumbraClient
import com.penumbraos.sdk.internal.HttpRequest
import com.penumbraos.sdk.internal.HttpStreamResponse
import com.penumbraos.sdk.internal.PenumbraHttpInterceptor
import com.penumbraos.sdk.internal.getReasonPhrase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import java.io.IOException

/**
 * TODO: This is untested
 */
class HttpInterceptor(private val penumbraClient: PenumbraClient) : Interceptor {

    private val coreInterceptor = PenumbraHttpInterceptor(penumbraClient)
    private val scope = CoroutineScope(Dispatchers.IO)

    @OptIn(DelicateCoroutinesApi::class)
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        return try {
            val httpRequest = HttpRequest(
                url = request.url.toString(),
                method = request.method,
                headers = request.headers.toMap(),
                body = request.body?.let { body ->
                    val buffer = Buffer()
                    body.writeTo(buffer)
                    buffer.readUtf8()
                }
            )

            var statusCode = 200
            var headers = mapOf<String, String>()
            val dataChannel = Channel<ByteArray>(Channel.UNLIMITED)

            val job = scope.launch {
                try {
                    coreInterceptor.intercept(httpRequest).collect { response ->
                        when (response) {
                            is HttpStreamResponse.Headers -> {
                                statusCode = response.statusCode
                                headers = response.headers
                            }

                            is HttpStreamResponse.Data -> {
                                dataChannel.send(response.chunk.toByteArray())
                            }

                            is HttpStreamResponse.Complete -> {
                                dataChannel.close()
                            }

                            is HttpStreamResponse.Error -> {
                                dataChannel.close(IOException("Stream error: ${response.message}"))
                            }
                        }
                    }
                } catch (e: Exception) {
                    dataChannel.close(e)
                }
            }

            val streamingResponseBody = object : ResponseBody() {
                override fun contentType() = headers["Content-Type"]?.toMediaType()
                override fun contentLength() = headers["Content-Length"]?.toLongOrNull() ?: -1L

                override fun source(): BufferedSource {
                    val source = object : Source {
                        override fun read(sink: Buffer, byteCount: Long): Long {
                            return runBlocking {
                                try {
                                    val data = dataChannel.receive()
                                    sink.write(data)
                                    data.size.toLong()
                                } catch (e: Exception) {
                                    if (dataChannel.isClosedForReceive) {
                                        -1L // EOF
                                    } else {
                                        throw IOException("Stream read error", e)
                                    }
                                }
                            }
                        }

                        override fun timeout() = Timeout.NONE
                        override fun close() {
                            dataChannel.cancel()
                            job.cancel()
                        }
                    }

                    return source.buffer()
                }
            }

            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message(getReasonPhrase(statusCode))
                .apply {
                    headers.forEach { (name, value) ->
                        addHeader(name, value)
                    }
                }
                .body(streamingResponseBody)
                .build()

        } catch (e: Exception) {
            throw IOException("Penumbra tunnel request failed: ${e.message}", e)
        }
    }

    private fun Headers.toMap(): Map<String, String> {
        return names().associateWith { name ->
            values(name).joinToString(", ")
        }
    }
}