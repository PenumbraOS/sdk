package com.penumbraos.sdk.http.ktor

import android.util.Log
import com.penumbraos.sdk.PenumbraClient
import com.penumbraos.sdk.internal.HttpRequest
import com.penumbraos.sdk.internal.HttpStreamResponse
import com.penumbraos.sdk.internal.PenumbraHttpInterceptor
import com.penumbraos.sdk.internal.getReasonPhrase
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.util.AttributeKey
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.close
import io.ktor.utils.io.writeStringUtf8

/**
 * Ktor HTTP client plugin that routes requests through the PenumbraOS tunnel
 */
class HttpClientPlugin(private val penumbraClient: PenumbraClient) {

    private val coreInterceptor = PenumbraHttpInterceptor(penumbraClient)

    class Config {
        lateinit var penumbraClient: PenumbraClient
    }

    companion object : io.ktor.client.plugins.HttpClientPlugin<Config, HttpClientPlugin> {
        override val key: AttributeKey<HttpClientPlugin> =
            AttributeKey("PenumbraHttpClientPlugin")

        override fun prepare(block: Config.() -> Unit): HttpClientPlugin {
            val config = Config().apply(block)
            return HttpClientPlugin(config.penumbraClient)
        }

        @OptIn(InternalAPI::class)
        override fun install(plugin: HttpClientPlugin, scope: HttpClient) {
            Log.d("HttpClientPlugin", "Installing HttpClientPlugin Ktor")

            scope.plugin(HttpSend).intercept { request ->
                val httpRequest = HttpRequest(
                    url = request.url.toString(),
                    method = request.method.value,
                    headers = request.headers.entries().associate { (key, values) ->
                        key to values.joinToString(", ")
                    },
                    body = when (request.body) {
                        is TextContent -> (request.body as TextContent).text
                        else -> request.body.toString()
                    }
                )

                val streamingFlow = plugin.coreInterceptor.intercept(httpRequest)

                var statusCode = 200
                var headers = mapOf<String, String>()

                val streamingByteChannel = ByteChannel()

                try {
                    streamingFlow.collect { response ->
                        when (response) {
                            is HttpStreamResponse.Headers -> {
                                statusCode = response.statusCode
                                headers = response.headers
                            }

                            is HttpStreamResponse.Data -> {
                                streamingByteChannel.writeStringUtf8(response.chunk)
                                streamingByteChannel.flush()
                            }

                            is HttpStreamResponse.Complete -> {
                                streamingByteChannel.flushAndClose()
                            }

                            is HttpStreamResponse.Error -> {
                                streamingByteChannel.close(RuntimeException("Stream error: ${response.message}"))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HttpClientPlugin", "Exception in streaming flow", e)
                    streamingByteChannel.close(e)
                }

                val headersList = mutableListOf<Pair<String, List<String>>>()
                for (header in headers) {
                    headersList.add(Pair(header.key, listOf(header.value)))
                }

                HttpClientCall(
                    scope,
                    request.build(),
                    HttpResponseData(
                        HttpStatusCode(
                            statusCode,
                            getReasonPhrase(statusCode)
                        ),
                        GMTDate(),
                        headersOf(*headersList.toTypedArray()),
                        HttpProtocolVersion.HTTP_1_1,
                        streamingByteChannel,
                        request.executionContext
                    )
                )
            }
        }
    }
}