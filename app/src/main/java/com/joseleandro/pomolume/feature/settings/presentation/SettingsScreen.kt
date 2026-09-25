package com.joseleandro.pomolume.feature.settings.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.joseleandro.pomolume.R
import com.joseleandro.pomolume.core.design.*
import com.joseleandro.pomolume.feature.settings.domain.*

@get:StringRes
val AppTheme.labelRes: Int get() = when (this) {
    AppTheme.SYSTEM -> R.string.settings_theme_system
    AppTheme.LIGHT -> R.string.settings_theme_light
    AppTheme.DARK -> R.string.settings_theme_dark
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onUpdate: ((PomodoroSettings) -> PomodoroSettings) -> Unit,
    onEnableNotifications: () -> Unit,
    onRetry: () -> Unit
) {
    var editing by rememberSaveable { mutableStateOf<DurationSetting?>(null) }
    var showTheme by rememberSaveable { mutableStateOf(false) }
    val settings = state.settings
    val enabled = state.canEdit
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("settings_list"),
        contentPadding = PaddingValues(horizontal = AppSpacing.page, vertical = AppSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.page)
    ) {
        if (state.isLoading) item(key = "loading") {
            val label = stringResource(R.string.settings_loading)
            Box(Modifier.fillMaxWidth().padding(AppSpacing.page), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp).semantics { contentDescription = label }, strokeWidth = 2.dp)
            }
        }
        state.error?.let { message -> item(key = "error") {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.settings_retry)) }
                }
            }
        } }
        item(key = "pomodoro") {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.compact)) {
                SettingsSection(stringResource(R.string.settings_section_pomodoro), Icons.Outlined.Timer) {
                    DurationSetting.entries.forEachIndexed { index, field ->
                        if (index > 0) SettingsDivider()
                        PomodoroSettingItem(
                            stringResource(field.titleRes),
                            pluralStringResource(if (field == DurationSetting.CYCLES) R.plurals.settings_duration_cycles else R.plurals.settings_duration_minutes, field.value(settings), field.value(settings)),
                            enabled = enabled, onClick = { editing = field }
                        )
                    }
                }
                Text(stringResource(R.string.settings_next_session_hint),
                    modifier = Modifier.padding(horizontal = AppSpacing.tiny),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "automation") {
            SettingsSection(stringResource(R.string.settings_section_automation), Icons.Outlined.AutoAwesome) {
                PomodoroSettingSwitch(stringResource(R.string.settings_autostart_break), settings.autoStartBreak, enabled) { value -> onUpdate { it.copy(autoStartBreak = value) } }
                SettingsDivider()
                PomodoroSettingSwitch(stringResource(R.string.settings_autostart_focus), settings.autoStartFocus, enabled) { value -> onUpdate { it.copy(autoStartFocus = value) } }
            }
        }
        item(key = "notifications") {
            SettingsSection(stringResource(R.string.settings_section_notifications), Icons.Outlined.NotificationsNone) {
                PomodoroSettingSwitch(stringResource(R.string.settings_notify_completion), settings.notificationsEnabled, enabled) { value ->
                    if (value) onEnableNotifications() else onUpdate { it.copy(notificationsEnabled = false) }
                }
                SettingsDivider()
                PomodoroSettingSwitch(stringResource(R.string.settings_sound), settings.soundEnabled, enabled) { value -> onUpdate { it.copy(soundEnabled = value) } }
                SettingsDivider()
                PomodoroSettingSwitch(stringResource(R.string.settings_vibration), settings.vibrationEnabled, enabled) { value -> onUpdate { it.copy(vibrationEnabled = value) } }
            }
        }
        item(key = "behavior") {
            SettingsSection(stringResource(R.string.settings_section_behavior), Icons.Outlined.Tune) {
                PomodoroSettingSwitch(stringResource(R.string.settings_keep_screen_on), settings.keepScreenOn, enabled) { value -> onUpdate { it.copy(keepScreenOn = value) } }
                SettingsDivider()
                PomodoroSettingSwitch(stringResource(R.string.settings_confirm_skip), settings.confirmSkip, enabled) { value -> onUpdate { it.copy(confirmSkip = value) } }
                SettingsDivider()
                PomodoroSettingSwitch(stringResource(R.string.settings_confirm_reset), settings.confirmReset, enabled) { value -> onUpdate { it.copy(confirmReset = value) } }
            }
        }
        item(key = "personalization") {
            SettingsSection(stringResource(R.string.settings_section_personalization), Icons.Outlined.Palette) {
                TimerAppearancePicker(settings.timerAppearance, enabled) { id ->
                    onUpdate { current -> current.copy(timerAppearance = current.timerAppearance.copy(presetId = id)) }
                }
            }
        }
        item(key = "appearance") {
            SettingsSection(stringResource(R.string.settings_section_appearance), Icons.Outlined.Settings) {
                PomodoroSettingItem(stringResource(R.string.settings_theme), stringResource(settings.theme.labelRes),
                    enabled = enabled, onClick = { showTheme = true })
            }
        }
        item { Spacer(Modifier.height(AppSpacing.small)) }
    }
    editing?.let { field ->
        DurationDialog(field, field.value(settings), onDismiss = { editing = null }, onSave = { value ->
            if (enabled) onUpdate { field.apply(it, value) }
            editing = null
        })
    }
    if (showTheme) {
        ThemeDialog(
            selectedTheme = settings.theme,
            enabled = enabled,
            onSelect = { theme -> onUpdate { it.copy(theme = theme) }; showTheme = false },
            onDismiss = { showTheme = false }
        )
    }
}

@Composable
fun ThemeDialog(selectedTheme: AppTheme, enabled: Boolean = true, onSelect: (AppTheme) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column(Modifier.selectableGroup()) {
                AppTheme.entries.forEach { theme ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                        selected = selectedTheme == theme, enabled = enabled, role = Role.RadioButton,
                        onClick = { onSelect(theme) }
                    ), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedTheme == theme, onClick = null, enabled = enabled)
                        Spacer(Modifier.width(AppSpacing.medium))
                        Text(stringResource(theme.labelRes))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) } }
    )
}

@AppThemePreview
@Composable
private fun SettingsPreview() = PomoTheme {
    Surface { SettingsScreen(SettingsUiState(isLoading = false), {}, {}, {}) }
}

@AppThemePreview
@Composable
private fun ThemeDialogPreview() = PomoTheme {
    ThemeDialog(AppTheme.SYSTEM, onSelect = {}, onDismiss = {})
}

@Preview(name = "Settings large type", widthDp = 320, heightDp = 640, fontScale = 2f, showBackground = true)
@Composable
private fun AccessibleSettingsPreview() = PomoTheme {
    SettingsScreen(SettingsUiState(isLoading = false), {}, {}, {})
}
