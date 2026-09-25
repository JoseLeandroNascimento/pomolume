package com.joseleandro.pomolume.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.joseleandro.pomolume.R
import com.joseleandro.pomolume.core.design.PomoTheme
import com.joseleandro.pomolume.core.design.TimerPresets
import com.joseleandro.pomolume.feature.settings.domain.*
import com.joseleandro.pomolume.feature.settings.presentation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun quickFocusPresetIsSavedWithoutKeyboardInput() {
        var settings by mutableStateOf(PomodoroSettings())
        compose.setContent { PomoTheme { Surface {
            SettingsScreen(SettingsUiState(settings = settings, isLoading = false),
                onUpdate = { settings = it(settings) }, onEnableNotifications = {}, onRetry = {})
        } } }
        compose.onNodeWithText("Duração do foco").performClick()
        compose.onNodeWithTag("duration_preset_45").performClick()
        compose.onNodeWithTag("duration_value").assertTextEquals("45 min")
        compose.onNodeWithText("Salvar").performClick()
        compose.runOnIdle { assertEquals(45, settings.focusDurationMinutes) }
        compose.onNodeWithText("45 minutos").assertIsDisplayed()
    }

    @Test fun cancellingQuickPresetKeepsPreviousDuration() {
        var settings by mutableStateOf(PomodoroSettings())
        compose.setContent { PomoTheme { Surface {
            SettingsScreen(SettingsUiState(settings = settings, isLoading = false),
                onUpdate = { settings = it(settings) }, onEnableNotifications = {}, onRetry = {})
        } } }
        compose.onNodeWithText("Duração do foco").performClick()
        compose.onNodeWithTag("duration_preset_60").performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertEquals(25, settings.focusDurationMinutes) }
    }

    @Test fun pickerRespectsBothDurationLimits() {
        var value by mutableStateOf(1)
        compose.setContent { PomoTheme { Surface { DurationPicker(DurationSetting.FOCUS, value) { value = it } } } }
        compose.onNodeWithContentDescription("Diminuir duração").assertIsNotEnabled()
        compose.runOnIdle { value = 120 }
        compose.onNodeWithContentDescription("Aumentar duração").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Diminuir duração").performClick()
        compose.onNodeWithTag("duration_value").assertTextEquals("119 min")
    }

    @Test fun cyclePickerUsesCyclesAndSuggestedCounts() {
        var value by mutableStateOf(4)
        compose.setContent { PomoTheme { Surface { DurationPicker(DurationSetting.CYCLES, value) { value = it } } } }
        compose.onNodeWithTag("duration_preset_6").performClick()
        compose.onNodeWithTag("duration_value").assertTextEquals("6 ciclos")
        compose.onNodeWithContentDescription("Aumentar ciclos").performClick()
        compose.onNodeWithTag("duration_value").assertTextEquals("7 ciclos")
        compose.runOnIdle { value = 1 }
        compose.onNodeWithTag("duration_value").assertTextEquals("1 ciclo")
    }

    @Test fun solidColorSelectionHasCheckedSemanticsAndUpdatesAppearance() {
        var selected by mutableStateOf("terracotta")
        compose.setContent { PomoTheme { Surface {
            TimerPresetSelector(TimerPresets.filterNot { it.isGradient }, selected, "Cores sólidas") { selected = it }
        } } }
        compose.onNodeWithTag("timer_style_terracotta").assertIsSelected()
        compose.onNodeWithTag("timer_style_sage").performClick().assertIsSelected()
        compose.onNodeWithTag("timer_style_terracotta").assertIsNotSelected()
        compose.onNodeWithTag("timer_style_sage")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selecionado"))
        compose.runOnIdle { assertEquals("sage", selected) }
    }

    @Test fun gradientSelectionUpdatesAppearance() {
        var selected by mutableStateOf("sunset")
        compose.setContent { PomoTheme { Surface {
            TimerPresetSelector(TimerPresets.filter { it.isGradient }, selected, "Gradientes") { selected = it }
        } } }
        compose.onNodeWithTag("timer_style_sunset").assertIsSelected()
        compose.onNodeWithTag("timer_style_coast").performClick().assertIsSelected()
        compose.onNodeWithTag("timer_style_sunset").assertIsNotSelected()
        compose.runOnIdle { assertEquals("coast", selected) }
    }

    @Test fun unknownStyleDisplaysAccessibleDefaultSelection() {
        compose.setContent { PomoTheme { Surface {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TimerAppearancePicker(TimerAppearance(presetId = "future-style"), onSelect = {})
            }
        } } }
        compose.onNodeWithTag("timer_style_terracotta").assertIsSelected()
    }

    @Test fun readFailureDisablesSettingsAndOffersRetry() {
        var retried = false
        compose.setContent { PomoTheme { Surface {
            SettingsScreen(SettingsUiState(isLoading = false, error = R.string.settings_error_load),
                onUpdate = {}, onEnableNotifications = {}, onRetry = { retried = true })
        } } }
        compose.onNodeWithText("Duração do foco").assertIsNotEnabled()
        compose.onNodeWithText("Tentar novamente").performClick()
        compose.runOnIdle { assertTrue(retried) }
    }
}
