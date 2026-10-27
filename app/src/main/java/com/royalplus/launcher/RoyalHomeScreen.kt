package com.royalplus.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun RoyalHomeScreen(
    apps: List<LauncherApp>,
    weather: WeatherUiState,
    onRequestLocation: () -> Unit,
    onRefreshWeather: () -> Unit,
    onLaunchApp: (LauncherApp) -> Unit
) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

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
        SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).apply {
            timeZone = displayTimeZone
        }.format(Date(currentTime))
    }
    val timeText = remember(currentTime, weather.timeZoneId) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).apply {
            timeZone = displayTimeZone
        }.format(Date(currentTime))
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = RoyalColors.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RoyalColors.Black)
                .padding(horizontal = 40.dp, vertical = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ROYAL+",
                        color = RoyalColors.Gold,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                    Text(
                        text = "SMART TV EXPERIENCE",
                        color = RoyalColors.Muted,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = dateText,
                        color = RoyalColors.Pearl,
                        fontSize = 14.sp
                    )
                    Text(
                        text = timeText,
                        color = RoyalColors.Gold,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .border(
                                1.dp,
                                RoyalColors.Gold.copy(alpha = 0.55f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            val temperatureText = weather.temperatureC?.let { "$it°C  •  ${weather.description}" }
                                ?: weather.description
                            Text(
                                text = temperatureText,
                                color = RoyalColors.Pearl,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = weather.city,
                                color = RoyalColors.Muted,
                                fontSize = 11.sp
                            )
                            if (weather.needsPermission) {
                                Text(
                                    text = "السماح بالموقع",
                                    color = RoyalColors.Gold,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clickable(onClick = onRequestLocation)
                                        .focusable()
                                        .padding(top = 3.dp)
                                )
                            } else if (!weather.isLoading && weather.temperatureC == null) {
                                Text(
                                    text = "إعادة المحاولة",
                                    color = RoyalColors.Gold,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clickable(onClick = onRefreshWeather)
                                        .focusable()
                                        .padding(top = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your screen. Your kingdom.",
                        color = RoyalColors.Pearl,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Choose an app to begin",
                        color = RoyalColors.Muted,
                        fontSize = 15.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .border(
                            1.dp,
                            RoyalColors.Gold.copy(alpha = 0.65f),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "HOME",
                        color = RoyalColors.Gold,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            if (apps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No launchable apps found on this device yet.",
                        color = RoyalColors.Muted,
                        fontSize = 18.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        Card(
                            onClick = { onLaunchApp(app) },
                            modifier = Modifier
                                .height(112.dp)
                                .focusable(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = RoyalColors.Panel
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 2.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = app.label,
                                    color = RoyalColors.Pearl,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "OPEN APP  ›",
                                    color = RoyalColors.Gold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
