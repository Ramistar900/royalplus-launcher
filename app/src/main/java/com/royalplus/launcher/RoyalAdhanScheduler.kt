package com.royalplus.launcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

internal data class RoyalPrayerLocation(
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val timeZoneId: String,
    val isManual: Boolean
)

internal object RoyalAdhanScheduler {
    private const val PREFS = "royalplus_adhan_preferences"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LATITUDE = "latitude"
    private const val KEY_LONGITUDE = "longitude"
    private const val KEY_CITY = "city"
    private const val KEY_TIME_ZONE = "time_zone"
    private const val KEY_MANUAL_LOCATION = "manual_location"
    private const val KEY_LANGUAGE = "language"
    private const val REQUEST_CODE = 84124
    internal const val ACTION_FIRE = "com.royalplus.launcher.action.PLAY_ADHAN"

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean, language: String = "ar") {
        preferences(context).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_LANGUAGE, language)
            .apply()
        if (enabled) {
            scheduleNext(context)
        } else {
            cancel(context)
            runCatching { context.applicationContext.stopService(Intent(context, RoyalAdhanPlaybackService::class.java)) }
        }
    }

    fun ensureLocation(
        context: Context,
        latitude: Double?,
        longitude: Double?,
        city: String,
        timeZoneId: String?
    ) {
        if (latitude == null || longitude == null) return
        val prefs = preferences(context)
        if (prefs.getBoolean(KEY_MANUAL_LOCATION, false) &&
            prefs.contains(KEY_LATITUDE) && prefs.contains(KEY_LONGITUDE)
        ) return
        saveLocation(context, latitude, longitude, city, timeZoneId, false)
    }

    fun updateLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
        city: String,
        timeZoneId: String?,
        manual: Boolean
    ) {
        saveLocation(context, latitude, longitude, city, timeZoneId, manual)
    }

    private fun saveLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
        city: String,
        timeZoneId: String?,
        manual: Boolean
    ) {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return
        preferences(context).edit()
            .putString(KEY_LATITUDE, latitude.toString())
            .putString(KEY_LONGITUDE, longitude.toString())
            .putString(KEY_CITY, city.ifBlank { "الموقع الحالي" })
            .putString(KEY_TIME_ZONE, timeZoneId?.takeIf { it.isNotBlank() } ?: TimeZone.getDefault().id)
            .putBoolean(KEY_MANUAL_LOCATION, manual)
            .apply()
        if (isEnabled(context)) scheduleNext(context)
    }

    fun getLocation(context: Context): RoyalPrayerLocation? {
        val prefs = preferences(context)
        val latitude = prefs.getString(KEY_LATITUDE, null)?.toDoubleOrNull() ?: return null
        val longitude = prefs.getString(KEY_LONGITUDE, null)?.toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return RoyalPrayerLocation(
            latitude = latitude,
            longitude = longitude,
            city = prefs.getString(KEY_CITY, "الموقع الحالي") ?: "الموقع الحالي",
            timeZoneId = prefs.getString(KEY_TIME_ZONE, TimeZone.getDefault().id) ?: TimeZone.getDefault().id,
            isManual = prefs.getBoolean(KEY_MANUAL_LOCATION, false)
        )
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)
    }

    fun scheduleNext(context: Context, onComplete: (() -> Unit)? = null) {
        val completed = AtomicBoolean(false)
        fun finish() {
            if (completed.compareAndSet(false, true)) onComplete?.invoke()
        }

        try {
            val appContext = context.applicationContext
            if (!isEnabled(appContext)) {
                cancel(appContext)
                finish()
                return
            }
            val location = getLocation(appContext)
            if (location == null) {
                finish()
                return
            }

            PrayerTimesRepository.fetch(
                latitude = location.latitude,
                longitude = location.longitude,
                city = location.city,
                timeZoneId = location.timeZoneId
            ) { today ->
                if (today.error != null || today.times.isEmpty()) {
                    finish()
                    return@fetch
                }
                PrayerTimesRepository.fetch(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    city = location.city,
                    timeZoneId = location.timeZoneId,
                    calendarDayOffset = 1
                ) { tomorrow ->
                    if (tomorrow.error != null || tomorrow.times.isEmpty()) {
                        finish()
                        return@fetch
                    }
                    val now = System.currentTimeMillis()
                    val zone = TimeZone.getTimeZone(location.timeZoneId)
                    val candidates = mutableListOf<AlarmCandidate>().apply {
                        addCandidates(today.times, 0, zone, now)
                        addCandidates(tomorrow.times, 1, zone, now)
                    }
                    val next = candidates.minByOrNull { it.triggerAtMillis }
                    if (next == null || !isEnabled(appContext)) {
                        finish()
                        return@fetch
                    }
                    setAlarm(
                        appContext,
                        next,
                        location.city,
                        preferences(appContext).getString(KEY_LANGUAGE, "ar") ?: "ar"
                    )
                    finish()
                }
            }
        } catch (_: Exception) {
            finish()
        }
    }

    private data class AlarmCandidate(
        val prayerKey: String,
        val triggerAtMillis: Long
    )

    private fun MutableList<AlarmCandidate>.addCandidates(
        times: Map<String, String>,
        dayOffset: Int,
        zone: TimeZone,
        now: Long
    ) {
        times.filterKeys { it != "Sunrise" }.forEach { (key, value) ->
            val parts = value.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
            if (hour !in 0..23 || minute !in 0..59) return@forEach
            val calendar = GregorianCalendar(zone, Locale.US).apply {
                timeInMillis = now
                add(GregorianCalendar.DAY_OF_YEAR, dayOffset)
                set(GregorianCalendar.HOUR_OF_DAY, hour)
                set(GregorianCalendar.MINUTE, minute)
                set(GregorianCalendar.SECOND, 0)
                set(GregorianCalendar.MILLISECOND, 0)
            }
            val trigger = calendar.timeInMillis
            if (trigger > now + 15_000L) add(AlarmCandidate(key, trigger))
        }
    }

    private fun setAlarm(
        context: Context,
        candidate: AlarmCandidate,
        city: String,
        language: String
    ) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, RoyalAdhanAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            data = Uri.parse("royalplus://adhan/next")
            putExtra("prayer_key", candidate.prayerKey)
            putExtra("city", city)
            putExtra("language", language)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            when {
                // Android 8–11 do not require the Android 12 exact-alarm special access.
                // Use the exact API on these versions instead of silently downgrading prayer times.
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S ->
                    manager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        candidate.triggerAtMillis,
                        pendingIntent
                    )

                // Android 12+ requires exact-alarm access; otherwise keep a safe inexact fallback.
                canScheduleExactAlarms(context) ->
                    manager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        candidate.triggerAtMillis,
                        pendingIntent
                    )

                else ->
                    manager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        candidate.triggerAtMillis,
                        pendingIntent
                    )
            }
        } catch (_: SecurityException) {
            // A user or OEM may revoke exact-alarm access between the check and scheduling.
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    manager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        candidate.triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    manager.set(AlarmManager.RTC_WAKEUP, candidate.triggerAtMillis, pendingIntent)
                }
            }
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, RoyalAdhanAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            data = Uri.parse("royalplus://adhan/next")
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            manager?.cancel(pending)
            pending.cancel()
        }
    }
}

internal class RoyalAdhanAlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!RoyalAdhanScheduler.isEnabled(context)) return
        val serviceIntent = Intent(context, RoyalAdhanPlaybackService::class.java).apply {
            putExtra("prayer_key", intent?.getStringExtra("prayer_key").orEmpty())
            putExtra("city", intent?.getStringExtra("city").orEmpty())
            putExtra("language", intent?.getStringExtra("language") ?: "ar")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (_: Exception) {
            // Playback may be blocked by device policy; preserve the next prayer schedule.
        }
        val pending = goAsync()
        RoyalAdhanScheduler.scheduleNext(context) {
            runCatching { pending.finish() }
        }
    }
}

internal class RoyalAdhanRescheduleReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val pending = goAsync()
                RoyalAdhanScheduler.scheduleNext(context) {
                    runCatching { pending.finish() }
                }
            }
        }
    }
}
