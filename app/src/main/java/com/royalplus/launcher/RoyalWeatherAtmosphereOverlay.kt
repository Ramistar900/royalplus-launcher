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
    windSpeedKmh: Int? = null
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

        when (weatherCode) {
            0 -> {
                drawRoyalSun(progress, width, height, strong = true)
                if (measuredWind >= 24) drawRoyalWind(progress, width, height, windStrength)
            }
            1, 2 -> {
                drawRoyalSun(progress, width, height, strong = false)
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
                drawRoyalClouds(progress, width, height, count = 4, dense = true)
                drawRoyalRain(progress, width, height, intensity = if (weatherCode >= 55) 2 else 1, windStrength)
                if (measuredWind >= 18) drawRoyalWind(progress, width, height, windStrength)
            }
            in 61..67, in 80..82 -> {
                drawRoyalClouds(progress, width, height, count = 4, dense = true)
                val intensity = when (weatherCode) {
                    61, 66, 80 -> 2
                    63, 81 -> 3
                    else -> 4
                }
                drawRoyalRain(progress, width, height, intensity, windStrength)
                if (measuredWind >= 18) drawRoyalWind(progress, width, height, windStrength)
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
                drawRoyalClouds(progress, width, height, count = 6, dense = true)
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
    // Long falling streaks with a wind-driven angle avoid the old horizontal dash effect.
    val count = when (intensity) {
        1 -> 38
        2 -> 58
        3 -> 82
        else -> 108
    }
    val travel = height * 1.42f
    val windTilt = (0.12f + windStrength * 0.78f)

    repeat(count) { index ->
        val seed = (index * 0.6180339f) % 1f
        val phase = (index * 0.381966f + (index % 5) * 0.047f) % 1f
        val speed = 0.76f + (index % 7) * 0.075f
        val length = height * when (intensity) {
            1 -> 0.020f + (index % 4) * 0.004f
            2 -> 0.027f + (index % 5) * 0.0045f
            3 -> 0.034f + (index % 5) * 0.0055f
            else -> 0.042f + (index % 5) * 0.006f
        }
        val x = width * (0.015f + seed * 0.97f)
        val y = (height * phase + progress * travel * speed) % (height + length) - length
        val slant = length * windTilt * (if (index % 2 == 0) 1f else -0.20f)
        val alpha = when (intensity) {
            1 -> 0.22f + (index % 4) * 0.035f
            2 -> 0.28f + (index % 5) * 0.032f
            3 -> 0.34f + (index % 5) * 0.035f
            else -> 0.39f + (index % 5) * 0.035f
        }.coerceAtMost(0.58f)
        val stroke = (0.8f + (index % 3) * 0.32f).dp.toPx()

        drawLine(
            color = Color(0xFFB9D9EE).copy(alpha = alpha * 0.26f),
            start = Offset(x - slant * 0.25f, y - length * 0.12f),
            end = Offset(x + slant * 1.18f, y + length * 1.10f),
            strokeWidth = stroke * 2.2f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFE8F5FF).copy(alpha = alpha),
            start = Offset(x, y),
            end = Offset(x + slant, y + length),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }

    if (intensity >= 3) {
        val sprayY = height * 0.88f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0x142D526B), Color(0x359AB9CA)),
                startY = sprayY,
                endY = height
            ),
            topLeft = Offset(0f, sprayY),
            size = Size(width, height - sprayY)
        )
        repeat(12) { index ->
            val x = width * ((index * 0.173f + progress * 0.045f) % 1f)
            val y = height * (0.91f + (index % 4) * 0.021f)
            val radius = (2.0f + (index % 3) * 0.8f).dp.toPx()
            drawCircle(
                color = Color(0x55DCEEFF),
                radius = radius,
                center = Offset(x, y),
                style = Stroke(width = 0.9.dp.toPx())
            )
        }
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
