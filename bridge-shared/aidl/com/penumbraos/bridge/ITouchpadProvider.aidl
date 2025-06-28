package com.penumbraos.bridge;

import com.penumbraos.bridge.ITouchpadCallback;

interface ITouchpadProvider {
    void registerCallback(ITouchpadCallback callback);
    void deregisterCallback(ITouchpadCallback callback);
}