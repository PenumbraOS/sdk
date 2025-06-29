package com.penumbraos.bridge

import android.os.IBinder
import android.util.Log

class BridgeService {

    private var httpProvider: IHttpProvider? = null
    private var webSocketProvider: IWebSocketProvider? = null
    private var touchpadProvider: ITouchpadProvider? = null
    private var sttProvider: ISttProvider? = null

    private val binder = object : IBridge.Stub() {
        override fun getHttpProvider(): IBinder? {
            return this@BridgeService.httpProvider?.asBinder()
        }

        override fun getWebSocketProvider(): IBinder? {
            return this@BridgeService.webSocketProvider?.asBinder()
        }

        override fun getTouchpadProvider(): IBinder? {
            return this@BridgeService.touchpadProvider?.asBinder()
        }

        override fun getSttProvider(): IBinder? {
            return this@BridgeService.sttProvider?.asBinder()
        }

        override fun registerHttpProvider(provider: IHttpProvider) {
            Log.d(TAG, "Registering HTTP provider")
            this@BridgeService.httpProvider = provider
        }

        override fun registerWebSocketProvider(provider: IWebSocketProvider) {
            Log.d(TAG, "Registering WebSocket provider")
            this@BridgeService.webSocketProvider = provider
        }

        override fun registerTouchpadProvider(provider: ITouchpadProvider?) {
            Log.d(TAG, "Registering Touchpad provider")
            this@BridgeService.touchpadProvider = provider
        }

        override fun registerSttProvider(provider: ISttProvider?) {
            Log.d(TAG, "Registering STT provider")
            this@BridgeService.sttProvider = provider
        }
    }

    fun asBinder(): IBridge = binder
}