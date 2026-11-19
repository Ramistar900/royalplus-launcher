package com.royalplus.launcher

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun RoyalPrivacySettingsSection(language: String) {
    val context = LocalContext.current
    val english = language == "en"
    val turkish = language == "tr"
    val microphoneGranted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val locationGranted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val overlayGranted = Settings.canDrawOverlays(context)
    val exactAlarmsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.canScheduleExactAlarms() == true

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            when { english -> "Privacy & permissions"; turkish -> "Gizlilik ve izinler"; else -> "الخصوصية والأذونات" },
            color = RoyalColors.Gold,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            when {
                english -> "Review the access ROYAL+ uses for voice translation, local weather, notifications and floating controls. Android remains in control of every permission."
                turkish -> "ROYAL+'ın sesli çeviri, yerel hava durumu, bildirimler ve kayan kontroller için kullandığı izinleri inceleyin. Her izni Android yönetir."
                else -> "راجع الأذونات التي يستخدمها ROYAL+ للترجمة الصوتية والطقس المحلي والإشعارات والعناصر العائمة. يبقى أندرويد المتحكم بكل إذن."
            },
            color = RoyalColors.Muted,
            fontSize = 11.sp
        )
        PrivacyPermissionRow(
            icon = Icons.Rounded.Mic,
            title = when { english -> "Microphone"; turkish -> "Mikrofon"; else -> "الميكروفون" },
            granted = microphoneGranted,
            language = language,
            detail = when {
                microphoneGranted && english -> "Allowed • used only when you enable voice translation or voice search"
                microphoneGranted && turkish -> "İzin verildi • yalnızca sesli çeviri veya sesli arama açıldığında kullanılır"
                microphoneGranted -> "مسموح • يُستخدم عند تشغيل الترجمة أو البحث الصوتي فقط"
                english -> "Not allowed • voice translation will ask for access when started"
                turkish -> "İzin verilmedi • sesli çeviri başlatıldığında izin ister"
                else -> "غير مسموح • سيطلب التطبيق الإذن عند تشغيل الترجمة الصوتية"
            }
        ) { openRoyalAppPermissionSettings(context) }
        PrivacyPermissionRow(
            icon = Icons.Rounded.Visibility,
            title = when { english -> "Floating window"; turkish -> "Kayan pencere"; else -> "الظهور فوق التطبيقات" },
            granted = overlayGranted,
            language = language,
            detail = when {
                overlayGranted && english -> "Allowed • needed only for the floating translation panel"
                overlayGranted && turkish -> "İzin verildi • yalnızca kayan çeviri paneli için gerekir"
                overlayGranted -> "مسموح • مطلوب للوحة الترجمة العائمة فقط"
                english -> "Not allowed • enable it only if you use floating translation"
                turkish -> "İzin verilmedi • yalnızca kayan çeviriyi kullanıyorsanız açın"
                else -> "غير مسموح • فعّله فقط عند استخدام الترجمة العائمة"
            }
        ) {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            openRoyalSettings(context, intent)
        }
        PrivacyPermissionRow(
            icon = Icons.Rounded.Storage,
            title = when { english -> "Location"; turkish -> "Konum"; else -> "الموقع" },
            granted = locationGranted,
            language = language,
            detail = when {
                locationGranted && english -> "Allowed • used for weather when device location is selected"
                locationGranted && turkish -> "İzin verildi • cihaz konumu seçildiğinde hava durumu için kullanılır"
                locationGranted -> "مسموح • يُستخدم للطقس عند اختيار موقع الجهاز"
                english -> "Not allowed • manual city selection remains available"
                turkish -> "İzin verilmedi • şehir elle seçilebilir"
                else -> "غير مسموح • يبقى اختيار المدينة يدوياً متاحاً"
            }
        ) { openRoyalAppPermissionSettings(context) }
        PrivacyPermissionRow(
            icon = Icons.Rounded.NotificationsActive,
            title = when { english -> "Notifications"; turkish -> "Bildirimler"; else -> "الإشعارات" },
            granted = notificationsGranted,
            language = language,
            detail = if (Build.VERSION.SDK_INT < 33) {
                when { english -> "Managed by Android on this version"; turkish -> "Bu Android sürümünde sistem tarafından yönetilir"; else -> "يديرها النظام في هذا الإصدار من أندرويد" }
            } else when {
                notificationsGranted && english -> "Allowed"
                notificationsGranted && turkish -> "İzin verildi"
                notificationsGranted -> "مسموح"
                english -> "Not allowed • reminders may not appear"
                turkish -> "İzin verilmedi • hatırlatmalar görünmeyebilir"
                else -> "غير مسموح • قد لا تظهر التذكيرات"
            }
        ) { openRoyalAppPermissionSettings(context) }
        PrivacyPermissionRow(
            icon = Icons.Rounded.Security,
            title = when { english -> "Exact alarms"; turkish -> "Kesin alarmlar"; else -> "المنبّهات الدقيقة" },
            granted = exactAlarmsAllowed,
            language = language,
            detail = when {
                exactAlarmsAllowed && english -> "Available • used for accurate prayer reminders"
                exactAlarmsAllowed && turkish -> "Kullanılabilir • namaz hatırlatmalarını doğru zamanda verir"
                exactAlarmsAllowed -> "متاحة • تساعد على دقة تنبيهات الصلاة"
                english -> "Not allowed • prayer reminders may be delayed"
                turkish -> "İzin verilmedi • namaz hatırlatmaları gecikebilir"
                else -> "غير مسموح • قد تتأخر تنبيهات الصلاة"
            }
        ) {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            }
            openRoyalSettings(context, intent)
        }
        RoyalSettingsActionRow(
            icon = Icons.Rounded.Security,
            title = when { english -> "Open Android app permissions"; turkish -> "Android uygulama izinlerini aç"; else -> "فتح أذونات التطبيق في أندرويد" },
            detail = when { english -> "Change or revoke permissions in system settings"; turkish -> "İzinleri sistem ayarlarından değiştirin veya kaldırın"; else -> "غيّر الأذونات أو ألغها من إعدادات النظام" }
        ) { openRoyalAppPermissionSettings(context) }
        Text(
            when {
                english -> "No permission is requested automatically from this page. Choose a permission above to open the matching Android settings screen."
                turkish -> "Bu sayfa hiçbir izni otomatik olarak istemez. İlgili Android ayarını açmak için yukarıdan bir izin seçin."
                else -> "لا تطلب هذه الصفحة أي إذن تلقائياً. اختر الإذن أعلاه لفتح صفحة إعداداته في أندرويد."
            },
            color = RoyalColors.Muted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun PrivacyPermissionRow(
    icon: ImageVector,
    title: String,
    granted: Boolean,
    language: String,
    detail: String,
    onClick: () -> Unit
) {
    val english = language == "en"
    val turkish = language == "tr"
    val status = when {
        granted && english -> "Allowed"
        granted && turkish -> "İzin verildi"
        granted -> "مسموح"
        english -> "Not allowed"
        turkish -> "İzin verilmedi"
        else -> "غير مسموح"
    }
    val statusColor = if (granted) RoyalColors.Turquoise else RoyalColors.Gold
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(RoyalColors.Surface)
            .border(1.dp, RoyalColors.Gold.copy(alpha = 0.32f), shape)
            .then(royalGlassFocus(shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(23.dp))
        Spacer(Modifier.size(11.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = RoyalColors.Pearl, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = RoyalColors.Muted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(status, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RoyalSettingsActionRow(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(RoyalColors.Panel)
            .border(1.dp, RoyalColors.Gold.copy(alpha = 0.52f), shape)
            .then(if (onClick != null) royalGlassFocus(shape).clickable { onClick() } else Modifier)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(23.dp))
        Spacer(Modifier.size(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = RoyalColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = RoyalColors.Muted, fontSize = 10.sp)
        }
    }
}

@Composable
internal fun RoyalAboutSettingsSection(language: String) {
    val context = LocalContext.current
    val english = language == "en"
    val turkish = language == "tr"
    val version = remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
            (info.versionName ?: "1.0.0") + " • Build " + code
        }.getOrDefault("Unknown")
    }
    val device = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            when { english -> "About ROYAL+"; turkish -> "ROYAL+ hakkında"; else -> "حول ROYAL+" },
            color = RoyalColors.Gold,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        RoyalSettingsActionRow(
            icon = Icons.Rounded.Info,
            title = when { english -> "ROYAL+ Launcher"; turkish -> "ROYAL+ Launcher"; else -> "ROYAL+ Launcher" },
            detail = if (english) "Version $version" else if (turkish) "Sürüm $version" else "الإصدار $version"
        ) { openRoyalAppPermissionSettings(context) }
        RoyalSettingsActionRow(
            icon = Icons.Rounded.Info,
            title = when { english -> "Developer"; turkish -> "Geliştirici"; else -> "المطوّر" },
            detail = when {
                english -> "The Royal Developer • Rami Al-Ahmad"
                turkish -> "Royal Geliştirici • Rami Al-Ahmad"
                else -> "المطور الملكي • رامي الأحمد"
            }
        )
        RoyalSettingsActionRow(
            icon = Icons.Rounded.Storage,
            title = when { english -> "Device information"; turkish -> "Cihaz bilgileri"; else -> "معلومات الجهاز" },
            detail = "$device • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        ) { openRoyalSettings(context, Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) }
        RoyalSettingsActionRow(
            icon = Icons.Rounded.Info,
            title = when { english -> "Project and support"; turkish -> "Proje ve destek"; else -> "المشروع والدعم" },
            detail = when {
                english -> "Open the official ROYAL+ repository"
                turkish -> "Resmî ROYAL+ deposunu açın"
                else -> "فتح مستودع ROYAL+ الرسمي"
            }
        ) { openRoyalSettings(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Ramistar900/royalplus-launcher"))) }
        RoyalSettingsActionRow(
            icon = Icons.Rounded.Storage,
            title = when { english -> "Latest official release"; turkish -> "En son resmî sürüm"; else -> "أحدث إصدار رسمي" },
            detail = when {
                english -> "View published APK downloads and release notes"
                turkish -> "Yayımlanan APK dosyalarını ve sürüm notlarını görün"
                else -> "عرض تنزيلات APK المنشورة وملاحظات الإصدار"
            }
        ) { openRoyalSettings(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Ramistar900/royalplus-launcher/releases/latest"))) }
    }
}

private fun openRoyalAppPermissionSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )
    openRoyalSettings(context, intent)
}

private fun openRoyalSettings(context: Context, intent: Intent) {
    try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, "تعذر فتح إعدادات أندرويد", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
