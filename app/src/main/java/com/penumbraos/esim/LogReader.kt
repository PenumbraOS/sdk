package com.penumbraos.esim

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG_TO_READ = "factoryService"
private const val CAPTURE_DURATION_S = 10

class LogReader {
    companion object {
        fun read() {
            val command = listOf("logcat", "-s", TAG_TO_READ)

            try {
                val process = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()

                val running = AtomicBoolean(true)

                println("\nPrinting logs for $CAPTURE_DURATION_S seconds\n")

                val logReaderThread = Thread {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        try {
                            var line: String? = null
                            while (running.get() && reader.readLine().also { line = it } != null) {
                                println(line)
                            }
                        } catch (e: IOException) {
                            if (running.get()) {
                                System.err.println("Error reading logcat output: ${e.message}")
                            }
                        } catch (e: Exception) {
                            System.err.println("An unexpected error occurred in log reader thread: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                }
                logReaderThread.name = "LogcatReaderThread"
                logReaderThread.start()

                Thread.sleep(CAPTURE_DURATION_S * 1000L)

                // Stop reading
                running.set(false)
                println("\nStopping log capture after $CAPTURE_DURATION_S seconds...")

                process.destroyForcibly()

                // Wait for the process to complete naturally
                val terminated = process.waitFor(5, TimeUnit.SECONDS)

                println("--- Log Output End ---")

                if (terminated) {
                    val exitCode = process.exitValue()
                    if (exitCode != 0) {
                        System.err.println("Logcat process exited with non-zero code: $exitCode")
                    }
                } else {
                    System.err.println("Logcat process did not terminate within timeout. Forcibly destroying...")
                    process.destroyForcibly()
                }
            } catch (e: Exception) {
                System.err.println("An unexpected error occurred: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}