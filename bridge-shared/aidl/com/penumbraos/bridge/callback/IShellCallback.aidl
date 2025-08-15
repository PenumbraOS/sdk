package com.penumbraos.bridge.callback;

interface IShellCallback {
    void onOutput(String output);
    void onError(String error);
    void onComplete(int exitCode);
}