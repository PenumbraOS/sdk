package com.penumbraos.bridge_settings

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.penumbraos.bridge_settings.android.TemperatureController
import com.penumbraos.sdk.api.ShellClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "SettingsRegistry"

private const val SYSTEM_SETTINGS_APP_ID = "penumbra_system"

data class ActionResult(
    val success: Boolean,
    val message: String? = null,
    val data: Map<String, Any?>? = null,
    val logs: List<LogEntry>? = null
)

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val message: String
)

enum class LogLevel {
    INFO, WARNING, ERROR, DEBUG
}

interface SettingsActionProvider {
    suspend fun executeAction(action: String, params: Map<String, Any>): ActionResult
    fun getActionDefinitions(): Map<String, LocalActionDefinition>
}

class SettingsRegistry(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    shellClient: ShellClient
) {
    private val appSettings = ConcurrentHashMap<String, MutableMap<String, AppSettingsCategory>>()
    private val systemSettings = ConcurrentHashMap<String, Any?>()
    private val actionProviders = ConcurrentHashMap<String, SettingsActionProvider>()

    // Execution state tracking
    @Volatile
    private var currentExecutingAction: ExecutingAction? = null
    private val executionTimeoutHandler = Handler(Looper.getMainLooper())
    private var executionTimeoutRunnable: Runnable? = null

    companion object {
        private const val EXECUTION_TIMEOUT_MS = 30000L // 30 seconds
    }

    private fun startExecutionTimeout(appId: String, action: String) {
        clearState()

        executionTimeoutRunnable = Runnable {
            Log.w(TAG, "Action execution timeout: $appId.$action")
            currentExecutingAction = null

            // Broadcast timeout error
            registryScope.launch {
                sendAppEvent(
                    appId, "actionResult", mapOf<String, Any>(
                        "action" to action,
                        "success" to false,
                        "message" to "Action timed out after ${EXECUTION_TIMEOUT_MS / 1000} seconds",
                        "logs" to listOf(
                            mapOf<String, Any>(
                                "timestamp" to System.currentTimeMillis(),
                                "level" to "ERROR",
                                "message" to "Action execution timeout"
                            )
                        )
                    )
                )
            }
        }

        executionTimeoutHandler.postDelayed(executionTimeoutRunnable!!, EXECUTION_TIMEOUT_MS)
        Log.d(TAG, "Started execution timeout for: $appId.$action")
    }

    private fun clearState() {
        currentExecutingAction = null
        executionTimeoutRunnable?.let {
            executionTimeoutHandler.removeCallbacks(it)
            executionTimeoutRunnable = null
        }
    }


    private val humaneDisplayController = HumaneDisplayController(shellClient)
    private val temperatureController = TemperatureController(shellClient)
    private val registryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Reference to web server for broadcasting (set by SettingsService)
    private var webServer: SettingsWebServer? = null

    private val _settingsFlow = MutableStateFlow<Map<String, Map<String, Any?>>>(emptyMap())
    val settingsFlow: StateFlow<Map<String, Map<String, Any?>>> = _settingsFlow.asStateFlow()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        loadSavedSettings()
        initializeSystemSettingsSync()
        setupTemperatureMonitoring()
    }

    suspend fun initialize() {
        loadCurrentAndroidSettings()
        updateSettingsFlow()
    }

    fun setWebServer(webServer: SettingsWebServer) {
        this.webServer = webServer
        // Force an initial emission so WebServer gets current settings immediately
        updateSettingsFlow()
    }

    private fun loadSavedSettings() {
        try {
            sharedPreferences.all.filter { it.key.startsWith(SYSTEM_SETTINGS_APP_ID) }
                .forEach { (key, value) ->
                    if (!isAndroidSystemSetting(key)) {
                        systemSettings[key] = value
                    }
                }

            Log.i(TAG, "Loaded settings from SharedPreferences")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings from SharedPreferences", e)
        }
    }

    private fun initializeSystemSettingsSync() {
        // Only load synchronous settings in constructor
        try {
            // Audio settings
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            systemSettings["audio.volume"] =
                (currentVolume * 100 / maxVolume) // Convert to 0-100 scale
            systemSettings["audio.muted"] = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load sync Android settings", e)
        }
        updateSettingsFlow()
    }

    private suspend fun loadCurrentAndroidSettings() {
        try {
            // Refresh audio settings
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            systemSettings["audio.volume"] =
                (currentVolume * 100 / maxVolume) // Convert to 0-100 scale
            systemSettings["audio.muted"] = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)

            // Humane display controller settings
            try {
                val displayEnabled = humaneDisplayController.isDisplayEnabled()
                systemSettings["display.humane_enabled"] = displayEnabled
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Humane display state", e)
                systemSettings["display.humane_enabled"] = false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load current Android settings", e)
        }
    }

    private fun isAndroidSystemSetting(key: String): Boolean {
        return when (key) {
            "audio.volume", "audio.muted", "display.humane_enabled", "device.temperature" -> true
            else -> false
        }
    }

    private suspend fun applyAndroidSystemSetting(key: String, value: Any): Boolean {
        return try {
            when (key) {
                "audio.volume" -> {
                    val volume = when (value) {
                        is Number -> value.toInt().coerceIn(0, 100)
                        else -> return false
                    }
                    val audioManager =
                        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val systemVolume = (volume * maxVolume / 100).coerceIn(0, maxVolume)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, systemVolume, 0)
                    systemSettings["audio.volume"] = volume
                    Log.i(TAG, "Set audio volume to $systemVolume/$maxVolume (${volume}%)")
                    true
                }

                "audio.muted" -> {
                    val muted = when (value) {
                        is Boolean -> value
                        else -> return false
                    }
                    val audioManager =
                        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0
                    )
                    systemSettings["audio.muted"] = muted
                    Log.i(TAG, "Set audio muted to $muted")
                    true
                }

                "display.humane_enabled" -> {
                    val enabled = when (value) {
                        is Boolean -> value
                        else -> return false
                    }
                    Log.i(TAG, "Attempting to set Humane display state to $enabled")
                    val success = humaneDisplayController.setDisplayEnabled(enabled)
                    if (success) {
                        // Verify the actual state after the operation
                        val actualState = humaneDisplayController.isDisplayEnabled()
                        Log.i(
                            TAG,
                            "Humane display command succeeded. Actual state: $actualState, requested: $enabled"
                        )
                        // Update with the actual state, not the requested state
                        systemSettings["display.humane_enabled"] = actualState
                    } else {
                        Log.w(TAG, "Failed to update Humane display state to $enabled")
                    }
                    success
                }

                else -> {
                    Log.w(TAG, "Unknown Android system setting: $key")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply Android system setting $key = $value", e)
            false
        }
    }

    fun registerAppSettings(
        appId: String,
        category: String,
        definitions: Map<String, SettingDefinition>
    ) {
        if (appId.startsWith(SYSTEM_SETTINGS_APP_ID)) {
            throw IllegalArgumentException("Cannot register system settings")
        }

        Log.i(TAG, "Registering settings for app: $appId, category: $category")

        val appCategories = appSettings.getOrPut(appId) { mutableMapOf() }
        val settingsCategory = AppSettingsCategory(appId, category, definitions)

        definitions.forEach { (key, definition) ->
            val fullKey = "$appId.$category.$key"
            val savedValue = sharedPreferences.all[fullKey]
            if (savedValue != null) {
                settingsCategory.values[key] = savedValue
            } else {
                settingsCategory.values[key] = definition.defaultValue
            }
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

        // Save this specific app setting immediately
        saveAppSetting(appId, category, key, value)

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

    suspend fun updateSystemSetting(key: String, value: Any): Boolean {
        if (validateSystemSetting(key, value)) {
            // Apply the setting to Android system if it's a system setting
            val success = if (isAndroidSystemSetting(key)) {
                applyAndroidSystemSetting(key, value)
            } else {
                true // App-specific settings always succeed
            }

            if (success) {
                if (!isAndroidSystemSetting(key)) {
                    systemSettings[key] = value
                    saveSystemSetting(key, value)
                }

                updateSettingsFlow()
                Log.i(TAG, "Updated system setting: $key = $value")
                return true
            }
        }
        return false
    }

    fun getSystemSetting(key: String): Any? {
        return systemSettings[key]
    }

    fun getAllSystemSettings(): Map<String, Any?> {
        return systemSettings.toMap()
    }

    fun getAllSettings(): Map<String, Map<String, Any?>> {
        val result = mutableMapOf<String, Map<String, Any?>>()

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

        // Add current execution status
        getCurrentExecutionStatus()?.let { executionStatus ->
            result["executionStatus"] = executionStatus
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
            SettingType.ACTION -> value is String // Actions are treated as strings
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
            "audio.volume" -> value is Number && value.toInt() in 0..100
            "audio.muted" -> value is Boolean
            "display.humane_enabled" -> value is Boolean
            "device.temperature" -> value is Number // Read-only, but validate type
            else -> true // Allow unknown settings for extensibility
        }
    }

    private fun SharedPreferences.Editor.putValue(key: String, value: Any) {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is String -> putString(key, value)
            else -> putString(key, value.toString())
        }
    }

    private fun saveSystemSetting(key: String, value: Any) {
        val fullKey = "$SYSTEM_SETTINGS_APP_ID.$key"
        try {
            sharedPreferences.edit().apply {
                putValue(fullKey, value)
                apply()
            }
            Log.d(TAG, "Saved system setting: $fullKey = $value")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save system setting $fullKey", e)
        }
    }

    private fun saveAppSetting(appId: String, category: String, key: String, value: Any) {
        val fullKey = "$appId.$category.$key"
        try {
            sharedPreferences.edit().apply {
                putValue(fullKey, value)
                apply()
            }
            Log.d(TAG, "Saved app setting: $fullKey = $value")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save app setting $fullKey", e)
        }
    }


    suspend fun sendAppStatusUpdate(appId: String, component: String, payload: Map<String, Any>) {
        webServer?.broadcastAppStatusUpdate(appId, component, payload)
            ?: Log.w(TAG, "Cannot send app status update - web server not initialized")
    }

    suspend fun sendAppEvent(appId: String, eventType: String, payload: Map<String, Any>) {
        webServer?.broadcastAppEvent(appId, eventType, payload)
            ?: Log.w(TAG, "Cannot send app event - web server not initialized")
    }

    fun registerActionProvider(appId: String, provider: SettingsActionProvider) {
        actionProviders[appId] = provider
        Log.i(TAG, "Registered action provider for app: $appId")

        // Broadcast available actions to web UI
        registryScope.launch {
            val actions = provider.getActionDefinitions()
            sendAppEvent(appId, "actionsRegistered", mapOf("actions" to actions))
        }
    }

    fun unregisterActionProvider(appId: String) {
        actionProviders.remove(appId)
        Log.i(TAG, "Unregistered action provider for app: $appId")
    }

    suspend fun executeAction(
        appId: String,
        action: String,
        params: Map<String, Any>
    ): ActionResult {
        Log.i(TAG, "Executing action: $appId.$action with params: $params")

        currentExecutingAction = ExecutingAction(appId, action, params, System.currentTimeMillis())
        startExecutionTimeout(appId, action)

        val provider = actionProviders[appId]
        if (provider == null) {
            val errorResult = ActionResult(
                success = false,
                message = "No action provider registered for app: $appId",
                logs = listOf(
                    LogEntry(
                        level = LogLevel.ERROR,
                        message = "Action provider not found for $appId"
                    )
                )
            )

            // Broadcast error result
            sendAppEvent(
                appId, "actionResult", mapOf<String, Any>(
                    "action" to action,
                    "success" to false,
                    "message" to (errorResult.message ?: ""),
                    "logs" to (errorResult.logs ?: emptyList())
                )
            )

            clearState()

            return errorResult
        }

        return try {
            val result = provider.executeAction(action, params)
            Log.i(TAG, "Action $appId.$action completed. Success: ${result.success}")

            // Broadcast action result via WebSocket
            sendAppEvent(
                appId, "actionResult", mapOf<String, Any>(
                    "action" to action,
                    "success" to result.success,
                    "message" to (result.message ?: ""),
                    "data" to (result.data ?: emptyMap()),
                    "logs" to (result.logs ?: emptyList())
                )
            )

            clearState()

            result
        } catch (e: Exception) {
            Log.e(TAG, "Action execution failed: $appId.$action", e)
            val errorResult = ActionResult(
                success = false,
                message = "Action execution failed: ${e.message}",
                logs = listOf(LogEntry(level = LogLevel.ERROR, message = "Exception: ${e.message}"))
            )

            // Broadcast error result
            sendAppEvent(
                appId, "actionResult", mapOf<String, Any>(
                    "action" to action,
                    "success" to false,
                    "message" to (errorResult.message ?: ""),
                    "logs" to (errorResult.logs ?: emptyList())
                )
            )

            clearState()

            errorResult
        }
    }

    fun getActionProvider(appId: String): SettingsActionProvider? {
        return actionProviders[appId]
    }

    fun getCurrentExecutionStatus(): Map<String, Any>? {
        return currentExecutingAction?.let { action ->
            mapOf(
                "providerId" to action.providerId,
                "actionName" to action.actionName,
                "params" to action.params,
                "startTime" to action.startTime,
                "duration" to (System.currentTimeMillis() - action.startTime)
            )
        }
    }

    fun getAllActionProviders(): Map<String, SettingsActionProvider> {
        return actionProviders.toMap()
    }

    private fun setupTemperatureMonitoring() {
        registryScope.launch {
            temperatureController.temperatureFlow.collect { temperature ->
                systemSettings["device.temperature"] = temperature
                updateSettingsFlow()
            }
        }
    }

    private fun updateSettingsFlow() {
        _settingsFlow.value = getAllSettings()
    }
}