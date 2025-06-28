package com.penumbraos.sdk

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.DeadObjectException
import android.os.IBinder
import android.util.Log
import com.penumbraos.bridge.IBridge
import com.penumbraos.bridge.external.BRIDGE_SERVICE_READY
import com.penumbraos.sdk.api.HttpClient
import com.penumbraos.sdk.api.WebSocketClient

const val TAG = "PenumbraClient"

class PenumbraClient {
    private var service: IBridge? = null
    private var context: Context
    var serviceReadyReceiver: BroadcastReceiver? = null
    var bridgeReadyListener: (() -> Unit)? = null

    val http = HttpClient(this)
    val websocket = WebSocketClient(this)

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
                // This method is called when the broadcast is received

                // Always check the action to be safe, although the filter should handle this
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
            // We must reflect as this is a private API
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)

            val binder = getService.invoke(null, "nfc") as IBinder
            service = IBridge.Stub.asInterface(binder)
        } catch (e: Exception) {
            throw Exception("Failed to connect to service bridge", e)
        }
    }

    fun isConnected(): Boolean = service?.asBinder()?.isBinderAlive == true

    internal fun getService(): IBridge {
        if (service == null) {
            throw DeadObjectException("No active connection to service bridge")
        }

        return service!!
    }

    /**
     * Simple ping method to verify service connectivity
     * @return true if service is responsive, false otherwise
     */
    fun ping(): Boolean {
        return try {
            Log.w(TAG, "Pinging NFC bridge service")
            getService().asBinder().pingBinder()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ping NFC bridge service", e)
            false
        }
    }
}