package com.penumbraos.bridge_settings

import android.util.Log
import com.penumbraos.bridge.ISettingsCallback
import com.penumbraos.bridge.ISettingsProvider
import com.penumbraos.bridge_settings.providers.safeCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "SettingsProvider"

class SettingsProvider(private val settingsRegistry: SettingsRegistry) : ISettingsProvider.Stub() {
    private val callbacks = ConcurrentHashMap<String, ISettingsCallback>()
    private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Monitor settings changes and notify callbacks
        providerScope.launch {
            settingsRegistry.settingsFlow.collect { allSettings ->
                notifySettingsChanged(allSettings)
            }
        }
    }

    override fun registerSettingsCategory(
        appId: String,
        category: String,
        settingsSchema: Map<*, *>,
        callback: ISettingsCallback
    ) {
        try {
            Log.i(TAG, "Registering settings category: $appId.$category")

            // Convert schema map to setting definitions
            val definitions = convertSchemaToDefinitions(settingsSchema)
            settingsRegistry.registerAppSettings(appId, category, definitions)

            // Store callback for future notifications
            callbacks["$appId.$category"] = callback

            // Notify successful registration
            safeCallback(TAG) {
                callback.onSettingsRegistered(appId, category)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register settings category: $appId.$category", e)
            safeCallback(TAG) {
                callback.onError("Failed to register settings: ${e.message}")
            }
        }
    }

    override fun unregisterSettingsCategory(appId: String, category: String) {
        try {
            Log.i(TAG, "Unregistering settings category: $appId.$category")
            settingsRegistry.unregisterAppSettings(appId, category)
            callbacks.remove("$appId.$category")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister settings category: $appId.$category", e)
        }
    }

    override fun updateSetting(appId: String, category: String, key: String, value: String) {
        try {
            val convertedValue = convertStringValue(value)
            val success = settingsRegistry.updateAppSetting(appId, category, key, convertedValue)

            if (!success) {
                Log.w(TAG, "Failed to update setting: $appId.$category.$key = $value")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating setting: $appId.$category.$key", e)
        }
    }

    override fun getSetting(appId: String, category: String, key: String): String? {
        return try {
            val value = settingsRegistry.getAppSetting(appId, category, key)
            value?.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting setting: $appId.$category.$key", e)
            null
        }
    }

    override fun getAllSettings(appId: String): Map<*, *> {
        return try {
            settingsRegistry.getAllAppSettings(appId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all settings for app: $appId", e)
            emptyMap<String, Any>()
        }
    }

    override fun getSystemSettings(): Map<*, *> {
        return try {
            settingsRegistry.getAllSystemSettings()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting system settings", e)
            emptyMap<String, Any>()
        }
    }

    override fun updateSystemSetting(key: String, value: String) {
        providerScope.launch {
            try {
                val convertedValue = convertStringValue(value)
                val success = settingsRegistry.updateSystemSetting(key, convertedValue)

                if (!success) {
                    Log.w(TAG, "Failed to update system setting: $key = $value")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating system setting: $key", e)
            }
        }
    }

    private fun convertSchemaToDefinitions(schema: Map<*, *>): Map<String, SettingDefinition> {
        val definitions = mutableMapOf<String, SettingDefinition>()

        schema.forEach { (key, value) ->
            if (key is String && value is Map<*, *>) {
                try {
                    val type =
                        SettingType.valueOf((value["type"] as? String)?.uppercase() ?: "STRING")
                    val defaultValue = value["default"] ?: getDefaultForType(type)

                    val validation = value["validation"]?.let { v ->
                        if (v is Map<*, *>) {
                            SettingValidation(
                                min = (v["min"] as? Number),
                                max = (v["max"] as? Number),
                                allowedValues = (v["allowedValues"] as? List<Any>),
                                regex = (v["regex"] as? String)
                            )
                        } else null
                    }

                    definitions[key] = SettingDefinition(key, type, defaultValue, validation)
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid setting definition for key: $key", e)
                }
            }
        }

        return definitions
    }

    private fun getDefaultForType(type: SettingType): Any {
        return when (type) {
            SettingType.BOOLEAN -> false
            SettingType.INTEGER -> 0
            SettingType.STRING -> ""
            SettingType.FLOAT -> 0.0f
        }
    }

    private fun convertStringValue(value: String): Any {
        return when {
            value.equals("true", ignoreCase = true) -> true
            value.equals("false", ignoreCase = true) -> false
            value.toIntOrNull() != null -> value.toInt()
            value.toFloatOrNull() != null -> value.toFloat()
            else -> value
        }
    }

    private fun notifySettingsChanged(allSettings: Map<String, Any>) {
        // This would be implemented to notify specific callbacks about relevant changes
        // For now, we'll skip detailed change detection
    }

    fun cleanup() {
        providerScope.cancel()
        callbacks.clear()
    }
}