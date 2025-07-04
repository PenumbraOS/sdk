package com.penumbraos.sdk.api.types

import com.penumbraos.bridge.LedAnimationIpc

enum class LedAnimation(val enumValue: Int) {
    None(31),
    Video(1),
    Photo(2),
    Scan(3),
    PhoneCall(6),
    Mic(7),
    TimeOfFlight(9),
    Custom(10),
    Alert(12),
    ThermalAlert(13),
    Notification(14),
    TrustedMessage(15),
    UntrustedMessage(16),
    IncomingCall(17),
    OtaUpdate(18),
    ThermalCooldown(19);

    companion object {
        fun fromValue(value: Int): LedAnimation? {
            return try {
                LedAnimation.entries.first { it.enumValue == value }
            } catch (e: Exception) {
                null
            }
        }
    }

    fun intoIpc(): LedAnimationIpc {
        val animation = LedAnimationIpc()
        animation.enumValue = this.enumValue
        return animation
    }
}