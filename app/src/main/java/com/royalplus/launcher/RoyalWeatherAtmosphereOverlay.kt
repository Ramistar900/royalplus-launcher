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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural weather artwork shared by the weather card, details screen and screensaver.
 *
 * Uses only Compose Canvas primitives already available in the Android 8 baseline.
 * Particle counts are capped for older Android TV GPUs. No runtime shaders, blur
 * effects, permissions, network calls or additional dependencies are used here.
 */
@Composable
internal fun RoyalWeatherAtmosphereOverlay(
    weatherCode: Int?,
    motionEnabled: Boolean,
    windSpeedKmh: Int? = null,
    nightMode: Boolean = false,
    sunsetMode: Boolean = false
) {
    if (!motionEnabled || weatherCode == null) return

    val transition = rememberInfiniteTransition(label = "royal-weather-atmosphere")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
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

        val measuredWind = (windSpeedKmh ?: 8).coerceIn(0, 70)
        val windStrength = measuredWind / 70f

        // Rain is intentionally rendered as droplets alone, even at night.
        val isRainScene = weatherCode?.let {
            it in 51..67 || it in 80..82 || it in 95..99
        } == true
        if (nightMode && !isRainScene && weatherCode?.let { it !in 0..2 } == true) {
            drawRoyalNightSky(progress, width, height)
        }

        when (weatherCode) {
            0 -> {
                when {
                    nightMode -> drawRoyalNightSky(progress, width, height)
                    sunsetMode -> drawRoyalSunset(progress, width, height)
                    else -> drawRoyalSun(progress, width, height, strong = true)
                }
                if (measuredWind >= 24) drawRoyalWind(progress, width, height, windStrength)
            }
            1, 2 -> {
                when {
                    nightMode -> drawRoyalNightSky(progress, width, height)
                    sunsetMode -> drawRoyalSunset(progress, width, height)
                    else -> drawRoyalSun(progress, width, height, strong = false)
                }
                drawRoyalClouds(progress, width, height, count = 2, dense = false)
                if (measuredWind >= 24) drawRoyalWind(progress, width, height, windStrength)
            }
            3 -> {
                drawRoyalClouds(progress, width, height, count = 5, dense = true)
                if (measuredWind >= 18) drawRoyalWind(progress, width, height, windStrength)
            }
            45, 48 -> {
                drawRoyalClouds(progress, width, height, count = 4, dense = true)
                drawRoyalFog(progress, width, height, dense = weatherCode == 48)
                if (measuredWind >= 24) drawRoyalWind(progress, width, height, windStrength)
            }
            in 51..57 -> {
                // Rain scenes are deliberately limited to individual falling droplets.
                drawRoyalRain(progress, width, height, intensity = if (weatherCode >= 55) 2 else 1, windStrength = windStrength)
            }
            in 61..67, in 80..82 -> {
                val intensity = when (weatherCode) {
                    61, 66, 80 -> 2
                    63, 81 -> 3
                    else -> 4
                }
                drawRoyalRain(progress, width, height, intensity, windStrength)
            }
            in 71..77, 85, 86 -> {
                drawRoyalClouds(progress, width, height, count = 4, dense = true)
                val intensity = when (weatherCode) {
                    71, 85 -> 1
                    73 -> 2
                    else -> 3
                }
                drawRoyalSnow(progress, width, height, intensity, windStrength)
                if (measuredWind >= 12) drawRoyalWind(progress, width, height, windStrength)
            }
            in 95..99 -> {
                drawRoyalRain(progress, width, height, if (weatherCode == 95) 3 else 4, windStrength)
                drawRoyalLightning(progress, width, height)
            }
        }
    }
}

private fun DrawScope.drawRoyalSun(
    progress: Float,
    width: Float,
    height: Float,
    strong: Boolean
) {
    val center = Offset(width * 0.82f, height * 0.17f)
    val pulse = 0.985f + 0.015f * sin(progress * PI * 2.0).toFloat()
    val radius = width * if (strong) 0.22f else 0.16f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0x66FFF8D9),
                Color(0x35FFE7A0),
                Color(0x16FFD36A),
                Color.Transparent
            ),
            center = center,
            radius = radius * 2.5f * pulse
        ),
        radius = radius * 2.5f * pulse,
        center = center
    )

    // Soft, slowly breathing sun rays instead of a static emoji-like disc.
    repeat(12) { index ->
        val angle = (index * PI / 6.0) + progress * PI * 0.12
        val inner = radius * 1.12f
        val outer = radius * (if (index % 2 == 0) 1.55f else 1.40f)
        val start = Offset(
            center.x + cos(angle).toFloat() * inner,
            center.y + sin(angle).toFloat() * inner
        )
        val end = Offset(
            center.x + cos(angle).toFloat() * outer,
            center.y + sin(angle).toFloat() * outer
        )
        drawLine(
            color = Color(0x55FFF0BD),
            start = start,
            end = end,
            strokeWidth = if (strong) 2.2.dp.toPx() else 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFFBEA), Color(0xFFFFE8A3), Color(0x00FFE2A0)),
            center = center,
            radius = radius * 0.72f
        ),
        radius = radius * 0.72f,
        center = center
    )
}

private fun DrawScope.drawRoyalSunset(progress: Float, width: Float, height: Float) {
    val center = Offset(width * 0.81f, height * 0.31f)
    val pulse = 0.99f + 0.01f * sin(progress * PI * 2.0).toFloat()
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x66FFD18B), Color(0x36FF9B62), Color.Transparent),
            center = center,
            radius = width * 0.31f
        ),
        radius = width * 0.31f,
        center = center
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFE1A1), Color(0xFFFFA66F), Color(0x00F06A55)),
            center = center,
            radius = width * 0.095f * pulse
        ),
        radius = width * 0.095f * pulse,
        center = center
    )
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color(0x18F58D69), Color.Transparent),
            startY = height * 0.30f,
            endY = height * 0.78f
        ),
        topLeft = Offset(0f, height * 0.30f),
        size = Size(width, height * 0.48f)
    )
}

private fun DrawScope.drawRoyalNightSky(progress: Float, width: Float, height: Float) {
    val moon = Offset(width * 0.82f, height * 0.17f)
    val radius = width * 0.055f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x55D9E9FF), Color(0x18AFCDF1), Color.Transparent),
            center = moon,
            radius = radius * 3.2f
        ),
        radius = radius * 3.2f,
        center = moon
    )
    val crescent = Path().apply {
        moveTo(moon.x, moon.y - radius)
        cubicTo(moon.x - radius * 1.12f, moon.y - radius * 0.42f, moon.x - radius * 0.70f, moon.y + radius * 0.92f, moon.x + radius * 0.02f, moon.y + radius)
        cubicTo(moon.x + radius * 0.76f, moon.y + radius * 0.48f, moon.x + radius * 0.74f, moon.y - radius * 0.28f, moon.x, moon.y - radius)
        close()
    }
    drawPath(crescent, Color(0xFFFFF1D2).copy(alpha = 0.86f))

    repeat(30) { index ->
        val x = width * ((index * 0.6180339f + 0.07f) % 1f)
        val y = height * (0.035f + ((index * 0.371f) % 1f) * 0.43f)
        val twinkle = 0.24f + 0.28f * (0.5f + 0.5f * sin(progress * PI * 2.0 + index * 1.7).toFloat())
        val starRadius = (0.65f + (index % 3) * 0.42f).dp.toPx()
        drawCircle(Color(0xFFE8F2FF).copy(alpha = twinkle), radius = starRadius, center = Offset(x, y))
        if (index % 7 == 0) {
            val ray = starRadius * 2.8f
            drawLine(Color(0x99E8F2FF).copy(alpha = twinkle), Offset(x - ray, y), Offset(x + ray, y), strokeWidth = 0.7.dp.toPx())
            drawLine(Color(0x99E8F2FF).copy(alpha = twinkle), Offset(x, y - ray), Offset(x, y + ray), strokeWidth = 0.7.dp.toPx())
        }
    }
}

private fun DrawScope.drawRoyalClouds(
    progress: Float,
    width: Float,
    height: Float,
    count: Int,
    dense: Boolean
) {
    val drift = width * 0.085f
    repeat(count) { index ->
        val scale = (if (dense) 1.08f else 0.84f) + (index % 3) * 0.13f
        val baseX = width * (0.05f + index * (0.91f / count))
        val direction = if (index % 2 == 0) 1f else -0.58f
        val x = (baseX + progress * drift * direction + width * 0.10f) % (width * 1.20f) - width * 0.10f
        val y = height * (0.12f + (index % 3) * 0.105f) +
            sin(progress * PI * 2.0 + index * 1.4).toFloat() * height * 0.009f
        val radius = width * 0.078f * scale
        val cloudBase = if (dense) Color(0xFF526577) else Color(0xFFF0F6FA)
        val shadow = if (dense) Color(0xFF172A3B) else Color(0xFF8DA6B8)

        // A low shadow gives the cloud bank depth against photographic backgrounds.
        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(shadow.copy(alpha = if (dense) 0.20f else 0.10f), Color.Transparent),
                startY = y + radius * 0.30f,
                endY = y + radius * 1.55f
            ),
            topLeft = Offset(x - radius * 1.65f, y - radius * 0.02f),
            size = Size(radius * 3.45f, radius * 1.22f)
        )

        val puffs = listOf(
            Triple(-0.88f, 0.03f, 0.68f),
            Triple(-0.28f, -0.38f, 0.88f),
            Triple(0.42f, -0.25f, 0.78f),
            Triple(0.98f, 0.04f, 0.56f)
        )
        puffs.forEachIndexed { puffIndex, puff ->
            val puffCenter = Offset(x + radius * puff.first, y + radius * puff.second)
            val puffRadius = radius * puff.third
            val light = if (dense) Color(0xFFB7C5D0) else Color.White
            val top = if (dense) Color(0xFF667B8D) else Color(0xFFF9FCFF)
            val bottom = if (dense) Color(0xFF26394A) else Color(0xFFD8E6EF)
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        top.copy(alpha = if (dense) 0.48f else 0.38f),
                        cloudBase.copy(alpha = if (dense) 0.34f else 0.30f),
                        bottom.copy(alpha = if (dense) 0.31f else 0.16f)
                    ),
                    startY = puffCenter.y - puffRadius,
                    endY = puffCenter.y + puffRadius
                ),
                radius = puffRadius,
                center = puffCenter
            )
            if (puffIndex == 1 || puffIndex == 2) {
                drawCircle(
                    color = light.copy(alpha = if (dense) 0.055f else 0.16f),
                    radius = puffRadius * 0.78f,
                    center = Offset(puffCenter.x - puffRadius * 0.12f, puffCenter.y - puffRadius * 0.20f)
                )
            }
        }
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

private fun DrawScope.drawRoyalFog(
    progress: Float,
    width: Float,
    height: Float,
    dense: Boolean
) {
    val alpha = if (dense) 0.19f else 0.12f
    repeat(4) { index ->
        val phase = (progress * (if (index % 2 == 0) 1f else -0.72f) + index * 0.27f + 1f) % 1f
        val y = height * (0.38f + index * 0.13f)
        val drift = (phase - 0.5f) * width * 0.30f
        val bandHeight = height * (0.075f + (index % 2) * 0.025f)
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFEAF3F7).copy(alpha = alpha),
                    Color(0xFFDDEAF0).copy(alpha = alpha * 0.58f),
                    Color.Transparent
                ),
                center = Offset(width * 0.52f + drift, y),
                radius = width * 0.65f
            ),
            topLeft = Offset(-width * 0.08f + drift, y - bandHeight * 0.5f),
            size = Size(width * 1.16f, bandHeight)
        )
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

private fun DrawScope.drawRoyalLightning(progress: Float, width: Float, height: Float) {
    val first = pulseWindow(progress, 0.115f, 0.055f)
    val second = pulseWindow(progress, 0.645f, 0.035f)
    val flash = maxOf(first, second)
    if (flash <= 0f) return

    drawRect(Color.White.copy(alpha = 0.15f * flash))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x88E7F3FF).copy(alpha = flash), Color.Transparent),
            center = Offset(width * 0.72f, height * 0.23f),
            radius = width * 0.54f
        ),
        radius = width * 0.54f,
        center = Offset(width * 0.72f, height * 0.23f)
    )

    val bolt = Path().apply {
        moveTo(width * 0.72f, height * 0.12f)
        lineTo(width * 0.685f, height * 0.205f)
        lineTo(width * 0.735f, height * 0.205f)
        lineTo(width * 0.675f, height * 0.315f)
        lineTo(width * 0.755f, height * 0.238f)
        lineTo(width * 0.715f, height * 0.238f)
        close()
    }
    drawPath(bolt, Color(0xFFFFFBE8).copy(alpha = 0.94f * flash))
    drawPath(
        bolt,
        Color(0xFFB9DFFF).copy(alpha = 0.82f * flash),
        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
    )

    val branch = Path().apply {
        moveTo(width * 0.70f, height * 0.265f)
        lineTo(width * 0.655f, height * 0.292f)
        lineTo(width * 0.635f, height * 0.35f)
    }
    drawPath(
        branch,
        Color(0xFFEAF6FF).copy(alpha = 0.82f * flash),
        style = Stroke(width = 1.35.dp.toPx(), cap = StrokeCap.Round)
    )
}

private fun pulseWindow(progress: Float, center: Float, width: Float): Float {
    val distance = abs(progress - center)
    if (distance >= width) return 0f
    val edge = 1f - distance / width
    return edge * edge
}
