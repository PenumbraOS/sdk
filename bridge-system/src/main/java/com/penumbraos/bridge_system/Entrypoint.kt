package com.penumbraos.bridge_system

import MockContext
import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.os.ServiceManager
import android.util.Log
import com.penumbraos.appprocessmocks.Common
import com.penumbraos.bridge.IBridge
import com.penumbraos.bridge_system.provider.HttpProvider
import com.penumbraos.bridge_system.provider.LedProvider
import com.penumbraos.bridge_system.provider.SttProvider
import com.penumbraos.bridge_system.provider.TouchpadProvider
import com.penumbraos.bridge_system.provider.WebSocketProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
                    val bridge = Entrypoint().connectToBridge(context)
                    Log.i(TAG, "Connected to bridge-core")
                    bridge.registerSystemService(
                        HttpProvider(),
                        WebSocketProvider(),
                        TouchpadProvider(looper),
                        SttProvider(context, looper),
                        LedProvider(context)
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

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    suspend fun connectToBridge(context: Context): IBridge {
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
                Log.i(TAG, "bridge-core registration signal received")
                return bridge
            }
            delay(5000)
            if (iterations % 10 == 0) {
                Log.i(TAG, "Waiting for bridge-core to register")
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
}
