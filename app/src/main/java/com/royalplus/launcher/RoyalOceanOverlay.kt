package com.royalplus.launcher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin

/** Soft, layered wave glints over the ocean photograph; intentionally subtle. */
@Composable
internal fun RoyalOceanOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI.toFloat()),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val shimmer by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        // Fine moving reflections stay low-opacity so the original photograph remains dominant.
        for (row in 0 until 7) {
            val yBase = h * (0.58f + row * 0.052f)
            val path = Path()
            for (step in 0..48) {
                val x = w * step / 48f
                val wave = sin((phase + step * 0.22f + row * 0.83f).toDouble()).toFloat()
                val secondary = sin((phase * 0.63f + step * 0.11f + row).toDouble()).toFloat()
                val y = yBase + wave * h * 0.0035f + secondary * h * 0.0018f
                if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = Color(0xFFBCEBFA).copy(alpha = shimmer * (0.10f + row * 0.012f)),
                style = Stroke(width = (1.0f + row * 0.18f).dp.toPx())
            )
        }
        // Scattered foam glints move horizontally like small wave crests.
        for (index in 0 until 22) {
            val xFraction = ((index * 0.137f + phase * (0.025f + (index % 3) * 0.006f)) % 1f)
            val yFraction = 0.62f + (index % 7) * 0.045f
            val glint = sin((phase * 1.2f + index * 1.71f).toDouble()).toFloat()
            val length = (w * (0.008f + (index % 4) * 0.003f)) * (0.65f + glint * 0.25f)
            drawLine(
                color = Color(0xFFD8F5FF).copy(alpha = shimmer * (0.10f + (glint + 1f) * 0.07f)),
                start = Offset(w * xFraction, h * yFraction),
                end = Offset(w * xFraction + length, h * yFraction - h * 0.0015f),
                strokeWidth = 1.2f.dp.toPx()
            )
        }
    }
}
