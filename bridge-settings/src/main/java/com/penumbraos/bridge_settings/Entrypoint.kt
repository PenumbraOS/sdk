package com.penumbraos.bridge_settings

import android.os.Looper
import android.util.Log
import kotlin.system.exitProcess

private const val TAG = "SettingsEntrypoint"

class Entrypoint {
    companion object {
        private var settingsService: SettingsService? = null

        @JvmStatic
        fun main(args: Array<String>) {
            Looper.prepare()
            Log.i(TAG, "Starting Settings Service entrypoint")

            try {
                settingsService = SettingsService()
                settingsService?.start()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Settings Service", e)
                exitProcess(1)
            }

            Looper.loop()
        }

        @JvmStatic
        fun stop() {
            Log.i(TAG, "Stopping Settings Service")
            settingsService?.stop()
            settingsService = null
        }

    }
}