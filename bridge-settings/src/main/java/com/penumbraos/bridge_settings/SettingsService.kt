package com.penumbraos.bridge_settings

import MockContext
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.penumbraos.appprocessmocks.Common
import com.penumbraos.bridge.external.connectToBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "SettingsService"

class SettingsService {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var settingsRegistry: SettingsRegistry
    private lateinit var webServer: SettingsWebServer
    private lateinit var settingsProvider: SettingsProvider

    fun start() {
        Log.i(TAG, "Starting Settings Service")

        try {
            // Register with bridge service first to get context
            serviceScope.launch {
                val classLoader = ClassLoader.getSystemClassLoader()
                val thread = Common.initialize(ClassLoader.getSystemClassLoader())
                val context =
                    MockContext.createWithAppContext(classLoader, thread, "com.android.settings")

                // Initialize components with context
                settingsRegistry = SettingsRegistry(context)
                settingsProvider = SettingsProvider(settingsRegistry)
                
                registerWithBridge(context, settingsProvider)

                webServer = SettingsWebServer(settingsRegistry)

                webServer.start()
            }

            Log.i(TAG, "Settings Service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Settings Service", e)
            throw e
        }
    }

    fun stop() {
        Log.i(TAG, "Stopping Settings Service")
        try {
            webServer.stop()
            Log.i(TAG, "Settings Service stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Settings Service", e)
        }
    }

    @SuppressLint("PrivateApi")
    private suspend fun registerWithBridge(
        context: Context,
        settingsProvider: SettingsProvider
    ): Context {
        try {
            val bridge = connectToBridge(TAG, context)
            Log.i(TAG, "Connected to bridge-core")
            bridge.registerSettingsService(settingsProvider)
            Log.i(TAG, "Registered settings bridge")
            return context
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register with bridge service", e)
            throw e
        }
    }
}