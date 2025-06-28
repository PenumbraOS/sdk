package com.penumbraos.bridge;

import com.penumbraos.bridge.IHttpCallback;
import com.penumbraos.bridge.IWebSocketCallback;
import com.penumbraos.bridge.IServiceProvider;

interface IBridge {
    void makeHttpRequest(String requestId, String url, String method, String body, in Map headers, IHttpCallback callback);
    void openWebSocket(String requestId, String url, in Map headers, IWebSocketCallback callback);
    void sendWebSocketMessage(String requestId, int type, in byte[] data);
    void closeWebSocket(String requestId);
    void registerServiceProvider(String name, IServiceProvider provider);
    void sendMessageToServiceProvider(String name, String message);
}
