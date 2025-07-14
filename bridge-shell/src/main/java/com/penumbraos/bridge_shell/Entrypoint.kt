package com.penumbraos.bridge_shell

import MockContext
import android.annotation.SuppressLint
import android.os.Looper
import android.util.Log
import com.penumbraos.appprocessmocks.Common
import com.penumbraos.bridge.external.connectToBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "ShellBridgeService"

@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
class Entrypoint {
    companion object {
        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        @JvmStatic
        fun main(args: Array<String>) {
            Log.w(TAG, "Starting shell bridge")
            val classLoader = ClassLoader.getSystemClassLoader()
            val thread = Common.initialize(ClassLoader.getSystemClassLoader())
            val context =
                MockContext.createWithAppContext(classLoader, thread, "com.android.shell")

            val looper = Looper.getMainLooper()

            Runtime.getRuntime().addShutdownHook(Thread {
                Log.w(TAG, "Shutting down shell bridge")
                looper.quitSafely()
                Log.w(TAG, "Terminating")
            })

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val bridge = connectToBridge(TAG, context)
                    Log.i(TAG, "Connected to bridge-core")
                    
                    val shellProvider = ShellProvider()
                    bridge.registerShellService(shellProvider)
                    
                    Log.w(TAG, "Registered shell bridge")
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting shell bridge", e)
                    looper.quit()
                }
            }

            Log.i(TAG, "Shell bridge started")
            Looper.loop()
            Log.i(TAG, "Shell bridge quit")
        }
    }
}