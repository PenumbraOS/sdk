package com.penumbraos.bridge

import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Test
import android.os.IBinder

@OptIn(ExperimentalCoroutinesApi::class)
class FriendlyAPIServiceTest {
    private lateinit var service: BridgeService
    private lateinit var mockCallback: IHttpCallback
    private lateinit var mockBinder: IBinder

    @Before
    fun setup() {
        service = BridgeService()
        mockCallback = mockk(relaxed = true)
        mockBinder = mockk(relaxed = true) {
            val api = mockk<IBridge>(relaxed = true)
            every { queryLocalInterface(any()) } returns api
        }
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `service binds successfully`() {
        val binder = service.onBind(mockk())
        assert(binder is IBridge.Stub)
    }

//    @Test
//    fun `makeHttpRequest calls callback on success`() = runTest {
//        val api = mockk<IMyFriendlyAPI>(relaxed = true)
//        val id = "hello123"
//        every { api.makeHttpRequest(any(), any(), any(), any(), any(), any()) } answers {
//            val callback = it.invocation.args[3] as IHttpCallback
//            callback.onHeaders(id, 200, emptyMap<String, String>())
//            callback.onComplete(id)
//        }
//
//        api.makeHttpRequest(id, "https://example.com", "GET", null, emptyMap<String, String>(), mockCallback)
//
//        verify { mockCallback.onHeaders(id, 200, emptyMap<String, String>()) }
//        verify { mockCallback.onComplete(id) }
//    }
//
//    @Test
//    fun `makeHttpRequest calls error callback on failure`() = runTest {
//        val api = mockk<IMyFriendlyAPI>(relaxed = true)
//        every { api.makeHttpRequest(any(), any(), any(), any()) } answers {
//            val callback = it.invocation.args[3] as IHttpCallback
//            callback.onError(123, "Connection failed", -1)
//        }
//
//        api.makeHttpRequest(123, "https://example.com", emptyMap<String, String>(), mockCallback)
//
//        verify { mockCallback.onError(123, "Connection failed", -1) }
//    }
//
//    @Test
//    fun `makeHttpRequest handles headers and body chunks`() = runTest {
//        val api = mockk<IMyFriendlyAPI>(relaxed = true)
//        every { api.makeHttpRequest(any(), any(), any(), any()) } answers {
//            val callback = it.invocation.args[3] as IHttpCallback
//            callback.onHeaders(123, 200, mapOf("Content-Type" to "application/json"))
//            callback.onData(123, "test".toByteArray())
//            callback.onComplete(123)
//        }
//
//        api.makeHttpRequest(123, "https://example.com", emptyMap<String, String>(), mockCallback)
//
//        verify { mockCallback.onHeaders(123, 200, mapOf("Content-Type" to "application/json")) }
//        verify { mockCallback.onData(123, "test".toByteArray()) }
//        verify { mockCallback.onComplete(123) }
//    }
}