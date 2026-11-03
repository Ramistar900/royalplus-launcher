package com.royalplus.launcher

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
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
    val context = LocalContext.current
    val storage = storageSummary(language)
    val connection = connectionSummary(context, language)
    val device = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
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
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(Color(0xFF080A0F))
        .border(1.dp, RoyalColors.Gold.copy(alpha = .28f), RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).focusable()
        .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(25.dp))
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
