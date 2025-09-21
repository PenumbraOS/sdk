package com.penumbraos.bridge_shell.provider

import android.content.Context
import android.os.IBinder
import android.os.ServiceManager
import android.util.Log
import com.penumbraos.bridge.IAccessoryProvider
import com.penumbraos.bridge.external.getApkClassLoader
import com.penumbraos.bridge.types.AccessoryBatteryInfo
import java.lang.reflect.Method

private const val TAG = "AccessoryProvider"

class AccessoryProvider(context: Context) : IAccessoryProvider.Stub() {

    private var deviceManagerService: Any? = null
    private var deviceManagerBinder: IBinder? = null

    private var iDeviceManagerClass: Class<*>
    private var iDeviceManagerAsInterfaceMethod: Method
    private var getBatteryStateMethod: Method
    private var isConnectedMethod: Method
    private var batteryStateClass: Class<*>

    init {
        val classLoader = getApkClassLoader(context, "humane.experience.settings")

        iDeviceManagerClass =
            classLoader.loadClass("humane.devicemanager.IDeviceManager")
        val iDeviceManagerStubClass =
            classLoader.loadClass("humane.devicemanager.IDeviceManager\$Stub")
        batteryStateClass =
            classLoader.loadClass("humane.devicemanager.DMAccessoryBatteryState")

        iDeviceManagerAsInterfaceMethod =
            iDeviceManagerStubClass.getMethod("asInterface", IBinder::class.java)
        getBatteryStateMethod =
            iDeviceManagerClass.getMethod("getBatteryState", Int::class.java, batteryStateClass)
        isConnectedMethod = iDeviceManagerClass.getMethod("isConnected", Int::class.java)
    }

    override fun getBatteryInfo(): AccessoryBatteryInfo? {
        if (deviceManagerService == null || deviceManagerBinder?.isBinderAlive != true) {
            Log.w(TAG, "Device Manager service not connected")
            connectToDeviceManager()
        }

        if (deviceManagerService == null || deviceManagerBinder?.isBinderAlive != true) {
            Log.w(TAG, "Device Manager service not connected. Giving up")
            return null
        }

        return try {
            Log.d(TAG, "PING ${deviceManagerBinder?.pingBinder()}")

            val isConnectedMethod = iDeviceManagerClass.getMethod("isConnected", Int::class.java)
            val connectionStatus = isConnectedMethod.invoke(deviceManagerService, 0)
            // If 128, it's "maybeConnected", otherwise connected
            val isConnected = connectionStatus != 1

            Log.d(TAG, "Device Manager isConnected returned: $isConnected $connectionStatus")
            val batteryStateObject = batteryStateClass.getConstructor().newInstance()

            val result =
                getBatteryStateMethod.invoke(deviceManagerService, 0, batteryStateObject) as Int
            Log.d(TAG, "getBatteryState returned: $result")

            if (result == 0) {
                val levelPercentField = batteryStateClass.getField("levelPercent")
                val b1Level = levelPercentField.getInt(batteryStateObject)

                val isChargingField = batteryStateClass.getField("isCharging")
                val isCharging = isChargingField.getBoolean(batteryStateObject)

                Log.d(TAG, "B1 level: $b1Level")

                AccessoryBatteryInfo().apply {
                    boosterBatteryLevel = if (isConnected) b1Level else -1
                    boosterBatteryCharging = isConnected && isCharging
                    boosterBatteryConnected = isConnected
                }
            } else {
                Log.e(TAG, "Failed to get B1 battery state, result: $result")

                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing B1 battery state", e)
            deviceManagerService = null
            deviceManagerBinder = null

            null
        }
    }

    private fun connectToDeviceManager() {
        try {
            val binder = ServiceManager.getService("humane.devicemanager")
            if (binder != null && binder.isBinderAlive) {
                deviceManagerService = iDeviceManagerAsInterfaceMethod.invoke(null, binder)
                deviceManagerBinder = binder

                Log.d(TAG, "Connected to Device Manager service")
            } else {
                Log.e(TAG, "Device Manager service not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to Device Manager service", e)
        }
    }
}