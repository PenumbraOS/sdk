package com.penumbraos.bridge_system

import MockContext
import android.annotation.SuppressLint
import android.os.Looper
import android.util.Log
import com.penumbraos.appprocessmocks.Common
import com.penumbraos.bridge.external.connectToBridge
import com.penumbraos.bridge_system.provider.HandTrackingProvider
import com.penumbraos.bridge_system.provider.HttpProvider
import com.penumbraos.bridge_system.provider.LedProvider
import com.penumbraos.bridge_system.provider.SttProvider
import com.penumbraos.bridge_system.provider.TouchpadProvider
import com.penumbraos.bridge_system.provider.WebSocketProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "SystemBridgeService"

@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
class Entrypoint {
    companion object {
        @SuppressLint("UnspecifiedRegisterReceiverFlag")
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
                Log.w(TAG, "Terminating")
            })

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val bridge = connectToBridge(TAG, context)
                    Log.i(TAG, "Connected to bridge-core")
                    bridge.registerSystemService(
                        HttpProvider(),
                        WebSocketProvider(),
                        SttProvider(context, looper),
                        TouchpadProvider(looper),
                        LedProvider(context),
                        HandTrackingProvider(context)
                    )
                    Log.w(TAG, "Registered system bridge")
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting bridge", e)
                    looper.quit()
                }
            }

            Log.i(TAG, "Bridge started")
            Looper.loop()
            Log.i(TAG, "Bridge quit")
        }
    }
}