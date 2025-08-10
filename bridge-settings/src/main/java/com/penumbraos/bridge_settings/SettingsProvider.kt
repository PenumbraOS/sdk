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

    override fun sendAppStatusUpdate(appId: String, component: String, payload: Map<*, *>) {
        providerScope.launch {
            try {
                val convertedPayload = convertMapPayload(payload)
                settingsRegistry.sendAppStatusUpdate(appId, component, convertedPayload)
                Log.d(TAG, "Sent app status update: $appId.$component")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending app status update: $appId.$component", e)
            }
        }
    }

    override fun sendAppEvent(appId: String, eventType: String, payload: Map<*, *>) {
        providerScope.launch {
            try {
                val convertedPayload = convertMapPayload(payload)
                settingsRegistry.sendAppEvent(appId, eventType, convertedPayload)
                Log.d(TAG, "Sent app event: $appId.$eventType")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending app event: $appId.$eventType", e)
            }
        }
    }

    override fun executeAction(appId: String, action: String, params: Map<*, *>) {
        providerScope.launch {
            try {
                val convertedParams = convertMapPayload(params)
                settingsRegistry.executeAction(appId, action, convertedParams)
                Log.i(TAG, "Executed action: $appId.$action")
            } catch (e: Exception) {
                Log.e(TAG, "Error executing action: $appId.$action", e)
            }
        }
    }

    override fun executeActionWithCallback(appId: String, action: String, params: Map<*, *>, callback: ISettingsCallback) {
        providerScope.launch {
            try {
                val convertedParams = convertMapPayload(params)
                val result = settingsRegistry.executeAction(appId, action, convertedParams)
                Log.i(TAG, "Executed action with callback: $appId.$action")
                
                safeCallback(TAG) {
                    callback.onActionResult(
                        appId, 
                        action, 
                        result.success, 
                        result.message ?: "", 
                        result.data ?: emptyMap<String, Any>()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing action: $appId.$action", e)
                safeCallback(TAG) {
                    callback.onError("Failed to execute action $appId.$action: ${e.message}")
                }
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
            SettingType.ACTION -> "Action"
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

    private fun convertMapPayload(payload: Map<*, *>): Map<String, Any> {
        return payload.mapKeys { it.key.toString() }.mapValues { it.value ?: "" }
    }

    private fun notifySettingsChanged(allSettings: Map<String, Any>) {
        // This would be implemented to notify specific callbacks about relevant changes
        // For now, we'll skip detailed change detection
    }

    fun cleanup() {
        providerScope.cancel()
        callbacks.clear()
    }

    // Discovery methods for dynamic registration
    override fun getAvailableSystemSettings(): List<com.penumbraos.bridge.types.SystemSettingInfo> {
        return try {
            val systemSettings = settingsRegistry.getAllSystemSettings()
            systemSettings.map { (key, value) ->
                com.penumbraos.bridge.types.SystemSettingInfo().apply {
                    this.key = key
                    this.type = when (value) {
                        is Boolean -> "boolean"
                        is Number -> "number"
                        else -> "string"
                    }
                    this.currentValue = value.toString()
                    this.readOnly = (key == "device.temperature")
                    this.description = getSystemSettingDescription(key)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available system settings", e)
            emptyList()
        }
    }

    override fun getRegisteredApps(): List<String> {
        return try {
            settingsRegistry.getAllActionProviders().keys.toList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting registered apps", e)
            emptyList()
        }
    }

    override fun getAppActions(appId: String): List<com.penumbraos.bridge.types.ActionDefinition> {
        return try {
            val provider = settingsRegistry.getActionProvider(appId)
            if (provider != null) {
                val actions = provider.getActionDefinitions()
                actions.values.map { definition ->
                    com.penumbraos.bridge.types.ActionDefinition().apply {
                        this.key = definition.key
                        this.displayText = definition.displayText
                        this.description = definition.description ?: ""
                        this.parameters = definition.parameters.map { param ->
                            com.penumbraos.bridge.types.ActionParameter().apply {
                                this.name = param.name
                                this.type = param.type.name.lowercase()
                                this.required = param.required
                                this.defaultValue = param.defaultValue?.toString() ?: ""
                                this.description = param.description ?: ""
                            }
                        }
                    }
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app actions for $appId", e)
            emptyList()
        }
    }

    override fun getAllAvailableActions(): List<com.penumbraos.bridge.types.AppActionInfo> {
        return try {
            settingsRegistry.getAllActionProviders().map { (appId, provider) ->
                val actions = provider.getActionDefinitions()
                val actionDefinitions = actions.values.map { definition ->
                    com.penumbraos.bridge.types.ActionDefinition().apply {
                        this.key = definition.key
                        this.displayText = definition.displayText
                        this.description = definition.description ?: ""
                        this.parameters = definition.parameters.map { param ->
                            com.penumbraos.bridge.types.ActionParameter().apply {
                                this.name = param.name
                                this.type = param.type.name.lowercase()
                                this.required = param.required
                                this.defaultValue = param.defaultValue?.toString() ?: ""
                                this.description = param.description ?: ""
                            }
                        }
                    }
                }
                
                com.penumbraos.bridge.types.AppActionInfo().apply {
                    this.appId = appId
                    this.actions = actionDefinitions
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all available actions", e)
            emptyList()
        }
    }
    
    private fun getSystemSettingDescription(key: String): String {
        return when (key) {
            "audio.volume" -> "Audio volume level (0-100)"
            "audio.muted" -> "Audio muted state"
            "display.humane_enabled" -> "Humane display enabled state"
            "device.temperature" -> "Current device temperature (read-only)"
            else -> "System setting: $key"
        }
    }
}