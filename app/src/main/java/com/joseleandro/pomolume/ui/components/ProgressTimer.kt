package com.joseleandro.pomolume.ui.components

import android.content.res.Configuration
import androidx.annotation.FloatRange
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joseleandro.pomolume.ui.theme.PomoLumeTheme

private val ProgressTimerSize = 300.dp
private val ProgressTimerStrokeWidth = 16.dp

@Composable
fun ProgressTimer(
    modifier: Modifier = Modifier,
    size: Dp = ProgressTimerSize,
    trackColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f),
    color: Color = MaterialTheme.colorScheme.primary,
    @FloatRange(from = 0.0, to = 1.0) progress: Float,
    time: String
) {

    Box(
        modifier = modifier
            .size(size)
            .drawBehind {

                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(
                        width = ProgressTimerStrokeWidth.toPx(),
                    )
                )

                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(
                        width = ProgressTimerStrokeWidth.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {

        Text(
            text = time,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 60.sp
            )
        )
    }

}

@Preview(name = "light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProgressTimerPreview() {
    PomoLumeTheme(
        dynamicColor = false
    ) {
        ProgressTimer(
            progress = .5f,
            time = "25:00"
        )
    }
}