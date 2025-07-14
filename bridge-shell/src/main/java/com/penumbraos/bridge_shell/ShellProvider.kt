package com.penumbraos.bridge_shell

import android.util.Log
import com.penumbraos.bridge.IShellCallback
import com.penumbraos.bridge.IShellProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

private const val TAG = "ShellProvider"
private const val DEFAULT_TIMEOUT_MS = 30000

class ShellProvider : IShellProvider.Stub() {
    
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun executeCommand(
        command: String,
        workingDirectory: String?,
        callback: IShellCallback
    ) {
        executeCommandWithTimeout(command, workingDirectory, DEFAULT_TIMEOUT_MS, callback)
    }

    override fun executeCommandWithTimeout(
        command: String,
        workingDirectory: String?,
        timeoutMs: Int,
        callback: IShellCallback
    ) {
        scope.launch {
            executeShellCommand(command, workingDirectory, timeoutMs, callback)
        }
    }

    private suspend fun executeShellCommand(
        command: String,
        workingDirectory: String?,
        timeoutMs: Int,
        callback: IShellCallback
    ) {
        Log.d(TAG, "Executing command: $command")
        
        try {
            val processBuilder = ProcessBuilder("/bin/sh", "-c", command)
            
            workingDirectory?.let { wd ->
                val workDir = File(wd)
                if (workDir.exists() && workDir.isDirectory) {
                    processBuilder.directory(workDir)
                } else {
                    safeCallback {
                        callback.onError("Working directory does not exist: $wd")
                    }
                    return
                }
            }

            val process = processBuilder.start()
            
            val result = withTimeoutOrNull(timeoutMs.toLong()) {
                val outputReader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))

                val outputThread = Thread {
                    try {
                        outputReader.useLines { lines ->
                            lines.forEach { line ->
                                safeCallback {
                                    callback.onOutput(line)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading output", e)
                    }
                }

                val errorThread = Thread {
                    try {
                        errorReader.useLines { lines ->
                            lines.forEach { line ->
                                safeCallback {
                                    callback.onError(line)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading error stream", e)
                    }
                }

                outputThread.start()
                errorThread.start()

                val exitCode = process.waitFor()
                
                outputThread.join()
                errorThread.join()
                
                exitCode
            }

            if (result != null) {
                safeCallback {
                    callback.onComplete(result)
                }
            } else {
                process.destroyForcibly()
                safeCallback {
                    callback.onError("Command timed out after ${timeoutMs}ms")
                    callback.onComplete(-1)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command", e)
            safeCallback {
                callback.onError("Command execution failed: ${e.message}")
                callback.onComplete(-1)
            }
        }
    }

    private inline fun safeCallback(operation: () -> Unit): Boolean {
        return try {
            operation()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Exception in callback", e)
            false
        }
    }
}