package com.joseleandro.pomolume.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joseleandro.pomolume.R
import com.joseleandro.pomolume.core.design.*
import com.joseleandro.pomolume.feature.settings.domain.TimerAppearance

@Composable
fun TimerAppearancePicker(appearance: TimerAppearance, enabled: Boolean = true, onSelect: (String) -> Unit) {
    val selected = TimerPresets.firstOrNull { it.id == appearance.presetId } ?: TimerPresets.first()
    Column(Modifier.padding(AppSpacing.medium), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
        Text(stringResource(R.string.settings_timer_appearance), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_timer_appearance_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TimerPresetSelector(
            presets = TimerPresets.filterNot { it.isGradient }, selectedId = selected.id,
            title = stringResource(R.string.settings_solid_colors), enabled = enabled, onSelect = onSelect
        )
        TimerPresetSelector(
            presets = TimerPresets.filter { it.isGradient }, selectedId = selected.id,
            title = stringResource(R.string.settings_gradients), enabled = enabled, onSelect = onSelect
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.settings_style_selected, stringResource(selected.nameRes)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TimerPresetSelector(
    presets: List<TimerPreset>,
    selectedId: String,
    title: String,
    enabled: Boolean = true,
    onSelect: (String) -> Unit
) {
    val columns = if (presets.firstOrNull()?.isGradient == true) 2 else 3
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.compact)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        presets.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                row.forEach { preset ->
                    TimerPresetOption(preset, preset.id == selectedId, enabled, Modifier.weight(1f)) { onSelect(preset.id) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TimerPresetOption(preset: TimerPreset, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = preset.resolvedColors()
    val stateLabel = stringResource(if (selected) R.string.settings_selected else R.string.settings_not_selected)
    val brush = Brush.horizontalGradient(if (colors.size == 1) listOf(colors.first(), colors.first()) else colors)
    Column(
        modifier.heightIn(min = 88.dp).clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { stateDescription = stateLabel }.testTag("timer_style_${preset.id}")
            .padding(AppSpacing.tiny),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
    ) {
        val shape = if (preset.isGradient) MaterialTheme.shapes.medium else CircleShape
        Box(
            Modifier.height(48.dp)
                .then(if (preset.isGradient) Modifier.fillMaxWidth() else Modifier.width(48.dp))
                .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.Transparent, shape)
                .padding(if (selected) 4.dp else 2.dp)
                .clip(shape).background(brush),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(Modifier.size(24.dp).background(MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Text(stringResource(preset.nameRes), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth())
    }
}

@AppThemePreview
@Composable
private fun TimerAppearancePickerPreview() = PomoTheme {
    Surface { Box(Modifier.width(360.dp)) { TimerAppearancePicker(TimerAppearance(), onSelect = {}) } }
}

@AppThemePreview
@Composable
private fun SolidTimerSelectorPreview() = PomoTheme {
    Surface {
        Box(Modifier.width(360.dp).padding(AppSpacing.page)) {
            TimerPresetSelector(TimerPresets.filterNot { it.isGradient }, "terracotta", stringResource(R.string.settings_solid_colors), onSelect = {})
        }
    }
}

@AppThemePreview
@Composable
private fun GradientTimerSelectorPreview() = PomoTheme {
    Surface {
        Box(Modifier.width(360.dp).padding(AppSpacing.page)) {
            TimerPresetSelector(TimerPresets.filter { it.isGradient }, "sunset", stringResource(R.string.settings_gradients), onSelect = {})
        }
    }
}
