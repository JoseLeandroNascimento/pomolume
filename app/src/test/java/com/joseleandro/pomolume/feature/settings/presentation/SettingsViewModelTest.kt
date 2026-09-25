package com.joseleandro.pomolume.feature.settings.presentation

import androidx.lifecycle.ViewModelStore
import com.joseleandro.pomolume.R
import com.joseleandro.pomolume.feature.settings.domain.*
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { store.clear(); Dispatchers.resetMain() }

    @Test fun updatesAreBlockedBeforeInitialSettingsHaveLoaded() = runTest(dispatcher) {
        val repository = TestSettingsRepository()
        val viewModel = create(repository)
        viewModel.update { it.copy(focusDurationMinutes = 90) }
        runCurrent()
        assertEquals(0, repository.saves)
        assertEquals(42, viewModel.uiState.value.settings.focusDurationMinutes)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test fun failedLoadCannotOverwritePersistedSettingsWithDefaults() = runTest(dispatcher) {
        val repository = TestSettingsRepository().apply { failReads = true }
        val viewModel = create(repository)
        runCurrent()
        assertEquals(R.string.settings_error_load, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.canEdit)
        viewModel.update { it.copy(focusDurationMinutes = 25) }
        runCurrent()
        assertEquals(0, repository.saves)
        assertEquals(42, repository.settings.value.focusDurationMinutes)
    }

    @Test fun retryRecoversAndKeepsOnlyOneSettingsObserver() = runTest(dispatcher) {
        val repository = TestSettingsRepository().apply { failReads = true }
        val viewModel = create(repository)
        runCurrent()
        repository.failReads = false
        viewModel.load()
        runCurrent()
        assertTrue(viewModel.uiState.value.canEdit)
        assertEquals(1, repository.activeObservers)
        viewModel.load()
        runCurrent()
        assertEquals(1, repository.activeObservers)
        assertEquals(1, repository.maxObservers)
    }

    @Test fun queuedUpdatesPreserveDurationAndAppearanceChanges() = runTest(dispatcher) {
        val repository = TestSettingsRepository()
        val viewModel = create(repository)
        runCurrent()
        viewModel.update { it.copy(focusDurationMinutes = 60) }
        viewModel.update { it.copy(timerAppearance = it.timerAppearance.copy(presetId = "sunset")) }
        runCurrent()
        assertEquals(60, repository.settings.value.focusDurationMinutes)
        assertEquals("sunset", repository.settings.value.timerAppearance.presetId)
        assertEquals(2, repository.saves)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test fun failedSaveReportsErrorWithoutPretendingSelectionWasPersisted() = runTest(dispatcher) {
        val repository = TestSettingsRepository()
        val viewModel = create(repository)
        runCurrent()
        repository.failWrites = true
        viewModel.update { it.copy(timerAppearance = TimerAppearance("ocean")) }
        runCurrent()
        assertEquals(R.string.settings_error_save, viewModel.uiState.value.error)
        assertEquals("terracotta", viewModel.uiState.value.settings.timerAppearance.presetId)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    private fun create(repository: SettingsRepository): SettingsViewModel =
        SettingsViewModel(GetSettingsUseCase(repository), UpdateSettingsUseCase(repository)).also { store.put("settings", it) }

    private class TestSettingsRepository : SettingsRepository {
        val settings = MutableStateFlow(PomodoroSettings(focusDurationMinutes = 42))
        var failReads = false
        var failWrites = false
        var saves = 0
        var activeObservers = 0
        var maxObservers = 0

        override fun observeSettings() = flow {
            if (failReads) throw IOException("Disk unavailable")
            activeObservers++
            maxObservers = maxOf(maxObservers, activeObservers)
            try { emitAll(settings) } finally { activeObservers-- }
        }

        override suspend fun updateSettings(settings: PomodoroSettings) {
            if (failWrites) throw IOException("Disk unavailable")
            saves++
            this.settings.value = settings
        }
    }
}
