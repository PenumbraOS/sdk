package com.penumbraos.bridge;

// Callback for HTTP requests
interface IHttpCallback {
    // Called once with headers and potentially the first chunk of data
    oneway void onHeaders(String requestId, int statusCode, in Map headers);
    // Called for each subsequent chunk of the response body
    oneway void onData(String requestId, in byte[] chunk);
    // Called when the response is fully received
    oneway void onComplete(String requestId);
    // Called if an error occurs
    oneway void onError(String requestId, String errorMessage, int errorCode);
}