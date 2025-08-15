package com.penumbraos.bridge_system

import android.annotation.SuppressLint
import android.os.Looper
import android.util.Log
import com.penumbraos.appprocessmocks.Common
import com.penumbraos.appprocessmocks.MockContext
import com.penumbraos.bridge.external.connectToBridge
import com.penumbraos.bridge_system.esim.CustomSharedPreferences
import com.penumbraos.bridge_system.esim.LPA_APK_PATH
import com.penumbraos.bridge_system.esim.MockFactoryService
import com.penumbraos.bridge_system.provider.DnsProvider
import com.penumbraos.bridge_system.provider.EsimProvider
import com.penumbraos.bridge_system.provider.HandGestureProvider
import com.penumbraos.bridge_system.provider.HandTrackingProvider
import com.penumbraos.bridge_system.provider.HttpProvider
import com.penumbraos.bridge_system.provider.LedProvider
import com.penumbraos.bridge_system.provider.SttProvider
import com.penumbraos.bridge_system.provider.TouchpadProvider
import com.penumbraos.bridge_system.provider.WebSocketProvider
import com.penumbraos.bridge_system.util.OkHttpDnsResolver
import dalvik.system.DexClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

private const val TAG = "SystemBridgeService"

@Suppress("DEPRECATION")
@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
class Entrypoint {
    companion object {
        @SuppressLint("UnspecifiedRegisterReceiverFlag", "BlockedPrivateApi")
        @JvmStatic
        fun main(args: Array<String>) {
            Log.w(TAG, "Starting bridge")
            val classLoader =
                DexClassLoader(LPA_APK_PATH, null, null, ClassLoader.getSystemClassLoader())

            val activityThread = Common.initialize(ClassLoader.getSystemClassLoader())
            val context =
                MockContext.createWithAppContext(
                    classLoader,
                    activityThread,
                    "com.android.settings"
                )

            // Prepare context for eSIM operations
            context.setSharedPreferences("Prefs", CustomSharedPreferences())
            context.mockResources = MockFactoryService.createResources(LPA_APK_PATH)
            context.mockApplicationContext = context

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
                    val dnsResolver = OkHttpDnsResolver()
                    val httpClient = OkHttpClient.Builder().dns(dnsResolver).build()
                    bridge.registerSystemService(
                        HttpProvider(httpClient),
                        WebSocketProvider(httpClient),
                        DnsProvider(dnsResolver),
                        SttProvider(context, looper),
                        TouchpadProvider(looper),
                        LedProvider(context),
                        HandGestureProvider(looper),
                        HandTrackingProvider(context),
                        EsimProvider(classLoader, context)
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