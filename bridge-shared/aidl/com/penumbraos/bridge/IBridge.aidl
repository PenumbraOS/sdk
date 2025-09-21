package com.penumbraos.bridge;

import com.penumbraos.bridge.IHttpProvider;
import com.penumbraos.bridge.IWebSocketProvider;
import com.penumbraos.bridge.IDnsProvider;
import com.penumbraos.bridge.ISttProvider;
import com.penumbraos.bridge.ITouchpadProvider;
import com.penumbraos.bridge.ILedProvider;
import com.penumbraos.bridge.IHandGestureProvider;
import com.penumbraos.bridge.IHandTrackingProvider;
import com.penumbraos.bridge.IEsimProvider;
import com.penumbraos.bridge.IAccessoryProvider;
import com.penumbraos.bridge.ISettingsProvider;
import com.penumbraos.bridge.IShellProvider;

interface IBridge {
    IBinder getHttpProvider();
    IBinder getWebSocketProvider();
    IBinder getDnsProvider();

    IBinder getSttProvider();

    IBinder getTouchpadProvider();
    IBinder getLedProvider();
    IBinder getHandGestureProvider();
    IBinder getHandTrackingProvider();

    IBinder getEsimProvider();
    IBinder getAccessoryProvider();

    IBinder getSettingsProvider();
    IBinder getShellProvider();
    void registerSystemService(IHttpProvider httpProvider, IWebSocketProvider webSocketProvider, IDnsProvider dnsProvider, ISttProvider sttProvider, ITouchpadProvider touchpadProvider, ILedProvider ledProvider, IHandGestureProvider handGestureProvider, IHandTrackingProvider handTrackingProvider, IEsimProvider esimProvider);
    void registerSettingsService(ISettingsProvider settingsProvider);
    void registerShellService(IShellProvider shellProvider, IAccessoryProvider accessoryProvider);
}
