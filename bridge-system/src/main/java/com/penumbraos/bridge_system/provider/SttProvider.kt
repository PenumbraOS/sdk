package com.penumbraos.bridge_system.provider

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.penumbraos.bridge.ISttProvider
import com.penumbraos.bridge.ISttRecognitionListener

private const val TAG = "SttProvider"

class SttProvider(private val context: Context, looper: Looper) : ISttProvider.Stub() {

    private val mainThreadHandler = Handler(looper)

    private var speechRecognizer: SpeechRecognizer? = null
    private var currentListener: ISttRecognitionListener? = null

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            currentListener?.onReadyForSpeech(params)
        }

        override fun onBeginningOfSpeech() {
            currentListener?.onBeginningOfSpeech()
        }

        override fun onRmsChanged(rmsdB: Float) {
            currentListener?.onRmsChanged(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            if (buffer != null) {
                currentListener?.onBufferReceived(buffer)
            }
        }

        override fun onEndOfSpeech() {
            currentListener?.onEndOfSpeech()
        }

        override fun onError(error: Int) {
            Log.e(TAG, "Speech recognition error: $error")
            currentListener?.onError(error)
            destroySpeechRecognizer()
        }

        override fun onResults(results: Bundle?) {
            currentListener?.onResults(results)
            destroySpeechRecognizer()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            currentListener?.onPartialResults(partialResults)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            currentListener?.onEvent(eventType, params)
        }
    }

    private fun destroySpeechRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        currentListener = null
    }

    override fun initialize(callback: ISttRecognitionListener?) {
        if (callback == null) {
            throw IllegalArgumentException("callback cannot be null")
        }
        Log.i(TAG, "Initializing STT")
        currentListener = callback
    }

    override fun startListening() {
        mainThreadHandler.post {
            if (speechRecognizer == null) {
                // Context needs attributionSource
                speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(recognitionListener)
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            Log.w(
                TAG,
                "My: ${Looper.myLooper()}, main: ${Looper.getMainLooper()}"
            )
            speechRecognizer?.startListening(intent)
        }
    }

    override fun stopListening() {
        mainThreadHandler.post {
            speechRecognizer?.stopListening()
        }
    }

    override fun cancel() {
        speechRecognizer?.cancel()
        destroySpeechRecognizer()
    }

    override fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
}