package com.royalplus.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private enum class LauncherDestination {
    HOME, APPS, FAVORITES, MORE
}

@Composable
internal fun RoyalHomeScreen(
    apps: List<LauncherApp>,
    weather: WeatherUiState,
    onRequestLocation: () -> Unit,
    onRefreshWeather: () -> Unit,
    onLaunchApp: (LauncherApp) -> Unit,
    onOpenSettings: () -> Unit
) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedDestination by remember { mutableStateOf(LauncherDestination.HOME) }
    var focusedAppPackage by remember { mutableStateOf<String?>(null) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

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
    val timeText = remember(currentTime, weather.timeZoneId) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
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
            RoyalBackdrop(Modifier.matchParentSize())

            val sidebarWidth = if (maxWidth >= 900.dp) 202.dp else (maxWidth * 0.225f).coerceIn(150.dp, 190.dp)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    RoyalBrandHeader(
                        searchActive = searchActive,
                        searchQuery = searchQuery,
                        onQueryChanged = { searchQuery = it },
                        onCloseSearch = {
                            searchActive = false
                            searchQuery = ""
                            selectedDestination = LauncherDestination.HOME
                        }
                    )
                    Spacer(Modifier.height(8.dp))

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val columns = when {
                                maxWidth >= 700.dp -> 6
                                maxWidth >= 570.dp -> 5
                                maxWidth >= 440.dp -> 4
                                else -> 3
                            }
                            val favoriteTileWidth = ((maxWidth - 50.dp) / 6).coerceIn(82.dp, 124.dp)

                            when (selectedDestination) {
                                LauncherDestination.HOME -> {
                                    HomeDashboard(
                                        favorites = favorites,
                                        otherApps = otherApps,
                                        columns = columns,
                                        favoriteTileWidth = favoriteTileWidth,
                                        focusedAppPackage = focusedAppPackage,
                                        onAppFocused = { focusedAppPackage = it },
                                        onLaunchApp = onLaunchApp,
                                        onExploreApps = { selectedDestination = LauncherDestination.APPS }
                                    )
                                }
                                LauncherDestination.APPS -> {
                                    AppCatalog(
                                        title = if (searchQuery.isBlank()) "جميع التطبيقات" else "نتائج البحث",
                                        apps = filteredApps,
                                        columns = columns,
                                        focusedAppPackage = focusedAppPackage,
                                        onAppFocused = { focusedAppPackage = it },
                                        onLaunchApp = onLaunchApp,
                                        initialHighlight = true
                                    )
                                }
                                LauncherDestination.FAVORITES -> {
                                    AppCatalog(
                                        title = "التطبيقات المفضلة",
                                        apps = filteredFavorites,
                                        columns = columns,
                                        focusedAppPackage = focusedAppPackage,
                                        onAppFocused = { focusedAppPackage = it },
                                        onLaunchApp = onLaunchApp,
                                        initialHighlight = true
                                    )
                                }
                                LauncherDestination.MORE -> {
                                    AppCatalog(
                                        title = "المزيد من التطبيقات",
                                        apps = otherApps,
                                        columns = columns,
                                        focusedAppPackage = focusedAppPackage,
                                        onAppFocused = { focusedAppPackage = it },
                                        onLaunchApp = onLaunchApp,
                                        initialHighlight = true
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(7.dp))
                    RoyalFooter(Modifier.fillMaxWidth().height(24.dp))
                }

                RoyalSidePanel(
                    modifier = Modifier.width(sidebarWidth).fillMaxHeight(),
                    currentTime = timeText,
                    dateText = dateText,
                    weather = weather,
                    selectedDestination = selectedDestination,
                    onNavigate = { destination ->
                        if (destination == LauncherDestination.APPS) {
                            selectedDestination = LauncherDestination.APPS
                        } else {
                            selectedDestination = destination
                            if (destination == LauncherDestination.HOME) {
                                searchActive = false
                                searchQuery = ""
                            }
                        }
                    },
                    onOpenSettings = onOpenSettings,
                    onSearch = {
                        searchActive = true
                        selectedDestination = LauncherDestination.APPS
                    },
                    onRequestLocation = onRequestLocation,
                    onRefreshWeather = onRefreshWeather
                )
            }
        }
    }
}

@Composable
private fun RoyalBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF080B12), Color(0xFF020407), Color(0xFF080A10))
            )
        )
        val lineWidth = 1.2.dp.toPx()
        val upperWave = Path().apply {
            moveTo(-size.width * 0.04f, size.height * 0.10f)
            cubicTo(size.width * 0.18f, size.height * 0.01f, size.width * 0.27f, size.height * 0.16f, size.width * 0.46f, size.height * 0.06f)
            cubicTo(size.width * 0.63f, -size.height * 0.03f, size.width * 0.81f, size.height * 0.09f, size.width * 1.04f, -size.height * 0.01f)
        }
        drawPath(
            upperWave,
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, RoyalColors.Gold.copy(alpha = 0.52f), Color(0xFF8C6428).copy(alpha = 0.12f), Color.Transparent)
            ),
            style = Stroke(width = lineWidth)
        )

        val lowerWave = Path().apply {
            moveTo(-size.width * 0.04f, size.height * 0.82f)
            cubicTo(size.width * 0.18f, size.height * 0.98f, size.width * 0.32f, size.height * 0.87f, size.width * 0.51f, size.height * 0.95f)
            cubicTo(size.width * 0.68f, size.height * 1.02f, size.width * 0.83f, size.height * 0.80f, size.width * 1.04f, size.height * 0.84f)
        }
        drawPath(
            lowerWave,
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, RoyalColors.Gold.copy(alpha = 0.72f), Color(0xFF8C6428).copy(alpha = 0.28f), Color.Transparent)
            ),
            style = Stroke(width = 2.dp.toPx())
        )

        val fineWave = Path().apply {
            moveTo(-size.width * 0.02f, size.height * 0.87f)
            cubicTo(size.width * 0.22f, size.height * 1.00f, size.width * 0.39f, size.height * 0.90f, size.width * 0.56f, size.height * 0.98f)
            cubicTo(size.width * 0.77f, size.height * 1.03f, size.width * 0.86f, size.height * 0.84f, size.width * 1.03f, size.height * 0.87f)
        }
        drawPath(
            fineWave,
            color = Color(0xFFFFE2A0).copy(alpha = 0.35f),
            style = Stroke(width = 0.7.dp.toPx())
        )
    }
}

@Composable
private fun RoyalBrandHeader(
    searchActive: Boolean,
    searchQuery: String,
    onQueryChanged: (String) -> Unit,
    onCloseSearch: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(42.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CrownMark(Modifier.size(width = 34.dp, height = 27.dp))
        Spacer(Modifier.width(9.dp))
        Text(
            text = "ROYAL+",
            color = RoyalColors.Gold,
            fontSize = 27.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.2.sp,
            maxLines = 1
        )
        Spacer(Modifier.width(11.dp))
        Text(
            text = "Launcher",
            color = RoyalColors.Pearl,
            fontSize = 15.sp,
            maxLines = 1
        )
        if (searchActive) {
            Spacer(Modifier.width(18.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(RoyalColors.Panel.copy(alpha = 0.92f))
                    .border(1.dp, RoyalColors.Gold.copy(alpha = 0.65f), RoundedCornerShape(13.dp))
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (searchQuery.isBlank()) {
                        Text("ابحث عن تطبيق", color = RoyalColors.Muted, fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onQueryChanged,
                        singleLine = true,
                        cursorBrush = SolidColor(RoyalColors.Gold),
                        textStyle = TextStyle(color = RoyalColors.Pearl, fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "إغلاق البحث",
                    tint = RoyalColors.Pearl,
                    modifier = Modifier.size(20.dp).clickable(onClick = onCloseSearch).focusable()
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
            Text(
                text = "MORE THAN JUST TV",
                color = RoyalColors.Muted.copy(alpha = 0.78f),
                fontSize = 9.sp,
                letterSpacing = 2.1.sp
            )
        }
    }
}

@Composable
private fun HomeDashboard(
    favorites: List<LauncherApp>,
    otherApps: List<LauncherApp>,
    columns: Int,
    favoriteTileWidth: Dp,
    focusedAppPackage: String?,
    onAppFocused: (String) -> Unit,
    onLaunchApp: (LauncherApp) -> Unit,
    onExploreApps: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = (maxHeight * 0.325f).coerceIn(112.dp, 151.dp)
        val favoriteHeight = 91.dp
        val gridTileHeight = if (maxWidth >= 680.dp) 82.dp else 76.dp

        Column(Modifier.fillMaxSize()) {
            RoyalScenicBanner(
                modifier = Modifier.fillMaxWidth().height(heroHeight),
                onExploreApps = onExploreApps
            )
            Spacer(Modifier.height(9.dp))
            SectionHeading(
                title = "التطبيقات المفضلة",
                icon = Icons.Rounded.Star
            )
            Spacer(Modifier.height(6.dp))
            if (favorites.isEmpty()) {
                EmptyAppsMessage(
                    message = "لا توجد تطبيقات قابلة للتشغيل على هذا الجهاز",
                    modifier = Modifier.fillMaxWidth().height(favoriteHeight)
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(favoriteHeight),
                    contentPadding = PaddingValues(end = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowItems(favorites, key = { it.packageName }) { app ->
                        val highlighted = focusedAppPackage == app.packageName ||
                            (focusedAppPackage == null && app.packageName == favorites.firstOrNull()?.packageName)
                        AppTile(
                            app = app,
                            modifier = Modifier.width(favoriteTileWidth).height(favoriteHeight),
                            highlighted = highlighted,
                            onFocused = onAppFocused,
                            onLaunch = { onLaunchApp(app) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            SectionHeading(
                title = "جميع التطبيقات",
                icon = Icons.Rounded.Apps
            )
            Spacer(Modifier.height(5.dp))
            AppGrid(
                apps = otherApps,
                columns = columns,
                tileHeight = gridTileHeight,
                modifier = Modifier.fillMaxWidth().weight(1f),
                focusedAppPackage = focusedAppPackage,
                onAppFocused = onAppFocused,
                onLaunchApp = onLaunchApp,
                initialHighlight = false
            )
        }
    }
}

@Composable
private fun RoyalScenicBanner(
    modifier: Modifier = Modifier,
    onExploreApps: () -> Unit
) {
    val shape = RoundedCornerShape(17.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .border(BorderStroke(1.dp, RoyalColors.Gold.copy(alpha = 0.85f)), shape)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF172A3D), Color(0xFF6F6D70), Color(0xFF172B35))
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFE3A1).copy(alpha = 0.85f), Color(0xFFF7B955).copy(alpha = 0.23f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(w * 0.76f, h * 0.30f),
                    radius = h * 0.72f
                ),
                radius = h * 0.72f,
                center = androidx.compose.ui.geometry.Offset(w * 0.76f, h * 0.30f)
            )

            val farPeaks = Path().apply {
                moveTo(0f, h * 0.76f)
                lineTo(w * 0.08f, h * 0.48f)
                lineTo(w * 0.15f, h * 0.58f)
                lineTo(w * 0.25f, h * 0.27f)
                lineTo(w * 0.34f, h * 0.53f)
                lineTo(w * 0.43f, h * 0.37f)
                lineTo(w * 0.52f, h * 0.62f)
                lineTo(w * 0.63f, h * 0.31f)
                lineTo(w * 0.72f, h * 0.56f)
                lineTo(w * 0.83f, h * 0.39f)
                lineTo(w * 0.92f, h * 0.61f)
                lineTo(w, h * 0.44f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(farPeaks, Color(0xFF243447))

            val snowPeaks = Path().apply {
                moveTo(w * 0.12f, h * 0.56f)
                lineTo(w * 0.25f, h * 0.17f)
                lineTo(w * 0.31f, h * 0.40f)
                lineTo(w * 0.36f, h * 0.51f)
                lineTo(w * 0.47f, h * 0.22f)
                lineTo(w * 0.55f, h * 0.51f)
                lineTo(w * 0.62f, h * 0.38f)
                lineTo(w * 0.73f, h * 0.61f)
                lineTo(w * 0.81f, h * 0.45f)
                lineTo(w * 0.90f, h * 0.62f)
                lineTo(w * 0.90f, h * 0.82f)
                lineTo(w * 0.12f, h * 0.82f)
                close()
            }
            drawPath(snowPeaks, Color(0xFF101A27))
            val snowHighlights = Path().apply {
                moveTo(w * 0.22f, h * 0.27f)
                lineTo(w * 0.25f, h * 0.17f)
                lineTo(w * 0.31f, h * 0.40f)
                lineTo(w * 0.27f, h * 0.34f)
                lineTo(w * 0.25f, h * 0.30f)
                close()
                moveTo(w * 0.44f, h * 0.31f)
                lineTo(w * 0.47f, h * 0.22f)
                lineTo(w * 0.55f, h * 0.51f)
                lineTo(w * 0.50f, h * 0.40f)
                lineTo(w * 0.47f, h * 0.34f)
                close()
            }
            drawPath(snowHighlights, Color(0xFFBCC3C5).copy(alpha = 0.60f))

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF172B35).copy(alpha = 0.15f), Color(0xFF06121B), Color(0xFF02070B)),
                    startY = h * 0.61f,
                    endY = h
                ),
                topLeft = androidx.compose.ui.geometry.Offset(0f, h * 0.61f),
                size = androidx.compose.ui.geometry.Size(w, h * 0.39f)
            )
            drawLine(
                color = RoyalColors.Gold.copy(alpha = 0.55f),
                start = androidx.compose.ui.geometry.Offset(0f, h * 0.66f),
                end = androidx.compose.ui.geometry.Offset(w, h * 0.66f),
                strokeWidth = 0.8.dp.toPx()
            )
            for (i in 0..13) {
                val y = h * (0.70f + i * 0.021f)
                val x = w * (0.30f + (i % 4) * 0.12f)
                drawLine(
                    color = Color(0xFFF4BB68).copy(alpha = 0.18f - i * 0.009f),
                    start = androidx.compose.ui.geometry.Offset(x, y),
                    end = androidx.compose.ui.geometry.Offset((x + w * (0.04f + (i % 3) * 0.035f)).coerceAtMost(w), y),
                    strokeWidth = (1.4f - i * 0.05f).coerceAtLeast(0.45f).dp.toPx()
                )
            }

            val vignette = Path().apply {
                moveTo(0f, 0f)
                lineTo(w * 0.24f, 0f)
                lineTo(w * 0.15f, h)
                lineTo(0f, h)
                close()
            }
            drawPath(vignette, Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.82f), Color.Black.copy(alpha = 0.18f))))
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent, Color.Black.copy(alpha = 0.78f))
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "مرحباً بك",
                    color = RoyalColors.Gold,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "عالمك من الترفيه أصبح أقرب إليك",
                    color = RoyalColors.Pearl,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.30f))
                        .border(1.4.dp, RoyalColors.Gold, RoundedCornerShape(50))
                        .clickable(onClick = onExploreApps)
                        .focusable()
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(17.dp))
                    Text("استكشف التطبيقات", color = RoyalColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.width(8.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CrownMark(Modifier.size(width = 43.dp, height = 31.dp))
                Text(
                    text = "ROYAL+",
                    color = RoyalColors.Gold,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    maxLines = 1
                )
                Text(
                    text = "MORE THAN JUST TV",
                    color = RoyalColors.Pearl,
                    fontSize = 7.sp,
                    letterSpacing = 1.7.sp,
                    maxLines = 1
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(5) { index ->
                Box(
                    Modifier
                        .width(if (index == 0) 16.dp else 6.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (index == 0) RoyalColors.Gold else RoyalColors.Muted.copy(alpha = 0.58f))
                )
            }
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
                    onFocused = onAppFocused,
                    onLaunch = { onLaunchApp(app) }
                )
            }
        }
    }
}

@Composable
private fun AppTile(
    app: LauncherApp,
    modifier: Modifier,
    highlighted: Boolean,
    onFocused: (String) -> Unit,
    onLaunch: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Card(
        onClick = onLaunch,
        modifier = modifier.onFocusChanged { state ->
            if (state.isFocused) onFocused(app.packageName)
        },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF070A0E).copy(alpha = 0.93f)),
        border = BorderStroke(
            if (highlighted) 2.dp else 1.dp,
            if (highlighted) Color(0xFFFFD36A) else RoyalColors.Gold.copy(alpha = 0.48f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (highlighted) 8.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val icon = app.icon
            if (icon != null) {
                Image(
                    painter = remember(icon) { BitmapPainter(icon.asImageBitmap()) },
                    contentDescription = app.label,
                    modifier = Modifier.size(42.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = app.label,
                    tint = RoyalColors.Gold,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = app.label,
                color = RoyalColors.Pearl,
                fontSize = 11.sp,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RoyalSidePanel(
    modifier: Modifier,
    currentTime: String,
    dateText: String,
    weather: WeatherUiState,
    selectedDestination: LauncherDestination,
    onNavigate: (LauncherDestination) -> Unit,
    onOpenSettings: () -> Unit,
    onSearch: () -> Unit,
    onRequestLocation: () -> Unit,
    onRefreshWeather: () -> Unit
) {
    val panelShape = RoundedCornerShape(23.dp)
    Column(
        modifier = modifier
            .clip(panelShape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xDA080A0F), Color(0xC9080A0F), Color(0xF507090D))
                ),
                panelShape
            )
            .border(1.dp, RoyalColors.Gold.copy(alpha = 0.16f), panelShape)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(29.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusIcon(Icons.Rounded.Wifi, "Wi-Fi")
            StatusIcon(Icons.Rounded.Bluetooth, "Bluetooth")
            StatusIcon(Icons.Rounded.Settings, "الإعدادات", onOpenSettings)
            StatusIcon(Icons.Rounded.Search, "البحث", onSearch)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = currentTime,
            color = RoyalColors.Pearl,
            fontSize = 31.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 0.4.sp,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = dateText,
            color = RoyalColors.Pearl.copy(alpha = 0.92f),
            fontSize = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        GoldDivider(Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = weatherIcon(weather.description),
                contentDescription = "الطقس",
                tint = RoyalColors.Gold,
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.width(5.dp))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = weather.temperatureC?.let { it.toString() + "°C" } ?: "—°C",
                    color = RoyalColors.Pearl,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = weather.city,
                    color = RoyalColors.Pearl.copy(alpha = 0.88f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = if (weather.isLoading) "جارٍ تحديث الطقس" else weather.description,
            color = RoyalColors.Muted,
            fontSize = 9.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
        )
        if (weather.needsPermission) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRequestLocation)
                    .focusable()
                    .padding(horizontal = 5.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(13.dp))
                Text("تفعيل الموقع", color = RoyalColors.Gold, fontSize = 9.sp)
            }
        } else if (!weather.isLoading && weather.temperatureC == null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRefreshWeather)
                    .focusable()
                    .padding(horizontal = 5.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(13.dp))
                Text("تحديث الطقس", color = RoyalColors.Gold, fontSize = 9.sp)
            }
        }

        Spacer(Modifier.height(9.dp))
        GoldDivider(Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))

        SidebarNavItem(
            title = "الرئيسية",
            icon = Icons.Rounded.Home,
            selected = selectedDestination == LauncherDestination.HOME,
            onClick = { onNavigate(LauncherDestination.HOME) }
        )
        Spacer(Modifier.height(5.dp))
        SidebarNavItem(
            title = "التطبيقات",
            icon = Icons.Rounded.Apps,
            selected = selectedDestination == LauncherDestination.APPS,
            onClick = { onNavigate(LauncherDestination.APPS) }
        )
        Spacer(Modifier.height(5.dp))
        SidebarNavItem(
            title = "الإعدادات",
            icon = Icons.Rounded.Settings,
            selected = false,
            onClick = onOpenSettings
        )
        Spacer(Modifier.height(5.dp))
        SidebarNavItem(
            title = "المفضلة",
            icon = Icons.Rounded.Favorite,
            selected = selectedDestination == LauncherDestination.FAVORITES,
            onClick = { onNavigate(LauncherDestination.FAVORITES) }
        )
        Spacer(Modifier.height(5.dp))
        SidebarNavItem(
            title = "المزيد",
            icon = Icons.Rounded.MoreHoriz,
            selected = selectedDestination == LauncherDestination.MORE,
            onClick = { onNavigate(LauncherDestination.MORE) }
        )

        Spacer(Modifier.weight(1f))
        GoldDivider(Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CrownMark(Modifier.size(width = 20.dp, height = 15.dp))
            Spacer(Modifier.width(4.dp))
            Text("ROYAL+", color = RoyalColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Text("MORE THAN JUST TV", color = RoyalColors.Muted, fontSize = 7.sp, letterSpacing = 1.1.sp)
    }
}

@Composable
private fun StatusIcon(
    icon: ImageVector,
    description: String,
    onClick: (() -> Unit)? = null
) {
    val clickModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick).focusable()
    Box(
        modifier = Modifier.size(28.dp).then(clickModifier),
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
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .background(
                if (selected) Brush.horizontalGradient(
                    listOf(Color(0xFFD5A64F).copy(alpha = 0.78f), Color(0xFF6D4C1A).copy(alpha = 0.55f), Color.Transparent)
                ) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
            )
            .border(
                if (selected) 1.2.dp else 0.6.dp,
                if (selected) RoyalColors.Gold else RoyalColors.Gold.copy(alpha = 0.10f),
                shape
            )
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = if (selected) Color(0xFFFFE2A1) else RoyalColors.Pearl,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (selected) Color(0xFFFFD36A) else RoyalColors.Pearl.copy(alpha = 0.90f),
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
private fun RoyalFooter(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CrownMark(Modifier.size(width = 21.dp, height = 16.dp))
        Spacer(Modifier.width(4.dp))
        Text("ROYAL+", color = RoyalColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Spacer(Modifier.width(11.dp))
        Box(Modifier.width(1.dp).height(14.dp).background(RoyalColors.Gold.copy(alpha = 0.55f)))
        Spacer(Modifier.width(11.dp))
        Text(
            text = "استمتع بتجربة مشاهدة استثنائية",
            color = RoyalColors.Pearl.copy(alpha = 0.92f),
            fontSize = 10.sp,
            maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.weight(0.35f).height(1.dp).background(
                Brush.horizontalGradient(listOf(RoyalColors.Gold.copy(alpha = 0.1f), RoyalColors.Gold.copy(alpha = 0.65f)))
            )
        )
        Spacer(Modifier.width(9.dp))
        Text("ROYAL+ Launcher", color = RoyalColors.Muted, fontSize = 9.sp, maxLines = 1)
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
