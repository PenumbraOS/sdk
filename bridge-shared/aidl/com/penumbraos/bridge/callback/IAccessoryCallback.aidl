package com.penumbraos.bridge.callback;

import com.penumbraos.bridge.types.AccessoryBatteryInfo;

oneway interface IAccessoryCallback {
    void onBatteryInfoChanged(in AccessoryBatteryInfo info);
}
