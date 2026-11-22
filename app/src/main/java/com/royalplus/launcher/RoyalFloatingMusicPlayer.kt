package com.royalplus.launcher

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.util.concurrent.Executor

@Composable
internal fun RoyalFloatingMusicPlayer(language: String) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var hasTrack by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var trackTitle by remember { mutableStateOf("ROYAL AUDIO") }
    var minimized by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, RoyalMusicPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val executor = Executor { command -> Handler(Looper.getMainLooper()).post(command) }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                hasTrack = mediaItem != null
                if (mediaItem != null) {
                    trackTitle = mediaItem.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "ROYAL AUDIO"
                    dismissed = false
                }
            }
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                trackTitle = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "ROYAL AUDIO"
            }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                hasTrack = controller?.currentMediaItem != null
            }
        }
        future.addListener({
            runCatching {
                val connected = future.get()
                controller = connected
                connected.addListener(listener)
                hasTrack = connected.currentMediaItem != null
                isPlaying = connected.isPlaying
                trackTitle = connected.currentMediaItem?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() } ?: "ROYAL AUDIO"
            }
        }, executor)
        onDispose {
            controller?.removeListener(listener)
            MediaController.releaseFuture(future)
            controller = null
        }
    }

    if (hasTrack && !dismissed) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomEnd) {
            if (minimized) {
                Surface(
                    modifier = Modifier.size(58.dp).clickable { minimized = false },
                    shape = CircleShape,
                    color = Color(0xF20D1118),
                    border = BorderStroke(1.dp, RoyalColors.Gold.copy(alpha = 0.9f)),
                    shadowElevation = 12.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = if (language == "en") "Open music player" else if (language == "tr") "Müzik oynatıcıyı aç" else "فتح مشغل الموسيقى", tint = RoyalColors.Gold, modifier = Modifier.size(29.dp))
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.width(292.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xF20D1118),
                    border = BorderStroke(1.dp, RoyalColors.Gold.copy(alpha = 0.78f)),
                    shadowElevation = 14.dp
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(22.dp))
                            Text(trackTitle, color = RoyalColors.Pearl, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { minimized = true }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Rounded.Remove, contentDescription = if (language == "en") "Minimize" else if (language == "tr") "Küçült" else "تصغير", tint = RoyalColors.Gold)
                            }
                            IconButton(onClick = {
                                controller?.let { active ->
                                    runCatching {
                                        active.stop()
                                        active.clearMediaItems()
                                    }
                                }
                                isPlaying = false
                                hasTrack = false
                                dismissed = true
                                minimized = false
                            }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = if (language == "en") "Stop and close" else if (language == "tr") "Durdur ve kapat" else "إيقاف وإغلاق", tint = Color(0xFFFF8A80))
                            }
                        }
                        Row(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(onClick = { controller?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() } }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Rounded.SkipPrevious, contentDescription = if (language == "en") "Previous" else if (language == "tr") "Önceki" else "السابق", tint = RoyalColors.Gold)
                            }
                            IconButton(onClick = {
                                controller?.let { if (it.isPlaying) it.pause() else it.play() }
                            }, modifier = Modifier.size(44.dp)) {
                                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play", tint = RoyalColors.Pearl, modifier = Modifier.size(31.dp))
                            }
                            IconButton(onClick = { controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() } }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Rounded.SkipNext, contentDescription = if (language == "en") "Next" else if (language == "tr") "Sonraki" else "التالي", tint = RoyalColors.Gold)
                            }
                        }
                    }
                }
            }
        }
    }
}
