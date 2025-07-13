package com.penumbraos.bridge_settings

import MockContext
import android.annotation.SuppressLint
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
            settingsRegistry = SettingsRegistry()
            settingsProvider = SettingsProvider(settingsRegistry)
            webServer = SettingsWebServer(settingsRegistry)

            // Register with bridge service
            serviceScope.launch {
                registerWithBridge()
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
    private suspend fun registerWithBridge() {
        val classLoader = ClassLoader.getSystemClassLoader()
        val thread = Common.initialize(ClassLoader.getSystemClassLoader())
        val context =
            MockContext.createWithAppContext(classLoader, thread, "com.android.settings")

        try {
            val bridge = connectToBridge(TAG, context)
            Log.i(TAG, "Connected to bridge-core")
            bridge.registerSettingsService(settingsProvider)
            Log.i(TAG, "Registered settings bridge")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register with bridge service", e)
            throw e
        }
    }
}