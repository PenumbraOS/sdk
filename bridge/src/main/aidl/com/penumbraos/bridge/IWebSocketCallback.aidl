package com.penumbraos.bridge;

interface IWebSocketCallback {
    oneway void onOpen(String requestId, in Map headers);
    oneway void onMessage(String requestId, int type, in byte[] data);
    oneway void onError(String requestId, String errorMessage);
    oneway void onClose(String requestId);
}