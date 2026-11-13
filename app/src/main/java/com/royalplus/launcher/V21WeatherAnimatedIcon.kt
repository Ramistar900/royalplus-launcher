package com.royalplus.launcher

import android.graphics.Color as AndroidColor
import android.view.View
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView

/** Render bundled V21 SMIL SVG artwork locally; JavaScript and remote network access are disabled. */
internal enum class RoyalV21WeatherIcon(val fileName: String, val label: String) {
    SUNNY("sunny.svg", "مشمس"),
    PARTLY_CLOUDY("partly_cloudy.svg", "غائم جزئياً"),
    CLOUDY("cloudy.svg", "غائم"),
    RAIN("rain.svg", "مطر"),
    DRIZZLE("drizzle.svg", "رذاذ"),
    HEAVY_RAIN("heavy_rain.svg", "أمطار غزيرة"),
    THUNDERSTORM("thunderstorm.svg", "عاصفة رعدية"),
    LIGHTNING("lightning.svg", "برق"),
    SNOW("snow.svg", "ثلج"),
    HEAVY_SNOW("heavy_snow.svg", "ثلوج كثيفة"),
    SLEET("sleet.svg", "مطر متجمد"),
    HAIL("hail.svg", "بَرَد"),
    WIND("wind.svg", "رياح"),
    STRONG_WIND("strong_wind.svg", "رياح شديدة"),
    FOG("fog.svg", "ضباب"),
    MIST("mist.svg", "شبورة خفيفة"),
    DUST("dust.svg", "غبار"),
    HOT("hot.svg", "حرارة مرتفعة"),
    COLD("cold.svg", "برودة"),
    SUNRISE("sunrise.svg", "شروق الشمس"),
    SUNSET("sunset.svg", "غروب الشمس"),
    CLEAR_NIGHT("clear_night.svg", "سماء صافية ليلاً"),
    TORNADO("tornado.svg", "إعصار"),
    TEMPERATURE("temperature.svg", "درجة الحرارة"),
    HUMIDITY("humidity.svg", "الرطوبة"),
    RAINBOW("rainbow.svg", "قوس قزح")
}

internal object RoyalV21WeatherIconResolver {
    fun resolve(
        weatherCode: Int?,
        description: String = "",
        apparentTemperatureC: Int? = null,
        windSpeedKmh: Int? = null,
        hour: Int? = null
    ): RoyalV21WeatherIcon {
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
internal fun RoyalV21WeatherAnimatedIcon(
    icon: RoyalV21WeatherIcon,
    modifier: Modifier = Modifier,
    motionEnabled: Boolean = true,
    contentDescription: String? = null
) {
    val label = contentDescription ?: icon.label
    AndroidView(
        modifier = modifier.semantics { this.contentDescription = label },
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isFocusable = false
                isFocusableInTouchMode = false
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
        },
        update = { webView ->
            if (webView.tag != icon.fileName) {
                webView.tag = icon.fileName
                val html = """
                    <!doctype html><html><head>
                    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
                    <style>html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:transparent}
                    img{display:block;width:100%;height:100%;object-fit:contain}</style>
                    </head><body><img src="${icon.fileName}" alt=""></body></html>
                """.trimIndent()
                webView.loadDataWithBaseURL(
                    "file:///android_asset/weather_v21/", html, "text/html", "UTF-8", null
                )
            }
            if (motionEnabled) webView.onResume() else webView.onPause()
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.onPause()
            webView.destroy()
        }
    )
}
