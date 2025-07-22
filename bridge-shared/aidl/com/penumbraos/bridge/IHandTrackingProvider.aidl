package com.penumbraos.bridge;

interface IHandTrackingProvider {
    void triggerStart();
    void triggerStop();

    void acquireHATSLock();
    void releaseHATSLock();
}