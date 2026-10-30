package com.royalplus.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private data class PrayerCity(val name: String, val latitude: Double, val longitude: Double)

private val prayerCities = listOf(
    PrayerCity("الموقع الحالي", 0.0, 0.0),
    PrayerCity("إسطنبول", 41.0082, 28.9784),
    PrayerCity("دمشق", 33.5138, 36.2765),
    PrayerCity("مكة المكرمة", 21.3891, 39.8579),
    PrayerCity("الرياض", 24.7136, 46.6753),
    PrayerCity("بغداد", 33.3152, 44.3661)
)

@Composable
internal fun MuslimScreen(
    weather: WeatherUiState,
    onRequestLocation: () -> Unit
) {
    var selectedCityIndex by remember { mutableStateOf(0) }
    var data by remember { mutableStateOf<PrayerTimesData?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val selectedCity = prayerCities[selectedCityIndex]
    val hasCoordinates = selectedCityIndex == 0 && weather.latitude != null && weather.longitude != null
    val latitude = if (selectedCityIndex == 0) weather.latitude else selectedCity.latitude.takeIf { it != 0.0 }
    val longitude = if (selectedCityIndex == 0) weather.longitude else selectedCity.longitude.takeIf { it != 0.0 }
    val cityName = if (selectedCityIndex == 0) weather.city else selectedCity.name

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    LaunchedEffect(latitude, longitude, cityName) {
        data = null
        if (latitude != null && longitude != null) {
            PrayerTimesRepository.fetch(latitude, longitude, cityName) { data = it }
        }
    }

    val prayerTimeZone = remember(weather.timeZoneId) {
        TimeZone.getTimeZone(weather.timeZoneId ?: TimeZone.getDefault().id)
    }
    val nextPrayer = remember(data, now, prayerTimeZone) {
        val current = SimpleDateFormat("HH:mm", Locale.US).apply { this.timeZone = prayerTimeZone }.format(Date(now))
        val candidates = data?.times.orEmpty().mapNotNull { (key, value) ->
            val parts = value.split(":")
            val mins = parts.getOrNull(0)?.toIntOrNull()?.times(60)?.plus(parts.getOrNull(1)?.toIntOrNull() ?: 0)
            if (mins != null && mins >= 0) Triple(key, value, mins) else null
        }.sortedBy { it.third }
        val currentParts = current.split(":")
        val currentMinutes = currentParts[0].toInt() * 60 + currentParts[1].toInt()
        candidates.firstOrNull { it.third > currentMinutes } ?: candidates.firstOrNull()
    }
    val countdown = remember(nextPrayer, now, prayerTimeZone) {
        if (nextPrayer == null) "" else {
            val current = SimpleDateFormat("HH:mm:ss", Locale.US).apply { this.timeZone = prayerTimeZone }.format(Date(now)).split(":")
            val currentSeconds = current[0].toInt() * 3600 + current[1].toInt() * 60 + current[2].toInt()
            val parts = nextPrayer.second.split(":")
            var remaining = parts[0].toInt() * 3600 + parts[1].toInt() * 60 - currentSeconds
            if (remaining <= 0) remaining += 24 * 3600
            val hours = remaining / 3600
            val minutes = (remaining % 3600) / 60
            val seconds = remaining % 60
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("مسلم", color = RoyalColors.Gold, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text("التقويم الهجري ومواقيت الصلاة", color = RoyalColors.Pearl.copy(alpha = 0.82f), fontSize = 12.sp)
            }
            SmallPrayerAction("تحديث", Icons.Rounded.Refresh) {
                if (latitude != null && longitude != null) {
                    PrayerTimesRepository.fetch(latitude, longitude, cityName) { data = it }
                } else onRequestLocation()
            }
            Spacer(Modifier.width(8.dp))
            SmallPrayerAction("موقعي", Icons.Rounded.LocationOn, onRequestLocation)
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                .background(Color(0xD912151D))
                .border(1.dp, RoyalColors.Gold.copy(alpha = 0.5f), RoundedCornerShape(17.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("التاريخ الهجري", color = RoyalColors.Gold, fontSize = 12.sp)
                Text(data?.hijriDate?.takeIf { it.isNotBlank() } ?: "جارٍ تحميل التاريخ الهجري", color = RoyalColors.Pearl, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(data?.gregorianDate.orEmpty(), color = RoyalColors.Muted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(cityName, color = RoyalColors.Pearl, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("طريقة الحساب: أم القرى/افتراضي MWL", color = RoyalColors.Muted, fontSize = 9.sp)
            }
        }

        if (data?.error != null) {
            Text(data!!.error!!, color = Color(0xFFFFB5A9), fontSize = 12.sp)
        }

        if (nextPrayer != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                    .background(Color(0xFF30240F))
                    .border(1.dp, RoyalColors.Gold, RoundedCornerShape(17.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("الصلاة القادمة", color = RoyalColors.Pearl.copy(alpha = 0.85f), fontSize = 11.sp)
                    Text(PrayerTimesRepository.displayNames()[nextPrayer.first] ?: nextPrayer.first, color = RoyalColors.Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(nextPrayer.second, color = RoyalColors.Pearl, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("المتبقي $countdown", color = RoyalColors.Gold, fontSize = 11.sp)
                }
            }
        }

        Text("مواقيت اليوم", color = RoyalColors.Pearl, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        val times = data?.times.orEmpty()
        val order = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
        Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            order.chunked(3).forEach { group ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.forEach { key ->
                        val selected = nextPrayer?.first == key
                        Column(
                            Modifier.weight(1f).height(77.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) Color(0xFF30240F) else Color(0xD910131A))
                                .border(1.dp, if (selected) RoyalColors.Gold else RoyalColors.Gold.copy(alpha = 0.23f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 8.dp, vertical = 9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(PrayerTimesRepository.displayNames()[key] ?: key, color = if (selected) RoyalColors.Gold else RoyalColors.Pearl, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(5.dp))
                            Text(times[key] ?: "--:--", color = RoyalColors.Pearl, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (selectedCityIndex != 0) {
            Text("المدينة المختارة: $cityName", color = RoyalColors.Muted, fontSize = 10.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            prayerCities.drop(1).forEachIndexed { index, city ->
                val chosen = selectedCityIndex == index + 1
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (chosen) RoyalColors.Gold.copy(alpha = 0.20f) else Color(0xAA11151D))
                        .border(1.dp, if (chosen) RoyalColors.Gold else RoyalColors.Gold.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .clickable { selectedCityIndex = index + 1 }
                        .onFocusChanged { }
                        .focusable()
                        .padding(vertical = 8.dp, horizontal = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(city.name, color = if (chosen) RoyalColors.Gold else RoyalColors.Pearl, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
        if (!hasCoordinates && selectedCityIndex == 0) {
            Text("لإظهار مواقيت موقعك، فعّل صلاحية الموقع؛ أو اختر مدينة من الأسفل.", color = RoyalColors.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SmallPrayerAction(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(11.dp))
            .background(Color(0xFF181B24))
            .border(1.dp, RoyalColors.Gold.copy(alpha = 0.45f), RoundedCornerShape(11.dp))
            .clickable(onClick = onClick).focusable()
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(title, color = RoyalColors.Pearl, fontSize = 11.sp)
    }
}
