package com.penumbraos.bridge_system.provider

import android.content.Context
import android.os.IBinder
import android.os.ServiceManager
import android.util.Log
import com.penumbraos.bridge.ILedProvider
import com.penumbraos.bridge_system.getApkClassLoader
import java.lang.reflect.Method

private const val TAG = "LedProvider"

class LedProvider(private val context: Context) : ILedProvider.Stub() {
    private var pmcuService: Any? = null

    private lateinit var playAnimationMethod: Method
    private lateinit var clearAllAnimationMethod: Method

    override fun playAnimation(animationId: Int) {
        connectService()
        if (pmcuService == null) {
            throw Error("Privacy MCU service not connected")
        }

        playAnimationMethod.invoke(pmcuService, animationId)
    }

    override fun clearAllAnimation() {
        connectService()
        if (pmcuService == null) {
            throw Error("Privacy MCU service not connected")
        }

        clearAllAnimationMethod.invoke(pmcuService)
    }

    fun connectService() {
        if (pmcuService != null) {
            return;
        }

        val notificationsClassLoader =
            getApkClassLoader(context, "humane.experience.systemnavigation")
        val iPrivacyMcuServiceClass =
            notificationsClassLoader.loadClass("humane.pmcu.IPrivacyMcuService")
        playAnimationMethod = iPrivacyMcuServiceClass.getMethod("playAnimation", Int::class.java)
        clearAllAnimationMethod =
            iPrivacyMcuServiceClass.getMethod("clearAllAnimation")

        val binder = ServiceManager.getService("humane.pmcu.IPrivacyMcuService")
        if (binder != null && binder.isBinderAlive) {
            val iPrivacyMcuServiceStub =
                notificationsClassLoader.loadClass("humane.pmcu.IPrivacyMcuService\$Stub")
            val asInterfaceMethod =
                iPrivacyMcuServiceStub.getMethod("asInterface", IBinder::class.java)
            pmcuService = asInterfaceMethod.invoke(null, binder)
            Log.d(TAG, "Connected to Privacy MCU service")
        } else {
            pmcuService = null
            Log.d(TAG, "Failed to connect to Privacy MCU service")
        }
    }
}