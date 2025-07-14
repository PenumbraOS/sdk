package com.penumbraos.bridge;

import com.penumbraos.bridge.IShellCallback;

interface IShellProvider {
    void executeCommand(String command, String workingDirectory, IShellCallback callback);
    void executeCommandWithTimeout(String command, String workingDirectory, int timeoutMs, IShellCallback callback);
}