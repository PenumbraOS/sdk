package com.penumbraos.bridge_system.provider

import android.os.Looper
import android.util.Log
import android.view.InputChannel
import android.view.InputEvent
import android.view.InputEventReceiver
import com.penumbraos.bridge.ITouchpadProvider
import com.penumbraos.bridge.callback.ITouchpadCallback
import com.penumbraos.bridge_system.util.registerTouchpadInputChannel

private const val TOUCHPAD_EVENT_SOURCE = 0x100008

private const val TAG = "TouchpadProvider"

class TouchpadProvider(private val looper: Looper) :
    ITouchpadProvider.Stub() {
    private val callbacks = mutableListOf<ITouchpadCallback>()
    private var listener: EventListener? = null

    inner class EventListener(inputChannel: InputChannel) :
        InputEventReceiver(inputChannel, looper) {
        override fun onInputEvent(event: InputEvent?) {
            if (event != null && event.isFromSource(TOUCHPAD_EVENT_SOURCE)) {
                val callbacksToRemove = mutableListOf<ITouchpadCallback>()
                callbacks.forEach { callback ->
                    safeCallback(TAG, {
                        callback.onInputEvent(event)
                    }, onDeadObject = {
                        callbacksToRemove.add(callback)
                    })
                }
                callbacksToRemove.forEach { callback -> deregisterCallback(callback) }
            }
            super.onInputEvent(event)
        }
    }

    override fun registerCallback(callback: ITouchpadCallback) {
        callbacks.add(callback)
        registerListenerIfNecessary()
    }

    override fun deregisterCallback(callback: ITouchpadCallback) {
        callbacks.remove(callback)
        if (callbacks.count() < 1) {
            Log.w(TAG, "Deregistering touchpad listener")
            listener?.dispose()
            listener = null
        }
    }

    private fun registerListenerIfNecessary() {
        if (listener != null) {
            return
        }

        Log.w(TAG, "Registering touchpad listener")

        val inputChannel = registerTouchpadInputChannel(TAG)

        if (inputChannel != null) {
            listener = EventListener(inputChannel)
        }
    }
}