package com.joseleandro.pomolume.feature.settings.domain

import kotlinx.serialization.Serializable

@Serializable
enum class TimerPresetAccess { FREE, PREMIUM }

/** Storage model for a future custom palette. No purchase or entitlement behavior is implied. */
@Serializable
data class CustomTimerPreset(
    val id: String,
    val name: String,
    val colorsArgb: List<Long>,
    val access: TimerPresetAccess = TimerPresetAccess.FREE
)

@Serializable
data class TimerAppearance(
    val presetId: String = "terracotta",
    val customPresets: List<CustomTimerPreset> = emptyList()
) {
    fun validated() = copy(
        presetId = presetId.ifBlank { "terracotta" },
        customPresets = customPresets.filter { preset ->
            preset.id.isNotBlank() && preset.name.isNotBlank() &&
                preset.colorsArgb.isNotEmpty() && preset.colorsArgb.all { it in 0..0xFFFFFFFFL }
        }.distinctBy { it.id }
    )
}
