package com.joseleandro.pomolume.feature.settings.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.joseleandro.pomolume.feature.settings.domain.*
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TimerAppearancePersistenceTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun existingSettingsKeepTheirValuesAndGainDefaultAppearance() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(directory.root, "legacy.preferences_pb")
        }
        store.edit {
            it[DataStoreSettingsRepository.SETTINGS] = """{
                "focusDurationMinutes":42,
                "shortBreakDurationMinutes":9,
                "longBreakDurationMinutes":27,
                "cyclesBeforeLongBreak":6,
                "autoStartBreak":true,
                "notificationsEnabled":false,
                "theme":"DARK"
            }"""
        }
        val settings = DataStoreSettingsRepository(store).observeSettings().first()
        assertEquals(42, settings.focusDurationMinutes)
        assertEquals(9, settings.shortBreakDurationMinutes)
        assertEquals(27, settings.longBreakDurationMinutes)
        assertEquals(6, settings.cyclesBeforeLongBreak)
        assertTrue(settings.autoStartBreak)
        assertFalse(settings.notificationsEnabled)
        assertEquals(AppTheme.DARK, settings.theme)
        assertEquals(TimerAppearance(), settings.timerAppearance)
    }

    @Test fun selectedGradientSurvivesDataStoreRecreation() = runTest {
        val file = File(directory.root, "appearance.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }
        val expected = PomodoroSettings(focusDurationMinutes = 35, timerAppearance = TimerAppearance(presetId = "coast"))
        DataStoreSettingsRepository(store).updateSettings(expected)
        scope.cancel()
        runCurrent()

        val recreated = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        assertEquals(expected, DataStoreSettingsRepository(recreated).observeSettings().first())
    }

    @Test fun nullAppearanceAndFutureThemeDoNotDiscardOtherPreferences() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(directory.root, "forward.preferences_pb")
        }
        store.edit {
            it[DataStoreSettingsRepository.SETTINGS] = """{
                "focusDurationMinutes":60,
                "confirmReset":false,
                "theme":"FUTURE_THEME",
                "timerAppearance":null,
                "futurePreference":true
            }"""
        }
        val settings = DataStoreSettingsRepository(store).observeSettings().first()
        assertEquals(60, settings.focusDurationMinutes)
        assertFalse(settings.confirmReset)
        assertEquals(AppTheme.SYSTEM, settings.theme)
        assertEquals("terracotta", settings.timerAppearance.presetId)
    }

    @Test fun customPaletteMetadataCanRoundTripWithoutACommerceDependency() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(directory.root, "custom.preferences_pb")
        }
        val appearance = TimerAppearance(
            presetId = "sage",
            customPresets = listOf(
                CustomTimerPreset("personal", "Meu verde", listOf(0xFF4E7158L)),
                CustomTimerPreset("future", "Paleta futura", listOf(0xFF416D92L, 0xFF409585L), TimerPresetAccess.PREMIUM)
            )
        )
        val repository = DataStoreSettingsRepository(store)
        repository.updateSettings(PomodoroSettings(timerAppearance = appearance))
        assertEquals(appearance, repository.observeSettings().first().timerAppearance)
    }

    @Test fun invalidCustomMetadataIsRemovedWithoutChangingKnownSettings() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(directory.root, "invalid.preferences_pb")
        }
        val repository = DataStoreSettingsRepository(store)
        repository.updateSettings(PomodoroSettings(focusDurationMinutes = 50, timerAppearance = TimerAppearance(
            presetId = " ",
            customPresets = listOf(
                CustomTimerPreset("missing", "", emptyList()),
                CustomTimerPreset("invalidColor", "Inválido", listOf(-1)),
                CustomTimerPreset("valid", "Válido", listOf(0xFF4E7158L))
            )
        )))
        val restored = repository.observeSettings().first()
        assertEquals(50, restored.focusDurationMinutes)
        assertEquals("terracotta", restored.timerAppearance.presetId)
        assertEquals(listOf("valid"), restored.timerAppearance.customPresets.map { it.id })
    }
}
