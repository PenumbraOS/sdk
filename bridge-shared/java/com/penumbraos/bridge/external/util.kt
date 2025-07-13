package com.penumbraos.bridge.external

import android.annotation.SuppressLint
import android.content.Context
import android.os.ServiceManager
import android.util.Log
import com.penumbraos.bridge.IBridge
import kotlinx.coroutines.delay

@SuppressLint("UnspecifiedRegisterReceiverFlag")
suspend fun connectToBridge(tag: String, context: Context): IBridge {
    try {
        return IBridge.Stub.asInterface(ServiceManager.getService("nfc"))
    } catch (e: Exception) {
        // Bridge is not yet running
    }

//        val channel = Channel<Unit>()

//        Log.i(TAG, "Waiting for bridge-core to register")
//        registerReceiver(context, object : BroadcastReceiver() {
//            override fun onReceive(
//                context: Context?,
//                intent: Intent?
//            ) {
//                if (intent?.action == BRIDGE_SERVICE_REGISTERED) {
//                    channel.trySendBlocking(Unit)
//                }
//            }
//        }, IntentFilter(BRIDGE_SERVICE_REGISTERED))
    var iterations = 0
    while (true) {
        val bridge = IBridge.Stub.asInterface(ServiceManager.getService("nfc"))
        if (bridge != null) {
            Log.i(tag, "bridge-core registration signal received")
            return bridge
        }
        delay(5000)
        if (iterations % 10 == 0) {
            Log.i(tag, "Waiting for bridge-core to register")
        }
        iterations += 1
    }

//        channel.receive()
//        Log.i(TAG, "bridge-core registration signal received")
//        return IBridge.Stub.asInterface(ServiceManager.getService("nfc"))
}

// TODO: Figure this out and add to MockContext
//    @SuppressLint("PrivateApi")
//    fun registerReceiver(
//        context: Context,
//        receiver: BroadcastReceiver?,
//        filter: IntentFilter
//    ): Intent? {
//        if (receiver == null) {
//            return null
//        }
//
//        val activityManager = MockActivityManager.getOriginalIActivityManagerProxy()
//
//        if (activityManager == null) {
//            Log.e(
//                "T",
//                "Activity manager is null. Cannot register receiver"
//            )
//            return null
//        }
//
//        // Create IIntentReceiver wrapper for our BroadcastReceiver
//        val intentReceiver = object : IIntentReceiver.Stub() {
//            override fun performReceive(
//                intent: Intent,
//                resultCode: Int,
//                data: String?,
//                extras: android.os.Bundle?,
//                ordered: Boolean,
//                sticky: Boolean,
//                sendingUser: Int
//            ) {
//                Log.w("Hello", "Received intent: $intent")
//                val handler = Handler(Looper.getMainLooper())
//                handler.post {
//                    receiver.onReceive(context, intent)
//                }
//            }
//        }
//
//        val registerReceiverMethod = activityManager::class.java.getDeclaredMethod(
//            "registerReceiver",
//            Class.forName("android.app.IApplicationThread"),  // caller
//            String::class.java,                               // callerPackage
//            Class.forName("android.content.IIntentReceiver"), // receiver
//            IntentFilter::class.java,                         // filter
//            String::class.java,                               // requiredPermission
//            Int::class.java,                                  // userId
//            Int::class.java                                   // flags
//        )
//        registerReceiverMethod.isAccessible = true
//
//        val intent = registerReceiverMethod.invoke(
//            activityManager,
//            null,             // caller
//            "android",        // callerPackage
//            intentReceiver,   // receiver
//            filter,           // filter
//            null,             // requiredPermission
//            -1,               // userId (UserHandle.USER_ALL)
//            0                 // flags
//        ) as? Intent
//
//        Log.w("Hello", "Registered receiver: $intent")
//
//        return intent
//    }