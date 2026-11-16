package com.royalplus.launcher

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Internal resource-quality ceilings. These presets never change Android's display mode,
 * Compose density, layout dimensions, or text scale.
 */
internal enum class RoyalInterfaceQualityPreset(
    val storedValue: String,
    val label: String,
    val maxEdgePx: Int,
    val maxPixels: Long,
    val motionScale: Float
) {
    AUTO("auto", "تلقائي", 0, 0L, 1f),
    P360("360p", "360p", 640, 230_400L, 0.25f),
    P480("480p", "480p", 854, 409_920L, 0.40f),
    P720("720p", "720p", 1280, 921_600L, 0.58f),
    P1080("1080p", "1080p", 1920, 2_073_600L, 0.76f),
    P1440("1440p", "1440p", 2560, 3_686_400L, 0.90f),
    P4K("4k", "4K", 3840, 8_294_400L, 1f);

    companion object {
        fun fromStoredValue(value: String?): RoyalInterfaceQualityPreset =
            values().firstOrNull { it.storedValue == value } ?: AUTO
    }
}

internal data class RoyalInterfaceQualityBudget(
    val selectedPreset: RoyalInterfaceQualityPreset,
    val effectivePreset: RoyalInterfaceQualityPreset,
    val maxEdgePx: Int,
    val maxPixels: Long,
    val motionScale: Float,
    val weatherVideoEnabled: Boolean
)

/** A selected level is an upper bound; display and memory safeguards can lower it. */
internal fun resolveRoyalInterfaceQualityBudget(
    selectedPreset: RoyalInterfaceQualityPreset,
    screenWidthPx: Int,
    screenHeightPx: Int,
    memoryClassMb: Int
): RoyalInterfaceQualityBudget {
    val longestEdge = maxOf(screenWidthPx, screenHeightPx).coerceAtLeast(1)
    val displayLimit = when {
        longestEdge <= 640 -> RoyalInterfaceQualityPreset.P360
        longestEdge <= 854 -> RoyalInterfaceQualityPreset.P480
        longestEdge <= 1280 -> RoyalInterfaceQualityPreset.P720
        longestEdge <= 1920 -> RoyalInterfaceQualityPreset.P1080
        longestEdge <= 2560 -> RoyalInterfaceQualityPreset.P1440
        else -> RoyalInterfaceQualityPreset.P4K
    }
    val safeMemoryClass = memoryClassMb.takeIf { it > 0 } ?: 192
    val memoryLimit = when {
        safeMemoryClass <= 128 -> RoyalInterfaceQualityPreset.P480
        safeMemoryClass <= 192 -> RoyalInterfaceQualityPreset.P720
        safeMemoryClass <= 256 -> RoyalInterfaceQualityPreset.P1080
        safeMemoryClass <= 384 -> RoyalInterfaceQualityPreset.P1440
        else -> RoyalInterfaceQualityPreset.P4K
    }
    val requestedLimit = if (selectedPreset == RoyalInterfaceQualityPreset.AUTO) displayLimit else selectedPreset
    val effectiveOrdinal = minOf(requestedLimit.ordinal, displayLimit.ordinal, memoryLimit.ordinal)
    val effective = RoyalInterfaceQualityPreset.values()
        .first { it.ordinal == effectiveOrdinal.coerceAtLeast(RoyalInterfaceQualityPreset.P360.ordinal) }
    return RoyalInterfaceQualityBudget(
        selectedPreset = selectedPreset,
        effectivePreset = effective,
        maxEdgePx = effective.maxEdgePx,
        maxPixels = effective.maxPixels,
        motionScale = effective.motionScale,
        weatherVideoEnabled = effective.ordinal >= RoyalInterfaceQualityPreset.P720.ordinal
    )
}

/** Persists one device-wide quality choice and exposes it as Compose snapshot state. */
internal object RoyalInterfaceQualitySettings {
    private const val PREFERENCES = "royalplus_interface_quality"
    private const val KEY_PRESET = "selected_preset"

    var current by mutableStateOf(RoyalInterfaceQualityPreset.AUTO)
        private set

    fun load(context: Context) {
        val stored = context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_PRESET, RoyalInterfaceQualityPreset.AUTO.storedValue)
        current = RoyalInterfaceQualityPreset.fromStoredValue(stored)
    }

    fun select(context: Context, preset: RoyalInterfaceQualityPreset) {
        current = preset
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(KEY_PRESET, preset.storedValue).apply()
    }

    fun budget(context: Context, preset: RoyalInterfaceQualityPreset = current): RoyalInterfaceQualityBudget {
        val appContext = context.applicationContext
        val metrics = appContext.resources.displayMetrics
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return resolveRoyalInterfaceQualityBudget(
            selectedPreset = preset,
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            memoryClassMb = activityManager?.memoryClass ?: 192
        )
    }
}
