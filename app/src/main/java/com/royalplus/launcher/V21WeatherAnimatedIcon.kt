package com.royalplus.launcher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Weather symbols are drawn natively; TV WebView/SVG support is not required. */
internal enum class RoyalV21WeatherIcon(val fileName: String, val label: String) {
    SUNNY("sunny.svg", "مشمس"), PARTLY_CLOUDY("partly_cloudy.svg", "غائم جزئياً"),
    CLOUDY("cloudy.svg", "غائم"), RAIN("rain.svg", "مطر"), DRIZZLE("drizzle.svg", "رذاذ"),
    HEAVY_RAIN("heavy_rain.svg", "أمطار غزيرة"), THUNDERSTORM("thunderstorm.svg", "عاصفة رعدية"),
    LIGHTNING("lightning.svg", "برق"), SNOW("snow.svg", "ثلج"), HEAVY_SNOW("heavy_snow.svg", "ثلوج كثيفة"),
    SLEET("sleet.svg", "مطر متجمد"), HAIL("hail.svg", "بَرَد"), WIND("wind.svg", "رياح"),
    STRONG_WIND("strong_wind.svg", "رياح شديدة"), FOG("fog.svg", "ضباب"), MIST("mist.svg", "شبورة خفيفة"),
    DUST("dust.svg", "غبار"), HOT("hot.svg", "حرارة مرتفعة"), COLD("cold.svg", "برودة"),
    SUNRISE("sunrise.svg", "شروق الشمس"), SUNSET("sunset.svg", "غروب الشمس"),
    CLEAR_NIGHT("clear_night.svg", "سماء صافية ليلاً"), TORNADO("tornado.svg", "إعصار"),
    TEMPERATURE("temperature.svg", "درجة الحرارة"), HUMIDITY("humidity.svg", "الرطوبة"),
    RAINBOW("rainbow.svg", "قوس قزح")
}

internal object RoyalV21WeatherIconResolver {
    fun resolve(weatherCode: Int?, description: String = "", apparentTemperatureC: Int? = null,
        windSpeedKmh: Int? = null, hour: Int? = null): RoyalV21WeatherIcon {
        val text = description.lowercase(java.util.Locale.ROOT)
        if (text.contains("tornado") || text.contains("إعصار") || text.contains("kasırga")) return RoyalV21WeatherIcon.TORNADO
        if (text.contains("rainbow") || text.contains("قوس قزح") || text.contains("gökkuşağı")) return RoyalV21WeatherIcon.RAINBOW
        if (text.contains("dust") || text.contains("غبار") || text.contains("toz fırtınası")) return RoyalV21WeatherIcon.DUST
        if (text.contains("lightning") || text.contains("برق") || text.contains("şimşek")) return RoyalV21WeatherIcon.LIGHTNING
        if (text.contains("freezing") || text.contains("متجمد") || text.contains("donan")) return RoyalV21WeatherIcon.SLEET
        if (text.contains("mist") || text.contains("شبورة") || text.contains("pus")) return RoyalV21WeatherIcon.MIST
        if (text.contains("fog") || text.contains("ضباب") || text.contains("sis")) return RoyalV21WeatherIcon.FOG
        when (weatherCode) {
            0, 1 -> {
                if (hour != null && hour in 5..7) return RoyalV21WeatherIcon.SUNRISE
                if (hour != null && hour in 17..19) return RoyalV21WeatherIcon.SUNSET
                if (hour != null && (hour >= 20 || hour < 5)) return RoyalV21WeatherIcon.CLEAR_NIGHT
                if ((windSpeedKmh ?: 0) >= 45) return RoyalV21WeatherIcon.STRONG_WIND
                if ((windSpeedKmh ?: 0) >= 28) return RoyalV21WeatherIcon.WIND
                if (apparentTemperatureC != null && apparentTemperatureC >= 35) return RoyalV21WeatherIcon.HOT
                if (apparentTemperatureC != null && apparentTemperatureC <= 0) return RoyalV21WeatherIcon.COLD
                return RoyalV21WeatherIcon.SUNNY
            }
            2 -> return RoyalV21WeatherIcon.PARTLY_CLOUDY
            3 -> return RoyalV21WeatherIcon.CLOUDY
            45, 48 -> return RoyalV21WeatherIcon.FOG
            51, 53, 55 -> return RoyalV21WeatherIcon.DRIZZLE
            56, 57, 66, 67 -> return RoyalV21WeatherIcon.SLEET
            61, 63, 80, 81 -> return RoyalV21WeatherIcon.RAIN
            65, 82 -> return RoyalV21WeatherIcon.HEAVY_RAIN
            71, 73, 77, 85 -> return RoyalV21WeatherIcon.SNOW
            75, 86 -> return RoyalV21WeatherIcon.HEAVY_SNOW
            95 -> return RoyalV21WeatherIcon.THUNDERSTORM
            96, 99 -> return RoyalV21WeatherIcon.HAIL
        }
        if ((windSpeedKmh ?: 0) >= 45) return RoyalV21WeatherIcon.STRONG_WIND
        if ((windSpeedKmh ?: 0) >= 28) return RoyalV21WeatherIcon.WIND
        if (apparentTemperatureC != null && apparentTemperatureC >= 35) return RoyalV21WeatherIcon.HOT
        if (apparentTemperatureC != null && apparentTemperatureC <= 0) return RoyalV21WeatherIcon.COLD
        return RoyalV21WeatherIcon.CLOUDY
    }
}

@Composable
internal fun RoyalV21WeatherAnimatedIcon(icon: RoyalV21WeatherIcon, modifier: Modifier = Modifier,
    motionEnabled: Boolean = true, contentDescription: String? = null) {
    val transition = rememberInfiniteTransition(label = "royal-native-weather")
    val shouldAnimate = motionEnabled && icon in setOf(
        RoyalV21WeatherIcon.RAIN, RoyalV21WeatherIcon.DRIZZLE, RoyalV21WeatherIcon.HEAVY_RAIN,
        RoyalV21WeatherIcon.SNOW, RoyalV21WeatherIcon.HEAVY_SNOW,
        RoyalV21WeatherIcon.WIND, RoyalV21WeatherIcon.STRONG_WIND
    )
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (shouldAnimate) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Restart),
        label = "weather-symbol-phase"
    )
    Canvas(modifier.semantics { this.contentDescription = contentDescription ?: icon.label }) {
        val u = size.minDimension.coerceAtLeast(1f)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val stroke = (u * 0.055f).coerceAtLeast(1.1.dp.toPx())
        val gold = Color(0xFFFFD36A)
        val pale = Color(0xFFEAF5FF)
        when (icon) {
            RoyalV21WeatherIcon.SUNNY, RoyalV21WeatherIcon.HOT ->
                drawNativeSun(cx, cy, u, gold, stroke)
            RoyalV21WeatherIcon.PARTLY_CLOUDY, RoyalV21WeatherIcon.CLOUDY,
            RoyalV21WeatherIcon.FOG, RoyalV21WeatherIcon.MIST -> drawNativeHaze(cx, cy, u, pale, stroke)
            RoyalV21WeatherIcon.RAIN, RoyalV21WeatherIcon.DRIZZLE, RoyalV21WeatherIcon.HEAVY_RAIN,
            RoyalV21WeatherIcon.SLEET, RoyalV21WeatherIcon.HAIL -> {
                drawNativeRain(cx, cy, u, pale, stroke, phase,
                    if (icon == RoyalV21WeatherIcon.HEAVY_RAIN) 5 else 3)
                if (icon == RoyalV21WeatherIcon.HAIL) repeat(3) { drawCircle(gold, u * 0.035f,
                    Offset(cx + (it - 1) * u * 0.22f, cy + u * 0.35f)) }
            }
            RoyalV21WeatherIcon.THUNDERSTORM, RoyalV21WeatherIcon.LIGHTNING ->
                drawNativeLightning(cx, cy, u, gold, stroke)
            RoyalV21WeatherIcon.SNOW, RoyalV21WeatherIcon.HEAVY_SNOW, RoyalV21WeatherIcon.COLD ->
                drawNativeSnow(cx, cy, u, pale, stroke, phase, if (icon == RoyalV21WeatherIcon.HEAVY_SNOW) 5 else 3)
            RoyalV21WeatherIcon.WIND, RoyalV21WeatherIcon.STRONG_WIND,
            RoyalV21WeatherIcon.DUST, RoyalV21WeatherIcon.TORNADO ->
                drawNativeWind(cx, cy, u, pale, stroke, phase, icon == RoyalV21WeatherIcon.STRONG_WIND)
            RoyalV21WeatherIcon.SUNRISE, RoyalV21WeatherIcon.SUNSET ->
                drawNativeHorizon(cx, cy, u, gold, stroke)
            RoyalV21WeatherIcon.CLEAR_NIGHT -> drawNativeMoon(cx, cy, u, pale)
            RoyalV21WeatherIcon.TEMPERATURE -> drawNativeThermometer(cx, cy, u, gold, stroke)
            RoyalV21WeatherIcon.HUMIDITY -> drawNativeDrop(cx, cy, u * 0.34f, pale)
            RoyalV21WeatherIcon.RAINBOW -> drawNativeRainbow(cx, cy, u, pale, gold, stroke)
        }
    }
}

private fun DrawScope.drawNativeSun(cx: Float, cy: Float, u: Float, c: Color, s: Float) {
    val r = u * 0.20f
    drawCircle(c, r, Offset(cx, cy), style = Stroke(s))
    val pulse = 1f
    repeat(8) { i ->
        val a = i * PI / 4
        val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
        drawLine(c, Offset(cx + dx * r * 1.35f, cy + dy * r * 1.35f),
            Offset(cx + dx * r * 1.85f * pulse, cy + dy * r * 1.85f * pulse), s, StrokeCap.Round)
    }
}

private fun DrawScope.drawNativeHaze(cx: Float, cy: Float, u: Float, c: Color, s: Float) {
    repeat(3) { i ->
        val y = cy + (i - 1) * u * 0.22f
        val shift = 0f
        drawLine(c, Offset(cx - u * 0.39f + shift, y), Offset(cx + u * 0.39f + shift, y),
            s * 0.9f, StrokeCap.Round)
    }
}

private fun DrawScope.drawNativeRain(cx: Float, cy: Float, u: Float, c: Color, s: Float, phase: Float, count: Int) {
    repeat(count) { i ->
        val x = cx + (i - (count - 1) / 2f) * u * 0.22f
        val y = cy + u * 0.08f + ((phase + i * 0.19f) % 1f) * u * 0.12f
        drawLine(c, Offset(x, y), Offset(x - u * 0.045f, y + u * 0.15f), s * 0.8f, StrokeCap.Round)
    }
}

private fun DrawScope.drawNativeSnow(cx: Float, cy: Float, u: Float, c: Color, s: Float, phase: Float, count: Int) {
    repeat(count) { i ->
        val x = cx + (i - (count - 1) / 2f) * u * 0.23f
        val y = cy + (sin(phase * PI * 2 + i).toFloat() * u * 0.055f)
        repeat(3) { arm ->
            val a = arm * PI / 3
            val dx = cos(a).toFloat() * u * 0.075f
            val dy = sin(a).toFloat() * u * 0.075f
            drawLine(c, Offset(x - dx, y - dy), Offset(x + dx, y + dy), s * 0.65f, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawNativeWind(cx: Float, cy: Float, u: Float, c: Color, s: Float, phase: Float, strong: Boolean) {
    repeat(if (strong) 4 else 3) { i ->
        val y = cy + (i - 1.5f) * u * 0.19f
        val shift = ((phase + i * 0.23f) % 1f) * u * 0.14f
        val path = Path().apply {
            moveTo(cx - u * 0.42f + shift, y)
            cubicTo(cx - u * 0.10f + shift, y - u * 0.10f,
                cx + u * 0.12f + shift, y + u * 0.10f, cx + u * 0.42f + shift, y)
        }
        drawPath(path, c, style = Stroke(s * 0.8f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawNativeLightning(cx: Float, cy: Float, u: Float, c: Color, s: Float) {
    val path = Path().apply {
        moveTo(cx + u * 0.10f, cy - u * 0.34f)
        lineTo(cx - u * 0.16f, cy + u * 0.05f)
        lineTo(cx + u * 0.015f, cy + u * 0.05f)
        lineTo(cx - u * 0.10f, cy + u * 0.36f)
        lineTo(cx + u * 0.20f, cy - u * 0.02f)
        lineTo(cx + u * 0.035f, cy - u * 0.02f)
        close()
    }
    drawPath(path, c)
}

private fun DrawScope.drawNativeHorizon(cx: Float, cy: Float, u: Float, c: Color, s: Float) {
    drawArc(c, 180f, 180f, false, Offset(cx - u * 0.28f, cy - u * 0.17f),
        androidx.compose.ui.geometry.Size(u * 0.56f, u * 0.56f), style = Stroke(s))
    drawLine(c, Offset(cx - u * 0.42f, cy + u * 0.12f), Offset(cx + u * 0.42f, cy + u * 0.12f), s, StrokeCap.Round)
}

private fun DrawScope.drawNativeMoon(cx: Float, cy: Float, u: Float, c: Color) {
    val path = Path().apply {
        moveTo(cx + u * 0.18f, cy - u * 0.34f)
        cubicTo(cx - u * 0.30f, cy - u * 0.30f, cx - u * 0.42f, cy + u * 0.26f, cx - u * 0.04f, cy + u * 0.38f)
        cubicTo(cx + u * 0.17f, cy + u * 0.43f, cx + u * 0.34f, cy + u * 0.30f, cx + u * 0.40f, cy + u * 0.12f)
        cubicTo(cx + u * 0.03f, cy + u * 0.19f, cx - u * 0.06f, cy - u * 0.20f, cx + u * 0.18f, cy - u * 0.34f)
        close()
    }
    drawPath(path, c)
}

private fun DrawScope.drawNativeThermometer(cx: Float, cy: Float, u: Float, c: Color, s: Float) {
    drawLine(c, Offset(cx, cy - u * 0.34f), Offset(cx, cy + u * 0.20f), s * 1.4f, StrokeCap.Round)
    drawCircle(c, u * 0.13f, Offset(cx, cy + u * 0.27f))
    drawCircle(c, u * 0.09f, Offset(cx, cy - u * 0.30f), style = Stroke(s))
}

private fun DrawScope.drawNativeDrop(cx: Float, cy: Float, u: Float, c: Color) {
    val path = Path().apply {
        moveTo(cx, cy - u)
        cubicTo(cx - u * 0.36f, cy - u * 0.34f, cx - u * 0.78f, cy + u * 0.20f, cx - u * 0.58f, cy + u * 0.62f)
        cubicTo(cx - u * 0.34f, cy + u, cx + u * 0.34f, cy + u, cx + u * 0.58f, cy + u * 0.62f)
        cubicTo(cx + u * 0.78f, cy + u * 0.20f, cx + u * 0.36f, cy - u * 0.34f, cx, cy - u)
        close()
    }
    drawPath(path, c)
}

private fun DrawScope.drawNativeRainbow(cx: Float, cy: Float, u: Float, cool: Color, warm: Color, s: Float) {
    drawArc(warm, 180f, 180f, false, Offset(cx - u * 0.38f, cy - u * 0.20f),
        androidx.compose.ui.geometry.Size(u * 0.76f, u * 0.76f), style = Stroke(s))
    drawArc(cool, 180f, 180f, false, Offset(cx - u * 0.25f, cy - u * 0.07f),
        androidx.compose.ui.geometry.Size(u * 0.50f, u * 0.50f), style = Stroke(s))
}
