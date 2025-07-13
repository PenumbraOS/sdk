package com.penumbraos.bridge;

import com.penumbraos.bridge.IHttpProvider;
import com.penumbraos.bridge.IWebSocketProvider;
import com.penumbraos.bridge.ITouchpadProvider;
import com.penumbraos.bridge.ISttProvider;
import com.penumbraos.bridge.ILedProvider;
import com.penumbraos.bridge.ISettingsProvider;

interface IBridge {
    IBinder getHttpProvider();
    IBinder getWebSocketProvider();
    IBinder getTouchpadProvider();
    IBinder getSttProvider();
    IBinder getLedProvider();
    IBinder getSettingsProvider();
    void registerSystemService(IHttpProvider httpProvider, IWebSocketProvider webSocketProvider, ITouchpadProvider touchpadProvider, ISttProvider sttProvider, ILedProvider ledProvider);
    void registerSettingsService(ISettingsProvider settingsProvider);
}
