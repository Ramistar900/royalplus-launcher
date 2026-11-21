package com.royalplus.launcher

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private data class RoyalRelaxScene(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val titleTr: String,
    val detailAr: String,
    val asset: String,
    val icon: ImageVector
)

private data class RoyalRelaxSound(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val titleTr: String,
    val resourceId: Int
)

private val royalRelaxScenes = listOf(
    RoyalRelaxScene("fire", "النار والحطب", "Fireplace & logs", "Şömine ve odun", "مدفأة وحطب مشتعل", "fireplace_burning_fire_loop.mp4", Icons.Rounded.Bolt),
    RoyalRelaxScene("rain", "المطر والمياه", "Rain & water", "Yağmur ve su", "أجواء المطر", "weather_videos/weather_rain.mp4", Icons.Rounded.WaterDrop),
    RoyalRelaxScene("snow", "أجواء الشتاء", "Winter ambience", "Kış atmosferi", "تساقط الثلوج", "weather_videos/weather_snow_day.mp4", Icons.Rounded.AcUnit),
    RoyalRelaxScene("sunny", "نهار هادئ", "Sunny day", "Güneşli gün", "مشهد نهاري هادئ", "weather_videos/weather_clear_sunny.mp4", Icons.Rounded.Cloud),
    RoyalRelaxScene("night", "ليلة صافية", "Clear night", "Açık gece", "أجواء الليل", "weather_videos/weather_clear_night.mp4", Icons.Rounded.Cloud)
)

@Composable
internal fun RoyalRelaxScreen(language: String) {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val sceneTitle: (RoyalRelaxScene) -> String = {
        when (language) { "en" -> it.titleEn; "tr" -> it.titleTr; else -> it.titleAr }
    }
    val sounds = remember {
        listOf(
            RoyalRelaxSound("rain", "صوت المطر", "Rain", "Yağmur", R.raw.weather_rain_natural),
            RoyalRelaxSound("thunder", "الرعد الطبيعي", "Thunder", "Gök gürültüsü", R.raw.weather_thunderstorm_thunder),
            RoyalRelaxSound("birds", "تغريد الطيور", "Birds", "Kuşlar", R.raw.nature_birds_morning),
            RoyalRelaxSound("owl", "بومة الليل", "Night owl", "Gece baykuşu", R.raw.nature_owl_clear_night),
            RoyalRelaxSound("wind", "صوت الرياح", "Wind", "Rüzgâr", R.raw.nature_wind_gentle)
        )
    }
    var selectedScene by remember { mutableStateOf(royalRelaxScenes.first()) }
    var selectedSound by remember { mutableStateOf<RoyalRelaxSound?>(sounds.first()) }
    var soundEnabled by remember { mutableStateOf(true) }
    var soundVolume by remember { mutableFloatStateOf(0.65f) }
    var videoPlaying by remember { mutableStateOf(true) }
    var activeAudioPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ROYAL RELAX", color = Color(0xFFD4AF37), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(
                    when (language) { "en" -> "Nature, calm and quiet moments"; "tr" -> "Doğa, huzur ve sakin anlar"; else -> "الطبيعة والهدوء واللحظات المريحة" },
                    color = Color(0xFFE8DDC8), fontSize = 13.sp
                )
            }
            Text(
                when (language) { "en" -> "FREE"; "tr" -> "ÜCRETSİZ"; else -> "مجاني بالكامل" },
                color = Color(0xFF20B8B0), fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
        }

        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF050807))
                .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.7f), RoundedCornerShape(20.dp))
        ) {
            RoyalRelaxVideoView(assetName = selectedScene.asset, playing = videoPlaying)
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Color(0xCC080B0A)).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(sceneTitle(selectedScene), color = Color(0xFFE8DDC8), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(selectedScene.detailAr, color = Color(0xFFBEB5A5), fontSize = 11.sp)
                }
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF173B32))
                        .clickable { videoPlaying = !videoPlaying }.focusable().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (videoPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = Color(0xFFD4AF37), modifier = Modifier.size(22.dp))
                    Text(
                        when (language) { "en" -> if (videoPlaying) "Pause" else "Play"; "tr" -> if (videoPlaying) "Duraklat" else "Oynat"; else -> if (videoPlaying) "إيقاف مؤقت" else "تشغيل" },
                        color = Color(0xFFE8DDC8), fontSize = 12.sp
                    )
                }
            }
        }

        Text(
            when (language) { "en" -> "Choose a nature scene"; "tr" -> "Bir doğa sahnesi seçin"; else -> "اختر مشهداً طبيعياً" },
            color = Color(0xFFD4AF37), fontSize = 15.sp, fontWeight = FontWeight.Bold
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            items(royalRelaxScenes, key = { it.id }) { scene ->
                val selected = scene.id == selectedScene.id
                Column(
                    Modifier.width(142.dp).clip(RoundedCornerShape(13.dp))
                        .background(if (selected) Color(0xFF173B32) else Color(0xFF111A17))
                        .border(1.dp, if (selected) Color(0xFFD4AF37) else Color(0xFF315047), RoundedCornerShape(13.dp))
                        .clickable { selectedScene = scene; videoPlaying = true }
                        .focusable().padding(11.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(scene.icon, contentDescription = null, tint = if (selected) Color(0xFFD4AF37) else Color(0xFF20B8B0), modifier = Modifier.size(23.dp))
                    Text(sceneTitle(scene), color = Color(0xFFE8DDC8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (language) { "en" -> "Nature sounds"; "tr" -> "Doğa sesleri"; else -> "أصوات الطبيعة" },
                color = Color(0xFFD4AF37), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                when (language) { "en" -> "One sound at a time"; "tr" -> "Aynı anda tek ses"; else -> "صوت واحد في كل مرة" },
                color = Color(0xFFBEB5A5), fontSize = 10.sp
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sounds, key = { it.id }) { sound ->
                val selected = soundEnabled && selectedSound?.id == sound.id
                Text(
                    when (language) { "en" -> sound.titleEn; "tr" -> sound.titleTr; else -> sound.titleAr },
                    color = if (selected) Color(0xFF07100D) else Color(0xFFE8DDC8),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (selected) Color(0xFF20B8B0) else Color(0xFF17221E))
                        .clickable { selectedSound = sound; soundEnabled = true }
                        .focusable().padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
            item {
                Text(
                    when (language) { "en" -> "Mute"; "tr" -> "Sessiz"; else -> "كتم الصوت" },
                    color = if (!soundEnabled) Color(0xFF07100D) else Color(0xFFE8DDC8),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (!soundEnabled) Color(0xFFD4AF37) else Color(0xFF17221E))
                        .clickable { soundEnabled = false }
                        .focusable().padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (language) { "en" -> "Sound level"; "tr" -> "Ses seviyesi"; else -> "مستوى الصوت" },
                color = Color(0xFFE8DDC8), fontSize = 11.sp
            )
            androidx.compose.material3.Slider(
                value = soundVolume,
                onValueChange = { soundVolume = it },
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )
            Text((soundVolume * 100).toInt().toString() + "%", color = Color(0xFF20B8B0), fontSize = 11.sp)
        }

        Text(
            when (language) {
                "en" -> "Live nature cameras, Quran recitation and instrumental music will appear only after their permitted sources and audio files are connected. Local scenes and nature sounds above work offline."
                "tr" -> "Canlı doğa kameraları, Kur'an tilaveti ve enstrümantal müzik yalnızca izinli kaynaklar ve ses dosyaları bağlandıktan sonra eklenecektir. Yukarıdaki yerel sahneler ve doğa sesleri çevrimdışı çalışır."
                else -> "كاميرات الطبيعة المباشرة وتلاوات القرآن والموسيقى الآلية ستُفعّل بعد ربط مصادر مسموح بها وملفات صوتية مناسبة. المشاهد والأصوات المحلية أعلاه تعمل دون إنترنت."
            },
            color = Color(0xFFBEB5A5), fontSize = 10.sp
        )
    }

    DisposableEffect(selectedSound?.resourceId, soundEnabled, lifecycleOwner) {
        var player: MediaPlayer? = null
        fun start() {
            if (!soundEnabled || player != null) return
            val resource = selectedSound?.resourceId ?: return
            val created = runCatching { MediaPlayer.create(context, resource) }.getOrNull() ?: return
            player = created
            activeAudioPlayer = created
            created.isLooping = true
            created.setVolume(soundVolume, soundVolume)
            runCatching { created.start() }
        }
        fun stop() {
            val active = player ?: return
            player = null
            if (activeAudioPlayer === active) activeAudioPlayer = null
            runCatching { if (active.isPlaying) active.stop() }
            runCatching { active.release() }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); stop() }
    }
    SideEffect { activeAudioPlayer?.setVolume(soundVolume, soundVolume) }
}

@Composable
private fun RoyalRelaxVideoView(assetName: String, playing: Boolean) {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(context) { RoyalRelaxVideoPlayer(context) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            TextureView(viewContext).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        player.attach(surface)
                    }
                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        player.detach()
                        return true
                    }
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }
            }
        },
        update = {}
    )
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> player.setLifecycleActive(true)
                Lifecycle.Event.ON_PAUSE -> player.setLifecycleActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        player.setLifecycleActive(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    SideEffect {
        player.setAsset(assetName)
        player.setPlaying(playing)
    }
}

private class RoyalRelaxVideoPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null
    private var assetName: String? = null
    private var prepared = false
    private var shouldPlay = true
    private var lifecycleActive = true

    fun attach(texture: SurfaceTexture) {
        surface?.release()
        surface = Surface(texture)
        prepareIfReady()
    }

    fun setAsset(value: String) {
        if (assetName == value && mediaPlayer != null) return
        assetName = value
        releasePlayer()
        prepareIfReady()
    }

    fun setPlaying(value: Boolean) {
        shouldPlay = value
        syncPlayback()
    }

    fun setLifecycleActive(value: Boolean) {
        lifecycleActive = value
        syncPlayback()
    }

    private fun prepareIfReady() {
        val currentSurface = surface ?: return
        val currentAsset = assetName ?: return
        if (mediaPlayer != null) return
        val created = MediaPlayer()
        mediaPlayer = created
        try {
            context.assets.openFd(currentAsset).use { descriptor ->
                created.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            }
            created.setSurface(currentSurface)
            created.isLooping = true
            created.setVolume(0f, 0f)
            created.setOnPreparedListener { preparedPlayer ->
                if (mediaPlayer !== preparedPlayer) return@setOnPreparedListener
                prepared = true
                syncPlayback()
            }
            created.setOnErrorListener { failed, _, _ ->
                if (mediaPlayer === failed) releasePlayer()
                true
            }
            created.prepareAsync()
        } catch (_: Exception) {
            releasePlayer()
        }
    }

    private fun syncPlayback() {
        val active = mediaPlayer ?: return
        if (!prepared) return
        if (shouldPlay && lifecycleActive) {
            if (!runCatching { active.isPlaying }.getOrDefault(false)) runCatching { active.start() }
        } else {
            if (runCatching { active.isPlaying }.getOrDefault(false)) runCatching { active.pause() }
        }
    }

    fun detach() {
        releasePlayer()
        surface?.release()
        surface = null
    }

    fun release() {
        releasePlayer()
        surface?.release()
        surface = null
    }

    private fun releasePlayer() {
        prepared = false
        val active = mediaPlayer
        mediaPlayer = null
        if (active != null) {
            runCatching { active.setOnPreparedListener(null) }
            runCatching { active.setOnErrorListener(null) }
            runCatching { if (active.isPlaying) active.stop() }
            runCatching { active.release() }
        }
    }
}
