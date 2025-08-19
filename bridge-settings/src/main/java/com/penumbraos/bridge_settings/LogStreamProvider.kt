package com.penumbraos.bridge_settings

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "LogStreamProvider"

data class LogStreamEntry(
    val level: String,
    val tag: String,
    val message: String,
    val timestamp: Long
)

class LogStreamProvider {
    private val _logFlow = MutableSharedFlow<LogStreamEntry>()
    val logFlow: SharedFlow<LogStreamEntry> = _logFlow.asSharedFlow()
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isStreaming = AtomicBoolean(false)
    private var streamJob: Job? = null
    
    private val logcatDateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    
    fun startLogStream() {
        if (isStreaming.get()) {
            Log.w(TAG, "Log stream already running")
            return
        }
        
        Log.i(TAG, "Starting log stream")
        isStreaming.set(true)
        
        streamJob = scope.launch {
            try {
                // Use logcat to stream Android logs
                val process = ProcessBuilder()
                    .command("logcat", "-v", "time", "*:V")
                    .redirectErrorStream(true)
                    .start()
                    
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                
                var line: String? = null
                while (isStreaming.get() && reader.readLine().also { line = it } != null) {
                    line?.let { logLine ->
                        parseLogLine(logLine)?.let { entry ->
                            _logFlow.emit(entry)
                        }
                    }
                }
                
                process.destroyForcibly()
                Log.i(TAG, "Log stream stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error in log stream", e)
                isStreaming.set(false)
            }
        }
    }
    
    fun stopLogStream() {
        Log.i(TAG, "Stopping log stream")
        isStreaming.set(false)
        streamJob?.cancel()
        streamJob = null
    }
    
    fun isStreamingActive(): Boolean = isStreaming.get()
    
    private fun parseLogLine(line: String): LogStreamEntry? {
        try {
            // Android logcat format: MM-DD HH:MM:SS.mmm LEVEL/TAG(  PID): message
            // Example: 08-18 14:56:11.324 D/PowerUI.Notification( 5737): dismissing low battery warning: level=100
            
            val logcatRegex = Regex("""^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEF])/([^(]+)\(\s*(\d+)\):\s*(.*)$""")
            val match = logcatRegex.find(line)
            
            return if (match != null) {
                val timestampStr = match.groupValues[1]
                val level = match.groupValues[2]
                val tag = match.groupValues[3].trim()
                val pid = match.groupValues[4]
                val message = match.groupValues[5]
                
                // Parse timestamp to milliseconds (add current year since logcat doesn't include it)
                val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                val fullTimestampStr = "$currentYear-$timestampStr"
                val timestamp = try {
                    val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                    fullDateFormat.parse(fullTimestampStr)?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
                
                LogStreamEntry(
                    level = level,
                    tag = tag,
                    message = message,
                    timestamp = timestamp
                )
            } else {
                // If parsing fails, create a generic entry
                LogStreamEntry(
                    level = "I",
                    tag = "Raw",
                    message = line,
                    timestamp = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            // If parsing fails, create a generic entry
            return LogStreamEntry(
                level = "I",
                tag = "Raw", 
                message = line,
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    fun destroy() {
        stopLogStream()
        scope.cancel()
    }
}