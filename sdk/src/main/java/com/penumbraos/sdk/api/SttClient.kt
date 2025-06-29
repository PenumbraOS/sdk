package com.penumbraos.sdk.api

import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.penumbraos.bridge.ISttProvider
import com.penumbraos.bridge.ISttRecognitionListener
import com.penumbraos.sdk.api.types.SttRecognitionListener

class SttClient {
    private var recognitionListener: ISttRecognitionListener? = null
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

    fun setRecognitionListener(listener: SttRecognitionListener) {
        this.recognitionListener = listener
    }

    fun startListening() {
        if (recognitionListener == null) {
            throw IllegalStateException("Recognition listener not set")
        }
        provider.startListening(recognitionListener)
    }

    fun stopListening() {
        if (recognitionListener == null) {
            throw IllegalStateException("Recognition listener not set")
        }
        provider.stopListening(recognitionListener)
    }

    fun cancel() {
        if (recognitionListener == null) {
            throw IllegalStateException("Recognition listener not set")
        }
        provider.cancel(recognitionListener)
    }

    fun isRecognitionAvailable(): Boolean {
        return provider.isRecognitionAvailable()
    }

    fun destroy() {
        recognitionListener = null
    }
}
