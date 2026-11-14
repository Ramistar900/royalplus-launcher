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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Android 8-safe atmosphere: only rain, snow and wind animations are drawn. */
@Composable
internal fun RoyalWeatherAtmosphereOverlay(
    weatherCode: Int?,
    weatherDescription: String? = null,
    motionEnabled: Boolean,
    windSpeedKmh: Int? = null,
    nightMode: Boolean = false,
    sunsetMode: Boolean = false
) {
    val description = weatherDescription.orEmpty().lowercase()
    val rainWords = listOf("rain", "drizzle", "مطر", "أمطار", "رذاذ", "yağmur", "çisenti")
    val descriptionIndicatesRain = rainWords.any(description::contains)
    val numericRainCode = weatherCode?.let { it in 51..67 || it in 80..82 || it in 95..99 } == true
    val effectiveWeatherCode = if (descriptionIndicatesRain && !numericRainCode) 61 else weatherCode
    if (!motionEnabled || effectiveWeatherCode == null) return

    val transition = rememberInfiniteTransition(label = "royal-weather-atmosphere")
    val progress by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "weather-motion"
    )

    Canvas(Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas
        val wind = (windSpeedKmh ?: 8).coerceIn(0, 70)
        val windStrength = wind / 70f

        val isRain = effectiveWeatherCode in 51..67 ||
            effectiveWeatherCode in 80..82 || effectiveWeatherCode in 95..99
        if (isRain) {
            val intensity = when (effectiveWeatherCode) {
                51, 53, 61, 66, 80 -> 2
                55, 63, 67, 81, 95 -> 3
                else -> 4
            }
            drawRoyalRain(progress, width, height, intensity, windStrength)
            return@Canvas
        }

        val isSnow = effectiveWeatherCode in 71..77 || effectiveWeatherCode in 85..86
        if (isSnow) {
            val intensity = when (effectiveWeatherCode) {
                71, 85 -> 1
                73 -> 2
                else -> 3
            }
            drawRoyalSnow(progress, width, height, intensity, windStrength)
            if (wind >= 12) drawRoyalWind(progress, width, height, windStrength)
            return@Canvas
        }

        if (wind >= 18) drawRoyalWind(progress, width, height, windStrength)
    }
}

private fun DrawScope.drawRoyalRain(
    progress: Float,
    width: Float,
    height: Float,
    intensity: Int,
    windStrength: Float
) {
    // Filled teardrops only: no animated clouds, streaks, wind trails, or splash layer.
    val count = when (intensity) { 1 -> 34; 2 -> 48; 3 -> 64; else -> 82 }
    val dropLength = height * when (intensity) { 1 -> 0.020f; 2 -> 0.024f; 3 -> 0.029f; else -> 0.034f }
    val horizontalDrift = width * windStrength.coerceIn(0f, 1f) * 0.11f

    repeat(count) { index ->
        val seed = (index * 0.6180339f) % 1f
        val phase = (index * 0.381966f + (index % 5) * 0.047f) % 1f
        val speed = 0.78f + (index % 7) * 0.07f
        val y = (height * phase + progress * height * 1.35f * speed) % (height + dropLength) - dropLength
        val baseX = width * (0.018f + seed * 0.964f)
        val drift = progress * horizontalDrift * (if (index % 2 == 0) 1f else -0.36f)
        val x = (baseX + drift + width) % width
        val dropWidth = dropLength * (0.34f + (index % 4) * 0.035f)
        val alpha = (0.54f + (index % 5) * 0.055f).coerceAtMost(0.78f)

        val droplet = Path().apply {
            moveTo(x, y)
            cubicTo(x - dropWidth * 0.20f, y + dropLength * 0.24f,
                x - dropWidth * 0.92f, y + dropLength * 0.62f,
                x - dropWidth * 0.68f, y + dropLength * 0.86f)
            cubicTo(x - dropWidth * 0.34f, y + dropLength * 1.16f,
                x + dropWidth * 0.34f, y + dropLength * 1.16f,
                x + dropWidth * 0.68f, y + dropLength * 0.86f)
            cubicTo(x + dropWidth * 0.92f, y + dropLength * 0.62f,
                x + dropWidth * 0.20f, y + dropLength * 0.24f,
                x, y)
            close()
        }
        drawPath(path = droplet, color = Color(0xFFDDF2FF).copy(alpha = alpha))
        drawCircle(
            color = Color.White.copy(alpha = alpha * 0.34f),
            radius = dropWidth * 0.12f,
            center = Offset(x - dropWidth * 0.17f, y + dropLength * 0.73f)
        )
    }
}

private fun DrawScope.drawRoyalSnow(
    progress: Float,
    width: Float,
    height: Float,
    intensity: Int,
    windStrength: Float
) {
    val count = when (intensity) {
        1 -> 28
        2 -> 40
        else -> 52
    }
    repeat(count) { index ->
        val seed = (index * 0.7548777f) % 1f
        val phase = (index * 0.193f + (index % 4) * 0.067f) % 1f
        val fallSpeed = if (index % 4 == 0) 0.58f else 0.86f + (index % 3) * 0.08f
        val y = (height * phase + progress * height * 0.76f * fallSpeed) % (height * 1.08f) - height * 0.04f
        val sway = sin(progress * PI * 2.0 + index * 1.31).toFloat()
        val drift = progress * windStrength * width * 0.10f
        val x = (width * (0.025f + seed * 0.95f) + sway * (5.dp.toPx() + intensity * 1.8.dp.toPx()) +
            drift * (if (index % 2 == 0) 1f else -0.25f))
        val radius = (1.35f + (index % 4) * 0.58f + if (intensity == 3) 0.45f else 0f).dp.toPx()
        val alpha = 0.48f + (index % 3) * 0.12f

        drawCircle(Color(0xFFDCEEFF).copy(alpha = alpha * 0.45f), radius = radius * 1.8f, center = Offset(x, y))
        drawCircle(Color(0xFFF7FCFF).copy(alpha = alpha), radius = radius, center = Offset(x, y))

        // A restrained six-arm crystal on larger flakes makes snowfall look varied.
        if (index % 3 == 0) {
            val arm = radius * 2.4f
            repeat(3) { armIndex ->
                val angle = armIndex * PI / 3.0
                val dx = cos(angle).toFloat() * arm
                val dy = sin(angle).toFloat() * arm
                drawLine(
                    color = Color(0xBDEAF6FF).copy(alpha = alpha),
                    start = Offset(x - dx, y - dy),
                    end = Offset(x + dx, y + dy),
                    strokeWidth = 0.75.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private fun DrawScope.drawRoyalWind(
    progress: Float,
    width: Float,
    height: Float,
    strength: Float
) {
    val lines = 7 + (strength * 5f).toInt()
    repeat(lines) { index ->
        val phase = (progress * (0.9f + index * 0.035f) + index * 0.163f) % 1f
        val x = phase * (width * 1.35f) - width * 0.18f
        val y = height * (0.34f + (index % 5) * 0.105f)
        val length = width * (0.075f + strength * 0.075f + (index % 3) * 0.018f)
        val curve = height * (0.006f + strength * 0.012f)
        val path = Path().apply {
            moveTo(x, y)
            cubicTo(
                x + length * 0.30f, y - curve,
                x + length * 0.64f, y + curve,
                x + length, y - curve * 0.18f
            )
        }
        drawPath(
            path = path,
            color = Color(0xFFDDEBF2).copy(alpha = 0.10f + strength * 0.12f),
            style = Stroke(width = (1.0f + strength * 1.2f).dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
