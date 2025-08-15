package com.penumbraos.bridge.callback;

interface IHttpCallback {
    oneway void onHeaders(String requestId, int statusCode, in Map headers);
    oneway void onData(String requestId, in byte[] chunk);
    oneway void onComplete(String requestId);
    oneway void onError(String requestId, String errorMessage, int errorCode);
}