package com.penumbraos.bridge

import android.os.IBinder
import android.util.Log

class BridgeService {

    private var httpProvider: IHttpProvider? = null
    private var webSocketProvider: IWebSocketProvider? = null

    private val binder = object : IBridge.Stub() {
        override fun getHttpProvider(): IBinder? {
            return this@BridgeService.httpProvider?.asBinder()
        }

        override fun getWebSocketProvider(): IBinder? {
            return this@BridgeService.webSocketProvider?.asBinder()
        }

        override fun registerHttpProvider(provider: IHttpProvider) {
            Log.d(TAG, "Registering HTTP provider")
            this@BridgeService.httpProvider = provider
        }

        override fun registerWebSocketProvider(provider: IWebSocketProvider) {
            Log.d(TAG, "Registering WebSocket provider")
            this@BridgeService.webSocketProvider = provider
        }
    }

    fun asBinder(): IBridge = binder
}