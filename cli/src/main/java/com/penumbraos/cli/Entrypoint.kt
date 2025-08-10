package com.penumbraos.cli

import android.annotation.SuppressLint
import android.util.Log
import com.penumbraos.appprocessmocks.Common
import com.penumbraos.appprocessmocks.MockContext
import com.penumbraos.bridge.IBridge
import com.penumbraos.bridge.ISettingsCallback
import com.penumbraos.bridge.ISettingsProvider
import com.penumbraos.bridge.external.connectToBridge
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.system.exitProcess

private const val TAG = "CLI"

/**
 * CLI program for PenumbraOS Settings
 *
 * This program connects to the bridge settings service and provides command-line access
 * to system settings and module actions like eSIM operations.
 *
 * Usage:
 *   pin settings list
 *   pin settings system audio.volume 75
 *   pin settings esim getProfiles
 *   pin settings esim enableProfile --iccid 89012345678901234567
 */
@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
class Entrypoint {
    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var bridge: IBridge? = null
        private var settingsProvider: ISettingsProvider? = null

        @JvmStatic
        fun main(args: Array<String>) {
            runBlocking {
                try {
                    run(args)
                } catch (e: Exception) {
                    Log.e(TAG, "CLI error", e)
                    println("Error: ${e.message}")
                    exitProcess(1)
                } finally {
                    scope.cancel()
                }
            }

            exitProcess(0)
        }

        suspend fun run(args: Array<String>) {
            if (args.isEmpty()) {
                showRootHelp()
                return
            }

            when (args[0].lowercase()) {
                "settings" -> handleSettingsCommand(args.drop(1).toTypedArray())
                "help", "--help", "-h" -> showRootHelp()
                else -> {
                    println("Unknown command '${args[0]}'")
                    println("Use 'penumbra help' to see available commands.")
                    exitProcess(1)
                }
            }
        }

        private suspend fun handleSettingsCommand(args: Array<String>) {
            if (!connectToServices()) {
                println("Failed to connect to settings service")
                throw RuntimeException("Service connection failed")
            }

            if (args.isEmpty()) {
                showSettingsHelp()
                return
            }

            when (args[0].lowercase()) {
                "list" -> listAvailableOptions()
                "help", "--help", "-h" -> showSettingsHelp()
                "system" -> handleSystemSetting(args.drop(1).toList())
                else -> handleAppAction(args.toList())
            }
        }

        private suspend fun connectToServices(): Boolean {
            return try {
                val classLoader = ClassLoader.getSystemClassLoader()
                val thread = Common.initialize(classLoader)
                val context =
                    MockContext.createWithAppContext(classLoader, thread, "com.android.settings")

                Log.i(TAG, "Connecting to bridge service...")
                bridge = connectToBridge(TAG, context)

                val settingsProviderBinder = bridge?.settingsProvider
                if (settingsProviderBinder != null) {
                    settingsProvider = ISettingsProvider.Stub.asInterface(settingsProviderBinder)
                    Log.i(TAG, "Connected to settings service")
                    true
                } else {
                    Log.e(TAG, "Settings provider not available")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to bridge", e)
                false
            }
        }

        private fun showRootHelp() {
            println(
                """
            PenumbraOS CLI
            
            Usage:
              penumbra <command> [options...]
              
            Available commands:
              settings    Manage system and app settings
              help        Show this help message
              
            Examples:
              penumbra settings list
              penumbra settings system audio.volume 75
              penumbra settings esim getProfiles
              
            For command-specific help, use: penumbra <command> help
            
        """.trimIndent()
            )
        }

        private fun showSettingsHelp() {
            println(
                """
            PenumbraOS Settings CLI
            
            Usage:
              penumbra settings list                              - List all available modules and actions
              penumbra settings system <setting> [value]         - Get or set system setting
              penumbra settings <module> <action> [params...]    - Execute module action
              penumbra settings help                             - Show this help
            
            Use 'penumbra settings list' to see all available system settings and module actions.
            
            Examples:
              penumbra settings list
              penumbra settings system audio.volume 75
              penumbra settings esim getProfiles
              penumbra settings esim enableProfile --iccid 89012345678901234567
            
        """.trimIndent()
            )
        }

        private fun listAvailableOptions() {
            val provider = settingsProvider ?: run {
                println("Settings service not available")
                return
            }

            println("Available Settings Modules and Actions:")
            println()

            try {
                // Get and display system settings dynamically
                val systemSettings = provider.availableSystemSettings
                if (systemSettings.isNotEmpty()) {
                    println("system:")
                    systemSettings.forEach { setting ->
                        val readOnlyIndicator = if (setting.readOnly) " (read-only)" else ""
                        println("  ${setting.key}${readOnlyIndicator} - ${setting.description}")
                    }
                    println()
                }

                // Get and display app actions dynamically
                val allActions = provider.allAvailableActions
                if (allActions.isNotEmpty()) {
                    allActions.forEach { appInfo ->
                        println("${appInfo.appId.lowercase()}:")
                        appInfo.actions.forEach { action ->
                            val paramText = if (action.parameters.isNotEmpty()) {
                                " " + action.parameters.joinToString(" ") { "--${it.name}" }
                            } else ""
                            println("  ${action.key}$paramText - ${action.description}")
                        }
                        println()
                    }
                } else {
                    println("No app actions registered")
                }
            } catch (e: Exception) {
                println("Failed to list available options: ${e.message}")
                Log.e(TAG, "Error listing available options", e)
            }
        }

        private fun handleSystemSetting(args: List<String>) {
            val provider = settingsProvider ?: run {
                println("Settings service not available")
                return
            }

            if (args.isEmpty()) {
                println("Error: System setting name required")
                try {
                    val availableSettings = provider.availableSystemSettings
                    if (availableSettings.isNotEmpty()) {
                        println("Available system settings:")
                        availableSettings.forEach { setting ->
                            val readOnlyIndicator = if (setting.readOnly) " (read-only)" else ""
                            println("  ${setting.key}${readOnlyIndicator} - ${setting.description}")
                        }
                    } else {
                        println("No system settings available")
                    }
                } catch (e: Exception) {
                    println("Failed to get available system settings: ${e.message}")
                }
                return
            }

            val settingKey = args[0]

            if (args.size == 1) {
                // No args, it's a get
                try {
                    val systemSettings = provider.systemSettings
                    val value = systemSettings?.get(settingKey)
                    if (value != null) {
                        println("$settingKey: $value")
                    } else {
                        println("Setting \"$settingKey\" not found")
                    }
                } catch (e: Exception) {
                    println("Failed to get setting: ${e.message}")
                    Log.e(TAG, "Error getting system setting", e)
                }
            } else {
                // More args, it's a set
                val value = args[1]
                try {
                    provider.updateSystemSetting(settingKey, value)
                    println("Successfully set")
                } catch (e: Exception) {
                    println("Failed to set setting: ${e.message}")
                    Log.e(TAG, "Error setting system setting", e)
                }
            }
        }

        private suspend fun handleAppAction(args: List<String>) {
            val provider = settingsProvider ?: run {
                println("Settings service not available")
                return
            }

            if (args.size < 2) {
                println("Error: Module and action required")
                println("Use 'pin settings list' to see available options")
                return
            }

            val module = args[0].lowercase()
            val action = args[1]
            val params = parseParameters(args.drop(2))

            // Validate module exists
            try {
                val registeredApps = provider.registeredApps
                if (module !in registeredApps) {
                    println(
                        "Unknown module '$module'. Available modules: ${
                            registeredApps.joinToString(
                                ", "
                            )
                        }"
                    )
                    return
                }

                // Validate action exists for this module
                val availableActions = provider.getAppActions(module)
                val actionExists = availableActions.any { it.key == action }
                if (!actionExists) {
                    println("Unknown action '$action' for module '$module'")
                    if (availableActions.isNotEmpty()) {
                        println("Available actions for $module:")
                        availableActions.forEach { actionDef ->
                            val paramText = if (actionDef.parameters.isNotEmpty()) {
                                " " + actionDef.parameters.joinToString(" ") { "--${it.name}" }
                            } else ""
                            println("  ${actionDef.key}$paramText - ${actionDef.description}")
                        }
                    }
                    return
                }
            } catch (e: Exception) {
                println("Failed to validate module/action: ${e.message}")
                return
            }

            println("Executing $module.$action...")
            if (params.isNotEmpty()) {
                println("Parameters: ${params.map { "${it.key}=${it.value}" }.joinToString(", ")}")
            }

            try {
                val aidlParams = params.mapValues { it.value as Any }.toMutableMap()

                val result = executeActionSync(provider, module, action, aidlParams)

                if (result.success) {
                    println("Action completed successfully")
                    if (!result.message.isNullOrEmpty()) {
                        println("Message: ${result.message}")
                    }
                    if (result.data.isNotEmpty()) {
                        println()
                        formatData(result.data)
                    }
                } else {
                    println("Action failed: ${result.message ?: "Unknown error"}")
                }

            } catch (e: Exception) {
                println("Failed to execute action: ${e.message}")
                Log.e(TAG, "Error executing action", e)
            }
        }

        private data class ActionResult(
            val success: Boolean,
            val message: String?,
            val data: Map<String, Any> = emptyMap()
        )

        private suspend fun executeActionSync(
            provider: ISettingsProvider,
            appId: String,
            action: String,
            params: Map<String, Any>
        ): ActionResult = suspendCancellableCoroutine { continuation ->
            val callback = object : ISettingsCallback.Stub() {
                override fun onSettingChanged(
                    appId: String,
                    category: String,
                    key: String,
                    value: String
                ) {
                }

                override fun onSettingsRegistered(appId: String, category: String) {}

                override fun onError(message: String) {
                    if (continuation.isActive) {
                        continuation.resume(ActionResult(success = false, message = message))
                    }
                }

                override fun onActionResult(
                    appId: String,
                    action: String,
                    success: Boolean,
                    message: String,
                    data: Map<*, *>
                ) {
                    if (continuation.isActive) {
                        @Suppress("UNCHECKED_CAST")
                        val convertedData = data as? Map<String, Any> ?: emptyMap()
                        continuation.resume(
                            ActionResult(
                                success = success,
                                message = message,
                                data = convertedData
                            )
                        )
                    }
                }
            }

            val timeoutJob = scope.launch {
                delay(30000)
                if (continuation.isActive) {
                    continuation.resume(ActionResult(success = false, message = "Action timed out"))
                }
            }

            continuation.invokeOnCancellation {
                timeoutJob.cancel()
            }

            try {
                provider.executeActionWithCallback(appId, action, params, callback)
            } catch (e: Exception) {
                timeoutJob.cancel()
                if (continuation.isActive) {
                    continuation.resume(
                        ActionResult(
                            success = false,
                            message = "Failed to execute: ${e.message}"
                        )
                    )
                }
            }
        }

        private fun formatData(data: Any?, indent: Int = 0) {
            val prefix = "  ".repeat(indent)
            when (data) {
                is Map<*, *> -> {
                    data.forEach { (key, value) ->
                        print("${prefix}${key}: ")
                        when (value) {
                            is Map<*, *>, is List<*> -> {
                                println()
                                formatData(value, indent + 1)
                            }
                            null -> println("(null)")
                            else -> println(value)
                        }
                    }
                }
                is List<*> -> {
                    if (data.isEmpty()) {
                        println("${prefix}(empty)")
                    } else {
                        data.forEachIndexed { index, item ->
                            print("${prefix}[$index]: ")
                            when (item) {
                                is Map<*, *>, is List<*> -> {
                                    println()
                                    formatData(item, indent + 1)
                                }
                                null -> println("(null)")
                                else -> println(item)
                            }
                        }
                    }
                }
                else -> println("${prefix}$data")
            }
        }

        private fun parseParameters(args: List<String>): Map<String, String> {
            val params = mutableMapOf<String, String>()
            var i = 0
            while (i < args.size) {
                val arg = args[i]
                if (arg.startsWith("--")) {
                    val paramName = arg.substring(2)
                    if (i + 1 < args.size && !args[i + 1].startsWith("--")) {
                        params[paramName] = args[i + 1]
                        i += 2
                    } else {
                        println("Warning: Parameter $arg missing value")
                        i++
                    }
                } else {
                    println("Warning: Unexpected argument $arg (expected --param format)")
                    i++
                }
            }
            return params
        }
    }
}