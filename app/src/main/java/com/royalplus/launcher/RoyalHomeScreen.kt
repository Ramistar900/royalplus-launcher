package com.royalplus.launcher

import androidx.activity.compose.BackHandler
import android.icu.util.IslamicCalendar
import android.os.Build

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private enum class LauncherDestination {
    HOME, APPS, MUSLIM, FAVORITES, SHORTCUTS, MORE
}

@Composable
internal fun RoyalHomeScreen(
    apps: List<LauncherApp>,
    weather: WeatherUiState,
    onRequestLocation: () -> Unit,
    onRefreshWeather: () -> Unit,
    onLaunchApp: (LauncherApp) -> Unit,
    onOpenSettings: () -> Unit,
    onChooseDefaultHome: () -> Unit,
    onInstallApk: () -> Unit,
    onManageApp: (LauncherApp) -> Unit
) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedDestination by remember { mutableStateOf(LauncherDestination.HOME) }
    var focusedAppPackage by remember { mutableStateOf<String?>(null) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showWallpaperSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val wallpaperPreferences = remember(context) { context.getSharedPreferences("royalplus_launcher_preferences", android.content.Context.MODE_PRIVATE) }
    var selectedWallpaper by remember { mutableStateOf(wallpaperPreferences.getString("launcher_wallpaper", "color") ?: "color") }
    var selectedIconStyle by remember { mutableStateOf(wallpaperPreferences.getString("launcher_icon_style", "rounded") ?: "rounded") }
    var use24HourClock by remember { mutableStateOf(wallpaperPreferences.getBoolean("launcher_24_hour_clock", true)) }
    var showDateOnHome by remember { mutableStateOf(wallpaperPreferences.getBoolean("launcher_show_date", true)) }
    var selectedShortcutPackages by remember {
        mutableStateOf(wallpaperPreferences.getStringSet("launcher_shortcut_packages", emptySet())?.toSet() ?: emptySet())
    }

    BackHandler(
        enabled = showWallpaperSettings || searchActive || selectedDestination != LauncherDestination.HOME
    ) {
        when {
            showWallpaperSettings -> showWallpaperSettings = false
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
            delay(30 * 60 * 1_000L)
            onRefreshWeather()
        }
    }

    val displayTimeZone = remember(weather.timeZoneId) {
        TimeZone.getTimeZone(weather.timeZoneId ?: TimeZone.getDefault().id)
    }
    val dateText = remember(currentTime, weather.timeZoneId) {
        SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar")).apply {
            timeZone = displayTimeZone
        }.format(Date(currentTime))
    }
    val timeText = remember(currentTime, weather.timeZoneId, use24HourClock) {
        SimpleDateFormat(if (use24HourClock) "HH:mm" else "h:mm a", Locale.getDefault()).apply {
            timeZone = displayTimeZone
        }.format(Date(currentTime))
    }
    val favorites = remember(apps) { buildFavoriteApps(apps) }
    val favoritePackages = remember(favorites) { favorites.map { it.packageName }.toSet() }
    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) {
            apps
        } else {
            val query = searchQuery.trim().lowercase(Locale.getDefault())
            apps.filter {
                it.label.lowercase(Locale.getDefault()).contains(query) ||
                    it.packageName.lowercase(Locale.getDefault()).contains(query)
            }
        }
    }
    val filteredFavorites = remember(favorites, searchQuery) {
        if (searchQuery.isBlank()) {
            favorites
        } else {
            val query = searchQuery.trim().lowercase(Locale.getDefault())
            favorites.filter {
                it.label.lowercase(Locale.getDefault()).contains(query) ||
                    it.packageName.lowercase(Locale.getDefault()).contains(query)
            }
        }
    }
    val otherApps = remember(filteredApps, favoritePackages) {
        filteredApps.filterNot { it.packageName in favoritePackages }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = RoyalColors.Black) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            RoyalBackdrop(Modifier.matchParentSize(), selectedWallpaper)
            val sidebarWidth = if (maxWidth >= 900.dp) 188.dp else (maxWidth * 0.205f).coerceIn(148.dp, 188.dp)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    RoyalBrandHeader(
                        searchActive = searchActive, searchQuery = searchQuery,
                        currentTime = timeText, dateText = dateText, showDate = showDateOnHome,
                        onQueryChanged = { searchQuery = it },
                        onCloseSearch = { searchActive = false; searchQuery = ""; selectedDestination = LauncherDestination.HOME },
                        onOpenSettings = { showWallpaperSettings = true },
                        onSearch = { searchActive = true; selectedDestination = LauncherDestination.APPS },
                        onInstallApk = onInstallApk
                    )
                    Spacer(Modifier.height(9.dp))
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        RoyalSidePanel(
                            modifier = Modifier.width(sidebarWidth).fillMaxHeight(),
                            selectedDestination = selectedDestination,
                            onNavigate = { destination ->
                                selectedDestination = destination
                                if (destination == LauncherDestination.HOME) { searchActive = false; searchQuery = "" }
                            },
                            onOpenSettings = { showWallpaperSettings = true }
                        )
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val columns = when { maxWidth >= 700.dp -> 6; maxWidth >= 570.dp -> 5; maxWidth >= 440.dp -> 4; else -> 3 }
                                val favoriteTileWidth = ((maxWidth - 50.dp) / 6).coerceIn(82.dp, 124.dp)
                                when (selectedDestination) {
                                    LauncherDestination.HOME -> HomeDashboard(
                                        favorites = favorites, shortcuts = apps.filter { it.packageName in selectedShortcutPackages },
                                        otherApps = otherApps, columns = columns, favoriteTileWidth = favoriteTileWidth,
                                        focusedAppPackage = focusedAppPackage, onAppFocused = { focusedAppPackage = it },
                                        onLaunchApp = onLaunchApp, onManageApp = onManageApp, iconStyle = selectedIconStyle,
                                        currentTime = timeText, dateText = dateText, showDate = showDateOnHome, weather = weather,
                                        onRequestLocation = onRequestLocation,
                                        onNavigate = { selectedDestination = it },
                                        onToggleDate = {
                                            showDateOnHome = !showDateOnHome
                                            wallpaperPreferences.edit().putBoolean("launcher_show_date", showDateOnHome).apply()
                                        },
                                        onRefreshWeather = onRefreshWeather,
                                        onOpenSettings = { showWallpaperSettings = true }
                                    )
                                    LauncherDestination.APPS -> AppCatalog(
                                        title = if (searchQuery.isBlank()) "جميع التطبيقات" else "نتائج البحث",
                                        apps = filteredApps, columns = columns, focusedAppPackage = focusedAppPackage,
                                        onAppFocused = { focusedAppPackage = it }, onLaunchApp = onLaunchApp,
                                        onManageApp = onManageApp, iconStyle = selectedIconStyle, initialHighlight = true
                                    )
                                    LauncherDestination.MUSLIM -> MuslimScreen(weather = weather, onRequestLocation = onRequestLocation)
                                    LauncherDestination.FAVORITES -> AppCatalog(
                                        title = "التطبيقات المفضلة", apps = filteredFavorites, columns = columns,
                                        focusedAppPackage = focusedAppPackage, onAppFocused = { focusedAppPackage = it },
                                        onLaunchApp = onLaunchApp, onManageApp = onManageApp, iconStyle = selectedIconStyle, initialHighlight = true
                                    )
                                    LauncherDestination.SHORTCUTS -> ShortcutManagerScreen(
                                        apps = filteredApps, selectedPackages = selectedShortcutPackages, columns = columns,
                                        iconStyle = selectedIconStyle,
                                        onToggleShortcut = { app ->
                                            val updated = selectedShortcutPackages.toMutableSet().apply { if (!add(app.packageName)) remove(app.packageName) }.toSet()
                                            selectedShortcutPackages = updated
                                            wallpaperPreferences.edit().putStringSet("launcher_shortcut_packages", updated).apply()
                                        },
                                        onLaunchApp = onLaunchApp, onManageApp = onManageApp
                                    )
                                    LauncherDestination.MORE -> AppCatalog(
                                        title = "المزيد من التطبيقات", apps = otherApps, columns = columns,
                                        focusedAppPackage = focusedAppPackage, onAppFocused = { focusedAppPackage = it },
                                        onLaunchApp = onLaunchApp, onManageApp = onManageApp, iconStyle = selectedIconStyle, initialHighlight = true
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    RoyalFooter(Modifier.fillMaxWidth().height(31.dp), currentTime = timeText, dateText = dateText, weather = weather)
                }
            }

            if (showWallpaperSettings) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xF5080A10))
                        .padding(horizontal = 28.dp, vertical = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF10131B))
                            .border(1.dp, RoyalColors.Gold.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("تخصيص الواجهة", color = RoyalColors.Gold, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("اختر الخلفية وشكل أيقونات التطبيقات", color = RoyalColors.Pearl, fontSize = 14.sp)
                        Spacer(Modifier.height(14.dp))
                        Text("الخلفيات", color = RoyalColors.Gold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            WallpaperChoice(
                                modifier = Modifier.weight(1f),
                                title = "الخلفية الملونة",
                                selected = selectedWallpaper == "color",
                                image = painterResource(R.drawable.royal_color_wallpaper),
                                onClick = {
                                    selectedWallpaper = "color"
                                    wallpaperPreferences.edit().putString("launcher_wallpaper", "color").apply()
                                }
                            )
                            WallpaperChoice(
                                modifier = Modifier.weight(1f),
                                title = "الخلفية الطبيعية",
                                selected = selectedWallpaper == "nature",
                                image = painterResource(R.drawable.royal_nature_wallpaper),
                                onClick = {
                                    selectedWallpaper = "nature"
                                    wallpaperPreferences.edit().putString("launcher_wallpaper", "nature").apply()
                                }
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("أشكال الأيقونات", color = RoyalColors.Gold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconStyleChoice(Modifier.weight(1f), "ناعم", "rounded", selectedIconStyle) {
                                selectedIconStyle = "rounded"
                                wallpaperPreferences.edit().putString("launcher_icon_style", "rounded").apply()
                            }
                            IconStyleChoice(Modifier.weight(1f), "دائري", "circle", selectedIconStyle) {
                                selectedIconStyle = "circle"
                                wallpaperPreferences.edit().putString("launcher_icon_style", "circle").apply()
                            }
                            IconStyleChoice(Modifier.weight(1f), "ذهبي", "gold", selectedIconStyle) {
                                selectedIconStyle = "gold"
                                wallpaperPreferences.edit().putString("launcher_icon_style", "gold").apply()
                            }
                            IconStyleChoice(Modifier.weight(1f), "نيون", "neon", selectedIconStyle) {
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
                                .clickable { onChooseDefaultHome() }
                                .focusable()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("واجهة التلفاز الافتراضية", color = RoyalColors.Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("اختر ROYAL+ من إعدادات أندرويد، أو ارجع إلى واجهة النظام لإيقافه", color = RoyalColors.Pearl, fontSize = 11.sp)
                            }
                            Text("إدارة", color = RoyalColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF080A0F))
                                .border(1.dp, RoyalColors.Gold.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
                                .clickable {
                                    use24HourClock = !use24HourClock
                                    wallpaperPreferences.edit().putBoolean("launcher_24_hour_clock", use24HourClock).apply()
                                }
                                .focusable()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("تنسيق الساعة", color = RoyalColors.Pearl, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (use24HourClock) "24 ساعة" else "12 ساعة", color = RoyalColors.Gold, fontSize = 12.sp)
                            }
                            Text(if (use24HourClock) "24H" else "12H", color = RoyalColors.Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF080A0F))
                                .border(1.dp, RoyalColors.Gold.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
                                .clickable {
                                    showDateOnHome = !showDateOnHome
                                    wallpaperPreferences.edit().putBoolean("launcher_show_date", showDateOnHome).apply()
                                }
                                .focusable()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("عرض التاريخ", color = RoyalColors.Pearl, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (showDateOnHome) "ظاهر" else "مخفي", color = RoyalColors.Gold, fontSize = 12.sp)
                            }
                            Text(if (showDateOnHome) "ON" else "OFF", color = RoyalColors.Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(RoyalColors.Gold.copy(alpha = 0.14f))
                                .border(1.dp, RoyalColors.Gold.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .clickable { showWallpaperSettings = false }
                                .focusable()
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("تم", color = RoyalColors.Gold, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoyalBackdrop(modifier: Modifier = Modifier, selectedWallpaper: String) {
    Image(
        painter = painterResource(
            if (selectedWallpaper == "nature") R.drawable.royal_nature_wallpaper
            else R.drawable.royal_color_wallpaper
        ),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize()
    )
    Box(modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
}

@Composable
private fun IconStyleChoice(
    modifier: Modifier,
    title: String,
    style: String,
    selectedStyle: String,
    onClick: () -> Unit
) {
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
            .background(Color(0xFF080A0F))
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else accent.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .focusable()
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
            Icon(
                imageVector = Icons.Rounded.Apps,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(25.dp)
            )
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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF080A0F))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) RoyalColors.Gold else RoyalColors.Gold.copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .focusable()
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
    onCloseSearch: () -> Unit, onOpenSettings: () -> Unit, onSearch: () -> Unit,
    onInstallApk: () -> Unit
) {
    Row(Modifier.fillMaxWidth().height(57.dp), verticalAlignment = Alignment.CenterVertically) {
        CrownMark(Modifier.size(width = 45.dp, height = 34.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("ROYAL+", color = RoyalColors.Gold, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.1.sp, maxLines = 1)
            Text("SMART TV LAUNCHER", color = RoyalColors.Pearl.copy(alpha = 0.85f), fontSize = 8.sp, letterSpacing = 2.sp, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        if (searchActive) {
            Row(Modifier.width(175.dp).height(36.dp).clip(RoundedCornerShape(10.dp)).background(RoyalColors.Panel)
                .border(1.dp, RoyalColors.Gold.copy(alpha = 0.65f), RoundedCornerShape(10.dp)).padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = RoyalColors.Gold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (searchQuery.isBlank()) Text("ابحث عن تطبيق", color = RoyalColors.Muted, fontSize = 12.sp)
                    BasicTextField(searchQuery, onQueryChanged, singleLine = true, cursorBrush = SolidColor(RoyalColors.Gold),
                        textStyle = TextStyle(color = RoyalColors.Pearl, fontSize = 12.sp), modifier = Modifier.fillMaxWidth())
                }
                Icon(Icons.Rounded.Close, "إغلاق البحث", tint = RoyalColors.Pearl, modifier = Modifier.size(17.dp).clickable(onClick = onCloseSearch).focusable())
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(currentTime, color = RoyalColors.Gold, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.width(9.dp))
        if (showDate) {
            Column(modifier = Modifier.widthIn(max = 150.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dateText, color = RoyalColors.Pearl, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("التاريخ المحلي", color = RoyalColors.Muted, fontSize = 8.sp, maxLines = 1)
            }
            Spacer(Modifier.width(9.dp))
        }
        StatusIcon(Icons.Rounded.Search, "البحث", onSearch)
        Spacer(Modifier.width(5.dp))
        StatusIcon(Icons.Rounded.Apps, "تثبيت ملف APK", onInstallApk)
        Spacer(Modifier.width(5.dp))
        StatusIcon(Icons.Rounded.Settings, "الإعدادات", onOpenSettings)
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
    currentTime: String, dateText: String, showDate: Boolean, weather: WeatherUiState,
    onRequestLocation: () -> Unit,
    onNavigate: (LauncherDestination) -> Unit, onToggleDate: () -> Unit,
    onRefreshWeather: () -> Unit, onOpenSettings: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = (maxHeight * 0.27f).coerceIn(112.dp, 148.dp)
        val favoriteHeight = (maxHeight * 0.24f).coerceIn(104.dp, 126.dp)
        Column(Modifier.fillMaxSize()) {
            RoyalScenicBanner(Modifier.fillMaxWidth().height(heroHeight), onExploreApps = { onNavigate(LauncherDestination.APPS) })
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("التطبيقات المثبتة", color = RoyalColors.Pearl, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Rounded.Apps, null, tint = RoyalColors.Gold, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.height(10.dp))
            if (favorites.isEmpty()) EmptyAppsMessage("لا توجد تطبيقات قابلة للتشغيل على هذا الجهاز", Modifier.fillMaxWidth().height(favoriteHeight))
            else LazyRow(
                modifier = Modifier.fillMaxWidth().height(favoriteHeight),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                rowItems(favorites, key = { it.packageName }) { app ->
                    val highlighted = focusedAppPackage == app.packageName || (focusedAppPackage == null && app.packageName == favorites.firstOrNull()?.packageName)
                    AppTile(app, Modifier.width(favoriteTileWidth.coerceAtLeast(88.dp)).height(favoriteHeight - 4.dp),
                        highlighted, iconStyle, onAppFocused, { onLaunchApp(app) }, { onManageApp(app) })
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HomeFeatureCard(Modifier.weight(1f).fillMaxHeight(), "التطبيقات", "جميع تطبيقاتك في مكان واحد", Icons.Rounded.Apps, false) { onNavigate(LauncherDestination.APPS) }
                HomeFeatureCard(Modifier.weight(1f).fillMaxHeight(), "مسلم", "مواقيت الصلاة والتاريخ الهجري", Icons.Rounded.Star, true) { onNavigate(LauncherDestination.MUSLIM) }
                HomeFeatureCard(Modifier.weight(1f).fillMaxHeight(), "الوقت والتاريخ", if (showDate) dateText else currentTime, Icons.Rounded.Schedule, false, onToggleDate)
                WeatherGlassCard(
                    weather = weather,
                    onRequestLocation = onRequestLocation,
                    onRefreshWeather = onRefreshWeather,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
        // Show the decorative overlay only during Ramadan (Islamic month 9).
        val isRamadan = remember(currentTime) {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                IslamicCalendar(Date(currentTime)).get(IslamicCalendar.MONTH) == 8
        }
        if (isRamadan) {
            Image(
                painter = painterResource(R.drawable.ramadan_overlay),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alpha = 0.42f,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun HomeFeatureCard(
    modifier: Modifier, title: String, subtitle: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val emphasized = selected || focused
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape)
            .background(if (emphasized) Brush.verticalGradient(listOf(Color(0xFF211A0A), Color(0xFF0A0C12))) else Brush.verticalGradient(listOf(Color(0xFF151B25), Color(0xFF090C12))))
            .border(if (focused) 2.5.dp else if (selected) 2.dp else 1.dp, if (emphasized) Color(0xFFFFD56D) else RoyalColors.Gold.copy(alpha = 0.32f), shape)
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, title, tint = if (emphasized) Color(0xFFFFD56D) else RoyalColors.Gold, modifier = Modifier.size(38.dp))
        Spacer(Modifier.height(7.dp))
        Text(title, color = if (emphasized) RoyalColors.Gold else RoyalColors.Pearl, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, color = RoyalColors.Pearl.copy(alpha = 0.78f), fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}


@Composable
private fun RoyalScenicBanner(modifier: Modifier = Modifier, onExploreApps: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(17.dp)
    Box(modifier.clip(shape).border(BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Color(0xFFFFD36A) else RoyalColors.Gold.copy(alpha = 0.72f)), shape)) {
        Image(painterResource(R.drawable.royal_nature_wallpaper), null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xE900050A), Color(0x5A00050A), Color(0xD900050A)))))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x18000000), Color(0x16000000), Color(0xC900050A)))))
        Row(Modifier.fillMaxSize().padding(horizontal = 25.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(0.4f))
            Column(Modifier.weight(1.4f).clickable(onClick = onExploreApps).onFocusChanged { focused = it.isFocused }.focusable(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("مرحباً بك في", color = RoyalColors.Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("ROYAL+", color = Color(0xFFFFD76D), fontSize = 43.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.5.sp, maxLines = 1)
                Text("عالم الترفيه بين يديك", color = RoyalColors.Pearl, fontSize = 16.sp, maxLines = 1)
            }
            Spacer(Modifier.width(18.dp))
            CrownMark(Modifier.size(width = 100.dp, height = 76.dp))
            Spacer(Modifier.weight(0.35f))
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
    initialHighlight: Boolean
) {
    Column(Modifier.fillMaxSize()) {
        SectionHeading(title = title, icon = Icons.Rounded.Apps)
        Spacer(Modifier.height(8.dp))
        AppGrid(
            apps = apps,
            columns = columns,
            tileHeight = 88.dp,
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
    if (apps.isEmpty()) {
        EmptyAppsMessage(
            message = "لا توجد تطبيقات لعرضها هنا",
            modifier = modifier.fillMaxWidth()
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = modifier,
            contentPadding = PaddingValues(bottom = 4.dp, top = 1.dp, end = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
    val shape = when (iconStyle) {
        "circle" -> RoundedCornerShape(50)
        "gold" -> RoundedCornerShape(9.dp)
        "neon" -> RoundedCornerShape(18.dp)
        else -> RoundedCornerShape(14.dp)
    }
    Card(
        modifier = modifier.combinedClickable(
            onClick = onLaunch,
            onLongClick = onManage
        ).onFocusChanged { state ->
            if (state.isFocused) onFocused(app.packageName)
        },
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
                    .size(68.dp)
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
                        modifier = Modifier.size(38.dp)
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

@Composable
private fun RoyalSidePanel(
    modifier: Modifier, selectedDestination: LauncherDestination,
    onNavigate: (LauncherDestination) -> Unit, onOpenSettings: () -> Unit
) {
    val shape = RoundedCornerShape(19.dp)
    Column(
        modifier.clip(shape).background(Brush.verticalGradient(listOf(Color(0xE8080A0F), Color(0xF7080A0F))))
            .border(1.dp, RoyalColors.Gold.copy(alpha = 0.18f), shape).padding(horizontal = 9.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(5.dp))
        SidebarNavItem("الرئيسية", Icons.Rounded.Home, selectedDestination == LauncherDestination.HOME) { onNavigate(LauncherDestination.HOME) }
        Spacer(Modifier.height(8.dp))
        SidebarNavItem("التطبيقات", Icons.Rounded.Apps, selectedDestination == LauncherDestination.APPS) { onNavigate(LauncherDestination.APPS) }
        Spacer(Modifier.height(8.dp))
        SidebarNavItem("مسلم", Icons.Rounded.Star, selectedDestination == LauncherDestination.MUSLIM) { onNavigate(LauncherDestination.MUSLIM) }
        Spacer(Modifier.height(8.dp))
        SidebarNavItem("القنوات", Icons.Rounded.Wifi, false) { onNavigate(LauncherDestination.APPS) }
        Spacer(Modifier.height(8.dp))
        SidebarNavItem("أفلام ومسلسلات", Icons.Rounded.ArrowForward, false) { onNavigate(LauncherDestination.APPS) }
        Spacer(Modifier.height(8.dp))
        SidebarNavItem("موسيقى", Icons.Rounded.MoreHoriz, false) { onNavigate(LauncherDestination.APPS) }
        Spacer(Modifier.height(8.dp))
        SidebarNavItem("الإعدادات", Icons.Rounded.Settings, false, onOpenSettings)
        Spacer(Modifier.weight(1f))
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
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
    }
    Box(
        modifier = Modifier.size(32.dp).then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (onClick == null) RoyalColors.Pearl.copy(alpha = 0.90f) else RoyalColors.Gold,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun SidebarNavItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val emphasized = selected || focused
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .background(
                if (emphasized) Brush.horizontalGradient(
                    listOf(Color(0xFFD5A64F).copy(alpha = if (selected) 0.78f else 0.48f), Color(0xFF6D4C1A).copy(alpha = 0.55f), Color.Transparent)
                ) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
            )
            .border(
                if (focused) 2.dp else if (selected) 1.2.dp else 0.6.dp,
                if (focused) Color(0xFFFFD36A) else if (selected) RoyalColors.Gold else RoyalColors.Gold.copy(alpha = 0.10f),
                shape
            )
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = if (emphasized) Color(0xFFFFE2A1) else RoyalColors.Pearl,
            fontSize = 12.sp,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (emphasized) Color(0xFFFFD36A) else RoyalColors.Pearl.copy(alpha = 0.90f),
            modifier = Modifier.size(20.dp)
        )
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
        Icon(imageVector = icon, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(19.dp))
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
        Icon(weatherIcon(weather.description), null, tint = RoyalColors.Gold, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(currentTime, color = RoyalColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (dateText.isNotBlank()) {
            Spacer(Modifier.width(10.dp))
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
    Canvas(modifier = modifier) {
        val crown = Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.22f)
            lineTo(size.width * 0.29f, size.height * 0.46f)
            lineTo(size.width * 0.43f, size.height * 0.08f)
            lineTo(size.width * 0.57f, size.height * 0.44f)
            lineTo(size.width * 0.78f, size.height * 0.15f)
            lineTo(size.width * 0.92f, size.height * 0.22f)
            lineTo(size.width * 0.84f, size.height * 0.76f)
            lineTo(size.width * 0.16f, size.height * 0.76f)
            close()
        }
        drawPath(crown, RoyalColors.Gold)
        drawRect(
            color = Color(0xFFFFE7A6),
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.16f, size.height * 0.78f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.68f, size.height * 0.12f)
        )
        drawCircle(RoyalColors.Pearl, radius = size.minDimension * 0.035f, center = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.16f))
        drawCircle(RoyalColors.Pearl, radius = size.minDimension * 0.035f, center = androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.04f))
        drawCircle(RoyalColors.Pearl, radius = size.minDimension * 0.035f, center = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.16f))
    }
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
