package com.penumbraos.bridge

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.penumbraos.bridge.external.BRIDGE_SERVICE_READY

@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
class Entrypoint {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Looper.prepare()
            Log.w("SDKBridge", "Starting bridge")

            val looper = Looper.myLooper()

            Runtime.getRuntime().addShutdownHook(Thread {
                Log.w("SDKBridge", "Shutting down bridge")
                looper?.quitSafely()
                Log.w("SDKBridge", "Terminating")
            })

            try {
                val service = BridgeService()
                val serviceManager = Class.forName("android.os.ServiceManager")
                val iBinderClass = Class.forName("android.os.IBinder")
                val addService =
                    serviceManager.getMethod("addService", String::class.java, iBinderClass)

                val castedBinder = iBinderClass.cast(service.asBinder())
                Log.w("SDKBridge", "Registering bridge")
                addService.invoke(null, "nfc", castedBinder)
                Log.w("SDKBridge", "Successfully registered service")
                sendStartBroadcast()
            } catch (e: Exception) {
                Log.e("SDKBridge", "Error starting bridge", e)
                looper?.quit()
                return
            }

            Log.w("SDKBridge", "Bridge started")
            Looper.loop()
            Log.w("SDKBridge", "Bridge quit")
        }

        fun sendStartBroadcast() {
            try {
                val getServiceMethod = ActivityManager::class.java.getDeclaredMethod("getService")
                getServiceMethod.isAccessible = true
                val activityManager = getServiceMethod.invoke(null)

                if (activityManager == null) {
                    Log.e("SDKBridge", "Activity manager is null. Cannot send start broadcast")
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

                Log.e("SDKBridge", "Sent bridge ready broadcast")
            } catch (e: Exception) {
                Log.e("SDKBridge", "Error sending start broadcast", e)
            }
        }
    }
}
