package com.penumbraos.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import com.penumbraos.bridge.IBridge
import com.penumbraos.sdk.api.HttpClient
import com.penumbraos.sdk.api.WebSocketClient

class PenumbraClient(private val context: Context) {
    private var service: IBridge? = null
    val http = HttpClient(this)
    val websocket = WebSocketClient(this)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IBridge.Stub.asInterface(binder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    fun initialize(): Boolean {
        val intent = Intent().apply {
            setComponent(ComponentName(
                "com.penumbraos.bridge",
                "com.penumbraos.bridge.FriendlyAPIService"
            ))
        }
        return context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun isConnected(): Boolean = service != null

    fun disconnect() {
        context.unbindService(connection)
    }

    internal fun getService(): IBridge {
        if (service == null) {
            throw DeadObjectException("No active connection to service bridge")
        }

        return service!!
    }
}