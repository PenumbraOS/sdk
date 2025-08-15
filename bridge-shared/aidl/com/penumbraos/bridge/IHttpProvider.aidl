package com.penumbraos.bridge;

import com.penumbraos.bridge.callback.IHttpCallback;

interface IHttpProvider {
    void makeHttpRequest(String requestId, String url, String method, String body, in Map headers, IHttpCallback callback);
}
