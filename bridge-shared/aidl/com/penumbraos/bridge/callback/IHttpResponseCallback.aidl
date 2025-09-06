package com.penumbraos.bridge.callback;

interface IHttpResponseCallback {
    void sendResponse(int statusCode, in Map headers, in byte[] body, String contentType);
}