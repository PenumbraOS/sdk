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
    void registerSystemService(IHttpProvider httpProvider, IWebSocketProvider webSocketProvider, ITouchpadProvider touchpadProvider, ISttProvider sttProvider);
}
