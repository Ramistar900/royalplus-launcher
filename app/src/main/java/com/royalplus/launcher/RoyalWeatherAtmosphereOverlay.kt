package com.royalplus.launcher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.sin

@Composable
internal fun RoyalWeatherAtmosphereOverlay(
    weatherCode: Int?,
    motionEnabled: Boolean
) {
    if (!motionEnabled || weatherCode == null) return
    val transition = rememberInfiniteTransition(label = "royal-weather-atmosphere")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "weather-particles"
    )

    Canvas(Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        when (weatherCode) {
            0, 1 -> {
                val center = Offset(width * 0.83f, height * 0.17f)
                val pulse = 0.92f + 0.08f * sin(progress * Math.PI * 2.0).toFloat()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x66FFF1B5), Color(0x22FFE9A5), Color.Transparent),
                        center = center,
                        radius = width * 0.27f * pulse
                    ),
                    radius = width * 0.27f * pulse,
                    center = center
                )
            }
            2, 3 -> {
                repeat(5) { index ->
                    val x = width * (0.12f + index * 0.22f)
                    val y = height * (0.13f + (index % 2) * 0.08f) +
                        sin(progress * Math.PI * 2.0 + index).toFloat() * 5.dpValue()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.035f),
                        radius = width * 0.12f,
                        center = Offset(x, y)
                    )
                }
            }
            45, 48 -> {
                repeat(5) { index ->
                    val travel = (progress * height * 0.45f + index * height * 0.18f) % (height * 1.4f)
                    val centerY = travel - height * 0.2f
                    drawCircle(
                        color = Color(0x33E8F3FA),
                        radius = width * (0.16f + (index % 2) * 0.05f),
                        center = Offset(width * (0.1f + index * 0.21f), centerY)
                    )
                }
            }
            in 51..67, in 80..82, in 95..99 -> {
                repeat(34) { index ->
                    val column = index % 17
                    val row = index / 17
                    val x = width * ((column + 0.28f + row * 0.48f) / 17.5f)
                    val y = (height * (row * 0.48f + column * 0.071f) +
                        progress * height * 1.35f) % (height + 50f) - 25f
                    drawLine(
                        color = Color(0x77E6F2FF),
                        start = Offset(x, y),
                        end = Offset(x - width * 0.008f, y + height * 0.045f),
                        strokeWidth = 1.5.dpValue(),
                        cap = StrokeCap.Round
                    )
                }
                if (weatherCode >= 95 && progress < 0.055f) {
                    val bolt = Path().apply {
                        moveTo(width * 0.73f, height * 0.16f)
                        lineTo(width * 0.69f, height * 0.25f)
                        lineTo(width * 0.74f, height * 0.25f)
                        lineTo(width * 0.70f, height * 0.34f)
                    }
                    drawPath(
                        bolt,
                        color = Color(0xFFFFF1B5),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 4.dpValue(),
                            cap = StrokeCap.Round
                        )
                    )
                }
            }
            in 71..77, 85, 86 -> {
                repeat(30) { index ->
                    val column = index % 10
                    val row = index / 10
                    val x = width * ((column + 0.2f + row * 0.27f) / 10.5f)
                    val baseY = height * (row * 0.34f + column * 0.053f)
                    val y = (baseY + progress * height * 0.95f) % (height + 18f) - 9f
                    val drift = sin(progress * Math.PI * 2.0 + index * 0.6).toFloat() * 7f
                    drawCircle(
                        color = Color(0xB8F1F8FF),
                        radius = if (index % 3 == 0) 3.dpValue() else 2.dpValue(),
                        center = Offset(x + drift, y)
                    )
                }
            }
        }
    }
}

private fun Float.dpValue(): Float = this * 1f
private fun Int.dpValue(): Float = this.toFloat()
