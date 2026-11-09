package com.royalplus.launcher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private enum class WeatherVisual { SUNNY, CLOUDY, OVERCAST, RAINY, FOGGY, SNOWY, STORMY, UNKNOWN }

private fun weatherVisual(weather: WeatherUiState): WeatherVisual {
    val code = weather.weatherCode
    return when {
        code == 0 || code == 1 -> WeatherVisual.SUNNY
        code == 2 -> WeatherVisual.CLOUDY
        code == 3 -> WeatherVisual.OVERCAST
        code == 45 || code == 48 -> WeatherVisual.FOGGY
        code in 51..67 || code in 80..82 -> WeatherVisual.RAINY
        code in 71..77 || code in 85..86 -> WeatherVisual.SNOWY
        code in 95..99 -> WeatherVisual.STORMY
        weather.description.contains("مشمس") || weather.description.contains("صافية") -> WeatherVisual.SUNNY
        weather.description.contains("ضباب") -> WeatherVisual.FOGGY
        weather.description.contains("مطر") || weather.description.contains("أمطار") || weather.description.contains("رذاذ") -> WeatherVisual.RAINY
        weather.description.contains("ثلج") -> WeatherVisual.SNOWY
        weather.description.contains("غائم كليا") || weather.description.contains("غائم كلياً") ||
            weather.description.contains("غائم بالكامل") || weather.description.contains("overcast") -> WeatherVisual.OVERCAST
        weather.description.contains("غائم جزئيا") || weather.description.contains("غائم جزئياً") ||
            weather.description.contains("غائم") || weather.description.contains("غيوم") -> WeatherVisual.CLOUDY
        else -> WeatherVisual.UNKNOWN
    }
}

@Composable
internal fun WeatherGlassCard(
    weather: WeatherUiState,
    currentTime: String,
    prayerTimes: PrayerTimesData?,
    currentTimeMillis: Long,
    onRequestLocation: () -> Unit,
    onRefreshWeather: () -> Unit,
    onOpenWeather: () -> Unit,
    modifier: Modifier = Modifier,
    language: String = "ar",
    motionEnabled: Boolean = true
) {
    RoyalOwlAmbienceEffect(weather)
    RoyalWeatherAmbienceEffect(weather)
    val prayerZone = java.util.TimeZone.getTimeZone(
        weather.timeZoneId ?: java.util.TimeZone.getDefault().id
    )
    val prayerClock = java.util.Calendar.getInstance(prayerZone).apply {
        timeInMillis = currentTimeMillis
    }
    val currentPrayerMinute = prayerClock.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
        prayerClock.get(java.util.Calendar.MINUTE)
    val prayerEntries = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
        .mapNotNull { key ->
            prayerTimes?.times?.get(key)?.let { time ->
                val parts = time.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull()
                val minute = parts.getOrNull(1)?.toIntOrNull()
                if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
                    Triple(key, time, hour * 60 + minute)
                } else null
            }
        }
    val nextPrayer = prayerEntries.firstOrNull { it.third > currentPrayerMinute }
        ?: prayerEntries.firstOrNull { it.first == "Fajr" }
    val prayerCountdown = nextPrayer?.let { (_, time, _) ->
        val parts = time.split(":")
        val target = java.util.Calendar.getInstance(prayerZone).apply {
            timeInMillis = currentTimeMillis
            set(java.util.Calendar.HOUR_OF_DAY, parts[0].toInt())
            set(java.util.Calendar.MINUTE, parts[1].toInt())
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= currentTimeMillis) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val remaining = ((target.timeInMillis - currentTimeMillis) / 1000L).coerceAtLeast(0L)
        String.format(
            java.util.Locale.US,
            "%02d:%02d:%02d",
            remaining / 3600L,
            (remaining % 3600L) / 60L,
            remaining % 60L
        )
    }
    val visual = weatherVisual(weather)
    val localizedDescription = when (weather.weatherCode) {
        0 -> when (language) { "en" -> "Clear sky"; "tr" -> "Açık hava"; else -> "سماء صافية" }
        1 -> when (language) { "en" -> "Mainly clear"; "tr" -> "Az bulutlu"; else -> "صحو غالباً" }
        2 -> when (language) { "en" -> "Partly cloudy"; "tr" -> "Parçalı bulutlu"; else -> "غائم جزئياً" }
        3 -> when (language) { "en" -> "Overcast"; "tr" -> "Kapalı"; else -> "غائم كلياً" }
        45, 48 -> when (language) { "en" -> "Fog"; "tr" -> "Sis"; else -> "ضباب" }
        in 51..57 -> when (language) { "en" -> "Drizzle"; "tr" -> "Çisenti"; else -> "رذاذ" }
        in 61..67, in 80..82 -> when (language) { "en" -> "Rain"; "tr" -> "Yağmur"; else -> "مطر" }
        in 71..77, 85, 86 -> when (language) { "en" -> "Snow"; "tr" -> "Kar"; else -> "ثلج" }
        in 95..99 -> when (language) { "en" -> "Thunderstorm"; "tr" -> "Gök gürültülü fırtına"; else -> "عاصفة رعدية" }
        else -> weather.description
    }
    val hour = currentTime.substringBefore(":").toIntOrNull()
    val isNight = hour != null && (hour >= 19 || hour < 6)
    val imageRes = when (visual) {
        WeatherVisual.SUNNY -> if (isNight) R.drawable.weather_clear_night else if (hour != null && hour in 17..18) R.drawable.weather_sunset else R.drawable.weather_clear_day
        WeatherVisual.CLOUDY -> if (isNight) R.drawable.weather_partly_cloudy_night else R.drawable.weather_partly_cloudy_day
        WeatherVisual.OVERCAST -> if (isNight) R.drawable.weather_overcast_night else R.drawable.weather_overcast
        WeatherVisual.RAINY -> R.drawable.weather_rain
        WeatherVisual.STORMY -> R.drawable.weather_storm
        WeatherVisual.FOGGY -> R.drawable.weather_fog
        WeatherVisual.SNOWY -> R.drawable.weather_snow
        WeatherVisual.UNKNOWN -> if (isNight) R.drawable.weather_overcast_night else R.drawable.weather_overcast
    }

    val weatherFrame = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenWeather)
            .focusable()
            .clip(weatherFrame)
            .border(1.dp, Color(0xBFFFD76D), weatherFrame)
    ) {
        RoyalMotionImage(
            image = rememberRoyalBitmapPainter(imageRes, maxEdgePx = 1280, maxPixels = 2_097_152L),
            modifier = Modifier.matchParentSize(),
            motionEnabled = motionEnabled
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x33000610), Color(0x66000610), Color(0xF0060A12))
                    )
                )
        )
        RoyalWeatherAtmosphereOverlay(
            weatherCode = weather.weatherCode,
            motionEnabled = motionEnabled
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = weather.temperatureC?.let { "$it°C" } ?: if (weather.isLoading) "…" else "—°C",
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = when {
                    weather.isLoading -> when (language) { "en" -> "Updating weather"; "tr" -> "Hava durumu güncelleniyor"; else -> "جارٍ تحديث الطقس" }
                    weather.needsPermission -> when (language) { "en" -> "Location required for weather"; "tr" -> "Hava durumu için konum gerekli"; else -> "الموقع مطلوب لعرض الطقس" }
                    else -> localizedDescription
                },
                color = Color.White.copy(alpha = 0.96f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = weather.city,
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (nextPrayer != null) {
                Spacer(Modifier.height(5.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x553B321C))
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = when (language) {
                                "en" -> "Next prayer"
                                "tr" -> "Sonraki namaz"
                                else -> "الصلاة القادمة"
                            },
                            color = Color(0xFFFFE4A6),
                            fontSize = 8.sp,
                            maxLines = 1
                        )
                        Text(
                            text = PrayerTimesRepository.displayNames()[nextPrayer.first] ?: nextPrayer.first,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            nextPrayer.second,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            prayerCountdown ?: "",
                            color = Color(0xFFFFD76D),
                            fontSize = 8.sp,
                            maxLines = 1
                        )
                    }
                }
            } else if (!weather.isLoading) {
                Text(
                    text = when (language) {
                        "en" -> "Prayer times unavailable"
                        "tr" -> "Namaz vakitleri yok"
                        else -> "مواقيت الصلاة غير متاحة"
                    },
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 8.sp,
                    maxLines = 1
                )
            }
            if (weather.needsPermission || (!weather.isLoading && weather.temperatureC == null)) {
                Spacer(Modifier.height(3.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.13f))
                        .clickable(onClick = if (weather.needsPermission) onRequestLocation else onRefreshWeather)
                        .focusable()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (weather.needsPermission) Icons.Rounded.LocationOn else Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (weather.needsPermission) when (language) { "en" -> "Enable location"; "tr" -> "Konumu etkinleştir"; else -> "تفعيل الموقع" } else when (language) { "en" -> "Retry"; "tr" -> "Tekrar dene"; else -> "إعادة المحاولة" },
                        color = Color.White,
                        fontSize = 9.sp
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
