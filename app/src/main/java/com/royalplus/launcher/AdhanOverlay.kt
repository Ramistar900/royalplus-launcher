package com.royalplus.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable

@Composable
internal fun AdhanOverlay(
    prayerName: String,
    cityName: String,
    language: String,
    isDaytime: Boolean,
    motionEnabled: Boolean = true,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    DisposableEffect(prayerName) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                onDismiss()
            }
        }
        val filter = android.content.IntentFilter(RoyalAdhanPlaybackService.ACTION_PLAYBACK_FINISHED)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    BackHandler(onBack = {
        context.stopService(android.content.Intent(context, RoyalAdhanPlaybackService::class.java))
        onDismiss()
    })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050811))
    ) {
        RoyalMotionImage(
            image = painterResource(if (isDaytime) R.drawable.royal_adhan_day else R.drawable.royal_adhan_night),
            modifier = Modifier.fillMaxSize(),
            motionEnabled = motionEnabled
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x77050811))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(190.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x337FC7E8), Color(0x007FC7E8))
                    )
                )
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("☾", color = Color(0xFFE5C46B), fontSize = 72.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(10.dp))
            Text(
                when (language) {
                    "en" -> "ROYAL+ • CALL TO PRAYER"
                    "tr" -> "ROYAL+ • EZAN VAKTİ"
                    else -> "ROYAL+ • نداء الصلاة"
                },
                color = Color(0xFFE5C46B),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Text(
                when (language) {
                    "en" -> "It's time for $prayerName prayer"
                    "tr" -> "$prayerName namazı vakti"
                    else -> "حان الآن موعد أذان $prayerName"
                },
                color = Color(0xFFFFF4D6),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 46.sp
            )
            Spacer(Modifier.height(16.dp))
            Text(
                cityName,
                color = Color(0xFFE5C46B),
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(30.dp))
            Text(
                when (language) {
                    "en" -> "The scheduled adhan is playing"
                    "tr" -> "Zamanlanan ezan çalıyor"
                    else -> "يجري تشغيل الأذان المجدول"
                },
                color = Color(0xFFC7CED9),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(42.dp))
            var focused by remember { mutableStateOf(false) }
            RowDismissButton(
                language = language,
                focused = focused,
                onFocusChanged = { focused = it },
                onClick = {
                    context.stopService(android.content.Intent(context, RoyalAdhanPlaybackService::class.java))
                    onDismiss()
                }
            )
        }
        }
    }
}

@Composable
private fun RowDismissButton(
    language: String,
    focused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (focused) Color(0xFF5A4618) else Color(0xFF17202D))
            .border(1.dp, Color(0xFFE5C46B), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .focusable()
            .padding(horizontal = 28.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Close, contentDescription = null, tint = Color(0xFFFFE7A3), modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Text(
            when (language) {
                "en" -> "Stop adhan / Close"
                "tr" -> "Ezanı durdur / Kapat"
                else -> "إيقاف الأذان وإغلاق الشاشة"
            },
            color = Color(0xFFFFE7A3),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
