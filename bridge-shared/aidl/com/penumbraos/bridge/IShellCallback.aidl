package com.penumbraos.bridge;

interface IShellCallback {
    void onOutput(String output);
    void onError(String error);
    void onComplete(int exitCode);
}