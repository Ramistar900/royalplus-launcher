package com.royalplus.launcher

import com.royal.adaptive.LocalRoyalAdaptiveProfile

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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
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

private data class PrayerCity(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
    val englishName: String = name,
    val turkishName: String = name
)

private val prayerCities = listOf(
    PrayerCity("الموقع الحالي", 0.0, 0.0, TimeZone.getDefault().id, "Current location", "Mevcut konum"),
    PrayerCity("إسطنبول", 41.0082, 28.9784, "Europe/Istanbul", "Istanbul", "İstanbul"),
    PrayerCity("دمشق", 33.5138, 36.2765, "Asia/Damascus", "Damascus", "Şam"),
    PrayerCity("مكة المكرمة", 21.3891, 39.8579, "Asia/Riyadh", "Mecca", "Mekke"),
    PrayerCity("الرياض", 24.7136, 46.6753, "Asia/Riyadh", "Riyadh", "Riyad"),
    PrayerCity("بغداد", 33.3152, 44.3661, "Asia/Baghdad", "Baghdad", "Bağdat"),
    PrayerCity("أنقرة", 39.9334, 32.8597, "Europe/Istanbul", "Ankara", "Ankara"),
    PrayerCity("إزمير", 38.4237, 27.1428, "Europe/Istanbul", "Izmir", "İzmir"),
    PrayerCity("غازي عنتاب", 37.0662, 37.3833, "Europe/Istanbul", "Gaziantep", "Gaziantep"),
    PrayerCity("حلب", 36.2021, 37.1343, "Asia/Damascus", "Aleppo", "Halep"),
    PrayerCity("جدة", 21.4858, 39.1925, "Asia/Riyadh", "Jeddah", "Cidde"),
    PrayerCity("المدينة المنورة", 24.5247, 39.5692, "Asia/Riyadh", "Medina", "Medine"),
    PrayerCity("أربيل", 36.1911, 44.0092, "Asia/Baghdad", "Erbil", "Erbil"),
    PrayerCity("القاهرة", 30.0444, 31.2357, "Africa/Cairo", "Cairo", "Kahire")
)

private fun localizedPrayerCityName(index: Int, language: String): String {
    val city = prayerCities.getOrNull(index) ?: return ""
    return when (language) {
        "en" -> city.englishName
        "tr" -> city.turkishName
        else -> city.name
    }
}

@Composable
internal fun MuslimScreen(
    weather: WeatherUiState,
    onRequestLocation: () -> Unit,
    language: String = "ar"
) {
    val adaptiveProfile = LocalRoyalAdaptiveProfile.current
    val context = LocalContext.current
    var selectedSection by remember { mutableStateOf("home") }
    var showCityPicker by remember { mutableStateOf(false) }
    val hijriPreferences = remember(context) {
        context.getSharedPreferences("royalplus_muslim_preferences", android.content.Context.MODE_PRIVATE)
    }
    val defaultCityIndex = remember {
        when (TimeZone.getDefault().id) {
            "Europe/Istanbul" -> 1
            "Asia/Damascus" -> 2
            "Asia/Riyadh", "Asia/Aden", "Asia/Kuwait", "Asia/Qatar", "Asia/Bahrain" -> 4
            "Asia/Baghdad" -> 5
            else -> 1
        }
    }
    var selectedCityIndex by remember(context) {
        mutableStateOf(
            hijriPreferences.getInt("selected_prayer_city_index", defaultCityIndex)
                .coerceIn(0, prayerCities.lastIndex)
        )
    }
    var prayerCityManuallySelected by remember(context) {
        mutableStateOf(hijriPreferences.getBoolean("prayer_city_manually_selected", false))
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
    val cityName = if (selectedCityIndex == 0) weather.city else localizedPrayerCityName(selectedCityIndex, language)
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
    LaunchedEffect(selectedCityIndex, prayerCityManuallySelected) {
        hijriPreferences.edit()
            .putInt("selected_prayer_city_index", selectedCityIndex)
            .putBoolean("prayer_city_manually_selected", prayerCityManuallySelected)
            .apply()
    }
    LaunchedEffect(selectedCityIndex, latitude, longitude, cityName, zoneForKey) {
        val selectedLatitude = latitude
        val selectedLongitude = longitude
        if (selectedLatitude != null && selectedLongitude != null) {
            RoyalAdhanScheduler.updateLocation(
                context = context,
                latitude = selectedLatitude,
                longitude = selectedLongitude,
                city = cityName,
                timeZoneId = zoneForKey,
                manual = prayerCityManuallySelected
            )
        }
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
        if (!prayerCityManuallySelected && weather.latitude != null && weather.longitude != null) {
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

    val nextPrayerRakats = when (nextPrayer?.first) {
        "Fajr" -> 2
        "Dhuhr" -> 4
        "Asr" -> 4
        "Maghrib" -> 3
        "Isha" -> 4
        else -> null
    }

    if (selectedSection != "home") {
        MuslimSubSectionScreen(
            section = selectedSection, language = language,
            latitude = latitude, longitude = longitude,
            onRequestLocation = onRequestLocation,
            onBack = { selectedSection = "home" }
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(RoyalColors.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = adaptiveProfile.pageHorizontalPadding, vertical = adaptiveProfile.pageVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(adaptiveProfile.itemSpacing)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val ramadanDecorated = data?.hijriDate?.contains("رمضان") == true
                Text(
                    if (ramadanDecorated) "☾ ✦ " + when (language) { "en" -> "Muslim"; "tr" -> "Müslüman"; else -> "مسلم" } + " ✦ ☾" else when (language) { "en" -> "Muslim"; "tr" -> "Müslüman"; else -> "مسلم" },
                    color = RoyalColors.Gold,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (ramadanDecorated) when (language) { "en" -> "Ramadan Kareem • Hijri calendar & prayer times"; "tr" -> "Ramazanınız mübarek olsun • Hicri takvim ve namaz vakitleri"; else -> "رمضان كريم • التقويم الهجري ومواقيت الصلاة" } else when (language) { "en" -> "Hijri calendar & prayer times"; "tr" -> "Hicri takvim ve namaz vakitleri"; else -> "التقويم الهجري ومواقيت الصلاة" },
                    color = RoyalColors.Muted,
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
                .background(Color(0xFF171B29))
                .border(1.dp, RoyalColors.Gold, RoundedCornerShape(17.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(when (language) { "en" -> "Hijri date"; "tr" -> "Hicri tarih"; else -> "التاريخ الهجري" }, color = RoyalColors.Gold, fontSize = 12.sp)
                Text(data?.hijriDate?.takeIf { it.isNotBlank() } ?: when (language) { "en" -> "Loading Hijri date"; "tr" -> "Hicri tarih yükleniyor"; else -> "جارٍ تحميل التاريخ الهجري" }, color = RoyalColors.Pearl, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(data?.gregorianDate.orEmpty(), color = RoyalColors.Muted, fontSize = 11.sp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFF202536))
                            .border(1.dp, RoyalColors.Gold, RoundedCornerShape(7.dp))
                            .clickable { hijriDayAdjustment = (hijriDayAdjustment - 1).coerceAtLeast(-30) }
                            .focusable()
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(if (language == "en") "− day" else if (language == "tr") "− gün" else "− يوم", color = RoyalColors.Pearl, fontSize = 11.sp) }
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
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.clip(RoundedCornerShape(9.dp))
                    .clickable { showCityPicker = true }
                    .focusable()
                    .padding(start = 8.dp, top = 5.dp, bottom = 5.dp)
            ) {
                Text(
                    if (selectedCityIndex == 0 && cityName.isBlank()) when (language) { "en" -> "Current location"; "tr" -> "Mevcut konum"; else -> "الموقع الحالي" } else cityName,
                    color = RoyalColors.Pearl, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    when (language) { "en" -> "Change city"; "tr" -> "Şehri değiştir"; else -> "تغيير المدينة" },
                    color = RoyalColors.Gold, fontSize = 10.sp
                )
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
                    if (nextPrayerRakats != null) {
                        Text(
                            when (language) {
                                "en" -> "$nextPrayerRakats rakats"
                                "tr" -> "$nextPrayerRakats rekât"
                                else -> "$nextPrayerRakats ركعات"
                            },
                            color = Color(0xFFE7D6B1),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        RamadanCountdownCard(
            now = now,
            timeZoneId = zoneForKey,
            language = language
        )

        Text(when (language) { "en" -> "Prayer times for the next seven days"; "tr" -> "Önümüzdeki yedi günün namaz vakitleri"; else -> "جدول مواقيت الصلاة للأيام السبعة" }, color = RoyalColors.Gold, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(when (language) { "en" -> "Updates daily and calculates times for the selected city"; "tr" -> "Her gün otomatik yenilenir ve seçilen şehre göre hesaplanır"; else -> "يتجدد تلقائياً يومياً عند تغيّر التاريخ، وتُحسب المواقيت حسب المدينة المختارة" }, color = RoyalColors.Muted, fontSize = 10.sp)
        val prayerColumns = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFFB68A2C), RoundedCornerShape(12.dp))
                .background(Color(0xFF11151D)).padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(Modifier.fillMaxWidth().background(Color(0xFF242A39), RoundedCornerShape(7.dp)).padding(vertical = 7.dp, horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
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

        if (!hasCoordinates && selectedCityIndex == 0) {
            Text(
                when (language) {
                    "en" -> "Location unavailable. Choose a city using the Change city button."
                    "tr" -> "Konum alınamadı. Şehri Değiştir düğmesinden bir şehir seçin."
                    else -> "تعذّر تحديد موقعك. اضغط على «تغيير المدينة» واختر مدينتك."
                },
                color = RoyalColors.Muted, fontSize = 11.sp
            )
        }

        Text(when (language) { "en" -> "Explore"; "tr" -> "Keşfet"; else -> "استكشف الأقسام" },
            color = RoyalColors.Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        listOf(listOf("quran", "duas", "hadith"), listOf("worship")).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { section ->
                    MuslimNavigationTile(section, language, Modifier.weight(1f)) { selectedSection = section }
                }
                repeat((3 - row.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    if (showCityPicker) {
        AlertDialog(
            onDismissRequest = { showCityPicker = false },
            title = {
                Text(
                    when (language) { "en" -> "Choose a city"; "tr" -> "Şehir seçin"; else -> "اختر المدينة" },
                    color = RoyalColors.Gold
                )
            },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    prayerCities.indices.forEach { index ->
                        val selected = selectedCityIndex == index
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) RoyalColors.Gold.copy(alpha = 0.16f) else Color(0xFF202536))
                                .border(1.dp, if (selected) RoyalColors.Gold else RoyalColors.Muted.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                                .clickable {
                                    selectedCityIndex = index
                                    prayerCityManuallySelected = index != 0
                                    showCityPicker = false
                                    if (index == 0) onRequestLocation()
                                }
                                .focusable()
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                localizedPrayerCityName(index, language),
                                modifier = Modifier.weight(1f),
                                color = if (selected) RoyalColors.Gold else RoyalColors.Pearl,
                                fontSize = 13.sp
                            )
                            if (selected) Text("✓", color = RoyalColors.Gold, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCityPicker = false }) {
                    Text(when (language) { "en" -> "Close"; "tr" -> "Kapat"; else -> "إغلاق" }, color = RoyalColors.Gold)
                }
            },
            containerColor = Color(0xFF171B29),
            titleContentColor = RoyalColors.Gold,
            textContentColor = RoyalColors.Pearl
        )
    }
}

@Composable
private fun MuslimNavigationTile(
    section: String, language: String,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val adaptiveProfile = LocalRoyalAdaptiveProfile.current
    val title = when (section) {
        "quran" -> when (language) { "en" -> "Holy Quran"; "tr" -> "Kur'an-ı Kerim"; else -> "القرآن الكريم" }
        "duas" -> when (language) { "en" -> "Duas & Adhkar"; "tr" -> "Dualar ve Zikirler"; else -> "الأدعية والأذكار" }
        "hadith" -> when (language) { "en" -> "Hadith"; "tr" -> "Hadisler"; else -> "الأحاديث النبوية" }
        else -> when (language) { "en" -> "Worship tools"; "tr" -> "İbadet araçları"; else -> "أدوات العبادة" }
    }
    val iconRes = when (section) {
        "quran" -> R.drawable.ic_muslim_quran
        "duas" -> R.drawable.ic_muslim_dua
        "hadith" -> R.drawable.ic_muslim_hadith
        else -> R.drawable.ic_muslim_worship
    }
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF171B29))
            .border(1.dp, RoyalColors.Gold.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).focusable()
            .padding(horizontal = 7.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = title, tint = Color.Unspecified, modifier = Modifier.size(adaptiveProfile.iconSize(36.dp)))
        Text(title, color = RoyalColors.Pearl, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MuslimSubSectionScreen(
    section: String, language: String, latitude: Double?, longitude: Double?,
    onRequestLocation: () -> Unit, onBack: () -> Unit
) {
    val adaptiveProfile = LocalRoyalAdaptiveProfile.current
    if (section == "quran") {
        QuranReaderScreen(language = language, onClose = onBack)
        return
    }
    Column(
        Modifier.fillMaxSize().background(RoyalColors.Black)
            .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(adaptiveProfile.itemSpacing)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SmallPrayerAction(when (language) { "en" -> "Back"; "tr" -> "Geri"; else -> "رجوع" }, Icons.Rounded.Refresh, onBack)
            Text(
                when (section) {
                    "duas" -> when (language) { "en" -> "Duas & Adhkar"; "tr" -> "Dualar ve Zikirler"; else -> "الأدعية والأذكار" }
                    "hadith" -> when (language) { "en" -> "Prophetic Hadith"; "tr" -> "Peygamber Hadisleri"; else -> "الأحاديث النبوية" }
                    else -> when (language) { "en" -> "Worship tools"; "tr" -> "İbadet araçları"; else -> "أدوات العبادة" }
                },
                color = RoyalColors.Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center
            )
        }
        when (section) {
            "duas" -> DuasSection(language = language)
            "hadith" -> HadithsSection(language = language)
            "worship" -> RoyalWorshipToolsSection(
                latitude = latitude, longitude = longitude, language = language,
                onRequestLocation = onRequestLocation
            )
        }
    }
}

@Composable
private fun PrayerTableCell(value: String, modifier: Modifier, highlighted: Boolean, expired: Boolean = false) {
    Box(modifier = modifier.padding(horizontal = 1.dp).clip(RoundedCornerShape(4.dp)).then(if (expired) Modifier.background(Color(0xFFD62828)) else Modifier).padding(vertical = if (expired) 4.dp else 2.dp, horizontal = 1.dp), contentAlignment = Alignment.Center) {
        Text(text = value, color = if (expired) Color.White else RoyalColors.Pearl, fontSize = 9.sp, fontWeight = if (highlighted || expired) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center, lineHeight = 11.sp, maxLines = 2)
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

private data class DuaItem(val title: String, val text: String, val source: String)

@Composable
private fun RamadanCountdownCard(now: Long, timeZoneId: String, language: String) {
    val target = remember(now / 60_000L, timeZoneId) {
        PrayerTimesRepository.nextRamadanTarget(now, timeZoneId)
    }
    val remainingDays = remember(now / 60_000L, target, timeZoneId) {
        if (target.isRamadan) 0L else {
            val zone = TimeZone.getTimeZone(timeZoneId)
            val cursor = java.util.GregorianCalendar(zone, Locale.US).apply {
                timeInMillis = now
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val end = java.util.GregorianCalendar(zone, Locale.US).apply {
                timeInMillis = target.targetMillis
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            var days = 0L
            while (cursor.timeInMillis < end.timeInMillis) {
                cursor.add(java.util.Calendar.DAY_OF_YEAR, 1)
                days++
            }
            days
        }
    }
    val displayedDays = remainingDays.toString().map { digit ->
        when (digit) {
            '0' -> '٠'; '1' -> '١'; '2' -> '٢'; '3' -> '٣'; '4' -> '٤'
            '5' -> '٥'; '6' -> '٦'; '7' -> '٧'; '8' -> '٨'; '9' -> '٩'
            else -> digit
        }
    }.joinToString("")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF111111), Color(0xFF173C32), Color(0xFF30200D))))
            .border(1.dp, RoyalColors.Gold, RoundedCornerShape(17.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (target.isRamadan) {
            Text(
                when (language) {
                    "en" -> "☾ Ramadan Mubarak"
                    "tr" -> "☾ Ramazanınız mübarek olsun"
                    else -> "☾ رمضان مبارك"
                },
                color = RoyalColors.Gold,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        } else if (language == "ar") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("☾", color = RoyalColors.Gold, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("باقي لشهر رمضان", color = RoyalColors.Gold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(displayedDays, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("يوم", color = RoyalColors.Gold, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text(
                if (language == "en") "☾ Days until Ramadan" else "☾ Ramazan'a kalan gün",
                color = RoyalColors.Gold,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (target.isRamadan) {
            Text(
                when (language) {
                    "en" -> "May this month bring you blessings"
                    "tr" -> "Ramazan ayınız mübarek olsun"
                    else -> "تقبّل الله طاعتكم وكل عام وأنتم بخير"
                },
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        } else if (language != "ar") {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    remainingDays.toString(),
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (language == "en") "days" else "gün",
                    color = RoyalColors.Gold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun DuasSection(language: String) {
    val categories = listOf(
        "أذكار الصباح والمساء" to listOf(
            DuaItem("سيد الاستغفار", "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي، فَاغْفِرْ لِي؛ فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ.", "صحيح البخاري • 6306"),
            DuaItem("ذكر الصباح والمساء", "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ، وَهُوَ السَّمِيعُ الْعَلِيمُ.", "سنن أبي داود 5088 والترمذي 3388 • ثلاث مرات"),
            DuaItem("رضيت بالله رباً", "رَضِيتُ بِاللَّهِ رَبًّا، وَبِالْإِسْلَامِ دِينًا، وَبِمُحَمَّدٍ ﷺ نَبِيًّا.", "سنن أبي داود • 5072 • ثلاث مرات")
        ),
        "أذكار النوم والاستيقاظ" to listOf(
            DuaItem("عند النوم", "بِاسْمِكَ اللَّهُمَّ أَمُوتُ وَأَحْيَا.", "صحيح البخاري • 6324"),
            DuaItem("عند الاستيقاظ", "الْحَمْدُ لِلَّهِ الَّذِي أَحْيَانَا بَعْدَ مَا أَمَاتَنَا، وَإِلَيْهِ النُّشُورُ.", "صحيح البخاري • 6312")
        ),
        "أذكار بعد الصلاة" to listOf(
            DuaItem("الاستغفار بعد الصلاة", "أَسْتَغْفِرُ اللَّهَ، أَسْتَغْفِرُ اللَّهَ، أَسْتَغْفِرُ اللَّهَ.", "صحيح مسلم • 591"),
            DuaItem("دعاء السلام بعد الصلاة", "اللَّهُمَّ أَنْتَ السَّلَامُ، وَمِنْكَ السَّلَامُ، تَبَارَكْتَ يَا ذَا الْجَلَالِ وَالْإِكْرَامِ.", "صحيح مسلم • 591"),
            DuaItem("التسبيح بعد الصلاة", "سُبْحَانَ اللَّهِ (٣٣)، وَالْحَمْدُ لِلَّهِ (٣٣)، وَاللَّهُ أَكْبَرُ (٣٣)، ثم تمام المئة: لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ.", "صحيح مسلم • 597")
        ),
        "أدعية الذرية والأبناء" to listOf(
            DuaItem("طلب الذرية الطيبة", "رَبِّ هَبْ لِي مِنْ لَدُنْكَ ذُرِّيَّةً طَيِّبَةً إِنَّكَ سَمِيعُ الدُّعَاءِ.", "القرآن الكريم • آل عمران: 38"),
            DuaItem("دعاء زكريا عليه السلام", "رَبِّ لَا تَذَرْنِي فَرْدًا وَأَنْتَ خَيْرُ الْوَارِثِينَ.", "القرآن الكريم • الأنبياء: 89"),
            DuaItem("صلاح الذرية والصلاة", "رَبِّ اجْعَلْنِي مُقِيمَ الصَّلَاةِ وَمِنْ ذُرِّيَّتِي رَبَّنَا وَتَقَبَّلْ دُعَاءِ.", "القرآن الكريم • إبراهيم: 40"),
            DuaItem("قرة أعين الأسرة", "رَبَّنَا هَبْ لَنَا مِنْ أَزْوَاجِنَا وَذُرِّيَّاتِنَا قُرَّةَ أَعْيُنٍ وَاجْعَلْنَا لِلْمُتَّقِينَ إِمَامًا.", "القرآن الكريم • الفرقان: 74")
        ),
        "أدعية الرزق والبركة" to listOf(
            DuaItem("طلب الخير والرزق", "رَبِّ إِنِّي لِمَا أَنْزَلْتَ إِلَيَّ مِنْ خَيْرٍ فَقِيرٌ.", "القرآن الكريم • القصص: 24"),
            DuaItem("طلب الرزق الطيب", "اللَّهُمَّ إِنِّي أَسْأَلُكَ عِلْمًا نَافِعًا، وَرِزْقًا طَيِّبًا، وَعَمَلًا مُتَقَبَّلًا.", "سنن ابن ماجه • 925"),
            DuaItem("الغنى بالحلال", "اللَّهُمَّ اكْفِنِي بِحَلَالِكَ عَنْ حَرَامِكَ، وَأَغْنِنِي بِفَضْلِكَ عَمَّنْ سِوَاكَ.", "جامع الترمذي • 3563")
        ),
        "أدعية الزواج والأسرة" to listOf(
            DuaItem("صلاح الأسرة", "رَبَّنَا هَبْ لَنَا مِنْ أَزْوَاجِنَا وَذُرِّيَّاتِنَا قُرَّةَ أَعْيُنٍ وَاجْعَلْنَا لِلْمُتَّقِينَ إِمَامًا.", "القرآن الكريم • الفرقان: 74"),
            DuaItem("إصلاح الذرية", "رَبِّ أَوْزِعْنِي أَنْ أَشْكُرَ نِعْمَتَكَ الَّتِي أَنْعَمْتَ عَلَيَّ وَعَلَى وَالِدَيَّ وَأَنْ أَعْمَلَ صَالِحًا تَرْضَاهُ وَأَصْلِحْ لِي فِي ذُرِّيَّتِي.", "القرآن الكريم • الأحقاف: 15")
        ),
        "أدعية الوالدين" to listOf(
            DuaItem("الرحمة بالوالدين", "رَبِّ ارْحَمْهُمَا كَمَا رَبَّيَانِي صَغِيرًا.", "القرآن الكريم • الإسراء: 24"),
            DuaItem("المغفرة للوالدين", "رَبَّنَا اغْفِرْ لِي وَلِوَالِدَيَّ وَلِلْمُؤْمِنِينَ يَوْمَ يَقُومُ الْحِسَابُ.", "القرآن الكريم • إبراهيم: 41")
        ),
        "أدعية الهم والكرب" to listOf(
            DuaItem("دعاء ذي النون", "لَا إِلَهَ إِلَّا أَنْتَ سُبْحَانَكَ إِنِّي كُنْتُ مِنَ الظَّالِمِينَ.", "القرآن الكريم • الأنبياء: 87"),
            DuaItem("دعاء الهم والحزن", "اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْهَمِّ وَالْحَزَنِ، وَالْعَجْزِ وَالْكَسَلِ، وَالْجُبْنِ وَالْبُخْلِ، وَضَلَعِ الدَّيْنِ وَغَلَبَةِ الرِّجَالِ.", "صحيح البخاري • 6369"),
            DuaItem("دعاء الكرب", "لَا إِلَهَ إِلَّا اللَّهُ الْعَظِيمُ الْحَلِيمُ، لَا إِلَهَ إِلَّا اللَّهُ رَبُّ الْعَرْشِ الْعَظِيمِ، لَا إِلَهَ إِلَّا اللَّهُ رَبُّ السَّمَوَاتِ وَرَبُّ الْأَرْضِ وَرَبُّ الْعَرْشِ الْكَرِيمِ.", "صحيح البخاري • 6346 وصحيح مسلم • 2730")
        ),
        "أدعية المرض والشفاء" to listOf(
            DuaItem("دعاء الشفاء", "اللَّهُمَّ رَبَّ النَّاسِ، أَذْهِبِ الْبَأْسَ، اشْفِ أَنْتَ الشَّافِي، لَا شِفَاءَ إِلَّا شِفَاؤُكَ، شِفَاءً لَا يُغَادِرُ سَقَمًا.", "صحيح البخاري • 5675 وصحيح مسلم • 2191"),
            DuaItem("دعاء أيوب عليه السلام", "أَنِّي مَسَّنِيَ الضُّرُّ وَأَنْتَ أَرْحَمُ الرَّاحِمِينَ.", "القرآن الكريم • الأنبياء: 83")
        ),
        "أدعية قضاء الدين" to listOf(
            DuaItem("الاستعاذة من الدَّين", "اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْمَأْثَمِ وَالْمَغْرَمِ.", "صحيح البخاري • 832"),
            DuaItem("قضاء الحاجة بالحلال", "اللَّهُمَّ اكْفِنِي بِحَلَالِكَ عَنْ حَرَامِكَ، وَأَغْنِنِي بِفَضْلِكَ عَمَّنْ سِوَاكَ.", "جامع الترمذي • 3563")
        ),
        "أدعية التوبة والمغفرة" to listOf(
            DuaItem("دعاء آدم وحواء عليهما السلام", "رَبَّنَا ظَلَمْنَا أَنْفُسَنَا وَإِنْ لَمْ تَغْفِرْ لَنَا وَتَرْحَمْنَا لَنَكُونَنَّ مِنَ الْخَاسِرِينَ.", "القرآن الكريم • الأعراف: 23"),
            DuaItem("طلب المغفرة", "رَبِّ اغْفِرْ لِي وَتُبْ عَلَيَّ إِنَّكَ أَنْتَ التَّوَّابُ الرَّحِيمُ.", "سنن أبي داود • 1516")
        ),
        "أدعية السفر" to listOf(
            DuaItem("دعاء ركوب وسيلة السفر", "سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ، وَإِنَّا إِلَى رَبِّنَا لَمُنْقَلِبُونَ.", "القرآن الكريم • الزخرف: 13–14"),
            DuaItem("دعاء السفر", "اللَّهُمَّ إِنَّا نَسْأَلُكَ فِي سَفَرِنَا هَذَا الْبِرَّ وَالتَّقْوَى، وَمِنَ الْعَمَلِ مَا تَرْضَى.", "صحيح مسلم • 1342")
        ),
        "أدعية النجاح والتيسير" to listOf(
            DuaItem("تيسير الأمر", "رَبِّ اشْرَحْ لِي صَدْرِي، وَيَسِّرْ لِي أَمْرِي.", "القرآن الكريم • طه: 25–26"),
            DuaItem("طلب العلم النافع", "رَبِّ زِدْنِي عِلْمًا.", "القرآن الكريم • طه: 114")
        ),
        "أدعية الطعام والشراب" to listOf(
            DuaItem("قبل الطعام", "بِسْمِ اللَّهِ.", "صحيح البخاري • 5376 وصحيح مسلم • 2022"),
            DuaItem("بعد الطعام", "الْحَمْدُ لِلَّهِ الَّذِي أَطْعَمَنِي هَذَا وَرَزَقَنِيهِ مِنْ غَيْرِ حَوْلٍ مِنِّي وَلَا قُوَّةٍ.", "سنن أبي داود • 4023 والترمذي • 3458")
        ),
        "أدعية الحفظ والتحصين" to listOf(
            DuaItem("الاستعاذة من شر الخلق", "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ.", "صحيح مسلم • 2708"),
            DuaItem("دعاء جامع", "اللَّهُمَّ إِنِّي أَسْأَلُكَ الْهُدَى وَالتُّقَى وَالْعَفَافَ وَالْغِنَى.", "صحيح مسلم • 2721")
        )
    )
    var expandedCategories by remember {
        mutableStateOf(setOf("أذكار الصباح والمساء"))
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            when (language) { "en" -> "Duas & daily adhkar"; "tr" -> "Dualar ve günlük zikirler"; else -> "الأدعية والأذكار" },
            color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold
        )
        Text(
            when (language) { "en" -> "Offline collection • Quranic supplications and sourced Prophetic duas"; "tr" -> "Çevrimdışı koleksiyon • Kur'an duaları ve kaynaklı nebevi dualar"; else -> "مجموعة تعمل دون إنترنت • أدعية قرآنية ونبوية مع توثيق المصادر" },
            color = RoyalColors.Muted, fontSize = 10.sp
        )
        categories.forEach { (category, items) ->
            val isExpanded = category in expandedCategories
            var categoryFocused by remember(category) { mutableStateOf(false) }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFFFFE7A8).copy(alpha = 0.68f))
                    .border(1.dp, Color(0xFFB68A2C), RoundedCornerShape(13.dp))
                    .padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                        .background(if (categoryFocused) Color(0xFFFFD66E) else Color.Transparent)
                        .border(
                            if (categoryFocused) 2.dp else 1.dp,
                            if (categoryFocused) Color(0xFF138F83) else Color(0xFFD4B66F),
                            RoundedCornerShape(9.dp)
                        )
                        .clickable {
                            expandedCategories = if (isExpanded) {
                                expandedCategories - category
                            } else {
                                expandedCategories + category
                            }
                        }
                        .onFocusChanged { categoryFocused = it.isFocused }
                        .focusable()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        category,
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF5A3B0B),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        items.size.toString(),
                        color = Color(0xFF6A5123),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        if (isExpanded) "−" else "+",
                        color = if (categoryFocused) Color(0xFF075B53) else Color(0xFF805A16),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isExpanded) {
                    items.forEach { dua ->
                        var duaFocused by remember(dua.title) { mutableStateOf(false) }
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                                .background(if (duaFocused) Color(0xFFFFF0C2) else Color(0xFFFFFAEC))
                                .border(
                                    if (duaFocused) 2.dp else 1.dp,
                                    if (duaFocused) Color(0xFF138F83) else Color(0xFFD8BD7B),
                                    RoundedCornerShape(9.dp)
                                )
                                .onFocusChanged { duaFocused = it.isFocused }
                                .focusable()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(dua.title, color = Color(0xFF805A16), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(dua.text, color = Color(0xFF171717), fontSize = 15.sp, lineHeight = 25.sp, textAlign = TextAlign.Right)
                            Text(dua.source, color = Color(0xFF6A6252), fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

private data class HadithItem(
    val title: String,
    val text: String,
    val narrator: String,
    val source: String,
    val lesson: String
)

@Composable
private fun HadithsSection(language: String) {
    val hadiths = listOf(
        HadithItem("الإخلاص والنية", "إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ، وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى.", "عمر بن الخطاب رضي الله عنه", "صحيح البخاري • 1، وصحيح مسلم • 1907", "صلاح العمل مرتبط بإخلاص النية."),
        HadithItem("محبة الخير للآخرين", "لَا يُؤْمِنُ أَحَدُكُمْ حَتَّى يُحِبَّ لِأَخِيهِ مَا يُحِبُّ لِنَفْسِهِ.", "أنس بن مالك رضي الله عنه", "صحيح البخاري • 13، وصحيح مسلم • 45", "من كمال الإيمان أن تحب للناس الخير."),
        HadithItem("حفظ اللسان", "مَنْ كَانَ يُؤْمِنُ بِاللَّهِ وَالْيَوْمِ الْآخِرِ فَلْيَقُلْ خَيْرًا أَوْ لِيَصْمُتْ.", "أبو هريرة رضي الله عنه", "صحيح البخاري • 6018، وصحيح مسلم • 47", "الكلمة الطيبة خير، والصمت عن الشر سلامة."),
        HadithItem("القوة وضبط الغضب", "لَيْسَ الشَّدِيدُ بِالصُّرَعَةِ، إِنَّمَا الشَّدِيدُ الَّذِي يَمْلِكُ نَفْسَهُ عِنْدَ الْغَضَبِ.", "أبو هريرة رضي الله عنه", "صحيح البخاري • 6114، وصحيح مسلم • 2609", "القوة الحقيقية في ضبط النفس."),
        HadithItem("الرحمة", "مَنْ لَا يَرْحَمْ لَا يُرْحَمْ.", "أبو هريرة رضي الله عنه", "صحيح البخاري • 5997، وصحيح مسلم • 2318", "ارحم الناس والخلق يرحمك الله."),
        HadithItem("الصدقة", "اتَّقُوا النَّارَ وَلَوْ بِشِقِّ تَمْرَةٍ.", "عدي بن حاتم رضي الله عنه", "صحيح البخاري • 1417، وصحيح مسلم • 1016", "لا تستقل عملاً صالحاً ولو كان قليلاً."),
        HadithItem("طلب العلم", "مَنْ سَلَكَ طَرِيقًا يَلْتَمِسُ فِيهِ عِلْمًا، سَهَّلَ اللَّهُ لَهُ بِهِ طَرِيقًا إِلَى الْجَنَّةِ.", "أبو هريرة رضي الله عنه", "صحيح مسلم • 2699", "طلب العلم الشرعي طريق إلى الخير."),
        HadithItem("الإحسان", "إِنَّ اللَّهَ كَتَبَ الْإِحْسَانَ عَلَى كُلِّ شَيْءٍ.", "شداد بن أوس رضي الله عنه", "صحيح مسلم • 1955", "أحسن في عبادتك ومعاملتك وكل عملك."),
        HadithItem("سلامة الناس", "الْمُسْلِمُ مَنْ سَلِمَ الْمُسْلِمُونَ مِنْ لِسَانِهِ وَيَدِهِ.", "عبد الله بن عمرو رضي الله عنهما", "صحيح البخاري • 10، وصحيح مسلم • 40", "لا تؤذِ الناس بقول أو فعل."),
        HadithItem("اليسر والبشارة", "يَسِّرُوا وَلَا تُعَسِّرُوا، وَبَشِّرُوا وَلَا تُنَفِّرُوا.", "أنس بن مالك رضي الله عنه", "صحيح البخاري • 69، وصحيح مسلم • 1734", "عامل الناس بالرفق واليسر."),
        HadithItem("الكلمة الطيبة", "الْكَلِمَةُ الطَّيِّبَةُ صَدَقَةٌ.", "أبو هريرة رضي الله عنه", "صحيح البخاري • 2989، وصحيح مسلم • 1009", "الكلام الحسن من أبواب الصدقة."),
        HadithItem("الوصية بضبط النفس", "لَا تَغْضَبْ.", "أبو هريرة رضي الله عنه", "صحيح البخاري • 6116", "ابتعد عن أسباب الغضب واضبط نفسك.")
    )
    var showHadiths by remember { mutableStateOf(false) }
    var headerFocused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                .background(if (headerFocused) Color(0xFFFFD66E) else Color(0xFFFFE7A8))
                .border(
                    if (headerFocused) 2.dp else 1.dp,
                    if (headerFocused) Color(0xFF138F83) else Color(0xFFB68A2C),
                    RoundedCornerShape(11.dp)
                )
                .clickable { showHadiths = !showHadiths }
                .onFocusChanged { headerFocused = it.isFocused }
                .focusable()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (language) { "en" -> "Authentic Prophetic hadiths"; "tr" -> "Sahih Peygamber hadisleri"; else -> "أحاديث نبوية صحيحة" },
                    color = Color(0xFF5A3B0B), fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    when (language) { "en" -> "12 sourced narrations"; "tr" -> "Kaynaklı 12 rivayet"; else -> "12 حديثاً مع الراوي والمصدر" },
                    color = Color(0xFF6A6252), fontSize = 10.sp
                )
            }
            Text(if (showHadiths) "−" else "+", color = Color(0xFF805A16), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (showHadiths) {
            Text(
                when (language) { "en" -> "Selected authentic narrations with narrator and source references"; "tr" -> "Ravi ve kaynak bilgileriyle seçilmiş sahih rivayetler"; else -> "أحاديث ثابتة مع اسم الراوي والمصدر وشرح موجز" },
                color = RoyalColors.Muted, fontSize = 10.sp
            )
            hadiths.forEachIndexed { index, hadith ->
                var hadithFocused by remember(hadith.title) { mutableStateOf(false) }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (hadithFocused) Color(0xFF244638) else Color(0xFF17231D))
                        .border(
                            if (hadithFocused) 2.dp else 1.dp,
                            if (hadithFocused) Color(0xFF20C9B5) else Color(0xFFB68A2C),
                            RoundedCornerShape(12.dp)
                        )
                        .onFocusChanged { hadithFocused = it.isFocused }
                        .focusable()
                        .padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text((index + 1).toString() + ". " + hadith.title, color = RoyalColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(hadith.text, color = Color.White, fontSize = 16.sp, lineHeight = 27.sp, textAlign = TextAlign.Right)
                    Text("الراوي: " + hadith.narrator, color = Color(0xFFE7D6B1), fontSize = 10.sp)
                    Text(hadith.source, color = Color(0xFFB9D7C3), fontSize = 10.sp)
                    Text("الفائدة: " + hadith.lesson, color = Color.White.copy(alpha = 0.82f), fontSize = 10.sp)
                    Text("الدرجة: صحيح", color = Color(0xFF8ED6A5), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

