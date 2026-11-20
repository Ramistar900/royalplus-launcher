package com.royalplus.launcher

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Resolves the app's operating role independently of microphone hardware.
 * A television is always a caption receiver; it never starts the phone capture path.
 */
internal enum class TranslationDeviceRole { PHONE_CAPTURE, TELEVISION_RECEIVER }

internal object TranslationDeviceRoleDetector {
    fun detect(context: Context): TranslationDeviceRole {
        val packageManager = context.packageManager
        val uiModeType = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        val isTelevision = uiModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            packageManager.hasSystemFeature("android.hardware.type.television")
        return if (isTelevision) {
            TranslationDeviceRole.TELEVISION_RECEIVER
        } else {
            TranslationDeviceRole.PHONE_CAPTURE
        }
    }
}
