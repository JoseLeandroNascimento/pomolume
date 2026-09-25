package com.joseleandro.pomolume.feature.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.pomolume.core.design.*
import com.joseleandro.pomolume.feature.settings.domain.*

enum class DurationSetting(val title: String, val maximum: Int) {
    FOCUS("Duração do foco", 120), SHORT("Pausa curta", 60),
    LONG("Pausa longa", 120), CYCLES("Pausa longa após", 10);

    fun value(settings: PomodoroSettings) = when (this) {
        FOCUS -> settings.focusDurationMinutes
        SHORT -> settings.shortBreakDurationMinutes
        LONG -> settings.longBreakDurationMinutes
        CYCLES -> settings.cyclesBeforeLongBreak
    }

    fun apply(settings: PomodoroSettings, value: Int) = when (this) {
        FOCUS -> settings.copy(focusDurationMinutes = value)
        SHORT -> settings.copy(shortBreakDurationMinutes = value)
        LONG -> settings.copy(longBreakDurationMinutes = value)
        CYCLES -> settings.copy(cyclesBeforeLongBreak = value)
    }
}

fun AppTheme.label(): String = when (this) {
    AppTheme.SYSTEM -> "Sistema"
    AppTheme.LIGHT -> "Claro"
    AppTheme.DARK -> "Escuro"
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
    val enabled = !state.isLoading && !state.isSaving
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("settings_list"), contentPadding = PaddingValues(bottom = AppSpacing.large)) {
        if (state.isLoading) item {
            Box(Modifier.fillMaxWidth().padding(AppSpacing.page), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        state.error?.let { message -> item { PomoError(message, onRetry, Modifier.fillMaxWidth()) } }
        item { PomodoroSectionTitle("POMODORO") }
        items(DurationSetting.entries.size) { index ->
            val field = DurationSetting.entries[index]
            PomodoroSettingItem(field.title,
                "${field.value(settings)} ${if (field == DurationSetting.CYCLES) "Pomodoros" else "minutos"}",
                enabled = enabled, onClick = { editing = field })
        }
        item {
            Text("Alterações de duração valem para a próxima sessão.",
                modifier = Modifier.padding(horizontal = AppSpacing.page, vertical = AppSpacing.compact),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { PomodoroSectionTitle("AUTOMAÇÃO") }
        item { PomodoroSettingSwitch("Iniciar pausas automaticamente", settings.autoStartBreak, enabled) { value -> onUpdate { it.copy(autoStartBreak = value) } } }
        item { PomodoroSettingSwitch("Iniciar foco automaticamente", settings.autoStartFocus, enabled) { value -> onUpdate { it.copy(autoStartFocus = value) } } }
        item { PomodoroSectionTitle("NOTIFICAÇÕES") }
        item {
            PomodoroSettingSwitch("Notificar ao terminar", settings.notificationsEnabled, enabled) { value ->
                if (value) onEnableNotifications() else onUpdate { it.copy(notificationsEnabled = false) }
            }
        }
        item { PomodoroSettingSwitch("Som", settings.soundEnabled, enabled) { value -> onUpdate { it.copy(soundEnabled = value) } } }
        item { PomodoroSettingSwitch("Vibração", settings.vibrationEnabled, enabled) { value -> onUpdate { it.copy(vibrationEnabled = value) } } }
        item { PomodoroSectionTitle("COMPORTAMENTO") }
        item { PomodoroSettingSwitch("Manter tela ligada durante sessão", settings.keepScreenOn, enabled) { value -> onUpdate { it.copy(keepScreenOn = value) } } }
        item { PomodoroSettingSwitch("Confirmar antes de pular sessão", settings.confirmSkip, enabled) { value -> onUpdate { it.copy(confirmSkip = value) } } }
        item { PomodoroSettingSwitch("Confirmar antes de reiniciar", settings.confirmReset, enabled) { value -> onUpdate { it.copy(confirmReset = value) } } }
        item { PomodoroSectionTitle("APARÊNCIA") }
        item { PomodoroSettingItem("Tema", settings.theme.label(), enabled = enabled, onClick = { showTheme = true }) }
    }
    editing?.let { field ->
        DurationDialog(field, field.value(settings), onDismiss = { editing = null }, onSave = { value ->
            onUpdate { field.apply(it, value) }
            editing = null
        })
    }
    if (showTheme) {
        AlertDialog(
            onDismissRequest = { showTheme = false },
            title = { Text("Tema") },
            text = {
                Column {
                    AppTheme.entries.forEach { theme ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                            selected = settings.theme == theme, role = Role.RadioButton,
                            onClick = { onUpdate { it.copy(theme = theme) }; showTheme = false }
                        ), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = settings.theme == theme, onClick = null)
                            Spacer(Modifier.width(AppSpacing.medium))
                            Text(theme.label())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTheme = false }) { Text("Fechar") } }
        )
    }
}

@Composable
fun PomodoroSettingItem(title: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = AppSpacing.page, vertical = AppSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.tiny)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PomodoroSettingSwitch(title: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = AppSpacing.page, vertical = AppSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = AppSpacing.medium))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun DurationDialog(field: DurationSetting, initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by rememberSaveable(field) { mutableIntStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(field.title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { value-- }, enabled = value > 1) {
                        Icon(Icons.Outlined.Remove, contentDescription = "Diminuir duração")
                    }
                    Text("$value ${if (field == DurationSetting.CYCLES) "ciclos" else "min"}",
                        style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("duration_value"))
                    IconButton(onClick = { value++ }, enabled = value < field.maximum) {
                        Icon(Icons.Outlined.Add, contentDescription = "Aumentar duração")
                    }
                }
                Spacer(Modifier.height(AppSpacing.small))
                Text("De 1 a ${field.maximum} ${if (field == DurationSetting.CYCLES) "ciclos" else "minutos"}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value.coerceIn(1, field.maximum)) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() = PomoTheme { SettingsScreen(SettingsUiState(isLoading = false), {}, {}, {}) }
