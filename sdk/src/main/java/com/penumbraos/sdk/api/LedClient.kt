package com.penumbraos.sdk.api

import com.penumbraos.bridge.ILedProvider
import com.penumbraos.sdk.api.types.LedAnimation

private const val TAG = "LedClient"

class LedClient(private val ledProvider: ILedProvider) {
    /**
     * TODO: Non-functional
     */
    fun playAnimation(animation: LedAnimation) {
        ledProvider.playAnimation(animation.enumValue)
    }

    /**
     * TODO: Non-functional
     */
    fun clearAllAnimation() {
        ledProvider.clearAllAnimation()
    }

    // Status is not implemented and immediately throws an exception, hardcoded in the native code
//    val status: LedStatus
//        get() = ledProvider.status
}