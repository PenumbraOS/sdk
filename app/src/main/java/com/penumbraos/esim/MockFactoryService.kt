package com.penumbraos.esim

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.telephony.TelephonyManager
import android.util.Log
import java.lang.Thread.sleep
import kotlin.system.exitProcess
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@SuppressLint("PrivateApi", "DiscouragedPrivateApi", "BlockedPrivateApi", "MissingPermission",
    "HardwareIds"
)
class MockFactoryService : FridaCallbackHandler {
    private var apiClient: ApiClient? = null
    
    companion object {
        // Static reference for Frida to access
        @JvmStatic
        var fridaCallbackInstance: MockFactoryService? = null
        
        fun createResources(apkDir: String): Resources {
            // Create asset manager for APK resources
            val assetManagerConstructor = AssetManager::class.java.getDeclaredConstructor()
            assetManagerConstructor.isAccessible = true
            val assetManager = assetManagerConstructor.newInstance()
            Log.w(TAG, "Asset manager: $assetManager")

            val addAssetPathMethod = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            val result = addAssetPathMethod.invoke(assetManager, apkDir)
            Log.w(TAG, "Added asset path result: $result")

            val systemResources = Resources.getSystem()
            val metrics = systemResources.displayMetrics
            val config = systemResources.configuration

            return Resources(assetManager, metrics, config)
        }

        fun printTelephonyDebugInfo(context: Context) {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val hasPrivileges = tm.hasCarrierPrivileges()
            Log.d(TAG, "Carrier Privileges: $hasPrivileges")

            val carrierName = if (tm.getSimCarrierIdName() != null) {
                tm.getSimCarrierIdName().toString()
            } else {
                "null"
            }
            val mccMnc = tm.simOperator
            Log.d(TAG, "Carrier ID Name: $carrierName, MCCMNC: $mccMnc, carrier name: ${tm.networkOperatorName}")

            val iccId = tm.simSerialNumber
            Log.d(TAG, "ICCID: $iccId")
        }
    }

    private val factoryService: android.app.Service

    constructor(classLoader: ClassLoader, apkDir: String, context: Context, activityThreadClass: Class<*>) {
        Log.i(TAG, "Starting eSIM LPA interception")
        // Set up reflection handlers
        AppProcessLooperSetupReflectionHandlers.initAndSetHandlers(classLoader)

        // Load the real FactoryService class
        val factoryServiceClass =
            classLoader.loadClass("humane.connectivity.esimlpa.factoryService")
        Log.w(TAG, "Factory service class loaded: $factoryServiceClass")

        val resources = createResources(apkDir)
        val combinedContext = CustomContext(context, resources, classLoader, false)

        Log.i(TAG, "Starting real FactoryService")

        factoryService = factoryServiceClass.newInstance() as android.app.Service
        Log.w(TAG, "Factory service instance created: $factoryService")

        val attachBaseContextMethod = android.app.Service::class.java.getDeclaredMethod(
            "attachBaseContext",
            Context::class.java
        )
        attachBaseContextMethod.isAccessible = true
        attachBaseContextMethod.invoke(factoryService, combinedContext)
        Log.w(TAG, "Factory service attached to context")
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun initializeWithInterception() {
        val baseDir = System.getProperty("java.class.path")?.removeSuffix("base.apk")
        val fridaPath = baseDir + "lib/arm64/libgadget.so"

        System.load(fridaPath)
        Log.w(TAG, "Waiting 1s for Frida to initialize script")
        sleep(1000)
        Log.w(TAG, "Loaded Frida")

        fridaCallbackInstance = this
        apiClient = ApiClient(this)

        factoryService.onCreate()
    }
    
    fun getApiClient(): ApiClient {
        return apiClient ?: throw IllegalStateException("ApiClient not initialized")
    }

    override fun onFridaCallback(operationType: String, operationName: String, result: String, isError: Boolean) {
        Log.i(TAG, "Frida callback received: $operationType.$operationName -> '$result' (error: $isError)")
        
        apiClient?.handleCallback(operationType, operationName, result, isError)
        
        if (operationType == "factoryService" && !isError) {
            when (operationName) {
                "getProfiles" -> handleProfilesData(result)
                "getActiveProfile" -> handleActiveProfileData(result)
                "getActiveProfileIccid" -> handleIccidData(result)
                "getEid" -> handleEidData(result)
                else -> Log.i(TAG, "Factory service operation '$operationName' completed")
            }
        } else if (operationType == "EuiccLevelController" && operationName == "onGetEid" && !isError) {
            handleEidData(result)
        } else if (operationType == "ProfileInfoControler" && !isError) {
            handleOperationResult(result)
        } else if (operationType == "DownloadControler") {
            handleOperationResult(result)
        }
        
        when {
            operationType == "DownloadControler" && (operationName == "onFinished" || operationName == "onError") -> {
                Log.i(TAG, "Download operation completed")
            }
            
            operationType == "ProfileInfoControler" -> {
                Log.i(TAG, "Profile operation completed")
            }
            
            operationType == "EuiccLevelController" -> {
                Log.i(TAG, "EUICC operation completed")
            }
            
            operationType == "factoryService" -> {
                Log.i(TAG, "Factory service operation '$operationName' completed")
            }
            
            else -> {
                Log.i(TAG, "Operation completed")
            }
        }
    }
    
    private fun handleProfilesData(result: String) {
        try {
            Log.i(TAG, "Parsing profiles JSON array...")
            val json = Json { ignoreUnknownKeys = true }
            val profiles = json.decodeFromString<List<ProfileData>>(result)
            
            Log.i(TAG, "Parsed ${profiles.size} profiles:")
            profiles.forEach { profile ->
                Log.i(TAG, "  - ${profile}")
            }
            
            val enabledProfile = profiles.find { it.isEnabled }
            Log.i(TAG, "Active profile: ${enabledProfile?.getDisplayName() ?: "None"}")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing profiles JSON: $e")
        }
    }
    
    private fun handleActiveProfileData(result: String) {
        try {
            Log.i(TAG, "Parsing active profile JSON...")
            val json = Json { ignoreUnknownKeys = true }
            val profile = json.decodeFromString<ProfileData>(result)
            Log.i(TAG, "Active profile: $profile")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing active profile JSON: $e")
        }
    }
    
    private fun handleIccidData(result: String) {
        try {
            Log.i(TAG, "Parsing ICCID JSON...")
            val json = Json { ignoreUnknownKeys = true }
            val iccid = json.decodeFromString<String>(result)
            Log.i(TAG, "Active profile ICCID: $iccid")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ICCID JSON: $e")
        }
    }
    
    private fun handleEidData(result: String) {
        try {
            Log.i(TAG, "Parsing EID JSON...")
            val json = Json { ignoreUnknownKeys = true }
            val eid = json.decodeFromString<String>(result)
            Log.i(TAG, "EID: $eid")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing EID JSON: $e")
        }
    }
    
    private fun handleOperationResult(result: String) {
        try {
            Log.i(TAG, "Parsing operation result JSON...")
            val json = Json { ignoreUnknownKeys = true }
            val operationResult = json.decodeFromString<OperationResult>(result)
            Log.i(TAG, "Operation ${operationResult.operation} ${if (operationResult.success) "succeeded" else "failed"}: ${operationResult.result}")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing operation result JSON: $e")
        }
    }

    internal fun getProfiles() {
        try {
            Log.i(TAG, "Executing getProfileAPI")
            val method = factoryService.javaClass.getDeclaredMethod("getProfileAPI")
            method.invoke(factoryService)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing getProfiles: ${e.message}", e)
        }
    }

    internal fun getActiveProfile() {
        try {
            Log.i(TAG, "Executing getActiveProfileAPI")
            val method = factoryService.javaClass.getDeclaredMethod("getActiveProfileAPI")
            method.invoke(factoryService)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing getActiveProfile: ${e.message}", e)
        }
    }

    internal fun getActiveProfileIccid() {
        try {
            Log.i(TAG, "Executing getActiveprofileICCIDAPI")
            val method = factoryService.javaClass.getDeclaredMethod("getActiveprofileICCIDAPI")
            method.invoke(factoryService)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing getActiveProfileIccid: ${e.message}", e)
        }
    }

    internal fun getEid() {
        try {
            Log.i(TAG, "Executing getEIDAPI")
            val method = factoryService.javaClass.getDeclaredMethod("getEIDAPI")
            method.invoke(factoryService)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing getEid: ${e.message}", e)
        }
    }

    internal fun enableProfile(iccid: String) {
        try {
            Log.i(TAG, "Executing enableProfileAPI with ICCID: $iccid")
            val method = factoryService.javaClass.getDeclaredMethod("enableProfileAPI", String::class.java)
            method.invoke(factoryService, iccid)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing enableProfile: ${e.message}", e)
        }
    }

    internal fun disableProfile(iccid: String) {
        try {
            Log.i(TAG, "Executing disableProfileAPI with ICCID: $iccid")
            val method = factoryService.javaClass.getDeclaredMethod("disableProfileAPI", String::class.java)
            method.invoke(factoryService, iccid)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing disableProfile: ${e.message}", e)
        }
    }

    internal fun deleteProfile(iccid: String) {
        try {
            Log.i(TAG, "Executing deleteProfileAPI with ICCID: $iccid")
            val method = factoryService.javaClass.getDeclaredMethod("deleteProfileAPI", String::class.java)
            method.invoke(factoryService, iccid)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing deleteProfile: ${e.message}", e)
        }
    }

    internal fun setNickname(iccid: String, nickname: String) {
        try {
            Log.i(TAG, "Executing setNicknameAPI with ICCID: $iccid, nickname: $nickname")
            val method = factoryService.javaClass.getDeclaredMethod("setNicknameAPI", String::class.java, String::class.java)
            method.invoke(factoryService, iccid, nickname)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing setNickname: ${e.message}", e)
        }
    }

    internal fun downloadProfile(activationCode: String) {
        try {
            Log.i(TAG, "Executing downloadProfileAPI with activation code: $activationCode")
            val method = factoryService.javaClass.getDeclaredMethod("downloadProfileAPI", String::class.java)
            method.invoke(factoryService, activationCode)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing downloadProfile: ${e.message}", e)
        }
    }

    internal fun downloadAndEnableProfile(activationCode: String) {
        try {
            Log.i(TAG, "Executing downloadAndEnableProfileAPI with activation code: $activationCode")
            val method = factoryService.javaClass.getDeclaredMethod("downloadAndEnableProfileAPI", String::class.java)
            method.invoke(factoryService, activationCode)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing downloadAndEnableProfile: ${e.message}", e)
        }
    }

    internal fun downloadVerifyAndEnableProfile(activationCode: String) {
        try {
            Log.i(TAG, "Executing downloadVerifyAndEnableProfileAPI with activation code: $activationCode")
            val method = factoryService.javaClass.getDeclaredMethod("downloadVerifyAndEnableProfileAPI", String::class.java)
            method.invoke(factoryService, activationCode)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing downloadVerifyAndEnableProfile: ${e.message}", e)
        }
    }
}