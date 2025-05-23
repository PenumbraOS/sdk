package com.penumbraos.sdk

import android.annotation.SuppressLint
import android.os.DeadObjectException
import android.os.IBinder
import com.penumbraos.bridge.IBridge
import com.penumbraos.sdk.api.HttpClient
import com.penumbraos.sdk.api.WebSocketClient

class PenumbraClient {
    private var service: IBridge? = null
    val http = HttpClient(this)
    val websocket = WebSocketClient(this)

    constructor() {
        this.initialize()
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
            getService().asBinder().pingBinder()
            true
        } catch (e: Exception) {
            false
        }
    }
}