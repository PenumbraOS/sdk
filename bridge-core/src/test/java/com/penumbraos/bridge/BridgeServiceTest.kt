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
import com.penumbraos.ipc.proxy.Ipc.WebSocketClosedFromServer
import com.penumbraos.ipc.proxy.Ipc.WebSocketMessageType
import com.penumbraos.ipc.proxy.Ipc.WebSocketOpenedResponse
import com.penumbraos.ipc.proxy.Ipc.WebSocketProxyMessage
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
    fun testOpenWebSocketSerializesCorrectProtobuf() = runTest {
        coEvery { mockClient.sendMessage(any()) } just Runs
        val requestId = "ws-test123"
        val url = "wss://example.com/ws"
        val mockWsCallback = mockk<IWebSocketCallback>(relaxed = true)

        service.asBinder().openWebSocket(requestId, url, emptyMap<String, String>(), mockWsCallback)
        advanceUntilIdle()

        val messageSlot = slot<ClientToServerMessage>()
        coVerify { mockClient.sendMessage(capture(messageSlot)) }

        val message = messageSlot.captured
        assert(message.origin.id == requestId)
        assert(message.wsOpenRequest.url == url)
    }

    @Test
    fun testHandlesTcpDisconnectionDuringWebSocketOpen() = runTest {
        coEvery { mockClient.sendMessage(any()) } throws IOException("Connection failed")
        val requestId = "ws-test456"
        val mockWsCallback = mockk<IWebSocketCallback>(relaxed = true)

        service.asBinder().openWebSocket(
            requestId,
            "wss://example.com",
            emptyMap<String, String>(),
            mockWsCallback
        )
        advanceUntilIdle()

        coVerify { mockWsCallback.onError(requestId, "Connection failed") }
    }

    @Test
    fun testHandlesCallbackWhenWebSocketClientDies() = runTest {
        val requestId = "ws-test789"
        val mockWsCallback = mockk<IWebSocketCallback>(relaxed = true)
        every { mockWsCallback.onOpen(any(), any()) } throws RemoteException("Client died")

        service.asBinder().openWebSocket(
            requestId,
            "wss://example.com",
            emptyMap<String, String>(),
            mockWsCallback
        )

        val message = ServerToClientMessage.newBuilder()
            .setOrigin(RequestOrigin.newBuilder().setId(requestId).build())
            .setWsOpened(
                WebSocketOpenedResponse.newBuilder()
                    .addAllHeaders(
                        listOf(
                            HttpHeader.newBuilder().setKey("foo").setValue("bar").build()
                        )
                    )
                    .build()
            ).build()

        writeMessage(message, testToClient)
        advanceUntilIdle()

        coVerify { mockWsCallback.onOpen(requestId, any()) }
        verify {
            service.genericError(
                requestId,
                "Callback failed with message type WS_OPENED"
            )
        }
    }

    @Test
    fun testSendWebSocketMessageSerializesCorrectProtobuf() = runTest {
        coEvery { mockClient.sendMessage(any()) } just Runs
        val requestId = "ws-test101"
        val testData = "test message".toByteArray()

        service.asBinder()
            .sendWebSocketMessage(requestId, WebSocketMessageType.TEXT.number, testData)
        advanceUntilIdle()

        val messageSlot = slot<ClientToServerMessage>()
        coVerify { mockClient.sendMessage(capture(messageSlot)) }

        val message = messageSlot.captured
        assert(message.origin.id == requestId)
        assert(message.wsMessageToServer.type == WebSocketMessageType.TEXT)
        assert(message.wsMessageToServer.data.toByteArray().contentEquals(testData))
    }

    @Test
    fun testReceivesWebSocketMessage() = runTest {
        val requestId = "ws-test202"
        val mockWsCallback = mockk<IWebSocketCallback>(relaxed = true)
        val testMessage = "test message".toByteArray()

        service.asBinder().openWebSocket(
            requestId,
            "wss://example.com",
            emptyMap<String, String>(),
            mockWsCallback
        )

        val message = ServerToClientMessage.newBuilder()
            .setOrigin(RequestOrigin.newBuilder().setId(requestId).build())
            .setWsMessageFromServer(
                WebSocketProxyMessage.newBuilder()
                    .setType(WebSocketMessageType.TEXT)
                    .setData(com.google.protobuf.ByteString.copyFrom(testMessage))
                    .build()
            ).build()

        writeMessage(message, testToClient)
        advanceUntilIdle()

        coVerify {
            mockWsCallback.onMessage(
                requestId,
                WebSocketMessageType.TEXT.number,
                testMessage
            )
        }
    }

    @Test
    fun testHandlesWebSocketClose() = runTest {
        val requestId = "ws-test303"
        val mockWsCallback = mockk<IWebSocketCallback>(relaxed = true)

        service.asBinder().openWebSocket(
            requestId,
            "wss://example.com",
            emptyMap<String, String>(),
            mockWsCallback
        )

        val message = ServerToClientMessage.newBuilder()
            .setOrigin(RequestOrigin.newBuilder().setId(requestId).build())
            .setWsClosedFromServer(
                WebSocketClosedFromServer.newBuilder().setCode(123)
                    .setReason("Custom closed reason")
            ).build()

        writeMessage(message, testToClient)
        advanceUntilIdle()

        coVerify { mockWsCallback.onClose(requestId) }
    }
}