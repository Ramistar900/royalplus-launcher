package com.royalplus.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun RoyalInterfaceQualitySection(language: String, onRefreshApps: () -> Unit) {
    val context = LocalContext.current
    val selectedQuality by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { RoyalInterfaceQualitySettings.current } }
    val effectiveQuality = remember(context, selectedQuality) {
        RoyalInterfaceQualitySettings.budget(context, selectedQuality).effectivePreset
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            when (language) {
                "en" -> "Overall display quality"
                "tr" -> "Genel görüntü kalitesi"
                else -> "جودة العرض العامة"
            },
            color = RoyalColors.Gold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
        )
        Text(
            when (language) {
                "en" -> "One setting for supported images, videos, icons, backgrounds, banners and visual effects. It never changes screen size or text scale."
                "tr" -> "Desteklenen resimler, videolar, simgeler, arka planlar, afişler ve görsel efektler için tek ayar. Ekran veya yazı boyutunu değiştirmez."
                else -> "إعداد موحّد للصور والفيديوهات والأيقونات والخلفيات والبنرات والمؤثرات المرئية المدعومة، دون تغيير حجم الشاشة أو النصوص."
            },
            color = RoyalColors.Muted, fontSize = 10.sp
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            RoyalInterfaceQualityPreset.selectablePresets.forEach { preset ->
                val selected = selectedQuality == preset
                val label = if (preset == RoyalInterfaceQualityPreset.AUTO) {
                    when (language) { "en" -> "Auto"; "tr" -> "Otomatik"; else -> "تلقائي" }
                } else preset.label
                androidx.compose.foundation.layout.Box(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .then(royalGlassFocus(RoundedCornerShape(10.dp)))
                        .background(if (selected) RoyalColors.Gold.copy(alpha = 0.20f) else RoyalColors.Surface)
                        .border(1.dp, if (selected) RoyalColors.Gold else RoyalColors.Gold.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                        .clickable {
                            RoyalInterfaceQualitySettings.select(context, preset)
                            LauncherAppsRepository.invalidate()
                            onRefreshApps()
                        }
                        .padding(horizontal = 13.dp, vertical = 10.dp)
                ) {
                    Text(label, color = if (selected) RoyalColors.Gold else RoyalColors.Pearl,
                        fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
        Text(
            when (language) {
                "en" -> "Current device limit: ${effectiveQuality.label}. Original source quality is never artificially enlarged."
                "tr" -> "Bu cihazdaki etkin sınır: ${effectiveQuality.label}. Kaynak görüntü yapay olarak büyütülmez."
                else -> "الحد الفعلي لهذا الجهاز: ${effectiveQuality.label}. لا تتم زيادة دقة المصدر بشكل مصطنع."
            },
            color = RoyalColors.Muted, fontSize = 10.sp
        )
    }
}
