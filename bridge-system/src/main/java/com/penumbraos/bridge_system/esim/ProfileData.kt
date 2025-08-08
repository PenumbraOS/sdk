package com.penumbraos.bridge_system.esim

import kotlinx.serialization.Serializable

@Serializable
data class ProfileData(
    val iccid: String = "",
    val profileState: String = "",
    val profileName: String = "",
    val profileNickname: String = "",
    val serviceProviderName: String = "",
    val index: Int = -1,
    val isEnabled: Boolean = false,
    val isDisabled: Boolean = false
) {
    fun isActive(): Boolean = isEnabled

    fun getDisplayName(): String {
        return when {
            profileNickname.isNotBlank() -> profileNickname
            profileName.isNotBlank() -> profileName
            serviceProviderName.isNotBlank() -> serviceProviderName
            else -> "Unknown Profile"
        }
    }

    override fun toString(): String {
        return "Profile(${getDisplayName()}, $profileState, $iccid)"
    }
}

@Serializable
data class OperationResult(
    val operation: String,
    val result: String,
    val success: Boolean
)