package com.penumbraos.bridge.callback;

import com.penumbraos.bridge.callback.IHttpResponseCallback;

interface IHttpEndpointCallback {
    void onHttpRequest(String path, String method, in Map pathParams, in Map headers, in Map queryParams, in byte[] body, IHttpResponseCallback responseCallback);
}