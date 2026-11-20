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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** Lightweight, procedural weather particles drawn above the existing local weather video. */
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
    val effectiveWeatherCode = weatherCode
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

        when {
            effectiveWeatherCode in 51..55 ->
                drawRoyalDrizzle(progress, width, height, effectScale)
            effectiveWeatherCode in 56..57 || effectiveWeatherCode in 66..67 ->
                drawRoyalFreezingRain(progress, width, height, effectScale)
            effectiveWeatherCode == 96 || effectiveWeatherCode == 99 ->
                drawRoyalHail(progress, width, height, effectScale)
        }

        val wind = (windSpeedKmh ?: 8).coerceIn(0, 70)
        val windStrength = wind / 70f
        val isSnow = effectiveWeatherCode in 71..77 || effectiveWeatherCode in 85..86
        val windThreshold = if (isSnow) 12 else 18
        if (wind >= windThreshold) {
            drawRoyalWind(progress, width, height, windStrength, effectScale)
        }
    }
}

/** Fine, short, translucent drops; the layer stays subtle over the light-rain footage. */
private fun DrawScope.drawRoyalDrizzle(
    progress: Float,
    width: Float,
    height: Float,
    effectScale: Float
) {
    val count = (34f * effectScale).toInt().coerceIn(16, 42)
    val stroke = 0.85f.dp.toPx()
    repeat(count) { index ->
        val seed = ((index * 37) % 101) / 101f
        val speed = 0.78f + (index % 4) * 0.11f
        val x = (seed * width + progress * width * 0.12f + (index % 5) * width * 0.035f) % width
        val y = ((seed * height * 0.73f) + progress * height * speed) % height
        val length = (4.2f + (index % 3) * 1.6f).dp.toPx()
        drawLine(
            color = Color(0xFFDDF5FF).copy(alpha = 0.30f + (index % 3) * 0.035f),
            start = Offset(x, y),
            end = Offset(x - length * 0.24f, y + length),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/** Cold, glassy drops with a blue-white highlight to distinguish freezing precipitation. */
private fun DrawScope.drawRoyalFreezingRain(
    progress: Float,
    width: Float,
    height: Float,
    effectScale: Float
) {
    val count = (29f * effectScale).toInt().coerceIn(14, 36)
    repeat(count) { index ->
        val seed = ((index * 53 + 11) % 103) / 103f
        val speed = 0.92f + (index % 5) * 0.12f
        val x = (seed * width + progress * width * 0.17f + (index % 4) * width * 0.025f) % width
        val y = ((seed * height * 0.61f) + progress * height * speed) % height
        val length = (8f + (index % 4) * 2.2f).dp.toPx()
        val radius = (1.25f + (index % 3) * 0.35f).dp.toPx()
        drawLine(
            color = Color(0xFFBCEBFF).copy(alpha = 0.38f),
            start = Offset(x - length * 0.23f, y - length * 0.35f),
            end = Offset(x, y + length),
            strokeWidth = (1.25f).dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color(0xFFDAF5FF).copy(alpha = 0.64f),
            radius = radius,
            center = Offset(x, y + length * 0.78f)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.80f),
            radius = radius * 0.34f,
            center = Offset(x - radius * 0.28f, y + length * 0.66f)
        )
    }
}

/** Small rounded ice pellets with shaded rims and specular highlights. */
private fun DrawScope.drawRoyalHail(
    progress: Float,
    width: Float,
    height: Float,
    effectScale: Float
) {
    val count = (23f * effectScale).toInt().coerceIn(12, 30)
    repeat(count) { index ->
        val seed = ((index * 41 + 7) % 107) / 107f
        val speed = 1.05f + (index % 4) * 0.20f
        val radius = (2.2f + (index % 4) * 0.75f).dp.toPx()
        val drift = if (index % 2 == 0) 1f else -0.72f
        val x = (seed * width + progress * width * 0.21f * drift + width) % width
        val y = ((seed * height * 0.67f) + progress * height * speed) % height
        val center = Offset(x, y)
        drawCircle(
            color = Color(0xFF6D8998).copy(alpha = 0.34f),
            radius = radius * 1.16f,
            center = Offset(x + radius * 0.18f, y + radius * 0.30f)
        )
        drawCircle(
            color = Color(0xFFD9F1FA).copy(alpha = 0.88f),
            radius = radius,
            center = center
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.90f),
            radius = radius * 0.34f,
            center = Offset(x - radius * 0.30f, y - radius * 0.34f)
        )
        drawArc(
            color = Color(0xFF8DB8CC).copy(alpha = 0.42f),
            startAngle = 20f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(x - radius * 0.74f, y - radius * 0.74f),
            size = androidx.compose.ui.geometry.Size(radius * 1.48f, radius * 1.48f),
            style = Stroke(width = (0.65f).dp.toPx())
        )
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
