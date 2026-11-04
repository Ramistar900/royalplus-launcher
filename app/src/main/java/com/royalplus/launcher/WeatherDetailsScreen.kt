package com.royalplus.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun WeatherDetailsScreen(
    weather: WeatherUiState,
    prayerTimes: PrayerTimesData?,
    currentTimeMillis: Long,
    timeText: String,
    dateText: String,
    language: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRequestLocation: () -> Unit
) {
    BackHandler(onBack = onBack)
    val zone = remember(weather.timeZoneId) { TimeZone.getTimeZone(weather.timeZoneId ?: TimeZone.getDefault().id) }
    val hour = remember(currentTimeMillis, weather.timeZoneId) {
        SimpleDateFormat("H", Locale.US).apply { timeZone = zone }.format(Date(currentTimeMillis)).toIntOrNull() ?: 12
    }
    val night = hour >= 19 || hour < 6
    val backgroundRes = when (weather.weatherCode) {
        0, 1 -> if (night) R.drawable.weather_clear_night else if (hour in 17..18) R.drawable.weather_sunset else R.drawable.weather_clear_day
        2 -> if (night) R.drawable.weather_partly_cloudy_night else R.drawable.weather_partly_cloudy_day
        3 -> if (night) R.drawable.weather_overcast_night else R.drawable.weather_overcast
        45, 48 -> R.drawable.weather_fog
        in 51..67, in 80..82 -> R.drawable.weather_rain
        in 71..77, 85, 86 -> R.drawable.weather_snow
        in 95..99 -> R.drawable.weather_storm
        else -> if (night) R.drawable.weather_overcast_night else R.drawable.weather_overcast
    }
    val nextPrayer = remember(prayerTimes, currentTimeMillis, weather.timeZoneId) {
        val now = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = zone }.format(Date(currentTimeMillis))
        prayerTimes?.times?.entries?.firstOrNull { it.key != "Sunrise" && it.value > now }
            ?: prayerTimes?.times?.entries?.firstOrNull { it.key == "Fajr" }
    }
    val countdown = remember(nextPrayer, currentTimeMillis, weather.timeZoneId) {
        if (nextPrayer == null) "--:--:--" else {
            val target = java.util.Calendar.getInstance(zone).apply {
                timeInMillis = currentTimeMillis
                val parts = nextPrayer.value.split(":")
                set(java.util.Calendar.HOUR_OF_DAY, parts.getOrNull(0)?.toIntOrNull() ?: 0)
                set(java.util.Calendar.MINUTE, parts.getOrNull(1)?.toIntOrNull() ?: 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                if (timeInMillis <= currentTimeMillis) add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            val seconds = ((target.timeInMillis - currentTimeMillis).coerceAtLeast(0L) / 1000L)
            String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
        }
    }
    val prayerNames = mapOf("Fajr" to "الفجر", "Sunrise" to "الشروق", "Dhuhr" to "الظهر", "Asr" to "العصر", "Maghrib" to "المغرب", "Isha" to "العشاء")
    val prayerNamesEn = mapOf("Fajr" to "Fajr", "Sunrise" to "Sunrise", "Dhuhr" to "Dhuhr", "Asr" to "Asr", "Maghrib" to "Maghrib", "Isha" to "Isha")
    val prayerNamesTr = mapOf("Fajr" to "Sabah", "Sunrise" to "Güneş", "Dhuhr" to "Öğle", "Asr" to "İkindi", "Maghrib" to "Akşam", "Isha" to "Yatsı")
    val activeKey = nextPrayer?.key
    val glass = Color(0x557DA9CC)
    val outline = Color(0x99E7F4FF)
    val gold = Color(0xFFFFD36A)

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF52799B))) {
        Image(painter = painterResource(backgroundRes), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x2200152B), Color(0x3A17334C), Color(0x6D193B59)))))
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
            Row(Modifier.fillMaxWidth().weight(0.30f), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(weather.city.ifBlank { "الموقع الحالي" }, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Rounded.LocationOn, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(weatherSymbol(weather.weatherCode), fontSize = 48.sp)
                        Column {
                            Text(weather.description, color = Color.White, fontSize = 18.sp, maxLines = 1)
                            Text((weather.temperatureC?.toString() ?: "--") + "°C", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.13f)).clickable { if (weather.needsPermission) onRequestLocation() else onRefresh() }.focusable().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Refresh, null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Text(if (language == "en") "Refresh" else if (language == "tr") "Yenile" else "تحديث الطقس", color = Color.White, fontSize = 11.sp)
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                    Text(timeText, color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                    Text(prayerTimes?.gregorianDate ?: dateText, color = Color.White, fontSize = 16.sp, maxLines = 1)
                    Text(prayerTimes?.hijriDate ?: "", color = Color.White, fontSize = 15.sp, maxLines = 1)
                    if (nextPrayer != null) Text(
                        (if (language == "en") "Next " + prayerNamesEn[nextPrayer.key] + " in " else if (language == "tr") "Sonraki " + prayerNamesTr[nextPrayer.key] + ": " else "الصلاة القادمة " + prayerNames[nextPrayer.key] + " بعد ") + countdown,
                        color = gold, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1
                    )
                }
            }
            Row(Modifier.fillMaxWidth().weight(0.70f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.weight(0.36f).fillMaxHeight().clip(RoundedCornerShape(25.dp)).background(glass).border(1.5.dp, outline, RoundedCornerShape(25.dp)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text(if (language == "en") "Prayer times" else if (language == "tr") "Namaz vakitleri" else "مواقيت الصلاة", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.Mosque, null, tint = Color.White, modifier = Modifier.size(23.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 7.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(weather.city, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(5.dp))
                        Icon(Icons.Rounded.LocationOn, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha").forEach { key ->
                        val isActive = key == activeKey
                        Row(Modifier.fillMaxWidth().weight(1f).padding(vertical = 2.dp).clip(RoundedCornerShape(15.dp))
                            .background(if (isActive) Color(0x667C6530) else Color.White.copy(alpha = 0.12f))
                            .border(if (isActive) 2.dp else 1.dp, if (isActive) gold else Color.White.copy(alpha = 0.28f), RoundedCornerShape(15.dp))
                            .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(prayerTimes?.times?.get(key) ?: "--:--", color = if (isActive) gold else Color.White, fontSize = 18.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium)
                            Spacer(Modifier.weight(1f))
                            Text(if (language == "en") prayerNamesEn[key].orEmpty() else if (language == "tr") prayerNamesTr[key].orEmpty() else prayerNames[key].orEmpty(), color = if (isActive) gold else Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Column(Modifier.weight(0.64f).fillMaxHeight()) {
                    Box(Modifier.fillMaxWidth().weight(0.30f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("الَّذِينَ آمَنُوا وَتَطْمَئِنُّ قُلُوبُهُم بِذِكْرِ اللَّهِ ۗ أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ", color = Color.White, fontSize = 21.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text("سورة الرعد – الآية ٢٨", color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp)
                        }
                    }
                    Column(Modifier.fillMaxWidth().weight(0.70f).clip(RoundedCornerShape(25.dp)).background(glass).border(1.5.dp, outline, RoundedCornerShape(25.dp)).padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                            Text(if (language == "en") "5-day forecast" else if (language == "tr") "5 günlük hava durumu" else "حالة الطقس - 5 أيام", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text(weatherSymbol(weather.weatherCode), fontSize = 20.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            if (weather.forecast.isNotEmpty()) {
                                weather.forecast.take(5).asReversed().forEach { day ->
                                    val dayLabel = remember(day.date, language) {
                                        try {
                                            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day.date)
                                            SimpleDateFormat("EEEE", if (language == "en") Locale.ENGLISH else if (language == "tr") Locale("tr") else Locale("ar")).format(parsed ?: Date())
                                        } catch (_: Exception) { day.date }
                                    }
                                    Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(17.dp)).background(Color.White.copy(alpha = 0.12f)).border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(17.dp)).padding(horizontal = 5.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                                        Text(dayLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(weatherSymbol(day.weatherCode), fontSize = 29.sp)
                                        Text(weatherDescriptionFor(day.weatherCode, language), color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(day.maxTemperatureC.toString() + "° / " + day.minTemperatureC + "°", color = Color.White, fontSize = 13.sp, maxLines = 1)
                                    }
                                }
                            } else {
                                repeat(5) {
                                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(17.dp)).background(Color.White.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                                        Text(if (weather.isLoading) "…" else "—", color = Color.White, fontSize = 20.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.18f)).clickable(onClick = onBack).focusable().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ArrowBack, null, tint = Color.White, modifier = Modifier.size(17.dp))
            Text(if (language == "en") "Back" else if (language == "tr") "Geri" else "رجوع", color = Color.White, fontSize = 11.sp)
        }
    }
}

private fun weatherSymbol(code: Int?): String = when (code) {
    0 -> "☀️"
    1, 2 -> "🌤️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    in 51..67, in 80..82 -> "🌧️"
    in 71..77, 85, 86 -> "❄️"
    in 95..99 -> "⛈️"
    else -> "🌤️"
}

private fun weatherDescriptionFor(code: Int, language: String): String = when (code) {
    0 -> if (language == "en") "Clear" else if (language == "tr") "Açık" else "صحو"
    1, 2 -> if (language == "en") "Cloudy" else if (language == "tr") "Bulutlu" else "غائم جزئياً"
    3 -> if (language == "en") "Overcast" else if (language == "tr") "Kapalı" else "غائم"
    45, 48 -> if (language == "en") "Fog" else if (language == "tr") "Sis" else "ضباب"
    in 51..67, in 80..82 -> if (language == "en") "Rain" else if (language == "tr") "Yağmur" else "مطر"
    in 71..77, 85, 86 -> if (language == "en") "Snow" else if (language == "tr") "Kar" else "ثلج"
    in 95..99 -> if (language == "en") "Storm" else if (language == "tr") "Fırtına" else "عاصفة"
    else -> if (language == "en") "Weather" else if (language == "tr") "Hava" else "الطقس"
}
