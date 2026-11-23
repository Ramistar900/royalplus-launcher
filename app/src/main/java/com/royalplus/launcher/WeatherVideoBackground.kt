package com.royalplus.launcher

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private const val ROYAL_WEATHER_VIDEO_TAG = "RoyalWeatherVideo"

/** Resolve day/night visuals using the selected weather location's timezone, not the device clock. */
internal fun weatherLocalHour(currentTimeMillis: Long, timeZoneId: String?): Int {
    val zone = timeZoneId?.takeIf { it.isNotBlank() }?.let(TimeZone::getTimeZone)
        ?: TimeZone.getDefault()
    return Calendar.getInstance(zone).apply { timeInMillis = currentTimeMillis }
        .get(Calendar.HOUR_OF_DAY)
}

internal fun weatherVideoAsset(weather: WeatherUiState, hour: Int?): String? {
    val night = hour != null && (hour >= 19 || hour < 6)
    val code = weather.weatherCode ?: -1
    val mappedCode = when {
        code == 0 || code == 1 -> when {
            weather.windSpeedKmh?.let { it >= 45 } == true -> "weather_wind.mp4"
            night -> "weather_clear_night.mp4"
            else -> "weather_clear_sunny.mp4"
        }
        code == 2 -> if (night) "weather_partly_cloudy_night.mp4" else "weather_partly_cloudy_day.mp4"
        code == 3 -> "weather_cloudy.mp4"
        code == 45 || code == 48 -> "weather_fog.mp4"
        code in 51..57 -> "weather_light_rain.mp4"
        code in 61..65 || code in 80..82 -> "weather_rain.mp4"
        code in 66..67 -> "weather_rain.mp4"
        code in 71..77 -> if (night) "weather_snow_night.mp4" else "weather_snow_day.mp4"
        code == 85 || code == 86 -> "weather_light_snow.mp4"
        // Hail-bearing thunderstorms use the rain scene; the atmosphere overlay draws the hail.
        code == 96 || code == 99 -> "weather_rain.mp4"
        code in 95..98 -> "weather_thunderstorm.mp4"
        else -> null
    }
    if (mappedCode != null) return mappedCode

    // Some cached/manual weather states contain a localized description but no numeric code.
    // Match those descriptions too, so a visible "غائم" state does not silently fall back to a still image.
    val description = weather.description.lowercase(Locale.ROOT)
    return when {
        description.contains("hail") || description.contains("تساقط البرد") ||
            description.contains("البَرَد") -> "weather_rain.mp4"
        description.contains("freezing rain") || description.contains("مطر متجمد") ->
            "weather_rain.mp4"
        description.contains("freezing drizzle") || description.contains("رذاذ متجمد") ->
            "weather_light_rain.mp4"
        description.contains("thunder") || description.contains("storm") ||
            description.contains("عاصفة") -> "weather_thunderstorm.mp4"
        description.contains("fog") || description.contains("mist") ||
            description.contains("ضباب") -> "weather_fog.mp4"
        description.contains("snow") || description.contains("ثلج") ->
            if (night) "weather_snow_night.mp4" else "weather_snow_day.mp4"
        description.contains("drizzle") || description.contains("رذاذ") -> "weather_light_rain.mp4"
        description.contains("rain") || description.contains("مطر") ||
            description.contains("أمطار") || description.contains("امطار") -> "weather_rain.mp4"
        description.contains("partly cloudy") || description.contains("غائم جزئ") ->
            if (night) "weather_partly_cloudy_night.mp4" else "weather_partly_cloudy_day.mp4"
        description.contains("overcast") || description.contains("غائم كلي") ||
            description.contains("غائم بالكامل") || description.contains("غائم") ||
            description.contains("غيوم") || description.contains("cloud") -> "weather_cloudy.mp4"
        description.contains("wind") || description.contains("رياح") ->
            "weather_wind.mp4"
        description.contains("clear") || description.contains("sunny") ||
            description.contains("صافي") || description.contains("مشمس") ->
            if (night) "weather_clear_night.mp4" else "weather_clear_sunny.mp4"
        else -> null
    }
}

@Composable
internal fun WeatherVideoBackground(
    assetName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val videoPathState = remember(assetName) { mutableStateOf<String?>(null) }
    LaunchedEffect(assetName) {
        videoPathState.value = try {
            withContext(Dispatchers.IO) {
                val target = File(context.cacheDir, "royal-weather-$assetName")
                val assetPath = "weather_videos/$assetName"
                context.cacheDir.listFiles()?.filter {
                    it.name.startsWith("royal-weather-") &&
                        it != target && !it.name.endsWith(".tmp")
                }?.forEach { oldFile ->
                    if (!oldFile.delete()) {
                        Log.w(ROYAL_WEATHER_VIDEO_TAG, "Could not remove old cached clip: " + oldFile.name)
                    }
                }
                val expectedLength = context.assets.open(assetPath).use { it.available().toLong() }
                if (!target.isFile || target.length() != expectedLength) {
                    val temporary = File(context.cacheDir, "royal-weather-$assetName.tmp")
                    context.assets.open(assetPath).use { input ->
                        temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                    }
                    check(temporary.isFile && temporary.length() == expectedLength && expectedLength > 0L) {
                        "Weather video copy was incomplete: $assetName"
                    }
                    if (target.exists()) check(target.delete()) {
                        "Unable to replace cached weather video: $assetName"
                    }
                    check(temporary.renameTo(target)) {
                        "Unable to cache weather video: $assetName"
                    }
                }
                check(target.isFile && target.length() > 0L) {
                    "Cached weather video is missing or empty: $assetName"
                }
                Log.d(ROYAL_WEATHER_VIDEO_TAG, "Weather clip ready: " + assetName + " (" + target.length() + " bytes)")
                target.absolutePath
            }
        } catch (error: Exception) {
            Log.e(ROYAL_WEATHER_VIDEO_TAG, "Unable to load weather clip: $assetName", error)
            null
        }
    }

    val videoPath = videoPathState.value
    if (videoPath != null) {
        val textureView = remember(context) { RoyalWeatherTextureView(context) }
        AndroidView(
            factory = { textureView },
            update = { it.setVideoPath(videoPath) },
            modifier = modifier.fillMaxSize()
        )
        DisposableEffect(textureView) {
            onDispose { textureView.releasePlayback() }
        }
    }
}

/**
 * TextureView keeps the clip inside Compose's normal view stacking order, so the
 * weather labels and glass overlays remain visible above the moving background.
 */
private class RoyalWeatherTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var requestedPath: String? = null
    private var mediaPlayer: MediaPlayer? = null
    private var videoSurface: Surface? = null

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    fun setVideoPath(path: String) {
        if (requestedPath == path) {
            if (isAvailable && mediaPlayer == null) {
                surfaceTexture?.let(::preparePlayer)
            }
            return
        }
        requestedPath = path
        releasePlayer()
        if (isAvailable) {
            surfaceTexture?.let(::preparePlayer)
        }
    }

    private fun preparePlayer(surfaceTexture: SurfaceTexture) {
        val path = requestedPath ?: return
        if (mediaPlayer != null) return

        val player = MediaPlayer()
        val surface = Surface(surfaceTexture)
        mediaPlayer = player
        videoSurface = surface

        try {
            player.setDataSource(path)
            player.setSurface(surface)
            player.isLooping = true
            player.setVolume(0f, 0f)
            player.setOnPreparedListener { preparedPlayer ->
                if (mediaPlayer !== preparedPlayer) return@setOnPreparedListener
                try {
                    preparedPlayer.isLooping = true
                    preparedPlayer.setVolume(0f, 0f)
                    // Keep the ambient clips calm and limit display motion on Android TV.
                    // PlaybackParams is available from API 23; unsupported vendor players keep normal speed.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        runCatching {
                            preparedPlayer.playbackParams = PlaybackParams().setSpeed(0.9f)
                        }
                    }
                    preparedPlayer.start()
                    Log.d(ROYAL_WEATHER_VIDEO_TAG, "Weather clip started: $path")
                } catch (error: IllegalStateException) {
                    Log.e(ROYAL_WEATHER_VIDEO_TAG, "Could not start weather clip: $path", error)
                    releasePlayer()
                }
            }
            player.setOnErrorListener { failedPlayer, what, extra ->
                if (mediaPlayer === failedPlayer) {
                    Log.e(
                        ROYAL_WEATHER_VIDEO_TAG,
                        "Weather clip playback failed: $path (what=$what, extra=$extra)"
                    )
                    releasePlayer()
                }
                true
            }
            player.prepareAsync()
        } catch (error: Exception) {
            Log.e(ROYAL_WEATHER_VIDEO_TAG, "Could not prepare weather clip: $path", error)
            releasePlayer()
        }
    }

    private fun releasePlayer() {
        val player = mediaPlayer
        mediaPlayer = null
        runCatching { player?.setOnPreparedListener(null) }
        runCatching { player?.setOnErrorListener(null) }
        runCatching { player?.stop() }
        runCatching { player?.reset() }
        runCatching { player?.release() }
        runCatching { videoSurface?.release() }
        videoSurface = null
    }

    fun releasePlayback() {
        requestedPath = null
        releasePlayer()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        preparePlayer(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        releasePlayer()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onDetachedFromWindow() {
        releasePlayer()
        super.onDetachedFromWindow()
    }
}
