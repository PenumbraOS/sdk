package com.penumbraos.esim

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import kotlin.concurrent.thread // More idiomatic Kotlin for starting threads

import java.lang.reflect.Method

import java.lang.reflect.Proxy

// IMPORTANT: This object should NOT have direct imports for io.reactivex.* or io.reactivex.android.*
// All interactions with those libraries are done via reflection.

object AppProcessLooperSetupReflectionHandlers {

    // Standard Android/Java imports are fine

    // Use nullable backing variables
//    private var _customMainLooper: Looper? = null

    // We will store the reflectively obtained Scheduler instance
    private var _injectedMainSchedulerInstance: Any? = null // Use Any? as we don't have the Scheduler type

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
    private const val RX3_ANDROID_SCHEDULERS_CLASS = "io.reactivex.rxjava3.android.schedulers.AndroidSchedulers"

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
        Log.w("AppProcessLooperSetup", "AppProcessLooperSetupReflectionHandlers.initAndSetHandlers() starting...")

//        // Step 1: Start the custom Looper thread
//        if (!isApp) {
//            startCustomLooperThread()
//        } else {
//            _customMainLooper = Looper.getMainLooper()
//            looperPreparedLatch.countDown()
//        }

        // Step 2: Perform reflection to find necessary RxJava/RxAndroid components
        // This must happen AFTER the looper thread *might* be started, but BEFORE
        // the handler lambda itself is invoked by AndroidSchedulers (which happens
        // during AndroidSchedulers static init).
        // We do the reflection lookup here, but the handler logic (waiting for looper)
        // is inside the created lambda/proxy.
        attemptReflectionLookup(classLoader)

        // Step 3: Create and set the RxAndroidPlugins handlers via reflection
        createAndSetRxAndroidPluginsHandlers()

        // Step 4: The main thread can optionally wait for the looper to be prepared
        // This isn't strictly necessary for the RxAndroidPlugins handler itself
        // (as the handler waits internally when called), but ensures the looper
        // is fully operational before your injected code starts running and expects it.
//        try {
//            Log.w("AppProcessLooperSetup", "Main thread waiting for custom looper to prepare before proceeding...")
//            val success = looperPreparedLatch.await(10, TimeUnit.SECONDS) // Wait up to 10 seconds
//            if (!success || _customMainLooper == null) {
//                val message = "FATAL: Custom Looper did not prepare in time after start!"
//                Log.e("AppProcessLooperSetup", message)
//                // Propagate the failure
//                throw IllegalStateException(message)
//            }
//            Log.w("AppProcessLooperSetup", "Custom Looper is fully prepared. AppProcessLooperSetupReflectionHandlers.initAndSetHandlers() complete.")
//        } catch (e: InterruptedException) {
//            Thread.currentThread().interrupt()
//            val message = "FATAL: Main thread interrupted while waiting for custom looper to start."
//            Log.e("AppProcessLooperSetup", message)
//            throw RuntimeException(message, e) // Wrap and rethrow
//        }
    }

    /**
     * Starts the dedicated thread that will run the custom Looper.
     */
//    private fun startCustomLooperThread() {
//        thread(name = "AppProcessMainLooper", isDaemon = false) {
//            try {
//                // Prepare a Looper for this thread
////                Looper.prepare()
//
//                // Store the looper and handler references
//                Looper.prepareMainLooper()
//                _customMainLooper = Looper.getMainLooper()
//                // Create handler now that looper is prepared
//
//                Log.w("AppProcessLooperSetup", "Custom Looper thread prepared: ${Thread.currentThread().name}, Looper: $_customMainLooper")
//
//                // Signal that the looper is ready. This unblocks the RxAndroidPlugins lambda
//                // (if it was already called) and any main thread waits.
//                looperPreparedLatch.countDown()
//
//                // Start the looper's message queue
//                Looper.loop() // This call blocks until quit() is called
//                Log.w("AppProcessLooperSetup", "Custom Looper thread stopping: ${Thread.currentThread().name}")
//
//            } catch (e: Throwable) {
//                Log.e("AppProcessLooperSetup", "FATAL ERROR in custom Looper thread:")
//                e.printStackTrace()
//                // Ensure latch is released even on error so main thread doesn't hang forever
//                looperPreparedLatch.countDown()
//                throw e // Re-throw to crash the thread
//            }
//        }
//    }

    /**
     * Performs the initial reflection lookups for required classes, fields, and methods.
     */
    private fun attemptReflectionLookup(classLoader: ClassLoader) {
        Log.w("AppProcessLooperSetup", "Attempting initial reflection lookup...")
        try {
            // Find core RxJava/RxAndroid classes
            rxAndroidPluginsClass = classLoader.loadClass(RX3_PLUGINS_CLASS)
            function0Class = classLoader.loadClass(RX3_FUNCTION0_CLASS) // The functional interface handler expects
            schedulerClass = classLoader.loadClass(RX3_SCHEDULER_CLASS) // The return type
            androidSchedulersClass = classLoader.loadClass(RX3_ANDROID_SCHEDULERS_CLASS)

            Log.w("AppProcessLooperSetup", "Reflection Lookup: Found RxAndroidPlugins: $rxAndroidPluginsClass")
            Log.w("AppProcessLooperSetup", "Reflection Lookup: Found Function0: $function0Class")
            Log.w("AppProcessLooperSetup", "Reflection Lookup: Found Scheduler: $schedulerClass")
            Log.w("AppProcessLooperSetup", "Reflection Lookup: Found AndroidSchedulers: $androidSchedulersClass")

            // Find the static fields in RxAndroidPlugins
            onInitMainThreadSchedulerMethod = rxAndroidPluginsClass!!.getDeclaredMethod(RX3_ON_INIT_FIELD, function0Class)
            onMainThreadSchedulerMethod = rxAndroidPluginsClass!!.getDeclaredMethod(RX3_ON_MAIN_FIELD, function0Class)

            onInitMainThreadSchedulerMethod!!.isAccessible = true
            onMainThreadSchedulerMethod!!.isAccessible = true

            Log.w("AppProcessLooperSetup", "Reflection Lookup: Found field '${RX3_ON_INIT_FIELD}': $onInitMainThreadSchedulerMethod")
            Log.w("AppProcessLooperSetup", "Reflection Lookup: Found field '${RX3_ON_MAIN_FIELD}': $onMainThreadSchedulerMethod")

            // Find the 'from(Looper)' static method in AndroidSchedulers
            fromLooperMethod = androidSchedulersClass!!.getMethod(RX3_FROM_LOOPER_METHOD, Looper::class.java)
            fromLooperMethod!!.isAccessible = true // Make sure it's accessible (it's public, but defensive)
            Log.w("AppProcessLooperSetup", "Reflection Lookup: Found method '${RX3_FROM_LOOPER_METHOD}(Looper)': $fromLooperMethod")

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
        Log.w("AppProcessLooperSetup", "Attempting to create and set RxAndroidPlugins handlers via reflection...")

        // Check if reflection lookups were successful
        val func0Class = function0Class ?: throw IllegalStateException("Function0 class not found during handler setup.")
        val schedulerClassRef = schedulerClass ?: throw IllegalStateException("Scheduler class not found during handler setup.")
        val initField = onInitMainThreadSchedulerMethod ?: throw IllegalStateException("onInitMainThreadScheduler field not found.")
        val mainField = onMainThreadSchedulerMethod ?: throw IllegalStateException("onMainThreadScheduler field not found.")
        val fromMethod = fromLooperMethod ?: throw IllegalStateException("AndroidSchedulers.from(Looper) method not found.")
        val androidSchedulersCls = androidSchedulersClass ?: throw IllegalStateException("AndroidSchedulers class not found.")


        // 1. Create the InvocationHandler for our Proxy.
        // This handler will implement the logic of the Function0.call() method.
        val handlerLogic = InvocationHandler { proxy, method, args ->
            // This code runs when Function0.call() is invoked on our proxy instance

            if (method.name == RX3_FUNCTION0_CALL_METHOD) {
                Log.w("AppProcessLooperSetup", "InvocationHandler: ${RX3_FUNCTION0_CALL_METHOD}() ${method.parameterTypes} invoked. Waiting for custom looper...")
                // Wait for the custom Looper thread to prepare the Looper
//                try {
//                    val success = looperPreparedLatch.await(20, TimeUnit.SECONDS) // Wait longer if needed
//                    if (!success) {
//                        val message = "FATAL: Custom Looper did not prepare in time within InvocationHandler!"
//                        Log.e("AppProcessLooperSetup", message)
//                        throw IllegalStateException(message) // Crash if looper isn't ready
//                    }
//                    Log.w("AppProcessLooperSetup", "InvocationHandler: Custom Looper prepared.")
//
//                } catch (e: InterruptedException) {
//                    Thread.currentThread().interrupt()
//                    val message = "FATAL: InvocationHandler thread interrupted while waiting for custom looper."
//                    Log.e("AppProcessLooperSetup", message)
//                    // Propagate the interruption as a runtime exception
//                    throw RuntimeException(message, e)
//                }

                // At this point, _customMainLooper is guaranteed non-null because the latch was released
//                val preparedLooper = _customMainLooper!!
                val preparedLooper = Looper.getMainLooper()

                // Create the scheduler instance using the reflectively found 'from(Looper)' method
                val scheduler = try {
                    fromMethod.invoke(null, preparedLooper) // Invoke static method: AndroidSchedulers.from(looper)
                } catch (e: Throwable) {
                    val message = "FATAL: Failed to create scheduler from prepared looper within InvocationHandler."
                    Log.e("AppProcessLooperSetup", "$message: ${e.message}")
                    e.printStackTrace()
                    throw RuntimeException(message, e) // Crash if scheduler creation fails
                }

                _injectedMainSchedulerInstance = scheduler // Store it for potential future use/verification
                Log.w("AppProcessLooperSetup", "InvocationHandler: Created and providing scheduler: ${scheduler::class.java.name}")
                return@InvocationHandler scheduler // Return the created scheduler instance (must match Scheduler type)

            } else {
                // Handle any other unexpected method calls on the Function0 proxy
                Log.e("AppProcessLooperSetup", "InvocationHandler: Unexpected method invoked: ${method.name}")
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

//            initField.set(null, proxyInstance) // Set onInitMainThreadScheduler
//            mainField.set(null, proxyInstance) // Set onMainThreadScheduler (can be the same handler)
            Log.w("AppProcessLooperSetup", "Successfully set RxAndroidPlugins handlers via reflection using Proxy.")

        } catch (e: Throwable) {
            Log.e("AppProcessLooperSetup", "FATAL: Failed to set RxAndroidPlugins handler fields via reflection: ${e.message}")
            e.printStackTrace()
            // If setting the fields failed, the default AndroidSchedulers will likely crash later.
            throw RuntimeException("Failed to set RxAndroidPlugins fields", e)
        }
    }
}
