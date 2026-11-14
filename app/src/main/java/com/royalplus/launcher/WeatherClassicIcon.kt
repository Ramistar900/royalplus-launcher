package com.royalplus.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.cos
import kotlin.math.sin

/**
 * Static, lightweight weather artwork matching the classic Royal forecast style:
 * a warm sun, soft white/grey cloud, and blue rain/snow marks.
 */
private enum class RoyalClassicWeatherKind {
    SUNNY, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, HEAVY_RAIN,
    SNOW, HEAVY_SNOW, THUNDERSTORM, WIND, STRONG_WIND,
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
        56, 57, 66, 67 -> return RoyalClassicWeatherKind.RAIN
        61, 63, 80, 81 -> return RoyalClassicWeatherKind.RAIN
        65, 82 -> return RoyalClassicWeatherKind.HEAVY_RAIN
        71, 73, 77, 85 -> return RoyalClassicWeatherKind.SNOW
        75, 86 -> return RoyalClassicWeatherKind.HEAVY_SNOW
        95, 96, 99 -> return RoyalClassicWeatherKind.THUNDERSTORM
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
    Canvas(modifier.semantics {
        this.contentDescription = contentDescription ?: description.ifBlank { kind.name }
    }) {
        val edge = size.minDimension
        if (edge <= 0f) return@Canvas

        when (kind) {
            RoyalClassicWeatherKind.SUNNY -> drawSun(
                center = Offset(size.width * 0.5f, size.height * 0.5f),
                radius = edge * 0.20f
            )
            RoyalClassicWeatherKind.PARTLY_CLOUDY -> {
                drawSun(Offset(size.width * 0.34f, size.height * 0.37f), edge * 0.17f)
                drawWeatherCloud(size.width * 0.17f, size.height * 0.38f, size.width * 0.76f, size.height * 0.43f)
            }
            RoyalClassicWeatherKind.CLOUDY,
            RoyalClassicWeatherKind.COLD,
            RoyalClassicWeatherKind.HOT -> drawWeatherCloud(
                size.width * 0.10f, size.height * 0.28f, size.width * 0.82f, size.height * 0.48f
            )
            RoyalClassicWeatherKind.FOG -> {
                drawWeatherCloud(size.width * 0.12f, size.height * 0.19f, size.width * 0.78f, size.height * 0.45f)
                drawFogLines(edge)
            }
            RoyalClassicWeatherKind.DRIZZLE,
            RoyalClassicWeatherKind.RAIN,
            RoyalClassicWeatherKind.HEAVY_RAIN -> {
                drawWeatherCloud(size.width * 0.12f, size.height * 0.17f, size.width * 0.78f, size.height * 0.48f)
                drawRain(edge, if (kind == RoyalClassicWeatherKind.DRIZZLE) 3 else if (kind == RoyalClassicWeatherKind.HEAVY_RAIN) 6 else 5)
            }
            RoyalClassicWeatherKind.SNOW,
            RoyalClassicWeatherKind.HEAVY_SNOW -> {
                drawWeatherCloud(size.width * 0.12f, size.height * 0.16f, size.width * 0.78f, size.height * 0.48f)
                drawSnow(edge, if (kind == RoyalClassicWeatherKind.HEAVY_SNOW) 6 else 4)
            }
            RoyalClassicWeatherKind.THUNDERSTORM -> {
                drawWeatherCloud(size.width * 0.12f, size.height * 0.13f, size.width * 0.78f, size.height * 0.48f)
                drawLightning(edge)
                drawRain(edge, 3)
            }
            RoyalClassicWeatherKind.WIND,
            RoyalClassicWeatherKind.STRONG_WIND -> {
                drawWeatherCloud(size.width * 0.18f, size.height * 0.12f, size.width * 0.68f, size.height * 0.42f)
                drawWind(edge, if (kind == RoyalClassicWeatherKind.STRONG_WIND) 3 else 2)
            }
            RoyalClassicWeatherKind.CLEAR_NIGHT -> drawMoon(edge)
            RoyalClassicWeatherKind.SUNRISE,
            RoyalClassicWeatherKind.SUNSET -> {
                drawSun(Offset(size.width * 0.5f, size.height * 0.56f), edge * 0.19f, horizon = true)
                val lineY = size.height * 0.67f
                drawLine(Color.White.copy(alpha = 0.88f), Offset(size.width * 0.14f, lineY), Offset(size.width * 0.86f, lineY), edge * 0.025f, StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.drawSun(center: Offset, radius: Float, horizon: Boolean = false) {
    val rayColor = Color(0xFFFFC857)
    val rayInner = radius * 1.30f
    val rayOuter = radius * 1.72f
    repeat(8) { index ->
        val angle = Math.PI * index / 4.0
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        if (!horizon || dy <= 0.35f) {
            drawLine(
                color = rayColor,
                start = Offset(center.x + dx * rayInner, center.y + dy * rayInner),
                end = Offset(center.x + dx * rayOuter, center.y + dy * rayOuter),
                strokeWidth = size.minDimension * 0.035f,
                cap = StrokeCap.Round
            )
        }
    }
    drawCircle(
        color = rayColor,
        radius = radius,
        center = center
    )
    drawCircle(
        color = Color(0xFFFFE39B),
        radius = radius * 0.78f,
        center = center
    )
}

private fun DrawScope.drawWeatherCloud(left: Float, top: Float, width: Float, height: Float) {
    val path = Path().apply {
        moveTo(left + width * 0.17f, top + height * 0.78f)
        cubicTo(left + width * 0.02f, top + height * 0.78f, left - width * 0.01f, top + height * 0.59f, left + width * 0.06f, top + height * 0.43f)
        cubicTo(left + width * 0.12f, top + height * 0.28f, left + width * 0.27f, top + height * 0.25f, left + width * 0.39f, top + height * 0.34f)
        cubicTo(left + width * 0.43f, top + height * 0.08f, left + width * 0.73f, top + height * 0.04f, left + width * 0.79f, top + height * 0.35f)
        cubicTo(left + width * 0.98f, top + height * 0.33f, left + width * 1.04f, top + height * 0.52f, left + width * 0.96f, top + height * 0.68f)
        cubicTo(left + width * 0.91f, top + height * 0.80f, left + width * 0.78f, top + height * 0.84f, left + width * 0.66f, top + height * 0.82f)
        lineTo(left + width * 0.17f, top + height * 0.82f)
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
        color = Color.White.copy(alpha = 0.80f),
        style = Stroke(width = size.minDimension * 0.018f)
    )
}

private fun DrawScope.drawRain(edge: Float, count: Int) {
    val rain = Color(0xFF36B9F2)
    repeat(count) { index ->
        val x = size.width * (0.30f + index * (0.40f / (count - 1).coerceAtLeast(1)))
        val y = size.height * (0.66f + (index % 2) * 0.035f)
        drawLine(
            color = rain,
            start = Offset(x, y),
            end = Offset(x - edge * 0.045f, y + edge * 0.14f),
            strokeWidth = edge * 0.045f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawFogLines(edge: Float) {
    val fog = Color(0xFFE5F0F6).copy(alpha = 0.92f)
    listOf(0.71f to 0.73f, 0.80f to 0.88f, 0.89f to 0.76f).forEach { (y, end) ->
        drawLine(
            color = fog,
            start = Offset(size.width * 0.18f, size.height * y),
            end = Offset(size.width * end, size.height * y),
            strokeWidth = edge * 0.035f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawSnow(edge: Float, count: Int) {
    val snow = Color(0xFFBDEBFF)
    repeat(count) { index ->
        val x = size.width * (0.29f + index * (0.42f / (count - 1).coerceAtLeast(1)))
        val y = size.height * (0.76f + (index % 2) * 0.09f)
        val radius = edge * 0.035f
        drawLine(snow, Offset(x - radius, y), Offset(x + radius, y), edge * 0.022f, StrokeCap.Round)
        drawLine(snow, Offset(x, y - radius), Offset(x, y + radius), edge * 0.022f, StrokeCap.Round)
        drawLine(snow, Offset(x - radius * 0.72f, y - radius * 0.72f), Offset(x + radius * 0.72f, y + radius * 0.72f), edge * 0.022f, StrokeCap.Round)
        drawLine(snow, Offset(x - radius * 0.72f, y + radius * 0.72f), Offset(x + radius * 0.72f, y - radius * 0.72f), edge * 0.022f, StrokeCap.Round)
    }
}

private fun DrawScope.drawLightning(edge: Float) {
    val path = Path().apply {
        moveTo(size.width * 0.54f, size.height * 0.56f)
        lineTo(size.width * 0.42f, size.height * 0.77f)
        lineTo(size.width * 0.53f, size.height * 0.77f)
        lineTo(size.width * 0.45f, size.height * 0.96f)
        lineTo(size.width * 0.70f, size.height * 0.68f)
        lineTo(size.width * 0.57f, size.height * 0.68f)
        close()
    }
    drawPath(path, color = Color(0xFFFFD05B))
    drawPath(path, color = Color(0xFFFFF0B3), style = Stroke(width = edge * 0.018f))
}

private fun DrawScope.drawWind(edge: Float, count: Int) {
    val color = Color(0xFFE4F3FB)
    repeat(count) { index ->
        val y = size.height * (0.72f + index * 0.10f)
        val path = Path().apply {
            moveTo(size.width * 0.18f, y)
            cubicTo(size.width * 0.42f, y - edge * 0.06f, size.width * 0.58f, y + edge * 0.07f, size.width * 0.82f, y)
        }
        drawPath(path, color, style = Stroke(width = edge * 0.032f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawMoon(edge: Float) {
    val moon = Path().apply {
        moveTo(size.width * 0.67f, size.height * 0.18f)
        cubicTo(size.width * 0.37f, size.height * 0.18f, size.width * 0.26f, size.height * 0.60f, size.width * 0.50f, size.height * 0.79f)
        cubicTo(size.width * 0.62f, size.height * 0.88f, size.width * 0.80f, size.height * 0.84f, size.width * 0.89f, size.height * 0.72f)
        cubicTo(size.width * 0.60f, size.height * 0.76f, size.width * 0.48f, size.height * 0.43f, size.width * 0.67f, size.height * 0.18f)
        close()
    }
    drawPath(moon, color = Color(0xFFF4F3E8))
    listOf(Offset(size.width * 0.24f, size.height * 0.29f), Offset(size.width * 0.79f, size.height * 0.25f), Offset(size.width * 0.23f, size.height * 0.68f)).forEach {
        drawCircle(Color.White.copy(alpha = 0.92f), edge * 0.025f, it)
    }
}
