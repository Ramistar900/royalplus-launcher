package com.royalplus.launcher

import android.os.Build
import java.util.Date

/**
 * Guards Android ICU's Hijri calendar API for devices below API 24.
 */
internal object HijriCalendarCompat {
    fun isRamadan(date: Date): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return runCatching { Api24.isRamadan(date) }.getOrDefault(false)
    }

    @TargetApi(Build.VERSION_CODES.N)
    private object Api24 {
        fun isRamadan(date: Date): Boolean {
            val calendar = android.icu.util.IslamicCalendar(date)
            return calendar.get(android.icu.util.IslamicCalendar.MONTH) == 8
        }
    }
}
