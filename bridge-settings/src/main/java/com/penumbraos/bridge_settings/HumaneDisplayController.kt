package com.penumbraos.bridge_settings

import android.util.Log
import com.penumbraos.sdk.api.ShellClient

private const val TAG = "HumaneDisplayController"

class HumaneDisplayController(private val shellClient: ShellClient) {

    suspend fun isDisplayEnabled(): Boolean {
        return try {
            val result = shellClient.executeCommand("settings get global stay_on_while_plugged_in")
            val output = result.output.trim()

            val displayEnabled = output == "3"
            Log.d(
                TAG,
                "Current Humane display state: enabled=$displayEnabled (stay_on_while_plugged_in=$output)"
            )
            displayEnabled
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Humane display state", e)
            false // Default to disabled
        }
    }

    suspend fun setDisplayEnabled(enabled: Boolean): Boolean {
        return try {
            if (enabled) {
                Log.i(TAG, "Enabling Humane display")

                val disableResult = shellClient.executeCommandWithTimeout(
                    "cmd power disable-humane-display-controller",
                    timeoutMs = 10000
                )
                if (!disableResult.isSuccess) {
                    Log.w(
                        TAG,
                        "Failed to disable Humane display controller, exit code: ${disableResult.exitCode}, error: '${disableResult.error}', stdout: '${disableResult.output}'"
                    )
                    return false
                }

                val settingsResult = shellClient.executeCommand(
                    "settings put global stay_on_while_plugged_in 3"
                )
                if (!settingsResult.isSuccess) {
                    Log.w(
                        TAG,
                        "Failed to set stay_on_while_plugged_in, exit code: ${settingsResult.exitCode}, error: '${settingsResult.error}'"
                    )
                    return false
                }
            } else {
                Log.i(TAG, "Disabling Humane display")

                val settingsResult = shellClient.executeCommand(
                    "settings put global stay_on_while_plugged_in /dev/null"
                )
                if (!settingsResult.isSuccess) {
                    Log.w(
                        TAG,
                        "Failed to set stay_on_while_plugged_in to /dev/null, exit code: ${settingsResult.exitCode}, error: '${settingsResult.error}'"
                    )
                    return false
                }

                val enableResult = shellClient.executeCommandWithTimeout(
                    "cmd power enable-humane-display-controller",
                    timeoutMs = 10000
                )
                if (!enableResult.isSuccess) {
                    Log.w(
                        TAG,
                        "Failed to enable Humane display controller, exit code: ${enableResult.exitCode}, error: '${enableResult.error}', stdout: '${enableResult.output}'"
                    )
                    return false
                }
            }

            Log.i(TAG, "Set Humane display enabled to $enabled")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set Humane display state to $enabled", e)
            false
        }
    }
}