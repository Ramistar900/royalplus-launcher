package com.royalplus.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executor
import com.royalplus.launcher.royalsounds.RoyalUiSound

private data class RoyalSong(val uri: Uri, val title: String)

@Composable
internal fun RoyalSongsScreen() {
    val adaptiveProfile = com.royal.adaptive.LocalRoyalAdaptiveProfile.current
    val context = LocalContext.current
    val songs = remember { mutableStateListOf<RoyalSong>() }
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
    fun refreshDeviceSongs() {
        songs.clear()
        songs.addAll(loadDeviceRoyalSongs(context))
        saveRoyalSongs(context, songs)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) refreshDeviceSongs()
    }
    LaunchedEffect(context) {
        if (context.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED) {
            refreshDeviceSongs()
        } else {
            songs.addAll(loadSavedRoyalSongs(context))
        }
    }
    var currentIndex by remember { mutableStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, RoyalMusicPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = controller?.currentMediaItemIndex ?: -1
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                isPlaying = false
                playbackError = "تعذّر تشغيل هذا الملف الصوتي"
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                controller?.let { currentIndex = it.currentMediaItemIndex }
            }
        }
        val mainExecutor = Executor { command -> Handler(Looper.getMainLooper()).post(command) }
        future.addListener({
            runCatching {
                val connected = future.get()
                controller = connected
                connected.addListener(playerListener)
                currentIndex = connected.currentMediaItemIndex
                isPlaying = connected.isPlaying
            }.onFailure { playbackError = "تعذّر الاتصال بمشغّل ROYAL AUDIO" }
        }, mainExecutor)
        onDispose {
            controller?.removeListener(playerListener)
            MediaController.releaseFuture(future)
            controller = null
        }
    }

    fun playSong(index: Int) {
        val active = controller ?: run {
            playbackError = "المشغّل قيد الاتصال، حاول مجدداً"
            return
        }
        if (index !in songs.indices) return
        runCatching {
            val queue = songs.map { song ->
                MediaItem.Builder().setUri(song.uri)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).build()).build()
            }
            active.setMediaItems(queue, index, 0L)
            active.prepare()
            active.play()
            currentIndex = index
            playbackError = null
        }.onFailure { playbackError = "تعذّر فتح الملف الصوتي" }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = adaptiveProfile.pageHorizontalPadding, vertical = adaptiveProfile.pageVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(adaptiveProfile.sectionSpacing)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(royalAdaptiveIconSize(28.dp)))
            Text("الأغاني", color = RoyalColors.Pearl, fontSize = 23.sp)
        }
        Text("يبحث ROYAL AUDIO تلقائياً عن ملفات الصوت على الجهاز وذاكرة USB بعد منح الإذن", color = RoyalColors.Pearl.copy(alpha = 0.75f), fontSize = 13.sp)
        Button(onClick = {
            playLauncherUiSound(context, RoyalUiSound.ACTION_ADD)
            if (context.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED) {
                refreshDeviceSongs()
            } else {
                permissionLauncher.launch(audioPermission)
            }
        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF76551F), contentColor = RoyalColors.Pearl)) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(royalAdaptiveIconSize(24.dp)))
            Text(if (context.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED) "  تحديث مكتبة الجهاز" else "  السماح بالوصول إلى الموسيقى")
        }
        if (songs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp)).background(Color(0xAA10131B)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(royalAdaptiveIconSize(46.dp)))
                Spacer(Modifier.height(12.dp))
                Text(if (context.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED) "لا توجد ملفات موسيقى على الجهاز" else "اسمح بالوصول إلى ملفات الموسيقى لعرض مكتبتك تلقائياً", color = RoyalColors.Pearl, fontSize = 19.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                itemsIndexed(songs, key = { _, song -> song.uri.toString() }) { index, song ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (index == currentIndex) Color(0xFF3B2D16) else Color(0xAA10131B))
                            .clickable { playLauncherUiSound(context, RoyalUiSound.MEDIA_PLAY); playSong(index) }
                            .focusable().padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(royalAdaptiveIconSize(24.dp)))
                        Text(song.title, color = RoyalColors.Pearl, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 2)
                        Icon(if (index == currentIndex && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (index == currentIndex && isPlaying) "يعمل الآن" else "تشغيل",
                            tint = RoyalColors.Gold, modifier = Modifier.size(royalAdaptiveIconSize(24.dp)))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(RoyalColors.Panel).padding(10.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "السابق",
                    tint = if (currentIndex > 0) RoyalColors.Gold else RoyalColors.Muted,
                    modifier = Modifier.size(royalAdaptiveIconSize(34.dp)).clickable {
                        controller?.let { if (it.hasPreviousMediaItem()) { playLauncherUiSound(context, RoyalUiSound.MEDIA_PREV); it.seekToPreviousMediaItem() } }
                    })
                Spacer(Modifier.size(22.dp))
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل", tint = RoyalColors.Gold,
                    modifier = Modifier.size(royalAdaptiveIconSize(38.dp)).clickable {
                        val active = controller
                        playLauncherUiSound(context, if (isPlaying) RoyalUiSound.MEDIA_PAUSE else RoyalUiSound.MEDIA_PLAY)
                        if (active != null && currentIndex >= 0) {
                            if (isPlaying) active.pause() else active.play()
                        } else if (songs.isNotEmpty()) playSong(0)
                    })
                Spacer(Modifier.size(22.dp))
                Icon(Icons.Rounded.SkipNext, contentDescription = "التالي",
                    tint = if (currentIndex < songs.lastIndex) RoyalColors.Gold else RoyalColors.Muted,
                    modifier = Modifier.size(royalAdaptiveIconSize(34.dp)).clickable {
                        controller?.let { if (it.hasNextMediaItem()) { playLauncherUiSound(context, RoyalUiSound.MEDIA_NEXT); it.seekToNextMediaItem() } }
                    })
            }
        }
        playbackError?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
    }
}

private fun loadSavedRoyalSongs(context: Context): List<RoyalSong> = runCatching {
    val raw = context.getSharedPreferences("royal_audio_engine", Context.MODE_PRIVATE).getString("local_playlist", null)
        ?: return emptyList()
    val array = JSONArray(raw)
    buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val uri = Uri.parse(item.getString("uri"))
            val title = item.optString("title", "أغنية")
            if (uri.scheme == "content" || uri.scheme == "file") add(RoyalSong(uri, title))
        }
    }
}.getOrDefault(emptyList())

private fun loadDeviceRoyalSongs(context: Context): List<RoyalSong> = runCatching {
    val permission = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
    if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.IS_MUSIC
    )
    val result = mutableListOf<RoyalSong>()
    val audioCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }
    context.contentResolver.query(
        audioCollection,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null,
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val title = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() } ?: "أغنية"
            val uri = android.content.ContentUris.withAppendedId(audioCollection, id)
            result += RoyalSong(uri, title)
        }
    }
    result
}.getOrDefault(emptyList())

private fun saveRoyalSongs(context: Context, songs: List<RoyalSong>) {
    val array = JSONArray()
    songs.forEach { array.put(JSONObject().put("uri", it.uri.toString()).put("title", it.title)) }
    context.getSharedPreferences("royal_audio_engine", Context.MODE_PRIVATE).edit()
        .putString("local_playlist", array.toString()).apply()
}
