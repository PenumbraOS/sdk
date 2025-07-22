package com.penumbraos.bridge_system.provider

import android.content.Context
import android.os.IBinder
import android.os.ServiceManager
import android.util.Log
import com.penumbraos.bridge.IHandTrackingProvider
import com.penumbraos.bridge_system.getApkClassLoader
import java.lang.reflect.Method

class HandTrackingProvider(context: Context) : IHandTrackingProvider.Stub() {
    val classLoader = getApkClassLoader(context, "humane.experience.systemnavigation")

    class HandTrackingService {
        val classLoader: ClassLoader
        val service: Any

        // Unused. Just present for deactivateHandTracking
        var flatHandService: Any? = null
        val triggerStartMethod: Method
        val triggerStopMethod: Method

        constructor(classLoader: ClassLoader, service: Any) {
            this.classLoader = classLoader
            this.service = service

            triggerStartMethod = service.javaClass.getMethod("triggerStart")
            triggerStopMethod = service.javaClass.getMethod("triggerStop")
        }

        fun triggerStart() {
            triggerStartMethod.invoke(service)
        }

        fun triggerStop() {
            triggerStopMethod.invoke(service)
        }

        // TODO: This seemed like it was disabling constant ToF power, but disabling it doesn't seem to turn it back on. Not sure why ToF is so weird
        fun deactivateHandTracking() {
            val flatHandServiceClass =
                classLoader.loadClass("humaneinternal.system.hats.FlatHandService")
            flatHandService = flatHandServiceClass.getDeclaredConstructor().newInstance()
            flatHandServiceClass.getMethod("initialize").invoke(flatHandService)
            Log.d("HandTrackingProvider", "Deactivated hand tracking")
        }
    }

    class SystemModeService {
        val classLoader: ClassLoader
        val service: Any


        constructor(classLoader: ClassLoader, service: Any) {
            this.classLoader = classLoader
            this.service = service
        }

        fun acquireHATSLock(binder: IBinder) {
            service.javaClass.getMethod("acquireHATSLock", IBinder::class.java, String::class.java)
                .invoke(service, binder, "HandTrackingProvider")
        }

        fun releaseHATSLock(binder: IBinder) {
            service.javaClass.getMethod("releaseHATSLock", IBinder::class.java)
                .invoke(service, binder)
        }
    }

    val handTrackingService: HandTrackingService by lazy {
        val handTrackingBinder =
            ServiceManager.getService("humane.handtracking.IHandTrackingService")
        val iHandTrackingServiceClass =
            classLoader.loadClass("humane.handtracking.IHandTrackingService\$Stub")
        val asInterface =
            iHandTrackingServiceClass.getMethod("asInterface", IBinder::class.java)
        val handTrackingService = asInterface.invoke(null, handTrackingBinder)

        val newService = HandTrackingService(classLoader, handTrackingService as Any)
        newService.deactivateHandTracking()
        newService
    }

    val systemModeService: SystemModeService by lazy {
        val systemModeBinder =
            ServiceManager.getService("humane.service.SystemModeService")
        val systemModeServiceClass =
            classLoader.loadClass("humane.sysmode.ISystemModeService\$Stub")
        val asInterface =
            systemModeServiceClass.getMethod("asInterface", IBinder::class.java)
        val systemModeService = asInterface.invoke(null, systemModeBinder)

        SystemModeService(classLoader, systemModeService as Any)
    }

    override fun triggerStart() {
        handTrackingService.triggerStart()
    }

    override fun triggerStop() {
        handTrackingService.triggerStop()
    }

    override fun acquireHATSLock() {
        systemModeService.acquireHATSLock(this.asBinder())
    }

    override fun releaseHATSLock() {
        systemModeService.releaseHATSLock(this.asBinder())
    }
}