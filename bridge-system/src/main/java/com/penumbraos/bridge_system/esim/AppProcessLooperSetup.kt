package com.penumbraos.bridge_system.esim

import android.os.Looper
import android.util.Log
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch

// IMPORTANT: This object should NOT have direct imports for io.reactivex.* or io.reactivex.android.*
// All interactions with those libraries are done via reflection.

object AppProcessLooperSetupReflectionHandlers {

    // Standard Android/Java imports are fine

    // Use nullable backing variables
//    private var _customMainLooper: Looper? = null

    // We will store the reflectively obtained Scheduler instance
    private var _injectedMainSchedulerInstance: Any? =
        null // Use Any? as we don't have the Scheduler type

    // Latch to signal that the custom Looper has called Looper.prepare()
    private val looperPreparedLatch = CountDownLatch(1)

    // Reflection references to RxJava/RxAndroid classes, fields, and methods
    private var rxAndroidPluginsClass: Class<*>? = null
    private var function0Class: Class<*>? = null      // io.reactivex.rxjava3.functions.Function0
    private var schedulerClass: Class<*>? = null      // io.reactivex.rxjava3.core.Scheduler
    private var androidSchedulersClass: Class<*>? = null

    // Fields in RxAndroidPlugins to set the handlers
    private var onInitMainThreadSchedulerMethod: Method? = null
    private var onMainThreadSchedulerMethod: Method? = null

    // Method in AndroidSchedulers to create the custom scheduler
    private var fromLooperMethod: Method? = null    // AndroidSchedulers.from(Looper) method

    // Constants for reflection names (targeting RxJava 3)
    private const val RX3_PLUGINS_CLASS = "io.reactivex.rxjava3.android.plugins.RxAndroidPlugins"
    private const val RX3_FUNCTION0_CLASS = "io.reactivex.rxjava3.functions.Function"
    private const val RX3_SCHEDULER_CLASS = "io.reactivex.rxjava3.core.Scheduler"
    private const val RX3_ANDROID_SCHEDULERS_CLASS =
        "io.reactivex.rxjava3.android.schedulers.AndroidSchedulers"

    private const val RX3_ON_INIT_FIELD = "setInitMainThreadSchedulerHandler"
    private const val RX3_ON_MAIN_FIELD = "setMainThreadSchedulerHandler"

    private const val RX3_FUNCTION0_CALL_METHOD = "apply" // Method name in Function0 interface
    private const val RX3_FROM_LOOPER_METHOD = "from"

    /**
     * Initializes the custom Looper thread and sets up reflection to set
     * the RxAndroidPlugins handlers.
     * This MUST be called as early as possible in your app_process entry point.
     */
    fun initAndSetHandlers(classLoader: ClassLoader) {
        Log.w(
            "AppProcessLooperSetup",
            "AppProcessLooperSetupReflectionHandlers.initAndSetHandlers() starting..."
        )

        attemptReflectionLookup(classLoader)
        createAndSetRxAndroidPluginsHandlers()
    }

    /**
     * Performs the initial reflection lookups for required classes, fields, and methods.
     */
    private fun attemptReflectionLookup(classLoader: ClassLoader) {
        Log.w("AppProcessLooperSetup", "Attempting initial reflection lookup...")
        try {
            // Find core RxJava/RxAndroid classes
            rxAndroidPluginsClass = classLoader.loadClass(RX3_PLUGINS_CLASS)
            function0Class =
                classLoader.loadClass(RX3_FUNCTION0_CLASS) // The functional interface handler expects
            schedulerClass = classLoader.loadClass(RX3_SCHEDULER_CLASS) // The return type
            androidSchedulersClass = classLoader.loadClass(RX3_ANDROID_SCHEDULERS_CLASS)

            Log.w(
                "AppProcessLooperSetup",
                "Reflection Lookup: Found RxAndroidPlugins: $rxAndroidPluginsClass"
            )
            Log.w("AppProcessLooperSetup", "Reflection Lookup: Found Function0: $function0Class")
            Log.w("AppProcessLooperSetup", "Reflection Lookup: Found Scheduler: $schedulerClass")
            Log.w(
                "AppProcessLooperSetup",
                "Reflection Lookup: Found AndroidSchedulers: $androidSchedulersClass"
            )

            // Find the static fields in RxAndroidPlugins
            onInitMainThreadSchedulerMethod =
                rxAndroidPluginsClass!!.getDeclaredMethod(RX3_ON_INIT_FIELD, function0Class)
            onMainThreadSchedulerMethod =
                rxAndroidPluginsClass!!.getDeclaredMethod(RX3_ON_MAIN_FIELD, function0Class)

            onInitMainThreadSchedulerMethod!!.isAccessible = true
            onMainThreadSchedulerMethod!!.isAccessible = true

            Log.w(
                "AppProcessLooperSetup",
                "Reflection Lookup: Found field '${RX3_ON_INIT_FIELD}': $onInitMainThreadSchedulerMethod"
            )
            Log.w(
                "AppProcessLooperSetup",
                "Reflection Lookup: Found field '${RX3_ON_MAIN_FIELD}': $onMainThreadSchedulerMethod"
            )

            // Find the 'from(Looper)' static method in AndroidSchedulers
            fromLooperMethod =
                androidSchedulersClass!!.getMethod(RX3_FROM_LOOPER_METHOD, Looper::class.java)
            fromLooperMethod!!.isAccessible =
                true // Make sure it's accessible (it's public, but defensive)
            Log.w(
                "AppProcessLooperSetup",
                "Reflection Lookup: Found method '${RX3_FROM_LOOPER_METHOD}(Looper)': $fromLooperMethod"
            )

        } catch (e: Throwable) {
            Log.e("AppProcessLooperSetup", "FATAL: Initial reflection lookup failed: ${e.message}")
            e.printStackTrace()
            // Cannot proceed if we can't find the necessary components
            throw RuntimeException("Initial RxJava/RxAndroid reflection lookup failed", e)
        }
    }


    /**
     * Creates the handler lambda/proxy and sets the RxAndroidPlugins fields via reflection.
     * This is called AFTER the custom Looper thread is started but potentially BEFORE
     * the looper is ready. The handler lambda itself will wait for the looper.
     */
    private fun createAndSetRxAndroidPluginsHandlers() {
        Log.w(
            "AppProcessLooperSetup",
            "Attempting to create and set RxAndroidPlugins handlers via reflection..."
        )

        // Check if reflection lookups were successful
        val func0Class = function0Class
            ?: throw IllegalStateException("Function0 class not found during handler setup.")
        val schedulerClassRef = schedulerClass
            ?: throw IllegalStateException("Scheduler class not found during handler setup.")
        val initField = onInitMainThreadSchedulerMethod
            ?: throw IllegalStateException("onInitMainThreadScheduler field not found.")
        val mainField = onMainThreadSchedulerMethod
            ?: throw IllegalStateException("onMainThreadScheduler field not found.")
        val fromMethod = fromLooperMethod
            ?: throw IllegalStateException("AndroidSchedulers.from(Looper) method not found.")
        val androidSchedulersCls = androidSchedulersClass
            ?: throw IllegalStateException("AndroidSchedulers class not found.")


        // 1. Create the InvocationHandler for our Proxy.
        // This handler will implement the logic of the Function0.call() method.
        val handlerLogic = InvocationHandler { proxy, method, args ->
            // This code runs when Function0.call() is invoked on our proxy instance

            if (method.name == RX3_FUNCTION0_CALL_METHOD) {
                Log.w(
                    "AppProcessLooperSetup",
                    "InvocationHandler: ${RX3_FUNCTION0_CALL_METHOD}() ${method.parameterTypes} invoked. Waiting for custom looper..."
                )
                val preparedLooper = Looper.getMainLooper()

                // Create the scheduler instance using the reflectively found 'from(Looper)' method
                val scheduler = try {
                    fromMethod.invoke(
                        null,
                        preparedLooper
                    ) // Invoke static method: AndroidSchedulers.from(looper)
                } catch (e: Throwable) {
                    val message =
                        "FATAL: Failed to create scheduler from prepared looper within InvocationHandler."
                    Log.e("AppProcessLooperSetup", "$message: ${e.message}")
                    e.printStackTrace()
                    throw RuntimeException(message, e) // Crash if scheduler creation fails
                }

                _injectedMainSchedulerInstance =
                    scheduler // Store it for potential future use/verification
                Log.w(
                    "AppProcessLooperSetup",
                    "InvocationHandler: Created and providing scheduler: ${scheduler::class.java.name}"
                )
                return@InvocationHandler scheduler // Return the created scheduler instance (must match Scheduler type)

            } else {
                // Handle any other unexpected method calls on the Function0 proxy
                Log.e(
                    "AppProcessLooperSetup",
                    "InvocationHandler: Unexpected method invoked: ${method.name}"
                )
                // Depending on how strictly this needs to adhere, you might throw an error
                // or return a default/null. Throwing is safer for debugging.
                throw UnsupportedOperationException("Unexpected method call on Function0 proxy: ${method.name}")
            }
        }


        // 2. Create the dynamic Proxy instance that implements Function0
        // The Proxy class must be able to access the target interface (Function0).
        // This requires the Function0 class to be loaded by a class loader
        // accessible to the Proxy factory. The System ClassLoader is usually sufficient.
        val proxyInstance = Proxy.newProxyInstance(
            function0Class!!.classLoader, // Use the class loader that loaded Function0
            arrayOf(function0Class),      // The interface(s) to implement
            handlerLogic                  // The InvocationHandler with our logic
        )

        // 3. Set the static fields in RxAndroidPlugins using the proxy instance
        try {
            initField.invoke(null, proxyInstance)
            mainField.invoke(null, proxyInstance)

            Log.w(
                "AppProcessLooperSetup",
                "Successfully set RxAndroidPlugins handlers via reflection using Proxy."
            )

        } catch (e: Throwable) {
            Log.e(
                "AppProcessLooperSetup",
                "FATAL: Failed to set RxAndroidPlugins handler fields via reflection: ${e.message}"
            )
            e.printStackTrace()
            // If setting the fields failed, the default AndroidSchedulers will likely crash later.
            throw RuntimeException("Failed to set RxAndroidPlugins fields", e)
        }
    }
}
