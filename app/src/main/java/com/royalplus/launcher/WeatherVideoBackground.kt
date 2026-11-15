package com.royalplus.launcher

import android.graphics.Color as AndroidColor
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal fun weatherVideoAsset(weather: WeatherUiState, hour: Int?): String? {
    val night = hour != null && (hour >= 19 || hour < 6)
    val code = weather.weatherCode ?: return null
    return when {
        code == 0 || code == 1 -> when {
            weather.windSpeedKmh?.let { it >= 45 } == true -> "weather_wind.mp4"
            night -> "weather_clear_night.mp4"
            else -> "weather_clear_sunny.mp4"
        }
        code == 2 -> if (night) "weather_partly_cloudy_night.mp4" else "weather_partly_cloudy_day.mp4"
        code == 3 -> "weather_cloudy.mp4"
        code == 45 || code == 48 -> "weather_fog.mp4"
        code in 51..57 -> "weather_light_rain.mp4"
        code in 61..67 || code in 80..82 -> "weather_rain.mp4"
        code in 71..77 -> if (night) "weather_snow_night.mp4" else "weather_snow_day.mp4"
        code == 85 || code == 86 -> "weather_light_snow.mp4"
        code in 95..99 -> "weather_thunderstorm.mp4"
        else -> null
    }
}

@Composable
internal fun WeatherVideoBackground(
    assetName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val videoPath by produceState<String?>(initialValue = null, assetName) {
        value = try {
            withContext(Dispatchers.IO) {
            val target = File(context.cacheDir, "royal-weather-$assetName")
            val assetPath = "weather_videos/$assetName"
            context.cacheDir.listFiles()?.filter {
                it.name.startsWith("royal-weather-") && it != target && !it.name.endsWith(".tmp")
            }?.forEach { it.delete() }
            val expectedLength = context.assets.open(assetPath).use { it.available().toLong() }
            if (!target.isFile || target.length() != expectedLength) {
                val temporary = File(context.cacheDir, "royal-weather-$assetName.tmp")
                context.assets.open(assetPath).use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
                if (target.exists()) target.delete()
                check(temporary.renameTo(target)) { "Unable to cache weather video: $assetName" }
            }
            target.absolutePath
            }
        } catch (_: Exception) {
            null
        }
    }
    if (videoPath != null) {
        val videoView = remember(videoPath) {
            VideoView(context).apply {
                setBackgroundColor(AndroidColor.BLACK)
                setVideoPath(videoPath!!)
                setOnPreparedListener { player ->
                    player.isLooping = true
                    player.setVolume(0f, 0f)
                    start()
                }
                setOnErrorListener { _, _, _ ->
                    visibility = android.view.View.GONE
                    true
                }
            }
        }
        AndroidView(factory = { videoView }, modifier = modifier.fillMaxSize())
        DisposableEffect(videoView) {
            onDispose { videoView.stopPlayback() }
        }
    }
}
