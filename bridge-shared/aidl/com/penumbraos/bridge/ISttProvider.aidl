package com.penumbraos.bridge;

import com.penumbraos.bridge.ISttRecognitionListener;

interface ISttProvider {
    void startListening(in ISttRecognitionListener callback);
    void stopListening(in ISttRecognitionListener callback);
    void cancel(in ISttRecognitionListener callback);
    boolean isRecognitionAvailable();
}
