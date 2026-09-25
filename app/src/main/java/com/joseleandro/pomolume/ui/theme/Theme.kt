package com.joseleandro.pomolume.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.joseleandro.pomolume.core.design.PomoTheme
import com.joseleandro.pomolume.feature.settings.domain.AppTheme

@Composable
fun PomoLumeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    PomoTheme(theme = if (darkTheme) AppTheme.DARK else AppTheme.LIGHT, content = content)
}
