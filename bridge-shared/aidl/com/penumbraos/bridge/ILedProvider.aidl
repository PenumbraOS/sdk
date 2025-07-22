package com.penumbraos.bridge;

interface ILedProvider {
    void playAnimation(int animationId);
    void clearAllAnimation();

    // Status is not implemented and immediately throws an exception, hardcoded in the native code
//    LedStatus getStatus();
}
