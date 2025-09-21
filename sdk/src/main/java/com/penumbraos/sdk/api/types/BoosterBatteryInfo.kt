package com.penumbraos.sdk.api.types

import com.penumbraos.bridge.types.AccessoryBatteryInfo

data class BoosterBatteryInfo(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val isConnected: Boolean
) {
    companion object {
        fun fromAidl(
            aidlInfo: AccessoryBatteryInfo,
        ): BoosterBatteryInfo {
            return BoosterBatteryInfo(
                batteryLevel = aidlInfo.boosterBatteryLevel,
                isCharging = aidlInfo.boosterBatteryCharging,
                isConnected = aidlInfo.boosterBatteryConnected
            )
        }
    }
}
