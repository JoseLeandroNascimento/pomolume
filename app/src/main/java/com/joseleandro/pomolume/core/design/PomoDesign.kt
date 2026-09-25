package com.joseleandro.pomolume.core.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.joseleandro.pomolume.R
import com.joseleandro.pomolume.feature.pomodoro.domain.SessionType
import com.joseleandro.pomolume.feature.settings.domain.AppTheme
import com.joseleandro.pomolume.feature.settings.domain.TimerAppearance
import java.util.Locale

object AppSpacing {
    val tiny = 4.dp
    val small = 8.dp
    val compact = 12.dp
    val medium = 16.dp
    val comfortable = 20.dp
    val page = 24.dp
    val large = 32.dp
    val spacious = 48.dp
}
object AppElevation { val none = 0.dp; val low = 1.dp }
object AppColors {
    val FocusColor = Color(0xFFA44E3C)
    val ShortBreakColor = Color(0xFF4E7158)
    val LongBreakColor = Color(0xFF3F678D)
    val Light = lightColorScheme(
        primary = FocusColor, onPrimary = Color.White,
        primaryContainer = Color(0xFFF6DFD5), onPrimaryContainer = Color(0xFF56281C),
        secondary = ShortBreakColor, onSecondary = Color.White,
        secondaryContainer = Color(0xFFDDE8DC), onSecondaryContainer = Color(0xFF243D2C),
        tertiary = LongBreakColor, onTertiary = Color.White,
        tertiaryContainer = Color(0xFFDCE8F2), onTertiaryContainer = Color(0xFF203B50),
        background = Color(0xFFFCF9F5), onBackground = Color(0xFF292622),
        surface = Color(0xFFFCF9F5), onSurface = Color(0xFF292622),
        surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF6F1EB),
        surfaceContainer = Color(0xFFF1EBE4), surfaceContainerHigh = Color(0xFFECE5DD),
        surfaceContainerHighest = Color(0xFFE7E0D8),
        surfaceVariant = Color(0xFFEDE5DC), onSurfaceVariant = Color(0xFF726A61),
        outline = Color(0xFF82776D), outlineVariant = Color(0xFFDCD2C8)
    )
    val Dark = darkColorScheme(
        primary = Color(0xFFF2A78E), onPrimary = Color(0xFF462316),
        primaryContainer = Color(0xFF633528), onPrimaryContainer = Color(0xFFFFDBCD),
        secondary = Color(0xFFADD1AE), onSecondary = Color(0xFF183A27),
        secondaryContainer = Color(0xFF344B3A), onSecondaryContainer = Color(0xFFD8EBD5),
        tertiary = Color(0xFF9AC5EB), onTertiary = Color(0xFF14344C),
        tertiaryContainer = Color(0xFF2D4A60), onTertiaryContainer = Color(0xFFD1E8F8),
        background = Color(0xFF191714), onBackground = Color(0xFFF0E7DD),
        surface = Color(0xFF191714), onSurface = Color(0xFFF0E7DD),
        surfaceContainerLowest = Color(0xFF14120F), surfaceContainerLow = Color(0xFF221F1B),
        surfaceContainer = Color(0xFF28241F), surfaceContainerHigh = Color(0xFF302B26),
        surfaceContainerHighest = Color(0xFF39332C),
        surfaceVariant = Color(0xFF39332C), onSurfaceVariant = Color(0xFFD0C3B6),
        outline = Color(0xFF9B8E82), outlineVariant = Color(0xFF51483F)
    )
}
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 72.sp, lineHeight = 80.sp, fontFeatureSettings = "tnum", letterSpacing = (-2).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
)
internal val LocalPomoDark = staticCompositionLocalOf { false }

@Composable
fun PomoTheme(theme: AppTheme = AppTheme.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (theme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.DARK -> true
        AppTheme.LIGHT -> false
    }
    ApplySystemBarAppearance(dark)
    CompositionLocalProvider(LocalPomoDark provides dark) {
        MaterialTheme(colorScheme = if (dark) AppColors.Dark else AppColors.Light, typography = AppTypography, shapes = AppShapes) {
            Surface(color = MaterialTheme.colorScheme.background, content = content)
        }
    }
}

@Composable
fun SessionType.label(): String = stringResource(when (this) {
    SessionType.FOCUS -> R.string.session_focus
    SessionType.SHORT_BREAK -> R.string.session_short_break
    SessionType.LONG_BREAK -> R.string.session_long_break
})

@Composable
fun sessionColor(type: SessionType): Color = when (type) {
    SessionType.FOCUS -> MaterialTheme.colorScheme.primary
    SessionType.SHORT_BREAK -> MaterialTheme.colorScheme.secondary
    SessionType.LONG_BREAK -> MaterialTheme.colorScheme.tertiary
}

fun formatTimer(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0) + 999) / 1000
    return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
}

@Composable
fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    return when {
        safe < 60 -> stringResource(R.string.duration_seconds, safe)
        minutes < 60 -> stringResource(R.string.duration_minutes, minutes)
        minutes % 60L == 0L -> stringResource(R.string.duration_hours, minutes / 60)
        else -> stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
    }
}

@Composable
fun PomodoroPrimaryButton(
    label: String, icon: ImageVector, enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit
) {
    val foreground = if (accent.luminance() > 0.4f) AppColors.Dark.background else Color.White
    Button(onClick, enabled = enabled,
        modifier = Modifier.widthIn(min = 200.dp).heightIn(min = 56.dp).testTag("primary_action"),
        shape = RoundedCornerShape(50), elevation = null,
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = foreground),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(AppSpacing.small))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun PomodoroSecondaryButton(label: String, icon: ImageVector, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag(tag),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(AppSpacing.small))
        Text(label)
    }
}

@Composable
fun PomodoroProgressRing(progress: Float, colors: List<Color>, modifier: Modifier = Modifier) {
    val value = progress.coerceIn(0f, 1f)
    val animation = animateFloatAsState(value, tween(450), label = "timerProgress")
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val brush = remember(colors) {
        if (colors.size < 2) SolidColor(colors.firstOrNull() ?: AppColors.FocusColor)
        else Brush.sweepGradient(colors + colors.first())
    }
    Canvas(modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) }) {
        val stroke = 3.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawCircle(track, radius = size.minDimension / 2 - inset, style = Stroke(stroke))
        // Animated value is read only during drawing; frames do not recompose the screen.
        rotate(-90f) {
            drawArc(brush, startAngle = 0f, sweepAngle = animation.value * 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun PomodoroTimer(
    remainingMillis: Long, progress: Float, type: SessionType, paused: Boolean,
    diameter: Dp = 300.dp, appearance: TimerAppearance = TimerAppearance()
) {
    val colors = timerColors(appearance)
    BoxWithConstraints(Modifier.widthIn(max = diameter).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        val time = formatTimer(remainingMillis)
        val remainingDescription = stringResource(R.string.timer_remaining_description, time)
        val fontScale = LocalDensity.current.fontScale
        val fitted = minOf(72f, (maxWidth.value - 48f) / (time.length * 0.60f * fontScale)).sp
        val showHint = maxWidth.value >= 176f * fontScale
        PomodoroProgressRing(progress, colors, Modifier.fillMaxSize().padding(AppSpacing.tiny))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(AppSpacing.page)) {
            Text(time, style = MaterialTheme.typography.displayLarge.copy(fontSize = fitted, lineHeight = fitted * 1.15f, letterSpacing = 0.sp),
                maxLines = 1, modifier = Modifier.testTag("timer_time").semantics { contentDescription = remainingDescription })
            if (showHint) {
                Spacer(Modifier.height(AppSpacing.small))
                Text(stringResource(if (paused) R.string.timer_paused_hint else if (type == SessionType.FOCUS) R.string.timer_focus_hint else R.string.timer_break_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
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
        TextButton(onRetry) { Text(stringResource(R.string.common_retry)) }
    }
}

@AppThemePreview @Composable
private fun PomodoroTimerPreview() = PomoTheme {
    Box(Modifier.padding(AppSpacing.page)) { PomodoroTimer(18 * 60_000L, 0.72f, SessionType.FOCUS, false) }
}
@AppThemePreview @Composable
private fun ProgressRingPreview() = PomoTheme {
    PomodoroProgressRing(0.65f, timerColors(TimerAppearance(presetId = "sunset")), Modifier.size(240.dp).padding(24.dp))
}
@AppThemePreview @Composable
private fun PrimaryButtonPreview() = PomoTheme {
    Box(Modifier.padding(24.dp)) { PomodoroPrimaryButton("Iniciar", Icons.Default.PlayArrow) {} }
}
@AppThemePreview @Composable
private fun ErrorPreview() = PomoTheme { PomoError(stringResource(R.string.common_retry), {}) }
@AppThemePreview @Composable
private fun SecondaryButtonPreview() = PomoTheme {
    PomodoroSecondaryButton(stringResource(R.string.pomodoro_reset),
        Icons.Outlined.RestartAlt, "preview_reset") {}
}
@AppThemePreview @Composable
private fun SectionTitlePreview() = PomoTheme { PomodoroSectionTitle(stringResource(R.string.pomodoro_title)) }
