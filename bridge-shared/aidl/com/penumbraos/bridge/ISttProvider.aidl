package com.penumbraos.bridge;

import com.penumbraos.bridge.ISttRecognitionListener;

interface ISttProvider {
    void initialize(in ISttRecognitionListener callback);
    void startListening();
    void stopListening();
    void cancel();
    boolean isRecognitionAvailable();
}
