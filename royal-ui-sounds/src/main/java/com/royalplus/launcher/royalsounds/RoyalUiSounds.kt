package com.royalplus.launcher.royalsounds

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Offline UI effects. Samples are preloaded selectively to stay below SoundPool's
 * decoded-memory budget on older devices, including Android 8 (API 26).
 */
enum class RoyalUiSound(val resourceId: Int) {
    NAV_FOCUS_MOVE(R.raw.royal_ui_nav_focus_move),
    NAV_SELECT(R.raw.royal_ui_nav_select),
    NAV_BACK(R.raw.royal_ui_nav_back),
    NAV_OPEN_MENU(R.raw.royal_ui_nav_open_menu),
    NAV_CLOSE_MENU(R.raw.royal_ui_nav_close_menu),
    NAV_HOME(R.raw.royal_ui_nav_home),
    NAV_TV_FOCUS(R.raw.royal_ui_nav_tv_focus),
    NAV_SCROLL_TICK(R.raw.royal_ui_nav_scroll_tick),
    NAV_SWIPE(R.raw.royal_ui_nav_swipe),
    NAV_ERROR(R.raw.royal_ui_nav_error),
    SELECT_TOGGLE_ON(R.raw.royal_ui_select_toggle_on),
    SELECT_TOGGLE_OFF(R.raw.royal_ui_select_toggle_off),
    SELECT_CHECK(R.raw.royal_ui_select_check),
    SELECT_RADIO(R.raw.royal_ui_select_radio),
    SELECT_SEARCH_START(R.raw.royal_ui_select_search_start),
    ACTION_CONFIRM(R.raw.royal_ui_action_confirm),
    ACTION_SUCCESS(R.raw.royal_ui_action_success),
    ACTION_ERROR(R.raw.royal_ui_action_error),
    ACTION_WARNING(R.raw.royal_ui_action_warning),
    ACTION_ADD_TO_CART(R.raw.royal_ui_action_add_to_cart),
    ACTION_ADD(R.raw.royal_ui_action_add),
    ACTION_REMOVE(R.raw.royal_ui_action_remove),
    ACTION_DELETE(R.raw.royal_ui_action_delete),
    ACTION_FAVORITE(R.raw.royal_ui_action_favorite),
    ACTION_SHARE(R.raw.royal_ui_action_share),
    ACTION_DOWNLOAD(R.raw.royal_ui_action_download),
    ACTION_CHECK_DONE(R.raw.royal_ui_action_check_done),
    ACTION_TOGGLE_WARNING(R.raw.royal_ui_action_toggle_warning),
    MEDIA_PLAY(R.raw.royal_ui_media_play),
    MEDIA_PAUSE(R.raw.royal_ui_media_pause),
    MEDIA_STOP(R.raw.royal_ui_media_stop),
    MEDIA_NEXT(R.raw.royal_ui_media_next),
    MEDIA_PREV(R.raw.royal_ui_media_prev),
    MEDIA_SEEK(R.raw.royal_ui_media_seek),
    MEDIA_VOLUME_TICK(R.raw.royal_ui_media_volume_tick),
    MEDIA_MUTE(R.raw.royal_ui_media_mute),
    MEDIA_UNMUTE(R.raw.royal_ui_media_unmute),
    MEDIA_FULLSCREEN_ENTER(R.raw.royal_ui_media_fullscreen_enter),
    MEDIA_FULLSCREEN_EXIT(R.raw.royal_ui_media_fullscreen_exit),
    MEDIA_RECORD_START(R.raw.royal_ui_media_record_start),
    MEDIA_RECORD_STOP(R.raw.royal_ui_media_record_stop),
    MEDIA_SUBTITLE_ON(R.raw.royal_ui_media_subtitle_on),
    TV_CHANNEL_CHANGE(R.raw.royal_ui_tv_channel_change),
    TV_POWER_ON(R.raw.royal_ui_tv_power_on),
    TV_POWER_OFF(R.raw.royal_ui_tv_power_off),
    SYSTEM_NOTIFICATION(R.raw.royal_ui_system_notification),
    SYSTEM_LOADING_TICK(R.raw.royal_ui_system_loading_tick),
    SYSTEM_LOCK(R.raw.royal_ui_system_lock),
    SYSTEM_KEYPAD_TAP(R.raw.royal_ui_system_keypad_tap),
}

object RoyalUiSounds {
    private const val PREFS_NAME = "royal_ui_sounds"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_VOLUME = "volume"
    private const val DEFAULT_VOLUME = 0.18f
    // Keep headroom below SoundPool's approximately 1 MiB decoded sample limit.
    private const val MAX_CACHED_BYTES = 900 * 1024

    private val lock = Any()
    private var appContext: Context? = null
    private var soundPool: SoundPool? = null
    private val sampleIds = mutableMapOf<RoyalUiSound, Int>()
    private val sampleBytes = mutableMapOf<RoyalUiSound, Int>()
    private val loadedSampleIds = mutableSetOf<Int>()
    private val pendingPlaySampleIds = mutableSetOf<Int>()
    private var reservedBytes = 0
    @Volatile private var enabled = true
    @Volatile private var volume = DEFAULT_VOLUME

    /**
     * Preload only sounds that this app actually uses. The remaining effects are
     * loaded on demand, so an app never decodes all 49 files into SoundPool.
     */
    fun initialize(
        context: Context,
        preloadSounds: Set<RoyalUiSound> = emptySet()
    ) {
        val applicationContext = context.applicationContext
        synchronized(lock) {
            if (soundPool == null) {
                appContext = applicationContext
                val preferences = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                enabled = preferences.getBoolean(KEY_ENABLED, true)
                volume = preferences.getFloat(KEY_VOLUME, DEFAULT_VOLUME).coerceIn(0f, 1f)

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val pool = SoundPool.Builder()
                    .setMaxStreams(4)
                    .setAudioAttributes(attributes)
                    .build()
                soundPool = pool
                pool.setOnLoadCompleteListener { completedPool, sampleId, status ->
                    var playPending = false
                    var playVolume = 0f
                    synchronized(lock) {
                        if (soundPool === completedPool) {
                            val sound = sampleIds.entries.firstOrNull { it.value == sampleId }?.key
                            if (status == 0) {
                                loadedSampleIds.add(sampleId)
                                playPending = enabled && pendingPlaySampleIds.remove(sampleId)
                                playVolume = volume
                            } else {
                                pendingPlaySampleIds.remove(sampleId)
                                if (sound != null) {
                                    sampleIds.remove(sound)
                                    reservedBytes = (reservedBytes - (sampleBytes.remove(sound) ?: 0)).coerceAtLeast(0)
                                }
                            }
                        }
                    }
                    if (playPending) {
                        runCatching { completedPool.play(sampleId, playVolume, playVolume, 1, 0, 1f) }
                    }
                }
            }
            preloadSounds.forEach { loadSampleLocked(applicationContext, it) }
        }
    }

    private fun loadSampleLocked(context: Context, sound: RoyalUiSound): Int {
        sampleIds[sound]?.let { return it }
        val pool = soundPool ?: return 0
        val bytes = runCatching {
            context.resources.openRawResource(sound.resourceId).use { it.available() }
        }.getOrDefault(0)
        if (bytes <= 0 || reservedBytes + bytes > MAX_CACHED_BYTES) return 0

        val sampleId = runCatching { pool.load(context, sound.resourceId, 1) }.getOrDefault(0)
        if (sampleId != 0) {
            sampleIds[sound] = sampleId
            sampleBytes[sound] = bytes
            reservedBytes += bytes
        }
        return sampleId
    }

    fun play(context: Context, sound: RoyalUiSound): Boolean {
        initialize(context)
        return play(sound)
    }

    fun play(sound: RoyalUiSound): Boolean {
        val pool: SoundPool
        val sampleId: Int
        val level: Float
        synchronized(lock) {
            if (!enabled) return false
            pool = soundPool ?: return false
            val context = appContext ?: return false
            sampleId = sampleIds[sound] ?: loadSampleLocked(context, sound)
            if (sampleId == 0) return false
            if (sampleId !in loadedSampleIds) {
                pendingPlaySampleIds.add(sampleId)
                return true
            }
            level = volume
        }
        return runCatching { pool.play(sampleId, level, level, 1, 0, 1f) != 0 }
            .getOrDefault(false)
    }

    fun isEnabled(context: Context): Boolean {
        initialize(context)
        return enabled
    }

    fun setEnabled(context: Context, value: Boolean) {
        initialize(context)
        enabled = value
        if (!value) synchronized(lock) { pendingPlaySampleIds.clear() }
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun setVolume(context: Context, value: Float) {
        initialize(context)
        volume = value.coerceIn(0f, 1f)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_VOLUME, volume).apply()
    }

    fun release() {
        val pool = synchronized(lock) {
            val current = soundPool
            soundPool = null
            sampleIds.clear()
            sampleBytes.clear()
            loadedSampleIds.clear()
            pendingPlaySampleIds.clear()
            reservedBytes = 0
            appContext = null
            current
        }
        runCatching { pool?.release() }
    }
}
