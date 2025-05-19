package com.penumbraos.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.penumbraos.bridge.IMyFriendlyAPI

class PenumbraSDK(private val context: Context) {
    private var service: IMyFriendlyAPI? = null
    private var isBound = false
    val http = HttpClient(this)
    val websocket = WebSocketClient(this)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IMyFriendlyAPI.Stub.asInterface(binder)
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
        }
    }

    fun initialize(): Boolean {
        val intent = Intent().apply {
            Intent.setComponent = ComponentName(
                "com.penumbraos.bridge",
                "com.penumbraos.bridge.FriendlyAPIService"
            )
        }
        return context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun isConnected(): Boolean = isBound

    fun disconnect() {
        context.unbindService(connection)
        isBound = false
    }

    internal fun getService(): IMyFriendlyAPI? = service
}