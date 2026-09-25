package com.joseleandro.pomolume.core.design

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.joseleandro.pomolume.R
import com.joseleandro.pomolume.feature.settings.domain.TimerAppearance
import com.joseleandro.pomolume.feature.settings.domain.TimerPresetAccess

@Immutable
data class TimerPreset(
    val id: String,
    @param:StringRes val nameRes: Int,
    val colors: List<Color>,
    val darkColors: List<Color>,
    val access: TimerPresetAccess = TimerPresetAccess.FREE
) {
    val isGradient: Boolean get() = colors.size > 1
}

val TimerPresets = listOf(
    TimerPreset("terracotta", R.string.timer_color_terracotta, listOf(Color(0xFFA44E3C)), listOf(Color(0xFFF2A78E))),
    TimerPreset("ocean", R.string.timer_color_ocean, listOf(Color(0xFF3F678D)), listOf(Color(0xFF9AC5EB))),
    TimerPreset("sage", R.string.timer_color_sage, listOf(Color(0xFF4E7158)), listOf(Color(0xFFADD1AE))),
    TimerPreset("amber", R.string.timer_color_amber, listOf(Color(0xFF8D681F)), listOf(Color(0xFFE1C07C))),
    TimerPreset("teal", R.string.timer_color_teal, listOf(Color(0xFF26756F)), listOf(Color(0xFF8FD1C8))),
    TimerPreset("rose", R.string.timer_color_rose, listOf(Color(0xFF96536A)), listOf(Color(0xFFE8ADC2))),
    TimerPreset("sunset", R.string.timer_color_sunset, listOf(Color(0xFF985048), Color(0xFFC28045)), listOf(Color(0xFFF0A396), Color(0xFFE8BF80))),
    TimerPreset("coast", R.string.timer_color_coast, listOf(Color(0xFF416D92), Color(0xFF409585)), listOf(Color(0xFF9FC8E7), Color(0xFF9EDACE))),
    TimerPreset("forest", R.string.timer_color_forest, listOf(Color(0xFF48705B), Color(0xFF929451)), listOf(Color(0xFFA8CFB5), Color(0xFFD6D599))),
    TimerPreset("dawn", R.string.timer_color_dawn, listOf(Color(0xFFA05A72), Color(0xFFB78060)), listOf(Color(0xFFE5ADC3), Color(0xFFF0C4A5)))
)

@Composable
fun TimerPreset.resolvedColors(): List<Color> = if (LocalPomoDark.current) darkColors else colors

@Composable
fun timerColors(appearance: TimerAppearance): List<Color> =
    (TimerPresets.firstOrNull { it.id == appearance.presetId } ?: TimerPresets.first()).resolvedColors()
