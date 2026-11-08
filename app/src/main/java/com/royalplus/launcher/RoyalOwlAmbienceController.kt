package com.royalplus.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Calendar
import java.util.TimeZone
import kotlin.random.Random

@Composable
internal fun RoyalOwlAmbienceEffect(weather: WeatherUiState) {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestWeather = rememberUpdatedState(weather)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME ->
                    RoyalOwlAmbienceController.onForeground(context, latestWeather.value)
                Lifecycle.Event.ON_PAUSE ->
                    RoyalOwlAmbienceController.onBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            RoyalOwlAmbienceController.onForeground(context, latestWeather.value)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            RoyalOwlAmbienceController.onBackground()
        }
    }

    SideEffect {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            RoyalOwlAmbienceController.onWeatherChanged(context, weather)
        }
    }
}

internal object RoyalOwlAmbienceController {
    const val PREFERENCES_NAME = "royalplus_launcher_preferences"
    const val ENABLED_KEY = "royal_owl_night_sound_enabled"

    private const val MIN_INTERVAL_MS = 30L * 60L * 1000L
    private const val MAX_INTERVAL_MS = 60L * 60L * 1000L
    private const val MAX_WEATHER_AGE_MS = 90L * 60L * 1000L
    private const val OWL_VOLUME = 0.24f
    private const val THUNDER_VOLUME = 0.30f
    private const val RAIN_VOLUME = 0.18f
    private const val BIRDS_VOLUME = 0.16f
    private const val WIND_VOLUME = 0.15f
    private const val SNOW_LIGHT_WIND_VOLUME = 0.10f
    private const val SNOW_MODERATE_WIND_VOLUME = 0.15f
    private const val LEAVES_VOLUME = 0.11f

    private const val THUNDER_AUDIO_URL =
        "https://ramistar900.github.io/royal-audio-player/assets/ogg/weather.thunderstorm.thunder.ogg"
    private const val RAIN_AUDIO_URL =
        "https://ramistar900.github.io/royal-audio-player/assets/ogg/weather.rain.natural.ogg"
    private const val BIRDS_GENERAL_AUDIO_URL =
        "https://ramistar900.github.io/royal-audio-player/assets/ogg/nature.birds.general.ogg"
    private const val BIRDS_MORNING_AUDIO_URL =
        "https://ramistar900.github.io/royal-audio-player/assets/ogg/nature.birds.morning.ogg"
    private const val WIND_AUDIO_URL =
        "https://ramistar900.github.io/royal-audio-player/assets/ogg/nature.wind.gentle.ogg"
    private const val LEAVES_AUDIO_URL =
        "https://ramistar900.github.io/royal-audio-player/assets/ogg/nature.leaves.rustle.ogg"

    private const val BREEZE_MIN_KMH = 18
    private const val STRONG_WIND_KMH = 35
    private const val LEAVES_MIN_KMH = 25

    private data class WeatherAmbienceTrack(
        val id: String,
        val url: String,
        val volume: Float
    )

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var latestWeather: WeatherUiState? = null
    private var latestWeatherIdentity: WeatherUiState? = null
    private var weatherReceivedAtElapsedMs: Long? = null
    private var foreground = false
    private var scheduledTask: Runnable? = null
    private var mediaPlayer: MediaPlayer? = null
    private var thunderPlayer: MediaPlayer? = null
    private val weatherAmbiencePlayers = linkedMapOf<String, MediaPlayer>()
    private var activeWeatherAmbienceKey: String? = null

    fun setEnabled(context: Context, enabled: Boolean) {
        val applicationContext = context.applicationContext
        appContext = applicationContext
        applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENABLED_KEY, enabled)
            .apply()

        if (enabled) {
            scheduleNext()
        } else {
            cancelScheduledTask()
            stopPlayer()
        }
    }

    fun onForeground(context: Context, weather: WeatherUiState) {
        appContext = context.applicationContext
        foreground = true
        updateWeatherIdentity(weather)
        if (isThunderstorm(weather)) playThunderstormOnce()
        syncWeatherAmbience()
        scheduleNext()
    }

    fun onWeatherChanged(context: Context, weather: WeatherUiState) {
        appContext = context.applicationContext
        val changed = updateWeatherIdentity(weather)
        if (changed) {
            if (isThunderstorm(weather)) playThunderstormOnce() else stopThunderPlayer()
            syncWeatherAmbience()
            if (foreground) scheduleNext()
        }
    }

    fun onBackground() {
        foreground = false
        cancelScheduledTask()
        stopPlayer()
        stopThunderPlayer()
        stopWeatherAmbience()
    }

    private fun updateWeatherIdentity(weather: WeatherUiState): Boolean {
        if (weather === latestWeatherIdentity) return false

        latestWeatherIdentity = weather
        latestWeather = weather
        weatherReceivedAtElapsedMs =
            if (!weather.isLoading && weather.weatherCode != null) {
                SystemClock.elapsedRealtime()
            } else {
                null
            }
        stopPlayer()
        return true
    }

    /**
     * Selects only ambience that can be justified by the actual weather response:
     * rain for precipitation, birds for calm clear daytime weather, and wind/leaves
     * for measured moderate winds. Storms use the separate one-shot thunder sound.
     */
    private fun resolveWeatherAmbience(weather: WeatherUiState?): List<WeatherAmbienceTrack> {
        if (weather == null || weather.isLoading) return emptyList()
        val code = weather.weatherCode ?: return emptyList()

        // The requested storm sound replaces the storm-rain recording entirely.
        if (code in 95..99) return emptyList()

        // Drizzle, freezing drizzle, rain, freezing rain and rain showers.
        if (code in 51..67 || code in 80..82) {
            return listOf(
                WeatherAmbienceTrack("rain", RAIN_AUDIO_URL, RAIN_VOLUME)
            )
        }

        // Gentle wind can accompany light/moderate snowfall only.
        // Heavy snow and snow grains are intentionally left without this gentle-wind track.
        if (code == 71 || code == 85) {
            return listOf(
                WeatherAmbienceTrack("snow-light-wind", WIND_AUDIO_URL, SNOW_LIGHT_WIND_VOLUME)
            )
        }
        if (code == 73) {
            return listOf(
                WeatherAmbienceTrack("snow-moderate-wind", WIND_AUDIO_URL, SNOW_MODERATE_WIND_VOLUME)
            )
        }
        if (code == 75 || code == 77 || code == 86) return emptyList()

        val windSpeed = weather.windSpeedKmh
        if (windSpeed != null && windSpeed >= STRONG_WIND_KMH) {
            // The library has a gentle breeze recording, not a strong-wind effect.
            return emptyList()
        }
        if (windSpeed != null && windSpeed >= BREEZE_MIN_KMH) {
            return buildList {
                add(WeatherAmbienceTrack("wind", WIND_AUDIO_URL, WIND_VOLUME))
                if (windSpeed >= LEAVES_MIN_KMH) {
                    add(WeatherAmbienceTrack("leaves", LEAVES_AUDIO_URL, LEAVES_VOLUME))
                }
            }
        }

        // Use bird ambience only in calm, clear/mostly-clear/partly-cloudy daylight.
        if (code in 0..2) {
            val zoneId = weather.timeZoneId ?: TimeZone.getDefault().id
            val hour = Calendar.getInstance(TimeZone.getTimeZone(zoneId))
                .get(Calendar.HOUR_OF_DAY)
            if (hour in 5..10) {
                return listOf(
                    WeatherAmbienceTrack("birds-morning", BIRDS_MORNING_AUDIO_URL, BIRDS_VOLUME)
                )
            }
            if (hour in 11..18) {
                return listOf(
                    WeatherAmbienceTrack("birds-day", BIRDS_GENERAL_AUDIO_URL, BIRDS_VOLUME)
                )
            }
        }

        // No unrelated coastal/stream sound is guessed from weather alone.
        return emptyList()
    }

    private fun syncWeatherAmbience() {
        if (!foreground) {
            stopWeatherAmbience()
            return
        }

        val tracks = resolveWeatherAmbience(latestWeather)
        val nextKey = tracks.joinToString("|") { it.id }
        if (nextKey == activeWeatherAmbienceKey &&
            tracks.all { weatherAmbiencePlayers.containsKey(it.id) }
        ) {
            return
        }

        stopWeatherAmbience()
        activeWeatherAmbienceKey = nextKey
        tracks.forEach(::playWeatherAmbienceTrack)
    }

    private fun playWeatherAmbienceTrack(track: WeatherAmbienceTrack) {
        val context = appContext ?: return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null && audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0) {
            return
        }

        val player = MediaPlayer()
        weatherAmbiencePlayers[track.id] = player
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.setDataSource(track.url)
            player.isLooping = true
            player.setVolume(track.volume, track.volume)
            player.setOnPreparedListener { prepared ->
                if (weatherAmbiencePlayers[track.id] !== prepared || !foreground) {
                    releaseWeatherPlayerInstance(track.id, prepared)
                } else {
                    runCatching { prepared.start() }
                        .onFailure { releaseWeatherPlayerInstance(track.id, prepared) }
                }
            }
            player.setOnErrorListener { failed, _, _ ->
                releaseWeatherPlayerInstance(track.id, failed)
                true
            }
            player.prepareAsync()
        } catch (_: Exception) {
            releaseWeatherPlayerInstance(track.id, player)
        }
    }

    private fun stopWeatherAmbience() {
        val activePlayers = weatherAmbiencePlayers.toList()
        weatherAmbiencePlayers.clear()
        activeWeatherAmbienceKey = null
        activePlayers.forEach { (id, player) ->
            releaseWeatherPlayerInstance(id, player)
        }
    }

    private fun releaseWeatherPlayerInstance(trackId: String, player: MediaPlayer) {
        if (weatherAmbiencePlayers[trackId] === player) {
            weatherAmbiencePlayers.remove(trackId)
        }
        runCatching {
            player.setOnPreparedListener(null)
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            if (player.isPlaying) player.stop()
            player.release()
        }
    }

    private fun scheduleNext() {
        cancelScheduledTask()
        if (!foreground || !isCurrentlyEligibleForScheduling()) {
            stopPlayer()
            return
        }

        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val delayMs = if (isOwlNightWindow(hour)) {
            Random.nextLong(MIN_INTERVAL_MS, MAX_INTERVAL_MS + 1L)
        } else {
            delayUntilNextNightWindow(calendar)
        }

        val task = Runnable {
            scheduledTask = null
            if (!isCurrentlyEligible()) {
                scheduleNext()
                return@Runnable
            }
            playOneShot()
            scheduleNext()
        }
        scheduledTask = task
        handler.postDelayed(task, delayMs)
    }

    private fun isCurrentlyEligibleForScheduling(): Boolean {
        val weather = latestWeather ?: return false
        val receivedAt = weatherReceivedAtElapsedMs ?: return false
        val age = SystemClock.elapsedRealtime() - receivedAt
        val code = weather.weatherCode ?: return false
        if (age < 0L || age > MAX_WEATHER_AGE_MS) return false
        if (weather.isLoading || code !in 0..1) return false
        if (!isEnabled()) return false
        return true
    }

    private fun isCurrentlyEligible(): Boolean {
        if (!isCurrentlyEligibleForScheduling()) return false
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return isOwlNightWindow(hour)
    }

    private fun isEnabled(): Boolean =
        appContext?.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            ?.getBoolean(ENABLED_KEY, true) == true

    private fun isOwlNightWindow(hour: Int): Boolean = hour >= 19 || hour < 6

    private fun delayUntilNextNightWindow(now: Calendar): Long {
        val nextStart = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        return (nextStart.timeInMillis - now.timeInMillis).coerceAtLeast(1_000L)
    }

    private fun playOneShot() {
        val context = appContext ?: return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null && audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0) {
            return
        }

        val player = MediaPlayer.create(context, R.raw.nature_owl_clear_night) ?: return
        mediaPlayer = player
        player.setVolume(OWL_VOLUME, OWL_VOLUME)
        player.setOnCompletionListener { completed -> releasePlayerInstance(completed) }
        player.setOnErrorListener { failed, _, _ ->
            releasePlayerInstance(failed)
            true
        }
        try {
            player.start()
        } catch (_: Exception) {
            releasePlayerInstance(player)
        }
    }

    private fun isThunderstorm(weather: WeatherUiState): Boolean =
        !weather.isLoading && weather.weatherCode?.let { it in 95..99 } == true

    private fun playThunderstormOnce() {
        val context = appContext ?: return
        if (!foreground || latestWeather?.let(::isThunderstorm) != true || thunderPlayer != null) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null && audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0) return

        val player = MediaPlayer()
        thunderPlayer = player
        try {
            player.setAudioStreamType(AudioManager.STREAM_MUSIC)
            player.setDataSource(THUNDER_AUDIO_URL)
            player.setVolume(THUNDER_VOLUME, THUNDER_VOLUME)
            player.setOnPreparedListener { prepared ->
                if (thunderPlayer !== prepared || !foreground ||
                    latestWeather?.let(::isThunderstorm) != true
                ) {
                    releaseThunderPlayerInstance(prepared)
                } else {
                    runCatching { prepared.start() }
                        .onFailure { releaseThunderPlayerInstance(prepared) }
                }
            }
            player.setOnCompletionListener { completed ->
                releaseThunderPlayerInstance(completed)
            }
            player.setOnErrorListener { failed, _, _ ->
                releaseThunderPlayerInstance(failed)
                true
            }
            player.prepareAsync()
        } catch (_: Exception) {
            releaseThunderPlayerInstance(player)
        }
    }

    private fun stopThunderPlayer() {
        val active = thunderPlayer ?: return
        thunderPlayer = null
        runCatching {
            active.setOnPreparedListener(null)
            active.setOnCompletionListener(null)
            active.setOnErrorListener(null)
            if (active.isPlaying) active.stop()
            active.release()
        }
    }

    private fun releaseThunderPlayerInstance(player: MediaPlayer) {
        if (thunderPlayer === player) thunderPlayer = null
        runCatching {
            player.setOnPreparedListener(null)
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            player.release()
        }
    }

    private fun cancelScheduledTask() {
        scheduledTask?.let(handler::removeCallbacks)
        scheduledTask = null
    }

    private fun stopPlayer() {
        val active = mediaPlayer ?: return
        mediaPlayer = null
        runCatching {
            active.setOnCompletionListener(null)
            active.setOnErrorListener(null)
            if (active.isPlaying) active.stop()
            active.release()
        }
    }

    private fun releasePlayerInstance(player: MediaPlayer) {
        if (mediaPlayer === player) mediaPlayer = null
        runCatching {
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            player.release()
        }
    }
}
