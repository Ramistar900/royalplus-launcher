package com.royalplus.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/** Persistent switch shared by the home card, details screen, and forecast icons. */
internal object RoyalV21WeatherIconPreferences {
    const val PREFERENCES_NAME = "royal_v21_weather_icon_preferences"
    const val KEY_ANIMATED = "weather_icons_animated"
    const val DEFAULT_ANIMATED = true

    fun isAnimated(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ANIMATED, DEFAULT_ANIMATED)

    fun setAnimated(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ANIMATED, enabled).apply()
    }
}

/**
 * Renders original V21 SVG artwork directly into an Android bitmap. AndroidSVG
 * handles the artwork's paths, groups and gradients; a small LRU cache avoids
 * reparsing the SVG during TV forecast recompositions.
 */
private object RoyalV21WeatherBitmapCache {
    private const val EDGE_PX = 384
    private const val MAX_BYTES = 6 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(context: Context, icon: RoyalV21WeatherIcon): Bitmap? {
        cache.get(icon.fileName)?.let { return it }
        return synchronized(cache) {
            cache.get(icon.fileName) ?: runCatching {
                val svg = SVG.getFromAsset(context.assets, "weather_v21/${icon.fileName}")
                val bitmap = Bitmap.createBitmap(EDGE_PX, EDGE_PX, Bitmap.Config.ARGB_8888)
                svg.renderToCanvas(AndroidCanvas(bitmap))
                cache.put(icon.fileName, bitmap)
                bitmap
            }.getOrNull()
        }
    }
}

@Composable
private fun rememberV21WeatherAnimationEnabled(): Boolean {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) {
        context.getSharedPreferences(RoyalV21WeatherIconPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var enabled by remember(preferences) {
        mutableStateOf(preferences.getBoolean(
            RoyalV21WeatherIconPreferences.KEY_ANIMATED,
            RoyalV21WeatherIconPreferences.DEFAULT_ANIMATED
        ))
    }
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == null || key == RoyalV21WeatherIconPreferences.KEY_ANIMATED) {
                enabled = prefs.getBoolean(
                    RoyalV21WeatherIconPreferences.KEY_ANIMATED,
                    RoyalV21WeatherIconPreferences.DEFAULT_ANIMATED
                )
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return enabled
}

@Composable
internal fun RoyalV21WeatherAnimatedIcon(
    icon: RoyalV21WeatherIcon,
    modifier: Modifier = Modifier,
    motionEnabled: Boolean = true,
    contentDescription: String? = null
) {
    val context = LocalContext.current.applicationContext
    val animationEnabled = rememberV21WeatherAnimationEnabled()
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = icon.fileName,
        key2 = context
    ) {
        value = withContext(Dispatchers.IO) {
            RoyalV21WeatherBitmapCache.load(context, icon)?.asImageBitmap()
        }
    }

    Box(modifier = modifier.semantics {
        this.contentDescription = contentDescription ?: icon.label
    }) {
        if (image != null) {
            Image(
                bitmap = image!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            // Shown only while the original asset is rasterized off the UI thread.
            Canvas(Modifier.fillMaxSize()) {
                val edge = size.minDimension
                drawCircle(Color(0xFF194D6B), edge * 0.46f, Offset(size.width / 2f, size.height / 2f))
                drawCircle(Color(0xFFFFD36A), edge * 0.17f,
                    Offset(size.width / 2f, size.height / 2f), style = Stroke(edge * 0.035f))
            }
        }
        if (motionEnabled && animationEnabled) {
            RoyalV21WeatherMotionOverlay(icon, Modifier.fillMaxSize())
        }
    }
}

/**
 * Native Compose movement layered over the unchanged V21 illustration.
 * Disabling the setting removes this layer entirely and leaves a static icon.
 */
@Composable
private fun RoyalV21WeatherMotionOverlay(icon: RoyalV21WeatherIcon, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "royal-v21-icon-motion")
    val phase by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2_200, easing = LinearEasing), RepeatMode.Restart),
        label = "royal-v21-icon-phase"
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val u = size.minDimension
        val center = Offset(w / 2f, h / 2f)
        val ice = Color(0xFFA9EDFF)
        val gold = Color(0xFFFFD36A)

        when (icon) {
            RoyalV21WeatherIcon.RAIN, RoyalV21WeatherIcon.DRIZZLE,
            RoyalV21WeatherIcon.HEAVY_RAIN, RoyalV21WeatherIcon.SLEET,
            RoyalV21WeatherIcon.HAIL -> {
                val count = if (icon == RoyalV21WeatherIcon.HEAVY_RAIN) 6 else 4
                repeat(count) { i ->
                    val x = w * (0.27f + i * 0.105f)
                    val y = ((phase + i * 0.19f) % 1f) * h * 0.68f + h * 0.20f
                    drawLine(ice.copy(alpha = 0.76f), Offset(x, y),
                        Offset(x - u * 0.035f, y + u * 0.075f), u * 0.016f, StrokeCap.Round)
                }
                if (icon == RoyalV21WeatherIcon.HAIL) repeat(3) { i ->
                    val y = ((phase + i * 0.31f) % 1f) * h * 0.55f + h * 0.28f
                    drawCircle(ice, u * 0.018f, Offset(w * (0.36f + i * 0.14f), y))
                }
            }

            RoyalV21WeatherIcon.SNOW, RoyalV21WeatherIcon.HEAVY_SNOW,
            RoyalV21WeatherIcon.COLD -> {
                val count = if (icon == RoyalV21WeatherIcon.HEAVY_SNOW) 7 else 5
                repeat(count) { i ->
                    val x = w * (0.24f + i * 0.105f)
                    val y = ((phase + i * 0.16f) % 1f) * h * 0.72f + h * 0.15f
                    val r = u * if (i % 2 == 0) 0.018f else 0.012f
                    drawCircle(ice.copy(alpha = 0.78f), r, Offset(x, y))
                }
            }

            RoyalV21WeatherIcon.WIND, RoyalV21WeatherIcon.STRONG_WIND,
            RoyalV21WeatherIcon.DUST, RoyalV21WeatherIcon.TORNADO -> {
                val drift = (phase - 0.5f) * u * 0.16f
                repeat(3) { i ->
                    val y = h * (0.43f + i * 0.105f)
                    val path = Path().apply {
                        moveTo(w * 0.19f + drift, y)
                        cubicTo(w * 0.36f + drift, y - u * 0.07f,
                            w * 0.58f + drift, y + u * 0.07f, w * 0.81f + drift, y)
                    }
                    drawPath(path, ice.copy(alpha = 0.48f), style = Stroke(u * 0.012f))
                }
            }

            RoyalV21WeatherIcon.CLOUDY, RoyalV21WeatherIcon.PARTLY_CLOUDY,
            RoyalV21WeatherIcon.FOG, RoyalV21WeatherIcon.MIST -> {
                val drift = (phase - 0.5f) * u * 0.16f
                repeat(3) { i ->
                    val y = h * (0.42f + i * 0.11f)
                    drawLine(Color.White.copy(alpha = 0.24f),
                        Offset(w * 0.20f + drift, y), Offset(w * 0.80f + drift, y),
                        u * 0.016f, StrokeCap.Round)
                }
            }

            RoyalV21WeatherIcon.SUNNY, RoyalV21WeatherIcon.HOT,
            RoyalV21WeatherIcon.SUNRISE, RoyalV21WeatherIcon.SUNSET -> {
                val radius = u * (0.15f + phase * 0.035f)
                val at = if (icon == RoyalV21WeatherIcon.SUNRISE || icon == RoyalV21WeatherIcon.SUNSET)
                    Offset(center.x, h * 0.68f) else center
                drawCircle(gold.copy(alpha = 0.30f), radius, at, style = Stroke(u * 0.014f))
            }

            RoyalV21WeatherIcon.THUNDERSTORM, RoyalV21WeatherIcon.LIGHTNING -> {
                if (phase < 0.12f || phase > 0.88f) {
                    val alpha = if (phase < 0.12f) 0.035f else 0.02f
                    drawRect(Color.White.copy(alpha = alpha))
                }
            }

            RoyalV21WeatherIcon.CLEAR_NIGHT, RoyalV21WeatherIcon.RAINBOW -> {
                repeat(4) { i ->
                    val alpha = if ((phase + i * 0.23f) % 1f > 0.5f) 0.9f else 0.25f
                    drawCircle((if (icon == RoyalV21WeatherIcon.RAINBOW) gold else Color.White)
                        .copy(alpha = alpha), u * 0.012f,
                        Offset(w * (0.28f + i * 0.15f), h * (0.26f + (i % 2) * 0.13f)))
                }
            }

            RoyalV21WeatherIcon.TEMPERATURE, RoyalV21WeatherIcon.HUMIDITY -> {
                val at = if (icon == RoyalV21WeatherIcon.TEMPERATURE)
                    Offset(w * 0.57f, h * 0.66f) else Offset(center.x, h * 0.53f)
                drawCircle(ice.copy(alpha = 0.26f), u * (0.10f + phase * 0.035f),
                    at, style = Stroke(u * 0.012f))
            }
        }
    }
}
