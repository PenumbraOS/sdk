package com.penumbraos.sdk.api

import android.util.Log
import com.penumbraos.bridge.IAccessoryProvider
import com.penumbraos.sdk.api.types.BoosterBatteryInfo

private const val TAG = "AccessoryClient"

class AccessoryClient(private val accessoryProvider: IAccessoryProvider) {

    fun getBatteryInfo(): BoosterBatteryInfo? {
        return try {
            val aidlBatteryInfo = accessoryProvider.batteryInfo
            BoosterBatteryInfo.fromAidl(aidlBatteryInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get battery info", e)
            null
        }
    }
}