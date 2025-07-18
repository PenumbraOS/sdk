package com.penumbraos.bridge;

import com.penumbraos.bridge.IHttpProvider;
import com.penumbraos.bridge.IWebSocketProvider;
import com.penumbraos.bridge.ISttProvider;
import com.penumbraos.bridge.ITouchpadProvider;
import com.penumbraos.bridge.ILedProvider;
import com.penumbraos.bridge.IHandTrackingProvider;
import com.penumbraos.bridge.ISettingsProvider;
import com.penumbraos.bridge.IShellProvider;

interface IBridge {
    IBinder getHttpProvider();
    IBinder getWebSocketProvider();
    IBinder getSttProvider();

    IBinder getTouchpadProvider();
    IBinder getLedProvider();
    IBinder getHandTrackingProvider();

    IBinder getSettingsProvider();
    IBinder getShellProvider();
    void registerSystemService(IHttpProvider httpProvider, IWebSocketProvider webSocketProvider, ISttProvider sttProvider, ITouchpadProvider touchpadProvider, ILedProvider ledProvider, IHandTrackingProvider handTrackingProvider);
    void registerSettingsService(ISettingsProvider settingsProvider);
    void registerShellService(IShellProvider shellProvider);
}
