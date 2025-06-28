package com.penumbraos.sdk.api

import android.view.InputEvent

interface TouchpadInputReceiver {
    fun onInputEvent(event: InputEvent)
}
