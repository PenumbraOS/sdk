package com.penumbraos.bridge_system.util

import android.hardware.input.IInputManager
import android.os.ServiceManager
import android.util.Log
import android.view.InputChannel

private const val TOUCHPAD_MONITOR_NAME = "Humane Touchpad Monitor"
private const val TOUCHPAD_DISPLAY_ID = 3344

fun registerTouchpadInputChannel(tag: String): InputChannel? {
    return try {
        val inputManagerBinder = ServiceManager.getService("input")
        val inputManager = IInputManager.Stub.asInterface(inputManagerBinder)

        inputManager.monitorGestureInput(TOUCHPAD_MONITOR_NAME, TOUCHPAD_DISPLAY_ID).inputChannel
    } catch (e: Exception) {
        Log.e(tag, "Failed to register touchpad listener", e)
        null
    }
}