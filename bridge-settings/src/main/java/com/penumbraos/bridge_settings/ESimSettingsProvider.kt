package com.penumbraos.bridge_settings

import android.util.Log
import com.penumbraos.bridge.types.EsimProfile
import com.penumbraos.sdk.api.EsimClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private const val TAG = "ESimSettingsProvider"

class ESimSettingsProvider(
    private val esimClient: EsimClient,
    private val settingsRegistry: SettingsRegistry
) : SettingsActionProvider {

    private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun executeAction(action: String, params: Map<String, Any>): ActionResult {
        Log.i(TAG, "Executing eSIM action: $action with params: $params")

        return try {
            when (action) {
                "getProfiles" -> getProfilesAction()
                "getActiveProfile" -> getActiveProfileAction()
                "getEid" -> getEidAction()
                "enableProfile" -> enableProfileAction(params)
                "disableProfile" -> disableProfileAction(params)
                "deleteProfile" -> deleteProfileAction(params)
                "setNickname" -> setNicknameAction(params)
                "downloadProfile" -> downloadProfileAction(params)
                "downloadAndEnableProfile" -> downloadAndEnableProfileAction(params)
                else -> ActionResult(
                    success = false,
                    message = "Unknown eSIM action: $action",
                    logs = listOf(
                        LogEntry(
                            level = LogLevel.ERROR,
                            message = "Unknown action: $action"
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing eSIM action $action", e)
            ActionResult(
                success = false,
                message = "eSIM action failed: ${e.message}",
                logs = listOf(
                    LogEntry(level = LogLevel.ERROR, message = "Exception in $action: ${e.message}")
                )
            )
        }
    }

    override fun getActionDefinitions(): Map<String, ActionDefinition> {
        return mapOf(
            "getProfiles" to ActionDefinition(
                key = "getProfiles",
                displayText = "List eSIM Profiles",
                description = "Retrieve all eSIM profiles on the device"
            ),
            "getActiveProfile" to ActionDefinition(
                key = "getActiveProfile",
                displayText = "Get Active Profile",
                description = "Get the currently active eSIM profile"
            ),
            "getEid" to ActionDefinition(
                key = "getEid",
                displayText = "Get Device EID",
                description = "Retrieve the device's embedded identity document (EID)"
            ),
            "enableProfile" to ActionDefinition(
                key = "enableProfile",
                displayText = "Enable Profile",
                parameters = listOf(
                    ActionParameter(
                        "iccid",
                        SettingType.STRING,
                        required = true,
                        description = "Profile ICCID to enable"
                    )
                ),
                description = "Enable an eSIM profile by ICCID"
            ),
            "disableProfile" to ActionDefinition(
                key = "disableProfile",
                displayText = "Disable Profile",
                parameters = listOf(
                    ActionParameter(
                        "iccid",
                        SettingType.STRING,
                        required = true,
                        description = "Profile ICCID to disable"
                    )
                ),
                description = "Disable an eSIM profile by ICCID"
            ),
            "deleteProfile" to ActionDefinition(
                key = "deleteProfile",
                displayText = "Delete Profile",
                parameters = listOf(
                    ActionParameter(
                        "iccid",
                        SettingType.STRING,
                        required = true,
                        description = "Profile ICCID to delete"
                    )
                ),
                description = "Permanently delete an eSIM profile"
            ),
            "setNickname" to ActionDefinition(
                key = "setNickname",
                displayText = "Set Profile Nickname",
                parameters = listOf(
                    ActionParameter(
                        "iccid",
                        SettingType.STRING,
                        required = true,
                        description = "Profile ICCID"
                    ),
                    ActionParameter(
                        "nickname",
                        SettingType.STRING,
                        required = true,
                        description = "New nickname for the profile"
                    )
                ),
                description = "Set a custom nickname for an eSIM profile"
            ),
            "downloadProfile" to ActionDefinition(
                key = "downloadProfile",
                displayText = "Download Profile",
                parameters = listOf(
                    ActionParameter(
                        "activationCode",
                        SettingType.STRING,
                        required = true,
                        description = "eSIM activation code or QR code data"
                    )
                ),
                description = "Download a new eSIM profile from activation code"
            ),
            "downloadAndEnableProfile" to ActionDefinition(
                key = "downloadAndEnableProfile",
                displayText = "Download & Enable Profile",
                parameters = listOf(
                    ActionParameter(
                        "activationCode",
                        SettingType.STRING,
                        required = true,
                        description = "eSIM activation code or QR code data"
                    )
                ),
                description = "Download and immediately enable a new eSIM profile"
            )
        )
    }

    private suspend fun getProfilesAction(): ActionResult {
        return try {
            sendStatusUpdate("Retrieving eSIM profiles...")

            val profiles = esimClient.getProfiles()
            val profilesData = profiles.map { profile ->
                mapOf<String, Any>(
                    "iccid" to profile.iccid,
                    "profileState" to profile.profileState,
                    "profileName" to (profile.profileName ?: ""),
                    "profileNickname" to (profile.profileNickname ?: ""),
                    "serviceProviderName" to (profile.serviceProviderName ?: ""),
                    "index" to profile.index,
                    "isEnabled" to profile.isEnabled,
                    "isDisabled" to profile.isDisabled,
                    "displayName" to getProfileDisplayName(profile)
                )
            }

            val logs = mutableListOf<LogEntry>()
            logs.add(
                LogEntry(
                    level = LogLevel.INFO,
                    message = "Found ${profiles.size} eSIM profiles"
                )
            )

            profiles.forEachIndexed { index, profile ->
                val displayName = getProfileDisplayName(profile)
                val status = when {
                    profile.isEnabled -> "ENABLED"
                    profile.isDisabled -> "DISABLED"
                    else -> profile.profileState
                }
                logs.add(
                    LogEntry(
                        level = LogLevel.INFO,
                        message = "[$index] $displayName (${profile.iccid}) - $status"
                    )
                )
            }

            ActionResult(
                success = true,
                message = "Retrieved ${profiles.size} eSIM profiles",
                data = mapOf("profiles" to profilesData),
                logs = logs
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "Failed to get eSIM profiles: ${e.message}",
                logs = listOf(
                    LogEntry(
                        level = LogLevel.ERROR,
                        message = "getProfiles failed: ${e.message}"
                    )
                )
            )
        }
    }

    private suspend fun getActiveProfileAction(): ActionResult {
        return try {
            sendStatusUpdate("Getting active eSIM profile...")

            val activeProfile = esimClient.getActiveProfile()

            if (activeProfile != null) {
                val profileData = mapOf(
                    "iccid" to activeProfile.iccid,
                    "profileState" to activeProfile.profileState,
                    "profileName" to (activeProfile.profileName ?: ""),
                    "profileNickname" to (activeProfile.profileNickname ?: ""),
                    "serviceProviderName" to (activeProfile.serviceProviderName ?: ""),
                    "index" to activeProfile.index,
                    "isEnabled" to activeProfile.isEnabled,
                    "isDisabled" to activeProfile.isDisabled,
                    "displayName" to getProfileDisplayName(activeProfile)
                )

                ActionResult(
                    success = true,
                    message = "Active profile: ${getProfileDisplayName(activeProfile)}",
                    data = mapOf("activeProfile" to profileData),
                    logs = listOf(
                        LogEntry(
                            level = LogLevel.INFO,
                            message = "Active profile: ${getProfileDisplayName(activeProfile)} (${activeProfile.iccid})"
                        )
                    )
                )
            } else {
                ActionResult(
                    success = true,
                    message = "No active eSIM profile",
                    data = mapOf("activeProfile" to null),
                    logs = listOf(
                        LogEntry(
                            level = LogLevel.INFO,
                            message = "No active eSIM profile found"
                        )
                    )
                )
            }
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "Failed to get active eSIM profile: ${e.message}",
                logs = listOf(
                    LogEntry(
                        level = LogLevel.ERROR,
                        message = "getActiveProfile failed: ${e.message}"
                    )
                )
            )
        }
    }

    private suspend fun getEidAction(): ActionResult {
        return try {
            sendStatusUpdate("Getting device EID...")

            val eid = esimClient.getEid()

            ActionResult(
                success = true,
                message = "Device EID retrieved",
                data = mapOf("eid" to eid),
                logs = listOf(LogEntry(level = LogLevel.INFO, message = "Device EID: $eid"))
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "Failed to get device EID: ${e.message}",
                logs = listOf(
                    LogEntry(
                        level = LogLevel.ERROR,
                        message = "getEid failed: ${e.message}"
                    )
                )
            )
        }
    }

    private suspend fun enableProfileAction(params: Map<String, Any>): ActionResult {
        val iccid = params["iccid"] as? String
        if (iccid.isNullOrBlank()) {
            return ActionResult(
                success = false,
                message = "ICCID parameter is required",
                logs = listOf(
                    LogEntry(
                        level = LogLevel.ERROR,
                        message = "Missing required parameter: iccid"
                    )
                )
            )
        }

        return try {
            sendStatusUpdate("Enabling eSIM profile $iccid...")

            val result = esimClient.enableProfile(iccid)

            ActionResult(
                success = result.success,
                message = if (result.success) "Profile enabled successfully" else "Failed to enable profile: ${result.result}",
                data = mapOf(
                    "operation" to result.operation,
                    "success" to result.success,
                    "result" to result.result
                ),
                logs = listOf(
                    LogEntry(
                        level = if (result.success) LogLevel.INFO else LogLevel.ERROR,
                        message = "Enable profile $iccid: ${if (result.success) "SUCCESS" else "FAILED - ${result.result}"}"
                    )
                )
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "Failed to enable profile: ${e.message}",
                logs = listOf(
                    LogEntry(
                        level = LogLevel.ERROR,
                        message = "enableProfile failed: ${e.message}"
                    )
                )
            )
        }
    }

    private suspend fun disableProfileAction(params: Map<String, Any>): ActionResult {
        val iccid = params["iccid"] as? String
        if (iccid.isNullOrBlank()) {
            return ActionResult(
                success = false,
                message = "ICCID parameter is required",
                logs = listOf(
                    LogEntry(
                        level = LogLevel.ERROR,
                        message = "Missing required parameter: iccid"
                    )
                )
            )
        }

        return try {
            sendStatusUpdate("Disabling eSIM profile $iccid...")

            val result = esimClient.disableProfile(iccid)

            ActionResult(
                success = result.success,
                message = if (result.success) "Profile disabled successfully" else "Failed to disable profile: ${result.result}",
                data = mapOf(
                    "operation" to result.operation,
                    "success" to result.success,
                    "result" to result.result
                ),
                logs = listOf(
                    LogEntry(
                        level = if (result.success) LogLevel.INFO else LogLevel.ERROR,
                        message = "Disable profile $iccid: ${if (result.success) "SUCCESS" else "FAILED - ${result.result}"}"
                    )
                )
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "Failed to disable profile: ${e.message}",
                logs = listOf(
                    LogEntry(
                        level = LogLevel.ERROR,
                        message = "disableProfile failed: ${e.message}"
                    )
                )
            )
        }
    }

    private suspend fun deleteProfileAction(params: Map<String, Any>): ActionResult {
        val iccid = params["iccid"] as? String
        if (iccid.isNullOrBlank()) {
            return ActionResult(
                success = false,
                message = "ICCID parameter is required",
                logs = listOf(
                    LogEntry(
                        level = LogLevel.ERROR,
                        message = "Missing required parameter: iccid"
                    )
                )
            )
        }

        return try {
            sendStatusUpdate("Deleting eSIM profile $iccid...")

            val result = esimClient.deleteProfile(iccid)

            ActionResult(
                success = result.success,
                message = if (result.success) "Profile deleted successfully" else "Failed to delete profile: ${result.result}",
                data = mapOf(
                    "operation" to result.operation,
                    "success" to result.success,
                    "result" to result.result
                ),
                logs = listOf(
                    LogEntry(
                        level = if (result.success) LogLevel.INFO else LogLevel.ERROR,
                        message = "Delete profile $iccid: ${if (result.success) "SUCCESS" else "FAILED - ${result.result}"}"
                    )
                )
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "Failed to delete profile: ${e.message}",
                logs = listOf(
                    LogEntry(
                        level = LogLevel.ERROR,
                        message = "deleteProfile failed: ${e.message}"
                    )
                )
            )
        }
    }

    private suspend fun setNicknameAction(params: Map<String, Any>): ActionResult {
        val iccid = params["iccid"] as? String
        val nickname = params["nickname"] as? String

        if (iccid.isNullOrBlank()) {
            return ActionResult(
                success = false,
                message = "ICCID parameter is required",
                logs = listOf(
                    LogEntry(
                        level = LogLevel.ERROR,
                        message = "Missing required parameter: iccid"
                    )
                )
            )
        }

        if (nickname.isNullOrBlank()) {
            return ActionResult(
                success = false,
                message = "Nickname parameter is required",
                logs = listOf(
                    LogEntry(
                        level = LogLevel.ERROR,
                        message = "Missing required parameter: nickname"
                    )
                )
            )
        }

        return try {
            sendStatusUpdate("Setting nickname for eSIM profile $iccid...")

            val result = esimClient.setNickname(iccid, nickname)

            ActionResult(
                success = result.success,
                message = if (result.success) "Nickname set successfully" else "Failed to set nickname: ${result.result}",
                data = mapOf(
                    "operation" to result.operation,
                    "success" to result.success,
                    "result" to result.result,
                    "iccid" to iccid,
                    "nickname" to nickname
                ),
                logs = listOf(
                    LogEntry(
                        level = if (result.success) LogLevel.INFO else LogLevel.ERROR,
                        message = "Set nickname '$nickname' for profile $iccid: ${if (result.success) "SUCCESS" else "FAILED - ${result.result}"}"
                    )
                )
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "Failed to set nickname: ${e.message}",
                logs = listOf(
                    LogEntry(
                        level = LogLevel.ERROR,
                        message = "setNickname failed: ${e.message}"
                    )
                )
            )
        }
    }

    private suspend fun downloadProfileAction(params: Map<String, Any>): ActionResult {
        val activationCode = params["activationCode"] as? String
        if (activationCode.isNullOrBlank()) {
            return ActionResult(
                success = false,
                message = "Activation code parameter is required",
                logs = listOf(
                    LogEntry(
                        level = LogLevel.ERROR,
                        message = "Missing required parameter: activationCode"
                    )
                )
            )
        }

        return try {
            sendStatusUpdate("Downloading eSIM profile...")
            sendStatusUpdate("Activation code: $activationCode")

            val result = esimClient.downloadProfile(activationCode)

            ActionResult(
                success = result.success,
                message = if (result.success) "Profile downloaded successfully" else "Failed to download profile: ${result.result}",
                data = mapOf(
                    "operation" to result.operation,
                    "success" to result.success,
                    "result" to result.result,
                    "activationCode" to activationCode
                ),
                logs = listOf(
                    LogEntry(level = LogLevel.INFO, message = "Starting profile download..."),
                    LogEntry(
                        level = if (result.success) LogLevel.INFO else LogLevel.ERROR,
                        message = "Download profile: ${if (result.success) "SUCCESS" else "FAILED - ${result.result}"}"
                    )
                )
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "Failed to download profile: ${e.message}",
                logs = listOf(
                    LogEntry(
                        level = LogLevel.ERROR,
                        message = "downloadProfile failed: ${e.message}"
                    )
                )
            )
        }
    }

    private suspend fun downloadAndEnableProfileAction(params: Map<String, Any>): ActionResult {
        val activationCode = params["activationCode"] as? String
        if (activationCode.isNullOrBlank()) {
            return ActionResult(
                success = false,
                message = "Activation code parameter is required",
                logs = listOf(
                    LogEntry(
                        level = LogLevel.ERROR,
                        message = "Missing required parameter: activationCode"
                    )
                )
            )
        }

        return try {
            sendStatusUpdate("Downloading and enabling eSIM profile...")
            sendStatusUpdate("Activation code: $activationCode")

            val result = esimClient.downloadAndEnableProfile(activationCode)

            ActionResult(
                success = result.success,
                message = if (result.success) "Profile downloaded and enabled successfully" else "Failed to download and enable profile: ${result.result}",
                data = mapOf(
                    "operation" to result.operation,
                    "success" to result.success,
                    "result" to result.result,
                    "activationCode" to activationCode
                ),
                logs = listOf(
                    LogEntry(
                        level = LogLevel.INFO,
                        message = "Starting profile download and enable..."
                    ),
                    LogEntry(
                        level = if (result.success) LogLevel.INFO else LogLevel.ERROR,
                        message = "Download and enable profile: ${if (result.success) "SUCCESS" else "FAILED - ${result.result}"}"
                    )
                )
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "Failed to download and enable profile: ${e.message}",
                logs = listOf(
                    LogEntry(
                        level = LogLevel.ERROR,
                        message = "downloadAndEnableProfile failed: ${e.message}"
                    )
                )
            )
        }
    }

    private fun getProfileDisplayName(profile: EsimProfile): String {
        return when {
            !profile.profileNickname.isNullOrBlank() -> profile.profileNickname
            !profile.profileName.isNullOrBlank() -> profile.profileName
            !profile.serviceProviderName.isNullOrBlank() -> profile.serviceProviderName
            else -> "Unknown Profile"
        }
    }

    private suspend fun sendStatusUpdate(message: String) {
        Log.d(TAG, message)
        settingsRegistry.sendAppStatusUpdate(
            "esim", "provider", mapOf<String, Any>(
                "status" to message,
                "timestamp" to System.currentTimeMillis()
            )
        )
    }
}