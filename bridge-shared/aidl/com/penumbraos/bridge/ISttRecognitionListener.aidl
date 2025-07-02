package com.penumbraos.bridge;

interface ISttRecognitionListener {
    void onReadyForSpeech(in Bundle params);
    void onBeginningOfSpeech();
    void onRmsChanged(float rmsdB);
    void onBufferReceived(in byte[] buffer);
    void onEndOfSpeech();
    void onError(int error);
    void onResults(in Bundle results);
    void onPartialResults(in Bundle partialResults);
}
