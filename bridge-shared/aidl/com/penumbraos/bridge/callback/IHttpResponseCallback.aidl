package com.penumbraos.bridge.callback;

interface IHttpResponseCallback {
    void sendResponse(int statusCode, in Map headers, in byte[] body, in ParcelFileDescriptor file, String contentType);
}