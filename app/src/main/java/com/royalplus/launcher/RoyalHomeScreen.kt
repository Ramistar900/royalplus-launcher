package com.royalplus.launcher

import com.royal.adaptive.LocalRoyalAdaptiveProfile
import com.royal.adaptive.RoyalWindowHeightClass
import com.royal.adaptive.RoyalWindowWidthClass
import com.royalplus.launcher.royalsounds.RoyalUiSound

import androidx.activity.compose.BackHandler
import android.os.Build

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private enum class LauncherDestination {
    HOME, APPS, MANAGE_APPS, MUSLIM, CALENDAR, SCREEN_CAST, SINJAR_TV, SPORTS, GALLERY, SONGS, FILES, FAVORITES, SHORTCUTS, MORE
}

@Composable
internal fun RoyalHomeScreen(
    apps: List<LauncherApp>,
    weather: WeatherUiState,
    onRequestLocation: () -> Unit,
    onRefreshWeather: () -> Unit,
    onRefreshApps: () -> Unit,
    onChooseWeatherCity: (WeatherCitySuggestion) -> Unit,
    onChooseWeatherCityByName: (String) -> Unit,
    onUseDeviceLocation: () -> Unit,
    onLaunchApp: (LauncherApp) -> Unit,
    onOpenSettings: () -> Unit,
    isDefaultHome: Boolean,
    onChooseDefaultHome: () -> Unit,
    onInstallApk: () -> Unit,
    onManageApp: (LauncherApp) -> Unit,
    onUninstallApp: (LauncherApp) -> Unit,
    onVoiceSearch: () -> Unit,
    voiceSearchAvailable: Boolean,
    voiceSearchResult: String,
    translationOverlayEnabled: Boolean,
    translationIsTelevision: Boolean,
    onToggleTranslationOverlay: () -> Unit
) {
    val adaptiveProfile = LocalRoyalAdaptiveProfile.current
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var prayerTimesForHome by remember { mutableStateOf<PrayerTimesData?>(null) }
    var prayerTimeBanner by remember { mutableStateOf<String?>(null) }
    var prayerBannerUntil by remember { mutableLongStateOf(0L) }
    var lastPrayerAlertKey by remember { mutableStateOf<String?>(null) }
    var activeAdhanPrayer by remember { mutableStateOf<String?>(null) }
    var activeAdhanCity by remember { mutableStateOf("") }
    var selectedDestination by remember { mutableStateOf(LauncherDestination.HOME) }
    var appForManagement by remember { mutableStateOf<LauncherApp?>(null) }
    var focusedAppPackage by remember { mutableStateOf<String?>(null) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val pressedRemoteKeys = remember { mutableSetOf<Key>() }
    var showSmartScreensaver by remember { mutableStateOf(false) }
    var lastUserInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(voiceSearchResult) {
        if (voiceSearchResult.isNotBlank()) {
            searchQuery = voiceSearchResult
            searchActive = true
            selectedDestination = LauncherDestination.APPS
        }
    }
    var showWallpaperSettings by remember { mutableStateOf(false) }
    val settingsInitialFocusRequester = remember { FocusRequester() }
    val settingsScrollState = rememberScrollState()
    var selectedSettingsCategory by remember { mutableStateOf("prayer") }
    val navigateSettingsSection: (String) -> Unit = { section -> selectedSettingsCategory = section }
    LaunchedEffect(selectedSettingsCategory) { settingsScrollState.scrollTo(0) }
    LaunchedEffect(showWallpaperSettings) {
        if (showWallpaperSettings) {
            withFrameNanos { }
            settingsInitialFocusRequester.requestFocus()
        }
    }
    var showWeatherDetails by remember { mutableStateOf(false) }
    // Weather ambience is allowed only while the dedicated Weather Details screen is open.
    // Hard-stop stale players whenever the user returns home or opens the smart screensaver.
    LaunchedEffect(showWeatherDetails, showSmartScreensaver) {
        if (!showWeatherDetails || showSmartScreensaver) {
            RoyalWeatherAmbienceController.onBackground()
        }
    }
    var showQuickControls by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val wallpaperPreferences = remember(context) { context.getSharedPreferences("royalplus_launcher_preferences", android.content.Context.MODE_PRIVATE) }
    var masterNatureSoundsEnabled by remember(wallpaperPreferences) {
        mutableStateOf(wallpaperPreferences.getBoolean(RoyalWeatherAmbienceController.MASTER_ENABLED_KEY, true))
    }
    DisposableEffect(wallpaperPreferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            if (key == RoyalWeatherAmbienceController.MASTER_ENABLED_KEY) {
                masterNatureSoundsEnabled = preferences.getBoolean(
                    RoyalWeatherAmbienceController.MASTER_ENABLED_KEY,
                    true
                )
            }
        }
        wallpaperPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { wallpaperPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var clickSoundsEnabled by remember {
        mutableStateOf(wallpaperPreferences.getBoolean("ui_click_sounds_enabled", false))
    }
    var lakeWaterSoundEnabled by remember {
        mutableStateOf(wallpaperPreferences.getBoolean("lake_water_sound_enabled", true))
    }
    var fireCrackleSoundEnabled by remember {
        mutableStateOf(wallpaperPreferences.getBoolean("fire_crackle_sound_enabled", true))
    }
    var fireAnimationEnabled by remember {
        mutableStateOf(wallpaperPreferences.getBoolean("fireplace_animation_enabled", true))
    }
    var oceanAnimationEnabled by remember {
        mutableStateOf(wallpaperPreferences.getBoolean("ocean_background_animation_enabled", true))
    }
    var syriaFlagAnimationEnabled by remember {
        mutableStateOf(wallpaperPreferences.getBoolean("syria_flag_animation_enabled", true))
    }
    var selectedWallpaper by remember { mutableStateOf(wallpaperPreferences.getString("launcher_wallpaper", "color") ?: "color") }
    var selectedScreensaverWallpaper by remember {
        mutableStateOf(wallpaperPreferences.getString("screensaver_wallpaper", "default") ?: "default")
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val shouldPlayLakeWaterSound = masterNatureSoundsEnabled && lakeWaterSoundEnabled &&
        (if (showSmartScreensaver) selectedScreensaverWallpaper == "lake" else selectedWallpaper == "lake")
    val shouldPlayFireCrackleSound = masterNatureSoundsEnabled && fireCrackleSoundEnabled &&
        (if (showSmartScreensaver) selectedScreensaverWallpaper == "fire" else selectedWallpaper == "fire")
    val latestLakeWaterSoundState by rememberUpdatedState(shouldPlayLakeWaterSound)
    val latestFireCrackleSoundState by rememberUpdatedState(shouldPlayFireCrackleSound)
    LaunchedEffect(shouldPlayLakeWaterSound) {
        RoyalLakeAmbienceController.sync(context, shouldPlayLakeWaterSound)
    }
    LaunchedEffect(shouldPlayFireCrackleSound) {
        RoyalFireAmbienceController.sync(context, shouldPlayFireCrackleSound)
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    RoyalLakeAmbienceController.sync(context, latestLakeWaterSoundState)
                    RoyalFireAmbienceController.sync(context, latestFireCrackleSoundState)
                }
                Lifecycle.Event.ON_STOP -> {
                    RoyalLakeAmbienceController.stop()
                    RoyalFireAmbienceController.stop()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            RoyalLakeAmbienceController.sync(context, latestLakeWaterSoundState)
            RoyalFireAmbienceController.sync(context, latestFireCrackleSoundState)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            RoyalLakeAmbienceController.stop()
            RoyalFireAmbienceController.stop()
        }
    }
    var screensaverMotionEnabled by remember {
        mutableStateOf(wallpaperPreferences.getBoolean("motion_screensaver", true))
    }
    var screensaverDelaySeconds by remember {
        mutableStateOf(wallpaperPreferences.getInt("screensaver_delay_seconds", 30).coerceIn(0, 300))
    }
    var selectedIconStyle by remember { mutableStateOf(wallpaperPreferences.getString("launcher_icon_style", "rounded") ?: "rounded") }
    var use24HourClock by remember { mutableStateOf(wallpaperPreferences.getBoolean("launcher_24_hour_clock", true)) }
    var showDateOnHome by remember { mutableStateOf(wallpaperPreferences.getBoolean("launcher_show_date", true)) }
    var isAdhanEnabled by remember { mutableStateOf(wallpaperPreferences.getBoolean("adhan_enabled", true)) }
    var homeMotionEnabled by remember { mutableStateOf(wallpaperPreferences.getBoolean("motion_home", true)) }
    var adhanMotionEnabled by remember { mutableStateOf(wallpaperPreferences.getBoolean("motion_adhan", true)) }
    var weatherMotionEnabled by remember { mutableStateOf(wallpaperPreferences.getBoolean("motion_weather", true)) }
    var selectedLanguage by remember { mutableStateOf(wallpaperPreferences.getString("launcher_language", "ar") ?: "ar") }
    var isSinjarPlayerFullscreen by remember { mutableStateOf(false) }
    var selectedFont by remember { mutableStateOf(wallpaperPreferences.getString("launcher_font", "cairo") ?: "cairo") }
    var lightThemeEnabled by remember(wallpaperPreferences) {
        mutableStateOf(wallpaperPreferences.getString("launcher_theme_mode", "dark") == "light")
    }
    var launcherTextScale by remember(wallpaperPreferences) {
        mutableStateOf(wallpaperPreferences.getFloat("launcher_text_scale", 1.0f).coerceIn(0.9f, 1.15f))
    }
    val selectedFontFamily = remember(selectedFont) {
        when (selectedFont) {
            "tajawal" -> FontFamily(Font(R.font.tajawal_regular))
            "kufi" -> FontFamily(Font(R.font.noto_kufi_arabic))
            "amiri" -> FontFamily(Font(R.font.amiri_regular))
            "ruqaa" -> FontFamily(Font(R.font.aref_ruqaa_regular))
            else -> FontFamily(Font(R.font.cairo))
        }
    }
    var selectedShortcutPackages by remember {
        mutableStateOf(wallpaperPreferences.getStringSet("launcher_shortcut_packages", emptySet())?.toSet() ?: emptySet())
    }
    var favoritePackageOrder by remember(apps) {
        mutableStateOf(
            wallpaperPreferences.getString("launcher_favorite_order", null)
                ?.split("|")?.filter { it.isNotBlank() }
                ?: buildFavoriteApps(apps).map { it.packageName }
        )
    }
    var hiddenPackages by remember(wallpaperPreferences) {
        mutableStateOf(wallpaperPreferences.getStringSet("launcher_hidden_packages", emptySet())?.toSet() ?: emptySet())
    }

    BackHandler(
        enabled = showWallpaperSettings || showWeatherDetails || showQuickControls || searchActive || selectedDestination != LauncherDestination.HOME
    ) {
        playLauncherUiSound(context, RoyalUiSound.NAV_BACK)
        when {
            showQuickControls -> showQuickControls = false
            showWallpaperSettings -> showWallpaperSettings = false
            showWeatherDetails -> showWeatherDetails = false
            searchActive -> {
                searchActive = false
                searchQuery = ""
                selectedDestination = LauncherDestination.HOME
            }
            selectedDestination != LauncherDestination.HOME -> {
                selectedDestination = LauncherDestination.HOME
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30L * 60L * 1_000L)
            onRefreshWeather()
        }
    }

    LaunchedEffect(lastUserInteraction, showWallpaperSettings, showQuickControls, searchActive, selectedDestination, screensaverDelaySeconds) {
        if (screensaverDelaySeconds > 0 && !showSmartScreensaver &&
            !showWallpaperSettings && !showQuickControls && !searchActive && selectedDestination == LauncherDestination.HOME
        ) {
            val delayMillis = screensaverDelaySeconds * 1_000L
            delay(delayMillis)
            if (System.currentTimeMillis() - lastUserInteraction >= delayMillis &&
                !showWallpaperSettings && !searchActive && selectedDestination == LauncherDestination.HOME
            ) {
                showSmartScreensaver = true
            }
        }
    }

    LaunchedEffect(weather.latitude, weather.longitude, weather.city, weather.timeZoneId) {
        prayerTimesForHome = null
        val savedPrayerLocation = RoyalAdhanScheduler.getLocation(context)
        val latitude = if (savedPrayerLocation?.isManual == true) savedPrayerLocation.latitude else weather.latitude
        val longitude = if (savedPrayerLocation?.isManual == true) savedPrayerLocation.longitude else weather.longitude
        val city = if (savedPrayerLocation?.isManual == true) savedPrayerLocation.city else weather.city
        val timeZoneId = if (savedPrayerLocation?.isManual == true) savedPrayerLocation.timeZoneId else weather.timeZoneId
        if (latitude != null && longitude != null) {
            PrayerTimesRepository.fetch(
                latitude = latitude,
                longitude = longitude,
                city = city,
                timeZoneId = timeZoneId
            ) { result ->
                prayerTimesForHome = result.takeIf { it.error == null && it.times.isNotEmpty() }
            }
        }
    }

    LaunchedEffect(isAdhanEnabled, weather.latitude, weather.longitude, weather.city, weather.timeZoneId, selectedLanguage) {
        RoyalAdhanScheduler.setEnabled(context, isAdhanEnabled, selectedLanguage)
        RoyalAdhanScheduler.ensureLocation(
            context = context,
            latitude = weather.latitude,
            longitude = weather.longitude,
            city = weather.city,
            timeZoneId = weather.timeZoneId
        )
    }

    LaunchedEffect(currentTime, prayerTimesForHome, weather.timeZoneId, weather.city, selectedLanguage) {
        val prayerData = prayerTimesForHome
        if (prayerData != null) {
            val zone = TimeZone.getTimeZone(weather.timeZoneId ?: TimeZone.getDefault().id)
            val clock = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = zone }.format(Date(currentTime))
            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }.format(Date(currentTime))
            val prayer = prayerData.times.entries.firstOrNull { (key, value) ->
                key != "Sunrise" && value == clock
            }
            if (prayer != null) {
                val alertKey = dateKey + ":" + prayer.key + ":" + weather.city
                if (lastPrayerAlertKey != alertKey) {
                    lastPrayerAlertKey = alertKey
                    if (isAdhanEnabled) {
                        val prayerName = when (selectedLanguage) {
                            "en" -> when (prayer.key) {
                                "Fajr" -> "Fajr"; "Dhuhr" -> "Dhuhr"; "Asr" -> "Asr"
                                "Maghrib" -> "Maghrib"; "Isha" -> "Isha"; else -> prayer.key
                            }
                            "tr" -> when (prayer.key) {
                                "Fajr" -> "Sabah"; "Dhuhr" -> "Öğle"; "Asr" -> "İkindi"
                                "Maghrib" -> "Akşam"; "Isha" -> "Yatsı"; else -> prayer.key
                            }
                            else -> PrayerTimesRepository.displayNames()[prayer.key] ?: prayer.key
                        }
                        prayerTimeBanner = when (selectedLanguage) {
                            "en" -> "It's time for $prayerName prayer in ${weather.city}"
                            "tr" -> "${weather.city} için $prayerName namazı vakti"
                            else -> "حان وقت صلاة $prayerName حسب التوقيت المحلي لمدينة ${weather.city}"
                        }
                        prayerBannerUntil = currentTime + 3 * 60 * 1_000L
                        activeAdhanPrayer = prayerName
                        activeAdhanCity = weather.city
                    }
                }
            }
        }
        if (currentTime >= prayerBannerUntil) prayerTimeBanner = null
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30 * 60 * 1_000L)
            onRefreshWeather()
        }
    }

    val displayTimeZone = remember(weather.timeZoneId) {
        TimeZone.getTimeZone(weather.timeZoneId ?: TimeZone.getDefault().id)
    }
    val displayLocale = remember(selectedLanguage) { Locale.forLanguageTag(selectedLanguage) }
    val dateText = remember(currentTime, weather.timeZoneId, selectedLanguage) {
        SimpleDateFormat(if (selectedLanguage == "ar") "EEEE، d MMMM yyyy" else "EEEE, d MMMM yyyy", displayLocale).apply {
            timeZone = displayTimeZone
        }.format(Date(currentTime))
    }
    val timeText = remember(currentTime, weather.timeZoneId, use24HourClock, selectedLanguage) {
        SimpleDateFormat(if (use24HourClock) "HH:mm" else "h:mm a", displayLocale).apply {
            timeZone = displayTimeZone
        }.format(Date(currentTime))
    }
    val visibleApps = remember(apps, hiddenPackages) {
        apps.filterNot { it.packageName in hiddenPackages }
    }
    val favorites = remember(visibleApps, favoritePackageOrder) {
        favoritePackageOrder.mapNotNull { packageName ->
            visibleApps.firstOrNull { it.packageName == packageName }
        }
    }
    val favoritePackages = remember(favorites) { favorites.map { it.packageName }.toSet() }
    val normalizedSearchQuery = remember(searchQuery) {
        LauncherAppSearch.normalize(searchQuery)
    }
    val filteredApps = remember(visibleApps, normalizedSearchQuery) {
        if (normalizedSearchQuery.isBlank()) {
            visibleApps
        } else {
            visibleApps.filter {
                LauncherAppSearch.normalize(it.label).contains(normalizedSearchQuery) ||
                    LauncherAppSearch.normalize(it.packageName).contains(normalizedSearchQuery)
            }
        }
    }
    val filteredFavorites = remember(favorites, normalizedSearchQuery) {
        if (normalizedSearchQuery.isBlank()) {
            favorites
        } else {
            favorites.filter {
                LauncherAppSearch.normalize(it.label).contains(normalizedSearchQuery) ||
                    LauncherAppSearch.normalize(it.packageName).contains(normalizedSearchQuery)
            }
        }
    }
    val otherApps = remember(filteredApps, favoritePackages) {
        filteredApps.filterNot { it.packageName in favoritePackages }
    }

    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(baseDensity.density, launcherTextScale),
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = selectedFontFamily)
    ) {
    Surface(modifier = Modifier.fillMaxSize(), color = RoyalColors.Black) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        lastUserInteraction = System.currentTimeMillis()
                        showSmartScreensaver = false

                        var deltaX = 0f
                        var deltaY = 0f
                        while (true) {
                            val pointerEvent = awaitPointerEvent()
                            val change = pointerEvent.changes.firstOrNull { it.id == down.id } ?: break
                            deltaX = change.position.x - down.position.x
                            deltaY = change.position.y - down.position.y
                            if (!change.pressed) break
                        }

                        val touchSlop = viewConfiguration.touchSlop
                        if (deltaX * deltaX + deltaY * deltaY >= touchSlop * touchSlop) {
                            if (kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY)) {
                                playLauncherUiSound(context, RoyalUiSound.NAV_SWIPE)
                            } else {
                                playLauncherUiSound(context, RoyalUiSound.NAV_SCROLL_TICK)
                            }
                        }
                    }
                }
                .onPreviewKeyEvent { event ->
                    val isKeyDown = event.type == KeyEventType.KeyDown
                    val isInitialPress = when (event.type) {
                        KeyEventType.KeyDown -> pressedRemoteKeys.add(event.key)
                        KeyEventType.KeyUp -> {
                            pressedRemoteKeys.remove(event.key)
                            false
                        }
                        else -> false
                    }

                    val handledRemoteKey = if (isKeyDown) {
                        when (RemoteKeys.fromComposeKey(event.key)) {
                            RemoteKey.MENU, RemoteKey.BLUE -> {
                                if (isInitialPress) {
                                    showQuickControls = !showQuickControls
                                    showSmartScreensaver = false
                                }
                                true
                            }
                            RemoteKey.SEARCH -> {
                                if (isInitialPress) {
                                    searchActive = true
                                    selectedDestination = LauncherDestination.APPS
                                    showQuickControls = false
                                }
                                true
                            }
                            RemoteKey.VOICE -> {
                                if (isInitialPress) {
                                    if (voiceSearchAvailable) onVoiceSearch()
                                    else {
                                        searchActive = true
                                        selectedDestination = LauncherDestination.APPS
                                    }
                                }
                                true
                            }
                            RemoteKey.RED -> {
                                if (isInitialPress) {
                                    showQuickControls = false
                                    showWallpaperSettings = true
                                }
                                true
                            }
                            RemoteKey.GREEN -> {
                                if (isInitialPress) onOpenSettings()
                                true
                            }
                            RemoteKey.YELLOW -> {
                                if (isInitialPress) onRefreshApps()
                                true
                            }
                            // BACK remains owned by BackHandler. Other media/channel keys
                            // are not consumed by the launcher.
                            else -> false
                        }
                    } else {
                        false
                    }

                    lastUserInteraction = System.currentTimeMillis()
                    showSmartScreensaver = false
                    handledRemoteKey
                }
        ) {
            RoyalBackdrop(
                modifier = Modifier.matchParentSize(),
                selectedWallpaper = selectedWallpaper,
                motionEnabled = homeMotionEnabled,
                oceanAnimationEnabled = oceanAnimationEnabled,
                syriaFlagAnimationEnabled = syriaFlagAnimationEnabled,
                fireAnimationEnabled = fireAnimationEnabled
            )
            appForManagement?.let { app ->
                val title = when (selectedLanguage) {
                    "en" -> "Manage ${app.label}"
                    "tr" -> "${app.label} uygulamasını yönet"
                    else -> "إدارة ${app.label}"
                }
                val infoLabel = when (selectedLanguage) { "en" -> "App info"; "tr" -> "Uygulama bilgileri"; else -> "معلومات التطبيق" }
                val uninstallLabel = when (selectedLanguage) { "en" -> "Uninstall"; "tr" -> "Kaldır"; else -> "إلغاء التثبيت" }
                val cancelLabel = when (selectedLanguage) { "en" -> "Cancel"; "tr" -> "İptal"; else -> "إلغاء" }
                AlertDialog(
                    onDismissRequest = { appForManagement = null },
                    title = { Text(title, color = RoyalColors.Pearl) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(infoLabel, color = RoyalColors.Gold, modifier = Modifier
                                .fillMaxWidth().clickable {
                                    appForManagement = null
                                    onManageApp(app)
                                }.padding(vertical = 10.dp))
                            Text(uninstallLabel, color = Color(0xFFFF8A80), modifier = Modifier
                                .fillMaxWidth().clickable {
                                    appForManagement = null
                                    onUninstallApp(app)
                                }.padding(vertical = 10.dp))
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { appForManagement = null }) {
                            Text(cancelLabel, color = RoyalColors.Gold)
                        }
                    },
                    containerColor = Color(0xFF11151D),
                    titleContentColor = RoyalColors.Pearl,
                    textContentColor = RoyalColors.Pearl
                )
            }
            val compactPhoneLayout = adaptiveProfile.isPhone && maxWidth < 600.dp
            val sidebarWidth = when {
                compactPhoneLayout -> 64.dp
                maxWidth >= 900.dp -> 188.dp
                else -> (maxWidth * 0.205f).coerceIn(120.dp, 188.dp)
            }
            CompositionLocalProvider(LocalLayoutDirection provides if (selectedLanguage == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Column(Modifier.fillMaxSize().then(if (isSinjarPlayerFullscreen) Modifier else Modifier.padding(horizontal = adaptiveProfile.pageHorizontalPadding, vertical = adaptiveProfile.pageVerticalPadding))) {
                    if (!isSinjarPlayerFullscreen) {
                    RoyalBrandHeader(
                        searchActive = searchActive, searchQuery = searchQuery,
                        currentTime = timeText, dateText = dateText, showDate = showDateOnHome,
                        onQueryChanged = { searchQuery = it },
                        onCloseSearch = { searchActive = false; searchQuery = ""; selectedDestination = LauncherDestination.HOME },
                        onOpenSettings = { showWallpaperSettings = true },
                        onOpenQuickControls = { showQuickControls = true; showSmartScreensaver = false },
                        onSearch = { searchActive = true; selectedDestination = LauncherDestination.APPS },
                        onInstallApk = onInstallApk,
                        onVoiceSearch = onVoiceSearch,
                        voiceSearchAvailable = voiceSearchAvailable,
                        translationOverlayEnabled = translationOverlayEnabled,
                        translationIsTelevision = translationIsTelevision,
                        onToggleTranslationOverlay = onToggleTranslationOverlay,
                        selectedLanguage = selectedLanguage
                    )
                    }
                    if (selectedDestination == LauncherDestination.HOME && prayerTimeBanner != null) {
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF30240F))
                                .border(1.dp, RoyalColors.Gold, RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(royalAdaptiveIconSize(22.dp)))
                            Text(
                                prayerTimeBanner.orEmpty(),
                                color = RoyalColors.Pearl,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    if (!isSinjarPlayerFullscreen) Spacer(Modifier.height(9.dp))
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (isSinjarPlayerFullscreen) 0.dp else 14.dp)) {
                        if (!isSinjarPlayerFullscreen) RoyalSidePanel(
                            modifier = Modifier.width(sidebarWidth).fillMaxHeight(),
                            selectedDestination = selectedDestination,
                            language = selectedLanguage,
                            onNavigate = { destination ->
                                selectedDestination = destination
                                if (destination == LauncherDestination.HOME) { searchActive = false; searchQuery = "" }
                            }
                        )
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val columns = adaptiveProfile.launcherAppColumns(maxWidth)
                                val favoriteTileWidth = ((maxWidth - 16.dp - adaptiveProfile.itemSpacing * 5f) / 6)
                                    .coerceIn(82.dp, if (adaptiveProfile.isTelevision) 168.dp else 124.dp)
                                when (selectedDestination) {
                                    LauncherDestination.HOME -> HomeDashboard(
                                        favorites = favorites, shortcuts = apps.filter { it.packageName in selectedShortcutPackages },
                                        otherApps = otherApps, columns = columns, favoriteTileWidth = favoriteTileWidth,
                                        focusedAppPackage = focusedAppPackage, onAppFocused = { focusedAppPackage = it },
                                        onLaunchApp = onLaunchApp, onManageApp = { appForManagement = it }, iconStyle = selectedIconStyle,
                                        currentTime = timeText, weather = weather,
                                        onRequestLocation = onRequestLocation,
                                        onNavigate = { selectedDestination = it },
                                        onRefreshWeather = onRefreshWeather,
                                        onOpenWeather = { showWeatherDetails = true },
                                        onOpenSettings = { showWallpaperSettings = true },
                                        selectedLanguage = selectedLanguage,
                                        weatherMotionEnabled = weatherMotionEnabled,
                                        prayerTimes = prayerTimesForHome,
                                        currentTimeMillis = currentTime
                                    )
                                    LauncherDestination.APPS -> AppCatalog(
                                        title = if (searchQuery.isBlank()) when (selectedLanguage) { "en" -> "All apps"; "tr" -> "Tüm uygulamalar"; else -> "جميع التطبيقات" } else when (selectedLanguage) { "en" -> "Search results"; "tr" -> "Arama sonuçları"; else -> "نتائج البحث" },
                                        apps = filteredApps, columns = columns, focusedAppPackage = focusedAppPackage,
                                        onAppFocused = { focusedAppPackage = it }, onLaunchApp = onLaunchApp,
                                        onManageApp = { appForManagement = it }, iconStyle = selectedIconStyle, initialHighlight = true
                                    )
                                    LauncherDestination.MANAGE_APPS -> RoyalAppManagementScreen(
                                        apps = apps,
                                        favoritePackages = favoritePackageOrder.toSet(),
                                        hiddenPackages = hiddenPackages,
                                        columns = columns,
                                        language = selectedLanguage,
                                        onToggleFavorite = { app ->
                                            val updated = favoritePackageOrder.toMutableList()
                                            if (!updated.remove(app.packageName)) updated.add(app.packageName)
                                            favoritePackageOrder = updated
                                            wallpaperPreferences.edit().putString("launcher_favorite_order", updated.joinToString("|")).apply()
                                        },
                                        onToggleHidden = { app ->
                                            val updated = hiddenPackages.toMutableSet()
                                            if (!updated.add(app.packageName)) updated.remove(app.packageName)
                                            hiddenPackages = updated
                                            wallpaperPreferences.edit().putStringSet("launcher_hidden_packages", updated).apply()
                                        },
                                        onMoveFavorite = { app, direction ->
                                            val updated = favoritePackageOrder.toMutableList()
                                            val currentIndex = updated.indexOf(app.packageName)
                                            val targetIndex = currentIndex + direction
                                            if (currentIndex >= 0 && targetIndex in updated.indices) {
                                                val moved = updated.removeAt(currentIndex)
                                                updated.add(targetIndex, moved)
                                                favoritePackageOrder = updated
                                                wallpaperPreferences.edit().putString("launcher_favorite_order", updated.joinToString("|")).apply()
                                            }
                                        },
                                        onLaunchApp = onLaunchApp,
                                        onManageApp = { appForManagement = it }
                                    )
                                    LauncherDestination.MUSLIM -> MuslimScreen(weather = weather, onRequestLocation = onRequestLocation, language = selectedLanguage)
                                    LauncherDestination.CALENDAR -> GregorianCalendarScreen(language = selectedLanguage)
                                    LauncherDestination.SCREEN_CAST -> RoyalCastScreen(language = selectedLanguage)
                                    LauncherDestination.SINJAR_TV -> SinjarTvScreen(language = selectedLanguage, onFullscreenChange = { isSinjarPlayerFullscreen = it })
                                    LauncherDestination.SPORTS -> RoyalSportsScreen(language = selectedLanguage)
                                    LauncherDestination.GALLERY -> RoyalGalleryScreen()
                                    LauncherDestination.FILES -> RoyalFileManagerScreen(language = selectedLanguage)
                                    LauncherDestination.SONGS -> RoyalSongsScreen()
                                    LauncherDestination.FAVORITES -> AppCatalog(
                                        title = when (selectedLanguage) { "en" -> "Favorite apps"; "tr" -> "Favori uygulamalar"; else -> "التطبيقات المفضلة" }, apps = filteredFavorites, columns = columns,
                                        focusedAppPackage = focusedAppPackage, onAppFocused = { focusedAppPackage = it },
                                        onLaunchApp = onLaunchApp, onManageApp = { appForManagement = it }, iconStyle = selectedIconStyle, initialHighlight = true
                                    )
                                    LauncherDestination.SHORTCUTS -> ShortcutManagerScreen(
                                        apps = filteredApps, selectedPackages = selectedShortcutPackages, columns = columns,
                                        iconStyle = selectedIconStyle,
                                        onToggleShortcut = { app ->
                                            val updated = selectedShortcutPackages.toMutableSet().apply { if (!add(app.packageName)) remove(app.packageName) }.toSet()
                                            selectedShortcutPackages = updated
                                            wallpaperPreferences.edit().putStringSet("launcher_shortcut_packages", updated).apply()
                                        },
                                        onLaunchApp = onLaunchApp, onManageApp = { appForManagement = it }
                                    )
                                    LauncherDestination.MORE -> AppCatalog(
                                        title = when (selectedLanguage) { "en" -> "More apps"; "tr" -> "Daha fazla uygulama"; else -> "المزيد من التطبيقات" }, apps = otherApps, columns = columns,
                                        focusedAppPackage = focusedAppPackage, onAppFocused = { focusedAppPackage = it },
                                        onLaunchApp = onLaunchApp, onManageApp = { appForManagement = it }, iconStyle = selectedIconStyle, initialHighlight = true
                                    )
                                }
                            }
                        }
                    }
                    if (!isSinjarPlayerFullscreen) {
                        Spacer(Modifier.height(8.dp))
                        RoyalFooter(Modifier.fillMaxWidth().height(31.dp), currentTime = timeText, dateText = dateText, weather = weather)
                    }
                }
            }

            if (showQuickControls) {
                RoyalQuickControlCenter(
                    language = selectedLanguage,
                    onDismiss = { showQuickControls = false },
                    onOpenApps = { selectedDestination = LauncherDestination.APPS; searchActive = false; searchQuery = "" },
                    onOpenMuslim = { selectedDestination = LauncherDestination.MUSLIM },
                    onOpenGallery = { selectedDestination = LauncherDestination.GALLERY },
                    onOpenSongs = { selectedDestination = LauncherDestination.SONGS },
                    onOpenPersonalization = { showWallpaperSettings = true },
                    onRefreshWeather = onRefreshWeather
                )
            }

            if (showWeatherDetails) {
                WeatherDetailsScreen(weather = weather, prayerTimes = prayerTimesForHome, currentTimeMillis = currentTime, timeText = timeText, dateText = dateText, language = selectedLanguage, onBack = { showWeatherDetails = false }, onRefresh = onRefreshWeather, onRequestLocation = onRequestLocation, onChooseCity = onChooseWeatherCity, onChooseCityByName = onChooseWeatherCityByName, onUseDeviceLocation = onUseDeviceLocation, motionEnabled = weatherMotionEnabled)
            }
            if (showSmartScreensaver && !showWallpaperSettings && !showWeatherDetails && !showQuickControls) {
                RoyalSmartScreensaver(
                    timeText = timeText,
                    dateText = dateText,
                    weather = weather,
                    language = selectedLanguage,
                    selectedWallpaper = selectedScreensaverWallpaper,
                    motionEnabled = screensaverMotionEnabled && selectedScreensaverWallpaper != "fire",
                    oceanAnimationEnabled = oceanAnimationEnabled,
                    syriaFlagAnimationEnabled = syriaFlagAnimationEnabled,
                    fireAnimationEnabled = fireAnimationEnabled,
                    onDismiss = {
                        showSmartScreensaver = false
                        lastUserInteraction = System.currentTimeMillis()
                    }
                )
            }

            if (showWallpaperSettings) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(RoyalColors.Overlay)
                        .padding(horizontal = 28.dp, vertical = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.92f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(RoyalColors.Panel)
                            .border(1.dp, RoyalColors.Gold.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                            .padding(18.dp)
                            .focusGroup()
                    ) {
                        Column(
                            modifier = Modifier
                                .width(205.dp)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text("ROYAL+", color = RoyalColors.Gold, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text(
                                when (selectedLanguage) {
                                    "en" -> "SETTINGS"
                                    "tr" -> "AYARLAR"
                                    else -> "الإعدادات"
                                },
                                color = RoyalColors.Pearl,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            RoyalSettingsNavigationItem(Icons.Rounded.Palette, when (selectedLanguage) { "en" -> "Appearance"; "tr" -> "Görünüm"; else -> "مظهر الشاشة" }, selectedSettingsCategory == "appearance", focusRequester = settingsInitialFocusRequester) { navigateSettingsSection("appearance") }
                            RoyalSettingsNavigationItem(Icons.Rounded.Tune, when (selectedLanguage) { "en" -> "Backgrounds & motion"; "tr" -> "Arka plan ve hareket"; else -> "الخلفيات والحركة" }, selectedSettingsCategory == "motion") { navigateSettingsSection("motion") }
                            RoyalSettingsNavigationItem(Icons.Rounded.Apps, when (selectedLanguage) { "en" -> "Icons & launcher"; "tr" -> "Simgeler ve başlatıcı"; else -> "الأيقونات والواجهة" }, selectedSettingsCategory == "icons") { navigateSettingsSection("icons") }
                            RoyalSettingsNavigationItem(Icons.Rounded.MusicNote, when (selectedLanguage) { "en" -> "Sounds & effects"; "tr" -> "Sesler ve efektler"; else -> "الأصوات والمؤثرات" }, selectedSettingsCategory == "sounds") { navigateSettingsSection("sounds") }
                            RoyalSettingsNavigationItem(Icons.Rounded.NotificationsActive, when (selectedLanguage) { "en" -> "Prayer & Adhan"; "tr" -> "Namaz ve ezan"; else -> "الصلاة والأذان" }, selectedSettingsCategory == "prayer") { navigateSettingsSection("prayer") }
                            RoyalSettingsNavigationItem(Icons.Rounded.Schedule, when (selectedLanguage) { "en" -> "Date & time"; "tr" -> "Tarih ve saat"; else -> "التاريخ والساعة" }, selectedSettingsCategory == "clock") { navigateSettingsSection("clock") }
                            RoyalSettingsNavigationItem(Icons.Rounded.Language, when (selectedLanguage) { "en" -> "Language & fonts"; "tr" -> "Dil ve yazı tipleri"; else -> "اللغة والخطوط" }, selectedSettingsCategory == "language") { navigateSettingsSection("language") }
                            RoyalSettingsNavigationItem(Icons.Rounded.Wifi, when (selectedLanguage) { "en" -> "Network & device"; "tr" -> "Ağ ve cihaz"; else -> "الشبكة والجهاز" }, selectedSettingsCategory == "device") { navigateSettingsSection("device") }
                            RoyalSettingsNavigationItem(Icons.Rounded.WbSunny, "ROYAL RELAX", selectedSettingsCategory == "relax") { navigateSettingsSection("relax") }
                            RoyalSettingsNavigationItem(Icons.Rounded.Mic, when (selectedLanguage) { "en" -> "Privacy & permissions"; "tr" -> "Gizlilik ve izinler"; else -> "الخصوصية والأذونات" }, selectedSettingsCategory == "privacy") { navigateSettingsSection("privacy") }
                            RoyalSettingsNavigationItem(Icons.Rounded.Refresh, when (selectedLanguage) { "en" -> "Share & updates"; "tr" -> "Paylaşım ve güncellemeler"; else -> "مشاركة التطبيق والتحديثات" }, selectedSettingsCategory == "share") { navigateSettingsSection("share") }
                            RoyalSettingsNavigationItem(Icons.Rounded.Info, when (selectedLanguage) { "en" -> "About & support"; "tr" -> "Hakkında ve destek"; else -> "حول التطبيق والدعم" }, selectedSettingsCategory == "about") { navigateSettingsSection("about") }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(RoyalColors.Gold.copy(alpha = 0.15f))
                                    .border(1.dp, RoyalColors.Gold.copy(alpha = 0.62f), RoundedCornerShape(12.dp))
                                    .then(royalGlassFocus(RoundedCornerShape(12.dp)))
                                    .clickable { showWallpaperSettings = false }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(when (selectedLanguage) { "en" -> "Done"; "tr" -> "Tamam"; else -> "تم" }, color = RoyalColors.Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(settingsScrollState)
                                .focusGroup(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                        Text(
                            when (selectedSettingsCategory) {
                                "appearance" -> if (selectedLanguage == "en") "Appearance" else if (selectedLanguage == "tr") "Görünüm" else "مظهر الشاشة"
                                "motion" -> if (selectedLanguage == "en") "Backgrounds & motion" else if (selectedLanguage == "tr") "Arka plan ve hareket" else "الخلفيات والحركة"
                                "icons" -> if (selectedLanguage == "en") "Icons & launcher" else if (selectedLanguage == "tr") "Simgeler ve başlatıcı" else "الأيقونات والواجهة"
                                "sounds" -> if (selectedLanguage == "en") "Sounds & effects" else if (selectedLanguage == "tr") "Sesler ve efektler" else "الأصوات والمؤثرات"
                                "prayer" -> if (selectedLanguage == "en") "Prayer & Adhan" else if (selectedLanguage == "tr") "Namaz ve ezan" else "الصلاة والأذان"
                                "clock" -> if (selectedLanguage == "en") "Date & time" else if (selectedLanguage == "tr") "Tarih ve saat" else "التاريخ والساعة"
                                "language" -> if (selectedLanguage == "en") "Language & fonts" else if (selectedLanguage == "tr") "Dil ve yazı tipleri" else "اللغة والخطوط"
                                "device" -> if (selectedLanguage == "en") "Network & device" else if (selectedLanguage == "tr") "Ağ ve cihaz" else "الشبكة والجهاز"
                                "relax" -> "ROYAL RELAX"
                                "privacy" -> if (selectedLanguage == "en") "Privacy & permissions" else if (selectedLanguage == "tr") "Gizlilik ve izinler" else "الخصوصية والأذونات"
                                "share" -> if (selectedLanguage == "en") "Share & updates" else if (selectedLanguage == "tr") "Paylaşım ve güncellemeler" else "مشاركة التطبيق والتحديثات"
                                else -> if (selectedLanguage == "en") "About & support" else if (selectedLanguage == "tr") "Hakkında ve destek" else "حول التطبيق والدعم"
                            },
                            color = RoyalColors.Gold, fontSize = 25.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            when (selectedSettingsCategory) {
                                "appearance" -> if (selectedLanguage == "en") "Choose the home screen wallpaper" else if (selectedLanguage == "tr") "Ana ekran duvar kâğıdını seçin" else "اختر خلفية الشاشة الرئيسية"
                                "motion" -> if (selectedLanguage == "en") "Control movement and the smart screensaver" else if (selectedLanguage == "tr") "Hareketi ve akıllı ekran koruyucuyu yönetin" else "تحكم بحركة الخلفيات وشاشة التوقف الذكية"
                                "icons" -> if (selectedLanguage == "en") "Choose icon styles and your default TV launcher" else if (selectedLanguage == "tr") "Simge stillerini ve varsayılan TV başlatıcısını seçin" else "اختر شكل الأيقونات وواجهة التلفاز الافتراضية"
                                "sounds" -> if (selectedLanguage == "en") "Manage nature sounds and background effects" else if (selectedLanguage == "tr") "Doğa seslerini ve arka plan efektlerini yönetin" else "تحكم بأصوات الطبيعة ومؤثرات الخلفيات"
                                "prayer" -> if (selectedLanguage == "en") "Prayer notifications and precise alarms" else if (selectedLanguage == "tr") "Namaz bildirimleri ve hassas alarmlar" else "تنبيهات الصلاة والأذان الدقيق"
                                "clock" -> if (selectedLanguage == "en") "Choose how date and time appear" else if (selectedLanguage == "tr") "Tarih ve saatin görünümünü seçin" else "اختر طريقة عرض التاريخ والساعة"
                                "language" -> if (selectedLanguage == "en") "Choose the interface language and font" else if (selectedLanguage == "tr") "Arayüz dilini ve yazı tipini seçin" else "اختر لغة الواجهة ونوع الخط"
                                "device" -> if (selectedLanguage == "en") "Network, storage and device information" else if (selectedLanguage == "tr") "Ağ, depolama ve cihaz bilgileri" else "الشبكة والتخزين ومعلومات الجهاز"
                                "relax" -> if (selectedLanguage == "en") "Nature cameras, cozy fireplace videos and winter ambience" else if (selectedLanguage == "tr") "Doğa kameraları, şömine videoları ve kış atmosferi" else "كاميرات الطبيعة وفيديوهات المدفأة وأجواء الشتاء"
                                "privacy" -> if (selectedLanguage == "en") "Review app permissions and open Android privacy controls" else if (selectedLanguage == "tr") "Uygulama izinlerini inceleyin ve Android gizlilik ayarlarını açın" else "راجع أذونات التطبيق وافتح إعدادات الخصوصية في أندرويد"
                                "share" -> if (selectedLanguage == "en") "Share the app or check for the latest official release" else if (selectedLanguage == "tr") "Uygulamayı paylaşın veya en yeni resmî sürümü kontrol edin" else "شارك التطبيق أو تحقق من أحدث إصدار رسمي"
                                else -> if (selectedLanguage == "en") "App version, developer information and support links" else if (selectedLanguage == "tr") "Uygulama sürümü, geliştirici bilgileri ve destek bağlantıları" else "إصدار التطبيق ومعلومات المطوّر وروابط الدعم"
                            },
                            color = RoyalColors.Pearl, fontSize = 14.sp
                        )
                        val homeWallpapers = listOf(
                            Triple("color", if (selectedLanguage == "en") "Colorful" else if (selectedLanguage == "tr") "Renkli" else "ملونة", R.drawable.royal_color_wallpaper),
                            Triple("nature", if (selectedLanguage == "en") "Nature" else if (selectedLanguage == "tr") "Doğa" else "طبيعة", R.drawable.royal_nature_wallpaper),
                            Triple("forest", if (selectedLanguage == "en") "Forest" else if (selectedLanguage == "tr") "Orman" else "غابة", R.drawable.royal_wallpaper_forest_04),
                            Triple("green", if (selectedLanguage == "en") "Green" else if (selectedLanguage == "tr") "Yeşil" else "خضراء", R.drawable.royal_wallpaper_grreen_07),
                            Triple("lake", if (selectedLanguage == "en") "Lake" else if (selectedLanguage == "tr") "Göl" else "بحيرة", R.drawable.royal_wallpaper_lake_03),
                            Triple("mountains", if (selectedLanguage == "en") "Mountains" else if (selectedLanguage == "tr") "Dağlar" else "جبال", R.drawable.royal_wallpaper_mountains_02),
                            Triple("sunset", if (selectedLanguage == "en") "Sunset" else if (selectedLanguage == "tr") "Gün batımı" else "غروب", R.drawable.royal_wallpaper_sunset_05),
                            Triple("waterfall", if (selectedLanguage == "en") "Waterfall" else if (selectedLanguage == "tr") "Şelale" else "شلال", R.drawable.royal_wallpaper_waterfall_06),
                            Triple("city_night", if (selectedLanguage == "en") "Night city" else if (selectedLanguage == "tr") "Gece şehri" else "مدينة ليلية", R.drawable.royal_city_night),
                            Triple("space", if (selectedLanguage == "en") "Space" else if (selectedLanguage == "tr") "Uzay" else "الفضاء", R.drawable.royal_space),
                            Triple("rami_golden_nature", if (selectedLanguage == "en") "Rami's golden nature" else if (selectedLanguage == "tr") "Rami'nin altın doğası" else "طبيعة ذهبية من رامي", R.drawable.royal_rami_golden_nature),
                            Triple("mountain_nature", if (selectedLanguage == "en") "Mountain nature" else if (selectedLanguage == "tr") "Dağ doğası" else "طبيعة جبلية", R.drawable.royal_mountain_nature),
                            Triple("blue_gold_magic", if (selectedLanguage == "en") "Blue & gold magic" else if (selectedLanguage == "tr") "Mavi ve altın büyüsü" else "السحر الأزرق الذهبي", R.drawable.royal_blue_gold_magic),
                            Triple("fire", if (selectedLanguage == "en") "Fireplace" else if (selectedLanguage == "tr") "Şömine" else "المدفأة", R.drawable.royal_live_fire_4k),
                            Triple("ocean", if (selectedLanguage == "en") "Ocean waves" else if (selectedLanguage == "tr") "Okyanus" else "موج البحر", R.drawable.royal_ocean_4k),
                            Triple("syria", if (selectedLanguage == "en") "Syrian flag" else if (selectedLanguage == "tr") "Suriye bayrağı" else "علم سوريا", R.drawable.royal_syria_flag_4k)
                        )
                        if (selectedSettingsCategory == "prayer") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isAdhanEnabled) RoyalColors.Gold.copy(alpha = 0.14f) else RoyalColors.Surface)
                                .border(1.dp, RoyalColors.Gold.copy(alpha = 0.48f), RoundedCornerShape(12.dp))
                                .then(royalGlassFocus(RoundedCornerShape(12.dp)))
                                .clickable {
                                    isAdhanEnabled = !isAdhanEnabled
                                    wallpaperPreferences.edit().putBoolean("adhan_enabled", isAdhanEnabled).apply()
                                    RoyalAdhanScheduler.setEnabled(context, isAdhanEnabled, selectedLanguage)
                                    if (!isAdhanEnabled) {
                                        activeAdhanPrayer = null
                                        prayerTimeBanner = null
                                        prayerBannerUntil = 0L
                                    }
                                }
                                
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(royalAdaptiveIconSize(25.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    when (selectedLanguage) { "en" -> "Adhan feature"; "tr" -> "Ezan özelliği"; else -> "ميزة الأذان" },
                                    color = RoyalColors.Pearl, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (isAdhanEnabled) when (selectedLanguage) { "en" -> "Adhan is enabled"; "tr" -> "Ezan etkin"; else -> "الأذان مفعّل" }
                                    else when (selectedLanguage) { "en" -> "Adhan is disabled"; "tr" -> "Ezan devre dışı"; else -> "الأذان متوقف" },
                                    color = RoyalColors.Gold, fontSize = 12.sp
                                )
                            }
                            Text(
                                if (isAdhanEnabled) "ON" else "OFF",
                                color = if (isAdhanEnabled) RoyalColors.Gold else RoyalColors.Muted,
                                fontSize = 16.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(RoyalColors.Surface)
                                .border(1.dp, RoyalColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .then(royalGlassFocus(RoundedCornerShape(12.dp)))
                                .clickable {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                                        !RoyalAdhanScheduler.canScheduleExactAlarms(context)
                                    ) {
                                        runCatching {
                                            context.startActivity(
                                                android.content.Intent(
                                                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                                    android.net.Uri.parse("package:${context.packageName}")
                                                )
                                            )
                                        }.onFailure {
                                            android.widget.Toast.makeText(context, "افتح إعدادات التطبيقات ثم الوصول الخاص ثم المنبّهات الدقيقة", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        RoyalAdhanScheduler.scheduleNext(context)
                                        android.widget.Toast.makeText(
                                            context,
                                            if (selectedLanguage == "en") "Prayer alarms are scheduled" else if (selectedLanguage == "tr") "Namaz alarmları zamanlandı" else "تمت جدولة مواقيت الصلاة",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(royalAdaptiveIconSize(23.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when (selectedLanguage) { "en" -> "Precise prayer alarms"; "tr" -> "Hassas namaz alarmları"; else -> "دقة تنبيهات الصلاة" },
                                    color = RoyalColors.Pearl, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (RoyalAdhanScheduler.canScheduleExactAlarms(context))
                                        when (selectedLanguage) { "en" -> "Exact alarm access is available"; "tr" -> "Hassas alarm izni açık"; else -> "صلاحية التنبيه الدقيق متاحة" }
                                    else
                                        when (selectedLanguage) { "en" -> "Tap to allow exact alarms; otherwise timing may vary"; "tr" -> "Kesin zamanlama için dokunun; aksi halde gecikme olabilir"; else -> "اضغط للسماح بالدقة العالية؛ وإلا قد يتأخر التنبيه" },
                                    color = RoyalColors.Muted, fontSize = 10.sp
                                )
                            }
                            Text(
                                if (RoyalAdhanScheduler.canScheduleExactAlarms(context)) "✓" else "›",
                                color = RoyalColors.Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        }
                        if (selectedSettingsCategory == "motion") {
                        Text(
                            when (selectedLanguage) { "en" -> "ROYAL Motion • Background movement"; "tr" -> "ROYAL Motion • Arka plan hareketi"; else -> "حيوية الخلفيات • ROYAL Motion" },
                            color = RoyalColors.Gold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(when (selectedLanguage) { "en" -> "Enable or freeze each background independently"; "tr" -> "Her arka planı ayrı ayrı hareket ettirin veya sabitleyin"; else -> "شغّل الحركة أو ثبّت كل خلفية بشكل مستقل" }, color = RoyalColors.Muted, fontSize = 11.sp)
                        listOf(
                            Triple("home", if (selectedLanguage == "en") "Home background" else if (selectedLanguage == "tr") "Ana ekran" else "الخلفية الرئيسية", homeMotionEnabled),
                            Triple("adhan", if (selectedLanguage == "en") "Adhan background" else if (selectedLanguage == "tr") "Ezan arka planı" else "خلفية الأذان", adhanMotionEnabled),
                            Triple("weather", if (selectedLanguage == "en") "Weather background" else if (selectedLanguage == "tr") "Hava durumu" else "خلفية الطقس", weatherMotionEnabled)
                        ).forEach { (key, label, enabled) ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(RoyalColors.Surface).border(1.dp, RoyalColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .then(royalGlassFocus(RoundedCornerShape(12.dp)))
                                .clickable {
                                    val next = !enabled
                                    when (key) {
                                        "home" -> { homeMotionEnabled = next; wallpaperPreferences.edit().putBoolean("motion_home", next).apply() }
                                        "adhan" -> { adhanMotionEnabled = next; wallpaperPreferences.edit().putBoolean("motion_adhan", next).apply() }
                                        else -> { weatherMotionEnabled = next; wallpaperPreferences.edit().putBoolean("motion_weather", next).apply() }
                                    }
                                }.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(label, color = RoyalColors.Pearl, fontSize = 13.sp)
                                Text(if (enabled) "MOTION ON" else "FIXED", color = if (enabled) RoyalColors.Gold else RoyalColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        }
                        if (selectedSettingsCategory == "appearance") {
                        Text(
                            when (selectedLanguage) { "en" -> "Interface theme"; "tr" -> "Arayüz teması"; else -> "مظهر الواجهة" },
                            color = RoyalColors.Gold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconStyleChoice(
                                Modifier.weight(1f),
                                when (selectedLanguage) { "en" -> "Night mode"; "tr" -> "Koyu mod"; else -> "الوضع الليلي" },
                                "dark",
                                if (lightThemeEnabled) "light" else "dark",
                                icon = Icons.Rounded.Schedule
                            ) {
                                lightThemeEnabled = false
                                RoyalColors.applyLightTheme(false)
                                wallpaperPreferences.edit().putString("launcher_theme_mode", "dark").apply()
                            }
                            IconStyleChoice(
                                Modifier.weight(1f),
                                when (selectedLanguage) { "en" -> "Light mode"; "tr" -> "Açık mod"; else -> "الوضع الفاتح" },
                                "light",
                                if (lightThemeEnabled) "light" else "dark",
                                icon = Icons.Rounded.WbSunny
                            ) {
                                lightThemeEnabled = true
                                RoyalColors.applyLightTheme(true)
                                wallpaperPreferences.edit().putString("launcher_theme_mode", "light").apply()
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            when (selectedLanguage) { "en" -> "Home screen wallpapers"; "tr" -> "Ana ekran duvar kâğıtları"; else -> "خلفيات الشاشة الرئيسية" },
                            color = RoyalColors.Gold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        homeWallpapers.chunked(4).forEach { wallpaperRow ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                wallpaperRow.forEach { (key, title, imageRes) ->
                                    WallpaperChoice(
                                        modifier = Modifier.weight(1f),
                                        title = title,
                                        selected = selectedWallpaper == key,
                                        image = rememberRoyalBitmapPainter(imageRes, maxEdgePx = 768, maxPixels = 1_000_000L),
                                        onClick = {
                                            selectedWallpaper = key
                                            wallpaperPreferences.edit().putString("launcher_wallpaper", key).apply()
                                        }
                                    )
                                }
                                repeat(4 - wallpaperRow.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        }
                        if (selectedSettingsCategory == "sounds") {
                        Text(
                            when (selectedLanguage) { "en" -> "Ambient sound & background effects"; "tr" -> "Ortam sesleri ve arka plan efektleri"; else -> "أصوات الطبيعة ومؤثرات الخلفيات" },
                            color = RoyalColors.Gold, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                        )
                        RoyalFeatureToggleRow(
                            title = when (selectedLanguage) { "en" -> "Button and navigation clicks"; "tr" -> "Düğme ve gezinme tıklamaları"; else -> "صوت النقر والتنقل" },
                            detail = if (clickSoundsEnabled) "ON" else "OFF",
                            checked = clickSoundsEnabled,
                            onClick = {
                                clickSoundsEnabled = !clickSoundsEnabled
                                wallpaperPreferences.edit().putBoolean("ui_click_sounds_enabled", clickSoundsEnabled).apply()
                            }
                        )
                        RoyalFeatureToggleRow(
                            title = when (selectedLanguage) { "en" -> "Lake water sound"; "tr" -> "Göl suyu sesi"; else -> "صوت جريان ماء البحيرة" },
                            detail = if (lakeWaterSoundEnabled) "ON" else "OFF",
                            checked = lakeWaterSoundEnabled,
                            onClick = {
                                lakeWaterSoundEnabled = !lakeWaterSoundEnabled
                                wallpaperPreferences.edit().putBoolean("lake_water_sound_enabled", lakeWaterSoundEnabled).apply()
                            }
                        )
                        RoyalFeatureToggleRow(
                            title = when (selectedLanguage) { "en" -> "Fire crackling sound"; "tr" -> "Ateş çıtırtısı sesi"; else -> "صوت طقطقة واحتراق الحطب" },
                            detail = if (fireCrackleSoundEnabled) "ON" else "OFF",
                            checked = fireCrackleSoundEnabled,
                            onClick = {
                                fireCrackleSoundEnabled = !fireCrackleSoundEnabled
                                wallpaperPreferences.edit().putBoolean("fire_crackle_sound_enabled", fireCrackleSoundEnabled).apply()
                            }
                        )
                        RoyalFeatureToggleRow(
                            title = when (selectedLanguage) { "en" -> "Fireplace video animation"; "tr" -> "Şömine ateş animasyonu"; else -> "حركة فيديو المدفأة" },
                            detail = if (fireAnimationEnabled) "ON" else "OFF",
                            checked = fireAnimationEnabled,
                            onClick = {
                                fireAnimationEnabled = !fireAnimationEnabled
                                wallpaperPreferences.edit().putBoolean("fireplace_animation_enabled", fireAnimationEnabled).apply()
                            }
                        )
                        RoyalFeatureToggleRow(
                            title = when (selectedLanguage) { "en" -> "Moving ocean waves"; "tr" -> "Hareketli okyanus dalgaları"; else -> "تحريك أمواج البحر" },
                            detail = if (oceanAnimationEnabled) "ON" else "OFF",
                            checked = oceanAnimationEnabled,
                            onClick = {
                                oceanAnimationEnabled = !oceanAnimationEnabled
                                wallpaperPreferences.edit().putBoolean("ocean_background_animation_enabled", oceanAnimationEnabled).apply()
                            }
                        )
                        RoyalFeatureToggleRow(
                            title = when (selectedLanguage) { "en" -> "Flag waving animation"; "tr" -> "Bayrak dalgalanma animasyonu"; else -> "حركة رفرفة العلم" },
                            detail = if (syriaFlagAnimationEnabled) "ON" else "OFF",
                            checked = syriaFlagAnimationEnabled,
                            onClick = {
                                syriaFlagAnimationEnabled = !syriaFlagAnimationEnabled
                                wallpaperPreferences.edit().putBoolean("syria_flag_animation_enabled", syriaFlagAnimationEnabled).apply()
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        RoyalDeviceSettingsSection(
                            language = selectedLanguage,
                            showDeviceSettings = false,
                            showAudioSettings = true
                        )
                        }
                        if (selectedSettingsCategory == "motion") {
                        Text(when (selectedLanguage) { "en" -> "Smart screensaver"; "tr" -> "Akıllı ekran koruyucu"; else -> "الشاشة الذكية" }, color = RoyalColors.Gold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(RoyalColors.Surface)
                                .border(1.dp, RoyalColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .then(royalGlassFocus(RoundedCornerShape(12.dp)))
                                .clickable {
                                    screensaverMotionEnabled = !screensaverMotionEnabled
                                    wallpaperPreferences.edit().putBoolean("motion_screensaver", screensaverMotionEnabled).apply()
                                }
                                
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                when (selectedLanguage) { "en" -> "Screensaver movement"; "tr" -> "Ekran koruyucu hareketi"; else -> "حركة شاشة التوقف" },
                                color = RoyalColors.Pearl, fontSize = 13.sp
                            )
                            Text(
                                if (screensaverMotionEnabled) "MOTION ON" else "FIXED",
                                color = if (screensaverMotionEnabled) RoyalColors.Gold else RoyalColors.Muted,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (selectedLanguage) { "en" -> "Start after"; "tr" -> "Başlatma süresi"; else -> "بدء شاشة التوقف بعد" },
                            color = RoyalColors.Pearl, fontSize = 12.sp
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(30, 60, 120, 300, 0).forEach { seconds ->
                                val selected = screensaverDelaySeconds == seconds
                                val label = when (seconds) {
                                    0 -> if (selectedLanguage == "en") "Off" else if (selectedLanguage == "tr") "Kapalı" else "إيقاف"
                                    30 -> "30s"
                                    60 -> "1m"
                                    120 -> "2m"
                                    else -> "5m"
                                }
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                                        .background(if (selected) RoyalColors.Gold.copy(alpha = 0.2f) else RoyalColors.Surface)
                                        .border(1.dp, if (selected) RoyalColors.Gold else RoyalColors.Gold.copy(alpha = 0.25f), RoundedCornerShape(9.dp))
                                        .then(royalGlassFocus(RoundedCornerShape(12.dp)))
                                        .clickable {
                                            screensaverDelaySeconds = seconds
                                            wallpaperPreferences.edit().putInt("screensaver_delay_seconds", seconds).apply()
                                            showSmartScreensaver = false
                                        }
                                        
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = if (selected) RoyalColors.Gold else RoyalColors.Pearl, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val screensaverWallpapers = listOf(
                            Triple("default", if (selectedLanguage == "en") "Royal" else if (selectedLanguage == "tr") "Kraliyet" else "الملكي", R.drawable.royal_smart_screensaver)
                        ) + homeWallpapers.filter { it.first != "color" }
                        screensaverWallpapers.chunked(4).forEach { wallpaperRow ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                wallpaperRow.forEach { (key, title, imageRes) ->
                                    WallpaperChoice(
                                        modifier = Modifier.weight(1f),
                                        title = title,
                                        selected = selectedScreensaverWallpaper == key,
                                        image = rememberRoyalBitmapPainter(imageRes, maxEdgePx = 768, maxPixels = 1_000_000L),
                                        onClick = {
                                            selectedScreensaverWallpaper = key
                                            wallpaperPreferences.edit().putString("screensaver_wallpaper", key).apply()
                                        }
                                    )
                                }
                                repeat(4 - wallpaperRow.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        }
                        if (selectedSettingsCategory == "icons") {
                        Text(
                            when (selectedLanguage) { "en" -> "Icon styles"; "tr" -> "Simge stilleri"; else -> "أشكال الأيقونات" },
                            color = RoyalColors.Gold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconStyleChoice(Modifier.weight(1f), when (selectedLanguage) { "en" -> "Rounded"; "tr" -> "Yuvarlak"; else -> "ناعم" }, "rounded", selectedIconStyle, icon = Icons.Rounded.Palette) {
                                selectedIconStyle = "rounded"
                                wallpaperPreferences.edit().putString("launcher_icon_style", "rounded").apply()
                            }
                            IconStyleChoice(Modifier.weight(1f), when (selectedLanguage) { "en" -> "Circle"; "tr" -> "Daire"; else -> "دائري" }, "circle", selectedIconStyle, icon = Icons.Rounded.Circle) {
                                selectedIconStyle = "circle"
                                wallpaperPreferences.edit().putString("launcher_icon_style", "circle").apply()
                            }
                            IconStyleChoice(Modifier.weight(1f), when (selectedLanguage) { "en" -> "Gold"; "tr" -> "Altın"; else -> "ذهبي" }, "gold", selectedIconStyle, icon = Icons.Rounded.Star) {
                                selectedIconStyle = "gold"
                                wallpaperPreferences.edit().putString("launcher_icon_style", "gold").apply()
                            }
                            IconStyleChoice(Modifier.weight(1f), when (selectedLanguage) { "en" -> "Neon"; "tr" -> "Neon"; else -> "نيون" }, "neon", selectedIconStyle, icon = Icons.Rounded.Bolt) {
                                selectedIconStyle = "neon"
                                wallpaperPreferences.edit().putString("launcher_icon_style", "neon").apply()
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(RoyalColors.Gold.copy(alpha = 0.14f))
                                .border(1.dp, RoyalColors.Gold.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                                .then(royalGlassFocus(RoundedCornerShape(12.dp)))
                                .clickable { onChooseDefaultHome() }
                                
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(painter = painterResource(R.drawable.royal_smart_tv), contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(royalAdaptiveIconSize(26.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(when (selectedLanguage) { "en" -> "Default TV launcher"; "tr" -> "Varsayılan TV başlatıcısı"; else -> "واجهة التلفاز الافتراضية" }, color = RoyalColors.Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (isDefaultHome) when (selectedLanguage) { "en" -> "ROYAL+ is the default launcher"; "tr" -> "ROYAL+ varsayılan başlatıcı"; else -> "ROYAL+ هو اللانشر الافتراضي لهذا الجهاز" } else when (selectedLanguage) { "en" -> "Enable ROYAL+ for the Home button"; "tr" -> "Ana ekran düğmesi için ROYAL+’ı etkinleştirin"; else -> "فعّل ROYAL+ ليظهر عند الضغط على زر الرئيسية" },
                                    color = RoyalColors.Pearl,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                if (isDefaultHome) when (selectedLanguage) { "en" -> "Enabled • Manage"; "tr" -> "Etkin • Yönet"; else -> "مفعّل • إدارة" } else when (selectedLanguage) { "en" -> "Enable launcher"; "tr" -> "Başlatıcıyı etkinleştir"; else -> "تفعيل اللانشر" },
                                color = RoyalColors.Gold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        }
                        if (selectedSettingsCategory == "clock") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(RoyalColors.Surface)
                                .border(1.dp, RoyalColors.Gold.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
                                .then(royalGlassFocus(RoundedCornerShape(12.dp)))
                                .clickable {
                                    use24HourClock = !use24HourClock
                                    wallpaperPreferences.edit().putBoolean("launcher_24_hour_clock", use24HourClock).apply()
                                }
                                
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(royalAdaptiveIconSize(24.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    when (selectedLanguage) { "en" -> "Clock format"; "tr" -> "Saat biçimi"; else -> "تنسيق الساعة" },
                                    color = RoyalColors.Pearl, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                                )
                                Text(if (use24HourClock) "24 h" else "12 h", color = RoyalColors.Gold, fontSize = 12.sp)
                            }
                            Text(if (use24HourClock) "24H" else "12H", color = RoyalColors.Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(RoyalColors.Surface)
                                .border(1.dp, RoyalColors.Gold.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
                                .then(royalGlassFocus(RoundedCornerShape(12.dp)))
                                .clickable {
                                    showDateOnHome = !showDateOnHome
                                    wallpaperPreferences.edit().putBoolean("launcher_show_date", showDateOnHome).apply()
                                }
                                
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(royalAdaptiveIconSize(24.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(when (selectedLanguage) { "en" -> "Show date"; "tr" -> "Tarihi göster"; else -> "عرض التاريخ" }, color = RoyalColors.Pearl, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (showDateOnHome) when (selectedLanguage) { "en" -> "Visible"; "tr" -> "Görünür"; else -> "ظاهر" } else when (selectedLanguage) { "en" -> "Hidden"; "tr" -> "Gizli"; else -> "مخفي" }, color = RoyalColors.Gold, fontSize = 12.sp)
                            }
                            Text(if (showDateOnHome) "ON" else "OFF", color = RoyalColors.Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(14.dp))
                        }
                        if (selectedSettingsCategory == "language") {
                        Text(
                            when (selectedLanguage) { "en" -> "Interface language"; "tr" -> "Arayüz dili"; else -> "لغة الواجهة" },
                            color = RoyalColors.Gold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconStyleChoice(Modifier.weight(1f), "العربية", "rounded", if (selectedLanguage == "ar") "rounded" else "", icon = Icons.Rounded.Language) {
                                selectedLanguage = "ar"
                                wallpaperPreferences.edit().putString("launcher_language", "ar").apply()
                            }
                            IconStyleChoice(Modifier.weight(1f), "English", "rounded", if (selectedLanguage == "en") "rounded" else "", icon = Icons.Rounded.Language) {
                                selectedLanguage = "en"
                                wallpaperPreferences.edit().putString("launcher_language", "en").apply()
                            }
                            IconStyleChoice(Modifier.weight(1f), "Türkçe", "rounded", if (selectedLanguage == "tr") "rounded" else "", icon = Icons.Rounded.Language) {
                                selectedLanguage = "tr"
                                wallpaperPreferences.edit().putString("launcher_language", "tr").apply()
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(when (selectedLanguage) { "en" -> "Font family"; "tr" -> "Yazı tipi"; else -> "نوع الخط" }, color = RoyalColors.Gold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconStyleChoice(Modifier.weight(1f), "Cairo", "rounded", if (selectedFont == "cairo") "rounded" else "", icon = Icons.Rounded.TextFields) {
                                selectedFont = "cairo"
                                wallpaperPreferences.edit().putString("launcher_font", "cairo").apply()
                            }
                            IconStyleChoice(Modifier.weight(1f), "Tajawal", "rounded", if (selectedFont == "tajawal") "rounded" else "", icon = Icons.Rounded.TextFields) {
                                selectedFont = "tajawal"
                                wallpaperPreferences.edit().putString("launcher_font", "tajawal").apply()
                            }
                            IconStyleChoice(Modifier.weight(1f), "Kufi", "rounded", if (selectedFont == "kufi") "rounded" else "", icon = Icons.Rounded.TextFields) {
                                selectedFont = "kufi"
                                wallpaperPreferences.edit().putString("launcher_font", "kufi").apply()
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconStyleChoice(Modifier.weight(1f), "Amiri", "rounded", if (selectedFont == "amiri") "rounded" else "", icon = Icons.Rounded.TextFields) {
                                selectedFont = "amiri"
                                wallpaperPreferences.edit().putString("launcher_font", "amiri").apply()
                            }
                            IconStyleChoice(Modifier.weight(1f), "Ruqaa", "rounded", if (selectedFont == "ruqaa") "rounded" else "", icon = Icons.Rounded.TextFields) {
                                selectedFont = "ruqaa"
                                wallpaperPreferences.edit().putString("launcher_font", "ruqaa").apply()
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            when (selectedLanguage) { "en" -> "Text size"; "tr" -> "Metin boyutu"; else -> "حجم النص" },
                            color = RoyalColors.Gold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            when (selectedLanguage) { "en" -> "Adjust text throughout ROYAL+ without changing TV resolution"; "tr" -> "TV çözünürlüğünü değiştirmeden ROYAL+ genelindeki metin boyutunu ayarlayın"; else -> "اضبط حجم النص في التطبيق دون تغيير دقة التلفاز" },
                            color = RoyalColors.Muted, fontSize = 11.sp
                        )
                        val textScaleChoice = when {
                            launcherTextScale < 0.95f -> "0.9"
                            launcherTextScale < 1.08f -> "1.0"
                            else -> "1.15"
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                Triple("0.9", "90%", "Smaller"),
                                Triple("1.0", "100%", "Normal"),
                                Triple("1.15", "115%", "Larger")
                            ).forEach { (value, label, _) ->
                                IconStyleChoice(
                                    Modifier.weight(1f),
                                    label,
                                    value,
                                    textScaleChoice,
                                    icon = Icons.Rounded.TextFields
                                ) {
                                    launcherTextScale = value.toFloat()
                                    wallpaperPreferences.edit().putFloat("launcher_text_scale", launcherTextScale).apply()
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        }
                        if (selectedSettingsCategory == "device") {
                        Text(
                            when (selectedLanguage) { "en" -> "Network & device settings"; "tr" -> "Ağ ve cihaz ayarları"; else -> "الشبكة وإعدادات الجهاز" },
                            color = RoyalColors.Gold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconStyleChoice(Modifier.weight(1f), "Wi‑Fi", "rounded", "", iconRes = R.drawable.royal_wifi) {
                                try { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) }
                                catch (_: Exception) { android.widget.Toast.makeText(context, "تعذر فتح إعدادات Wi-Fi", android.widget.Toast.LENGTH_SHORT).show() }
                            }
                            IconStyleChoice(Modifier.weight(1f), when (selectedLanguage) { "en" -> "Bluetooth"; "tr" -> "Bluetooth"; else -> "بلوتوث" }, "rounded", "", icon = Icons.Rounded.Bluetooth) {
                                try { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }
                                catch (_: Exception) { android.widget.Toast.makeText(context, "تعذر فتح إعدادات البلوتوث", android.widget.Toast.LENGTH_SHORT).show() }
                            }
                            IconStyleChoice(Modifier.weight(1f), when (selectedLanguage) { "en" -> "Network"; "tr" -> "Ağ"; else -> "الشبكة" }, "rounded", "", iconRes = R.drawable.royal_cloud_sync) {
                                try { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS)) }
                                catch (_: Exception) { android.widget.Toast.makeText(context, "تعذر فتح إعدادات الجهاز", android.widget.Toast.LENGTH_SHORT).show() }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconStyleChoice(Modifier.weight(1f), when (selectedLanguage) { "en" -> "Location"; "tr" -> "Konum"; else -> "الموقع" }, "rounded", "", icon = Icons.Rounded.LocationOn) {
                                try { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                                catch (_: Exception) { android.widget.Toast.makeText(context, "تعذر فتح إعدادات الموقع", android.widget.Toast.LENGTH_SHORT).show() }
                            }
                            IconStyleChoice(Modifier.weight(1f), when (selectedLanguage) { "en" -> "App info"; "tr" -> "Uygulama bilgisi"; else -> "معلومات التطبيق" }, "rounded", "", iconRes = R.drawable.royal_security) {
                                try { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) }
                                catch (_: Exception) { android.widget.Toast.makeText(context, "تعذر فتح معلومات التطبيق", android.widget.Toast.LENGTH_SHORT).show() }
                            }
                            IconStyleChoice(Modifier.weight(1f), when (selectedLanguage) { "en" -> "All settings"; "tr" -> "Tüm ayarlar"; else -> "كل الإعدادات" }, "rounded", "", iconRes = R.drawable.royal_dashboard) {
                                try { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS)) }
                                catch (_: Exception) { android.widget.Toast.makeText(context, "تعذر فتح إعدادات الجهاز", android.widget.Toast.LENGTH_SHORT).show() }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconStyleChoice(Modifier.weight(1f), when (selectedLanguage) { "en" -> "Developer options"; "tr" -> "Geliştirici seçenekleri"; else -> "خيارات المطوّر" }, "rounded", "", iconRes = R.drawable.royal_developer_mode) {
                                try { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                                catch (_: Exception) { android.widget.Toast.makeText(context, "تعذر فتح خيارات المطوّر", android.widget.Toast.LENGTH_SHORT).show() }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        RoyalDeviceSettingsSection(
                            language = selectedLanguage,
                            showDeviceSettings = true,
                            showAudioSettings = false
                        )
                        Spacer(Modifier.height(14.dp))
                        }
                        if (selectedSettingsCategory == "privacy") {
                        RoyalPrivacySettingsSection(language = selectedLanguage)
                        Spacer(Modifier.height(14.dp))
                        }
                        if (selectedSettingsCategory == "share") {
                        RoyalShareUpdatesSection(
                            language = selectedLanguage,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))
                        }
                        if (selectedSettingsCategory == "relax") {
                            RoyalRelaxSettingsSection(selectedLanguage)
                            Spacer(Modifier.height(14.dp))
                        }
                        if (selectedSettingsCategory == "about") {
                        RoyalAboutSettingsSection(language = selectedLanguage)
                        Spacer(Modifier.height(14.dp))
                        }
                    }
                    }
                }
            }
            activeAdhanPrayer?.let { prayerName ->
                AdhanOverlay(
                    prayerName = prayerName,
                    cityName = activeAdhanCity,
                    language = selectedLanguage,
                    motionEnabled = adhanMotionEnabled,
                    isDaytime = java.util.Calendar.getInstance(
                        TimeZone.getTimeZone(weather.timeZoneId ?: TimeZone.getDefault().id)
                    ).apply {
                        timeInMillis = currentTime
                    }.get(java.util.Calendar.HOUR_OF_DAY) in 6..17,
                    onDismiss = {
                        context.stopService(android.content.Intent(context, RoyalAdhanPlaybackService::class.java))
                        activeAdhanPrayer = null
                        prayerTimeBanner = null
                    }
                )
            }
        }
    }
    }
}


@Composable
private fun RoyalRelaxSettingsSection(selectedLanguage: String) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF18372C), Color(0xFF101916), Color(0xFF080B0A))
                                )
                            )
                            .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.72f), RoundedCornerShape(22.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = Color(0xFF20B8B0), modifier = Modifier.size(34.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("ROYAL RELAX", color = Color(0xFFD4AF37), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    when (selectedLanguage) {
                                        "en" -> "A peaceful world of nature"
                                        "tr" -> "Doğanın huzurlu dünyası"
                                        else -> "عالم من الطبيعة والهدوء"
                                    },
                                    color = Color(0xFFE8DDC8), fontSize = 12.sp
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            RoyalRelaxRibbon()
                        }
                        Text(
                            when (selectedLanguage) {
                                "en" -> "Watch natural places and relaxing scenes"
                                "tr" -> "Doğal yerleri ve huzur veren sahneleri izleyin"
                                else -> "شاهد الطبيعة واستمتع بالمشاهد الهادئة"
                            },
                            color = Color(0xFFE8DDC8), fontSize = 18.sp, fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RoyalRelaxFeatureTile(
                                icon = Icons.Rounded.WbSunny,
                                title = when (selectedLanguage) { "en" -> "Live nature cameras"; "tr" -> "Canlı doğa kameraları"; else -> "كاميرات الطبيعة المباشرة" },
                                detail = when (selectedLanguage) { "en" -> "Seas, forests, lakes, waterfalls and wildlife only"; "tr" -> "Yalnızca denizler, ormanlar, göller, şelaleler ve yaban hayatı"; else -> "البحار والغابات والبحيرات والشلالات والحياة البرية فقط" },
                                accent = Color(0xFF20B8B0),
                                modifier = Modifier.weight(1f)
                            )
                            RoyalRelaxFeatureTile(
                                icon = Icons.Rounded.Bolt,
                                title = when (selectedLanguage) { "en" -> "Fireplace & logs"; "tr" -> "Şömine ve odun ateşi"; else -> "النار والحطب" },
                                detail = when (selectedLanguage) { "en" -> "Warm fire and crackling wood videos"; "tr" -> "Sıcak şömine ve çıtırdayan odun videoları"; else -> "فيديوهات المدفأة والحطب المشتعل" },
                                accent = Color(0xFFD4AF37),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RoyalRelaxFeatureTile(
                                icon = Icons.Rounded.AcUnit,
                                title = when (selectedLanguage) { "en" -> "Winter ambience"; "tr" -> "Kış atmosferi"; else -> "أجواء الشتاء" },
                                detail = when (selectedLanguage) { "en" -> "Snowfall, snowy cabins and winter forests"; "tr" -> "Kar yağışı, karlı kulübeler ve kış ormanları"; else -> "تساقط الثلوج والأكواخ والغابات الشتوية" },
                                accent = Color(0xFF20B8B0),
                                modifier = Modifier.weight(1f)
                            )
                            RoyalRelaxFeatureTile(
                                icon = Icons.Rounded.WaterDrop,
                                title = when (selectedLanguage) { "en" -> "Rain & water"; "tr" -> "Yağmur ve su"; else -> "المطر والمياه" },
                                detail = when (selectedLanguage) { "en" -> "Rain, storms, rivers and waterfalls"; "tr" -> "Yağmur, fırtınalar, nehirler ve şelaleler"; else -> "المطر والعواصف والأنهار والشلالات" },
                                accent = Color(0xFF1677C8),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            when (selectedLanguage) { "en" -> "Choose your audio atmosphere"; "tr" -> "Ses atmosferinizi seçin"; else -> "اختر الأجواء الصوتية" },
                            color = Color(0xFFD4AF37), fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RoyalRelaxAudioModeRow(
                                icon = Icons.Rounded.Star,
                                title = when (selectedLanguage) { "en" -> "Quran recitation"; "tr" -> "Kur'an tilaveti"; else -> "القرآن الكريم" },
                                detail = when (selectedLanguage) { "en" -> "A separate listening mode, never mixed with music or effects"; "tr" -> "Müzik veya efektlerle asla karıştırılmayan ayrı bir dinleme modu"; else -> "وضع مستقل تماماً، لا يُخلط بالموسيقى أو المؤثرات" },
                                accent = Color(0xFFD4AF37)
                            )
                            RoyalRelaxAudioModeRow(
                                icon = Icons.Rounded.MusicNote,
                                title = when (selectedLanguage) { "en" -> "Calm instrumental music"; "tr" -> "Sakin enstrümantal müzik"; else -> "موسيقى هادئة وآلات موسيقية" },
                                detail = when (selectedLanguage) { "en" -> "Optional, with independent volume control"; "tr" -> "İsteğe bağlı, ayrı ses seviyesi kontrolüyle"; else -> "اختيارية مع تحكم مستقل بمستوى الصوت" },
                                accent = Color(0xFFD4AF37)
                            )
                            RoyalRelaxAudioModeRow(
                                icon = Icons.Rounded.WaterDrop,
                                title = when (selectedLanguage) { "en" -> "Nature sounds"; "tr" -> "Doğa sesleri"; else -> "أصوات الطبيعة" },
                                detail = when (selectedLanguage) { "en" -> "Rain, wind, birds, waves and fire crackle"; "tr" -> "Yağmur, rüzgâr, kuşlar, dalgalar ve ateş sesi"; else -> "المطر والرياح والطيور والأمواج وفرقعة الحطب" },
                                accent = Color(0xFF20B8B0)
                            )
                            RoyalRelaxAudioModeRow(
                                icon = Icons.Rounded.Cloud,
                                title = when (selectedLanguage) { "en" -> "Nature + music mix"; "tr" -> "Doğa + müzik karışımı"; else -> "مزج الطبيعة مع الموسيقى" },
                                detail = when (selectedLanguage) { "en" -> "Mix nature ambience and music, with separate volume controls"; "tr" -> "Doğa seslerini ve müziği ayrı ses kontrolleriyle karıştırın"; else -> "امزج أصوات الطبيعة والموسيقى مع تحكم منفصل لكل صوت" },
                                accent = Color(0xFF20B8B0)
                            )
                            RoyalRelaxAudioModeRow(
                                icon = Icons.Rounded.Close,
                                title = when (selectedLanguage) { "en" -> "Silent viewing"; "tr" -> "Sessiz izleme"; else -> "المشاهدة الصامتة" },
                                detail = when (selectedLanguage) { "en" -> "Enjoy the picture without audio"; "tr" -> "Görüntünün keyfini sessizce çıkarın"; else -> "استمتع بالمشهد دون صوت" },
                                accent = Color(0xFFE8DDC8)
                            )
                        }
                        Text(
                            when (selectedLanguage) {
                                "en" -> "Lightweight by design: play only when requested, keep one video player active, do not download automatically, and release playback resources when leaving the section."
                                "tr" -> "Hafif tasarım: yalnızca istendiğinde oynatın, tek video oynatıcı kullanın, otomatik indirme yapmayın ve bölümden çıkınca oynatma kaynaklarını serbest bırakın."
                                else -> "مصمم ليكون خفيفاً: التشغيل عند الطلب فقط، ومشغل فيديو واحد، دون تنزيل تلقائي، وتحرير موارد التشغيل عند مغادرة القسم."
                            },
                            color = Color(0xFFE8DDC8).copy(alpha = 0.82f), fontSize = 11.sp
                        )
                        Text(
                            when (selectedLanguage) {
                                "en" -> "Nature-only camera sources and videos will be added after their embedding, playback and usage permissions are verified. All ROYAL RELAX options are free."
                                "tr" -> "Doğa kameraları ve videoları, yerleştirme, oynatma ve kullanım izinleri doğrulandıktan sonra eklenecektir. ROYAL RELAX seçeneklerinin tümü ücretsizdir."
                                else -> "ستُضاف كاميرات الطبيعة والفيديوهات بعد التحقق من أذونات التضمين والتشغيل والاستخدام. جميع خيارات ROYAL RELAX مجانية."
                            },
                            color = Color(0xFFE8DDC8).copy(alpha = 0.72f), fontSize = 11.sp
                        )
                    }
}

@Composable
private fun RoyalRelaxRibbon() {
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFFFFE7A0), Color(0xFFD6A63D), Color(0xFFB98525))
                )
            )
            .border(1.dp, Color(0xFF08090B), shape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("♛", color = Color(0xFF111111), fontSize = 13.sp, fontWeight = FontWeight.Black)
        Text("RELAX", color = Color(0xFF111111), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.1.sp)
    }
}

@Composable
private fun RoyalRelaxFeatureTile(
    icon: ImageVector,
    title: String,
    detail: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(15.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF0D1713).copy(alpha = 0.9f))
            .border(1.dp, accent.copy(alpha = 0.42f), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
        Text(title, color = Color(0xFFE8DDC8), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        Text(detail, color = Color(0xFFE8DDC8).copy(alpha = 0.76f), fontSize = 10.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun RoyalRelaxAudioModeRow(
    icon: ImageVector,
    title: String,
    detail: String,
    accent: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0D1713).copy(alpha = 0.85f))
            .border(1.dp, accent.copy(alpha = 0.26f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color(0xFFE8DDC8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Color(0xFFE8DDC8).copy(alpha = 0.72f), fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun RoyalSettingsNavigationItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .background(
                if (selected) Color(0xFF0B555B).copy(alpha = 0.42f)
                else RoyalColors.Surface
            )
            .border(
                1.dp,
                if (selected) RoyalColors.Gold else RoyalColors.Gold.copy(alpha = 0.22f),
                shape
            )
            .then(royalGlassFocus(shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) RoyalColors.Gold else RoyalColors.Pearl.copy(alpha = 0.82f),
            modifier = Modifier.size(21.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            color = if (selected) RoyalColors.Gold else RoyalColors.Pearl,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2
        )
        if (selected) {
            Spacer(Modifier.weight(1f))
            Text("•", color = RoyalColors.Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RoyalFeatureToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (checked) RoyalColors.Gold.copy(alpha = 0.12f) else RoyalColors.Surface)
            .border(
                1.dp,
                if (checked) RoyalColors.Gold.copy(alpha = 0.55f) else RoyalColors.Gold.copy(alpha = 0.24f),
                RoundedCornerShape(10.dp)
            )
            .then(royalGlassFocus(RoundedCornerShape(10.dp)))
            .clickable(onClick = {
                playLauncherUiSound(context, if (checked) RoyalUiSound.SELECT_TOGGLE_OFF else RoyalUiSound.SELECT_TOGGLE_ON)
                onClick()
            })
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = RoyalColors.Pearl, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, color = if (checked) RoyalColors.Gold else RoyalColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RoyalBackdrop(
    modifier: Modifier = Modifier,
    selectedWallpaper: String,
    motionEnabled: Boolean = true,
    oceanAnimationEnabled: Boolean = true,
    syriaFlagAnimationEnabled: Boolean = true,
    fireAnimationEnabled: Boolean = true
) {
    if (selectedWallpaper == "fire" && fireAnimationEnabled) {
        RoyalFireplaceBackground(modifier = modifier.fillMaxSize())
    } else if (selectedWallpaper == "fire") {
        RoyalMotionImage(
            image = rememberRoyalBitmapPainter(R.drawable.royal_live_fire_4k, maxEdgePx = 2048),
            modifier = modifier,
            motionEnabled = false
        )
    } else {
        RoyalMotionImage(
            image = rememberRoyalBitmapPainter(when (selectedWallpaper) {
                "nature" -> R.drawable.royal_nature_wallpaper
                "forest" -> R.drawable.royal_wallpaper_forest_04
                "green" -> R.drawable.royal_wallpaper_grreen_07
                "lake" -> R.drawable.royal_wallpaper_lake_03
                "mountains" -> R.drawable.royal_wallpaper_mountains_02
                "sunset" -> R.drawable.royal_wallpaper_sunset_05
                "waterfall" -> R.drawable.royal_wallpaper_waterfall_06
                "city_night" -> R.drawable.royal_city_night
                "space" -> R.drawable.royal_space
                "rami_golden_nature" -> R.drawable.royal_rami_golden_nature
                "mountain_nature" -> R.drawable.royal_mountain_nature
                "blue_gold_magic" -> R.drawable.royal_blue_gold_magic
                "ocean" -> R.drawable.royal_ocean_4k
                "syria" -> R.drawable.royal_syria_flag_4k
                else -> R.drawable.royal_color_wallpaper
            }, maxEdgePx = 2048),
            modifier = modifier,
            motionEnabled = motionEnabled
        )
    }
    Box(modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
    if (selectedWallpaper == "ocean" && oceanAnimationEnabled) {
        RoyalOceanOverlay(modifier = modifier)
    }
    if (selectedWallpaper == "syria" && syriaFlagAnimationEnabled) {
        RoyalFlagOverlay(modifier = modifier)
    }
}

@Composable
internal fun RoyalMotionImage(image: androidx.compose.ui.graphics.painter.Painter, modifier: Modifier = Modifier, motionEnabled: Boolean = true) {
    val quality = RoyalInterfaceQualitySettings.budget(LocalContext.current)
    if (motionEnabled && quality.motionScale > 0.25f) {
        RoyalAnimatedMotionImage(image, modifier, quality.motionScale)
    } else {
        Image(painter = image, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize())
    }
}

@Composable
private fun RoyalAnimatedMotionImage(
    image: androidx.compose.ui.graphics.painter.Painter,
    modifier: Modifier,
    motionScale: Float
) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition()
    val drift by transition.animateFloat(-0.012f * motionScale, 0.012f * motionScale,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(18000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse))
    val zoom by transition.animateFloat(1f + 0.015f * motionScale, 1f + 0.055f * motionScale,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(24000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse))
    Image(painter = image, contentDescription = null, contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize().graphicsLayer {
            scaleX = zoom
            scaleY = zoom
            translationX = size.width * drift
        })
}
@Composable
private fun IconStyleChoice(
    modifier: Modifier,
    title: String,
    style: String,
    selectedStyle: String,
    icon: ImageVector = Icons.Rounded.Apps,
    iconRes: Int? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val selected = style == selectedStyle
    val shape = when (style) {
        "circle" -> RoundedCornerShape(50)
        "gold" -> RoundedCornerShape(8.dp)
        "neon" -> RoundedCornerShape(16.dp)
        else -> RoundedCornerShape(12.dp)
    }
    val accent = when (style) {
        "neon" -> Color(0xFF42E8E0)
        else -> RoyalColors.Gold
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(RoyalColors.Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else accent.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .then(royalGlassFocus(RoundedCornerShape(12.dp)))
            .clickable(onClick = {
                playLauncherUiSound(context, RoyalUiSound.NAV_SELECT)
                onClick()
            })
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(shape)
                .background(if (style == "gold") Color(0xFF3A2A0C) else Color(0xFF17212B))
                .border(1.5.dp, accent, shape),
            contentAlignment = Alignment.Center
        ) {
            if (iconRes == null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(royalAdaptiveIconSize(25.dp))
                )
            } else {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(royalAdaptiveIconSize(25.dp))
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(title, color = if (selected) accent else RoyalColors.Pearl, fontSize = 11.sp, maxLines = 1)
        if (selected) Text("✓", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WallpaperChoice(
    modifier: Modifier,
    title: String,
    selected: Boolean,
    image: androidx.compose.ui.graphics.painter.Painter,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(RoyalColors.Surface)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) RoyalColors.Gold else RoyalColors.Gold.copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.dp)
            )
            .then(royalGlassFocus(RoundedCornerShape(16.dp)))
            .clickable(onClick = {
                playLauncherUiSound(context, RoyalUiSound.NAV_SELECT)
                onClick()
            })
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = image,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (selected) "✓  $title" else title,
            color = if (selected) RoyalColors.Gold else RoyalColors.Pearl,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RoyalBrandHeader(
    searchActive: Boolean, searchQuery: String, currentTime: String, dateText: String,
    showDate: Boolean, onQueryChanged: (String) -> Unit,
    onCloseSearch: () -> Unit, onOpenSettings: () -> Unit, onOpenQuickControls: () -> Unit, onSearch: () -> Unit,
    onInstallApk: () -> Unit,
    onVoiceSearch: () -> Unit,
    voiceSearchAvailable: Boolean,
    translationOverlayEnabled: Boolean,
    translationIsTelevision: Boolean,
    onToggleTranslationOverlay: () -> Unit,
    selectedLanguage: String
) {
    val adaptiveProfile = LocalRoyalAdaptiveProfile.current
    val compactPhone = adaptiveProfile.isPhone && adaptiveProfile.widthClass == RoyalWindowWidthClass.COMPACT
    val headerHeight = if (compactPhone) 44.dp else if (adaptiveProfile.isTelevision) 64.dp else 57.dp
    Row(Modifier.fillMaxWidth().height(headerHeight), verticalAlignment = Alignment.CenterVertically) {
        CrownMark(Modifier.size(width = if (compactPhone) 30.dp else 45.dp, height = if (compactPhone) 24.dp else 34.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("ROYAL+", color = RoyalColors.Gold, fontSize = if (compactPhone) 20.sp else if (adaptiveProfile.isTelevision) 32.sp else 29.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.1.sp, maxLines = 1)
            if (!compactPhone) Text("SMART TV LAUNCHER", color = RoyalColors.Pearl.copy(alpha = 0.85f), fontSize = 8.sp, letterSpacing = 2.sp, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        if (searchActive) {
            Row(Modifier.width(if (compactPhone) 104.dp else 175.dp).height(36.dp).clip(RoundedCornerShape(10.dp)).background(RoyalColors.Panel)
                .border(1.dp, RoyalColors.Gold.copy(alpha = 0.65f), RoundedCornerShape(10.dp)).padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = RoyalColors.Gold, modifier = Modifier.size(royalAdaptiveIconSize(18.dp)))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (searchQuery.isBlank()) Text(when (selectedLanguage) { "en" -> "Search apps"; "tr" -> "Uygulama ara"; else -> "ابحث عن تطبيق" }, color = RoyalColors.Muted, fontSize = 12.sp)
                    BasicTextField(searchQuery, onQueryChanged, singleLine = true, cursorBrush = SolidColor(RoyalColors.Gold),
                        textStyle = TextStyle(color = RoyalColors.Pearl, fontSize = 12.sp), modifier = Modifier.fillMaxWidth())
                }
                Icon(Icons.Rounded.Close, when (selectedLanguage) { "en" -> "Close search"; "tr" -> "Aramayı kapat"; else -> "إغلاق البحث" }, tint = RoyalColors.Pearl, modifier = Modifier.size(royalAdaptiveIconSize(17.dp)).clickable(onClick = onCloseSearch).focusable())
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(currentTime, color = RoyalColors.Gold, fontSize = if (compactPhone) 16.sp else if (adaptiveProfile.isTelevision) 28.sp else 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.width(9.dp))
        if (showDate && !compactPhone) {
            Column(modifier = Modifier.widthIn(max = 150.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dateText, color = RoyalColors.Pearl, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(when (selectedLanguage) { "en" -> "Local date"; "tr" -> "Yerel tarih"; else -> "التاريخ المحلي" }, color = RoyalColors.Muted, fontSize = 8.sp, maxLines = 1)
            }
            Spacer(Modifier.width(9.dp))
        }
        StatusIcon(
            Icons.Rounded.ClosedCaption,
            when {
                translationIsTelevision -> when (selectedLanguage) {
                    "en" -> if (translationOverlayEnabled) "Stop phone caption receiver" else "Receive captions from phone"
                    "tr" -> if (translationOverlayEnabled) "Telefon altyazı alımını durdur" else "Telefondan altyazı al"
                    else -> if (translationOverlayEnabled) "إيقاف استقبال الترجمة" else "استقبال الترجمة من الهاتف"
                }
                else -> when (selectedLanguage) {
                    "en" -> if (translationOverlayEnabled) "Stop phone audio capture" else "Capture TV audio on phone"
                    "tr" -> if (translationOverlayEnabled) "Telefon ses yakalamayı durdur" else "Telefondan TV sesini yakala"
                    else -> if (translationOverlayEnabled) "إيقاف التقاط صوت التلفاز" else "التقاط صوت التلفاز وإرسال الترجمة"
                }
            },
            onToggleTranslationOverlay
        )
        Spacer(Modifier.width(5.dp))
        StatusIcon(Icons.Rounded.Search, when (selectedLanguage) { "en" -> "Search"; "tr" -> "Ara"; else -> "البحث" }, onSearch)
        if (voiceSearchAvailable) {
            Spacer(Modifier.width(5.dp))
            StatusIcon(Icons.Rounded.Mic, when (selectedLanguage) { "en" -> "Voice search"; "tr" -> "Sesli arama"; else -> "البحث الصوتي" }, onVoiceSearch)
        }
        Spacer(Modifier.width(5.dp))
        StatusIcon(Icons.Rounded.Apps, when (selectedLanguage) { "en" -> "Install APK"; "tr" -> "APK yükle"; else -> "تثبيت ملف APK" }, onInstallApk)
        Spacer(Modifier.width(5.dp))
        StatusIcon(Icons.Rounded.Tune, when (selectedLanguage) { "en" -> "Quick controls"; "tr" -> "Hızlı kontroller"; else -> "مركز التحكم" }, onOpenQuickControls)
        Spacer(Modifier.width(5.dp))
        StatusIcon(Icons.Rounded.Settings, when (selectedLanguage) { "en" -> "Settings"; "tr" -> "Ayarlar"; else -> "الإعدادات" }, onOpenSettings)
    }
}


@Composable
private fun GlassWeatherTile(
    weather: WeatherUiState,
    currentTime: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(15.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0x55DDF4FF),
                        Color(0x222D6E91),
                        Color(0x443A526F)
                    )
                )
            )
            .border(1.dp, Color(0x99E5F6FF), shape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = weatherEmoji(weather.description, currentTime),
            fontSize = 27.sp,
            maxLines = 1
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = weather.temperatureC?.toString()?.plus("°") ?: "--°",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = weather.city.ifBlank { "الطقس" },
                color = Color(0xFFE0F3FF),
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun weatherEmoji(description: String, currentTime: String): String {
    val value = description.lowercase(Locale.getDefault())
    val hour = currentTime.substringBefore(":").toIntOrNull()
    val night = hour != null && (hour >= 19 || hour < 6)
    return when {
        "thunder" in value || "رعد" in value -> "⛈️"
        "snow" in value || "ثلج" in value -> "❄️"
        "rain" in value || "مطر" in value || "drizzle" in value -> "🌧️"
        "cloud" in value || "غائم" in value || "سحب" in value -> if (night) "☁️" else "🌤️"
        night -> "🌙"
        else -> "☀️"
    }
}

@Composable
private fun ShortcutManagerScreen(
    apps: List<LauncherApp>,
    selectedPackages: Set<String>,
    columns: Int,
    iconStyle: String,
    onToggleShortcut: (LauncherApp) -> Unit,
    onLaunchApp: (LauncherApp) -> Unit,
    onManageApp: (LauncherApp) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "اختصاراتي",
                    color = RoyalColors.Gold,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "اضغط على التطبيق لإضافته أو إزالته من الاختصارات",
                    color = RoyalColors.Pearl.copy(alpha = 0.82f),
                    fontSize = 11.sp
                )
            }
            Text(
                text = "${selectedPackages.size} محدد",
                color = RoyalColors.Gold,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (apps.isEmpty()) {
            EmptyAppsMessage(
                message = "لا توجد تطبيقات متاحة للاختصار",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp, end = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    val selected = app.packageName in selectedPackages
                    AppTile(
                        app = app,
                        modifier = Modifier.height(88.dp),
                        highlighted = selected,
                        iconStyle = iconStyle,
                        onFocused = {},
                        onLaunch = { onToggleShortcut(app) },
                        onManage = { onManageApp(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeDashboard(
    favorites: List<LauncherApp>, shortcuts: List<LauncherApp>, otherApps: List<LauncherApp>,
    columns: Int, favoriteTileWidth: Dp, iconStyle: String, focusedAppPackage: String?,
    onAppFocused: (String) -> Unit, onLaunchApp: (LauncherApp) -> Unit,
    onManageApp: (LauncherApp) -> Unit,
    currentTime: String, weather: WeatherUiState,
    prayerTimes: PrayerTimesData?, currentTimeMillis: Long,
    onRequestLocation: () -> Unit,
    onNavigate: (LauncherDestination) -> Unit,
    onRefreshWeather: () -> Unit, onOpenWeather: () -> Unit, onOpenSettings: () -> Unit, selectedLanguage: String,
    weatherMotionEnabled: Boolean
) {
    val quickAccessApps = remember(shortcuts, favorites) {
        (shortcuts + favorites).distinctBy { it.packageName }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = (maxHeight * 0.27f).coerceIn(112.dp, 148.dp)
        val favoriteHeight = (maxHeight * 0.24f).coerceIn(104.dp, 126.dp)
        // Ramadan artwork and greeting are rendered behind the interactive home content,
        // and only while the device's Islamic calendar is in Ramadan.
        val isRamadan = remember(currentTime) {
            HijriCalendarCompat.isRamadan(java.util.Date(System.currentTimeMillis()))
        }
        if (isRamadan) {
            Image(
                painter = rememberRoyalBitmapPainter(R.drawable.ramadan_overlay, maxEdgePx = 2048),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alpha = 0.30f,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(Modifier.fillMaxSize()) {
            RoyalScenicBanner(
                modifier = Modifier.fillMaxWidth().height(heroHeight),
                weather = weather,
                language = selectedLanguage,
                currentTime = currentTime,
                prayerTimes = prayerTimes,
                currentTimeMillis = currentTimeMillis,
                onRequestLocation = onRequestLocation,
                onRefreshWeather = onRefreshWeather,
                onOpenWeather = onOpenWeather,
                onExploreApps = { onNavigate(LauncherDestination.APPS) },
                motionEnabled = weatherMotionEnabled
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(when (selectedLanguage) { "en" -> "Quick access"; "tr" -> "Hızlı erişim"; else -> "الوصول السريع" }, color = RoyalColors.Pearl, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Rounded.Apps, null, tint = RoyalColors.Gold, modifier = Modifier.size(royalAdaptiveIconSize(21.dp)))
            }
            Spacer(Modifier.height(10.dp))
            if (quickAccessApps.isEmpty()) EmptyAppsMessage("لا توجد تطبيقات قابلة للتشغيل على هذا الجهاز", Modifier.fillMaxWidth().height(favoriteHeight))
            else LazyRow(
                modifier = Modifier.fillMaxWidth().height(favoriteHeight),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                rowItems(quickAccessApps, key = { it.packageName }) { app ->
                    val highlighted = focusedAppPackage == app.packageName || (focusedAppPackage == null && app.packageName == quickAccessApps.firstOrNull()?.packageName)
                    AppTile(app, Modifier.width(favoriteTileWidth.coerceAtLeast(88.dp)).height(favoriteHeight - 4.dp),
                        highlighted, iconStyle, onAppFocused, { onLaunchApp(app) }, { onManageApp(app) })
                }
                item(key = "royal_muslim_section") {
                    MuslimLauncherCard(
                        modifier = Modifier.width(favoriteTileWidth.coerceAtLeast(88.dp)).height(favoriteHeight - 4.dp),
                        language = selectedLanguage,
                        onClick = { onNavigate(LauncherDestination.MUSLIM) }
                    )
                }
            }
        }
        // Draw the greeting last so it stays visible above the banner, only during Ramadan.
        if (isRamadan) {
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFFFFD56D), modifier = Modifier.size(royalAdaptiveIconSize(24.dp)))
                Text(when (selectedLanguage) { "en" -> "Ramadan Kareem"; "tr" -> "Ramazanınız mübarek olsun"; else -> "رمضان كريم" }, color = Color(0xFFFFD56D), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MuslimLauncherCard(
    modifier: Modifier,
    language: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val adaptiveProfile = LocalRoyalAdaptiveProfile.current
    var focused by remember { mutableStateOf(false) }
    val label = when (language) {
        "en" -> "Muslim"
        "tr" -> "Müslüman"
        else -> "مسلم"
    }
    val iconSize = adaptiveProfile.iconSize(96.dp).coerceIn(72.dp, 112.dp)
    val shape = RoundedCornerShape(16.dp)
    Card(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable {
                playLauncherUiSound(context, RoyalUiSound.NAV_SELECT)
                onClick()
            }
            .focusable(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (focused) Color(0xFF211A0D) else Color(0xFF070A0E).copy(alpha = 0.93f)
        ),
        border = BorderStroke(
            if (focused) 2.5.dp else 1.dp,
            if (focused) Color(0xFFFFD36A) else RoyalColors.Gold.copy(alpha = 0.48f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (focused) 12.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_muslim_mosque),
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(iconSize)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                color = if (focused) RoyalColors.Gold else RoyalColors.Pearl,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
private fun RoyalScenicBanner(
    modifier: Modifier = Modifier,
    weather: WeatherUiState,
    language: String = "ar",
    currentTime: String,
    onRequestLocation: () -> Unit,
    onRefreshWeather: () -> Unit,
    onOpenWeather: () -> Unit,
    onExploreApps: () -> Unit,
    prayerTimes: PrayerTimesData?,
    currentTimeMillis: Long,
    motionEnabled: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(17.dp)
    Box(modifier.clip(shape).border(BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Color(0xFFFFD36A) else RoyalColors.Gold.copy(alpha = 0.72f)), shape)) {
        Image(rememberRoyalBitmapPainter(R.drawable.royal_nature_wallpaper, maxEdgePx = 1024, maxPixels = 1_500_000L), null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xE900050A), Color(0x5A00050A), Color(0xD900050A)))))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x18000000), Color(0x16000000), Color(0xC900050A)))))
        Row(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .clip(RoundedCornerShape(15.dp))
                    .clickable(onClick = onExploreApps)
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(when (language) { "en" -> "Welcome to"; "tr" -> "Hoş geldiniz"; else -> "مرحباً بك في" }, color = RoyalColors.Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("ROYAL+", color = Color(0xFFFFD76D), fontSize = 43.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.5.sp, maxLines = 1)
                Text(when (language) { "en" -> "Entertainment at your fingertips"; "tr" -> "Eğlence parmaklarınızın ucunda"; else -> "عالم الترفيه بين يديك" }, color = RoyalColors.Pearl, fontSize = 16.sp, maxLines = 1)
            }
            WeatherGlassCard(
                weather = weather,
                currentTime = currentTime,
                prayerTimes = prayerTimes,
                currentTimeMillis = currentTimeMillis,
                onRequestLocation = onRequestLocation,
                onRefreshWeather = onRefreshWeather,
                onOpenWeather = onOpenWeather,
                modifier = Modifier.weight(1.05f).fillMaxHeight(),
                language = language,
                motionEnabled = motionEnabled
            )
        }
    }
}


@Composable
private fun AppCatalog(
    title: String,
    apps: List<LauncherApp>,
    columns: Int,
    focusedAppPackage: String?,
    onAppFocused: (String) -> Unit,
    onLaunchApp: (LauncherApp) -> Unit,
    onManageApp: (LauncherApp) -> Unit,
    iconStyle: String,
    initialHighlight: Boolean,
    headingIcon: ImageVector = Icons.Rounded.Apps
) {
    Column(Modifier.fillMaxSize()) {
        SectionHeading(title = title, icon = headingIcon)
        Spacer(Modifier.height(8.dp))
        AppGrid(
            apps = apps,
            columns = columns,
            tileHeight = LocalRoyalAdaptiveProfile.current.appTileHeight,
            modifier = Modifier.fillMaxSize(),
            focusedAppPackage = focusedAppPackage,
            onAppFocused = onAppFocused,
            onLaunchApp = onLaunchApp,
            onManageApp = onManageApp,
            iconStyle = iconStyle,
            initialHighlight = initialHighlight
        )
    }
}

@Composable
private fun AppGrid(
    apps: List<LauncherApp>,
    columns: Int,
    tileHeight: Dp,
    modifier: Modifier,
    focusedAppPackage: String?,
    onAppFocused: (String) -> Unit,
    onLaunchApp: (LauncherApp) -> Unit,
    onManageApp: (LauncherApp) -> Unit,
    iconStyle: String,
    initialHighlight: Boolean
) {
    val adaptiveProfile = LocalRoyalAdaptiveProfile.current
    if (apps.isEmpty()) {
        EmptyAppsMessage(
            message = "لا توجد تطبيقات لعرضها هنا",
            modifier = modifier.fillMaxWidth()
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = modifier,
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 4.dp, top = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(adaptiveProfile.itemSpacing),
            verticalArrangement = Arrangement.spacedBy(adaptiveProfile.itemSpacing)
        ) {
            items(apps, key = { it.packageName }) { app ->
                val highlighted = focusedAppPackage == app.packageName ||
                    (focusedAppPackage == null && initialHighlight && app.packageName == apps.firstOrNull()?.packageName)
                AppTile(
                    app = app,
                    modifier = Modifier.fillMaxWidth().height(tileHeight),
                    highlighted = highlighted,
                    iconStyle = iconStyle,
                    onFocused = onAppFocused,
                    onLaunch = { onLaunchApp(app) },
                    onManage = { onManageApp(app) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: LauncherApp,
    modifier: Modifier,
    highlighted: Boolean,
    iconStyle: String,
    onFocused: (String) -> Unit,
    onLaunch: () -> Unit,
    onManage: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val adaptiveProfile = LocalRoyalAdaptiveProfile.current
    val appIconSize = adaptiveProfile.iconSize(68.dp).coerceIn(60.dp, 88.dp)
    val fallbackIconSize = adaptiveProfile.iconSize(38.dp).coerceIn(34.dp, 54.dp)
    var hasFocus by remember(app.packageName) { mutableStateOf(false) }
    val focusScale by animateFloatAsState(
        targetValue = if (hasFocus) 1.04f else 1f,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "app_tile_focus_scale"
    )
    val iconFocusScale by animateFloatAsState(
        targetValue = if (hasFocus || highlighted) 1.20f else 1f,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "app_icon_focus_scale"
    )
    val shape = when (iconStyle) {
        "circle" -> RoundedCornerShape(50)
        "gold" -> RoundedCornerShape(9.dp)
        "neon" -> RoundedCornerShape(18.dp)
        else -> RoundedCornerShape(14.dp)
    }
    Card(
        modifier = modifier.graphicsLayer {
            scaleX = focusScale
            scaleY = focusScale
        }.onFocusChanged { state ->
            hasFocus = state.isFocused
            if (state.isFocused) onFocused(app.packageName)
        }.combinedClickable(
            onClick = {
                playLauncherUiSound(context, RoyalUiSound.NAV_SELECT)
                onLaunch()
            },
            onLongClick = {
                playLauncherUiSound(context, RoyalUiSound.NAV_OPEN_MENU)
                onManage?.invoke()
            }
        ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) Color(0xFF211A0D).copy(alpha = 0.98f)
            else Color(0xFF070A0E).copy(alpha = 0.93f)
        ),
        border = BorderStroke(
            if (highlighted) 2.5.dp else 1.dp,
            if (highlighted) Color(0xFFFFD36A) else RoyalColors.Gold.copy(alpha = 0.48f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (highlighted) 12.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val iconBackground = when (iconStyle) {
                "gold" -> Color(0xFF17110A)
                "neon" -> Color(0xFF08131A)
                "circle" -> Color(0xFF11151D)
                else -> Color(0xFF0D1119)
            }
            val iconBorder = when (iconStyle) {
                "gold" -> RoyalColors.Gold
                "neon" -> Color(0xFF42E8E0)
                else -> RoyalColors.Gold.copy(alpha = 0.42f)
            }
            Box(
                modifier = Modifier
                    .size(appIconSize)
                    .graphicsLayer {
                        scaleX = iconFocusScale
                        scaleY = iconFocusScale
                    }
                    .clip(shape)
                    .background(iconBackground)
                    .border(if (iconStyle == "rounded" ) 1.dp else 1.5.dp, iconBorder, shape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                val icon = app.icon
                if (icon != null) {
                    Image(
                        painter = remember(icon) { BitmapPainter(icon.asImageBitmap()) },
                        contentDescription = app.label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().clip(shape)
                    )
                } else {
                    Icon(
                        imageVector = fallbackAppIcon(app.label),
                        contentDescription = app.label,
                        tint = if (iconStyle == "neon") Color(0xFF42E8E0) else RoyalColors.Gold,
                        modifier = Modifier.size(fallbackIconSize)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = app.label,
                color = RoyalColors.Pearl,
                fontSize = 13.sp,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun fallbackAppIcon(label: String): ImageVector {
    val name = label.lowercase(Locale.ROOT)
    return when {
        "setting" in name || "إعداد" in label -> Icons.Rounded.Settings
        "home" in name || "منزل" in label || "الرئيسية" in label -> Icons.Rounded.Home
        "search" in name || "بحث" in label -> Icons.Rounded.Search
        "weather" in name || "طقس" in label || "الطقس" in label -> Icons.Rounded.Cloud
        "wifi" in name || "واي فاي" in label || "شبكة" in label -> Icons.Rounded.Wifi
        "favorite" in name || "مفضلة" in label || "المفضلة" in label -> Icons.Rounded.Favorite
        "star" in name || "نجمة" in label -> Icons.Rounded.Star
        else -> Icons.Rounded.Apps
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoyalSidePanel(
    modifier: Modifier, selectedDestination: LauncherDestination, language: String,
    onNavigate: (LauncherDestination) -> Unit
) {
    val adaptiveProfile = LocalRoyalAdaptiveProfile.current
    val compactPhone = adaptiveProfile.isPhone && adaptiveProfile.widthClass == RoyalWindowWidthClass.COMPACT
    val itemGap = if (compactPhone) 3.dp else 8.dp
    val sidebarFocusRequesters = remember { List(10) { FocusRequester() } }
    val navigationFocusModifiers = remember(sidebarFocusRequesters) {
        sidebarFocusRequesters.mapIndexed { index, requester ->
            Modifier.focusRequester(requester).focusProperties {
                up = sidebarFocusRequesters[(index + sidebarFocusRequesters.size - 1) % sidebarFocusRequesters.size]
                down = sidebarFocusRequesters[(index + 1) % sidebarFocusRequesters.size]
            }
        }
    }
    val labels = when (language) {
        "en" -> listOf("Home", "Apps", "Calendar", "Phone Cast", "Sinjar TV", "Sports", "Gallery", "Songs", "Files", "Manage apps")
        "tr" -> listOf("Ana sayfa", "Uygulamalar", "Takvim", "Telefon yansıtma", "Sinjar TV", "Spor", "Galeri", "Şarkılar", "Dosyalar", "Uygulamaları yönet")
        else -> listOf("الرئيسية", "التطبيقات", "التقويم الميلادي", "بث شاشة الهاتف", "سنجار TV", "الرياضة", "المعرض", "الأغاني", "الملفات", "إدارة التطبيقات")
    }
    val shape = RoundedCornerShape(19.dp)
    val panelBrush = if (RoyalColors.isLightTheme) {
        Brush.verticalGradient(listOf(Color(0xFFF8F4EA), Color(0xFFECE5D8)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF080A12), Color(0xFF111620)))
    }
    Column(
        modifier.focusGroup().verticalScroll(rememberScrollState()).clip(shape).background(panelBrush)
            .border(1.dp, RoyalColors.Gold.copy(alpha = 0.28f), shape)
            .padding(horizontal = if (compactPhone) 5.dp else 9.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(5.dp))
        SidebarNavItem(
            labels[0], Icons.Rounded.Home, selectedDestination == LauncherDestination.HOME,
            sound = RoyalUiSound.NAV_HOME, modifier = navigationFocusModifiers[0]
        ) { onNavigate(LauncherDestination.HOME) }
        Spacer(Modifier.height(itemGap))
        SidebarNavItem(
            labels[1], Icons.Rounded.Apps, selectedDestination == LauncherDestination.APPS,
            iconRes = R.drawable.royal_apps_luxe, modifier = navigationFocusModifiers[1]
        ) { onNavigate(LauncherDestination.APPS) }
        Spacer(Modifier.height(itemGap))
        SidebarNavItem(
            labels[2], Icons.Rounded.CalendarToday, selectedDestination == LauncherDestination.CALENDAR,
            iconRes = R.drawable.royal_calendar_luxe, modifier = navigationFocusModifiers[2]
        ) { onNavigate(LauncherDestination.CALENDAR) }
        Spacer(Modifier.height(itemGap))
        SidebarNavItem(
            labels[3], null, selectedDestination == LauncherDestination.SCREEN_CAST,
            iconRes = R.drawable.royal_screen_cast, modifier = navigationFocusModifiers[3]
        ) { onNavigate(LauncherDestination.SCREEN_CAST) }
        Spacer(Modifier.height(itemGap))
        SidebarNavItem(
            labels[4], null, selectedDestination == LauncherDestination.SINJAR_TV,
            sinjarLogo = true, modifier = navigationFocusModifiers[4]
        ) { onNavigate(LauncherDestination.SINJAR_TV) }
        Spacer(Modifier.height(itemGap))
        SidebarNavItem(
            labels[5], null, selectedDestination == LauncherDestination.SPORTS,
            sportsTv = true, modifier = navigationFocusModifiers[5]
        ) { onNavigate(LauncherDestination.SPORTS) }
        Spacer(Modifier.height(itemGap))
        SidebarNavItem(
            labels[6], Icons.Rounded.Collections, selectedDestination == LauncherDestination.GALLERY,
            iconRes = R.drawable.royal_gallery_luxe, modifier = navigationFocusModifiers[6]
        ) { onNavigate(LauncherDestination.GALLERY) }
        Spacer(Modifier.height(itemGap))
        SidebarNavItem(
            labels[7], Icons.Rounded.MusicNote, selectedDestination == LauncherDestination.SONGS,
            iconRes = R.drawable.royal_songs_luxe, modifier = navigationFocusModifiers[7]
        ) { onNavigate(LauncherDestination.SONGS) }
        Spacer(Modifier.height(itemGap))
        SidebarNavItem(
            labels[8], Icons.Rounded.Folder, selectedDestination == LauncherDestination.FILES,
            iconRes = R.drawable.royal_files_luxe, modifier = navigationFocusModifiers[8]
        ) { onNavigate(LauncherDestination.FILES) }
        Spacer(Modifier.height(if (compactPhone) 8.dp else 14.dp))
        SidebarNavItem(
            labels[9], null, selectedDestination == LauncherDestination.MANAGE_APPS,
            manageAppsGear = true, language = language, modifier = navigationFocusModifiers[9]
        ) { onNavigate(LauncherDestination.MANAGE_APPS) }
        Spacer(Modifier.height(12.dp))
        GoldDivider(Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        CrownMark(Modifier.size(width = 30.dp, height = 22.dp))
        Spacer(Modifier.height(4.dp))
        Text("ROYAL+", color = RoyalColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text("SMART TV", color = RoyalColors.Muted, fontSize = 7.sp, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun StatusIcon(
    icon: ImageVector,
    description: String,
    onClick: (() -> Unit)? = null
) {
    val adaptiveProfile = LocalRoyalAdaptiveProfile.current
    val compactPhone = adaptiveProfile.isPhone && adaptiveProfile.widthClass == RoyalWindowWidthClass.COMPACT
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val clickModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier
            .clip(shape)
            .background(if (focused) RoyalColors.Gold.copy(alpha = 0.18f) else Color.Transparent)
            .border(
                width = if (focused) 1.5.dp else 0.dp,
                color = if (focused) Color(0xFFFFD36A) else Color.Transparent,
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = {
                playLauncherUiSound(context, RoyalUiSound.NAV_SELECT)
                onClick()
            })
    }
    Box(
        modifier = Modifier.size(if (compactPhone) 27.dp else 32.dp).then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (onClick == null) RoyalColors.Pearl.copy(alpha = 0.90f) else RoyalColors.Gold,
            modifier = Modifier.size(royalAdaptiveIconSize(if (compactPhone) 16.dp else 19.dp))
        )
    }
}

@Composable
private fun SidebarNavItem(
    title: String,
    icon: ImageVector?,
    selected: Boolean,
    iconRes: Int? = null,
    sportsBall: Boolean = false,
    sportsTv: Boolean = false,
    manageAppsGear: Boolean = false,
    language: String = "ar",
    sinjarLogo: Boolean = false,
    sound: RoyalUiSound = RoyalUiSound.NAV_SELECT,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val adaptiveProfile = LocalRoyalAdaptiveProfile.current
    val compactPhone = adaptiveProfile.isPhone && adaptiveProfile.widthClass == RoyalWindowWidthClass.COMPACT
    val navItemHeight = when {
        compactPhone -> 42.dp
        adaptiveProfile.isTelevision && adaptiveProfile.heightClass == RoyalWindowHeightClass.EXPANDED -> 56.dp
        adaptiveProfile.isTelevision -> 48.dp
        else -> maxOf(42.dp, adaptiveProfile.minimumFocusTarget)
    }
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(13.dp)
    val focusScale by animateFloatAsState(
        targetValue = if (focused) 1.045f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "sidebar_focus_scale"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(navItemHeight)
            .graphicsLayer {
                scaleX = focusScale
                scaleY = focusScale
            }
            .clip(shape)
            .background(
                when {
                    focused -> Brush.horizontalGradient(listOf(RoyalColors.Activity.copy(alpha = 0.34f), RoyalColors.Activity.copy(alpha = 0.14f), Color.Transparent))
                    selected -> Brush.horizontalGradient(
                        listOf(Color(0xFFD5A64F).copy(alpha = 0.28f), Color(0xFF6D4C1A).copy(alpha = 0.22f), Color.Transparent)
                    )
                    else -> if (RoyalColors.isLightTheme) Brush.horizontalGradient(listOf(Color(0xFFF9F5EC), Color(0xFFEFE8DB))) else Brush.horizontalGradient(listOf(Color(0xFF080A12), Color(0xFF171B29)))
                }
            )
            .border(
                if (focused) 2.2.dp else if (selected) 1.2.dp else 0.6.dp,
                if (focused) RoyalColors.Activity else if (selected) RoyalColors.Gold else RoyalColors.Gold.copy(alpha = 0.10f),
                shape
            )
            .then(if (focused) Modifier.border(1.dp, RoyalColors.Activity.copy(alpha = 0.92f), shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = {
                playLauncherUiSound(context, sound)
                onClick()
            })
            .padding(horizontal = if (compactPhone) 0.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (compactPhone) Arrangement.Center else Arrangement.SpaceBetween
    ) {
        if (!compactPhone && !sinjarLogo && !sportsTv && !manageAppsGear) Text(
            text = title,
            color = if (focused) RoyalColors.Activity else if (selected) RoyalColors.Gold else RoyalColors.Pearl,
            fontSize = 12.sp,
            fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
        when {
            sinjarLogo -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = rememberRoyalBitmapPainter(R.drawable.sinjar_show_logo, maxEdgePx = 512, maxPixels = 262_144L),
                    contentDescription = "Sinjar TV",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 34.dp, height = 25.dp)
                )
                Text(
                    "TV",
                    color = if (focused) Color(0xFFEAF8FF) else if (selected) Color(0xFFFFD36A) else RoyalColors.Pearl,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            sportsTv -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Rounded.Tv, contentDescription = "ROYAL SPORTS", tint = if (selected) Color(0xFF54C878) else RoyalColors.Pearl, modifier = Modifier.size(royalAdaptiveIconSize(27.dp)))
                Text("ROYAL SPORTS", color = if (selected) Color(0xFF54C878) else RoyalColors.Pearl, fontSize = 6.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.35.sp, maxLines = 1)
            }
            manageAppsGear -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = title, tint = if (selected) RoyalColors.Gold else RoyalColors.Pearl, modifier = Modifier.size(royalAdaptiveIconSize(25.dp)))
                Text(if (language == "en") "APPS" else if (language == "tr") "UYGULAMALAR" else "التطبيقات", color = if (selected) RoyalColors.Gold else RoyalColors.Pearl, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            sportsBall -> RoyalRotatingFootballIcon(
                modifier = Modifier.size(royalSportsIconSize(28.dp, max = (navItemHeight - 8.dp))),
                contentDescription = title
            )
            iconRes == null -> {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (focused) RoyalColors.Activity else if (selected) RoyalColors.Gold else RoyalColors.Pearl.copy(alpha = 0.90f),
                        modifier = Modifier.size(royalAdaptiveIconSize(if (compactPhone) 22.dp else 20.dp))
                    )
                }
            }
            else -> Image(
                painter = painterResource(iconRes),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(royalAdaptiveIconSize(if (adaptiveProfile.isTelevision) 31.dp else if (compactPhone) 22.dp else 27.dp))
            )
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(royalAdaptiveIconSize(19.dp)))
        Text(
            text = title,
            color = RoyalColors.Pearl,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun RoyalFooter(
    modifier: Modifier = Modifier, currentTime: String = "", dateText: String = "", weather: WeatherUiState = WeatherUiState()
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.clip(shape).background(Color(0xE807090D)).border(1.dp, RoyalColors.Gold.copy(alpha = 0.68f), shape)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CrownMark(Modifier.size(width = 24.dp, height = 18.dp))
        Spacer(Modifier.width(6.dp))
        Text("ROYAL+", color = RoyalColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Spacer(Modifier.width(16.dp))
        Text("أهلاً وسهلاً بكم في ROYAL+ ... كل ما تحتاجه في مكان واحد", color = RoyalColors.Pearl, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        if (dateText.isNotBlank()) {
            Text(dateText, color = RoyalColors.Pearl.copy(alpha = 0.85f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}


@Composable
private fun GoldDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(1.dp).background(
            Brush.horizontalGradient(
                listOf(Color.Transparent, RoyalColors.Gold.copy(alpha = 0.72f), RoyalColors.Gold.copy(alpha = 0.18f), Color.Transparent)
            )
        )
    )
}

@Composable
private fun CrownMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.royalplus_home_logo),
        contentDescription = "ROYAL+",
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}
@Composable
private fun EmptyAppsMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = RoyalColors.Muted,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun buildFavoriteApps(apps: List<LauncherApp>): List<LauncherApp> {
    if (apps.isEmpty()) return emptyList()

    val priorities = listOf(
        listOf("youtube"),
        listOf("netflix"),
        listOf("google play", "play store", "متجر play", "متجر google"),
        listOf("disney"),
        listOf("prime video", "amazon prime"),
        listOf("bein", "be in", "connect", "بي إن"),
        listOf("iptv"),
        listOf("chrome"),
        listOf("maps", "خرائط"),
        listOf("spotify"),
        listOf("vlc")
    )
    val selected = mutableListOf<LauncherApp>()
    for (keywords in priorities) {
        val match = apps.firstOrNull { app ->
            selected.none { it.packageName == app.packageName } &&
                keywords.any { keyword ->
                    app.label.lowercase(Locale.getDefault()).contains(keyword) ||
                        app.packageName.lowercase(Locale.getDefault()).contains(keyword.replace(" ", ""))
                }
        }
        if (match != null) selected += match
        if (selected.size >= 6) break
    }
    if (selected.size < 6) {
        apps.filterNot { candidate -> selected.any { it.packageName == candidate.packageName } }
            .take(6 - selected.size)
            .forEach(selected::add)
    }
    return selected.take(6)
}

@Composable
private fun weatherIcon(description: String): ImageVector {
    val value = description.lowercase(Locale.getDefault())
    return when {
        "thunder" in value || "رعد" in value -> Icons.Rounded.Thunderstorm
        "snow" in value || "ثلج" in value -> Icons.Rounded.AcUnit
        "rain" in value || "مطر" in value || "drizzle" in value -> Icons.Rounded.WaterDrop
        "cloud" in value || "غائم" in value || "سحب" in value -> Icons.Rounded.Cloud
        else -> Icons.Rounded.WbSunny
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoyalAppManagementScreen(
    apps: List<LauncherApp>,
    favoritePackages: Set<String>,
    hiddenPackages: Set<String>,
    columns: Int,
    language: String,
    onToggleFavorite: (LauncherApp) -> Unit,
    onToggleHidden: (LauncherApp) -> Unit,
    onMoveFavorite: (LauncherApp, Int) -> Unit,
    onLaunchApp: (LauncherApp) -> Unit,
    onManageApp: (LauncherApp) -> Unit
) {
    val adaptiveProfile = LocalRoyalAdaptiveProfile.current
    val managementCardHeight = when {
        adaptiveProfile.isTelevision -> (adaptiveProfile.appTileHeight + 40.dp).coerceIn(156.dp, 184.dp)
        adaptiveProfile.isTablet -> 164.dp
        adaptiveProfile.isPhone -> 132.dp
        else -> 150.dp
    }
    val managementIconSize = adaptiveProfile.iconSize(42.dp).coerceIn(38.dp, 58.dp)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = adaptiveProfile.itemSpacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    when (language) { "en" -> "App management"; "tr" -> "Uygulama yönetimi"; else -> "إدارة التطبيقات" },
                    color = RoyalColors.Gold, fontSize = if (adaptiveProfile.isPhone) 18.sp else if (adaptiveProfile.isTelevision) 26.sp else 22.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    when (language) { "en" -> "Pin favorites, hide apps and arrange your quick access"; "tr" -> "Favorileri sabitleyin, uygulamaları gizleyin ve sıralayın"; else -> "أضف للمفضلة أو أخفِ التطبيقات ورتّب الوصول السريع" },
                    color = RoyalColors.Pearl.copy(alpha = 0.78f), fontSize = if (adaptiveProfile.isPhone) 9.sp else 10.sp
                )
            }
            Text(
                when (language) { "en" -> "${apps.size} apps"; "tr" -> "${apps.size} uygulama"; else -> "${apps.size} تطبيق" },
                color = RoyalColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
        }
        if (apps.isEmpty()) {
            EmptyAppsMessage(
                when (language) { "en" -> "No installed apps are available"; "tr" -> "Yüklü uygulama bulunamadı"; else -> "لا توجد تطبيقات مثبتة قابلة للتشغيل" },
                Modifier.fillMaxSize()
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = adaptiveProfile.pageHorizontalPadding, vertical = adaptiveProfile.itemSpacing),
                horizontalArrangement = Arrangement.spacedBy(adaptiveProfile.itemSpacing),
                verticalArrangement = Arrangement.spacedBy(adaptiveProfile.itemSpacing)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    val isFavorite = app.packageName in favoritePackages
                    val isHidden = app.packageName in hiddenPackages
                    Card(
                        modifier = Modifier.fillMaxWidth().height(managementCardHeight)
                            .combinedClickable(
                                onClick = { onLaunchApp(app) },
                                onLongClick = { onManageApp(app) }
                            ),
                        shape = RoundedCornerShape(15.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D121B)),
                        border = BorderStroke(
                            if (isFavorite) 1.8.dp else 1.dp,
                            if (isFavorite) RoyalColors.Gold else RoyalColors.Gold.copy(alpha = 0.25f)
                        )
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (app.icon != null) {
                                    Image(
                                        painter = remember(app.icon) { BitmapPainter(app.icon.asImageBitmap()) },
                                        contentDescription = app.label,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(managementIconSize)
                                    )
                                } else {
                                    Icon(Icons.Rounded.Apps, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(royalAdaptiveIconSize(42.dp)))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, color = RoyalColors.Pearl, fontSize = if (adaptiveProfile.isPhone) 11.sp else 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(app.packageName, color = RoyalColors.Muted, fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onToggleFavorite(app) }, modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (isFavorite) when (language) { "en" -> "★ Pinned"; "tr" -> "★ Sabit"; else -> "★ مفضلة" }
                                        else when (language) { "en" -> "☆ Pin"; "tr" -> "☆ Sabitle"; else -> "☆ أضف" },
                                        color = if (isFavorite) RoyalColors.Gold else RoyalColors.Pearl,
                                        fontSize = 9.sp, maxLines = 1
                                    )
                                }
                                TextButton(onClick = { onToggleHidden(app) }, modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (isHidden) when (language) { "en" -> "Show"; "tr" -> "Göster"; else -> "إظهار" }
                                        else when (language) { "en" -> "Hide"; "tr" -> "Gizle"; else -> "إخفاء" },
                                        color = if (isHidden) Color(0xFF72D6A0) else RoyalColors.Pearl,
                                        fontSize = 9.sp, maxLines = 1
                                    )
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onLaunchApp(app) }) {
                                    Text(when (language) { "en" -> "Open"; "tr" -> "Aç"; else -> "فتح" }, color = RoyalColors.Pearl, fontSize = 9.sp)
                                }
                                if (isFavorite) {
                                    Row {
                                        TextButton(onClick = { onMoveFavorite(app, -1) }) { Text("↑", color = RoyalColors.Gold, fontSize = 15.sp) }
                                        TextButton(onClick = { onMoveFavorite(app, 1) }) { Text("↓", color = RoyalColors.Gold, fontSize = 15.sp) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
