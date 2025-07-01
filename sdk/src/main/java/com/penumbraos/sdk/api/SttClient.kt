package com.penumbraos.sdk.api

import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.penumbraos.bridge.ISttProvider
import com.penumbraos.sdk.api.types.SttRecognitionListener

class SttClient {
    lateinit var provider: ISttProvider

    /**
     * A hack to forcibly launch the Humane recognition service. This is necessary due to current pinitd Zygote limitations
     */
    fun launchListenerProcess(applicationContext: Context) {
        val intent = Intent()
        intent.component = ComponentName(
            "humane.voice.recognition",
            "humane.voice.recognition.HumaneRecognitionService"
        )

        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?
            ) {
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        }

        try {
            applicationContext.bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        } catch (e: Exception) {
            // Do nothing
        }
    }

    fun initialize(listener: SttRecognitionListener) {
        provider.initialize(listener)
    }

    fun startListening() {
        provider.startListening()
    }

    fun stopListening() {
        provider.stopListening()
    }

    fun cancel() {
        provider.cancel()
    }

    fun isRecognitionAvailable(): Boolean {
        return provider.isRecognitionAvailable()
    }

    fun destroy() {
        cancel()
    }
}
