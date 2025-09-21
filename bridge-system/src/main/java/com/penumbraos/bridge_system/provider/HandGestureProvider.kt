package com.penumbraos.bridge_system.provider

import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.InputChannel
import android.view.InputEvent
import android.view.InputEventReceiver
import android.view.KeyEvent
import android.view.MotionEvent
import com.penumbraos.bridge.IHandGestureProvider
import com.penumbraos.bridge.callback.IHandGestureCallback
import com.penumbraos.bridge.external.safeCallback
import com.penumbraos.bridge_system.util.registerTouchpadInputChannel

private const val TAG = "HandGestureProvider"

private const val VELOCITY_THRESHOLD = 0.5f
private const val VELOCITY_MIN_MOVING = 0.3f
private const val GESTURE_COOLDOWN_MS = 500L
private const val MAX_SAMPLE_DELTA = 1000L

class HandGestureProvider(private val looper: Looper) : IHandGestureProvider.Stub() {
    private val callbacks = mutableListOf<IHandGestureCallback>()
    private var listener: EventListener? = null

    inner class PushListener {

        private var gestureActive = false
        private var gestureEndTime = 0L

        private var lastDepth = Float.NaN
        private var lastTimestamp = 0L

        fun processMotionEvent(event: MotionEvent) {
            val depth = event.getAxisValue(MotionEvent.AXIS_PRESSURE)
            // HATS sends events every ~100ms
            val timestamp = event.eventTime

            if (lastDepth.isNaN()) {
                lastDepth = depth
                lastTimestamp = timestamp
                return
            }

            val timeDelta = timestamp - lastTimestamp
            if (timeDelta > 0 && timeDelta < MAX_SAMPLE_DELTA) {
                val depthDelta = depth - lastDepth
                val velocity = kotlin.math.abs(depthDelta * 1000.0f / timeDelta)

                when {
                    !gestureActive && velocity >= VELOCITY_THRESHOLD && (timestamp - gestureEndTime) > GESTURE_COOLDOWN_MS -> {
                        gestureActive = true
                        onHandPush()
                    }

                    gestureActive && velocity < VELOCITY_MIN_MOVING -> {
                        gestureActive = false
                        gestureEndTime = timestamp
                    }
                }
            }

            lastDepth = depth
            lastTimestamp = timestamp
        }
    }

    inner class EventListener(inputChannel: InputChannel) :
        InputEventReceiver(inputChannel, looper) {
        private val pushListener = PushListener()

        override fun onInputEvent(event: InputEvent?) {
            if (event != null) {
                if (event is MotionEvent) {
                    pushListener.processMotionEvent(event)
                } else if (event is KeyEvent && event.keyCode == KeyEvent.KEYCODE_H) {
                    onHandClose()
                }
            }
            super.onInputEvent(event)
        }
    }

    override fun registerCallback(callback: IHandGestureCallback) {
        callback.asBinder().linkToDeath(object : IBinder.DeathRecipient {
            override fun binderDied() {
                deregisterCallback(callback)
            }
        }, 0)

        callbacks.add(callback)
        registerListenerIfNecessary()
    }

    override fun deregisterCallback(callback: IHandGestureCallback) {
        callbacks.remove(callback)
        if (callbacks.count() < 1) {
            Log.w(TAG, "Deregistering hand gesture listener")
            listener?.dispose()
            listener = null
        }
    }

    fun onHandPush() {
        callCallback { callback -> callback.onHandPush() }
    }

    fun onHandClose() {
        callCallback { callback -> callback.onHandClose() }
    }

    private fun callCallback(withCallback: (IHandGestureCallback) -> Unit) {
        val callbacksToRemove = mutableListOf<IHandGestureCallback>()
        callbacks.forEach { callback ->
            safeCallback(TAG, {
                withCallback(callback)
            }, onDeadObject = {
                callbacksToRemove.add(callback)
            })
        }
        callbacksToRemove.forEach { callback -> deregisterCallback(callback) }
    }

    private fun registerListenerIfNecessary() {
        if (listener != null) {
            return
        }

        Log.w(TAG, "Registering touchpad listener")

        // We register an input channel for the touchpad, but we'll get events from the hand tracker as well
        // We will filter the events down based on source in the listener
        val inputChannel = registerTouchpadInputChannel(TAG)
        if (inputChannel != null) {
            listener = EventListener(inputChannel)
        }
    }
}
