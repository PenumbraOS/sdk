package com.penumbraos.bridge

import android.annotation.SuppressLint
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import android.os.ServiceManager
import android.util.Log
import com.penumbraos.appprocessmocks.MockActivityManager
import com.penumbraos.bridge.external.BRIDGE_SERVICE_REGISTERED

const val TAG = "BridgeCoreService"

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
                Log.w(TAG, "Terminating")
            })

            try {
                val service = BridgeService().asBinder() as IBinder
                Log.w(TAG, "Registering bridge")
                ServiceManager.addService("nfc", service)
                Log.w(TAG, "Successfully registered service")
                MockActivityManager.sendBroadcast(Intent(BRIDGE_SERVICE_REGISTERED))
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
