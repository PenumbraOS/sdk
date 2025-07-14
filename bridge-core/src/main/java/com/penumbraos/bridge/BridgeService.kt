package com.penumbraos.bridge

import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.penumbraos.appprocessmocks.MockActivityManager
import com.penumbraos.bridge.external.BRIDGE_SERVICE_READY

class BridgeService {

    private var httpProvider: IHttpProvider? = null
    private var webSocketProvider: IWebSocketProvider? = null
    private var touchpadProvider: ITouchpadProvider? = null
    private var sttProvider: ISttProvider? = null
    private var ledProvider: ILedProvider? = null
    private var settingsProvider: ISettingsProvider? = null
    private var shellProvider: IShellProvider? = null

    private var registrationCount = 0

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

        override fun getLedProvider(): IBinder? {
            return this@BridgeService.ledProvider?.asBinder()
        }

        override fun getSettingsProvider(): IBinder? {
            return this@BridgeService.settingsProvider?.asBinder()
        }

        override fun getShellProvider(): IBinder? {
            return this@BridgeService.shellProvider?.asBinder()
        }

        override fun registerSystemService(
            httpProvider: IHttpProvider?,
            webSocketProvider: IWebSocketProvider?,
            touchpadProvider: ITouchpadProvider?,
            sttProvider: ISttProvider?,
            ledProvider: ILedProvider?
        ) {
            Log.d(TAG, "Registering system bridge services")
            this@BridgeService.httpProvider = httpProvider
            this@BridgeService.webSocketProvider = webSocketProvider
            this@BridgeService.touchpadProvider = touchpadProvider
            this@BridgeService.sttProvider = sttProvider
            this@BridgeService.ledProvider = ledProvider

            registrationCount++
            sendBroadcastIfReady()
        }

        override fun registerSettingsService(settingsProvider: ISettingsProvider?) {
            Log.d(TAG, "Registering settings service")
            this@BridgeService.settingsProvider = settingsProvider

            registrationCount++
            sendBroadcastIfReady()
        }

        override fun registerShellService(shellProvider: IShellProvider?) {
            Log.d(TAG, "Registering shell service")
            this@BridgeService.shellProvider = shellProvider

            registrationCount++
            sendBroadcastIfReady()
        }
    }

    fun sendBroadcastIfReady() {
        if (registrationCount != 3) {
            return
        }

        Log.d(TAG, "Broadcasting bridge ready")
        MockActivityManager.sendBroadcast(Intent(BRIDGE_SERVICE_READY))
    }

    fun asBinder(): IBridge = binder
}