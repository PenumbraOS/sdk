package com.penumbraos.sdk

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import com.penumbraos.bridge.IBridge
import com.penumbraos.bridge.IHandTrackingProvider
import com.penumbraos.bridge.IHttpProvider
import com.penumbraos.bridge.ILedProvider
import com.penumbraos.bridge.ISettingsProvider
import com.penumbraos.bridge.IShellProvider
import com.penumbraos.bridge.ISttProvider
import com.penumbraos.bridge.ITouchpadProvider
import com.penumbraos.bridge.IWebSocketProvider
import com.penumbraos.bridge.external.BRIDGE_SERVICE_READY
import com.penumbraos.sdk.api.HandTrackingClient
import com.penumbraos.sdk.api.HttpClient
import com.penumbraos.sdk.api.LedClient
import com.penumbraos.sdk.api.SettingsClient
import com.penumbraos.sdk.api.ShellClient
import com.penumbraos.sdk.api.SttClient
import com.penumbraos.sdk.api.TouchpadClient
import com.penumbraos.sdk.api.WebSocketClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

const val TAG = "PenumbraClient"

class PenumbraClient {
    private var service: IBridge? = null
    private var context: Context
    private var serviceReadyReceiver: BroadcastReceiver? = null
    private var bridgeReadyListeners: MutableList<() -> Unit> = mutableListOf()
    private val bridgeReadySignal: CompletableDeferred<Unit> = CompletableDeferred()

    lateinit var http: HttpClient
    lateinit var websocket: WebSocketClient
    val stt: SttClient = SttClient()

    lateinit var touchpad: TouchpadClient
    lateinit var led: LedClient
    lateinit var handTracking: HandTrackingClient

    lateinit var settings: SettingsClient
    lateinit var shell: ShellClient

    constructor(context: Context) {
        this.context = context
        registerBroadcastListener()
        try {
            this.initialize()
        } catch (_: Exception) {
            Log.w(TAG, "Could not connect to bridge")
        }
    }

    constructor(
        context: Context,
        bridgeReadyListener: (() -> Unit),
    ) : this(
        context
    ) {
        this.bridgeReadyListeners.add(bridgeReadyListener)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerBroadcastListener() {
        serviceReadyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (BRIDGE_SERVICE_READY == intent?.action) {
                    Log.d(TAG, "Received bridge service ready")

                    try {
                        initialize()
                        for (listener in bridgeReadyListeners) {
                            try {
                                listener.invoke()
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to invoke bridge ready listener", e)
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore
                    }
                    bridgeReadySignal.complete(Unit)
                }
            }
        }

        context.registerReceiver(
            serviceReadyReceiver, IntentFilter(BRIDGE_SERVICE_READY),
        )
    }

    @SuppressLint("PrivateApi")
    fun initialize() {
        try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)

            val binder = getService.invoke(null, "nfc") as IBinder
            service = IBridge.Stub.asInterface(binder)

            val httpProvider = IHttpProvider.Stub.asInterface(service!!.getHttpProvider())
            val webSocketProvider =
                IWebSocketProvider.Stub.asInterface(service!!.getWebSocketProvider())
            val sttProvider = ISttProvider.Stub.asInterface(service!!.getSttProvider())

            val touchpadProvider =
                ITouchpadProvider.Stub.asInterface(service!!.getTouchpadProvider())
            val ledProvider = ILedProvider.Stub.asInterface(service!!.getLedProvider())
            val handTrackingProvider =
                IHandTrackingProvider.Stub.asInterface(service!!.getHandTrackingProvider())

            val settingsProvider =
                ISettingsProvider.Stub.asInterface(service!!.getSettingsProvider())

            val shellProvider =
                IShellProvider.Stub.asInterface(service!!.getShellProvider())

            http = HttpClient(httpProvider)
            websocket = WebSocketClient(webSocketProvider)
            stt.provider = sttProvider

            touchpad = TouchpadClient(touchpadProvider)
            led = LedClient(ledProvider)
            handTracking = HandTrackingClient(handTrackingProvider)

            settings = SettingsClient(settingsProvider)
            shell = ShellClient(shellProvider)

            // If we're here, the bridge service connected
            bridgeReadySignal.complete(Unit)
        } catch (e: Exception) {
            throw Exception("Failed to connect to service bridge", e)
        }
    }

    suspend fun waitForBridge(timeout: Long? = null) {
        if (timeout != null) {
            withTimeout(5000) {
                bridgeReadySignal.await()
            }
        } else {
            bridgeReadySignal.await()
        }
    }

    fun isConnected(): Boolean = service?.asBinder()?.isBinderAlive == true

    fun ping(): Boolean {
        return try {
            Log.w(TAG, "Pinging NFC bridge service")
            service?.asBinder()?.pingBinder() == true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ping NFC bridge service", e)
            false
        }
    }
}