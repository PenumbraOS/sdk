package com.penumbraos.sdk.api.types

import android.view.InputEvent

interface TouchpadInputReceiver {
    fun onInputEvent(event: InputEvent)
}