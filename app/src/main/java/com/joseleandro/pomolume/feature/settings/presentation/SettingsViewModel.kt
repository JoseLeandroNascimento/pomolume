package com.joseleandro.pomolume.feature.settings.presentation

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joseleandro.pomolume.R
import com.joseleandro.pomolume.feature.settings.domain.GetSettingsUseCase
import com.joseleandro.pomolume.feature.settings.domain.PomodoroSettings
import com.joseleandro.pomolume.feature.settings.domain.UpdateSettingsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SettingsUiState(
    val settings: PomodoroSettings = PomodoroSettings(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    @param:StringRes val error: Int? = null
) {
    val canEdit: Boolean get() = !isLoading && !isSaving && error == null
}

class SettingsViewModel(
    private val getSettings: GetSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val uiState = mutableState.asStateFlow()
    private val mutex = Mutex()
    private var loadJob: Job? = null
    private var hasLoaded = false

    init { load() }

    fun load() {
        loadJob?.cancel()
        hasLoaded = false
        mutableState.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                getSettings().collect { settings ->
                    hasLoaded = true
                    mutableState.update { it.copy(settings = settings, isLoading = false, error = null) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                hasLoaded = false
                mutableState.update { it.copy(isLoading = false, error = R.string.settings_error_load) }
            }
        }
    }

    fun update(transform: (PomodoroSettings) -> PomodoroSettings) {
        if (!hasLoaded || mutableState.value.isLoading) return
        viewModelScope.launch {
            mutex.withLock {
                if (!hasLoaded || mutableState.value.isLoading) return@withLock
                mutableState.update { it.copy(isSaving = true, error = null) }
                try {
                    val next = transform(mutableState.value.settings).validated()
                    updateSettings(next)
                    mutableState.update { it.copy(settings = next, isSaving = false) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.update { it.copy(isSaving = false, error = R.string.settings_error_save) }
                }
            }
        }
    }
}
