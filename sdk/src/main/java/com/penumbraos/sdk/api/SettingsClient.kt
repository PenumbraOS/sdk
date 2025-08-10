package com.penumbraos.sdk.api

import android.util.Log
import com.penumbraos.bridge.ISettingsCallback
import com.penumbraos.bridge.ISettingsProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "SettingsClient"

@Suppress("UNCHECKED_CAST")
class SettingsClient(private val settingsProvider: ISettingsProvider) {

    suspend fun registerSettings(
        appId: String,
        settingsBuilder: SettingsCategoryBuilder.() -> Unit
    ): Boolean {
        return suspendCancellableCoroutine { continuation ->
            try {
                val builder = SettingsCategoryBuilder()
                builder.settingsBuilder()

                val callback = object : ISettingsCallback.Stub() {
                    override fun onSettingChanged(
                        appId: String,
                        category: String,
                        key: String,
                        value: String
                    ) {
                        Log.d(TAG, "Setting changed: $appId.$category.$key = $value")
                    }

                    override fun onSettingsRegistered(appId: String, category: String) {
                        Log.i(TAG, "Settings registered: $appId.$category")
                        continuation.resume(true)
                    }

                    override fun onError(message: String) {
                        Log.e(TAG, "Settings registration error: $message")
                        continuation.resumeWithException(SettingsException(message))
                    }
                    override fun onActionResult(appId: String, action: String, success: Boolean, message: String, data: Map<*, *>) {
                        Log.d(TAG, "Action result: $appId.$action success=$success")
                    }
                }

                builder.categories.forEach { (categoryName, category) ->
                    settingsProvider.registerSettingsCategory(
                        appId,
                        categoryName,
                        category.toSchemaMap(),
                        callback
                    )
                }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    suspend fun updateSetting(appId: String, category: String, key: String, value: Any): Boolean {
        return try {
            settingsProvider.updateSetting(appId, category, key, value.toString())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update setting: $appId.$category.$key", e)
            false
        }
    }

    suspend fun getSetting(appId: String, category: String, key: String): String? {
        return try {
            settingsProvider.getSetting(appId, category, key)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get setting: $appId.$category.$key", e)
            null
        }
    }

    suspend fun getAllSettings(appId: String): Map<String, Any> {
        return try {
            settingsProvider.getAllSettings(appId) as? Map<String, Any> ?: emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all settings for app: $appId", e)
            emptyMap()
        }
    }

    suspend fun getSystemSettings(): Map<String, Any> {
        return try {
            settingsProvider.getSystemSettings() as? Map<String, Any> ?: emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get system settings", e)
            emptyMap()
        }
    }

    suspend fun updateSystemSetting(key: String, value: Any): Boolean {
        return try {
            settingsProvider.updateSystemSetting(key, value.toString())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update system setting: $key", e)
            false
        }
    }

    fun sendStatusUpdate(appId: String, component: String, payload: Map<String, Any>) {
        try {
            settingsProvider.sendAppStatusUpdate(appId, component, payload)
            Log.d(TAG, "Sent status update: $appId.$component")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send status update: $appId.$component", e)
        }
    }

    fun sendEvent(appId: String, eventType: String, payload: Map<String, Any>) {
        try {
            settingsProvider.sendAppEvent(appId, eventType, payload)
            Log.d(TAG, "Sent event: $appId.$eventType")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send event: $appId.$eventType", e)
        }
    }

    suspend fun executeAction(appId: String, action: String, params: Map<String, Any>): Boolean {
        return try {
            settingsProvider.executeAction(appId, action, params)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute action: $appId.$action", e)
            false
        }
    }
}

class SettingsCategoryBuilder {
    internal val categories = mutableMapOf<String, SettingsCategory>()

    fun category(name: String, builder: SettingsCategory.() -> Unit) {
        val category = SettingsCategory()
        category.builder()
        categories[name] = category
    }
}

@Suppress("UNCHECKED_CAST")
class SettingsCategory {
    internal val settings = mutableMapOf<String, SettingDefinition>()

    fun booleanSetting(key: String, defaultValue: Boolean = false) {
        settings[key] = SettingDefinition(key, SettingType.BOOLEAN, defaultValue)
    }

    fun intSetting(key: String, defaultValue: Int = 0, min: Int? = null, max: Int? = null) {
        val validation = if (min != null || max != null) {
            mapOf("min" to min, "max" to max).filterValues { it != null }
        } else null

        settings[key] = SettingDefinition(
            key, SettingType.INTEGER, defaultValue,
            validation as Map<String, Any>?
        )
    }

    fun stringSetting(
        key: String,
        defaultValue: String = "",
        allowedValues: List<String>? = null,
        regex: String? = null
    ) {
        val validation = mutableMapOf<String, Any>()
        allowedValues?.let { validation["allowedValues"] = it }
        regex?.let { validation["regex"] = it }

        settings[key] =
            SettingDefinition(key, SettingType.STRING, defaultValue, validation.ifEmpty { null })
    }

    fun floatSetting(
        key: String,
        defaultValue: Float = 0.0f,
        min: Float? = null,
        max: Float? = null
    ) {
        val validation = if (min != null || max != null) {
            mapOf("min" to min, "max" to max).filterValues { it != null }
        } else null

        settings[key] = SettingDefinition(
            key, SettingType.FLOAT, defaultValue,
            validation as Map<String, Any>?
        )
    }

    fun actionSetting(
        key: String,
        displayText: String,
        parameters: List<String>? = null,
        description: String? = null
    ) {
        val validation = mutableMapOf<String, Any>()
        validation["displayText"] = displayText
        parameters?.let { validation["parameters"] = it }
        description?.let { validation["description"] = it }

        settings[key] = SettingDefinition(
            key, SettingType.ACTION, displayText,
            validation.ifEmpty { null }
        )
    }

    internal fun toSchemaMap(): Map<String, Map<String, Any>> {
        return settings.mapValues { (_, definition) ->
            val schema = mutableMapOf<String, Any>(
                "type" to definition.type.name.lowercase(),
                "default" to definition.defaultValue
            )
            definition.validation?.let { schema["validation"] = it }
            schema
        }
    }
}

data class SettingDefinition(
    val key: String,
    val type: SettingType,
    val defaultValue: Any,
    val validation: Map<String, Any>? = null
)

enum class SettingType {
    BOOLEAN, INTEGER, STRING, FLOAT, ACTION
}

class SettingsException(message: String) : Exception(message)