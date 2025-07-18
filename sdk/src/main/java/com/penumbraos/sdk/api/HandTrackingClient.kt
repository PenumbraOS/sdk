package com.penumbraos.sdk.api

import com.penumbraos.bridge.IHandTrackingProvider

class HandTrackingClient(private val handTrackingProvider: IHandTrackingProvider) {
    fun startTracking() {
        handTrackingProvider.triggerStart()
    }

    fun stopTracking() {
        handTrackingProvider.triggerStop()
    }
}