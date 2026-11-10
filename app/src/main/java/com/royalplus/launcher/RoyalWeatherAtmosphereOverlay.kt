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
import kotlin.math.sin

/**
 * Lightweight, procedural weather effects.
 *
 * All effects are drawn by Compose Canvas (no shaders, runtime permissions, external
 * assets or newer Android APIs), keeping the overlay compatible with Android 8 / API 26.
 * Particle counts are deliberately capped for Android TV devices and older GPUs.
 */
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
            animation = tween(durationMillis = 16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "weather-motion"
    )

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        when (weatherCode) {
            0 -> drawRoyalSun(progress)
            1, 2 -> {
                drawRoyalSun(progress, subtle = true)
                drawRoyalClouds(progress, w, h, cloudCount = 2, dense = false)
            }
            3 -> drawRoyalClouds(progress, w, h, cloudCount = 4, dense = true)
            45, 48 -> {
                drawRoyalClouds(progress, w, h, cloudCount = 4, dense = true)
                drawRoyalFog(progress, w, h, dense = weatherCode == 48)
            }
            in 51..57 -> {
                drawRoyalClouds(progress, w, h, cloudCount = 3, dense = true)
                drawRoyalRain(progress, w, h, intensity = if (weatherCode >= 55) 2 else 1)
            }
            in 61..67, in 80..82 -> {
                drawRoyalClouds(progress, w, h, cloudCount = 3, dense = true)
                val intensity = when (weatherCode) {
                    61, 66, 80 -> 2
                    63, 81 -> 3
                    else -> 4
                }
                drawRoyalRain(progress, w, h, intensity)
            }
            in 71..77, 85, 86 -> {
                drawRoyalClouds(progress, w, h, cloudCount = 3, dense = true)
                drawRoyalSnow(progress, w, h, intensity = when (weatherCode) {
                    71, 85 -> 1
                    73 -> 2
                    else -> 3
                })
            }
            in 95..99 -> {
                drawRoyalClouds(progress, w, h, cloudCount = 5, dense = true)
                drawRoyalRain(progress, w, h, intensity = if (weatherCode == 95) 3 else 4)
                drawRoyalLightning(progress, w, h)
            }
        }
    }
}

private fun DrawScope.drawRoyalSun(progress: Float, subtle: Boolean = false) {
    val center = Offset(size.width * 0.82f, size.height * 0.17f)
    val pulse = 0.97f + 0.03f * sin(progress * PI * 2.0).toFloat()
    val radius = size.width * if (subtle) 0.19f else 0.25f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0x55FFF6D5),
                Color(0x30FFE8A1),
                Color(0x12FFE5A0),
                Color.Transparent
            ),
            center = center,
            radius = radius * pulse
        ),
        radius = radius * pulse,
        center = center
    )
    if (!subtle) {
        drawCircle(Color(0x18FFF1C2), radius = size.width * 0.055f, center = center)
        drawCircle(
            color = Color(0x22FFF6D5),
            radius = size.width * 0.064f,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

private fun DrawScope.drawRoyalClouds(
    progress: Float,
    width: Float,
    height: Float,
    cloudCount: Int,
    dense: Boolean
) {
    val drift = progress * width * 0.10f
    repeat(cloudCount) { index ->
        val scale = if (dense) 1f + (index % 2) * 0.16f else 0.72f + (index % 2) * 0.12f
        val baseX = width * (0.08f + index * 0.235f)
        val direction = if (index % 2 == 0) 1f else -0.65f
        val x = (baseX + drift * direction + width) % (width * 1.18f) - width * 0.08f
        val y = height * (0.12f + (index % 3) * 0.09f) +
            sin(progress * PI * 2.0 + index * 1.7).toFloat() * height * 0.008f
        val radius = width * 0.105f * scale
        val alpha = if (dense) 0.12f else 0.075f
        val cloudColor = if (dense) Color(0xFF24394D) else Color(0xFFEAF4FA)
        val softBrush = Brush.radialGradient(
            colors = listOf(
                cloudColor.copy(alpha = alpha),
                cloudColor.copy(alpha = alpha * 0.62f),
                cloudColor.copy(alpha = 0f)
            ),
            center = Offset(x, y),
            radius = radius * 1.8f
        )
        drawCircle(softBrush, radius = radius * 1.55f, center = Offset(x, y))
        drawCircle(softBrush, radius = radius * 1.22f, center = Offset(x - radius * 0.75f, y + radius * 0.12f))
        drawCircle(softBrush, radius = radius * 1.28f, center = Offset(x + radius * 0.72f, y + radius * 0.08f))
    }
}

private fun DrawScope.drawRoyalRain(progress: Float, width: Float, height: Float, intensity: Int) {
    val count = when (intensity) {
        1 -> 28
        2 -> 46
        3 -> 64
        else -> 82
    }
    val windSlant = width * 0.018f
    val lineAlpha = when (intensity) {
        1 -> 0.30f
        2 -> 0.38f
        3 -> 0.46f
        else -> 0.54f
    }
    val travel = height * if (intensity == 1) 0.78f else 1.25f
    repeat(count) { index ->
        // Irrational spacing avoids visible particle columns without allocating arrays.
        val seed = (index * 0.6180339f) % 1f
        val x = width * (0.02f + seed * 0.96f)
        val phase = (index * 0.137f) % 1f
        val y = (height * phase + progress * travel) % (height * 1.12f) - height * 0.06f
        val length = height * when (intensity) {
            1 -> 0.014f + (index % 3) * 0.003f
            2 -> 0.022f + (index % 4) * 0.004f
            3 -> 0.030f + (index % 4) * 0.005f
            else -> 0.038f + (index % 5) * 0.005f
        }
        val start = Offset(x, y)
        val end = Offset(x - windSlant, y + length)
        drawLine(
            color = Color(0xFFDCEEFF).copy(alpha = lineAlpha),
            start = start,
            end = end,
            strokeWidth = (0.8f + intensity * 0.32f).dp.toPx(),
            cap = StrokeCap.Round
        )
        if (intensity >= 3 && index % 9 == 0) {
            drawCircle(
                color = Color(0x99D8EEFF),
                radius = 1.1.dp.toPx(),
                center = Offset(end.x, end.y + 1.5.dp.toPx())
            )
        }
    }

    // Fine ground-level spray adds depth without expensive blur effects.
    if (intensity >= 3) {
        val sprayY = height * 0.91f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0x142D526B), Color(0x1D9AB9CA)),
                startY = sprayY,
                endY = height
            ),
            topLeft = Offset(0f, sprayY),
            size = Size(width, height - sprayY)
        )
        repeat(12) { index ->
            val x = width * ((index * 0.173f + progress * 0.025f) % 1f)
            val y = height * (0.91f + (index % 3) * 0.025f)
            drawCircle(
                color = Color(0x44DCEEFF),
                radius = (1.2f + (index % 2) * 0.7f).dp.toPx(),
                center = Offset(x, y),
                style = Stroke(width = 0.8.dp.toPx())
            )
        }
    }
}

private fun DrawScope.drawRoyalSnow(progress: Float, width: Float, height: Float, intensity: Int) {
    val count = when (intensity) {
        1 -> 24
        2 -> 38
        else -> 52
    }
    repeat(count) { index ->
        val seed = (index * 0.7548777f) % 1f
        val phase = (index * 0.193f) % 1f
        val fallSpeed = if (index % 4 == 0) 0.72f else 1f
        val y = (height * phase + progress * height * 0.72f * fallSpeed) % (height * 1.08f) - height * 0.04f
        val sway = sin(progress * PI * 2.0 + index * 1.31).toFloat()
        val x = width * (0.025f + seed * 0.95f) + sway * (5.dp.toPx() + intensity * 1.8.dp.toPx())
        val radius = (1.2f + (index % 4) * 0.48f + if (intensity == 3) 0.35f else 0f).dp.toPx()
        val alpha = 0.46f + (index % 3) * 0.12f
        drawCircle(
            color = Color(0xFFEAF7FF).copy(alpha = alpha),
            radius = radius,
            center = Offset(x, y)
        )
        if (index % 5 == 0) {
            val arm = radius * 2.2f
            drawLine(Color(0x88F5FBFF), Offset(x - arm, y), Offset(x + arm, y), strokeWidth = 0.75.dp.toPx())
            drawLine(Color(0x88F5FBFF), Offset(x, y - arm), Offset(x, y + arm), strokeWidth = 0.75.dp.toPx())
        }
    }
}

private fun DrawScope.drawRoyalFog(progress: Float, width: Float, height: Float, dense: Boolean) {
    val alpha = if (dense) 0.13f else 0.085f
    repeat(3) { index ->
        val phase = (progress + index * 0.31f) % 1f
        val y = height * (0.47f + index * 0.16f)
        val drift = (phase - 0.5f) * width * 0.22f
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFE7F1F5).copy(alpha = alpha),
                    Color(0xFFDDEAF0).copy(alpha = alpha * 0.72f),
                    Color.Transparent
                ),
                startX = -width * 0.2f + drift,
                endX = width * 1.1f + drift
            ),
            topLeft = Offset(0f, y),
            size = Size(width, height * 0.12f)
        )
    }
}

private fun DrawScope.drawRoyalLightning(progress: Float, width: Float, height: Float) {
    // Two brief, irregular-looking flashes per loop; the screen background lights up too.
    val first = pulseWindow(progress, 0.115f, 0.055f)
    val second = pulseWindow(progress, 0.645f, 0.035f)
    val flash = maxOf(first, second)
    if (flash <= 0f) return

    drawRect(Color.White.copy(alpha = 0.13f * flash))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x66E7F3FF).copy(alpha = flash), Color.Transparent),
            center = Offset(width * 0.72f, height * 0.24f),
            radius = width * 0.48f
        ),
        radius = width * 0.48f,
        center = Offset(width * 0.72f, height * 0.24f)
    )

    val bolt = Path().apply {
        moveTo(width * 0.73f, height * 0.13f)
        lineTo(width * 0.685f, height * 0.235f)
        lineTo(width * 0.725f, height * 0.235f)
        lineTo(width * 0.665f, height * 0.355f)
        lineTo(width * 0.755f, height * 0.245f)
        lineTo(width * 0.715f, height * 0.245f)
        close()
    }
    drawPath(bolt, Color(0xFFFFFBE8).copy(alpha = 0.90f * flash))
    drawPath(
        bolt,
        Color(0xFFB9DFFF).copy(alpha = 0.75f * flash),
        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
    )
}

private fun pulseWindow(progress: Float, center: Float, width: Float): Float {
    val distance = abs(progress - center)
    if (distance >= width) return 0f
    val edge = 1f - distance / width
    return edge * edge
}
