package com.penumbraos.bridge.callback;

interface IHttpResponseCallback {
    void sendResponse(int statusCode, in Map headers, String body, String contentType);
}