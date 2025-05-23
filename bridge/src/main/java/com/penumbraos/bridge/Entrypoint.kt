package com.penumbraos.bridge

import android.annotation.SuppressLint
import android.os.Looper
import android.util.Log;

class Entrypoint {
    companion object {
        @SuppressLint("PrivateApi")
        @JvmStatic
        fun main(args: Array<String>) {
            Looper.prepare()
            Log.w("SDKBridge", "Starting bridge")

            try {
                val service = BridgeService()
                val serviceManager = Class.forName("android.os.ServiceManager")
                val iBinderClass = Class.forName("android.os.IBinder")
                val addService =
                    serviceManager.getMethod("addService", String::class.java, iBinderClass)

                val castedBinder = iBinderClass.cast(service.asBinder())
                Log.w("SDKBridge", "Registering bridge")
                addService.invoke(null, "nfc", castedBinder)
                Log.w("SDKBridge", "Successfully registered service");
            } catch (e: Exception) {
                Log.e("SDKBridge", "Error starting bridge", e)
                Looper.myLooper()?.quit()
                return
            }

            Log.w("SDKBridge", "Bridge started")
            Looper.loop()
            Log.w("SDKBridge", "Bridge quit")
        }
    }
}
