package com.penumbraos.bridge_system

import MockContext
import android.annotation.SuppressLint
import android.os.Looper
import android.os.ServiceManager
import android.util.Log
import com.penumbraos.appprocessmocks.Common
import com.penumbraos.bridge.IBridge
import com.penumbraos.bridge_system.provider.HttpProvider
import com.penumbraos.bridge_system.provider.SttProvider
import com.penumbraos.bridge_system.provider.TouchpadProvider
import com.penumbraos.bridge_system.provider.WebSocketProvider

private const val TAG = "SystemBridgeService"

@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
class Entrypoint {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Log.w(TAG, "Starting bridge")
            val classLoader = ClassLoader.getSystemClassLoader()
            val thread = Common.initialize(ClassLoader.getSystemClassLoader())
            val context =
                MockContext.createWithAppContext(classLoader, thread, "com.android.settings")

            val looper = Looper.getMainLooper()

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
                bridge.registerSttProvider(SttProvider(context, looper))
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
