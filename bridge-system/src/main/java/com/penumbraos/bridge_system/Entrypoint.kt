package com.penumbraos.bridge_system

import android.annotation.SuppressLint
import android.os.Looper
import android.util.Log

const val TAG = "SystemBridgeService"

@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
class Entrypoint {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Looper.prepare()
            Log.w(TAG, "Starting bridge")

            val looper = Looper.myLooper()

            Runtime.getRuntime().addShutdownHook(Thread {
                Log.w(TAG, "Shutting down bridge")
                looper?.quitSafely()
                Log.w("SDKBridge", "Terminating")
            })

            try {
            } catch (e: Exception) {
                Log.e(TAG, "Error starting bridge", e)
                looper?.quit()
                return
            }

            Log.w(TAG, "Bridge started")
            Looper.loop()
            Log.w(TAG, "Bridge quit")
        }
    }
}
