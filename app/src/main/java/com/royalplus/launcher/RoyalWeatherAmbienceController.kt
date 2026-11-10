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
internal fun RoyalWeatherAmbienceEffect(weather: WeatherUiState) {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestWeather = rememberUpdatedState(weather)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME ->
                    RoyalWeatherAmbienceController.onForeground(context, latestWeather.value)
                Lifecycle.Event.ON_PAUSE ->
                    RoyalWeatherAmbienceController.onBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            RoyalWeatherAmbienceController.onForeground(context, latestWeather.value)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            RoyalWeatherAmbienceController.onBackground()
        }
    }

    SideEffect {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            RoyalWeatherAmbienceController.onWeatherChanged(context, weather)
        }
    }
}

internal object RoyalWeatherAmbienceController {
    const val PREFERENCES_NAME = "royalplus_launcher_preferences"
    const val MASTER_ENABLED_KEY = "royal_weather_audio_master_enabled"
    const val RAIN_ENABLED_KEY = "royal_weather_audio_rain_enabled"
    const val THUNDER_ENABLED_KEY = "royal_weather_audio_thunder_enabled"
    const val WIND_ENABLED_KEY = "royal_weather_audio_wind_enabled"
    const val BIRDS_ENABLED_KEY = "royal_weather_audio_morning_birds_enabled"
    const val VOLUME_KEY = "royal_weather_audio_volume"

    private const val MAX_WEATHER_AGE_MS = 90L * 60L * 1000L
    private const val WIND_THRESHOLD_KMH = 25
    private const val MORNING_START_HOUR = 6
    private const val MORNING_END_HOUR = 10
    private const val MIN_THUNDER_INTERVAL_MS = 7L * 60L * 1000L
    private const val MAX_THUNDER_INTERVAL_MS = 15L * 60L * 1000L
    private const val MAX_AMBIENCE_VOLUME = 0.48f

    private data class AmbienceScene(
        val key: String,
        val resourceId: Int = 0,
        val volumeMultiplier: Float = 1f,
        val streamUrl: String? = null
    )

    // CC0 recording by Tvabutzku1234 on Wikimedia Commons.
    // Used only for heavy snowfall, where the existing gentle breeze is insufficient.
    private const val HEAVY_SNOW_WIND_URL =
        "https://upload.wikimedia.org/wikipedia/commons/2/2d/Howling_wind.ogg"

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var latestWeather: WeatherUiState? = null
    private var latestWeatherIdentity: WeatherUiState? = null
    private var weatherReceivedAtElapsedMs: Long? = null
    private var foreground = false
    private var activeSceneKey: String? = null
    private var activeSceneVolumeMultiplier = 1f
    private var loopPlayer: MediaPlayer? = null
    private var thunderPlayer: MediaPlayer? = null
    private var thunderTask: Runnable? = null
    private var reevaluationTask: Runnable? = null
    private var audioManager: AudioManager? = null
    private var hasAudioFocus = false
    private var pausedForAudioFocus = false

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                releasePlayer(loopPlayer)
                releasePlayer(thunderPlayer)
                loopPlayer = null
                thunderPlayer = null
                activeSceneKey = null
                hasAudioFocus = false
                pausedForAudioFocus = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pausePlayer(loopPlayer)
                pausePlayer(thunderPlayer)
                pausedForAudioFocus = true
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> applyPlayerVolume(0.18f)
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedForAudioFocus) {
                    resumePlayer(loopPlayer)
                    resumePlayer(thunderPlayer)
                    pausedForAudioFocus = false
                }
                applyPlayerVolume(1f)
            }
        }
    }

    fun isEnabled(context: Context, key: String, defaultValue: Boolean = true): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(key, defaultValue)

    fun volume(context: Context): Float =
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getFloat(VOLUME_KEY, 0.72f)
            .coerceIn(0f, 1f)

    fun setSoundEnabled(context: Context, key: String, enabled: Boolean) {
        val applicationContext = context.applicationContext
        applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, enabled)
            .apply()
        appContext = applicationContext
        updatePlaybackForCurrentWeather()
        RoyalOwlAmbienceController.onAudioSettingsChanged()
    }

    fun setVolume(context: Context, value: Float) {
        val applicationContext = context.applicationContext
        val safeValue = value.coerceIn(0f, 1f)
        applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(VOLUME_KEY, safeValue)
            .apply()
        appContext = applicationContext
        if (safeValue <= 0f) {
            stopAllPlayback()
        } else {
            applyPlayerVolume(1f)
            updatePlaybackForCurrentWeather()
        }
        RoyalOwlAmbienceController.onAudioVolumeChanged()
    }

    fun onForeground(context: Context, weather: WeatherUiState) {
        appContext = context.applicationContext
        foreground = true
        updateWeatherIdentity(weather)
        updatePlaybackForCurrentWeather()
    }

    fun onWeatherChanged(context: Context, weather: WeatherUiState) {
        appContext = context.applicationContext
        val changed = updateWeatherIdentity(weather)
        if (changed && foreground) updatePlaybackForCurrentWeather()
    }

    fun onBackground() {
        foreground = false
        stopAllPlayback()
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
        return true
    }

    private fun updatePlaybackForCurrentWeather() {
        val context = appContext
        val weather = latestWeather
        if (!foreground || context == null || weather == null ||
            !isEnabled(context, MASTER_ENABLED_KEY) || volume(context) <= 0f ||
            !hasFreshWeather()) {
            stopAllPlayback()
            return
        }

        val nextScene = selectScene(context, weather)
        if (nextScene?.key != activeSceneKey) {
            stopLoopPlayer()
            activeSceneKey = nextScene?.key
            activeSceneVolumeMultiplier = nextScene?.volumeMultiplier ?: 1f
            if (nextScene != null) startLoopPlayer(context, nextScene)
        } else if (nextScene != null && loopPlayer == null && !pausedForAudioFocus) {
            startLoopPlayer(context, nextScene)
        }

        updateThunderSchedule(context, weather)
        scheduleReevaluation()
    }

    private fun scheduleReevaluation() {
        reevaluationTask?.let(handler::removeCallbacks)
        val task = Runnable {
            reevaluationTask = null
            updatePlaybackForCurrentWeather()
        }
        reevaluationTask = task
        handler.postDelayed(task, 60_000L)
    }

    private fun cancelReevaluation() {
        reevaluationTask?.let(handler::removeCallbacks)
        reevaluationTask = null
    }

    private fun hasFreshWeather(): Boolean {
        val receivedAt = weatherReceivedAtElapsedMs ?: return false
        val age = SystemClock.elapsedRealtime() - receivedAt
        return age in 0..MAX_WEATHER_AGE_MS &&
            latestWeather?.let { !it.isLoading && it.weatherCode != null } == true
    }

    private fun selectScene(context: Context, weather: WeatherUiState): AmbienceScene? {
        val code = weather.weatherCode ?: return null
        return when {
            // Thunderstorms use the separate distant-thunder one-shot track below.
            // Do not layer the old storm-rain recording over it.
            isRainCode(code) && isEnabled(context, RAIN_ENABLED_KEY) ->
                AmbienceScene("rain", R.raw.weather_rain_natural)
            (weather.weatherCode in setOf(75, 86)) &&
                (weather.windSpeedKmh ?: 0) >= 15 &&
                isEnabled(context, WIND_ENABLED_KEY) ->
                AmbienceScene(
                    key = "heavy-snow-wind",
                    volumeMultiplier = 0.88f,
                    streamUrl = HEAVY_SNOW_WIND_URL
                )
            (weather.windSpeedKmh ?: 0) >= WIND_THRESHOLD_KMH &&
                isEnabled(context, WIND_ENABLED_KEY) ->
                AmbienceScene("wind", R.raw.nature_wind_gentle)
            code in 0..1 && isMorning(weather) &&
                isEnabled(context, BIRDS_ENABLED_KEY) ->
                AmbienceScene("morning-birds", R.raw.nature_birds_morning)
            else -> null
        }
    }

    private fun isRainCode(code: Int): Boolean = code in 51..67 || code in 80..82

    private fun isMorning(weather: WeatherUiState): Boolean {
        val zone = weather.timeZoneId?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()
        val hour = Calendar.getInstance(zone).get(Calendar.HOUR_OF_DAY)
        return hour in MORNING_START_HOUR..MORNING_END_HOUR
    }

    private fun isThunderEligible(context: Context, weather: WeatherUiState): Boolean =
        isEnabled(context, MASTER_ENABLED_KEY) &&
            isEnabled(context, THUNDER_ENABLED_KEY) &&
            (weather.weatherCode ?: -1) in 95..99 &&
            volume(context) > 0f &&
            hasFreshWeather()

    private fun startLoopPlayer(context: Context, scene: AmbienceScene) {
        if (currentMediaVolumeIsMuted(context) || !requestAudioFocus(context)) return
        val player = runCatching {
            if (scene.streamUrl == null) MediaPlayer.create(context, scene.resourceId)
            else MediaPlayer()
        }.getOrNull() ?: return
        loopPlayer = player
        player.isLooping = true
        val level = (effectiveVolume(context) * scene.volumeMultiplier).coerceIn(0f, 1f)
        player.setVolume(level, level)
        player.setOnErrorListener { failed, _, _ ->
            if (loopPlayer === failed) loopPlayer = null
            releasePlayer(failed)
            maybeAbandonAudioFocus()
            true
        }
        if (scene.streamUrl != null) {
            try {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                player.setDataSource(scene.streamUrl)
                player.setOnPreparedListener { prepared ->
                    if (loopPlayer === prepared && foreground) {
                        runCatching { prepared.start() }.onFailure {
                            if (loopPlayer === prepared) loopPlayer = null
                            releasePlayer(prepared)
                            maybeAbandonAudioFocus()
                        }
                    } else {
                        releasePlayer(prepared)
                    }
                }
                player.prepareAsync()
            } catch (_: Exception) {
                if (loopPlayer === player) loopPlayer = null
                releasePlayer(player)
                maybeAbandonAudioFocus()
            }
        } else {
            runCatching { player.start() }.onFailure {
                if (loopPlayer === player) loopPlayer = null
                releasePlayer(player)
                maybeAbandonAudioFocus()
            }
        }
    }

    private fun updateThunderSchedule(context: Context, weather: WeatherUiState) {
        if (!isThunderEligible(context, weather)) {
            cancelThunderTask()
            stopThunderPlayer()
            return
        }
        if (thunderTask != null || thunderPlayer != null) return
        val delay = Random.nextLong(MIN_THUNDER_INTERVAL_MS, MAX_THUNDER_INTERVAL_MS + 1L)
        val task = Runnable {
            thunderTask = null
            val currentContext = appContext
            val currentWeather = latestWeather
            if (foreground && currentContext != null && currentWeather != null &&
                isThunderEligible(currentContext, currentWeather)) {
                playThunderOneShot(currentContext)
            } else {
                maybeAbandonAudioFocus()
            }
        }
        thunderTask = task
        handler.postDelayed(task, delay)
    }

    private fun playThunderOneShot(context: Context) {
        if (currentMediaVolumeIsMuted(context) || !requestAudioFocus(context)) {
            updateThunderSchedule(context, latestWeather ?: return)
            return
        }
        val player = runCatching {
            MediaPlayer.create(context, R.raw.weather_thunderstorm_thunder)
        }.getOrNull()
        if (player == null) {
            updateThunderSchedule(context, latestWeather ?: return)
            return
        }
        thunderPlayer = player
        val level = effectiveVolume(context) * 0.9f
        player.setVolume(level, level)
        player.setOnCompletionListener { completed ->
            if (thunderPlayer === completed) thunderPlayer = null
            releasePlayer(completed)
            maybeAbandonAudioFocus()
            val currentContext = appContext
            val currentWeather = latestWeather
            if (foreground && currentContext != null && currentWeather != null) {
                updateThunderSchedule(currentContext, currentWeather)
            }
        }
        player.setOnErrorListener { failed, _, _ ->
            if (thunderPlayer === failed) thunderPlayer = null
            releasePlayer(failed)
            maybeAbandonAudioFocus()
            val currentContext = appContext
            val currentWeather = latestWeather
            if (foreground && currentContext != null && currentWeather != null) {
                updateThunderSchedule(currentContext, currentWeather)
            }
            true
        }
        runCatching { player.start() }.onFailure {
            if (thunderPlayer === player) thunderPlayer = null
            releasePlayer(player)
            maybeAbandonAudioFocus()
            updateThunderSchedule(context, latestWeather ?: return)
        }
    }

    private fun requestAudioFocus(context: Context): Boolean {
        if (hasAudioFocus) return true
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val result = runCatching {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        audioManager = manager
        hasAudioFocus = true
        return true
    }

    private fun currentMediaVolumeIsMuted(context: Context): Boolean {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return manager.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0
    }

    private fun effectiveVolume(context: Context): Float =
        (volume(context) * MAX_AMBIENCE_VOLUME).coerceIn(0f, 1f)

    private fun applyPlayerVolume(multiplier: Float) {
        val context = appContext ?: return
        val level = effectiveVolume(context) *
            activeSceneVolumeMultiplier * multiplier.coerceIn(0f, 1f)
        runCatching { loopPlayer?.setVolume(level, level) }
        runCatching { thunderPlayer?.setVolume(level, level) }
    }

    private fun stopLoopPlayer() {
        val active = loopPlayer
        loopPlayer = null
        active?.let(::releasePlayer)
        activeSceneKey = null
        maybeAbandonAudioFocus()
    }

    private fun stopThunderPlayer() {
        val active = thunderPlayer
        thunderPlayer = null
        active?.let(::releasePlayer)
        maybeAbandonAudioFocus()
    }

    private fun stopAllPlayback() {
        cancelReevaluation()
        cancelThunderTask()
        val loop = loopPlayer
        val thunder = thunderPlayer
        loopPlayer = null
        thunderPlayer = null
        activeSceneKey = null
        activeSceneVolumeMultiplier = 1f
        releasePlayer(loop)
        releasePlayer(thunder)
        abandonAudioFocus()
    }

    private fun cancelThunderTask() {
        thunderTask?.let(handler::removeCallbacks)
        thunderTask = null
    }

    private fun maybeAbandonAudioFocus() {
        if (loopPlayer == null && thunderPlayer == null) abandonAudioFocus()
    }

    private fun abandonAudioFocus() {
        val manager = audioManager
        if (hasAudioFocus && manager != null) {
            runCatching {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(audioFocusListener)
            }
        }
        audioManager = null
        hasAudioFocus = false
        pausedForAudioFocus = false
    }

    private fun releasePlayer(player: MediaPlayer?) {
        if (player == null) return
        runCatching {
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            if (player.isPlaying) player.stop()
            player.release()
        }
    }

    private fun pausePlayer(player: MediaPlayer?) {
        if (player != null) runCatching { if (player.isPlaying) player.pause() }
    }

    private fun resumePlayer(player: MediaPlayer?) {
        if (player != null) runCatching { if (!player.isPlaying) player.start() }
    }
}
