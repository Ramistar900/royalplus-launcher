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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
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

private data class PrayerCity(val name: String, val latitude: Double, val longitude: Double, val timeZoneId: String)

private val prayerCities = listOf(
    PrayerCity(if (language == "en") "Current location" else if (language == "tr") "Mevcut konum" else "الموقع الحالي", 0.0, 0.0, TimeZone.getDefault().id),
    PrayerCity(if (language == "en") "Istanbul" else if (language == "tr") "İstanbul" else "إسطنبول", 41.0082, 28.9784, "Europe/Istanbul"),
    PrayerCity(if (language == "en") "Damascus" else if (language == "tr") "Şam" else "دمشق", 33.5138, 36.2765, "Asia/Damascus"),
    PrayerCity(if (language == "en") "Mecca" else if (language == "tr") "Mekke" else "مكة المكرمة", 21.3891, 39.8579, "Asia/Riyadh"),
    PrayerCity(if (language == "en") "Riyadh" else if (language == "tr") "Riyad" else "الرياض", 24.7136, 46.6753, "Asia/Riyadh"),
    PrayerCity(if (language == "en") "Baghdad" else if (language == "tr") "Bağdat" else "بغداد", 33.3152, 44.3661, "Asia/Baghdad")
)

@Composable
internal fun MuslimScreen(
    weather: WeatherUiState,
    onRequestLocation: () -> Unit,
    language: String = "ar"
) {
    var selectedCityIndex by remember {
        mutableStateOf(
            when (TimeZone.getDefault().id) {
                "Europe/Istanbul" -> 1
                "Asia/Damascus" -> 2
                "Asia/Riyadh", "Asia/Aden", "Asia/Kuwait", "Asia/Qatar", "Asia/Bahrain" -> 3
                "Asia/Baghdad" -> 5
                else -> 1
            }
        )
    }
    val context = LocalContext.current
    val hijriPreferences = remember(context) {
        context.getSharedPreferences("royalplus_muslim_preferences", android.content.Context.MODE_PRIVATE)
    }
    var data by remember { mutableStateOf<PrayerTimesData?>(null) }
    val weekSchedulesState = remember { mutableStateOf<List<PrayerTimesData>>(emptyList()) }
    val weekSchedules = weekSchedulesState.value
    var hijriDayAdjustment by remember {
        mutableStateOf(hijriPreferences.getInt("hijri_day_adjustment", 0).coerceIn(-30, 30))
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val selectedCity = prayerCities[selectedCityIndex]
    val hasCoordinates = selectedCityIndex == 0 && weather.latitude != null && weather.longitude != null
    val latitude = if (selectedCityIndex == 0) weather.latitude else selectedCity.latitude.takeIf { it != 0.0 }
    val longitude = if (selectedCityIndex == 0) weather.longitude else selectedCity.longitude.takeIf { it != 0.0 }
    val cityName = if (selectedCityIndex == 0) weather.city else selectedCity.name
    val zoneForKey = if (selectedCityIndex == 0) weather.timeZoneId ?: TimeZone.getDefault().id else selectedCity.timeZoneId
    val todayKey = remember(now, zoneForKey) {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone(zoneForKey) }.format(Date(now))
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    LaunchedEffect(hijriDayAdjustment) {
        hijriPreferences.edit().putInt("hijri_day_adjustment", hijriDayAdjustment).apply()
    }

    LaunchedEffect(latitude, longitude, cityName, hijriDayAdjustment, todayKey) {
        data = null
        weekSchedulesState.value = emptyList()
        if (latitude != null && longitude != null) {
            val zoneId = if (selectedCityIndex == 0) weather.timeZoneId else selectedCity.timeZoneId
            PrayerTimesRepository.fetch(
                latitude, longitude, cityName,
                hijriDayOffset = hijriDayAdjustment, timeZoneId = zoneId
            ) { data = it }
            (0..6).forEach { dayOffset ->
                PrayerTimesRepository.fetch(
                    latitude, longitude, cityName,
                    hijriDayOffset = hijriDayAdjustment,
                    timeZoneId = zoneId,
                    calendarDayOffset = dayOffset
                ) { dayData ->
                    weekSchedulesState.value = weekSchedulesState.value.filterNot {
                        it.gregorianDate == dayData.gregorianDate
                    } + dayData
                }
            }
        }
    }

    LaunchedEffect(weather.latitude, weather.longitude) {
        if (weather.latitude != null && weather.longitude != null) {
            selectedCityIndex = 0
        }
    }

    val prayerTimeZone = remember(weather.timeZoneId, selectedCityIndex) {
        TimeZone.getTimeZone(
            if (selectedCityIndex == 0) {
                weather.timeZoneId ?: TimeZone.getDefault().id
            } else {
                selectedCity.timeZoneId
            }
        )
    }
    val nextPrayer = remember(data, now, prayerTimeZone) {
        val current = SimpleDateFormat("HH:mm", Locale.US).apply { this.timeZone = prayerTimeZone }.format(Date(now))
        val candidates = data?.times.orEmpty().filterKeys { it != "Sunrise" }.mapNotNull { (key, value) ->
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
        modifier = Modifier.fillMaxSize()
            .background(Color(0xFFFFF3D8))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val ramadanDecorated = data?.hijriDate?.contains("رمضان") == true
                Text(
                    if (ramadanDecorated) "☾ ✦ " + when (language) { "en" -> "Muslim"; "tr" -> "Müslüman"; else -> "مسلم" } + " ✦ ☾" else when (language) { "en" -> "Muslim"; "tr" -> "Müslüman"; else -> "مسلم" },
                    color = Color.Black,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (ramadanDecorated) when (language) { "en" -> "Ramadan Kareem • Hijri calendar & prayer times"; "tr" -> "Ramazanınız mübarek olsun • Hicri takvim ve namaz vakitleri"; else -> "رمضان كريم • التقويم الهجري ومواقيت الصلاة" } else when (language) { "en" -> "Hijri calendar & prayer times"; "tr" -> "Hicri takvim ve namaz vakitleri"; else -> "التقويم الهجري ومواقيت الصلاة" },
                    color = Color(0xFF333333),
                    fontSize = 12.sp
                )
            }
            SmallPrayerAction(when (language) { "en" -> "Refresh"; "tr" -> "Yenile"; else -> "تحديث" }, Icons.Rounded.Refresh) {
                if (latitude != null && longitude != null) {
                    PrayerTimesRepository.fetch(latitude, longitude, cityName, hijriDayOffset = hijriDayAdjustment, timeZoneId = if (selectedCityIndex == 0) weather.timeZoneId else selectedCity.timeZoneId) { data = it }
                } else onRequestLocation()
            }
            Spacer(Modifier.width(8.dp))
            SmallPrayerAction(when (language) { "en" -> "My location"; "tr" -> "Konumum"; else -> "موقعي" }, Icons.Rounded.LocationOn, onRequestLocation)
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                .background(Color(0xFF8C641A))
                .border(1.dp, Color(0xFF20C9B5), RoundedCornerShape(17.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(when (language) { "en" -> "Hijri date"; "tr" -> "Hicri tarih"; else -> "التاريخ الهجري" }, color = Color.Black, fontSize = 12.sp)
                Text(data?.hijriDate?.takeIf { it.isNotBlank() } ?: when (language) { "en" -> "Loading Hijri date"; "tr" -> "Hicri tarih yükleniyor"; else -> "جارٍ تحميل التاريخ الهجري" }, color = Color.Black, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(data?.gregorianDate.orEmpty(), color = RoyalColors.Muted, fontSize = 11.sp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFFFFE7A8))
                            .border(1.dp, Color(0xFF20C9B5), RoundedCornerShape(7.dp))
                            .clickable { hijriDayAdjustment = (hijriDayAdjustment - 1).coerceAtLeast(-30) }
                            .focusable()
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(if (language == "en") "− day" else if (language == "tr") "− gün" else "− يوم", color = Color.Black, fontSize = 11.sp) }
                    Text(
                        if (hijriDayAdjustment == 0) when (language) { "en" -> "Offset: 0"; "tr" -> "Düzeltme: 0"; else -> "ضبط: 0" } else "${when (language) { "en" -> "Offset"; "tr" -> "Düzeltme"; else -> "ضبط" }}: ${if (hijriDayAdjustment > 0) "+" else ""}$hijriDayAdjustment",
                        color = RoyalColors.Muted,
                        fontSize = 10.sp
                    )
                    Box(
                        Modifier.clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFF24202A))
                            .border(1.dp, RoyalColors.Gold.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
                            .clickable { hijriDayAdjustment = (hijriDayAdjustment + 1).coerceAtMost(30) }
                            .focusable()
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(if (language == "en") "+ day" else if (language == "tr") "+ gün" else "+ يوم", color = Color.Black, fontSize = 11.sp) }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(cityName, color = RoyalColors.Pearl, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(when (language) { "en" -> "Times for selected city"; "tr" -> "Seçilen şehrin vakitleri"; else -> "حساب المواقيت حسب المدينة المختارة" }, color = RoyalColors.Muted, fontSize = 9.sp)
            }
        }

        if (data?.error != null) {
            Text(data!!.error!!, color = Color(0xFFFFB5A9), fontSize = 12.sp)
        }

        if (nextPrayer != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF111111), Color(0xFF174D43), Color(0xFF48204F), Color(0xFF111111))))
                    .border(1.dp, Color(0xFF20C9B5), RoundedCornerShape(17.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(when (language) { "en" -> "Next prayer"; "tr" -> "Sonraki namaz"; else -> "الصلاة القادمة" }, color = Color.White, fontSize = 11.sp)
                    Text(PrayerTimesRepository.displayNames()[nextPrayer.first] ?: nextPrayer.first, color = RoyalColors.Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(nextPrayer.second, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF101010))
                            .border(1.dp, Color(0xFF20C9B5), RoundedCornerShape(8.dp))
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(when (language) { "en" -> "In "; "tr" -> "Kalan "; else -> "بعد " }, color = Color.White, fontSize = 10.sp)
                        Text(countdown.ifBlank { "00:00:00" }, color = Color(0xFF20C9B5), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (data?.hijriDate?.contains("رمضان") == true) {
            val fajrMinutes = timesafeMinutes(data?.times?.get("Fajr"))
            val maghribMinutes = timesafeMinutes(data?.times?.get("Maghrib"))
            val imsakTime = fajrMinutes?.let { minutes ->
                val adjusted = (minutes - 10 + 1440) % 1440
                String.format(Locale.US, "%02d:%02d", adjusted / 60, adjusted % 60)
            }
            val iftarTime = data?.times?.get("Maghrib")
            val iftarCountdown = maghribMinutes?.let { target ->
                val current = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = prayerTimeZone }.format(Date(now)).split(":")
                val currentSeconds = current[0].toInt() * 3600 + current[1].toInt() * 60 + current[2].toInt()
                var remaining = target * 60 - currentSeconds
                if (remaining <= 0) remaining += 24 * 3600
                String.format(Locale.US, "%02d:%02d:%02d", remaining / 3600, (remaining % 3600) / 60, remaining % 60)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                        .background(Color(0xFF14271F))
                        .border(1.dp, Color(0xFF6DAA80), RoundedCornerShape(15.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(when (language) { "en" -> "🌙 Suhoor cutoff"; "tr" -> "🌙 İmsak"; else -> "🌙 الإمساك" }, color = Color(0xFFD8E9B8), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(imsakTime ?: "--:--", color = RoyalColors.Pearl, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(when (language) { "en" -> "10 minutes before Fajr, as a precaution"; "tr" -> "Tedbir amaçlı imsak, fecirden 10 dakika önce"; else -> "قبل الفجر بـ10 دقائق احتياطًا" }, color = RoyalColors.Muted, fontSize = 9.sp, textAlign = TextAlign.Center)
                }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                        .background(Color(0xFF30240F))
                        .border(1.dp, RoyalColors.Gold, RoundedCornerShape(15.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(when (language) { "en" -> "✨ Iftar"; "tr" -> "✨ İftar"; else -> "✨ الإفطار" }, color = RoyalColors.Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(iftarTime ?: "--:--", color = RoyalColors.Pearl, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("${when (language) { "en" -> "Remaining"; "tr" -> "Kalan"; else -> "المتبقي" }} ${iftarCountdown ?: "--:--:--"}", color = RoyalColors.Gold, fontSize = 10.sp, textAlign = TextAlign.Center)
                }
            }
        }

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF8C641A)).border(1.dp, Color(0xFF20C9B5), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("${when (language) { "en" -> "Today’s Hijri date"; "tr" -> "Bugünün Hicri tarihi"; else -> "التاريخ الهجري اليوم" }}: ${data?.hijriDate?.takeIf { it.isNotBlank() } ?: when (language) { "en" -> "Loading Hijri date"; "tr" -> "Hicri tarih yükleniyor"; else -> "جارٍ تحميل التاريخ الهجري" }}", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(when (language) { "en" -> "Prayer times for the next seven days"; "tr" -> "Önümüzdeki yedi günün namaz vakitleri"; else -> "جدول مواقيت الصلاة للأيام السبعة" }, color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(when (language) { "en" -> "Updates daily and calculates times for the selected city"; "tr" -> "Her gün otomatik yenilenir ve seçilen şehre göre hesaplanır"; else -> "يتجدد تلقائياً يومياً عند تغيّر التاريخ، وتُحسب المواقيت حسب المدينة المختارة" }, color = RoyalColors.Muted, fontSize = 10.sp)
        val prayerColumns = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFFB68A2C), RoundedCornerShape(12.dp))
                .background(Color(0xFFFFF3D8)).padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(Modifier.fillMaxWidth().background(Color(0xFFFFE7A8), RoundedCornerShape(7.dp)).padding(vertical = 7.dp, horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                PrayerTableCell(when (language) { "en" -> "#"; "tr" -> "No."; else -> "الفهرس" }, Modifier.weight(0.55f), true)
                PrayerTableCell(when (language) { "en" -> "Day & date"; "tr" -> "Gün ve tarih"; else -> "اليوم والتاريخ" }, Modifier.weight(1.55f), true)
                prayerColumns.forEach { key ->
                    PrayerTableCell(PrayerTimesRepository.displayNames()[key] ?: key, Modifier.weight(1f), true)
                }
            }
            if (weekSchedules.isEmpty()) {
                Text(when (language) { "en" -> "Calculating prayer schedule…"; "tr" -> "Namaz vakitleri hesaplanıyor…"; else -> "جارٍ حساب جدول المواقيت…" }, color = RoyalColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            } else {
                weekSchedules.forEachIndexed { index, schedule ->
                    val isToday = schedule.gregorianDate == data?.gregorianDate
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (isToday) RoyalColors.Gold.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(5.dp))
                            .padding(vertical = 6.dp, horizontal = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PrayerTableCell((index + 1).toString(), Modifier.weight(0.55f), isToday)
                        PrayerTableCell(schedule.dayName, Modifier.weight(1.55f), isToday)
                        prayerColumns.forEach { key ->
                            val timeValue = schedule.times[key] ?: "--:--"
                            val expired = isToday && key != "Sunrise" && (timesafeMinutes(timeValue)?.let { it <= currentMinutesForZone(now, prayerTimeZone) } == true)
                            PrayerTableCell(timeValue, Modifier.weight(1f), isToday, expired)
                        }
                    }
                    if (index < weekSchedules.lastIndex) {
                        Spacer(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFB68A2C)))
                    }
                }
            }
        }

        if (selectedCityIndex != 0) {
            Text("${when (language) { "en" -> "Selected city"; "tr" -> "Seçilen şehir"; else -> "المدينة المختارة" }}: $cityName", color = RoyalColors.Muted, fontSize = 10.sp)
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
            Text(when (language) { "en" -> "Could not detect your location. Choose a city below to view prayer times and the Hijri date; the selected city appears above."; "tr" -> "Konumunuz belirlenemedi. Namaz vakitlerini ve Hicri tarihi görmek için aşağıdan bir şehir seçin; seçilen şehir yukarıda gösterilir."; else -> "تعذّر تحديد موقعك؛ اختر مدينتك من الأسفل لعرض مواقيت الصلاة والتاريخ الهجري. المدينة المحددة ظاهرة أعلى البطاقة." }, color = RoyalColors.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PrayerTableCell(value: String, modifier: Modifier, highlighted: Boolean, expired: Boolean = false) {
    Box(modifier = modifier.padding(horizontal = 1.dp).clip(RoundedCornerShape(4.dp)).then(if (expired) Modifier.background(Color(0xFFD62828)) else Modifier).padding(vertical = if (expired) 4.dp else 2.dp, horizontal = 1.dp), contentAlignment = Alignment.Center) {
        Text(text = value, color = if (expired) Color.White else Color.Black, fontSize = 9.sp, fontWeight = if (highlighted || expired) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center, lineHeight = 11.sp, maxLines = 2)
    }
}

private fun currentMinutesForZone(now: Long, zone: TimeZone): Int {
    val current = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = zone }.format(Date(now)).split(":")
    return current[0].toInt() * 60 + current[1].toInt()
}

private fun timesafeMinutes(value: String?): Int? {
    val parts = value?.split(":") ?: return null
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

@Composable
private fun SmallPrayerAction(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(11.dp))
            .background(Color(0xFFFFE7A8))
            .border(1.dp, Color(0xFFB68A2C), RoundedCornerShape(11.dp))
            .clickable(onClick = onClick).focusable()
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(title, color = Color.Black, fontSize = 11.sp)
    }
}
