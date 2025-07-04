package com.penumbraos.bridge_system.provider

import android.content.Context
import android.content.pm.PackageManager
import android.os.ServiceManager
import android.util.Log
import com.penumbraos.bridge.ILedProvider
import com.penumbraos.bridge.LedAnimationIpc
import dalvik.system.DexClassLoader
import java.lang.reflect.Method

private const val TAG = "LedProvider"

class LedProvider(private val context: Context) : ILedProvider.Stub() {
    private var pmcuService: Any? = null

    private lateinit var playAnimationMethod: Method
    private lateinit var clearAllAnimationMethod: Method

    override fun playAnimation(animation: LedAnimationIpc) {
        connectService()
        if (pmcuService == null) {
            throw Error("Privacy MCU service not connected")
        }

        playAnimationMethod.invoke(pmcuService, animation.enumValue)
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

        val packageManager = context.packageManager
        val apkPath: String
        try {
            val packageInfo = packageManager.getPackageInfo("humane.experience.notifications", 0)
            apkPath = packageInfo.applicationInfo!!.sourceDir
        } catch (e: PackageManager.NameNotFoundException) {
            throw Error("Could not look up humane.experience.notifications for class extraction", e)
        }

        val notificationsClassLoader = DexClassLoader(apkPath, null, null, null)
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
                iPrivacyMcuServiceStub.getMethod("asInterface", android.os.IBinder::class.java)
            pmcuService = asInterfaceMethod.invoke(null, binder)
            Log.d(TAG, "Connected to Privacy MCU service")
        } else {
            pmcuService = null
            Log.d(TAG, "Failed to connect to Privacy MCU service")
        }
    }
}