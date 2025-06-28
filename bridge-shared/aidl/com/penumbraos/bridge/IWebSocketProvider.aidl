package com.penumbraos.bridge;

import com.penumbraos.bridge.IWebSocketCallback;

interface IWebSocketProvider {
    void openWebSocket(String requestId, String url, in Map headers, IWebSocketCallback callback);
    void sendWebSocketMessage(String requestId, int type, in byte[] data);
    void closeWebSocket(String requestId);
}
