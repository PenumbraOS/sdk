package com.penumbraos.bridge_system

import android.annotation.SuppressLint
import android.os.Looper
import android.os.ServiceManager
import android.util.Log
import com.penumbraos.bridge.IBridge

const val TAG = "SystemBridgeService"

@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
class Entrypoint {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Looper.prepare()
            Log.w(TAG, "Starting bridge")

            val looper = Looper.myLooper() as Looper

            Runtime.getRuntime().addShutdownHook(Thread {
                Log.w(TAG, "Shutting down bridge")
                looper.quitSafely()
                Log.w("SDKBridge", "Terminating")
            })

            try {
                val bridge = IBridge.Stub.asInterface(ServiceManager.getService("nfc"))
                Log.w(TAG, "Received bridge $bridge")
                bridge.registerHttpProvider(HttpProvider())
                bridge.registerWebSocketProvider(WebSocketProvider())
                bridge.registerTouchpadProvider(TouchpadProvider(looper))
                Log.w(TAG, "Registered system bridge")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting bridge", e)
                looper.quit()
                return
            }

            Log.w(TAG, "Bridge started")
            Looper.loop()
            Log.w(TAG, "Bridge quit")
        }
    }
}
