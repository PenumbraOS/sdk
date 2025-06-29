package com.penumbraos.bridge;

import com.penumbraos.bridge.IHttpProvider;
import com.penumbraos.bridge.IWebSocketProvider;
import com.penumbraos.bridge.ITouchpadProvider;
import com.penumbraos.bridge.ISttProvider;

interface IBridge {
    IBinder getHttpProvider();
    IBinder getWebSocketProvider();
    IBinder getTouchpadProvider();
    IBinder getSttProvider();
    void registerHttpProvider(IHttpProvider provider);
    void registerWebSocketProvider(IWebSocketProvider provider);
    void registerTouchpadProvider(ITouchpadProvider provider);
    void registerSttProvider(ISttProvider provider);
}
