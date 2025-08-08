@file:OptIn(ExperimentalCli::class)

package com.penumbraos.esim

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Looper
import android.util.Log
import com.penumbraos.esim.MockFactoryService.Companion.printTelephonyDebugInfo
import com.penumbraos.sdk.PenumbraClient
import kotlin.concurrent.thread
import dalvik.system.DexClassLoader
import kotlinx.cli.*
import kotlinx.coroutines.*

const val TAG = "penumbra-esim"

private const val LPA_APK_PATH = "/system_ext/priv-app/humane.connectivity.esimlpa/humane.connectivity.esimlpa.apk"
// Settings appears to work in all scenarios we care about (I haven't found something where it doesn't work), so just always use it for ease of use
private const val MOCK_PACKAGE_NAME = "com.android.settings"
// private const val MOCK_PACKAGE_NAME = "com.android.phone"

class Entrypoint {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Log.w(TAG, "Args: ${args.contentToString()}")
            
            val command = Args.parse(args)
            start(command.first, *command.second)
        }

        @SuppressLint("PrivateApi", "BlockedPrivateApi", "DiscouragedPrivateApi",
            "MissingPermission", "HardwareIds", "UnsafeDynamicallyLoadedCode"
        )
        fun start(command: String, vararg params: String) {
            val classLoader =
                DexClassLoader(LPA_APK_PATH, null, null, null)

            Looper.prepareMainLooper()

            val activityThreadPair = createActivityThread(classLoader)
            val activityThread = activityThreadPair.first
            val activityThreadClass = activityThreadPair.second

            val loadedApkPair = createLoadedApk(classLoader, activityThreadClass, activityThread)
            val loadedApk = loadedApkPair.first
            val loadedApkClass = loadedApkPair.second

            val context = createContextImpl(
                classLoader,
                activityThreadClass,
                loadedApkClass,
                activityThread,
                loadedApk
            )

            Log.w(TAG, "Context: $context, package name: ${context.packageName}")

            // Set up process under ActivityThread as a kind of Zygote process
            val initializeMainlineModulesMethod =
                activityThreadClass.getDeclaredMethod("initializeMainlineModules")
            initializeMainlineModulesMethod.isAccessible = true
            initializeMainlineModulesMethod.invoke(null)

            printTelephonyDebugInfo(context)

            val client = PenumbraClient(context, disableBroadcastListener = true)

            thread(name = "FactoryService", isDaemon = false) {
                val service = MockFactoryService(classLoader, LPA_APK_PATH, context, activityThreadClass)
                service.initializeWithInterception()
                
                runBlocking {
                    val apiClient = service.getApiClient()
                    
                    try {
                        when (command) {
                            "getProfiles" -> {
                                val profiles = apiClient.getProfiles()
                                println("Found ${profiles.size} profiles:")
                                profiles.forEach { println("  - ${it.getDisplayName()} (${it.profileState}) - ${it.iccid}") }
                            }
                            "getActiveProfile" -> {
                                val active = apiClient.getActiveProfile()
                                println("Active profile: ${active?.getDisplayName() ?: "None"}")
                            }
                            "getActiveProfileIccid", "getActiveprofileICCID" -> {
                                val iccid = apiClient.getActiveProfileIccid()
                                println("Active ICCID: ${iccid ?: "None"}")
                            }
                            "getEid", "getEID" -> {
                                val eid = apiClient.getEid()
                                println("EID: $eid")
                            }
                            "enableProfile" -> {
                                val iccid = params.getOrNull(0)
                                if (iccid != null) {
                                    val result = apiClient.enableProfile(iccid)
                                    println("Enable result: ${result.result} (success: ${result.success})")
                                } else {
                                    println("Usage: enableProfile <iccid>")
                                }
                            }
                            "disableProfile" -> {
                                val iccid = params.getOrNull(0)
                                if (iccid != null) {
                                    val result = apiClient.disableProfile(iccid)
                                    println("Disable result: ${result.result} (success: ${result.success})")
                                } else {
                                    println("Usage: disableProfile <iccid>")
                                }
                            }
                            "disableActiveProfile" -> {
                                val activeProfile = apiClient.getActiveProfile()
                                if (activeProfile != null) {
                                    val result = apiClient.disableProfile(activeProfile.iccid)
                                    println("Disable result: ${result.result} (success: ${result.success})")
                                } else {
                                    println("No active profile to disable")
                                }
                            }
                            "deleteProfile" -> {
                                val iccid = params.getOrNull(0)
                                if (iccid != null) {
                                    val result = apiClient.deleteProfile(iccid)
                                    println("Delete result: ${result.result} (success: ${result.success})")
                                } else {
                                    println("Usage: deleteProfile <iccid>")
                                }
                            }
                            "setNickname" -> {
                                val iccid = params.getOrNull(0)
                                val nickname = params.getOrNull(1)
                                if (iccid != null && nickname != null) {
                                    val result = apiClient.setNickname(iccid, nickname)
                                    println("Set nickname result: ${result.result} (success: ${result.success})")
                                } else {
                                    println("Usage: setNickname <iccid> <nickname>")
                                }
                            }
                            "downloadProfile" -> {
                                val activationCode = params.getOrNull(0)
                                if (activationCode != null) {
                                    val result = apiClient.downloadProfile(activationCode)
                                    println("Download result: ${result.result} (success: ${result.success})")
                                } else {
                                    println("Usage: downloadProfile <activationCode>")
                                }
                            }
                            "downloadAndEnableProfile" -> {
                                val activationCode = params.getOrNull(0)
                                if (activationCode != null) {
                                    val result = apiClient.downloadAndEnableProfile(activationCode)
                                    println("Download and enable result: ${result.result} (success: ${result.success})")
                                } else {
                                    println("Usage: downloadAndEnableProfile <activationCode>")
                                }
                            }
                            "downloadVerifyAndEnableProfile" -> {
                                val activationCode = params.getOrNull(0)
                                if (activationCode != null) {
                                    val result = apiClient.downloadVerifyAndEnableProfile(activationCode)
                                    println("Download, verify and enable result: ${result.result} (success: ${result.success})")
                                } else {
                                    println("Usage: downloadVerifyAndEnableProfile <activationCode>")
                                }
                            }
                            else -> {
                                println("Unknown command: $command")
                                println("Available commands:")
                                println("  getProfiles - List all profiles")
                                println("  getActiveProfile - Get active profile")
                                println("  getActiveProfileIccid - Get active profile ICCID")
                                println("  getEid - Get EID")
                                println("  enableProfile <iccid> - Enable profile")
                                println("  disableProfile <iccid> - Disable profile")
                                println("  disableActiveProfile - Disable active profile")
                                println("  deleteProfile <iccid> - Delete profile")
                                println("  setNickname <iccid> <nickname> - Set profile nickname")
                                println("  downloadProfile <activationCode> - Download profile")
                                println("  downloadAndEnableProfile <activationCode> - Download and enable")
                                println("  downloadVerifyAndEnableProfile <activationCode> - Download, verify and enable")
                                println("  demo - Run comprehensive API demo")
                            }
                        }
                    } catch (e: ApiException) {
                        println("API Error: ${e.message}")
                        System.exit(1)
                    } catch (e: Exception) {
                        println("Unexpected error: ${e.message}")
                        e.printStackTrace()
                        System.exit(1)
                    }
                }
            }

            Looper.loop()

            // Keep from being GCed
            client.ping()
        }

        fun createActivityThread(classLoader: ClassLoader): Pair<Any, Class<*>> {
            val activityThreadClass = classLoader.loadClass("android.app.ActivityThread")
            val activityThreadConstructor = activityThreadClass.getDeclaredConstructor()
            activityThreadConstructor.isAccessible = true
            val mainThread = activityThreadConstructor.newInstance()
            Log.w(TAG, "Main thread: $mainThread")
            return Pair(mainThread, activityThreadClass)
        }

        fun createLoadedApk(classLoader: ClassLoader, activityThreadClass: Class<*>, mainThread: Any): Pair<Any, Class<*>> {
            val loadedApkClass = classLoader.loadClass("android.app.LoadedApk")
            val loadedApkConstructor = loadedApkClass.getDeclaredConstructor(activityThreadClass)
            loadedApkConstructor.isAccessible = true
            val loadedApk = loadedApkConstructor.newInstance(mainThread)
            Log.w(TAG, "LoadedApk: $loadedApk")

            val loadedApkPackageNameField = loadedApkClass.getDeclaredField("mPackageName")
            loadedApkPackageNameField.isAccessible = true
            loadedApkPackageNameField.set(loadedApk, MOCK_PACKAGE_NAME)

            val loadedApkApplicationInfoField = loadedApkClass.getDeclaredField("mApplicationInfo")
            loadedApkApplicationInfoField.isAccessible = true
            val applicationInfo = loadedApkApplicationInfoField.get(loadedApk) as ApplicationInfo
            applicationInfo.packageName = MOCK_PACKAGE_NAME

            return Pair(loadedApk, loadedApkClass)
        }

        fun createContextImpl(classLoader: ClassLoader, activityThreadClass: Class<*>, loadedApkClass: Class<*>, mainThread: Any, loadedApk: Any): Context {
            val contextImplClass = classLoader.loadClass("android.app.ContextImpl")
            val contextImplConstructor = contextImplClass.getDeclaredMethod("createAppContext", activityThreadClass, loadedApkClass)
            contextImplConstructor.isAccessible = true
            return contextImplConstructor.invoke(null, mainThread, loadedApk) as Context
        }
    }
}
