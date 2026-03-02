package com.penumbraos.bridge_shell.provider

import android.os.Binder
import android.os.Parcel
import android.util.Log

private const val TAG = "AccessoryEventBinder"
private const val DESCRIPTOR = "humane.devicemanager.IDMAccessoryEventsCallback"
private const val TRANSACTION_ON_ACCESSORY_EVENT = 1
private const val TRANSACTION_ON_BATTERY_LEVEL_CHANGE_EVENT = 2
private const val INTERFACE_TRANSACTION = 1598968902 // IBinder.INTERFACE_TRANSACTION

class AccessoryEventBinder(
    private val onAccessoryEvent: (from: Int, to: Int) -> Unit,
    private val onBatteryLevelChangeEvent: (fromLevel: Int, toLevel: Int) -> Unit,
) : Binder() {

    init {
        attachInterface(null, DESCRIPTOR)
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        return when (code) {
            INTERFACE_TRANSACTION -> {
                reply?.writeString(DESCRIPTOR)
                true
            }
            TRANSACTION_ON_ACCESSORY_EVENT -> {
                data.enforceInterface(DESCRIPTOR)
                val from = data.readInt()
                val to = data.readInt()
                Log.d(TAG, "onAccessoryEvent: from=$from to=$to")
                onAccessoryEvent(from, to)
                true
            }
            TRANSACTION_ON_BATTERY_LEVEL_CHANGE_EVENT -> {
                data.enforceInterface(DESCRIPTOR)
                val fromLevel = data.readInt()
                val toLevel = data.readInt()
                Log.d(TAG, "onBatteryLevelChangeEvent: from=$fromLevel to=$toLevel")
                onBatteryLevelChangeEvent(fromLevel, toLevel)
                true
            }
            else -> super.onTransact(code, data, reply, flags)
        }
    }
}
