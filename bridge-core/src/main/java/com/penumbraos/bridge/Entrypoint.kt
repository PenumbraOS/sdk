package com.penumbraos.bridge

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.ServiceManager
import android.util.Log
import com.penumbraos.bridge.external.BRIDGE_SERVICE_READY

const val TAG = "BridgeService"

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
                sendStartBroadcast()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting bridge", e)
                looper?.quit()
                return
            }

            Log.w(TAG, "Bridge started")
            Looper.loop()
            Log.w(TAG, "Bridge quit")
        }

        fun sendStartBroadcast() {
            try {
                val getServiceMethod = ActivityManager::class.java.getDeclaredMethod("getService")
                getServiceMethod.isAccessible = true
                val activityManager = getServiceMethod.invoke(null)

                if (activityManager == null) {
                    Log.e(TAG, "Activity manager is null. Cannot send start broadcast")
                    return
                }

                val paramTypes = arrayOf<Class<*>>(
                    Class.forName("android.app.IApplicationThread"), // caller
                    String::class.java, // callingFeatureId
                    Intent::class.java, // intent
                    String::class.java, // resolvedType
                    Class.forName("android.content.IIntentReceiver"), // resultTo
                    Int::class.javaPrimitiveType!!, // resultCode (primitive int)
                    String::class.java, // resultData
                    Bundle::class.java, // map
                    Array<String>::class.java, // requiredPermissions (String[])
                    Array<String>::class.java, // excludePermissions (String[])
                    Array<String>::class.java, // excludePackages (String[])
                    Int::class.javaPrimitiveType!!, // appOp (primitive int)
                    Bundle::class.java, // options
                    Boolean::class.javaPrimitiveType!!, // serialized (primitive boolean)
                    Boolean::class.javaPrimitiveType!!, // sticky (primitive boolean)
                    Int::class.javaPrimitiveType!! // userId (primitive int)
                )

                val broadcastIntentWithFeatureMethod =
                    activityManager::class.java.getDeclaredMethod(
                        "broadcastIntentWithFeature",
                        *paramTypes
                    )

                // This will display a stack trace, but this should still function
                broadcastIntentWithFeatureMethod.invoke(
                    activityManager,
                    null, // caller
                    null, // callingFeatureId
                    Intent(BRIDGE_SERVICE_READY), // intent,
                    null, // resolvedType
                    null, // resultTo
                    0, // resultCode
                    null, // resultData
                    null, // map
                    emptyArray<String>(), // requiredPermissions
                    emptyArray<String>(), // excludePermissions
                    emptyArray<String>(), // excludePackages
                    -1, // appOp (APP_OP_NONE)
                    null, // options
                    false, // serialized
                    false, // sticky
                    -1 // userId
                )

                Log.e(TAG, "Sent bridge ready broadcast")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending start broadcast", e)
            }
        }
    }
}
