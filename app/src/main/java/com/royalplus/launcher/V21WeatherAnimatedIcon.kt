package com.royalplus.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
internal fun RoyalV21WeatherAnimatedIcon(
    icon: RoyalV21WeatherIcon,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") motionEnabled: Boolean = false,
    contentDescription: String? = null
) {
    val context = LocalContext.current.applicationContext
    var image by remember(icon.fileName) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(context, icon.fileName) {
        image = withContext(Dispatchers.IO) {
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
    }
}
