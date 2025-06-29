package com.penumbraos.sdk.api

import com.penumbraos.bridge.ISttProvider
import com.penumbraos.bridge.ISttRecognitionListener
import com.penumbraos.sdk.api.types.SttRecognitionListener

class SttClient(private val provider: ISttProvider) {
    private var recognitionListener: ISttRecognitionListener? = null

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
