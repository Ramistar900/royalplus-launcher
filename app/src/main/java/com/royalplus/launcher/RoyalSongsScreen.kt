package com.royalplus.launcher

import com.royalplus.launcher.royalsounds.RoyalUiSound

import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class RoyalSong(val uri: Uri, val title: String)

@Composable
internal fun RoyalSongsScreen() {
    val context = LocalContext.current
    val songs = remember { mutableStateListOf<RoyalSong>() }
    var currentIndex by remember { mutableStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { selected ->
        selected.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            if (songs.none { it.uri == uri }) {
                val title = runCatching {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "أغنية"
                songs.add(RoyalSong(uri, title))
            }
        }
    }

    fun playSong(index: Int) {
        if (index !in songs.indices) return
        runCatching {
            player?.release()
            player = null
            isPlaying = false
            playbackError = null
            MediaPlayer().also { media ->
                player = media
                media.setOnPreparedListener {
                    it.start()
                    currentIndex = index
                    isPlaying = true
                }
                media.setOnCompletionListener { isPlaying = false }
                media.setOnErrorListener { _, _, _ ->
                    isPlaying = false
                    playbackError = "تعذّر تشغيل هذا الملف الصوتي"
                    true
                }
                media.setDataSource(context, songs[index].uri)
                media.prepareAsync()
            }
        }.onFailure {
            isPlaying = false
            playbackError = "تعذّر فتح الملف الصوتي"
        }
    }

    DisposableEffect(player) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(28.dp))
            Text("الأغاني", color = RoyalColors.Pearl, fontSize = 23.sp)
        }
        Text(
            "اختر الأغاني من الجهاز أو فلاشة USB أو بطاقة الذاكرة",
            color = RoyalColors.Pearl.copy(alpha = 0.75f),
            fontSize = 13.sp
        )
        Button(
            onClick = {
                playLauncherUiSound(context, RoyalUiSound.ACTION_ADD)
                picker.launch(arrayOf("audio/*"))
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF76551F), contentColor = RoyalColors.Pearl)
        ) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
            Text("  اختيار الأغاني")
        }

        if (songs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(Color(0xAA10131B)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(46.dp))
                Spacer(Modifier.height(12.dp))
                Text("لا توجد قائمة أغاني", color = RoyalColors.Pearl, fontSize = 19.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                itemsIndexed(songs, key = { _, song -> song.uri.toString() }) { index, song ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (index == currentIndex) Color(0xFF3B2D16) else Color(0xAA10131B))
                            .clickable {
                                playLauncherUiSound(context, RoyalUiSound.MEDIA_PLAY)
                                playSong(index)
                            }.focusable().padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = RoyalColors.Gold)
                        Text(song.title, color = RoyalColors.Pearl, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1)
                        if (index == currentIndex && isPlaying) {
                            Icon(Icons.Rounded.Pause, contentDescription = "يعمل الآن", tint = RoyalColors.Gold)
                        } else {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "تشغيل", tint = RoyalColors.Gold)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF10131B)).padding(10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "السابق",
                    tint = if (currentIndex > 0) RoyalColors.Gold else RoyalColors.Muted,
                    modifier = Modifier.size(34.dp).clickable {
                        if (currentIndex > 0) {
                            playLauncherUiSound(context, RoyalUiSound.MEDIA_PREV)
                            playSong(currentIndex - 1)
                        }
                    })
                Spacer(Modifier.size(22.dp))
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                    tint = RoyalColors.Gold, modifier = Modifier.size(38.dp).clickable {
                        playLauncherUiSound(context, if (isPlaying) RoyalUiSound.MEDIA_PAUSE else RoyalUiSound.MEDIA_PLAY)
                        val active = player
                        if (active != null && currentIndex >= 0) {
                            if (isPlaying) { active.pause(); isPlaying = false }
                            else { runCatching { active.start(); isPlaying = true } }
                        } else if (songs.isNotEmpty()) playSong(0)
                    })
                Spacer(Modifier.size(22.dp))
                Icon(Icons.Rounded.SkipNext, contentDescription = "التالي",
                    tint = if (currentIndex < songs.lastIndex) RoyalColors.Gold else RoyalColors.Muted,
                    modifier = Modifier.size(34.dp).clickable {
                        if (currentIndex < songs.lastIndex) {
                            playLauncherUiSound(context, RoyalUiSound.MEDIA_NEXT)
                            playSong(currentIndex + 1)
                        }
                    })
            }
        }
        playbackError?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
    }
}
