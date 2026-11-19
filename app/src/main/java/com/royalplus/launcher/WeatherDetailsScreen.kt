package com.royalplus.launcher

import com.royal.adaptive.LocalRoyalAdaptiveProfile

import com.royalplus.launcher.royalsounds.RoyalUiSound

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.delay
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
    onRequestLocation: () -> Unit,
    onChooseCity: (WeatherCitySuggestion) -> Unit,
    onChooseCityByName: (String) -> Unit,
    onUseDeviceLocation: () -> Unit,
    motionEnabled: Boolean = true
) {
    val adaptiveProfile = LocalRoyalAdaptiveProfile.current
    val context = LocalContext.current.applicationContext
    RoyalWeatherAmbienceEffect(weather)
    RoyalOwlAmbienceEffect(weather)
    BackHandler(onBack = {
        playLauncherUiSound(context, RoyalUiSound.NAV_BACK)
        onBack()
    })
    var showCityPicker by remember { mutableStateOf(false) }
    var showAudioSettings by remember { mutableStateOf(false) }
    var masterAudioEnabled by remember {
        mutableStateOf(RoyalWeatherAmbienceController.isEnabled(context, RoyalWeatherAmbienceController.MASTER_ENABLED_KEY))
    }
    var rainAudioEnabled by remember {
        mutableStateOf(RoyalWeatherAmbienceController.isEnabled(context, RoyalWeatherAmbienceController.RAIN_ENABLED_KEY))
    }
    var thunderAudioEnabled by remember {
        mutableStateOf(RoyalWeatherAmbienceController.isEnabled(context, RoyalWeatherAmbienceController.THUNDER_ENABLED_KEY))
    }
    var windAudioEnabled by remember {
        mutableStateOf(RoyalWeatherAmbienceController.isEnabled(context, RoyalWeatherAmbienceController.WIND_ENABLED_KEY))
    }
    var birdsAudioEnabled by remember {
        mutableStateOf(RoyalWeatherAmbienceController.isEnabled(context, RoyalWeatherAmbienceController.BIRDS_ENABLED_KEY))
    }
    var owlAudioEnabled by remember {
        mutableStateOf(
            context.getSharedPreferences(RoyalOwlAmbienceController.PREFERENCES_NAME, android.content.Context.MODE_PRIVATE)
                .getBoolean(RoyalOwlAmbienceController.ENABLED_KEY, true)
        )
    }
    var weatherAudioVolume by remember {
        mutableStateOf(RoyalWeatherAmbienceController.volume(context))
    }
    var cityInput by remember(weather.city) {
        mutableStateOf(weather.city.substringBefore("،").substringBefore(","))
    }
    var citySuggestions by remember { mutableStateOf(emptyList<WeatherCitySuggestion>()) }
    var isSearchingCities by remember { mutableStateOf(false) }
    val latestCityQuery = rememberUpdatedState(cityInput.trim())
    val cityPickerVisible = rememberUpdatedState(showCityPicker)
    LaunchedEffect(showCityPicker, cityInput) {
        val query = cityInput.trim()
        citySuggestions = emptyList()
        if (!showCityPicker || query.length < 2) {
            isSearchingCities = false
            return@LaunchedEffect
        }
        isSearchingCities = true
        delay(350L)
        RoyalWeatherRepository.searchCities(query) { results ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (cityPickerVisible.value && latestCityQuery.value == query) {
                    citySuggestions = results
                    isSearchingCities = false
                }
            }
        }
    }
    val zone = remember(weather.timeZoneId) { TimeZone.getTimeZone(weather.timeZoneId ?: TimeZone.getDefault().id) }
    val hour = remember(currentTimeMillis, weather.timeZoneId) {
        SimpleDateFormat("H", Locale.US).apply { timeZone = zone }.format(Date(currentTimeMillis)).toIntOrNull() ?: 12
    }
    val night = hour >= 19 || hour < 6
    val weatherVideo = weatherVideoAsset(weather, hour)
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

    if (showAudioSettings) {
        AlertDialog(
            onDismissRequest = { showAudioSettings = false },
            title = {
                Text(
                    when (language) { "en" -> "Nature & weather sounds"; "tr" -> "Doğa ve hava sesleri"; else -> "أصوات الطبيعة والطقس" },
                    color = gold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    WeatherAudioToggleRow(
                        language = language,
                        title = when (language) { "en" -> "All ambient sounds"; "tr" -> "Tüm ortam sesleri"; else -> "تشغيل جميع الأصوات" },
                        enabled = masterAudioEnabled
                    ) {
                        masterAudioEnabled = !masterAudioEnabled
                        RoyalWeatherAmbienceController.setSoundEnabled(
                            context, RoyalWeatherAmbienceController.MASTER_ENABLED_KEY, masterAudioEnabled
                        )
                    }
                    WeatherAudioToggleRow(
                        language = language,
                        title = when (language) { "en" -> "Natural rain"; "tr" -> "Doğal yağmur"; else -> "المطر الطبيعي" },
                        enabled = rainAudioEnabled
                    ) {
                        rainAudioEnabled = !rainAudioEnabled
                        RoyalWeatherAmbienceController.setSoundEnabled(
                            context, RoyalWeatherAmbienceController.RAIN_ENABLED_KEY, rainAudioEnabled
                        )
                    }
                    WeatherAudioToggleRow(
                        language = language,
                        title = when (language) { "en" -> "Distant thunder"; "tr" -> "Uzak gök gürültüsü"; else -> "دوي الرعد البعيد" },
                        enabled = thunderAudioEnabled
                    ) {
                        thunderAudioEnabled = !thunderAudioEnabled
                        RoyalWeatherAmbienceController.setSoundEnabled(
                            context, RoyalWeatherAmbienceController.THUNDER_ENABLED_KEY, thunderAudioEnabled
                        )
                    }
                    WeatherAudioToggleRow(
                        language = language,
                        title = when (language) { "en" -> "Wind"; "tr" -> "Rüzgâr"; else -> "الرياح" },
                        enabled = windAudioEnabled
                    ) {
                        windAudioEnabled = !windAudioEnabled
                        RoyalWeatherAmbienceController.setSoundEnabled(
                            context, RoyalWeatherAmbienceController.WIND_ENABLED_KEY, windAudioEnabled
                        )
                    }
                    WeatherAudioToggleRow(
                        language = language,
                        title = when (language) { "en" -> "Morning birds"; "tr" -> "Sabah kuşları"; else -> "عصافير الصباح" },
                        enabled = birdsAudioEnabled
                    ) {
                        birdsAudioEnabled = !birdsAudioEnabled
                        RoyalWeatherAmbienceController.setSoundEnabled(
                            context, RoyalWeatherAmbienceController.BIRDS_ENABLED_KEY, birdsAudioEnabled
                        )
                    }
                    WeatherAudioToggleRow(
                        language = language,
                        title = when (language) { "en" -> "Night owl"; "tr" -> "Gece baykuşu"; else -> "بومة الليل" },
                        enabled = owlAudioEnabled
                    ) {
                        owlAudioEnabled = !owlAudioEnabled
                        RoyalOwlAmbienceController.setEnabled(context, owlAudioEnabled)
                    }
                    Text(
                        when (language) { "en" -> "Volume"; "tr" -> "Ses seviyesi"; else -> "مستوى الصوت" },
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = weatherAudioVolume,
                        onValueChange = { value ->
                            weatherAudioVolume = value
                            RoyalWeatherAmbienceController.setVolume(context, value)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioSettings = false }) {
                    Text(when (language) { "en" -> "Done"; "tr" -> "Bitti"; else -> "تم" }, color = gold)
                }
            },
            containerColor = Color(0xFF101722)
        )
    }

    if (showCityPicker) {
        AlertDialog(
            onDismissRequest = { showCityPicker = false },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        when (language) { "en" -> "Choose a city or town"; "tr" -> "Şehir veya kasaba seç"; else -> "اختيار مدينة أو بلدة" },
                        color = gold
                    )
                    Text(
                        when (language) { "en" -> "Location data: Open-Meteo / GeoNames"; "tr" -> "Konum verileri: Open-Meteo / GeoNames"; else -> "مصدر بيانات المواقع: Open-Meteo / GeoNames" },
                        modifier = Modifier.clickable {
                            try {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://open-meteo.com/en/docs/geocoding-api")
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Exception) {
                                // Keep the city picker usable if the device has no browser.
                            }
                        },
                        color = gold.copy(alpha = 0.82f),
                        fontSize = 10.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    val searchShape = RoundedCornerShape(16.dp)
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(searchShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF078F91), Color(0xFF126DB2))
                                )
                            )
                            .border(1.5.dp, Color.White, searchShape)
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = cityInput,
                            onValueChange = { cityInput = it },
                            singleLine = true,
                            label = {
                                Text(
                                    when (language) { "en" -> "City or town name"; "tr" -> "Şehir veya kasaba adı"; else -> "اسم المدينة أو البلدة" },
                                    color = gold
                                )
                            },
                            placeholder = {
                                Text(
                                    when (language) { "en" -> "Type at least 2 characters"; "tr" -> "En az 2 karakter yazın"; else -> "اكتب حرفين على الأقل" },
                                    color = gold.copy(alpha = 0.78f)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = gold,
                                unfocusedTextColor = gold,
                                focusedLabelColor = gold,
                                unfocusedLabelColor = gold,
                                cursorColor = gold,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    }
                    when {
                        cityInput.trim().length < 2 -> Text(
                            when (language) { "en" -> "Enter at least 2 characters to search cities and villages"; "tr" -> "Şehirleri ve köyleri aramak için en az 2 karakter yazın"; else -> "اكتب حرفين على الأقل للبحث عن المدن والقرى" },
                            color = gold,
                            fontSize = 12.sp
                        )
                        isSearchingCities -> Text(
                            when (language) { "en" -> "Searching cities and villages…"; "tr" -> "Şehirler ve köyler aranıyor…"; else -> "جارٍ البحث عن المدن والقرى…" },
                            color = gold,
                            fontSize = 12.sp
                        )
                        citySuggestions.isEmpty() -> Text(
                            when (language) { "en" -> "No matching places found. Try another spelling."; "tr" -> "Eşleşen yer bulunamadı. Farklı yazmayı deneyin."; else -> "لم نعثر على أماكن مطابقة؛ جرّب كتابة الاسم بطريقة أخرى" },
                            color = gold,
                            fontSize = 12.sp
                        )
                        else -> Column(
                            modifier = Modifier.fillMaxWidth()
                                .heightIn(max = 245.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            citySuggestions.forEach { suggestion ->
                                val resultShape = RoundedCornerShape(12.dp)
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(resultShape)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFF087F83), Color(0xFF175D9C))
                                            )
                                        )
                                        .border(1.dp, Color.White.copy(alpha = 0.72f), resultShape)
                                        .clickable {
                                            onChooseCity(suggestion)
                                            showCityPicker = false
                                        }
                                        .focusable()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = suggestion.displayName,
                                        color = gold,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = cityInput.trim()
                    if (value.isNotBlank()) onChooseCityByName(value)
                    showCityPicker = false
                }) {
                    Text(when (language) { "en" -> "Apply"; "tr" -> "Uygula"; else -> "تطبيق" }, color = gold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showCityPicker = false
                        onUseDeviceLocation()
                    }) {
                        Text(when (language) { "en" -> "Use my location"; "tr" -> "Konumumu kullan"; else -> "استخدام موقعي" }, color = gold)
                    }
                    TextButton(onClick = { showCityPicker = false }) {
                        Text(when (language) { "en" -> "Cancel"; "tr" -> "İptal"; else -> "إلغاء" }, color = gold)
                    }
                }
            },
            containerColor = Color(0xFF123C50)
        )
    }


    val nextPrayerLabel = nextPrayer?.key?.let { key ->
        when (language) {
            "en" -> prayerNamesEn[key].orEmpty()
            "tr" -> prayerNamesTr[key].orEmpty()
            else -> prayerNames[key].orEmpty()
        }
    }.orEmpty()
    val nextPrayerSummary = when {
        nextPrayer == null -> ""
        language == "en" -> "Next $nextPrayerLabel in $countdown"
        language == "tr" -> "Sonraki $nextPrayerLabel: $countdown"
        else -> "الصلاة القادمة $nextPrayerLabel بعد $countdown"
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF52799B))) {
        RoyalMotionImage(
            image = rememberRoyalBitmapPainter(backgroundRes, maxEdgePx = 4096),
            modifier = Modifier.fillMaxSize(),
            motionEnabled = motionEnabled
        )
        if (motionEnabled && weatherVideo != null) {
            WeatherVideoBackground(assetName = weatherVideo, modifier = Modifier.fillMaxSize())
        }
        RoyalWeatherAtmosphereOverlay(
            weatherCode = weather.weatherCode,
            motionEnabled = motionEnabled,
            windSpeedKmh = weather.windSpeedKmh,
            nightMode = night,
            sunsetMode = hour in 17..18
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0x2200152B), Color(0x3A17334C), Color(0x6D193B59))
                )
            )
        )

        if (adaptiveProfile.isPhone) {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = 12.dp,
                        vertical = adaptiveProfile.pageVerticalPadding + 30.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WeatherCurrentDetailsPanel(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 340.dp),
                    weather = weather,
                    hour = hour,
                    timeText = timeText,
                    dateText = dateText,
                    prayerTimes = prayerTimes,
                    language = language,
                    nextPrayerSummary = nextPrayerSummary,
                    onOpenAudioSettings = { showAudioSettings = true },
                    onRefresh = { if (weather.needsPermission && !weather.isManualLocation) onRequestLocation() else onRefresh() },
                    onChooseCity = {
                        cityInput = weather.city.substringBefore("،").substringBefore(",")
                        showCityPicker = true
                    },
                    onUseDeviceLocation = onUseDeviceLocation,
                    isPhone = true
                )
                WeatherForecastPanel(
                    modifier = Modifier.fillMaxWidth().height(390.dp),
                    weather = weather,
                    language = language,
                    currentTimeMillis = currentTimeMillis,
                    timeZone = zone,
                    glass = glass,
                    outline = outline,
                    gold = gold
                )
                WeatherPrayerTimesPanel(
                    modifier = Modifier.fillMaxWidth().height(164.dp),
                    prayerTimes = prayerTimes,
                    city = weather.city,
                    activeKey = activeKey,
                    language = language,
                    glass = glass,
                    outline = outline,
                    gold = gold
                )
            }
        } else {
            Row(
                Modifier.fillMaxSize().padding(
                    horizontal = adaptiveProfile.pageHorizontalPadding,
                    vertical = adaptiveProfile.pageVerticalPadding + 30.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    Modifier.weight(0.62f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WeatherForecastPanel(
                        modifier = Modifier.weight(0.60f).fillMaxWidth(),
                        weather = weather,
                        language = language,
                        currentTimeMillis = currentTimeMillis,
                        timeZone = zone,
                        glass = glass,
                        outline = outline,
                        gold = gold
                    )
                    WeatherPrayerTimesPanel(
                        modifier = Modifier.weight(0.40f).fillMaxWidth(),
                        prayerTimes = prayerTimes,
                        city = weather.city,
                        activeKey = activeKey,
                        language = language,
                        glass = glass,
                        outline = outline,
                        gold = gold
                    )
                }
                WeatherCurrentDetailsPanel(
                    modifier = Modifier.weight(0.38f).fillMaxHeight(),
                    weather = weather,
                    hour = hour,
                    timeText = timeText,
                    dateText = dateText,
                    prayerTimes = prayerTimes,
                    language = language,
                    nextPrayerSummary = nextPrayerSummary,
                    onOpenAudioSettings = { showAudioSettings = true },
                    onRefresh = { if (weather.needsPermission && !weather.isManualLocation) onRequestLocation() else onRefresh() },
                    onChooseCity = {
                        cityInput = weather.city.substringBefore("،").substringBefore(",")
                        showCityPicker = true
                    },
                    onUseDeviceLocation = onUseDeviceLocation,
                    isPhone = false
                )
            }
        }

        Row(
            Modifier.align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.18f))
                .clickable(onClick = onBack)
                .focusable()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.ArrowBack,
                null,
                tint = Color.White,
                modifier = Modifier.size(royalAdaptiveIconSize(17.dp))
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (language == "en") "Back" else if (language == "tr") "Geri" else "رجوع",
                color = Color.White,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun WeatherCurrentDetailsPanel(
    modifier: Modifier,
    weather: WeatherUiState,
    hour: Int,
    timeText: String,
    dateText: String,
    prayerTimes: PrayerTimesData?,
    language: String,
    nextPrayerSummary: String,
    onOpenAudioSettings: () -> Unit,
    onRefresh: () -> Unit,
    onChooseCity: () -> Unit,
    onUseDeviceLocation: () -> Unit,
    isPhone: Boolean
) {
    Column(
        modifier = modifier.padding(if (isPhone) 8.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherQuickAction(
                modifier = Modifier.weight(1f),
                title = if (language == "en") "Sounds" else if (language == "tr") "Sesler" else "الأصوات",
                icon = Icons.Rounded.Tune,
                onClick = onOpenAudioSettings
            )
            WeatherQuickAction(
                modifier = Modifier.weight(1f),
                title = if (language == "en") "Refresh" else if (language == "tr") "Yenile" else "تحديث",
                icon = Icons.Rounded.Refresh,
                onClick = onRefresh
            )
            WeatherQuickAction(
                modifier = Modifier.weight(1f),
                title = if (language == "en") "City" else if (language == "tr") "Şehir" else "المدينة",
                icon = Icons.Rounded.EditLocationAlt,
                onClick = onChooseCity
            )
            if (weather.isManualLocation || weather.needsPermission) {
                WeatherQuickAction(
                    modifier = Modifier.weight(1f),
                    title = if (language == "en") "My place" else if (language == "tr") "Konumum" else "موقعي",
                    icon = Icons.Rounded.LocationOn,
                    onClick = onUseDeviceLocation
                )
            }
        }

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(
                    Icons.Rounded.LocationOn,
                    null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(royalAdaptiveIconSize(19.dp))
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    weather.city.ifBlank { if (language == "en") "Current location" else if (language == "tr") "Geçerli konum" else "الموقع الحالي" },
                    color = Color.White,
                    fontSize = if (isPhone) 19.sp else 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(timeText, color = Color.White.copy(alpha = 0.92f), fontSize = if (isPhone) 14.sp else 17.sp)
            Text(
                prayerTimes?.gregorianDate ?: dateText,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = if (isPhone) 12.sp else 14.sp,
                maxLines = 1
            )
            if (!prayerTimes?.hijriDate.isNullOrBlank()) {
                Text(
                    prayerTimes?.hijriDate.orEmpty(),
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = if (isPhone) 12.sp else 14.sp,
                    maxLines = 1
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RoyalClassicWeatherIcon(
                weatherCode = weather.weatherCode,
                description = weather.description,
                apparentTemperatureC = weather.apparentTemperatureC,
                windSpeedKmh = weather.windSpeedKmh,
                hour = hour,
                modifier = Modifier.size(royalAdaptiveIconSize(if (isPhone) 74.dp else 108.dp, maxSize = 116.dp)),
                contentDescription = weather.description
            )
            Text(
                (weather.temperatureC?.toString() ?: "--") + "°",
                color = Color.White,
                fontSize = if (isPhone) 68.sp else 88.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1
            )
            Text(
                weather.description,
                color = Color.White,
                fontSize = if (isPhone) 17.sp else 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (nextPrayerSummary.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    nextPrayerSummary,
                    color = Color(0xFFFFD36A),
                    fontSize = if (isPhone) 12.sp else 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WeatherMetricCard(
                title = when (language) { "en" -> "Feels like"; "tr" -> "Hissedilen"; else -> "المحسوسة" },
                value = weather.apparentTemperatureC?.let { "$it°" } ?: "--",
                modifier = Modifier.weight(1f)
            )
            WeatherMetricCard(
                title = when (language) { "en" -> "Humidity"; "tr" -> "Nem"; else -> "الرطوبة" },
                value = weather.humidityPercent?.let { "$it%" } ?: "--",
                modifier = Modifier.weight(1f)
            )
            WeatherMetricCard(
                title = when (language) { "en" -> "Wind"; "tr" -> "Rüzgâr"; else -> "الرياح" },
                value = weather.windSpeedKmh?.let { "$it km/h" } ?: "--",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun WeatherQuickAction(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier.clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 5.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color(0xFFFFD36A), modifier = Modifier.size(royalAdaptiveIconSize(14.dp)))
        Spacer(Modifier.width(3.dp))
        Text(title, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WeatherForecastPanel(
    modifier: Modifier,
    weather: WeatherUiState,
    language: String,
    currentTimeMillis: Long,
    timeZone: TimeZone,
    glass: Color,
    outline: Color,
    gold: Color
) {
    val locale = when (language) {
        "en" -> Locale.ENGLISH
        "tr" -> Locale("tr")
        else -> Locale("ar")
    }
    val todayDate = remember(currentTimeMillis, weather.timeZoneId) {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { this.timeZone = timeZone }
            .format(Date(currentTimeMillis))
    }
    val tomorrowDate = remember(currentTimeMillis, weather.timeZoneId) {
        val tomorrow = java.util.Calendar.getInstance(timeZone).apply {
            timeInMillis = currentTimeMillis
            add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { this.timeZone = timeZone }
            .format(tomorrow.time)
    }
    val days = weather.forecast.take(7)
    val weeklyMin = days.minOfOrNull { it.minTemperatureC } ?: 0
    val weeklyMax = days.maxOfOrNull { it.maxTemperatureC } ?: 1

    Column(
        modifier.clip(RoundedCornerShape(25.dp))
            .background(glass)
            .border(1.5.dp, outline, RoundedCornerShape(25.dp))
            .padding(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                when (language) { "en" -> "7-day forecast"; "tr" -> "7 günlük hava durumu"; else -> "توقعات 7 أيام" },
                color = Color.White,
                fontSize = if (weather.forecast.size > 5) 17.sp else 19.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(7.dp))
            Icon(Icons.Rounded.LocationOn, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(royalAdaptiveIconSize(17.dp)))
        }
        Spacer(Modifier.height(5.dp))
        if (days.isEmpty()) {
            repeat(7) {
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (weather.isLoading) "…" else "—",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            days.forEachIndexed { index, day ->
                val dayLabel = remember(day.date, language, todayDate, tomorrowDate) {
                    when (day.date) {
                        todayDate -> when (language) { "en" -> "Today"; "tr" -> "Bugün"; else -> "اليوم" }
                        tomorrowDate -> when (language) { "en" -> "Tomorrow"; "tr" -> "Yarın"; else -> "غداً" }
                        else -> try {
                            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                                this.timeZone = timeZone
                            }.parse(day.date)
                            SimpleDateFormat("EEE", locale).apply { this.timeZone = timeZone }
                                .format(parsed ?: Date())
                        } catch (_: Exception) { day.date }
                    }
                }
                WeatherForecastDayRow(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    day = day,
                    dayLabel = dayLabel,
                    language = language,
                    weeklyMin = weeklyMin,
                    weeklyMax = weeklyMax,
                    gold = gold
                )
                if (index < days.lastIndex) {
                    Box(
                        Modifier.fillMaxWidth().height(1.dp)
                            .background(Color.White.copy(alpha = 0.18f))
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherForecastDayRow(
    modifier: Modifier,
    day: WeatherForecastDay,
    dayLabel: String,
    language: String,
    weeklyMin: Int,
    weeklyMax: Int,
    gold: Color
) {
    Row(
        modifier.padding(horizontal = 2.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            dayLabel,
            modifier = Modifier.weight(0.85f),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        RoyalClassicWeatherIcon(
            weatherCode = day.weatherCode,
            description = weatherDescriptionFor(day.weatherCode, language),
            apparentTemperatureC = (day.maxTemperatureC + day.minTemperatureC) / 2,
            hour = 12,
            modifier = Modifier.size(royalAdaptiveIconSize(34.dp, maxSize = 38.dp)),
            contentDescription = weatherDescriptionFor(day.weatherCode, language)
        )
        Text(
            weatherDescriptionFor(day.weatherCode, language),
            modifier = Modifier.weight(1.05f),
            color = Color.White.copy(alpha = 0.95f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Column(
            Modifier.weight(1.1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                "${day.maxTemperatureC}° / ${day.minTemperatureC}°",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            WeatherTemperatureRangeBar(
                minTemperature = day.minTemperatureC,
                maxTemperature = day.maxTemperatureC,
                weeklyMin = weeklyMin,
                weeklyMax = weeklyMax,
                gold = gold,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WeatherTemperatureRangeBar(
    minTemperature: Int,
    maxTemperature: Int,
    weeklyMin: Int,
    weeklyMax: Int,
    gold: Color,
    modifier: Modifier = Modifier
) {
    val totalRange = (weeklyMax - weeklyMin).coerceAtLeast(1).toFloat()
    val start = ((minTemperature - weeklyMin) / totalRange).coerceIn(0f, 0.94f)
    val end = ((maxTemperature - weeklyMin) / totalRange).coerceIn(start + 0.04f, 1f)
    val segment = (end - start).coerceIn(0.04f, 1f - start)
    val remainder = (1f - start - segment).coerceAtLeast(0f)
    Row(
        modifier.height(4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.24f))
    ) {
        if (start > 0f) Spacer(Modifier.weight(start))
        Box(
            Modifier.weight(segment)
                .fillMaxHeight()
                .background(gold)
        )
        if (remainder > 0f) Spacer(Modifier.weight(remainder))
    }
}

@Composable
private fun WeatherPrayerTimesPanel(
    modifier: Modifier,
    prayerTimes: PrayerTimesData?,
    city: String,
    activeKey: String?,
    language: String,
    glass: Color,
    outline: Color,
    gold: Color
) {
    val prayerKeys = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
    Column(
        modifier.clip(RoundedCornerShape(25.dp))
            .background(glass)
            .border(1.5.dp, outline, RoundedCornerShape(25.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Mosque,
                    null,
                    tint = gold,
                    modifier = Modifier.size(royalAdaptiveIconSize(19.dp))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    when (language) { "en" -> "Prayer times"; "tr" -> "Namaz vakitleri"; else -> "مواقيت الصلاة" },
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    city,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Rounded.LocationOn,
                    null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(royalAdaptiveIconSize(14.dp))
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            prayerKeys.forEach { key ->
                val isActive = key == activeKey
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            if (isActive) Color(0x667C6530)
                            else Color.White.copy(alpha = 0.12f)
                        )
                        .border(
                            if (isActive) 2.dp else 1.dp,
                            if (isActive) gold else Color.White.copy(alpha = 0.26f),
                            RoundedCornerShape(13.dp)
                        )
                        .padding(horizontal = 3.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        prayerNameForWeather(key, language),
                        color = if (isActive) gold else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        prayerTimes?.times?.get(key) ?: "--:--",
                        color = if (isActive) gold else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun prayerNameForWeather(key: String, language: String): String = when (key) {
    "Fajr" -> when (language) { "en" -> "Fajr"; "tr" -> "Sabah"; else -> "الفجر" }
    "Sunrise" -> when (language) { "en" -> "Sunrise"; "tr" -> "Güneş"; else -> "الشروق" }
    "Dhuhr" -> when (language) { "en" -> "Dhuhr"; "tr" -> "Öğle"; else -> "الظهر" }
    "Asr" -> when (language) { "en" -> "Asr"; "tr" -> "İkindi"; else -> "العصر" }
    "Maghrib" -> when (language) { "en" -> "Maghrib"; "tr" -> "Akşam"; else -> "المغرب" }
    else -> when (language) { "en" -> "Isha"; "tr" -> "Yatsı"; else -> "العشاء" }
}

private fun weatherDescriptionFor(code: Int, language: String): String = when (code) {
    0 -> if (language == "en") "Clear" else if (language == "tr") "Açık" else "صحو"
    1 -> if (language == "en") "Mainly clear" else if (language == "tr") "Az bulutlu" else "صحو غالباً"
    2 -> if (language == "en") "Partly cloudy" else if (language == "tr") "Parçalı bulutlu" else "غائم جزئياً"
    3 -> if (language == "en") "Overcast" else if (language == "tr") "Kapalı" else "غائم كلياً"
    45, 48 -> if (language == "en") "Fog" else if (language == "tr") "Sis" else "ضباب"
    51, 53, 55 -> if (language == "en") "Drizzle" else if (language == "tr") "Çisenti" else "رذاذ"
    56, 57 -> if (language == "en") "Freezing drizzle" else if (language == "tr") "Donan çisenti" else "رذاذ متجمد"
    61, 63, 80, 81 -> if (language == "en") "Rain" else if (language == "tr") "Yağmur" else "مطر"
    65, 82 -> if (language == "en") "Heavy rain" else if (language == "tr") "Kuvvetli yağmur" else "أمطار غزيرة"
    66, 67 -> if (language == "en") "Freezing rain" else if (language == "tr") "Donan yağmur" else "مطر متجمد"
    71, 73, 77, 85 -> if (language == "en") "Snow" else if (language == "tr") "Kar" else "ثلج"
    75, 86 -> if (language == "en") "Heavy snow" else if (language == "tr") "Yoğun kar" else "ثلوج كثيفة"
    95 -> if (language == "en") "Thunderstorm" else if (language == "tr") "Gök gürültülü fırtına" else "عاصفة رعدية"
    96, 99 -> if (language == "en") "Thunderstorm with hail" else if (language == "tr") "Dolu ve gök gürültülü fırtına" else "عاصفة رعدية مع بَرَد"
    else -> if (language == "en") "Weather" else if (language == "tr") "Hava" else "الطقس"
}

@Composable
private fun WeatherMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(12.dp))
            .padding(horizontal = 7.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(title, color = Color.White.copy(alpha = 0.82f), fontSize = 10.sp, maxLines = 1)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}


@Composable
private fun WeatherAudioToggleRow(
    title: String,
    language: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF080D15))
            .border(1.dp, Color(0x66FFD36A), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(
            if (enabled) {
                when (language) { "en" -> "ON"; "tr" -> "Açık"; else -> "مفعّل" }
            } else {
                when (language) { "en" -> "OFF"; "tr" -> "Kapalı"; else -> "متوقف" }
            },
            color = if (enabled) Color(0xFFFFD36A) else Color(0xFF9299A5),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
