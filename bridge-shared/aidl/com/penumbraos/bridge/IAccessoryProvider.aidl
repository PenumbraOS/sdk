package com.penumbraos.bridge;

import com.penumbraos.bridge.types.AccessoryBatteryInfo;
import com.penumbraos.bridge.callback.IAccessoryCallback;

interface IAccessoryProvider {
    AccessoryBatteryInfo getBatteryInfo();
    void registerCallback(IAccessoryCallback callback);
    void deregisterCallback(IAccessoryCallback callback);
}
