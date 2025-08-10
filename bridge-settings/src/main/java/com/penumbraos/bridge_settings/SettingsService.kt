package com.penumbraos.bridge_settings

import android.util.Log
import com.penumbraos.appprocessmocks.Common
import com.penumbraos.appprocessmocks.MockContext
import com.penumbraos.bridge.IEsimProvider
import com.penumbraos.bridge.IShellProvider
import com.penumbraos.bridge.external.connectToBridge
import com.penumbraos.bridge.external.waitForBridgeShell
import com.penumbraos.bridge.external.waitForBridgeSystem
import com.penumbraos.sdk.api.EsimClient
import com.penumbraos.sdk.api.ShellClient
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
    private lateinit var esimProvider: EsimSettingsProvider

    fun start() {
        Log.i(TAG, "Starting Settings Service")

        try {
            // Register with bridge service first to get context
            serviceScope.launch {
                val classLoader = ClassLoader.getSystemClassLoader()
                val thread = Common.initialize(ClassLoader.getSystemClassLoader())
                val context =
                    MockContext.createWithAppContext(classLoader, thread, "com.android.settings")

                // Connect to bridge and get ShellClient
                val bridge = connectToBridge(TAG, context)
                Log.i(TAG, "Connected to bridge-core")

                waitForBridgeShell(TAG, bridge)

                val shellProvider = IShellProvider.Stub.asInterface(bridge.shellProvider)
                val shellClient = ShellClient(shellProvider)
                Log.i(TAG, "Created ShellClient")

                // Initialize components with context and shell client
                settingsRegistry = SettingsRegistry(context, shellClient)
                settingsRegistry.initialize()
                settingsProvider = SettingsProvider(settingsRegistry)

                bridge.registerSettingsService(settingsProvider)
                Log.i(TAG, "Registered settings service")

                webServer = SettingsWebServer(settingsRegistry)

                // Connect registry to web server for broadcasting
                settingsRegistry.setWebServer(webServer)

                webServer.start()

                waitForBridgeSystem(TAG, bridge)

                try {
                    val esimProviderInterface = IEsimProvider.Stub.asInterface(bridge.esimProvider)
                    if (esimProviderInterface != null) {
                        val esimClient = EsimClient(esimProviderInterface)
                        esimProvider = EsimSettingsProvider(esimClient, settingsRegistry)
                        settingsRegistry.registerActionProvider("esim", esimProvider)
                        Log.i(TAG, "Registered eSIM action provider")
                    } else {
                        Log.e(TAG, "eSIM provider not available or failed to initialize")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "eSIM provider not available or failed to initialize", e)
                }
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

}