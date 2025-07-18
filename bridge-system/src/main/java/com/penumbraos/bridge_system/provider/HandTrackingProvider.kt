package com.penumbraos.bridge_system.provider

import android.content.Context
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
        val registerProjectableFlatHandCallbackMethod: Method

        constructor(classLoader: ClassLoader, service: Any) {
            this.classLoader = classLoader
            this.service = service

            triggerStartMethod = service.javaClass.getMethod("triggerStart")
            triggerStopMethod = service.javaClass.getMethod("triggerStop")
            val iHandTrackingCallbackClass =
                classLoader.loadClass("humane.handtracking.IHandTrackingCallback")
            registerProjectableFlatHandCallbackMethod =
                service.javaClass.getMethod(
                    "registerProjectableFlatHandCallback",
                    iHandTrackingCallbackClass
                )
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

    var service: HandTrackingService? = null

    override fun triggerStart() {
        service().triggerStart()
    }

    override fun triggerStop() {
        service().triggerStop()
    }

    fun service(): HandTrackingService {
        service?.let { return it }

        val handTrackingBinder =
            ServiceManager.getService("humane.handtracking.IHandTrackingService")
        val iHandTrackingServiceClass =
            classLoader.loadClass("humane.handtracking.IHandTrackingService\$Stub")
        val asInterface =
            iHandTrackingServiceClass.getMethod("asInterface", android.os.IBinder::class.java)
        val handTrackingService = asInterface.invoke(null, handTrackingBinder)

        val newService = HandTrackingService(classLoader, handTrackingService as Any)
        newService.deactivateHandTracking()
        service = newService
        return newService
    }
}