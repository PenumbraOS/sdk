package com.penumbraos.bridge;

import com.penumbraos.bridge.IHttpProvider;
import com.penumbraos.bridge.IWebSocketProvider;
import com.penumbraos.bridge.ITouchpadProvider;

interface IBridge {
    IBinder getHttpProvider();
    IBinder getWebSocketProvider();
    IBinder getTouchpadProvider();
    void registerHttpProvider(IHttpProvider provider);
    void registerWebSocketProvider(IWebSocketProvider provider);
    void registerTouchpadProvider(ITouchpadProvider provider);
}
