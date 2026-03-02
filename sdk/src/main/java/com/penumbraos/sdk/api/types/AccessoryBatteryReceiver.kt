package com.penumbraos.sdk.api.types

interface AccessoryBatteryReceiver {
    fun onBatteryInfoChanged(info: BoosterBatteryInfo)
}
