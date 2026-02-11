package com.penumbraos.bridge_settings.android

import android.util.Log
import com.penumbraos.sdk.api.ShellClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

private const val TAG = "TemperatureController"
private const val TEMPERATURE_UPDATE_INTERVAL_MS = 30000L // 30 seconds

class TemperatureController(private val shellClient: ShellClient) {
    
    val temperatureFlow: Flow<Float> = flow {
        Log.i(TAG, "Temperature monitoring is disabled")
        // Log.i(TAG, "Starting temperature monitoring flow with ${TEMPERATURE_UPDATE_INTERVAL_MS}ms interval")
        // while (true) {
        //     try {
        //         val temperature = getCurrentTemperature()
        //         Log.i(TAG, "Emitting temperature: ${temperature}°C")
        //         emit(temperature)
        //     } catch (e: Exception) {
        //         Log.e(TAG, "Error during temperature monitoring", e)
        //         emit(0.0f) // Emit default value on error
        //     }
        //     delay(TEMPERATURE_UPDATE_INTERVAL_MS)
        // }
    }
    
    private val monitoringScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun getCurrentTemperature(): Float {
        return try {
            val result = shellClient.executeCommand("cat /sys/class/thermal/thermal_zone20/temp")
            
            if (result.isSuccess) {
                val tempString = result.output.trim()
                val tempMilliCelsius = tempString.toIntOrNull() ?: 0
                val tempCelsius = tempMilliCelsius / 1000.0f
                
                Log.d(TAG, "Current CPU temperature: ${tempCelsius}°C")
                tempCelsius
            } else {
                Log.w(TAG, "Failed to read temperature: exit code ${result.exitCode}, error: '${result.error}'")
                0.0f
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get device temperature", e)
            0.0f
        }
    }

    fun cleanup() {
        monitoringScope.cancel()
    }
}