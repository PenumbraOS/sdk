package com.penumbraos.bridge.callback;

import com.penumbraos.bridge.callback.IHttpResponseCallback;

interface IHttpEndpointCallback {
    void onHttpRequest(String path, String method, in Map headers, in Map queryParams, String body, IHttpResponseCallback responseCallback);
}