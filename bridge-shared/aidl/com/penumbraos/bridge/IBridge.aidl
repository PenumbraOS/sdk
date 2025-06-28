package com.penumbraos.bridge;

import com.penumbraos.bridge.IHttpProvider;
import com.penumbraos.bridge.IWebSocketProvider;

interface IBridge {
    IBinder getHttpProvider();
    IBinder getWebSocketProvider();
    void registerHttpProvider(IHttpProvider provider);
    void registerWebSocketProvider(IWebSocketProvider provider);
}
