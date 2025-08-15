package com.penumbraos.bridge;

import com.penumbraos.bridge.callback.IHandGestureCallback;

interface IHandGestureProvider {
    void registerCallback(IHandGestureCallback callback);
    void deregisterCallback(IHandGestureCallback callback);
}