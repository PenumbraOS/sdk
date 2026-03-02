package com.penumbraos.sdk.api.types

import com.penumbraos.bridge.types.AccessoryBatteryInfo

data class BoosterBatteryInfo(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val isConnected: Boolean,
    val connectionState: Int = 1,
    val isOnChargePad: Boolean = false,
    val isInChargeCase: Boolean = false
) {
    companion object {
        fun fromAidl(
            aidlInfo: AccessoryBatteryInfo,
        ): BoosterBatteryInfo {
            return BoosterBatteryInfo(
                batteryLevel = aidlInfo.boosterBatteryLevel,
                isCharging = aidlInfo.boosterBatteryCharging,
                isConnected = aidlInfo.boosterBatteryConnected,
                connectionState = aidlInfo.boosterConnectionState,
                isOnChargePad = aidlInfo.isOnChargePad,
                isInChargeCase = aidlInfo.isInChargeCase
            )
        }
    }
}
