package com.penumbraos.bridge;

import com.penumbraos.bridge.IHttpCallback;

interface IBridge {
    void makeHttpRequest(String requestId, String url, String method, String body, in Map headers, IHttpCallback callback);
    void ping();
}