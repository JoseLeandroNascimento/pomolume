package com.joseleandro.pomolume.feature.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.joseleandro.pomolume.R
import com.joseleandro.pomolume.core.design.AppSpacing
import com.joseleandro.pomolume.core.design.AppThemePreview
import com.joseleandro.pomolume.core.design.PomoTheme

@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.compact)) {
        Row(
            Modifier.padding(horizontal = AppSpacing.tiny),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) { Column(Modifier.fillMaxWidth(), content = content) }
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = AppSpacing.medium),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
fun PomodoroSettingItem(title: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = AppSpacing.medium, vertical = AppSpacing.compact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.compact)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.tiny)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.6f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun PomodoroSettingSwitch(title: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = AppSpacing.medium, vertical = AppSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.compact)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.6f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@AppThemePreview
@Composable
private fun SettingItemPreview() = PomoTheme {
    PomodoroSettingItem(stringResource(R.string.settings_focus_duration), pluralStringResource(R.plurals.settings_duration_minutes, 25, 25), onClick = {})
}

@AppThemePreview
@Composable
private fun SettingSwitchPreview() = PomoTheme {
    PomodoroSettingSwitch(stringResource(R.string.settings_autostart_break), true, onCheckedChange = {})
}

@AppThemePreview
@Composable
private fun SettingsSectionPreview() = PomoTheme {
    Surface {
        SettingsSection(stringResource(R.string.settings_section_pomodoro), Icons.Outlined.Timer, Modifier.padding(AppSpacing.page)) {
            PomodoroSettingItem(stringResource(R.string.settings_focus_duration), pluralStringResource(R.plurals.settings_duration_minutes, 25, 25), onClick = {})
            SettingsDivider()
            PomodoroSettingSwitch(stringResource(R.string.settings_autostart_break), true, onCheckedChange = {})
        }
    }
}
