package com.royalplus.launcher

import com.royalplus.launcher.royalsounds.RoyalUiSound

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class RoyalQuickControl(
    val titleAr: String,
    val titleEn: String,
    val titleTr: String,
    val subtitleAr: String,
    val icon: Int,
    val action: () -> Unit
)

@Composable
internal fun RoyalQuickControlCenter(
    language: String,
    onDismiss: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenMuslim: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenPersonalization: () -> Unit,
    onRefreshWeather: () -> Unit
) {
    val context = LocalContext.current
    val firstTileFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstTileFocusRequester.requestFocus() }
    val controls = listOf(
        RoyalQuickControl("شبكة Wi-Fi", "Wi-Fi", "Wi-Fi", "إعدادات الاتصال", R.drawable.royal_wifi) {
            openQuickSystemSettings(context, Settings.ACTION_WIFI_SETTINGS, language)
        },
        RoyalQuickControl("بلوتوث", "Bluetooth", "Bluetooth", "الأجهزة اللاسلكية", R.drawable.royal_bluetooth) {
            openQuickSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS, language)
        },
        RoyalQuickControl("الشاشة والعرض", "Display", "Ekran", "دقة الشاشة والعرض", R.drawable.royal_smart_tv) {
            openQuickSystemSettings(context, Settings.ACTION_DISPLAY_SETTINGS, language)
        },
        RoyalQuickControl("الصوت", "Sound", "Ses", "مستوى الصوت والإخراج", R.drawable.royal_volume_up) {
            openQuickSystemSettings(context, Settings.ACTION_SOUND_SETTINGS, language)
        },
        RoyalQuickControl("التخزين", "Storage", "Depolama", "مساحة الجهاز", R.drawable.royal_package) {
            openQuickSystemSettings(context, Settings.ACTION_INTERNAL_STORAGE_SETTINGS, language)
        },
        RoyalQuickControl("التطبيقات", "Apps", "Uygulamalar", "فتح جميع التطبيقات", R.drawable.royal_apps_grid, onOpenApps),
        RoyalQuickControl("الموقع", "Location", "Konum", "إعدادات تحديد الموقع", R.drawable.royal_dashboard) {
            openQuickSystemSettings(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS, language)
        },
        RoyalQuickControl("واجهة التلفاز", "TV launcher", "TV başlatıcı", "اختيار الواجهة الافتراضية", R.drawable.royal_smart_tv) {
            openQuickSystemSettings(context, Settings.ACTION_HOME_SETTINGS, language)
        },
        RoyalQuickControl("تحديث الطقس", "Refresh weather", "Hava durumunu yenile", "جلب أحدث حالة الطقس", R.drawable.royal_refresh, onRefreshWeather),
        RoyalQuickControl("تخصيص الواجهة", "Personalize", "Arayüzü özelleştir", "الخلفية والخط والأيقونات", R.drawable.royal_settings, onOpenPersonalization),
        RoyalQuickControl("القسم الإسلامي", "Muslim", "Müslüman", "الصلاة والقرآن والأذكار", R.drawable.royal_security, onOpenMuslim),
        RoyalQuickControl("المعرض", "Gallery", "Galeri", "الصور والوسائط", R.drawable.royal_gallery, onOpenGallery),
        RoyalQuickControl("الأغاني", "Music", "Müzik", "مكتبة الموسيقى", R.drawable.royal_music_note, onOpenSongs)
    )
    val title = when (language) { "en" -> "Quick Control Center"; "tr" -> "Hızlı Kontrol Merkezi"; else -> "مركز التحكم السريع" }
    val subtitle = when (language) { "en" -> "Device shortcuts"; "tr" -> "Cihaz kısayolları"; else -> "اختصارات التلفاز والجهاز" }
    Box(
        Modifier.fillMaxSize()
            .background(Color(0xD9000000))
            .clickable(onClick = {
                playLauncherUiSound(context, RoyalUiSound.NAV_CLOSE_MENU)
                onDismiss()
            }),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth(0.92f)
                .widthIn(max = 850.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF0C1018))
                .border(1.5.dp, Color(0xFFD6B25E).copy(alpha = 0.78f), RoundedCornerShape(26.dp))
                .clickable(enabled = false) {}
                .padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.royal_dashboard),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color(0xFFFFD36A), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = Color(0xFFB8C0CC), fontSize = 11.sp)
                }
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1B202B))
                        .clickable(onClick = onDismiss)
                        .focusable(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = if (language == "en") "Close" else if (language == "tr") "Kapat" else "إغلاق",
                        tint = Color(0xFFFFD36A), modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().height(390.dp),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(controls) { control ->
                    RoyalQuickControlTile(
                        title = when (language) { "en" -> control.titleEn; "tr" -> control.titleTr; else -> control.titleAr },
                        subtitle = if (language == "ar") control.subtitleAr else "",
                        icon = control.icon,
                        focusRequester = if (control === controls.first()) firstTileFocusRequester else null,
                        onClick = {
                            onDismiss()
                            control.action()
                        }
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                when (language) {
                    "en" -> "Use the TV remote arrows and OK to choose a shortcut"
                    "tr" -> "Bir kısayol seçmek için TV kumandasındaki okları ve OK tuşunu kullanın"
                    else -> "استخدم أسهم الريموت وزر OK لاختيار الوظيفة"
                },
                color = Color(0xFF8792A2), fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun RoyalQuickControlTile(
    title: String,
    subtitle: String,
    icon: Int,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    var focused = remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(17.dp)
    Column(
        Modifier.fillMaxWidth().height(82.dp)
            .clip(shape)
            .background(if (focused.value) Color(0xFF2A2110) else Color(0xFF151B25))
            .border(
                if (focused.value) 2.dp else 1.dp,
                if (focused.value) Color(0xFFFFD36A) else Color(0x66D6B25E),
                shape
            )
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .clickable(onClick = {
                playLauncherUiSound(context, RoyalUiSound.NAV_SELECT)
                onClick()
            })
            .onFocusChanged { focused.value = it.isFocused }
            .focusable()
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(30.dp)
        )
        Spacer(Modifier.height(5.dp))
        Text(title, color = if (focused.value) Color(0xFFFFD36A) else Color(0xFFF2F0E9),
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = Color(0xFF9BA5B4), fontSize = 8.sp, maxLines = 1)
        }
    }
}

private fun openQuickSystemSettings(context: Context, action: String, language: String) {
    try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(
                context,
                when (language) { "en" -> "This settings page is not available on this device"; "tr" -> "Bu ayar sayfası cihazda kullanılamıyor"; else -> "هذه الصفحة غير متاحة في إعدادات هذا الجهاز" },
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
