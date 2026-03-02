package com.penumbraos.bridge_shell.provider

import android.content.Context
import android.os.IBinder
import android.os.ServiceManager
import android.util.Log
import com.penumbraos.bridge.IAccessoryProvider
import com.penumbraos.bridge.callback.IAccessoryCallback
import com.penumbraos.bridge.external.getApkClassLoader
import com.penumbraos.bridge.external.safeCallback
import com.penumbraos.bridge.types.AccessoryBatteryInfo
import java.lang.reflect.Method

private const val TAG = "AccessoryProvider"

private const val STATE_DISCONNECTED = 1
private const val STATE_CONNECTED = 2
private const val STATE_MAYBE_CONNECTED = 128
private const val POLL_INTERVAL_MS = 5000L

class AccessoryProvider(context: Context) : IAccessoryProvider.Stub() {

    private var deviceManagerService: Any? = null
    private var deviceManagerBinder: IBinder? = null

    private var iDeviceManagerClass: Class<*>
    private var iDeviceManagerAsInterfaceMethod: Method
    private var getBatteryStateMethod: Method
    private var isConnectedMethod: Method
    private var batteryStateClass: Class<*>

    private var boosterLevel: Int = -1
    private var boosterCharging: Boolean = false
    private var boosterConnectionState: Int = STATE_DISCONNECTED
    private var isOnChargePad: Boolean = false
    private var isInChargeCase: Boolean = false

    private val callbacks = mutableListOf<IAccessoryCallback>()

    private var pollingThread: Thread? = null
    @Volatile private var pollingActive = false

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
            connectToDeviceManager()
        }
        if (deviceManagerService == null) return null

        return try {
            pollAllAccessoryState()
            buildBatteryInfo()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery info", e)
            deviceManagerService = null
            deviceManagerBinder = null
            null
        }
    }

    override fun registerCallback(callback: IAccessoryCallback) {
        callback.asBinder().linkToDeath(object : IBinder.DeathRecipient {
            override fun binderDied() {
                deregisterCallback(callback)
            }
        }, 0)

        callbacks.add(callback)
        startPolling()

        try {
            if (deviceManagerService == null || deviceManagerBinder?.isBinderAlive != true) {
                connectToDeviceManager()
            }
            if (deviceManagerService != null) {
                pollAllAccessoryState()
            }
            callback.onBatteryInfoChanged(buildBatteryInfo())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send initial battery info", e)
            deviceManagerService = null
            deviceManagerBinder = null
        }
    }

    override fun deregisterCallback(callback: IAccessoryCallback) {
        callbacks.remove(callback)

        if (callbacks.count() < 1) {
            Log.w(TAG, "Deregistering accessory listener")
            stopPolling()
        }
    }

    private fun startPolling() {
        if (pollingActive) return
        pollingActive = true

        pollingThread = Thread({
            while (pollingActive) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
                if (!pollingActive) break

                try {
                    if (deviceManagerService == null || deviceManagerBinder?.isBinderAlive != true) {
                        connectToDeviceManager()
                    }
                    if (deviceManagerService != null) {
                        val changed = pollAllAccessoryState()
                        if (changed) {
                            callCallback { callback ->
                                callback.onBatteryInfoChanged(buildBatteryInfo())
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Poll cycle failed", e)
                    deviceManagerService = null
                    deviceManagerBinder = null
                }
            }
        }, "AccessoryProvider-poll").also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun stopPolling() {
        pollingActive = false
        pollingThread?.interrupt()
        pollingThread = null
    }

    private fun pollAllAccessoryState(): Boolean {
        val svc = deviceManagerService ?: return false
        var changed = false

        // Booster (type 0)
        val oldBoosterConn = boosterConnectionState
        val oldBoosterLevel = boosterLevel
        val oldBoosterCharging = boosterCharging

        val boosterConnStatus = isConnectedMethod.invoke(svc, 0) as Int
        boosterConnectionState = boosterConnStatus

        if (boosterConnStatus == STATE_CONNECTED) {
            val batteryStateObject = batteryStateClass.getConstructor().newInstance()
            val result = getBatteryStateMethod.invoke(svc, 0, batteryStateObject) as Int
            if (result == 0) {
                boosterLevel = batteryStateClass.getField("levelPercent").getInt(batteryStateObject)
                boosterCharging = batteryStateClass.getField("isCharging").getBoolean(batteryStateObject)
            }
        } else {
            boosterLevel = 0
            boosterCharging = false
        }

        if (oldBoosterConn != boosterConnectionState ||
            oldBoosterLevel != boosterLevel ||
            oldBoosterCharging != boosterCharging) {
            changed = true
        }

        // Charge pad (type 2)
        val oldPad = isOnChargePad
        val padStatus = isConnectedMethod.invoke(svc, 2) as Int
        isOnChargePad = (padStatus == STATE_CONNECTED)
        if (oldPad != isOnChargePad) changed = true

        // Charge case (type 1)
        val oldCase = isInChargeCase
        val caseStatus = isConnectedMethod.invoke(svc, 1) as Int
        isInChargeCase = (caseStatus == STATE_CONNECTED)
        if (oldCase != isInChargeCase) changed = true

        return changed
    }

    private fun callCallback(withCallback: (IAccessoryCallback) -> Unit) {
        val callbacksToRemove = mutableListOf<IAccessoryCallback>()

        callbacks.forEach { callback ->
            safeCallback(TAG, {
                withCallback(callback)
            }, onDeadObject = {
                callbacksToRemove.add(callback)
            })
        }

        callbacksToRemove.forEach { callback -> deregisterCallback(callback) }
    }

    private fun buildBatteryInfo(): AccessoryBatteryInfo {
        val isConnected = boosterConnectionState == STATE_CONNECTED
        return AccessoryBatteryInfo().apply {
            boosterBatteryLevel = if (isConnected) boosterLevel else -1
            boosterBatteryCharging = isConnected && boosterCharging
            boosterBatteryConnected = isConnected
            boosterConnectionState = this@AccessoryProvider.boosterConnectionState
            this.isOnChargePad = this@AccessoryProvider.isOnChargePad
            this.isInChargeCase = this@AccessoryProvider.isInChargeCase
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
