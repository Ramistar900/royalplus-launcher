package com.royalplus.launcher

import com.royal.adaptive.LocalRoyalAdaptiveProfile

import com.royalplus.launcher.royalsounds.RoyalUiSound

import android.media.AudioAttributes
import android.media.MediaPlayer
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
internal fun QuranMossadSelectionsScreen(onClose: () -> Unit) {
    val adaptiveProfile = LocalRoyalAdaptiveProfile.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { MediaPlayer() }
    var downloaded by remember {
        mutableStateOf(QuranMossadSelectionsRepository.downloadedIds(context.applicationContext))
    }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var playingId by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("نزّل التلاوة أولاً لتصبح متاحة للتشغيل دون إنترنت") }

    DisposableEffect(player) {
        player.setOnCompletionListener {
            isPlaying = false
            playingId = null
            status = "انتهت التلاوة"
        }
        player.setOnErrorListener { _, _, _ ->
            isPlaying = false
            playingId = null
            status = "تعذر تشغيل الملف الصوتي"
            true
        }
        onDispose {
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.reset() }
            runCatching { player.release() }
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Color(0xF00A111B))
            .padding(horizontal = adaptiveProfile.pageHorizontalPadding, vertical = adaptiveProfile.pageVerticalPadding)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(Color(0xFF1C2937))
                        .border(1.dp, RoyalColors.Gold.copy(alpha = 0.65f), CircleShape)
                        .clickable(onClick = {
                            playLauncherUiSound(context, RoyalUiSound.NAV_BACK)
                            onClose()
                        })
                        .focusable(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("‹", color = RoyalColors.Gold, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("مختارات عبد الرحمن مسعد", color = RoyalColors.Gold, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("17 تلاوة مختارة • حفص عن عاصم", color = RoyalColors.Pearl, fontSize = 11.sp)
                }
                Text("${downloaded.size}/17", color = RoyalColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                status,
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF172536))
                    .border(1.dp, RoyalColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                color = RoyalColors.Pearl,
                fontSize = 11.sp
            )

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(QuranMossadSelectionsRepository.selections, key = { it.id }) { selection ->
                    val isDownloaded = selection.id in downloaded
                    val isCurrent = selection.id == playingId
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrent) Color(0xFF24413F) else Color(0xFF172332))
                            .border(
                                1.dp,
                                if (isCurrent) RoyalColors.Gold else Color.White.copy(alpha = 0.09f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(selection.title, color = RoyalColors.Pearl, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(selection.category, color = RoyalColors.Muted, fontSize = 10.sp)
                        }
                        Box(
                            Modifier.width(if (adaptiveProfile.isTelevision) 104.dp else 82.dp).clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFF203B3A))
                                .border(1.dp, RoyalColors.Gold.copy(alpha = 0.55f), RoundedCornerShape(9.dp))
                                .clickable(enabled = downloadingId == null) {
                                    playLauncherUiSound(
                                        context,
                                        if (!isDownloaded) RoyalUiSound.ACTION_DOWNLOAD
                                        else if (isPlaying) RoyalUiSound.MEDIA_PAUSE
                                        else RoyalUiSound.MEDIA_PLAY
                                    )
                                    if (!isDownloaded) {
                                        downloadingId = selection.id
                                        status = "جارٍ تنزيل ${selection.title}…"
                                        scope.launch {
                                            val file = QuranMossadSelectionsRepository.download(context.applicationContext, selection)
                                            if (file != null) {
                                                playLauncherUiSound(context, RoyalUiSound.ACTION_SUCCESS)
                                                downloaded = downloaded + selection.id
                                                status = "اكتمل التنزيل • أصبحت التلاوة متاحة دون إنترنت"
                                            } else {
                                                playLauncherUiSound(context, RoyalUiSound.ACTION_ERROR)
                                                status = "تعذر تنزيل التلاوة. تحقق من الاتصال ثم أعد المحاولة"
                                            }
                                            downloadingId = null
                                        }
                                    } else {
                                        val file = QuranMossadSelectionsRepository.fileFor(context.applicationContext, selection)
                                        if (!file.isFile || file.length() <= 1_024) {
                                            downloaded = QuranMossadSelectionsRepository.downloadedIds(context.applicationContext)
                                            status = "الملف غير مكتمل؛ أعد تنزيل التلاوة"
                                        } else {
                                            runCatching {
                                                player.reset()
                                                player.setAudioAttributes(
                                                    AudioAttributes.Builder()
                                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                                        .build()
                                                )
                                                player.setDataSource(file.absolutePath)
                                                player.setOnPreparedListener { prepared ->
                                                    prepared.start()
                                                    playingId = selection.id
                                                    isPlaying = true
                                                    status = "تعمل الآن دون إنترنت • ${selection.title}"
                                                }
                                                player.prepareAsync()
                                                playingId = selection.id
                                                isPlaying = false
                                                status = "جارٍ تجهيز التلاوة…"
                                            }.onFailure {
                                                playingId = null
                                                status = "تعذر فتح ملف التلاوة"
                                            }
                                        }
                                    }
                                }
                                .focusable(enabled = downloadingId == null)
                                .padding(horizontal = 7.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                when {
                                    downloadingId == selection.id -> "…"
                                    isDownloaded && isCurrent && isPlaying -> "تعمل ▶"
                                    isDownloaded -> "تشغيل ▶"
                                    else -> "تنزيل ↓"
                                },
                                color = RoyalColors.Gold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier.clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFF1C2937))
                        .border(1.dp, RoyalColors.Gold.copy(alpha = 0.55f), RoundedCornerShape(9.dp))
                        .clickable(onClick = onClose)
                        .focusable()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("إغلاق", color = RoyalColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
