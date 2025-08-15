package com.penumbraos.sdk.api

import com.penumbraos.bridge.IHandGestureProvider
import com.penumbraos.bridge.callback.IHandGestureCallback
import com.penumbraos.sdk.api.types.HandGestureReceiver
import java.util.concurrent.ConcurrentHashMap

class HandGestureClient(private val handGestureProvider: IHandGestureProvider) {
    private val registeredCallbacks =
        ConcurrentHashMap<HandGestureReceiver, IHandGestureCallback.Stub>()

    fun register(receiver: HandGestureReceiver) {
        val callbackStub = object : IHandGestureCallback.Stub() {
            override fun onHandClose() {
                receiver.onHandClose()
            }

            override fun onHandPush() {
                receiver.onHandPush()
            }
        }
        registeredCallbacks[receiver] = callbackStub
        handGestureProvider.registerCallback(callbackStub)
    }

    fun remove(receiver: HandGestureReceiver) {
        val callbackStub = registeredCallbacks.remove(receiver)
        if (callbackStub != null) {
            handGestureProvider.deregisterCallback(callbackStub)
        }
    }
}