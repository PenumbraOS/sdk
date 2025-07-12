package com.penumbraos.bridge_system.provider

import android.hardware.input.IInputManager
import android.os.Looper
import android.os.ServiceManager
import android.util.Log
import android.view.InputChannel
import android.view.InputEvent
import android.view.InputEventReceiver
import com.penumbraos.bridge.ITouchpadCallback
import com.penumbraos.bridge.ITouchpadProvider

private const val TOUCHPAD_MONITOR_NAME = "Humane Touchpad Monitor"
private const val TOUCHPAD_DISPLAY_ID = 3344

private const val TAG = "TouchpadProvider"

class TouchpadProvider(private val looper: Looper) :
    ITouchpadProvider.Stub() {
    private val callbacks = mutableListOf<ITouchpadCallback>()

    inner class EventListener(inputChannel: InputChannel) :
        InputEventReceiver(inputChannel, looper) {
        override fun onInputEvent(event: InputEvent?) {
            if (event != null) {
                val deadCallbacks = mutableListOf<ITouchpadCallback>()
                callbacks.forEach { callback ->
                    safeCallback(TAG) {
                        callback.onInputEvent(event)
                    }
                }
                callbacks.removeAll(deadCallbacks)
            }
            super.onInputEvent(event)
        }
    }

    private var listener: EventListener? = null

    override fun registerCallback(callback: ITouchpadCallback) {
        callbacks.add(callback)
        registerListenerIfNecessary()
    }

    override fun deregisterCallback(callback: ITouchpadCallback) {
        callbacks.remove(callback)
        if (callbacks.count() < 1) {
            listener?.dispose()
            listener = null
        }
    }

    private fun registerListenerIfNecessary() {
        if (listener != null) {
            return
        }

        Log.w(TAG, "Registering touchpad listener")

        try {
            val inputManagerBinder = ServiceManager.getService("input")
            val inputManager = IInputManager.Stub.asInterface(inputManagerBinder)

            val inputMonitor =
                inputManager.monitorGestureInput(TOUCHPAD_MONITOR_NAME, TOUCHPAD_DISPLAY_ID)
            listener = EventListener(inputMonitor.inputChannel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register touchpad listener", e)
        }
    }
}