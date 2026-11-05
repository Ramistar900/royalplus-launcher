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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun RoyalSmartScreensaver(
    timeText: String,
    dateText: String,
    weather: WeatherUiState,
    language: String,
    selectedWallpaper: String,
    onDismiss: () -> Unit,
    motionEnabled: Boolean = true
) {
    val isEnglish = language == "en"
    val isTurkish = language == "tr"
    val headline = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحَانَ اللَّهِ الْعَظِيمِ"
    val localizedWeatherDescription = when (weather.weatherCode) {
        0 -> if (isEnglish) "Clear sky" else if (isTurkish) "Açık hava" else "سماء صافية"
        1 -> if (isEnglish) "Mainly clear" else if (isTurkish) "Az bulutlu" else "صحو غالباً"
        2 -> if (isEnglish) "Partly cloudy" else if (isTurkish) "Parçalı bulutlu" else "غائم جزئياً"
        3 -> if (isEnglish) "Overcast" else if (isTurkish) "Kapalı" else "غائم كلياً"
        45, 48 -> if (isEnglish) "Fog" else if (isTurkish) "Sis" else "ضباب"
        in 51..57 -> if (isEnglish) "Drizzle" else if (isTurkish) "Çisenti" else "رذاذ"
        in 61..67, in 80..82 -> if (isEnglish) "Rain" else if (isTurkish) "Yağmur" else "مطر"
        in 71..77, 85, 86 -> if (isEnglish) "Snow" else if (isTurkish) "Kar" else "ثلج"
        in 95..99 -> if (isEnglish) "Thunderstorm" else if (isTurkish) "Gök gürültülü fırtına" else "عاصفة رعدية"
        else -> weather.description
    }
    val hint = when {
        isEnglish -> "Press any button to return"
        isTurkish -> "Dönmek için herhangi bir tuşa basın"
        else -> "اضغط أي زر للعودة"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030509))
            .clickable(onClick = onDismiss)
            .onPreviewKeyEvent { onDismiss(); true }
            .focusable()
            .focusTarget(),
        contentAlignment = Alignment.Center
    ) {
        RoyalMotionImage(
            image = painterResource(id = when (selectedWallpaper) {
                "forest" -> R.drawable.royal_wallpaper_forest_04
                "green" -> R.drawable.royal_wallpaper_grreen_07
                "lake" -> R.drawable.royal_wallpaper_lake_03
                "mountains" -> R.drawable.royal_wallpaper_mountains_02
                "nature" -> R.drawable.royal_wallpaper_nature_01
                "sunset" -> R.drawable.royal_wallpaper_sunset_05
                "waterfall" -> R.drawable.royal_wallpaper_waterfall_06
                else -> R.drawable.royal_smart_screensaver
            }),
            modifier = Modifier.fillMaxSize(),
            motionEnabled = motionEnabled
        )
        RoyalWeatherAtmosphereOverlay(weatherCode = weather.weatherCode, motionEnabled = motionEnabled)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0x44030509), Color(0x2210131B), Color(0x6605070B))))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(340.dp)
                .clip(RoundedCornerShape(50))
                .border(1.dp, RoyalColors.Gold.copy(alpha = 0.10f), RoundedCornerShape(50))
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 78.dp, height = 3.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, RoyalColors.Gold, Color.Transparent)))
            )
            Spacer(Modifier.height(22.dp))
            Text(
                "ROYAL+",
                color = RoyalColors.Gold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 7.sp
            )
            Spacer(Modifier.height(18.dp))
            Text(
                timeText,
                color = Color(0xFFFFF5DE),
                fontSize = 88.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(Modifier.height(8.dp))
            Text(
                dateText,
                color = Color(0xFFD5B878),
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .size(width = 180.dp, height = 1.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, RoyalColors.Gold.copy(alpha = 0.6f), Color.Transparent)))
            )
            Spacer(Modifier.height(24.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF111720).copy(alpha = 0.92f))
                    .border(1.dp, RoyalColors.Gold.copy(alpha = 0.24f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 22.dp, vertical = 13.dp)
            ) {
                Icon(
                    imageVector = if (weather.description.contains("cloud", true) || weather.description.contains("غائم") || weather.description.contains("سحب")) Icons.Rounded.Cloud else Icons.Rounded.WbSunny,
                    contentDescription = null,
                    tint = RoyalColors.Gold,
                    modifier = Modifier.size(27.dp)
                )
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        weather.city.ifBlank { if (isEnglish) "Weather" else if (isTurkish) "Hava durumu" else "الطقس" },
                        color = Color(0xFFFFE8B0),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        localizedWeatherDescription.ifBlank { if (isEnglish) "Weather unavailable" else if (isTurkish) "Hava durumu kullanılamıyor" else "الطقس غير متاح" },
                        color = Color(0xFFD6D9E0),
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.height(34.dp))
            Text(
                headline,
                color = RoyalColors.Gold,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp,
                maxLines = 2
            )
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(15.dp))
                Text(hint, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            }
        }
    }
}
