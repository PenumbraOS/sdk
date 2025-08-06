package com.penumbraos.bridge

import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.penumbraos.appprocessmocks.MockActivityManager
import com.penumbraos.bridge.external.BRIDGE_SERVICE_READY

class BridgeService {

    private var httpProvider: IHttpProvider? = null
    private var webSocketProvider: IWebSocketProvider? = null
    private var dnsProvider: IDnsProvider? = null

    private var sttProvider: ISttProvider? = null

    private var touchpadProvider: ITouchpadProvider? = null
    private var ledProvider: ILedProvider? = null
    private var handTrackingProvider: IHandTrackingProvider? = null

    private var settingsProvider: ISettingsProvider? = null
    private var shellProvider: IShellProvider? = null

    private val binder = object : IBridge.Stub() {
        override fun getHttpProvider(): IBinder? {
            return this@BridgeService.httpProvider?.asBinder()
        }

        override fun getWebSocketProvider(): IBinder? {
            return this@BridgeService.webSocketProvider?.asBinder()
        }

        override fun getDnsProvider(): IBinder? {
            return this@BridgeService.dnsProvider?.asBinder()
        }

        override fun getSttProvider(): IBinder? {
            return this@BridgeService.sttProvider?.asBinder()
        }

        override fun getTouchpadProvider(): IBinder? {
            return this@BridgeService.touchpadProvider?.asBinder()
        }

        override fun getLedProvider(): IBinder? {
            return this@BridgeService.ledProvider?.asBinder()
        }

        override fun getHandTrackingProvider(): IBinder? {
            return this@BridgeService.handTrackingProvider?.asBinder()
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
            dnsProvider: IDnsProvider?,
            sttProvider: ISttProvider?,
            touchpadProvider: ITouchpadProvider?,
            ledProvider: ILedProvider?,
            handTrackingProvider: IHandTrackingProvider?
        ) {
            Log.d(TAG, "Registering system bridge services")
            this@BridgeService.httpProvider = httpProvider
            this@BridgeService.webSocketProvider = webSocketProvider
            this@BridgeService.dnsProvider = dnsProvider

            this@BridgeService.sttProvider = sttProvider

            this@BridgeService.touchpadProvider = touchpadProvider
            this@BridgeService.ledProvider = ledProvider
            this@BridgeService.handTrackingProvider = handTrackingProvider

            sendBroadcastIfReady()
        }

        override fun registerSettingsService(settingsProvider: ISettingsProvider?) {
            Log.d(TAG, "Registering settings service")
            this@BridgeService.settingsProvider = settingsProvider

            sendBroadcastIfReady()
        }

        override fun registerShellService(shellProvider: IShellProvider?) {
            Log.d(TAG, "Registering shell service")
            this@BridgeService.shellProvider = shellProvider

            sendBroadcastIfReady()
        }
    }

    fun sendBroadcastIfReady() {
        val isSystemBridgeReady = httpProvider != null
        val isSettingsBridgeReady = settingsProvider != null
        val isShellBridgeReady = shellProvider != null
        if (!isSystemBridgeReady || !isSettingsBridgeReady || !isShellBridgeReady) {
            Log.d(
                TAG,
                "Not all services registered yet. System bridge: $isSystemBridgeReady, settings bridge: $isSettingsBridgeReady, shell bridge: $isShellBridgeReady"
            )
            return
        }

        Log.d(TAG, "Broadcasting bridge ready")
        MockActivityManager.sendBroadcast(Intent(BRIDGE_SERVICE_READY))
    }

    fun asBinder(): IBridge = binder
}