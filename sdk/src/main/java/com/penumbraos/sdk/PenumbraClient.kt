package com.penumbraos.sdk

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import com.penumbraos.bridge.IBridge
import com.penumbraos.bridge.IHttpProvider
import com.penumbraos.bridge.ISttProvider
import com.penumbraos.bridge.ITouchpadProvider
import com.penumbraos.bridge.IWebSocketProvider
import com.penumbraos.bridge.external.BRIDGE_SERVICE_READY
import com.penumbraos.sdk.api.HttpClient
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
    lateinit var touchpad: TouchpadClient
    val stt: SttClient = SttClient()

    constructor(context: Context, allowInitFailure: Boolean = false) {
        this.context = context
        registerBroadcastListener()
        try {
            this.initialize()
        } catch (e: Exception) {
            if (!allowInitFailure) {
                throw e
            }

            Log.e(TAG, "Failed to initialize bridge", e)
        }
    }

    constructor(
        context: Context,
        bridgeReadyListener: (() -> Unit),
        allowInitFailure: Boolean = false
    ) : this(
        context, allowInitFailure
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
                    } catch (e: Exception) {
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
            val touchpadProvider =
                ITouchpadProvider.Stub.asInterface(service!!.getTouchpadProvider())
            val sttProvider = ISttProvider.Stub.asInterface(service!!.getSttProvider())

            http = HttpClient(httpProvider)
            websocket = WebSocketClient(webSocketProvider)
            touchpad = TouchpadClient(touchpadProvider)
            stt.provider = sttProvider
        } catch (e: Exception) {
            throw Exception("Failed to connect to service bridge", e)
        }
    }

    suspend fun waitForBridge() {
        withTimeout(5000) {
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