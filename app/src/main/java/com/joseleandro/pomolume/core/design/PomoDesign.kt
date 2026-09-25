package com.joseleandro.pomolume.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joseleandro.pomolume.feature.pomodoro.domain.SessionType
import com.joseleandro.pomolume.feature.settings.domain.AppTheme

object AppSpacing {
    val tiny = 4.dp
    val small = 8.dp
    val compact = 12.dp
    val medium = 16.dp
    val page = 24.dp
    val large = 32.dp
    val spacious = 48.dp
}

object AppColors {
    val Light = lightColorScheme(
        primary = Color(0xFF6259A8), onPrimary = Color.White,
        primaryContainer = Color(0xFFE8E2FF), onPrimaryContainer = Color(0xFF272047),
        secondary = Color(0xFF536760), secondaryContainer = Color(0xFFD6E8DE),
        tertiary = Color(0xFF47677E), tertiaryContainer = Color(0xFFD2E6F6),
        background = Color(0xFFFAF9FC), onBackground = Color(0xFF26252E),
        surface = Color(0xFFFAF9FC), onSurface = Color(0xFF26252E),
        surfaceVariant = Color(0xFFE9E6EF), onSurfaceVariant = Color(0xFF625F6D),
        outline = Color(0xFF797581), outlineVariant = Color(0xFFD5D1DE)
    )
    val Dark = darkColorScheme(
        primary = Color(0xFFC9BEFF), onPrimary = Color(0xFF30265F),
        primaryContainer = Color(0xFF473E77), onPrimaryContainer = Color(0xFFE8E2FF),
        secondary = Color(0xFFB4D0BF), secondaryContainer = Color(0xFF354C40),
        tertiary = Color(0xFFA8CBE5), tertiaryContainer = Color(0xFF2D475A),
        background = Color(0xFF15151C), onBackground = Color(0xFFE8E5F0),
        surface = Color(0xFF15151C), onSurface = Color(0xFFE8E5F0),
        surfaceVariant = Color(0xFF33313E), onSurfaceVariant = Color(0xFFC6C1D1),
        outline = Color(0xFF938D9F), outlineVariant = Color(0xFF494553)
    )
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)
val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 64.sp, lineHeight = 72.sp, fontFeatureSettings = "tnum"),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
)

@Composable
fun PomoTheme(theme: AppTheme = AppTheme.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (theme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.DARK -> true
        AppTheme.LIGHT -> false
    }
    MaterialTheme(colorScheme = if (dark) AppColors.Dark else AppColors.Light, typography = AppTypography, shapes = AppShapes, content = content)
}

fun SessionType.label(): String = when (this) {
    SessionType.FOCUS -> "Sessão de foco"
    SessionType.SHORT_BREAK -> "Pausa curta"
    SessionType.LONG_BREAK -> "Pausa longa"
}

@Composable
fun sessionColor(type: SessionType): Color = when (type) {
    SessionType.FOCUS -> MaterialTheme.colorScheme.primary
    SessionType.SHORT_BREAK -> MaterialTheme.colorScheme.secondary
    SessionType.LONG_BREAK -> MaterialTheme.colorScheme.tertiary
}

fun formatTimer(millis: Long): String {
    val seconds = ((millis.coerceAtLeast(0) + 999) / 1000)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

fun formatDuration(seconds: Long): String {
    val minutes = seconds.coerceAtLeast(0) / 60
    return when {
        seconds < 60 -> "${seconds.coerceAtLeast(0)} s"
        minutes < 60 -> "$minutes min"
        minutes % 60L == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

@Composable
fun PomodoroPrimaryButton(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.widthIn(min = 200.dp).heightIn(min = 56.dp).testTag("primary_action"),
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(AppSpacing.small))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun PomodoroSecondaryButton(label: String, icon: ImageVector, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag(tag)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(AppSpacing.small))
        Text(label)
    }
}

@Composable
fun PomodoroTimer(remainingMillis: Long, progress: Float, type: SessionType, paused: Boolean) {
    Box(
        modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize().padding(AppSpacing.small),
            color = sessionColor(type), strokeWidth = 4.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeCap = StrokeCap.Round
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatTimer(remainingMillis), style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.testTag("timer_time"))
            Spacer(Modifier.height(AppSpacing.small))
            Text(
                if (paused) "No seu ritmo" else if (type == SessionType.FOCUS) "Foco agora" else "Respire um pouco",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PomodoroSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = AppSpacing.page, vertical = AppSpacing.medium))
}

@Composable
fun PomoError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(AppSpacing.page), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry) { Text("Tentar novamente") }
    }
}
