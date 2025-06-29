package com.penumbraos.sdk.api

import android.os.Bundle
import com.penumbraos.bridge.ISttProvider
import com.penumbraos.bridge.ISttRecognitionListener

class SttClient(private val provider: ISttProvider) {

    fun startListening(listener: ISttRecognitionListener) {
        provider.startListening(listener)
    }

    fun stopListening(listener: ISttRecognitionListener) {
        provider.stopListening(listener)
    }

    fun cancel(listener: ISttRecognitionListener) {
        provider.cancel(listener)
    }

    fun isRecognitionAvailable(): Boolean {
        return provider.isRecognitionAvailable()
    }
}
