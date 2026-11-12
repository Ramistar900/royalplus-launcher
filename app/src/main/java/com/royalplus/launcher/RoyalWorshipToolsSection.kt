package com.royalplus.launcher

import com.royal.adaptive.LocalRoyalAdaptiveProfile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun RoyalWorshipToolsSection(
    latitude: Double?,
    longitude: Double?,
    language: String,
    onRequestLocation: () -> Unit
) {
    val adaptiveProfile = LocalRoyalAdaptiveProfile.current
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("royalplus_muslim_preferences", android.content.Context.MODE_PRIVATE)
    }
    var tasbeehCount by remember(preferences) {
        mutableStateOf(preferences.getInt("tasbeeh_count", 0).coerceAtLeast(0))
    }
    val qiblaBearing = remember(latitude, longitude) {
        if (latitude == null || longitude == null ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) null else calculateQiblaBearing(latitude, longitude)
    }
    val isArabic = language == "ar"
    val isTurkish = language == "tr"

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF111820))
            .border(1.dp, RoyalColors.Gold.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(adaptiveProfile.pageHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            when {
                isArabic -> "أدوات العبادة"
                isTurkish -> "İbadet araçları"
                else -> "Worship tools"
            },
            color = RoyalColors.Gold,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        isArabic -> "المسبحة الإلكترونية"
                        isTurkish -> "Dijital tesbih"
                        else -> "Digital tasbeeh"
                    },
                    color = RoyalColors.Pearl,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    tasbeehCount.toString(),
                    color = RoyalColors.Gold,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when {
                        isArabic -> "الدورة الحالية: ${(tasbeehCount % 33)}/33"
                        isTurkish -> "Geçerli tur: ${(tasbeehCount % 33)}/33"
                        else -> "Current round: ${(tasbeehCount % 33)}/33"
                    },
                    color = RoyalColors.Muted,
                    fontSize = 11.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    WorshipAction(
                        label = when { isArabic -> "سبّح +1"; isTurkish -> "Tesbih +1"; else -> "Count +1" },
                        primary = true,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (tasbeehCount < Int.MAX_VALUE) {
                            tasbeehCount += 1
                            preferences.edit().putInt("tasbeeh_count", tasbeehCount).apply()
                        }
                    }
                    WorshipAction(
                        label = when { isArabic -> "−1"; isTurkish -> "−1"; else -> "−1" },
                        modifier = Modifier.weight(0.55f)
                    ) {
                        tasbeehCount = (tasbeehCount - 1).coerceAtLeast(0)
                        preferences.edit().putInt("tasbeeh_count", tasbeehCount).apply()
                    }
                }
                WorshipAction(
                    label = when { isArabic -> "تصفير العدّاد"; isTurkish -> "Sayacı sıfırla"; else -> "Reset counter" },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tasbeehCount = 0
                    preferences.edit().putInt("tasbeeh_count", 0).apply()
                }
            }

            Box(
                Modifier.width(1.dp).height((adaptiveProfile.windowHeightDp * 0.22f).coerceIn(124.dp, 190.dp)).background(RoyalColors.Gold.copy(alpha = 0.25f))
            )

            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    when { isArabic -> "اتجاه القبلة"; isTurkish -> "Kıble yönü"; else -> "Qibla direction" },
                    color = RoyalColors.Pearl,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (qiblaBearing == null) {
                    Text(
                        when {
                            isArabic -> "يلزم تحديد الموقع أو اختيار مدينة"
                            isTurkish -> "Konum belirleyin veya şehir seçin"
                            else -> "Set a location or choose a city"
                        },
                        color = RoyalColors.Muted,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                    WorshipAction(
                        label = when { isArabic -> "تحديد موقعي"; isTurkish -> "Konumumu bul"; else -> "Find my location" },
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onRequestLocation
                    )
                } else {
                    QiblaDirectionDial(bearing = qiblaBearing)
                    Text(
                        String.format(Locale.US, "%.1f°", qiblaBearing),
                        color = RoyalColors.Gold,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when { isArabic -> "زاوية من الشمال"; isTurkish -> "Kuzeyden açı"; else -> "Bearing from north" },
                        color = RoyalColors.Muted,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        qiblaCardinalDirection(qiblaBearing, language),
                        color = RoyalColors.Pearl,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun WorshipAction(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(if (primary) RoyalColors.Gold else Color(0xFF202A35))
            .border(1.dp, RoyalColors.Gold.copy(alpha = if (primary) 1f else 0.45f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (primary) Color(0xFF151515) else RoyalColors.Pearl,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun QiblaDirectionDial(bearing: Double) {
    val adaptiveProfile = LocalRoyalAdaptiveProfile.current
    Box(Modifier.size(adaptiveProfile.iconSize(118.dp)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(adaptiveProfile.iconSize(118.dp))) {
            val radius = size.minDimension * 0.46f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = RoyalColors.Gold.copy(alpha = 0.75f),
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            for (tick in 0 until 8) {
                val angle = Math.toRadians(tick * 45.0)
                val inner = radius * 0.82f
                val outer = radius * if (tick % 2 == 0) 0.96f else 0.90f
                drawLine(
                    color = RoyalColors.Gold.copy(alpha = 0.7f),
                    start = Offset(
                        center.x + sin(angle).toFloat() * inner,
                        center.y - cos(angle).toFloat() * inner
                    ),
                    end = Offset(
                        center.x + sin(angle).toFloat() * outer,
                        center.y - cos(angle).toFloat() * outer
                    ),
                    strokeWidth = if (tick % 2 == 0) 2.dp.toPx() else 1.dp.toPx()
                )
            }
            rotate(degrees = bearing.toFloat(), pivot = center) {
                val tip = Offset(center.x, center.y - radius * 0.73f)
                val left = Offset(center.x - radius * 0.16f, center.y - radius * 0.36f)
                val right = Offset(center.x + radius * 0.16f, center.y - radius * 0.36f)
                val arrow = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(left.x, left.y)
                    lineTo(center.x, center.y - radius * 0.43f)
                    lineTo(right.x, right.y)
                    close()
                }
                drawPath(arrow, color = RoyalColors.Gold)
                drawLine(
                    color = RoyalColors.Pearl.copy(alpha = 0.65f),
                    start = center,
                    end = Offset(center.x, center.y + radius * 0.48f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            drawCircle(color = RoyalColors.Pearl, radius = 3.dp.toPx(), center = center)
        }
        Text("ش", color = RoyalColors.Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp))
    }
}

private fun calculateQiblaBearing(latitude: Double, longitude: Double): Double {
    val userLatitude = Math.toRadians(latitude)
    val kaabaLatitude = Math.toRadians(21.4225)
    val deltaLongitude = Math.toRadians(39.8262 - longitude)
    val y = sin(deltaLongitude)
    val x = cos(userLatitude) * kotlin.math.tan(kaabaLatitude) -
        sin(userLatitude) * cos(deltaLongitude)
    return ((Math.toDegrees(atan2(y, x)) % 360.0) + 360.0) % 360.0
}

private fun qiblaCardinalDirection(bearing: Double, language: String): String {
    val index = (((bearing + 22.5) / 45.0).toInt() % 8).coerceIn(0, 7)
    val directions = when (language) {
        "en" -> listOf("North", "North-east", "East", "South-east", "South", "South-west", "West", "North-west")
        "tr" -> listOf("Kuzey", "Kuzeydoğu", "Doğu", "Güneydoğu", "Güney", "Güneybatı", "Batı", "Kuzeybatı")
        else -> listOf("الشمال", "الشمال الشرقي", "الشرق", "الجنوب الشرقي", "الجنوب", "الجنوب الغربي", "الغرب", "الشمال الغربي")
    }
    return directions[index]
}
