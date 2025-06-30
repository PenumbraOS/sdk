package com.penumbraos.bridge

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.util.Log

@SuppressLint("DiscouragedPrivateApi")
fun sendBroadcast(intent: Intent) {
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
            intent, // intent,
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