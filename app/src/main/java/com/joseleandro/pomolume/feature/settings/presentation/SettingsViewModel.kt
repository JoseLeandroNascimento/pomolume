package com.joseleandro.pomolume.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.pomolume.feature.settings.domain.GetSettingsUseCase
import com.joseleandro.pomolume.feature.settings.domain.PomodoroSettings
import com.joseleandro.pomolume.feature.settings.domain.UpdateSettingsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SettingsUiState(
    val settings: PomodoroSettings = PomodoroSettings(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
)

class SettingsViewModel(
    private val getSettings: GetSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val uiState = mutableState.asStateFlow()
    private val mutex = Mutex()

    init { load() }

    fun load() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, error = null)
            try {
                getSettings().collect { settings ->
                    mutableState.value = mutableState.value.copy(settings = settings, isLoading = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(isLoading = false, error = "Não foi possível carregar suas configurações.")
            }
        }
    }

    fun update(transform: (PomodoroSettings) -> PomodoroSettings) {
        viewModelScope.launch {
            mutex.withLock {
                mutableState.value = mutableState.value.copy(isSaving = true, error = null)
                try {
                    val next = transform(mutableState.value.settings).validated()
                    updateSettings(next)
                    mutableState.value = mutableState.value.copy(settings = next, isSaving = false)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = mutableState.value.copy(isSaving = false, error = "Não foi possível salvar. Tente novamente.")
                }
            }
        }
    }

    fun dismissError() { mutableState.value = mutableState.value.copy(error = null) }
}
