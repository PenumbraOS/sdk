package com.penumbraos.esim

import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiClient(private val mockFactoryService: MockFactoryService) {
    private val json = Json { ignoreUnknownKeys = true }
    private var pendingCallback: ((String, String, String, Boolean) -> Unit)? = null
    
    companion object {
        private const val TAG = "ApiClient"
    }
    
    fun handleCallback(operationType: String, operationName: String, result: String, isError: Boolean) {
        Log.d(TAG, "Received callback: $operationType.$operationName")
        pendingCallback?.invoke(operationType, operationName, result, isError)
        pendingCallback = null
    }
    
    suspend fun getProfiles(): List<ProfileData> = suspendCancellableCoroutine { continuation ->
        pendingCallback = { operationType, operationName, result, isError ->
            if (operationType == "factoryService" && operationName == "getProfiles") {
                if (isError) {
                    continuation.resumeWithException(ApiException("getProfiles failed: $result"))
                } else {
                    try {
                        val profiles = json.decodeFromString<List<ProfileData>>(result)
                        continuation.resume(profiles)
                    } catch (e: Exception) {
                        continuation.resumeWithException(ApiException("Failed to parse profiles: $e"))
                    }
                }
            }
        }
        
        mockFactoryService.getProfiles()
        
        continuation.invokeOnCancellation {
            pendingCallback = null
        }
    }
    
    suspend fun getActiveProfile(): ProfileData? = suspendCancellableCoroutine { continuation ->
        pendingCallback = { operationType, operationName, result, isError ->
            if (operationType == "factoryService" && operationName == "getActiveProfile") {
                if (isError) {
                    continuation.resumeWithException(ApiException("getActiveProfile failed: $result"))
                } else {
                    try {
                        if (result == "null") {
                            continuation.resume(null)
                        } else {
                            val profile = json.decodeFromString<ProfileData>(result)
                            continuation.resume(profile)
                        }
                    } catch (e: Exception) {
                        continuation.resumeWithException(ApiException("Failed to parse active profile: $e"))
                    }
                }
            }
        }
        
        mockFactoryService.getActiveProfile()
        
        continuation.invokeOnCancellation {
            pendingCallback = null
        }
    }
    
    suspend fun getActiveProfileIccid(): String? = suspendCancellableCoroutine { continuation ->
        pendingCallback = { operationType, operationName, result, isError ->
            if (operationType == "factoryService" && operationName == "getActiveProfileIccid") {
                if (isError) {
                    continuation.resumeWithException(ApiException("getActiveProfileIccid failed: $result"))
                } else {
                    try {
                        if (result == "null") {
                            continuation.resume(null)
                        } else {
                            val iccid = json.decodeFromString<String>(result)
                            continuation.resume(iccid)
                        }
                    } catch (e: Exception) {
                        continuation.resumeWithException(ApiException("Failed to parse ICCID: $e"))
                    }
                }
            }
        }
        
        mockFactoryService.getActiveProfileIccid()
        
        continuation.invokeOnCancellation {
            pendingCallback = null
        }
    }
    
    suspend fun getEid(): String = suspendCancellableCoroutine { continuation ->
        pendingCallback = { operationType, operationName, result, isError ->
            if ((operationType == "factoryService" && operationName == "getEid") ||
                (operationType == "EuiccLevelController" && operationName == "onGetEid")) {
                if (isError) {
                    continuation.resumeWithException(ApiException("getEid failed: $result"))
                } else {
                    try {
                        val eid = json.decodeFromString<String>(result)
                        continuation.resume(eid)
                    } catch (e: Exception) {
                        continuation.resumeWithException(ApiException("Failed to parse EID: $e"))
                    }
                }
            }
        }
        
        mockFactoryService.getEid()
        
        continuation.invokeOnCancellation {
            pendingCallback = null
        }
    }
    
    suspend fun enableProfile(iccid: String): OperationResult = suspendCancellableCoroutine { continuation ->
        pendingCallback = { operationType, operationName, result, isError ->
            if (operationType == "ProfileInfoControler" && operationName == "onEnable") {
                try {
                    val operationResult = json.decodeFromString<OperationResult>(result)
                    continuation.resume(operationResult)
                } catch (e: Exception) {
                    continuation.resumeWithException(ApiException("Failed to parse enable result: $e"))
                }
            }
        }
        
        mockFactoryService.enableProfile(iccid)
        
        continuation.invokeOnCancellation {
            pendingCallback = null
        }
    }
    
    suspend fun disableProfile(iccid: String): OperationResult = suspendCancellableCoroutine { continuation ->
        pendingCallback = { operationType, operationName, result, isError ->
            if (operationType == "ProfileInfoControler" && operationName == "onDisable") {
                try {
                    val operationResult = json.decodeFromString<OperationResult>(result)
                    continuation.resume(operationResult)
                } catch (e: Exception) {
                    continuation.resumeWithException(ApiException("Failed to parse disable result: $e"))
                }
            }
        }
        
        mockFactoryService.disableProfile(iccid)
        
        continuation.invokeOnCancellation {
            pendingCallback = null
        }
    }
    
    suspend fun deleteProfile(iccid: String): OperationResult = suspendCancellableCoroutine { continuation ->
        pendingCallback = { operationType, operationName, result, isError ->
            if (operationType == "ProfileInfoControler" && operationName == "onDelete") {
                try {
                    val operationResult = json.decodeFromString<OperationResult>(result)
                    continuation.resume(operationResult)
                } catch (e: Exception) {
                    continuation.resumeWithException(ApiException("Failed to parse delete result: $e"))
                }
            }
        }
        
        mockFactoryService.deleteProfile(iccid)
        
        continuation.invokeOnCancellation {
            pendingCallback = null
        }
    }
    
    suspend fun setNickname(iccid: String, nickname: String): OperationResult = suspendCancellableCoroutine { continuation ->
        pendingCallback = { operationType, operationName, result, isError ->
            if (operationType == "ProfileInfoControler" && operationName == "onsetNickName") {
                try {
                    val operationResult = json.decodeFromString<OperationResult>(result)
                    continuation.resume(operationResult)
                } catch (e: Exception) {
                    continuation.resumeWithException(ApiException("Failed to parse nickname result: $e"))
                }
            }
        }
        
        mockFactoryService.setNickname(iccid, nickname)
        
        continuation.invokeOnCancellation {
            pendingCallback = null
        }
    }
    
    suspend fun downloadProfile(activationCode: String): OperationResult = suspendCancellableCoroutine { continuation ->
        pendingCallback = { operationType, operationName, result, isError ->
            if (operationType == "DownloadControler" && (operationName == "onFinished" || operationName == "onError")) {
                try {
                    val operationResult = json.decodeFromString<OperationResult>(result)
                    continuation.resume(operationResult)
                } catch (e: Exception) {
                    continuation.resumeWithException(ApiException("Failed to parse download result: $e"))
                }
            }
        }
        
        mockFactoryService.downloadProfile(activationCode)
        
        continuation.invokeOnCancellation {
            pendingCallback = null
        }
    }
    
    suspend fun downloadAndEnableProfile(activationCode: String): OperationResult = suspendCancellableCoroutine { continuation ->
        pendingCallback = { operationType, operationName, result, isError ->
            if (operationType == "DownloadControler" && (operationName == "onFinished" || operationName == "onError")) {
                try {
                    val operationResult = json.decodeFromString<OperationResult>(result)
                    continuation.resume(operationResult)
                } catch (e: Exception) {
                    continuation.resumeWithException(ApiException("Failed to parse download result: $e"))
                }
            }
        }
        
        mockFactoryService.downloadAndEnableProfile(activationCode)
        
        continuation.invokeOnCancellation {
            pendingCallback = null
        }
    }
    
    suspend fun downloadVerifyAndEnableProfile(activationCode: String): OperationResult = suspendCancellableCoroutine { continuation ->
        pendingCallback = { operationType, operationName, result, isError ->
            if (operationType == "DownloadControler" && (operationName == "onFinished" || operationName == "onError")) {
                try {
                    val operationResult = json.decodeFromString<OperationResult>(result)
                    continuation.resume(operationResult)
                } catch (e: Exception) {
                    continuation.resumeWithException(ApiException("Failed to parse download result: $e"))
                }
            }
        }
        
        mockFactoryService.downloadVerifyAndEnableProfile(activationCode)
        
        continuation.invokeOnCancellation {
            pendingCallback = null
        }
    }
}

class ApiException(message: String) : Exception(message)