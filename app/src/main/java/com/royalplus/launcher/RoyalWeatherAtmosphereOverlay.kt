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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
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
        val density = this.density

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
                        sin(progress * Math.PI * 2.0 + index).toFloat() * 5.dp.toPx()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.035f),
                        radius = width * 0.12f,
                        center = Offset(x, y)
                    )
                }
            }
            45, 48 -> {
                repeat(7) { index ->
                    val phase = ((index * 31) % 101) / 101f
                    val travel = (progress * height * (0.32f + (index % 3) * 0.07f) +
                        phase * height * 1.25f) % (height * 1.4f)
                    val centerY = travel - height * 0.2f
                    val radius = width * (0.09f + (index % 3) * 0.025f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0x1DE8F3FA), Color(0x0CE8F3FA), Color.Transparent),
                            center = Offset(width * (0.07f + index * 0.145f), centerY),
                            radius = radius
                        ),
                        radius = radius,
                        center = Offset(width * (0.07f + index * 0.145f), centerY)
                    )
                }
            }
            in 51..67, in 80..82, in 95..99 -> {
                val drizzle = weatherCode in 51..57
                val heavyRain = weatherCode in 80..82 || weatherCode in 95..99
                val particleCount = if (drizzle) 48 else if (heavyRain) 82 else 66
                val windLean = if (heavyRain) 0.035f else 0.018f

                // A soft lower haze gives the rain depth without obscuring weather details.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x071A2C3C),
                            Color(0x25101D2A)
                        ),
                        startY = height * 0.40f,
                        endY = height
                    ),
                    size = size
                )

                repeat(particleCount) { index ->
                    val xSeed = ((index * 47 + 13) % 97) / 97f
                    val ySeed = ((index * 61 + 7) % 101) / 101f
                    val speed = 0.58f + ((index * 23) % 70) / 100f
                    val lengthDp = if (drizzle) {
                        5f + ((index * 17) % 10) * 0.55f
                    } else {
                        10f + ((index * 17) % 23) * 0.72f
                    }
                    val length = lengthDp * density
                    val x = width * xSeed
                    val y = (height * ySeed + progress * height * speed * 1.15f) %
                        (height + length + 24f) - 12f
                    val lean = width * windLean * (0.55f + (index % 5) * 0.16f)
                    val opacity = if (drizzle) {
                        0.16f + ((index * 11) % 24) / 100f
                    } else {
                        0.22f + ((index * 11) % 42) / 100f
                    }
                    val stroke = (if (drizzle) 0.75f else 0.9f + (index % 4) * 0.28f) * density
                    drawLine(
                        color = Color(0xFFE8F4FC).copy(alpha = opacity),
                        start = Offset(x, y),
                        end = Offset(x - lean, y + length),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    if (index % 4 == 0) {
                        drawCircle(
                            color = Color(0xFFDDECF8).copy(alpha = opacity * 0.75f),
                            radius = (0.65f + (index % 3) * 0.18f) * density,
                            center = Offset(x - lean, y + length)
                        )
                    }
                }

                // Small expanding rings suggest drops striking a wet foreground surface.
                repeat(13) { index ->
                    val splashPhase = (progress * 1.35f + ((index * 37) % 100) / 100f) % 1f
                    if (splashPhase < 0.24f) {
                        val radius = (1.5f + splashPhase * 15f) * density
                        val alpha = ((0.24f - splashPhase) / 0.24f) * 0.25f
                        val x = width * (((index * 53 + 19) % 101) / 101f)
                        drawCircle(
                            color = Color(0xFFDDECF8).copy(alpha = alpha),
                            radius = radius,
                            center = Offset(x, height * (0.88f + (index % 3) * 0.025f)),
                            style = Stroke(width = 0.8f * density)
                        )
                    }
                }

                if (weatherCode in 95..99) {
                    val flash = when {
                        progress in 0.18f..0.205f -> (progress - 0.18f) / 0.025f
                        progress in 0.205f..0.235f -> (0.235f - progress) / 0.03f
                        else -> 0f
                    }.coerceIn(0f, 1f)
                    if (flash > 0f) {
                        drawRect(
                            color = Color(0xFFFFF7D9).copy(alpha = flash * 0.10f),
                            size = size
                        )
                        val bolt = Path().apply {
                            moveTo(width * 0.73f, height * 0.16f)
                            lineTo(width * 0.69f, height * 0.25f)
                            lineTo(width * 0.74f, height * 0.25f)
                            lineTo(width * 0.70f, height * 0.34f)
                        }
                        drawPath(
                            bolt,
                            color = Color(0xFFFFF4C7).copy(alpha = 0.45f + flash * 0.55f),
                            style = Stroke(width = (2.5f + flash * 2f) * density, cap = StrokeCap.Round)
                        )
                    }
                }
            }
            in 71..77, 85, 86 -> {
                repeat(34) { index ->
                    val xSeed = ((index * 43 + 9) % 103) / 103f
                    val ySeed = ((index * 29 + 17) % 107) / 107f
                    val speed = 0.35f + (index % 6) * 0.055f
                    val x = width * xSeed
                    val y = (height * ySeed + progress * height * speed) % (height + 22f) - 11f
                    val drift = sin(progress * Math.PI * 2.0 + index * 0.6).toFloat() * (4f + index % 7) * density
                    drawCircle(
                        color = Color(0xB8F1F8FF).copy(alpha = 0.50f + (index % 4) * 0.12f),
                        radius = (1.4f + (index % 4) * 0.55f) * density,
                        center = Offset(x + drift, y)
                    )
                }
            }
        }
    }
}
