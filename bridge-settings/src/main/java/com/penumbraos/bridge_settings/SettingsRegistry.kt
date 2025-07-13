package com.penumbraos.bridge_settings

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "SettingsRegistry"

data class SettingDefinition(
    val key: String,
    val type: SettingType,
    val defaultValue: Any,
    val validation: SettingValidation? = null
)

enum class SettingType {
    BOOLEAN, INTEGER, STRING, FLOAT
}

data class SettingValidation(
    val min: Number? = null,
    val max: Number? = null,
    val allowedValues: List<Any>? = null,
    val regex: String? = null
)

data class AppSettingsCategory(
    val appId: String,
    val category: String,
    val definitions: Map<String, SettingDefinition>,
    val values: MutableMap<String, Any> = mutableMapOf()
)

class SettingsRegistry {
    private val appSettings = ConcurrentHashMap<String, MutableMap<String, AppSettingsCategory>>()
    private val systemSettings = ConcurrentHashMap<String, Any>()
    
    private val _settingsFlow = MutableStateFlow<Map<String, Any>>(emptyMap())
    val settingsFlow: StateFlow<Map<String, Any>> = _settingsFlow.asStateFlow()

    init {
        initializeSystemSettings()
    }

    private fun initializeSystemSettings() {
        // Initialize with some common system settings
        systemSettings["display.brightness"] = 50
        systemSettings["display.auto_brightness"] = true
        systemSettings["audio.volume"] = 70
        systemSettings["audio.muted"] = false
        systemSettings["network.wifi_enabled"] = true
        systemSettings["battery.power_save_mode"] = false
        
        updateSettingsFlow()
    }

    fun registerAppSettings(appId: String, category: String, definitions: Map<String, SettingDefinition>) {
        Log.i(TAG, "Registering settings for app: $appId, category: $category")
        
        val appCategories = appSettings.getOrPut(appId) { mutableMapOf() }
        val settingsCategory = AppSettingsCategory(appId, category, definitions)
        
        // Initialize with default values
        definitions.forEach { (key, definition) ->
            settingsCategory.values[key] = definition.defaultValue
        }
        
        appCategories[category] = settingsCategory
        updateSettingsFlow()
    }

    fun unregisterAppSettings(appId: String, category: String) {
        Log.i(TAG, "Unregistering settings for app: $appId, category: $category")
        
        appSettings[appId]?.remove(category)
        if (appSettings[appId]?.isEmpty() == true) {
            appSettings.remove(appId)
        }
        updateSettingsFlow()
    }

    fun updateAppSetting(appId: String, category: String, key: String, value: Any): Boolean {
        val settingsCategory = appSettings[appId]?.get(category) ?: return false
        val definition = settingsCategory.definitions[key] ?: return false
        
        if (!validateSetting(definition, value)) {
            Log.w(TAG, "Invalid value for setting $appId.$category.$key: $value")
            return false
        }
        
        settingsCategory.values[key] = value
        updateSettingsFlow()
        Log.i(TAG, "Updated app setting: $appId.$category.$key = $value")
        return true
    }

    fun getAppSetting(appId: String, category: String, key: String): Any? {
        return appSettings[appId]?.get(category)?.values?.get(key)
    }

    fun getAllAppSettings(appId: String): Map<String, Map<String, Any>> {
        return appSettings[appId]?.mapValues { (_, category) ->
            category.values.toMap()
        } ?: emptyMap()
    }

    fun updateSystemSetting(key: String, value: Any): Boolean {
        // In a real implementation, this would interact with Android system settings
        if (validateSystemSetting(key, value)) {
            systemSettings[key] = value
            updateSettingsFlow()
            Log.i(TAG, "Updated system setting: $key = $value")
            return true
        }
        return false
    }

    fun getSystemSetting(key: String): Any? {
        return systemSettings[key]
    }

    fun getAllSystemSettings(): Map<String, Any> {
        return systemSettings.toMap()
    }

    fun getAllSettings(): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        // Add system settings
        result["system"] = systemSettings.toMap()
        
        // Add app settings
        appSettings.forEach { (appId, categories) ->
            val appData = mutableMapOf<String, Any>()
            categories.forEach { (category, categoryData) ->
                appData[category] = categoryData.values.toMap()
            }
            result[appId] = appData
        }
        
        return result
    }

    private fun validateSetting(definition: SettingDefinition, value: Any): Boolean {
        // Type validation
        val isValidType = when (definition.type) {
            SettingType.BOOLEAN -> value is Boolean
            SettingType.INTEGER -> value is Int || value is Long
            SettingType.STRING -> value is String
            SettingType.FLOAT -> value is Float || value is Double
        }
        
        if (!isValidType) return false
        
        // Additional validation
        definition.validation?.let { validation ->
            when {
                validation.min != null && value is Number && value.toDouble() < validation.min.toDouble() -> return false
                validation.max != null && value is Number && value.toDouble() > validation.max.toDouble() -> return false
                validation.allowedValues != null && value !in validation.allowedValues -> return false
                validation.regex != null && value is String && !value.matches(Regex(validation.regex)) -> return false
            }
        }
        
        return true
    }

    private fun validateSystemSetting(key: String, value: Any): Boolean {
        // Basic validation for system settings
        return when (key) {
            "display.brightness" -> value is Number && value.toInt() in 0..100
            "display.auto_brightness" -> value is Boolean
            "audio.volume" -> value is Number && value.toInt() in 0..100
            "audio.muted" -> value is Boolean
            "network.wifi_enabled" -> value is Boolean
            "battery.power_save_mode" -> value is Boolean
            else -> true // Allow unknown settings for extensibility
        }
    }

    private fun updateSettingsFlow() {
        _settingsFlow.value = getAllSettings()
    }
}