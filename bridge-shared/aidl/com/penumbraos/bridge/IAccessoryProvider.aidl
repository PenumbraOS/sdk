package com.penumbraos.bridge;

import com.penumbraos.bridge.types.AccessoryBatteryInfo;

interface IAccessoryProvider {
    AccessoryBatteryInfo getBatteryInfo();
}