package com.penumbraos.bridge.callback;

import android.view.InputEvent;

oneway interface ITouchpadCallback {
    void onInputEvent(in InputEvent event);
}