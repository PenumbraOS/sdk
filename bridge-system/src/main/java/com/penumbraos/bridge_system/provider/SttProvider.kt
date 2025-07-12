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
            safeCallback(TAG) { currentListener?.onReadyForSpeech(params) }
        }

        override fun onBeginningOfSpeech() {
            safeCallback(TAG) { currentListener?.onBeginningOfSpeech() }
        }

        override fun onRmsChanged(rmsdB: Float) {
            safeCallback(TAG) { currentListener?.onRmsChanged(rmsdB) }
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            if (buffer != null) {
                safeCallback(TAG) { currentListener?.onBufferReceived(buffer) }
            }
        }

        override fun onEndOfSpeech() {
            safeCallback(TAG) { currentListener?.onEndOfSpeech() }
        }

        override fun onError(error: Int) {
            Log.e(TAG, "Speech recognition error: $error")
            safeCallback(TAG) { currentListener?.onError(error) }
        }

        override fun onResults(results: Bundle?) {
            safeCallback(TAG) { currentListener?.onResults(results) }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            safeCallback(TAG) { currentListener?.onPartialResults(partialResults) }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Stub
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
        if (currentListener == null) {
            throw IllegalStateException("STT not initialized with listener callback")
        }

        Log.i(TAG, "Start listening")
        mainThreadHandler.post {
            try {
                if (speechRecognizer == null) {
                    Log.i(TAG, "Initializing speech recognizer")
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

                Log.i(TAG, "Starting speech recognition")
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition", e)
            }
        }
    }

    override fun stopListening() {
        Log.i(TAG, "Stop listening")
        mainThreadHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping speech recognition", e)
            }
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