package com.royalplus.launcher

import android.os.Build
import java.util.Date

/**
 * Isolates the Android ICU Hijri calendar API (introduced in API 24).
 * Android 6 devices (API 23), which are still covered by minSdk, safely
 * omit the Ramadan calendar highlight instead of failing class verification.
 */
internal object HijriCalendarCompat {
    fun isRamadan(date: Date): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return runCatching { Api24.isRamadan(date) }.getOrDefault(false)
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.N)
    private object Api24 {
        fun isRamadan(date: Date): Boolean {
            val calendar = android.icu.util.IslamicCalendar(date)
            return calendar.get(android.icu.util.IslamicCalendar.MONTH) == 8
        }
    }
}
