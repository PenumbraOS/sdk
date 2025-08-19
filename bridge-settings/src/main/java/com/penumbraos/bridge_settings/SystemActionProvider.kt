package com.penumbraos.bridge_settings

import android.util.Log

private const val TAG = "SystemActionProvider"

class SystemActionProvider(
    private val logStreamProvider: LogStreamProvider
) : SettingsActionProvider {
    
    override suspend fun executeAction(action: String, params: Map<String, Any>): ActionResult {
        Log.i(TAG, "Executing system action: $action with params: $params")
        
        return try {
            when (action.lowercase()) {
                "startlogstream" -> startLogStreamAction()
                "stoplogstream" -> stopLogStreamAction()
                else -> ActionResult(
                    success = false,
                    message = "Unknown system action: $action",
                    logs = listOf(
                        LogEntry(
                            level = LogLevel.ERROR,
                            message = "Unknown system action: $action"
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing system action: $action", e)
            ActionResult(
                success = false,
                message = "System action failed: ${e.message}",
                logs = listOf(
                    LogEntry(
                        level = LogLevel.ERROR,
                        message = "System action execution error: ${e.message}"
                    )
                )
            )
        }
    }
    
    override fun getActionDefinitions(): Map<String, LocalActionDefinition> {
        return mapOf(
            "startLogStream" to LocalActionDefinition(
                key = "startLogStream",
                displayText = "Start Log Stream",
                description = "Start streaming system logs in real-time",
                parameters = emptyList()
            ),
            "stopLogStream" to LocalActionDefinition(
                key = "stopLogStream",
                displayText = "Stop Log Stream", 
                description = "Stop the real-time log stream",
                parameters = emptyList()
            ),
        )
    }
    
    private fun startLogStreamAction(): ActionResult {
        return try {
            if (logStreamProvider.isStreamingActive()) {
                ActionResult(
                    success = true,
                    message = "Log stream is already active",
                    logs = listOf(
                        LogEntry(
                            level = LogLevel.INFO,
                            message = "Log stream already running"
                        )
                    )
                )
            } else {
                logStreamProvider.startLogStream()
                ActionResult(
                    success = true,
                    message = "Log stream started successfully",
                    logs = listOf(
                        LogEntry(
                            level = LogLevel.INFO,
                            message = "Log stream started"
                        )
                    )
                )
            }
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "Failed to start log stream: ${e.message}",
                logs = listOf(
                    LogEntry(
                        level = LogLevel.ERROR,
                        message = "Failed to start log stream: ${e.message}"
                    )
                )
            )
        }
    }
    
    private fun stopLogStreamAction(): ActionResult {
        return try {
            if (!logStreamProvider.isStreamingActive()) {
                ActionResult(
                    success = true,
                    message = "Log stream is already stopped",
                    logs = listOf(
                        LogEntry(
                            level = LogLevel.INFO,
                            message = "Log stream already stopped"
                        )
                    )
                )
            } else {
                logStreamProvider.stopLogStream()
                ActionResult(
                    success = true,
                    message = "Log stream stopped successfully",
                    logs = listOf(
                        LogEntry(
                            level = LogLevel.INFO,
                            message = "Log stream stopped"
                        )
                    )
                )
            }
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "Failed to stop log stream: ${e.message}",
                logs = listOf(
                    LogEntry(
                        level = LogLevel.ERROR,
                        message = "Failed to stop log stream: ${e.message}"
                    )
                )
            )
        }
    }
}