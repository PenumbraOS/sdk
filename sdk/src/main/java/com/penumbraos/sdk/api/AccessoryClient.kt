package com.penumbraos.sdk.api

import android.util.Log
import com.penumbraos.bridge.IAccessoryProvider
import com.penumbraos.bridge.callback.IAccessoryCallback
import com.penumbraos.bridge.types.AccessoryBatteryInfo as AidlAccessoryBatteryInfo
import com.penumbraos.sdk.api.types.AccessoryBatteryReceiver
import com.penumbraos.sdk.api.types.BoosterBatteryInfo
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "AccessoryClient"

class AccessoryClient(private val accessoryProvider: IAccessoryProvider) {
    private val registeredCallbacks =
        ConcurrentHashMap<AccessoryBatteryReceiver, IAccessoryCallback.Stub>()

    fun getBatteryInfo(): BoosterBatteryInfo? {
        return try {
            val aidlBatteryInfo = accessoryProvider.batteryInfo
            BoosterBatteryInfo.fromAidl(aidlBatteryInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get battery info", e)
            null
        }
    }

    fun register(receiver: AccessoryBatteryReceiver) {
        val callbackStub = object : IAccessoryCallback.Stub() {
            override fun onBatteryInfoChanged(info: AidlAccessoryBatteryInfo) {
                receiver.onBatteryInfoChanged(BoosterBatteryInfo.fromAidl(info))
            }
        }
        registeredCallbacks[receiver] = callbackStub
        accessoryProvider.registerCallback(callbackStub)
    }

    fun remove(receiver: AccessoryBatteryReceiver) {
        val callbackStub = registeredCallbacks.remove(receiver)
        if (callbackStub != null) {
            accessoryProvider.deregisterCallback(callbackStub)
        }
    }
}