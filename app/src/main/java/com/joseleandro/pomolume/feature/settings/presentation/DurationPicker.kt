package com.joseleandro.pomolume.feature.settings.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joseleandro.pomolume.R
import com.joseleandro.pomolume.core.design.AppSpacing
import com.joseleandro.pomolume.core.design.AppThemePreview
import com.joseleandro.pomolume.core.design.PomoTheme
import com.joseleandro.pomolume.feature.settings.domain.PomodoroSettings

enum class DurationSetting(@param:StringRes val titleRes: Int, val maximum: Int, val presets: List<Int>) {
    FOCUS(R.string.settings_focus_duration, 120, listOf(15, 20, 25, 30, 45, 60)),
    SHORT(R.string.settings_short_break, 60, listOf(5, 10, 15, 20)),
    LONG(R.string.settings_long_break, 120, listOf(5, 10, 15, 20)),
    CYCLES(R.string.settings_long_break_after, 10, listOf(2, 3, 4, 5, 6));

    fun value(settings: PomodoroSettings) = when (this) {
        FOCUS -> settings.focusDurationMinutes
        SHORT -> settings.shortBreakDurationMinutes
        LONG -> settings.longBreakDurationMinutes
        CYCLES -> settings.cyclesBeforeLongBreak
    }

    fun apply(settings: PomodoroSettings, value: Int): PomodoroSettings {
        val safe = value.coerceIn(1, maximum)
        return when (this) {
            FOCUS -> settings.copy(focusDurationMinutes = safe)
            SHORT -> settings.copy(shortBreakDurationMinutes = safe)
            LONG -> settings.copy(longBreakDurationMinutes = safe)
            CYCLES -> settings.copy(cyclesBeforeLongBreak = safe)
        }
    }
}

@Composable
fun DurationDialog(field: DurationSetting, initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by rememberSaveable(field) { mutableIntStateOf(initial.coerceIn(1, field.maximum)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(field.titleRes)) },
        text = { DurationPicker(field, value, onValueChange = { value = it }) },
        confirmButton = {
            TextButton(onClick = { onSave(value.coerceIn(1, field.maximum)) }) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } }
    )
}

@Composable
fun DurationPicker(field: DurationSetting, value: Int, onValueChange: (Int) -> Unit) {
    val cycles = field == DurationSetting.CYCLES
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { onValueChange(value - 1) }, enabled = value > 1,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Outlined.Remove, contentDescription = stringResource(
                    if (cycles) R.string.settings_decrease_cycles else R.string.settings_decrease_duration))
            }
            BoxWithConstraints(
                modifier = Modifier.weight(1f).padding(horizontal = AppSpacing.small),
                contentAlignment = Alignment.Center
            ) {
                val digits = value.toString()
                val fontSize = minOf(48f, maxWidth.value / (digits.length * 0.65f * LocalDensity.current.fontScale)).sp
                Text(digits, style = MaterialTheme.typography.displayMedium.copy(fontSize = fontSize, lineHeight = fontSize * 1.15f),
                    color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, maxLines = 1,
                    modifier = Modifier.testTag("duration_number"))
            }
            FilledTonalIconButton(
                onClick = { onValueChange(value + 1) }, enabled = value < field.maximum,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(
                    if (cycles) R.string.settings_increase_cycles else R.string.settings_increase_duration))
            }
        }
        Text(if (cycles) pluralStringResource(R.plurals.settings_cycles_value, value, value) else stringResource(R.string.settings_duration_value, value),
            modifier = Modifier.testTag("duration_value"), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary)
        Text(stringResource(if (cycles) R.string.settings_cycles_range else R.string.settings_duration_range, field.maximum),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Text(stringResource(R.string.settings_quick_presets), style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.Start), color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.tiny),
            maxItemsInEachRow = 3
        ) {
            field.presets.forEach { preset ->
                FilterChip(
                    selected = value == preset,
                    onClick = { onValueChange(preset) },
                    label = { Text(if (cycles) preset.toString() else stringResource(R.string.settings_duration_value, preset)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("duration_preset_$preset")
                )
            }
        }
    }
}

@AppThemePreview
@Composable
private fun DurationPickerPreview() = PomoTheme {
    Surface { Box(Modifier.width(320.dp).padding(AppSpacing.page)) { DurationPicker(DurationSetting.FOCUS, 25, {}) } }
}

@AppThemePreview
@Composable
private fun DurationDialogPreview() = PomoTheme {
    DurationDialog(DurationSetting.FOCUS, 25, {}, {})
}

@AppThemePreview
@Composable
private fun CyclePickerPreview() = PomoTheme {
    Surface { Box(Modifier.width(320.dp).padding(AppSpacing.page)) { DurationPicker(DurationSetting.CYCLES, 4, {}) } }
}

@Preview(name = "Narrow dialog, large type", widthDp = 280, heightDp = 640, fontScale = 2f, showBackground = true)
@Composable
private fun AccessibleDurationPickerPreview() = PomoTheme {
    Surface { Box(Modifier.padding(AppSpacing.page)) { DurationPicker(DurationSetting.FOCUS, 120, {}) } }
}
