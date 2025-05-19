package com.penumbraos.bridge;

import com.penumbraos.bridge.IHttpCallback;

// Main API Interface
interface IMyFriendlyAPI {
    // HTTP GET request
    void makeHttpRequest(int requestId, String url, in Map headers, IHttpCallback callback);
}