package com.penumbraos.sdk.api

import android.util.Log
import com.penumbraos.bridge.IEsimCallback
import com.penumbraos.bridge.IEsimProvider
import com.penumbraos.bridge.types.EsimOperationResult
import com.penumbraos.bridge.types.EsimProfile
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "EsimClient"

class EsimClient(private val provider: IEsimProvider) {

    suspend fun getProfiles(): List<EsimProfile> {
        return suspendCancellableCoroutine { continuation ->
            val callback = object : IEsimCallback.Stub() {
                override fun onProfiles(profiles: MutableList<EsimProfile>) {
                    Log.d(TAG, "Received ${profiles.size} profiles")
                    continuation.resume(profiles.toList())
                }

                override fun onActiveProfile(profile: EsimProfile?) {}
                override fun onActiveProfileIccid(iccid: String?) {}
                override fun onEid(eid: String?) {}
                override fun onOperationResult(result: EsimOperationResult?) {}

                override fun onError(error: String) {
                    Log.e(TAG, "getProfiles error: $error")
                    continuation.resumeWithException(EsimException(error))
                }
            }

            try {
                provider.getProfiles(callback)
            } catch (e: Exception) {
                continuation.resumeWithException(EsimException("Failed to call getProfiles: ${e.message}", e))
            }

            continuation.invokeOnCancellation {
                Log.d(TAG, "getProfiles cancelled")
            }
        }
    }

    suspend fun getActiveProfile(): EsimProfile? {
        return suspendCancellableCoroutine { continuation ->
            val callback = object : IEsimCallback.Stub() {
                override fun onProfiles(profiles: MutableList<EsimProfile>) {}

                override fun onActiveProfile(profile: EsimProfile?) {
                    Log.d(TAG, "Received active profile: ${profile?.profileName ?: "null"}")
                    continuation.resume(profile)
                }

                override fun onActiveProfileIccid(iccid: String?) {}
                override fun onEid(eid: String?) {}
                override fun onOperationResult(result: EsimOperationResult?) {}

                override fun onError(error: String) {
                    Log.e(TAG, "getActiveProfile error: $error")
                    continuation.resumeWithException(EsimException(error))
                }
            }

            try {
                provider.getActiveProfile(callback)
            } catch (e: Exception) {
                continuation.resumeWithException(EsimException("Failed to call getActiveProfile: ${e.message}", e))
            }

            continuation.invokeOnCancellation {
                Log.d(TAG, "getActiveProfile cancelled")
            }
        }
    }

    suspend fun getActiveProfileIccid(): String? {
        return suspendCancellableCoroutine { continuation ->
            val callback = object : IEsimCallback.Stub() {
                override fun onProfiles(profiles: MutableList<EsimProfile>) {}
                override fun onActiveProfile(profile: EsimProfile?) {}

                override fun onActiveProfileIccid(iccid: String?) {
                    Log.d(TAG, "Received active profile ICCID: $iccid")
                    continuation.resume(iccid)
                }

                override fun onEid(eid: String?) {}
                override fun onOperationResult(result: EsimOperationResult?) {}

                override fun onError(error: String) {
                    Log.e(TAG, "getActiveProfileIccid error: $error")
                    continuation.resumeWithException(EsimException(error))
                }
            }

            try {
                provider.getActiveProfileIccid(callback)
            } catch (e: Exception) {
                continuation.resumeWithException(EsimException("Failed to call getActiveProfileIccid: ${e.message}", e))
            }

            continuation.invokeOnCancellation {
                Log.d(TAG, "getActiveProfileIccid cancelled")
            }
        }
    }

    suspend fun getEid(): String {
        return suspendCancellableCoroutine { continuation ->
            val callback = object : IEsimCallback.Stub() {
                override fun onProfiles(profiles: MutableList<EsimProfile>) {}
                override fun onActiveProfile(profile: EsimProfile?) {}
                override fun onActiveProfileIccid(iccid: String?) {}

                override fun onEid(eid: String?) {
                    Log.d(TAG, "Received EID: $eid")
                    if (eid != null) {
                        continuation.resume(eid)
                    } else {
                        continuation.resumeWithException(EsimException("EID is null"))
                    }
                }

                override fun onOperationResult(result: EsimOperationResult?) {}

                override fun onError(error: String) {
                    Log.e(TAG, "getEid error: $error")
                    continuation.resumeWithException(EsimException(error))
                }
            }

            try {
                provider.getEid(callback)
            } catch (e: Exception) {
                continuation.resumeWithException(EsimException("Failed to call getEid: ${e.message}", e))
            }

            continuation.invokeOnCancellation {
                Log.d(TAG, "getEid cancelled")
            }
        }
    }

    suspend fun enableProfile(iccid: String): EsimOperationResult {
        return suspendCancellableCoroutine { continuation ->
            val callback = object : IEsimCallback.Stub() {
                override fun onProfiles(profiles: MutableList<EsimProfile>) {}
                override fun onActiveProfile(profile: EsimProfile?) {}
                override fun onActiveProfileIccid(iccid: String?) {}
                override fun onEid(eid: String?) {}

                override fun onOperationResult(result: EsimOperationResult?) {
                    Log.d(TAG, "Enable profile operation result: ${result?.operation} - success: ${result?.success}")
                    if (result != null) {
                        continuation.resume(result)
                    } else {
                        continuation.resumeWithException(EsimException("Operation result is null"))
                    }
                }

                override fun onError(error: String) {
                    Log.e(TAG, "enableProfile error: $error")
                    continuation.resumeWithException(EsimException(error))
                }
            }

            try {
                provider.enableProfile(iccid, callback)
            } catch (e: Exception) {
                continuation.resumeWithException(EsimException("Failed to call enableProfile: ${e.message}", e))
            }

            continuation.invokeOnCancellation {
                Log.d(TAG, "enableProfile cancelled")
            }
        }
    }

    suspend fun disableProfile(iccid: String): EsimOperationResult {
        return suspendCancellableCoroutine { continuation ->
            val callback = object : IEsimCallback.Stub() {
                override fun onProfiles(profiles: MutableList<EsimProfile>) {}
                override fun onActiveProfile(profile: EsimProfile?) {}
                override fun onActiveProfileIccid(iccid: String?) {}
                override fun onEid(eid: String?) {}

                override fun onOperationResult(result: EsimOperationResult?) {
                    Log.d(TAG, "Disable profile operation result: ${result?.operation} - success: ${result?.success}")
                    if (result != null) {
                        continuation.resume(result)
                    } else {
                        continuation.resumeWithException(EsimException("Operation result is null"))
                    }
                }

                override fun onError(error: String) {
                    Log.e(TAG, "disableProfile error: $error")
                    continuation.resumeWithException(EsimException(error))
                }
            }

            try {
                provider.disableProfile(iccid, callback)
            } catch (e: Exception) {
                continuation.resumeWithException(EsimException("Failed to call disableProfile: ${e.message}", e))
            }

            continuation.invokeOnCancellation {
                Log.d(TAG, "disableProfile cancelled")
            }
        }
    }

    suspend fun deleteProfile(iccid: String): EsimOperationResult {
        return suspendCancellableCoroutine { continuation ->
            val callback = object : IEsimCallback.Stub() {
                override fun onProfiles(profiles: MutableList<EsimProfile>) {}
                override fun onActiveProfile(profile: EsimProfile?) {}
                override fun onActiveProfileIccid(iccid: String?) {}
                override fun onEid(eid: String?) {}

                override fun onOperationResult(result: EsimOperationResult?) {
                    Log.d(TAG, "Delete profile operation result: ${result?.operation} - success: ${result?.success}")
                    if (result != null) {
                        continuation.resume(result)
                    } else {
                        continuation.resumeWithException(EsimException("Operation result is null"))
                    }
                }

                override fun onError(error: String) {
                    Log.e(TAG, "deleteProfile error: $error")
                    continuation.resumeWithException(EsimException(error))
                }
            }

            try {
                provider.deleteProfile(iccid, callback)
            } catch (e: Exception) {
                continuation.resumeWithException(EsimException("Failed to call deleteProfile: ${e.message}", e))
            }

            continuation.invokeOnCancellation {
                Log.d(TAG, "deleteProfile cancelled")
            }
        }
    }

    suspend fun setNickname(iccid: String, nickname: String): EsimOperationResult {
        return suspendCancellableCoroutine { continuation ->
            val callback = object : IEsimCallback.Stub() {
                override fun onProfiles(profiles: MutableList<EsimProfile>) {}
                override fun onActiveProfile(profile: EsimProfile?) {}
                override fun onActiveProfileIccid(iccid: String?) {}
                override fun onEid(eid: String?) {}

                override fun onOperationResult(result: EsimOperationResult?) {
                    Log.d(TAG, "Set nickname operation result: ${result?.operation} - success: ${result?.success}")
                    if (result != null) {
                        continuation.resume(result)
                    } else {
                        continuation.resumeWithException(EsimException("Operation result is null"))
                    }
                }

                override fun onError(error: String) {
                    Log.e(TAG, "setNickname error: $error")
                    continuation.resumeWithException(EsimException(error))
                }
            }

            try {
                provider.setNickname(iccid, nickname, callback)
            } catch (e: Exception) {
                continuation.resumeWithException(EsimException("Failed to call setNickname: ${e.message}", e))
            }

            continuation.invokeOnCancellation {
                Log.d(TAG, "setNickname cancelled")
            }
        }
    }

    suspend fun downloadProfile(activationCode: String): EsimOperationResult {
        return suspendCancellableCoroutine { continuation ->
            val callback = object : IEsimCallback.Stub() {
                override fun onProfiles(profiles: MutableList<EsimProfile>) {}
                override fun onActiveProfile(profile: EsimProfile?) {}
                override fun onActiveProfileIccid(iccid: String?) {}
                override fun onEid(eid: String?) {}

                override fun onOperationResult(result: EsimOperationResult?) {
                    Log.d(TAG, "Download profile operation result: ${result?.operation} - success: ${result?.success}")
                    if (result != null) {
                        continuation.resume(result)
                    } else {
                        continuation.resumeWithException(EsimException("Operation result is null"))
                    }
                }

                override fun onError(error: String) {
                    Log.e(TAG, "downloadProfile error: $error")
                    continuation.resumeWithException(EsimException(error))
                }
            }

            try {
                provider.downloadProfile(activationCode, callback)
            } catch (e: Exception) {
                continuation.resumeWithException(EsimException("Failed to call downloadProfile: ${e.message}", e))
            }

            continuation.invokeOnCancellation {
                Log.d(TAG, "downloadProfile cancelled")
            }
        }
    }

    suspend fun downloadAndEnableProfile(activationCode: String): EsimOperationResult {
        return suspendCancellableCoroutine { continuation ->
            val callback = object : IEsimCallback.Stub() {
                override fun onProfiles(profiles: MutableList<EsimProfile>) {}
                override fun onActiveProfile(profile: EsimProfile?) {}
                override fun onActiveProfileIccid(iccid: String?) {}
                override fun onEid(eid: String?) {}

                override fun onOperationResult(result: EsimOperationResult?) {
                    Log.d(TAG, "Download and enable profile operation result: ${result?.operation} - success: ${result?.success}")
                    if (result != null) {
                        continuation.resume(result)
                    } else {
                        continuation.resumeWithException(EsimException("Operation result is null"))
                    }
                }

                override fun onError(error: String) {
                    Log.e(TAG, "downloadAndEnableProfile error: $error")
                    continuation.resumeWithException(EsimException(error))
                }
            }

            try {
                provider.downloadAndEnableProfile(activationCode, callback)
            } catch (e: Exception) {
                continuation.resumeWithException(EsimException("Failed to call downloadAndEnableProfile: ${e.message}", e))
            }

            continuation.invokeOnCancellation {
                Log.d(TAG, "downloadAndEnableProfile cancelled")
            }
        }
    }

    suspend fun downloadVerifyAndEnableProfile(activationCode: String): EsimOperationResult {
        return suspendCancellableCoroutine { continuation ->
            val callback = object : IEsimCallback.Stub() {
                override fun onProfiles(profiles: MutableList<EsimProfile>) {}
                override fun onActiveProfile(profile: EsimProfile?) {}
                override fun onActiveProfileIccid(iccid: String?) {}
                override fun onEid(eid: String?) {}

                override fun onOperationResult(result: EsimOperationResult?) {
                    Log.d(TAG, "Download, verify and enable profile operation result: ${result?.operation} - success: ${result?.success}")
                    if (result != null) {
                        continuation.resume(result)
                    } else {
                        continuation.resumeWithException(EsimException("Operation result is null"))
                    }
                }

                override fun onError(error: String) {
                    Log.e(TAG, "downloadVerifyAndEnableProfile error: $error")
                    continuation.resumeWithException(EsimException(error))
                }
            }

            try {
                provider.downloadVerifyAndEnableProfile(activationCode, callback)
            } catch (e: Exception) {
                continuation.resumeWithException(EsimException("Failed to call downloadVerifyAndEnableProfile: ${e.message}", e))
            }

            continuation.invokeOnCancellation {
                Log.d(TAG, "downloadVerifyAndEnableProfile cancelled")
            }
        }
    }

    /**
     * Convenience method to disable the currently active profile
     */
    suspend fun disableActiveProfile(): EsimOperationResult? {
        val activeProfile = getActiveProfile()
        return if (activeProfile != null) {
            disableProfile(activeProfile.iccid)
        } else {
            null
        }
    }

    /**
     * Get display name for a profile (nickname > profileName > serviceProviderName > "Unknown Profile")
     */
    fun EsimProfile.getDisplayName(): String {
        return when {
            !profileNickname.isNullOrBlank() -> profileNickname
            !profileName.isNullOrBlank() -> profileName
            !serviceProviderName.isNullOrBlank() -> serviceProviderName
            else -> "Unknown Profile"
        }
    }

    /**
     * Check if a profile is active (enabled)
     */
    fun EsimProfile.isActive(): Boolean = isEnabled
}

class EsimException(message: String, cause: Throwable? = null) : Exception(message, cause)