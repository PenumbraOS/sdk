package com.penumbraos.sdk.api

import android.util.Log
import com.penumbraos.bridge.ISettingsProvider
import com.penumbraos.bridge.callback.IHttpEndpointCallback
import com.penumbraos.bridge.callback.IHttpResponseCallback
import com.penumbraos.bridge.callback.ISettingsCallback
import com.penumbraos.sdk.api.types.HttpEndpointHandler
import com.penumbraos.sdk.api.types.HttpRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "SettingsClient"

interface BooleanSettingListener {
    fun onSettingChanged(value: Boolean)
}

interface StringSettingListener {
    fun onSettingChanged(value: String)
}

interface IntSettingListener {
    fun onSettingChanged(value: Int)
}

interface FloatSettingListener {
    fun onSettingChanged(value: Float)
}

@Suppress("UNCHECKED_CAST")
class SettingsClient(private val settingsProvider: ISettingsProvider) {
    private val listenerCallbacks = mutableMapOf<String, ISettingsCallback>()
    private val endpointCallbacks = mutableMapOf<String, IHttpEndpointCallback>()

    private val scope = CoroutineScope(Dispatchers.IO)

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

                    override fun onActionResult(
                        appId: String,
                        action: String,
                        success: Boolean,
                        message: String,
                        data: Map<*, *>
                    ) {
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

    suspend fun registerHttpEndpoint(
        providerId: String,
        path: String,
        method: String,
        handler: HttpEndpointHandler
    ): Boolean {
        return suspendCancellableCoroutine { continuation ->
            try {
                val endpointKey = "${method.uppercase()}:$path"

                val aidlCallback = object : IHttpEndpointCallback.Stub() {
                    override fun onHttpRequest(
                        path: String,
                        method: String,
                        pathParams: MutableMap<Any?, Any?>,
                        headers: MutableMap<Any?, Any?>?,
                        queryParams: MutableMap<Any?, Any?>?,
                        body: ByteArray?,
                        responseCallback: IHttpResponseCallback
                    ) {
                        try {
                            val headerMap = headers?.mapKeys { it.key.toString() }
                                ?.mapValues { it.value.toString() } ?: emptyMap()
                            val queryMap = queryParams?.mapKeys { it.key.toString() }
                                ?.mapValues { it.value.toString() } ?: emptyMap()

                            val request =
                                HttpRequest(
                                    path,
                                    method,
                                    pathParams as Map<String, String>,
                                    headerMap,
                                    queryMap,
                                    body
                                )

                            scope.launch {
                                try {
                                    val response = handler.handleRequest(request)
                                    responseCallback.sendResponse(
                                        response.statusCode,
                                        response.headers,
                                        response.body,
                                        response.file,
                                        response.contentType
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error handling HTTP request for $path", e)
                                    responseCallback.sendResponse(
                                        500,
                                        emptyMap<Any?, Any?>(),
                                        "{\"error\": \"Internal server error: ${e.message}\"}".toByteArray(),
                                        null,
                                        "application/json"
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in HTTP endpoint callback for $path", e)
                            try {
                                responseCallback.sendResponse(
                                    500,
                                    emptyMap<Any?, Any?>().toMutableMap(),
                                    "{\"error\": \"Callback error: ${e.message}\"}".toByteArray(),
                                    null,
                                    "application/json"
                                )
                            } catch (callbackError: Exception) {
                                Log.e(TAG, "Failed to send error response", callbackError)
                            }
                        }
                    }
                }

                val success =
                    settingsProvider.registerHttpEndpoint(providerId, path, method, aidlCallback)
                if (success) {
                    endpointCallbacks[endpointKey] = aidlCallback
                    Log.i(TAG, "Registered HTTP endpoint: $method $path for provider $providerId")
                }
                continuation.resume(success)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to register HTTP endpoint: $providerId $method $path", e)
                continuation.resume(false)
            }
        }
    }

    suspend fun unregisterHttpEndpoint(providerId: String, path: String, method: String): Boolean {
        return try {
            val endpointKey = "${method.uppercase()}:$path"
            val success = settingsProvider.unregisterHttpEndpoint(providerId, path, method)
            if (success) {
                endpointCallbacks.remove(endpointKey)
                Log.i(TAG, "Unregistered HTTP endpoint: $method $path for provider $providerId")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister HTTP endpoint: $providerId $method $path", e)
            false
        }
    }

    suspend fun unregisterAllHttpEndpoints(providerId: String) {
        try {
            settingsProvider.unregisterAllHttpEndpoints(providerId)
            endpointCallbacks.clear()
            Log.i(TAG, "Unregistered all HTTP endpoints for provider: $providerId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister all HTTP endpoints for provider: $providerId", e)
        }
    }

    fun addBooleanListener(
        appId: String,
        category: String,
        key: String,
        listener: BooleanSettingListener
    ) {
        val settingKey = "$appId.$category.$key"
        val callback = createTypedCallback(settingKey, "boolean") { value ->
            try {
                listener.onSettingChanged(value.toBoolean())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to notify boolean listener for $settingKey", e)
            }
        }

        listenerCallbacks[settingKey] = callback
        settingsProvider.registerSettingListener(appId, category, key, "boolean", callback)
    }

    fun addStringListener(
        appId: String,
        category: String,
        key: String,
        listener: StringSettingListener
    ) {
        val settingKey = "$appId.$category.$key"
        val callback = createTypedCallback(settingKey, "string") { value ->
            try {
                listener.onSettingChanged(value)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to notify string listener for $settingKey", e)
            }
        }

        listenerCallbacks[settingKey] = callback
        settingsProvider.registerSettingListener(appId, category, key, "string", callback)
    }

    fun addIntListener(appId: String, category: String, key: String, listener: IntSettingListener) {
        val settingKey = "$appId.$category.$key"
        val callback = createTypedCallback(settingKey, "int") { value ->
            try {
                listener.onSettingChanged(value.toInt())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to notify int listener for $settingKey", e)
            }
        }

        listenerCallbacks[settingKey] = callback
        settingsProvider.registerSettingListener(appId, category, key, "int", callback)
    }

    fun addFloatListener(
        appId: String,
        category: String,
        key: String,
        listener: FloatSettingListener
    ) {
        val settingKey = "$appId.$category.$key"
        val callback = createTypedCallback(settingKey, "float") { value ->
            try {
                listener.onSettingChanged(value.toFloat())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to notify float listener for $settingKey", e)
            }
        }

        listenerCallbacks[settingKey] = callback
        settingsProvider.registerSettingListener(appId, category, key, "float", callback)
    }

    fun removeListener(appId: String, category: String, key: String, listener: Any) {
        val settingKey = "$appId.$category.$key"
        val callback = listenerCallbacks.remove(settingKey)
        if (callback != null) {
            settingsProvider.unregisterSettingListener(appId, category, key, callback)
        }
    }

    private fun createTypedCallback(
        settingKey: String,
        type: String,
        onChanged: (String) -> Unit
    ): ISettingsCallback {
        return object : ISettingsCallback.Stub() {
            override fun onSettingChanged(
                appId: String,
                category: String,
                key: String,
                value: String
            ) {
                onChanged(value)
            }

            override fun onSettingsRegistered(appId: String, category: String) {
            }

            override fun onError(message: String) {
                Log.e(TAG, "Listener callback error for $settingKey ($type): $message")
            }

            override fun onActionResult(
                appId: String,
                action: String,
                success: Boolean,
                message: String,
                data: Map<*, *>
            ) {
            }
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