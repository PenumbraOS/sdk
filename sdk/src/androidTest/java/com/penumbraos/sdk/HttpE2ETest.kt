package com.penumbraos.sdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.penumbraos.sdk.api.HttpClient
import com.penumbraos.sdk.api.HttpMethod
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

// TODO: This won't actually run on my VM due to opening a test socket for some reason
@RunWith(AndroidJUnit4::class)
class HttpE2ETest {
    @get:Rule
    val mockWebServer = MockWebServer()

    private lateinit var client: PenumbraClient
    private lateinit var rustProcess: Process

    @Before
    fun setup() {
        // Start Rust proxy
        val rustBinary = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "bridge_priv_rs"
        ).absolutePath

        rustProcess = ProcessBuilder()
            .command(rustBinary, "--port", "0")
            .start()

        // Start mock web server
        mockWebServer.start()

        // Wait for Rust proxy to start
        var attempts = 0
        while (attempts++ < 10) {
            try {
                client = PenumbraClient(InstrumentationRegistry.getInstrumentation().targetContext)
                client.ping() // Verify connection
                break
            } catch (e: Exception) {
                if (attempts >= 10) throw e
                Thread.sleep(500)
            }
        }
    }

    @After
    fun teardown() {
        try {
            mockWebServer.shutdown()
            rustProcess.destroy()
            if (!rustProcess.waitFor(5, TimeUnit.SECONDS)) {
                rustProcess.destroyForcibly()
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    @Test
    fun testBasicHttpRequest() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok"}""")
        )

        val response = HttpClient(client).request(
            url = mockWebServer.url("/test").toString(),
            method = HttpMethod.GET
        )

        assertEquals(200, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        assertEquals("""{"status":"ok"}""", response.body)
    }

    @Test
    fun testErrorResponse() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Not found")
        )

        val response = HttpClient(client).request(
            url = mockWebServer.url("/error").toString(),
            method = HttpMethod.GET
        )

        assertEquals(404, response.statusCode)
        assertEquals("Not found", response.body)
    }

    @Test
    fun testPostWithBody() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
        )

        val response = HttpClient(client).request(
            url = mockWebServer.url("/post").toString(),
            method = HttpMethod.POST,
            body = """{"test":1}"""
        )

        assertEquals(201, response.statusCode)
    }
}