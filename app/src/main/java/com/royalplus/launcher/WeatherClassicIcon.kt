package com.royalplus.launcher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lightweight animated sun and fuller classic Royal forecast artwork:
 * a warm sun, soft white/grey cloud, and blue rain/snow marks.
 */
private enum class RoyalClassicWeatherKind {
    SUNNY, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, HEAVY_RAIN,
    SNOW, HEAVY_SNOW, FREEZING_DRIZZLE, FREEZING_RAIN, HAIL,
    THUNDERSTORM, THUNDERSTORM_HAIL, WIND, STRONG_WIND,
    CLEAR_NIGHT, SUNRISE, SUNSET, HOT, COLD
}

private fun resolveRoyalClassicWeatherKind(
    weatherCode: Int?,
    description: String,
    apparentTemperatureC: Int?,
    windSpeedKmh: Int?,
    hour: Int?
): RoyalClassicWeatherKind {
    val text = description.lowercase(java.util.Locale.ROOT)
    if (text.contains("blizzard") || text.contains("عاصفة ثلجية") || text.contains("kar fırtınası")) {
        return RoyalClassicWeatherKind.HEAVY_SNOW
    }
    if (text.contains("tornado") || text.contains("إعصار") || text.contains("kasırga")) {
        return RoyalClassicWeatherKind.STRONG_WIND
    }
    if (text.contains("dust") || text.contains("غبار") || text.contains("toz fırtınası")) {
        return RoyalClassicWeatherKind.WIND
    }
    if (text.contains("mist") || text.contains("شبورة") || text.contains("pus") ||
        text.contains("fog") || text.contains("ضباب") || text.contains("sis")) {
        return RoyalClassicWeatherKind.FOG
    }
    if (text.contains("lightning") || text.contains("برق") || text.contains("şimşek")) {
        return RoyalClassicWeatherKind.THUNDERSTORM
    }

    when (weatherCode) {
        0, 1 -> {
            if (hour != null && (hour >= 20 || hour < 5)) return RoyalClassicWeatherKind.CLEAR_NIGHT
            if (hour != null && hour in 5..7) return RoyalClassicWeatherKind.SUNRISE
            if (hour != null && hour in 17..19) return RoyalClassicWeatherKind.SUNSET
            if ((windSpeedKmh ?: 0) >= 45) return RoyalClassicWeatherKind.STRONG_WIND
            if ((windSpeedKmh ?: 0) >= 28) return RoyalClassicWeatherKind.WIND
            if (apparentTemperatureC != null && apparentTemperatureC >= 35) return RoyalClassicWeatherKind.HOT
            if (apparentTemperatureC != null && apparentTemperatureC <= 0) return RoyalClassicWeatherKind.COLD
            return RoyalClassicWeatherKind.SUNNY
        }
        2 -> return RoyalClassicWeatherKind.PARTLY_CLOUDY
        3 -> return RoyalClassicWeatherKind.CLOUDY
        45, 48 -> return RoyalClassicWeatherKind.FOG
        51, 53, 55 -> return RoyalClassicWeatherKind.DRIZZLE
        56, 57 -> return RoyalClassicWeatherKind.FREEZING_DRIZZLE
        66, 67 -> return RoyalClassicWeatherKind.FREEZING_RAIN
        61, 63, 80, 81 -> return RoyalClassicWeatherKind.RAIN
        65, 82 -> return RoyalClassicWeatherKind.HEAVY_RAIN
        71, 73, 77, 85 -> return RoyalClassicWeatherKind.SNOW
        75, 86 -> return RoyalClassicWeatherKind.HEAVY_SNOW
        95 -> return RoyalClassicWeatherKind.THUNDERSTORM
        96, 99 -> return RoyalClassicWeatherKind.THUNDERSTORM_HAIL
    }

    if ((windSpeedKmh ?: 0) >= 45) return RoyalClassicWeatherKind.STRONG_WIND
    if ((windSpeedKmh ?: 0) >= 28) return RoyalClassicWeatherKind.WIND
    if (apparentTemperatureC != null && apparentTemperatureC >= 35) return RoyalClassicWeatherKind.HOT
    if (apparentTemperatureC != null && apparentTemperatureC <= 0) return RoyalClassicWeatherKind.COLD
    if (text.contains("sunny") || text.contains("مشمس") || text.contains("صافية")) return RoyalClassicWeatherKind.SUNNY
    if (text.contains("rain") || text.contains("مطر") || text.contains("أمطار") || text.contains("رذاذ")) return RoyalClassicWeatherKind.RAIN
    if (text.contains("snow") || text.contains("ثلج")) return RoyalClassicWeatherKind.SNOW
    if (text.contains("partly") || text.contains("parçalı bulutlu") || text.contains("غائم جزئي")) {
        return RoyalClassicWeatherKind.PARTLY_CLOUDY
    }
    if (text.contains("cloud") || text.contains("غائم") || text.contains("غيوم")) return RoyalClassicWeatherKind.CLOUDY
    return RoyalClassicWeatherKind.PARTLY_CLOUDY
}

@Composable
internal fun RoyalClassicWeatherIcon(
    weatherCode: Int?,
    description: String = "",
    apparentTemperatureC: Int? = null,
    windSpeedKmh: Int? = null,
    hour: Int? = null,
    showRainMarks: Boolean = true,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val kind = resolveRoyalClassicWeatherKind(
        weatherCode = weatherCode,
        description = description,
        apparentTemperatureC = apparentTemperatureC,
        windSpeedKmh = windSpeedKmh,
        hour = hour
    )
    val motionScale = RoyalInterfaceQualitySettings.budget(LocalContext.current).motionScale
    val sunTransition = rememberInfiniteTransition(label = "royalSunRotation")
    val sunRotation by sunTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (motionScale <= 0.25f) 0f else 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (18_000f / motionScale.coerceAtLeast(0.35f)).toInt(),
                easing = LinearEasing
            )
        ),
        label = "royalSunRotationAngle"
    )
    val weatherMotion = rememberInfiniteTransition(label = "royalWeatherIconMotion")
    val weatherMotionProgress by weatherMotion.animateFloat(
        initialValue = 0f,
        targetValue = if (motionScale <= 0.25f) 0f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (3_200f / motionScale.coerceAtLeast(0.35f)).toInt(),
                easing = LinearEasing
            )
        ),
        label = "royalWeatherMotionProgress"
    )
    val weatherPulse by weatherMotion.animateFloat(
        initialValue = 0.55f,
        targetValue = if (motionScale <= 0.25f) 0.55f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (1_350f / motionScale.coerceAtLeast(0.35f)).toInt(),
                easing = LinearEasing
            )
        ),
        label = "royalWeatherPulse"
    )
    Canvas(modifier.semantics {
        this.contentDescription = contentDescription ?: description.ifBlank { kind.name }
    }) {
        val edge = size.minDimension
        if (edge <= 0f) return@Canvas
        val cloudDrift = edge * 0.018f * sin(weatherMotionProgress * (Math.PI * 2.0).toFloat())

        when (kind) {
            RoyalClassicWeatherKind.SUNNY -> drawSun(
                center = Offset(size.width * 0.5f, size.height * 0.5f),
                radius = edge * 0.20f,
                rotationDegrees = sunRotation
            )
            RoyalClassicWeatherKind.PARTLY_CLOUDY -> {
                drawSun(
                    Offset(size.width * 0.34f, size.height * 0.35f),
                    edge * 0.17f,
                    rotationDegrees = sunRotation
                )
                drawWeatherCloud(size.width * 0.17f, size.height * 0.36f, size.width * 0.76f, size.height * 0.50f)
            }
            RoyalClassicWeatherKind.CLOUDY -> drawStandaloneCloud(edge, cloudDrift)
            RoyalClassicWeatherKind.HOT -> {
                drawSun(
                    center = Offset(size.width * 0.5f, size.height * 0.40f),
                    radius = edge * 0.18f,
                    rotationDegrees = sunRotation
                )
                drawHeatShimmer(edge, weatherMotionProgress, weatherPulse)
            }
            RoyalClassicWeatherKind.COLD -> {
                drawStandaloneCloud(edge, cloudDrift)
                drawFrost(edge, weatherMotionProgress, weatherPulse)
            }
            RoyalClassicWeatherKind.FOG -> {
                drawWeatherCloud(size.width * 0.12f, size.height * 0.16f, size.width * 0.78f, size.height * 0.53f)
                drawFogLines(edge, weatherMotionProgress)
            }
            RoyalClassicWeatherKind.DRIZZLE,
            RoyalClassicWeatherKind.RAIN,
            RoyalClassicWeatherKind.HEAVY_RAIN,
            RoyalClassicWeatherKind.FREEZING_DRIZZLE,
            RoyalClassicWeatherKind.FREEZING_RAIN -> {
                drawWeatherCloud(size.width * 0.12f, size.height * 0.14f, size.width * 0.78f, size.height * 0.55f)
                if (showRainMarks) {
                    val icy = kind == RoyalClassicWeatherKind.FREEZING_DRIZZLE ||
                        kind == RoyalClassicWeatherKind.FREEZING_RAIN
                    val count = when (kind) {
                        RoyalClassicWeatherKind.DRIZZLE, RoyalClassicWeatherKind.FREEZING_DRIZZLE -> 3
                        RoyalClassicWeatherKind.HEAVY_RAIN -> 6
                        else -> 5
                    }
                    drawRain(edge, count, weatherMotionProgress, icy = icy)
                }
            }
            RoyalClassicWeatherKind.SNOW,
            RoyalClassicWeatherKind.HEAVY_SNOW -> {
                drawWeatherCloud(size.width * 0.12f, size.height * 0.13f, size.width * 0.78f, size.height * 0.56f)
                drawSnow(edge, if (kind == RoyalClassicWeatherKind.HEAVY_SNOW) 6 else 4, weatherMotionProgress)
            }
            RoyalClassicWeatherKind.THUNDERSTORM,
            RoyalClassicWeatherKind.THUNDERSTORM_HAIL -> {
                drawWeatherCloud(size.width * 0.12f, size.height * 0.10f, size.width * 0.78f, size.height * 0.56f)
                drawLightning(edge, weatherPulse)
                if (kind == RoyalClassicWeatherKind.THUNDERSTORM_HAIL) {
                    drawHail(edge, 5, weatherMotionProgress)
                } else if (showRainMarks) {
                    drawRain(edge, 3, weatherMotionProgress)
                }
            }
            RoyalClassicWeatherKind.HAIL -> drawHail(edge, 6, weatherMotionProgress)
            RoyalClassicWeatherKind.WIND,
            RoyalClassicWeatherKind.STRONG_WIND -> {
                drawWeatherCloud(size.width * 0.18f, size.height * 0.10f, size.width * 0.68f, size.height * 0.50f)
                drawWind(edge, if (kind == RoyalClassicWeatherKind.STRONG_WIND) 3 else 2, weatherMotionProgress)
            }
            RoyalClassicWeatherKind.CLEAR_NIGHT -> drawMoon(edge, weatherMotionProgress, weatherPulse)
            RoyalClassicWeatherKind.SUNRISE,
            RoyalClassicWeatherKind.SUNSET -> {
                drawSun(
                    center = Offset(
                        size.width * 0.5f,
                        size.height * (0.56f + 0.025f * sin(weatherMotionProgress * (Math.PI * 2.0).toFloat()))
                    ),
                    radius = edge * 0.19f,
                    rotationDegrees = sunRotation
                )
            }
        }
    }
}

private fun DrawScope.drawSun(
    center: Offset,
    radius: Float,
    rotationDegrees: Float = 0f
) {
    // Rotate the complete sun disc and its rays around the sun's own center.
    // The small surface details make the disc's rotation visibly readable.
    rotate(degrees = rotationDegrees, pivot = center) {
        val rayColor = Color(0xFFFFC857)
        val rayInner = radius * 1.30f
        val rayOuter = radius * 1.72f
        repeat(8) { index ->
            val angle = Math.PI * index / 4.0
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            drawLine(
                color = rayColor,
                start = Offset(center.x + dx * rayInner, center.y + dy * rayInner),
                end = Offset(center.x + dx * rayOuter, center.y + dy * rayOuter),
                strokeWidth = size.minDimension * 0.040f,
                cap = StrokeCap.Round
            )
        }
        drawCircle(color = rayColor, radius = radius, center = center)
        drawCircle(
            color = Color(0xFFFFE39B),
            radius = radius * 0.78f,
            center = center
        )
        // A curved solar highlight and a small surface mark show the disc turning.
        drawArc(
            color = Color(0xFFFFC857).copy(alpha = 0.72f),
            startAngle = 205f,
            sweepAngle = 105f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 0.48f, center.y - radius * 0.48f),
            size = androidx.compose.ui.geometry.Size(radius * 0.96f, radius * 0.96f),
            style = Stroke(width = radius * 0.075f, cap = StrokeCap.Round)
        )
        drawCircle(
            color = Color(0xFFFFF7D6).copy(alpha = 0.78f),
            radius = radius * 0.075f,
            center = Offset(center.x + radius * 0.30f, center.y - radius * 0.16f)
        )
    }
}

private fun DrawScope.drawStandaloneCloud(edge: Float, drift: Float) {
    // A dedicated centered silhouette for a cloud-only forecast: broad,
    // softly rounded lobes and a substantial base, without looking stretched.
    val left = size.width * 0.10f + drift
    val top = size.height * 0.27f
    val width = size.width * 0.80f
    val height = size.height * 0.47f
    val path = Path().apply {
        moveTo(left + width * 0.20f, top + height * 0.88f)
        cubicTo(left + width * 0.04f, top + height * 0.88f, left - width * 0.01f, top + height * 0.70f, left + width * 0.04f, top + height * 0.53f)
        cubicTo(left + width * 0.08f, top + height * 0.35f, left + width * 0.22f, top + height * 0.25f, left + width * 0.39f, top + height * 0.34f)
        cubicTo(left + width * 0.43f, top + height * 0.08f, left + width * 0.61f, top - height * 0.01f, left + width * 0.75f, top + height * 0.17f)
        cubicTo(left + width * 0.84f, top + height * 0.27f, left + width * 0.85f, top + height * 0.37f, left + width * 0.84f, top + height * 0.42f)
        cubicTo(left + width * 1.02f, top + height * 0.43f, left + width * 1.04f, top + height * 0.67f, left + width * 0.94f, top + height * 0.80f)
        cubicTo(left + width * 0.87f, top + height * 0.91f, left + width * 0.75f, top + height * 0.93f, left + width * 0.63f, top + height * 0.91f)
        lineTo(left + width * 0.20f, top + height * 0.91f)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFD5E0E8)),
            startY = top,
            endY = top + height
        )
    )
    drawPath(
        path = path,
        color = Color.White.copy(alpha = 0.96f),
        style = Stroke(width = edge * 0.022f)
    )
}

private fun DrawScope.drawWeatherCloud(left: Float, top: Float, width: Float, height: Float) {
    // Taller, rounder cloud lobes give the classic icon a fuller silhouette.
    val path = Path().apply {
        moveTo(left + width * 0.17f, top + height * 0.88f)
        cubicTo(left + width * 0.00f, top + height * 0.88f, left - width * 0.02f, top + height * 0.64f, left + width * 0.06f, top + height * 0.45f)
        cubicTo(left + width * 0.12f, top + height * 0.20f, left + width * 0.29f, top + height * 0.18f, left + width * 0.41f, top + height * 0.34f)
        cubicTo(left + width * 0.45f, top + height * 0.02f, left + width * 0.74f, top + height * 0.00f, left + width * 0.81f, top + height * 0.36f)
        cubicTo(left + width * 1.00f, top + height * 0.33f, left + width * 1.04f, top + height * 0.59f, left + width * 0.96f, top + height * 0.76f)
        cubicTo(left + width * 0.91f, top + height * 0.91f, left + width * 0.78f, top + height * 0.94f, left + width * 0.65f, top + height * 0.92f)
        lineTo(left + width * 0.17f, top + height * 0.92f)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFF7FAFC), Color(0xFFD4DEE5)),
            startY = top,
            endY = top + height
        )
    )
    drawPath(
        path = path,
        color = Color.White.copy(alpha = 0.88f),
        style = Stroke(width = size.minDimension * 0.024f)
    )
}

private fun DrawScope.drawRain(edge: Float, count: Int, progress: Float, icy: Boolean = false) {
    val rain = if (icy) Color(0xFFBDEBFF) else Color(0xFF36B9F2)
    repeat(count) { index ->
        val x = size.width * (0.30f + index * (0.40f / (count - 1).coerceAtLeast(1)))
        val phase = (progress + index * 0.19f) % 1f
        val y = size.height * (0.61f + phase * 0.24f)
        val driftX = edge * 0.018f * sin((phase * Math.PI * 2.0).toFloat())
        drawLine(
            color = rain.copy(alpha = 0.72f + phase * 0.28f),
            start = Offset(x + driftX, y),
            end = Offset(x + driftX - edge * 0.045f, y + edge * 0.14f),
            strokeWidth = edge * 0.045f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawHail(edge: Float, count: Int, progress: Float) {
    repeat(count) { index ->
        val phase = (progress + index * 0.21f) % 1f
        val x = size.width * (0.28f + index * (0.44f / (count - 1).coerceAtLeast(1)))
        val y = size.height * (0.63f + phase * 0.25f)
        val radius = edge * (0.027f + (index % 2) * 0.008f)
        val bounce = if (phase > 0.82f) (phase - 0.82f) * 5f else 0f
        drawCircle(Color(0xFF7896A8).copy(alpha = 0.55f), radius * 1.18f, Offset(x, y + bounce * edge * 0.025f))
        drawCircle(Color(0xFFE8F7FF), radius, Offset(x - edge * 0.012f, y - bounce * edge * 0.025f))
        drawCircle(Color.White.copy(alpha = 0.9f), radius * 0.28f, Offset(x - radius * 0.28f, y - radius * 0.35f - bounce * edge * 0.025f))
    }
}

private fun DrawScope.drawHeatShimmer(edge: Float, progress: Float, pulse: Float) {
    repeat(3) { index ->
        val phase = (progress + index * 0.29f) % 1f
        val x = size.width * (0.34f + index * 0.16f)
        val y = size.height * (0.68f - phase * 0.16f)
        val path = Path().apply {
            moveTo(x, y + edge * 0.055f)
            cubicTo(x - edge * 0.045f, y + edge * 0.015f, x + edge * 0.045f, y - edge * 0.02f, x, y - edge * 0.06f)
        }
        drawPath(path, Color(0xFFFFD36A).copy(alpha = 0.28f + pulse * 0.18f), style = Stroke(width = edge * 0.018f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawFrost(edge: Float, progress: Float, pulse: Float) {
    val points = listOf(0.25f to 0.72f, 0.48f to 0.80f, 0.72f to 0.70f)
    points.forEachIndexed { index, (x, y) ->
        val twinkle = 0.45f + 0.45f * (0.5f + 0.5f * sin((progress * Math.PI * 2.0 + index).toFloat()))
        val radius = edge * (0.025f + pulse * 0.008f)
        val center = Offset(size.width * x, size.height * y)
        drawLine(Color(0xFFBDEBFF).copy(alpha = twinkle), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), edge * 0.018f, StrokeCap.Round)
        drawLine(Color(0xFFBDEBFF).copy(alpha = twinkle), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), edge * 0.018f, StrokeCap.Round)
    }
}

private fun DrawScope.drawFogLines(edge: Float, progress: Float) {
    val fog = Color(0xFFE5F0F6).copy(alpha = 0.92f)
    listOf(0.71f to 0.73f, 0.80f to 0.88f, 0.89f to 0.76f).forEachIndexed { index, (y, end) ->
        val drift = size.width * (((progress + index * 0.31f) % 1f) - 0.5f) * 0.12f
        drawLine(
            color = fog.copy(alpha = 0.55f + 0.35f * (0.5f + 0.5f * sin((progress * 6.283f + index).toDouble()).toFloat())),
            start = Offset(size.width * 0.18f + drift, size.height * y),
            end = Offset(size.width * end + drift, size.height * y),
            strokeWidth = edge * 0.035f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawSnow(edge: Float, count: Int, progress: Float) {
    val snow = Color(0xFFBDEBFF)
    repeat(count) { index ->
        val x = size.width * (0.29f + index * (0.42f / (count - 1).coerceAtLeast(1)))
        val phase = (progress + index * 0.23f) % 1f
        val y = size.height * (0.67f + phase * 0.24f)
        val driftX = edge * 0.035f * sin((phase * Math.PI * 2.0 + index).toFloat())
        val xAnimated = x + driftX
        val radius = edge * 0.035f
        drawLine(snow, Offset(xAnimated - radius, y), Offset(xAnimated + radius, y), edge * 0.022f, StrokeCap.Round)
        drawLine(snow, Offset(xAnimated, y - radius), Offset(xAnimated, y + radius), edge * 0.022f, StrokeCap.Round)
        drawLine(snow, Offset(xAnimated - radius * 0.72f, y - radius * 0.72f), Offset(xAnimated + radius * 0.72f, y + radius * 0.72f), edge * 0.022f, StrokeCap.Round)
        drawLine(snow, Offset(xAnimated - radius * 0.72f, y + radius * 0.72f), Offset(xAnimated + radius * 0.72f, y - radius * 0.72f), edge * 0.022f, StrokeCap.Round)
    }
}

private fun DrawScope.drawLightning(edge: Float, flash: Float) {
    val path = Path().apply {
        moveTo(size.width * 0.54f, size.height * 0.56f)
        lineTo(size.width * 0.42f, size.height * 0.77f)
        lineTo(size.width * 0.53f, size.height * 0.77f)
        lineTo(size.width * 0.45f, size.height * 0.96f)
        lineTo(size.width * 0.70f, size.height * 0.68f)
        lineTo(size.width * 0.57f, size.height * 0.68f)
        close()
    }
    drawPath(path, color = Color(0xFFFFD05B).copy(alpha = flash))
    drawPath(path, color = Color(0xFFFFF0B3).copy(alpha = flash), style = Stroke(width = edge * 0.018f))
}

private fun DrawScope.drawWind(edge: Float, count: Int, progress: Float) {
    // Three-dimensional-looking S-curves read as moving gusts instead of flat lines.
    val color = Color(0xFFE4F3FB)
    repeat(count) { index ->
        val travel = (progress + index * 0.27f) % 1f
        val startX = size.width * (0.08f + travel * 0.16f + (index % 2) * 0.035f)
        val span = size.width * (0.76f - (index % 2) * 0.07f)
        val y = size.height * (0.70f + index * 0.095f)
        val wave = edge * (0.022f + index * 0.004f)
        val path = Path().apply {
            moveTo(startX, y)
            cubicTo(
                startX + span * 0.16f, y - wave * 1.7f,
                startX + span * 0.33f, y + wave * 1.8f,
                startX + span * 0.52f, y + wave * 0.12f
            )
            cubicTo(
                startX + span * 0.68f, y - wave * 1.7f,
                startX + span * 0.84f, y - wave * 0.95f,
                startX + span, y - wave * 0.25f
            )
        }
        drawPath(
            path,
            color.copy(alpha = 0.90f - index * 0.08f),
            style = Stroke(width = edge * 0.032f, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawMoon(edge: Float, progress: Float, pulse: Float) {
    val moon = Path().apply {
        moveTo(size.width * 0.67f, size.height * 0.18f)
        cubicTo(size.width * 0.37f, size.height * 0.18f, size.width * 0.26f, size.height * 0.60f, size.width * 0.50f, size.height * 0.79f)
        cubicTo(size.width * 0.62f, size.height * 0.88f, size.width * 0.80f, size.height * 0.84f, size.width * 0.89f, size.height * 0.72f)
        cubicTo(size.width * 0.60f, size.height * 0.76f, size.width * 0.48f, size.height * 0.43f, size.width * 0.67f, size.height * 0.18f)
        close()
    }
    val moonGlow = Color(0xFFF4F3E8).copy(alpha = 0.10f + pulse * 0.12f)
    drawCircle(moonGlow, edge * 0.34f, Offset(size.width * 0.58f, size.height * 0.49f))
    drawPath(moon, color = Color(0xFFF4F3E8))
    listOf(Offset(size.width * 0.24f, size.height * 0.29f), Offset(size.width * 0.79f, size.height * 0.25f), Offset(size.width * 0.23f, size.height * 0.68f)).forEachIndexed { index, point ->
        val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin((progress * Math.PI * 2.0 + index * 1.8).toFloat()))
        drawCircle(Color.White.copy(alpha = twinkle), edge * (0.018f + 0.012f * pulse), point)
    }
}
