package com.joseleandro.pomolume.feature.settings.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
enum class AppTheme { SYSTEM, LIGHT, DARK }

@Serializable
data class PomodoroSettings(
    val focusDurationMinutes: Int = 25,
    val shortBreakDurationMinutes: Int = 5,
    val longBreakDurationMinutes: Int = 15,
    val cyclesBeforeLongBreak: Int = 4,
    val autoStartBreak: Boolean = false,
    val autoStartFocus: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val keepScreenOn: Boolean = false,
    val confirmSkip: Boolean = true,
    val confirmReset: Boolean = true,
    val theme: AppTheme = AppTheme.SYSTEM
) {
    fun validated() = copy(
        focusDurationMinutes = focusDurationMinutes.coerceIn(1, 120),
        shortBreakDurationMinutes = shortBreakDurationMinutes.coerceIn(1, 60),
        longBreakDurationMinutes = longBreakDurationMinutes.coerceIn(1, 120),
        cyclesBeforeLongBreak = cyclesBeforeLongBreak.coerceIn(1, 10)
    )
}

interface SettingsRepository {
    fun observeSettings(): Flow<PomodoroSettings>
    suspend fun updateSettings(settings: PomodoroSettings)
}
class GetSettingsUseCase(private val repository: SettingsRepository) {
    operator fun invoke() = repository.observeSettings()
}
class UpdateSettingsUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(settings: PomodoroSettings) = repository.updateSettings(settings.validated())
}
