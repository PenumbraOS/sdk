package com.penumbraos.bridge

import android.os.Build
import android.os.RemoteException
import com.penumbraos.bridge.util.PrivClient
import com.penumbraos.bridge.util.writeMessage
import com.penumbraos.ipc.proxy.Ipc.ClientToServerMessage
import com.penumbraos.ipc.proxy.Ipc.HttpHeader
import com.penumbraos.ipc.proxy.Ipc.HttpResponseHeaders
import com.penumbraos.ipc.proxy.Ipc.RequestOrigin
import com.penumbraos.ipc.proxy.Ipc.ServerToClientMessage
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.Socket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S_V2])
@OptIn(ExperimentalCoroutinesApi::class)
class BridgeServiceTest {
    init {
        MockKAnnotations.init(this)
    }

    private lateinit var service: BridgeService
    private lateinit var mockClient: PrivClient
    private lateinit var mockCallback: IHttpCallback
    private lateinit var clientToTest: PipedInputStream
    private lateinit var testToClient: PipedOutputStream

    @Before
    fun setup() {
        mockkConstructor(Socket::class)
        mockkConstructor(PrivClient::class)

        // Mock out the socket communicating to privileged Rust
        val clientOut = PipedOutputStream()
        clientToTest = PipedInputStream(clientOut)

        testToClient = PipedOutputStream()
        val clientIn = PipedInputStream(testToClient)


        every { anyConstructed<Socket>().connect(any()) } returns Unit
        every { anyConstructed<Socket>().getInputStream() } returns clientIn
        every { anyConstructed<Socket>().getOutputStream() } returns clientOut
        every { anyConstructed<Socket>().close() } just Runs
        every { anyConstructed<Socket>().isClosed } returns false

        val baseService = spyk<BridgeService>()
        // Force creation of client
        baseService.onCreate()
        mockCallback = mockk(relaxed = true)
        service = baseService.apply {
            mockClient = client!!
        }
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun testMakeHttpRequestSerializesCorrectProtobuf() = runTest {
        coEvery { mockClient.sendMessage(any()) } just Runs
        val requestId = "test123"
        val url = "https://example.com"

        service.asBinder().makeHttpRequest(
            requestId, url, "GET", null, emptyMap<String, String>(), mockCallback
        )

        // Advance time to ensure coroutines complete
        advanceUntilIdle()

        val messageSlot = slot<ClientToServerMessage>()
        coVerify { mockClient.sendMessage(capture(messageSlot)) }

        val message = messageSlot.captured
        assert(message.origin.id == requestId)
        assert(message.httpRequest.url == url)
    }

    @Test
    fun testHandlesTcpDisconnectionDuringRequest() = runTest {
        coEvery { mockClient.sendMessage(any()) } throws IOException("Connection failed")

        service.asBinder().makeHttpRequest(
            "test123", "https://example.com", "GET", null, emptyMap<String, String>(), mockCallback
        )

        advanceUntilIdle()
        coVerify { mockCallback.onError("test123", "Connection failed", -1) }
    }

    @Test
    fun testHandlesCallbackWhenClientDies() = runTest {
        val requestId = "test456"
        every { mockCallback.onHeaders(any(), any(), any()) } throws RemoteException("Client died")

        service.asBinder().makeHttpRequest(
            requestId, "https://example.com", "GET", null, emptyMap<String, String>(), mockCallback
        )

        val message = ServerToClientMessage.newBuilder()
            .setOrigin(RequestOrigin.newBuilder().setId(requestId).build()).setHttpHeaders(
                HttpResponseHeaders.newBuilder().addAllHeaders(
                    listOf(
                        HttpHeader.newBuilder().setKey("foo").setValue("bar").build()
                    )
                )
            ).build()

        writeMessage(message, testToClient)

        advanceUntilIdle()
        coVerify { mockCallback.onHeaders(requestId, any(), any()) }
        coVerify {
            service.genericError(
                requestId,
                "Callback failed with message type HTTP_HEADERS"
            )
        }
    }

    @Test
    fun testCleansUpCallbacksOnServiceDestroy() = runTest {
        val requestId = "test789"
        coEvery { mockClient.sendMessage(any()) } just Runs
        service.asBinder().makeHttpRequest(
            requestId, "https://example.com", "GET", null, emptyMap<String, String>(), mockCallback
        )

        advanceUntilIdle()
        service.onDestroy()

        verify { mockClient.disconnect() }
    }
}