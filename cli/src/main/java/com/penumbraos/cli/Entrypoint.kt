package com.penumbraos.cli

import android.util.Log
import kotlinx.coroutines.*
import kotlin.system.exitProcess

private const val TAG = "CLI"

/**
 * Main entry point for PenumbraOS CLI
 * 
 * Handles command routing to different subsystems
 */
class Entrypoint {
    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @JvmStatic
        fun main(args: Array<String>) {
            runBlocking {
                try {
                    run(args)
                } catch (e: Exception) {
                    Log.e(TAG, "CLI error", e)
                    println("Error: ${e.message}")
                    exitProcess(1)
                } finally {
                    scope.cancel()
                }
            }
            exitProcess(0)
        }

        suspend fun run(args: Array<String>) {
            if (args.isEmpty()) {
                showRootHelp()
                return
            }

            when (args[0].lowercase()) {
                "settings" -> {
                    val settingsCommand = SettingsCommand(scope)
                    settingsCommand.execute(args.drop(1).toTypedArray())
                }
                "help", "--help", "-h" -> showRootHelp()
                else -> {
                    println("Unknown command '${args[0]}'")
                    println("Use 'penumbra help' to see available commands.")
                    exitProcess(1)
                }
            }
        }

        private fun showRootHelp() {
            println(
                """
            PenumbraOS CLI
            
            Usage:
              penumbra <command> [options...]
              
            Available commands:
              settings    Manage system and app settings
              help        Show this help message
              
            Examples:
              penumbra settings list
              penumbra settings system audio.volume 75
              penumbra settings esim getProfiles
              
            For command-specific help, use: penumbra <command> help
            
        """.trimIndent()
            )
        }
    }
}