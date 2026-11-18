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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
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
    val effectScale = RoyalInterfaceQualitySettings.budget(LocalContext.current).motionScale
    val description = weatherDescription.orEmpty().lowercase()
    val rainWords = listOf("rain", "drizzle", "مطر", "أمطار", "رذاذ", "yağmur", "çisenti")
    val descriptionIndicatesRain = rainWords.any(description::contains)
    val numericRainCode = weatherCode?.let { it in 51..67 || it in 80..82 || it in 95..99 } == true
    val effectiveWeatherCode = if (descriptionIndicatesRain && !numericRainCode) 61 else weatherCode
    if (!motionEnabled || effectScale <= 0.25f || effectiveWeatherCode == null) return

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
        val isSnow = effectiveWeatherCode in 71..77 || effectiveWeatherCode in 85..86
        val windThreshold = if (isSnow) 12 else 18

        // Rain and snow particles are intentionally not drawn over the weather video.
        // Only a clear, flowing gust animation remains when wind is noticeable.
        if (wind >= windThreshold) {
            drawRoyalWind(progress, width, height, windStrength, effectScale)
        }
    }
}

private fun DrawScope.drawRoyalWind(
    progress: Float,
    width: Float,
    height: Float,
    strength: Float,
    effectScale: Float
) {
    // Broad S-shaped ribbons and a hooked tail make each moving mark read as a gust.
    val gustCount = ((3f + strength * 2f) * effectScale).toInt().coerceIn(3, 5)
    repeat(gustCount) { index ->
        val speed = 0.72f + (index % 3) * 0.12f
        val phase = (progress * speed + index * 0.217f) % 1f
        val x = phase * (width * 1.48f) - width * 0.25f
        val wave = sin(progress * PI * 2.0 + index * 0.91).toFloat()
        val y = height * (0.25f + (index % 4) * 0.15f) + wave * height * 0.008f
        val length = width * (0.13f + strength * 0.14f + (index % 2) * 0.035f)
        val curve = height * (0.014f + strength * 0.025f)
        val gust = Path().apply {
            moveTo(x, y)
            cubicTo(
                x + length * 0.17f, y - curve * 1.65f,
                x + length * 0.34f, y + curve * 1.85f,
                x + length * 0.52f, y + curve * 0.12f
            )
            cubicTo(
                x + length * 0.68f, y - curve * 1.75f,
                x + length * 0.84f, y - curve * 0.95f,
                x + length, y - curve * 0.25f
            )
        }
        val alpha = 0.18f + strength * 0.20f
        val stroke = (1.8f + strength * 1.3f).dp.toPx()
        drawPath(
            path = gust,
            color = Color(0xFFDDEBF2).copy(alpha = alpha),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        val trailingCurl = Path().apply {
            moveTo(x + length * 0.43f, y + curve * 1.2f)
            cubicTo(
                x + length * 0.58f, y + curve * 2.1f,
                x + length * 0.77f, y + curve * 1.25f,
                x + length * 0.91f, y + curve * 0.78f
            )
        }
        drawPath(
            path = trailingCurl,
            color = Color(0xFFDDEBF2).copy(alpha = alpha * 0.62f),
            style = Stroke(width = stroke * 0.72f, cap = StrokeCap.Round)
        )
    }
}

