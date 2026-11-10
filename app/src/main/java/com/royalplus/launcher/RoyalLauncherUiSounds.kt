package com.royalplus.launcher

import android.content.Context
import com.royalplus.launcher.royalsounds.RoyalUiSound
import com.royalplus.launcher.royalsounds.RoyalUiSounds

/**
 * Uses the launcher's existing click-sound preference for every app screen.
 * UI effects remain separate from weather and nature ambience.
 */
internal fun playLauncherUiSound(context: Context, sound: RoyalUiSound) {
    val preferences = context.getSharedPreferences(
        "royalplus_launcher_preferences",
        Context.MODE_PRIVATE
    )
    if (!preferences.getBoolean("ui_click_sounds_enabled", true)) return
    RoyalUiSounds.play(context, sound)
}
