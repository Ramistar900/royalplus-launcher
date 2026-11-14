package com.royalplus.launcher

import com.royal.adaptive.LocalRoyalAdaptiveProfile

import com.royalplus.launcher.royalsounds.RoyalUiSound

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun RoyalDeviceSettingsSection(language: String) {
    val adaptiveProfile = LocalRoyalAdaptiveProfile.current
    val context = LocalContext.current
    val audioPreferences = remember(context) {
        context.getSharedPreferences(RoyalWeatherAmbienceController.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var masterSoundsEnabled by remember(audioPreferences) {
        mutableStateOf(audioPreferences.getBoolean(RoyalWeatherAmbienceController.MASTER_ENABLED_KEY, true))
    }
    var rainSoundEnabled by remember(audioPreferences) {
        mutableStateOf(audioPreferences.getBoolean(RoyalWeatherAmbienceController.RAIN_ENABLED_KEY, true))
    }
    var thunderSoundEnabled by remember(audioPreferences) {
        mutableStateOf(audioPreferences.getBoolean(RoyalWeatherAmbienceController.THUNDER_ENABLED_KEY, true))
    }
    var windSoundEnabled by remember(audioPreferences) {
        mutableStateOf(audioPreferences.getBoolean(RoyalWeatherAmbienceController.WIND_ENABLED_KEY, true))
    }
    var birdsSoundEnabled by remember(audioPreferences) {
        mutableStateOf(audioPreferences.getBoolean(RoyalWeatherAmbienceController.BIRDS_ENABLED_KEY, true))
    }
    var audioVolume by remember(audioPreferences) {
        mutableStateOf(audioPreferences.getFloat(RoyalWeatherAmbienceController.VOLUME_KEY, 0.72f).coerceIn(0f, 1f))
    }
    val owlPreferences = remember(context) {
        context.getSharedPreferences(RoyalOwlAmbienceController.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var owlSoundEnabled by remember(owlPreferences) {
        mutableStateOf(owlPreferences.getBoolean(RoyalOwlAmbienceController.ENABLED_KEY, true))
    }
    val weatherIconPreferences = remember(context) {
        context.getSharedPreferences(RoyalV21WeatherIconPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var weatherIconsAnimated by remember(weatherIconPreferences) {
        mutableStateOf(
            weatherIconPreferences.getBoolean(
                RoyalV21WeatherIconPreferences.KEY_ANIMATED,
                RoyalV21WeatherIconPreferences.DEFAULT_ANIMATED
            )
        )
    }
    val sportsIconPreferences = remember(context) {
        context.getSharedPreferences(RoyalV20SportsIconPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var sportsIconsAnimated by remember(sportsIconPreferences) {
        mutableStateOf(
            sportsIconPreferences.getBoolean(
                RoyalV20SportsIconPreferences.KEY_ANIMATED,
                RoyalV20SportsIconPreferences.DEFAULT_ANIMATED
            )
        )
    }
    val storage = storageSummary(language)
    val connection = connectionSummary(context, language)
    val device = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(adaptiveProfile.itemSpacing)) {
        Text(if (language == "en") "Device & system" else if (language == "tr") "Cihaz ve sistem" else "الجهاز والنظام",
            color = RoyalColors.Gold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        SettingsInfoRow(Icons.Rounded.Storage,
            if (language == "en") "Device & storage" else if (language == "tr") "Cihaz ve depolama" else "معلومات الجهاز والتخزين",
            "$device • $storage") { openSystemSettings(context, Settings.ACTION_INTERNAL_STORAGE_SETTINGS) }
        SettingsInfoRow(Icons.Rounded.Wifi,
            if (language == "en") "Wi-Fi & connection" else if (language == "tr") "Wi-Fi ve bağlantı" else "حالة Wi-Fi والاتصال",
            connection) { openSystemSettings(context, Settings.ACTION_WIFI_SETTINGS) }
        SettingsInfoRow(Icons.Rounded.CleaningServices,
            if (language == "en") "Clear ROYAL+ cache" else if (language == "tr") "ROYAL+ önbelleğini temizle" else "تنظيف الذاكرة المؤقتة",
            if (language == "en") "Clears temporary files owned by this app only" else if (language == "tr") "Yalnızca bu uygulamanın geçici dosyalarını temizler" else "يحذف الملفات المؤقتة الخاصة باللانشر فقط") {
            val cleared = runCatching {
                var success = true
                context.cacheDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    if (file.exists() && !file.delete()) success = false
                }
                success
            }.getOrDefault(false)
            val message = if (cleared) {
                if (language == "en") "ROYAL+ temporary files cleared" else if (language == "tr") "ROYAL+ geçici dosyaları temizlendi" else "تم تنظيف الملفات المؤقتة للانشر"
            } else {
                if (language == "en") "Some temporary files could not be cleared" else if (language == "tr") "Bazı geçici dosyalar temizlenemedi" else "تعذر حذف بعض الملفات المؤقتة"
            }
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
        SettingsInfoRow(Icons.Rounded.Tv,
            if (language == "en") "Display settings" else if (language == "tr") "Ekran ayarları" else "إعدادات الشاشة",
            if (language == "en") "Screen and display options" else if (language == "tr") "Ekran seçenekleri" else "خيارات الشاشة والعرض") {
            openSystemSettings(context, Settings.ACTION_DISPLAY_SETTINGS)
        }
        SettingsInfoRow(Icons.Rounded.VolumeUp,
            if (language == "en") "Sound settings" else if (language == "tr") "Ses ayarları" else "إعدادات الصوت",
            if (language == "en") "Volume and audio output" else if (language == "tr") "Ses seviyesi ve çıkışı" else "مستوى الصوت ومخرج الصوت") {
            openSystemSettings(context, Settings.ACTION_SOUND_SETTINGS)
        }
        SettingsInfoRow(
            Icons.Rounded.WbSunny,
            if (language == "en") "V21 weather icon style" else if (language == "tr") "V21 hava durumu simge biçimi" else "نمط أيقونات الطقس V21",
            when (language) {
                "en" -> if (weatherIconsAnimated) "Animated • tap to switch to static" else "Static • tap to enable animation"
                "tr" -> if (weatherIconsAnimated) "Hareketli • sabit moda geçmek için dokun" else "Sabit • hareketi açmak için dokun"
                else -> if (weatherIconsAnimated) "متحركة • اضغط للتحويل إلى ثابتة" else "ثابتة • اضغط لتفعيل الحركة"
            }
        ) {
            weatherIconsAnimated = !weatherIconsAnimated
            RoyalV21WeatherIconPreferences.setAnimated(context, weatherIconsAnimated)
        }
        SettingsInfoRow(
            Icons.Rounded.SportsSoccer,
            if (language == "en") "V20 sports icon style" else if (language == "tr") "V20 spor simge biçimi" else "نمط أيقونات الرياضة V20",
            when (language) {
                "en" -> if (sportsIconsAnimated) "Animated • tap to switch to static" else "Static • tap to enable animation"
                "tr" -> if (sportsIconsAnimated) "Hareketli • sabit moda geçmek için dokun" else "Sabit • hareketi açmak için dokun"
                else -> if (sportsIconsAnimated) "متحركة • اضغط للتحويل إلى ثابتة" else "ثابتة • اضغط لتفعيل الحركة"
            }
        ) {
            sportsIconsAnimated = !sportsIconsAnimated
            RoyalV20SportsIconPreferences.setAnimated(context, sportsIconsAnimated)
        }
        Text(
            if (language == "en") "Nature & weather sounds" else if (language == "tr") "Doğa ve hava sesleri" else "أصوات الطبيعة والطقس",
            color = RoyalColors.Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold
        )
        val enabledLabel: (Boolean) -> String = { enabled ->
            if (enabled) {
                if (language == "en") "Enabled" else if (language == "tr") "Etkin" else "مفعّل"
            } else {
                if (language == "en") "Muted" else if (language == "tr") "Sessiz" else "مكتوم"
            }
        }
        val mutedByMaster = if (language == "en") "Muted by master switch" else if (language == "tr") "Ana anahtar kapalı" else "مكتوم بسبب كتم جميع الأصوات"
        SettingsInfoRow(
            Icons.Rounded.VolumeUp,
            if (language == "en") "Mute all nature sounds" else if (language == "tr") "Tüm doğa seslerini kapat" else "كتم جميع الأصوات",
            if (masterSoundsEnabled) {
                if (language == "en") "All automatic weather and nature sounds are allowed • tap to mute"
                else if (language == "tr") "Tüm otomatik hava ve doğa sesleri açık • kapatmak için dokunun"
                else "الأصوات التلقائية مسموحة • اضغط لكتمها"
            } else {
                if (language == "en") "All nature and weather sounds are muted • tap to enable"
                else if (language == "tr") "Tüm doğa ve hava sesleri kapalı • açmak için dokunun"
                else "جميع أصوات الطبيعة والطقس مكتومة • اضغط للتشغيل"
            }
        ) {
            masterSoundsEnabled = !masterSoundsEnabled
            RoyalWeatherAmbienceController.setSoundEnabled(context, RoyalWeatherAmbienceController.MASTER_ENABLED_KEY, masterSoundsEnabled)
        }
        SettingsInfoRow(Icons.Rounded.VolumeUp,
            if (language == "en") "Rain" else if (language == "tr") "Yağmur" else "المطر",
            if (!masterSoundsEnabled) mutedByMaster else enabledLabel(rainSoundEnabled)) {
            rainSoundEnabled = !rainSoundEnabled
            RoyalWeatherAmbienceController.setSoundEnabled(context, RoyalWeatherAmbienceController.RAIN_ENABLED_KEY, rainSoundEnabled)
        }
        SettingsInfoRow(Icons.Rounded.VolumeUp,
            if (language == "en") "Thunder" else if (language == "tr") "Gök gürültüsü" else "الرعد",
            if (!masterSoundsEnabled) mutedByMaster else enabledLabel(thunderSoundEnabled)) {
            thunderSoundEnabled = !thunderSoundEnabled
            RoyalWeatherAmbienceController.setSoundEnabled(context, RoyalWeatherAmbienceController.THUNDER_ENABLED_KEY, thunderSoundEnabled)
        }
        SettingsInfoRow(Icons.Rounded.VolumeUp,
            if (language == "en") "Wind" else if (language == "tr") "Rüzgâr" else "الرياح",
            if (!masterSoundsEnabled) mutedByMaster else enabledLabel(windSoundEnabled)) {
            windSoundEnabled = !windSoundEnabled
            RoyalWeatherAmbienceController.setSoundEnabled(context, RoyalWeatherAmbienceController.WIND_ENABLED_KEY, windSoundEnabled)
        }
        SettingsInfoRow(Icons.Rounded.VolumeUp,
            if (language == "en") "Morning birds" else if (language == "tr") "Sabah kuşları" else "عصافير الصباح",
            if (!masterSoundsEnabled) mutedByMaster else enabledLabel(birdsSoundEnabled)) {
            birdsSoundEnabled = !birdsSoundEnabled
            RoyalWeatherAmbienceController.setSoundEnabled(context, RoyalWeatherAmbienceController.BIRDS_ENABLED_KEY, birdsSoundEnabled)
        }
        SettingsInfoRow(
            Icons.Rounded.VolumeUp,
            if (language == "en") "Night owl call" else if (language == "tr") "Gece baykuş sesi" else "نداء البومة الليلي",
            if (owlSoundEnabled) {
                if (language == "en") "Enabled • clear nights only; volume follows device media level"
                else if (language == "tr") "Açık • yalnızca açık gecelerde; ses cihazın medya düzeyini izler"
                else "مفعّل • في الليالي الصافية فقط؛ مستوى الصوت من الجهاز"
            } else {
                if (language == "en") "Muted • tap to enable"
                else if (language == "tr") "Sessiz • açmak için dokunun"
                else "مكتوم • اضغط للتشغيل"
            }
        ) {
            owlSoundEnabled = !owlSoundEnabled
            RoyalOwlAmbienceController.setEnabled(context, owlSoundEnabled)
        }
        Text(
            (if (language == "en") "Sound level: " else if (language == "tr") "Ses seviyesi: " else "مستوى الصوت: ") +
                (audioVolume * 100f).toInt().toString() + "%",
            color = RoyalColors.Pearl, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
        )
        Slider(
            value = audioVolume,
            onValueChange = { value ->
                audioVolume = value
                RoyalWeatherAmbienceController.setVolume(context, value)
            },
            valueRange = 0f..1f
        )
        SettingsInfoRow(Icons.Rounded.Info,
            if (language == "en") "System information" else if (language == "tr") "Sistem bilgileri" else "معلومات النظام وإصدار Android",
            androidVersion) { openSystemSettings(context, Settings.ACTION_DEVICE_INFO_SETTINGS) }
    }
}

@Composable
private fun SettingsInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit
) {
    val adaptiveProfile = LocalRoyalAdaptiveProfile.current
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(Color(0xFF080A0F))
        .border(1.dp, RoyalColors.Gold.copy(alpha = .28f), RoundedCornerShape(12.dp))
        .clickable(onClick = {
            playLauncherUiSound(context, RoyalUiSound.NAV_SELECT)
            onClick()
        }).focusable()
        .padding(horizontal = adaptiveProfile.pageHorizontalPadding, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(adaptiveProfile.iconSize(25.dp)))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = RoyalColors.Pearl, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = RoyalColors.Muted, fontSize = 10.sp)
        }
    }
}

private fun storageSummary(language: String): String {
    val stat = StatFs(Environment.getDataDirectory().path)
    val total = stat.totalBytes
    val free = stat.availableBytes
    val used = formatStorage(total - free)
    val capacity = formatStorage(total)
    return when (language) {
        "en" -> "$used used • $capacity total"
        "tr" -> "$used kullanılıyor • $capacity toplam"
        else -> "$used مستخدم • $capacity إجمالي"
    }
}

private fun formatStorage(bytes: Long): String =
    String.format(java.util.Locale.US, "%.1f GB", bytes.coerceAtLeast(0L) / (1024.0 * 1024.0 * 1024.0))

private fun connectionSummary(context: Context, language: String): String {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val network = manager?.activeNetwork
    val capabilities = if (network != null) manager?.getNetworkCapabilities(network) else null
    val en = language == "en"
    val tr = language == "tr"
    return when {
        capabilities == null -> if (en) "Not connected" else if (tr) "Bağlı değil" else "غير متصل"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> if (en) "Connected via Wi-Fi" else if (tr) "Wi-Fi üzerinden bağlı" else "متصل عبر Wi-Fi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> if (en) "Connected via Ethernet" else if (tr) "Ethernet üzerinden bağlı" else "متصل عبر Ethernet"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> if (en) "Connected via mobile network" else if (tr) "Mobil ağ üzerinden bağlı" else "متصل عبر شبكة الهاتف"
        else -> if (en) "Connected to network" else if (tr) "Ağa bağlı" else "متصل بالشبكة"
    }
}

private fun openSystemSettings(context: Context, action: String) {
    try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, "تعذر فتح إعدادات النظام", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
