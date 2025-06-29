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
import com.penumbraos.bridge.IWebSocketProvider
import com.penumbraos.bridge.ITouchpadProvider
import com.penumbraos.bridge.ISttProvider
import com.penumbraos.bridge.external.BRIDGE_SERVICE_READY
import com.penumbraos.sdk.api.HttpClient
import com.penumbraos.sdk.api.TouchpadClient
import com.penumbraos.sdk.api.WebSocketClient
import com.penumbraos.sdk.api.SttClient

const val TAG = "PenumbraClient"

class PenumbraClient {
    private var service: IBridge? = null
    private var context: Context
    var serviceReadyReceiver: BroadcastReceiver? = null
    var bridgeReadyListener: (() -> Unit)? = null

    lateinit var http: HttpClient
    lateinit var websocket: WebSocketClient
    lateinit var touchpad: TouchpadClient
    lateinit var stt: SttClient

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
        this.bridgeReadyListener = bridgeReadyListener
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerBroadcastListener() {
        serviceReadyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (BRIDGE_SERVICE_READY == intent?.action) {
                    Log.d(TAG, "Received bridge service ready")

                    try {
                        initialize()
                        bridgeReadyListener?.invoke()
                    } catch (e: Exception) {
                        // Ignore
                    }
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
            val webSocketProvider = IWebSocketProvider.Stub.asInterface(service!!.getWebSocketProvider())
            val touchpadProvider = ITouchpadProvider.Stub.asInterface(service!!.getTouchpadProvider())
            val sttProvider = ISttProvider.Stub.asInterface(service!!.getSttProvider())

            http = HttpClient(httpProvider)
            websocket = WebSocketClient(webSocketProvider)
            touchpad = TouchpadClient(touchpadProvider)
            stt = SttClient(sttProvider)
        } catch (e: Exception) {
            throw Exception("Failed to connect to service bridge", e)
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