package com.penumbraos.sdk.api

import android.view.InputEvent
import com.penumbraos.bridge.ITouchpadCallback
import com.penumbraos.bridge.ITouchpadProvider
import java.util.concurrent.ConcurrentHashMap

class TouchpadClient(private val touchpadProvider: ITouchpadProvider) {

    private val registeredCallbacks =
        ConcurrentHashMap<TouchpadInputReceiver, ITouchpadCallback.Stub>()

    fun register(receiver: TouchpadInputReceiver) {
        val callbackStub = object : ITouchpadCallback.Stub() {
            override fun onInputEvent(event: InputEvent?) {
                if (event != null) {
                    receiver.onInputEvent(event)
                }
            }
        }
        registeredCallbacks[receiver] = callbackStub
        touchpadProvider.registerCallback(callbackStub)
    }

    fun remove(receiver: TouchpadInputReceiver) {
        val callbackStub = registeredCallbacks.remove(receiver)
        if (callbackStub != null) {
            touchpadProvider.deregisterCallback(callbackStub)
        }
    }
}