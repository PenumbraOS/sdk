package com.penumbraos.bridge;

import com.penumbraos.bridge.LedAnimationIpc;

interface ILedProvider {
    void playAnimation(in LedAnimationIpc animation);
    void clearAllAnimation();
}
