package com.penumbraos.bridge_system.provider

import android.content.Context
import android.util.Log
import com.penumbraos.bridge.IEsimCallback
import com.penumbraos.bridge.IEsimProvider
import com.penumbraos.bridge.types.EsimOperationResult
import com.penumbraos.bridge.types.EsimProfile
import com.penumbraos.bridge_system.esim.FridaCallbackHandler
import com.penumbraos.bridge_system.esim.MockFactoryService
import com.penumbraos.bridge_system.esim.OperationResult
import com.penumbraos.bridge_system.esim.ProfileData
import kotlinx.serialization.json.Json

class EsimProvider(
    private val classLoader: ClassLoader,
    private val context: Context,
) :
    IEsimProvider.Stub(), FridaCallbackHandler {
    private val mockFactoryService: MockFactoryService by lazy {
        MockFactoryService(classLoader, context, this@EsimProvider)
    }
    private val json = Json { ignoreUnknownKeys = true }
    private var activeCallback: IEsimCallback? = null
    private var pendingOperationType: String? = null
    private var pendingOperationName: String? = null

    companion object {
        private const val TAG = "EsimProvider"
    }

    override fun getProfiles(callback: IEsimCallback?) {
        Log.d(TAG, "getProfiles called")
        activeCallback = callback
        pendingOperationType = "factoryService"
        pendingOperationName = "getProfiles"
        mockFactoryService.getProfiles()
    }

    override fun getActiveProfile(callback: IEsimCallback?) {
        Log.d(TAG, "getActiveProfile called")
        activeCallback = callback
        pendingOperationType = "factoryService"
        pendingOperationName = "getActiveProfile"
        mockFactoryService.getActiveProfile()
    }

    override fun getActiveProfileIccid(callback: IEsimCallback?) {
        Log.d(TAG, "getActiveProfileIccid called")
        activeCallback = callback
        pendingOperationType = "factoryService"
        pendingOperationName = "getActiveProfileIccid"
        mockFactoryService.getActiveProfileIccid()
    }

    override fun getEid(callback: IEsimCallback?) {
        Log.d(TAG, "getEid called")
        activeCallback = callback
        pendingOperationType = "factoryService"
        pendingOperationName = "getEid"
        mockFactoryService.getEid()
    }

    override fun enableProfile(iccid: String?, callback: IEsimCallback?) {
        Log.d(TAG, "enableProfile called with ICCID: $iccid")
        if (iccid == null) {
            callback?.onError("ICCID cannot be null")
            return
        }
        activeCallback = callback
        pendingOperationType = "ProfileInfoControler"
        pendingOperationName = "onEnable"
        mockFactoryService.enableProfile(iccid)
    }

    override fun disableProfile(iccid: String?, callback: IEsimCallback?) {
        Log.d(TAG, "disableProfile called with ICCID: $iccid")
        if (iccid == null) {
            callback?.onError("ICCID cannot be null")
            return
        }
        activeCallback = callback
        pendingOperationType = "ProfileInfoControler"
        pendingOperationName = "onDisable"
        mockFactoryService.disableProfile(iccid)
    }

    override fun deleteProfile(iccid: String?, callback: IEsimCallback?) {
        Log.d(TAG, "deleteProfile called with ICCID: $iccid")
        if (iccid == null) {
            callback?.onError("ICCID cannot be null")
            return
        }
        activeCallback = callback
        pendingOperationType = "ProfileInfoControler"
        pendingOperationName = "onDelete"
        mockFactoryService.deleteProfile(iccid)
    }

    override fun setNickname(iccid: String?, nickname: String?, callback: IEsimCallback?) {
        Log.d(TAG, "setNickname called with ICCID: $iccid, nickname: $nickname")
        if (iccid == null || nickname == null) {
            callback?.onError("ICCID and nickname cannot be null")
            return
        }
        activeCallback = callback
        pendingOperationType = "ProfileInfoControler"
        pendingOperationName = "onsetNickName"
        mockFactoryService.setNickname(iccid, nickname)
    }

    override fun downloadProfile(activationCode: String?, callback: IEsimCallback?) {
        Log.d(TAG, "downloadProfile called with activation code: $activationCode")
        if (activationCode == null) {
            callback?.onError("Activation code cannot be null")
            return
        }
        activeCallback = callback
        pendingOperationType = "DownloadControler"
        pendingOperationName = "onFinished"
        mockFactoryService.downloadProfile(activationCode)
    }

    override fun downloadAndEnableProfile(activationCode: String?, callback: IEsimCallback?) {
        Log.d(TAG, "downloadAndEnableProfile called with activation code: $activationCode")
        if (activationCode == null) {
            callback?.onError("Activation code cannot be null")
            return
        }
        activeCallback = callback
        pendingOperationType = "DownloadControler"
        pendingOperationName = "onFinished"
        mockFactoryService.downloadAndEnableProfile(activationCode)
    }

    override fun downloadVerifyAndEnableProfile(activationCode: String?, callback: IEsimCallback?) {
        Log.d(TAG, "downloadVerifyAndEnableProfile called with activation code: $activationCode")
        if (activationCode == null) {
            callback?.onError("Activation code cannot be null")
            return
        }
        activeCallback = callback
        pendingOperationType = "DownloadControler"
        pendingOperationName = "onFinished"
        mockFactoryService.downloadVerifyAndEnableProfile(activationCode)
    }

    override fun onFridaCallback(
        operationType: String,
        operationName: String,
        result: String,
        isError: Boolean
    ) {
        Log.d(
            TAG,
            "Received callback: $operationType.$operationName -> '$result' (error: $isError)"
        )

        val callback = activeCallback
        if (callback == null) {
            Log.w(TAG, "No active callback for operation: $operationType.$operationName")
            return
        }

        if (pendingOperationType == operationType &&
            (pendingOperationName == operationName ||
                    (operationType == "DownloadControler" && (operationName == "onFinished" || operationName == "onError")) ||
                    (operationType == "EuiccLevelController" && operationName == "onGetEid" && pendingOperationName == "getEid") ||
                    (operationType == "factoryService" && operationName == "setSysProp" && pendingOperationName == "getEid" && result.contains(
                        "eid"
                    )))
        ) {

            if (isError) {
                callback.onError(result)
            } else {
                try {
                    when {
                        operationType == "factoryService" && operationName == "getProfiles" -> {
                            val profiles = json.decodeFromString<List<ProfileData>>(result)
                            val esimProfiles = profiles.map { it.toEsimProfile() }
                            callback.onProfiles(esimProfiles)
                        }

                        operationType == "factoryService" && operationName == "getActiveProfile" -> {
                            if (result == "null") {
                                callback.onActiveProfile(null)
                            } else {
                                val profile = json.decodeFromString<ProfileData>(result)
                                callback.onActiveProfile(profile.toEsimProfile())
                            }
                        }

                        operationType == "factoryService" && operationName == "getActiveProfileIccid" -> {
                            if (result == "null") {
                                callback.onActiveProfileIccid(null)
                            } else {
                                val iccid = json.decodeFromString<String>(result)
                                callback.onActiveProfileIccid(iccid)
                            }
                        }

                        (operationType == "factoryService" && operationName == "getEid") ||
                                (operationType == "EuiccLevelController" && operationName == "onGetEid") -> {
                            callback.onEid(result)
                        }

                        operationType == "ProfileInfoControler" -> {
                            val operationResult = json.decodeFromString<OperationResult>(result)
                            callback.onOperationResult(operationResult.toEsimOperationResult())
                        }

                        operationType == "DownloadControler" && (operationName == "onFinished" || operationName == "onError") -> {
                            val operationResult = json.decodeFromString<OperationResult>(result)
                            callback.onOperationResult(operationResult.toEsimOperationResult())
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse result: $e")
                    callback.onError("Failed to parse result: ${e.message}")
                }
            }

            // Clear the active callback and pending operation
            activeCallback = null
            pendingOperationType = null
            pendingOperationName = null
        }
    }

    private fun ProfileData.toEsimProfile(): EsimProfile {
        val esimProfile = EsimProfile()
        esimProfile.iccid = this.iccid
        esimProfile.profileState = this.profileState
        esimProfile.profileName = this.profileName
        esimProfile.profileNickname = this.profileNickname
        esimProfile.serviceProviderName = this.serviceProviderName
        esimProfile.index = this.index
        esimProfile.isEnabled = this.isEnabled
        esimProfile.isDisabled = this.isDisabled
        return esimProfile
    }

    private fun OperationResult.toEsimOperationResult(): EsimOperationResult {
        val esimOperationResult = EsimOperationResult()
        esimOperationResult.operation = this.operation
        esimOperationResult.result = this.result
        esimOperationResult.success = this.success
        return esimOperationResult
    }
}
