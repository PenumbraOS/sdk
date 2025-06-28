package com.penumbraos.bridge;

import android.view.InputEvent;

oneway interface ITouchpadCallback {
    void onInputEvent(in InputEvent event);
}