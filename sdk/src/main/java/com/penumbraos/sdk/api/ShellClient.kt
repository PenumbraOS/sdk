package com.penumbraos.sdk.api

import android.util.Log
import com.penumbraos.bridge.IShellCallback
import com.penumbraos.bridge.IShellProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ShellClient"

class ShellClient(private val provider: IShellProvider) {

    suspend fun executeCommand(
        command: String, 
        workingDirectory: String? = null
    ): ShellResult = withContext(Dispatchers.IO) {
        val result = CompletableDeferred<ShellResult>()
        val outputBuffer = StringBuilder()
        val errorBuffer = StringBuilder()

        val callback = object : IShellCallback.Stub() {
            override fun onOutput(output: String) {
                outputBuffer.appendLine(output)
            }

            override fun onError(error: String) {
                errorBuffer.appendLine(error)
            }

            override fun onComplete(exitCode: Int) {
                result.complete(
                    ShellResult(
                        exitCode = exitCode,
                        output = outputBuffer.toString().trim(),
                        error = errorBuffer.toString().trim()
                    )
                )
            }
        }

        try {
            provider.executeCommand(command, workingDirectory, callback)
            result.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command", e)
            ShellResult(
                exitCode = -1,
                output = "",
                error = "Command execution failed: ${e.message}"
            )
        }
    }

    suspend fun executeCommandWithTimeout(
        command: String,
        workingDirectory: String? = null,
        timeoutMs: Int = 30000
    ): ShellResult = withContext(Dispatchers.IO) {
        val result = CompletableDeferred<ShellResult>()
        val outputBuffer = StringBuilder()
        val errorBuffer = StringBuilder()

        val callback = object : IShellCallback.Stub() {
            override fun onOutput(output: String) {
                outputBuffer.appendLine(output)
            }

            override fun onError(error: String) {
                errorBuffer.appendLine(error)
            }

            override fun onComplete(exitCode: Int) {
                result.complete(
                    ShellResult(
                        exitCode = exitCode,
                        output = outputBuffer.toString().trim(),
                        error = errorBuffer.toString().trim()
                    )
                )
            }
        }

        try {
            provider.executeCommandWithTimeout(command, workingDirectory, timeoutMs, callback)
            result.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command with timeout", e)
            ShellResult(
                exitCode = -1,
                output = "",
                error = "Command execution failed: ${e.message}"
            )
        }
    }

    fun executeCommandAsync(
        command: String,
        workingDirectory: String? = null,
        onOutput: (String) -> Unit = {},
        onError: (String) -> Unit = {},
        onComplete: (Int) -> Unit = {}
    ) {
        val callback = object : IShellCallback.Stub() {
            override fun onOutput(output: String) {
                onOutput(output)
            }

            override fun onError(error: String) {
                onError(error)
            }

            override fun onComplete(exitCode: Int) {
                onComplete(exitCode)
            }
        }

        try {
            provider.executeCommand(command, workingDirectory, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing async command", e)
            onError("Command execution failed: ${e.message}")
            onComplete(-1)
        }
    }
}

data class ShellResult(
    val exitCode: Int,
    val output: String,
    val error: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}