package com.royalplus.launcher

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

internal enum class CalendarReminderRepeat {
    NONE, MONTHLY, YEARLY
}

internal data class GregorianCalendarReminder(
    val id: String,
    val title: String,
    val description: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val leadMinutes: Int,
    val repeat: CalendarReminderRepeat
)

internal object GregorianCalendarReminderScheduler {
    private const val PREFERENCES = "royalplus_gregorian_calendar"
    private const val KEY_REMINDERS = "reminders"
    private const val ACTION_REMIND = "com.royalplus.launcher.action.CALENDAR_REMINDER"
    private const val EXTRA_REMINDER_ID = "calendar_reminder_id"
    private const val CHANNEL_ID = "royalplus_calendar_reminders"

    fun load(context: Context): List<GregorianCalendarReminder> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_REMINDERS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val repeat = runCatching {
                        CalendarReminderRepeat.valueOf(item.optString("repeat", "NONE"))
                    }.getOrDefault(CalendarReminderRepeat.NONE)
                    val reminder = GregorianCalendarReminder(
                        id = item.optString("id"),
                        title = item.optString("title").trim(),
                        description = item.optString("description"),
                        year = item.optInt("year"),
                        month = item.optInt("month"),
                        day = item.optInt("day"),
                        hour = item.optInt("hour"),
                        minute = item.optInt("minute"),
                        leadMinutes = item.optInt("leadMinutes"),
                        repeat = repeat
                    )
                    if (isValid(reminder)) add(reminder)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, reminders: List<GregorianCalendarReminder>) {
        val array = JSONArray()
        reminders.filter(::isValid).forEach { reminder ->
            array.put(
                JSONObject()
                    .put("id", reminder.id)
                    .put("title", reminder.title)
                    .put("description", reminder.description)
                    .put("year", reminder.year)
                    .put("month", reminder.month)
                    .put("day", reminder.day)
                    .put("hour", reminder.hour)
                    .put("minute", reminder.minute)
                    .put("leadMinutes", reminder.leadMinutes)
                    .put("repeat", reminder.repeat.name)
            )
        }
        context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REMINDERS, array.toString())
            .apply()
    }

    fun upsert(context: Context, reminder: GregorianCalendarReminder) {
        val updated = load(context).filterNot { it.id == reminder.id } + reminder
        save(context, updated)
        schedule(context, reminder)
    }

    fun delete(context: Context, reminderId: String) {
        cancel(context, reminderId)
        save(context, load(context).filterNot { it.id == reminderId })
    }

    fun rescheduleAll(context: Context) {
        load(context).forEach { schedule(context, it) }
    }

    fun schedule(context: Context, reminder: GregorianCalendarReminder) {
        val appContext = context.applicationContext
        val triggerAt = nextTriggerAt(reminder, System.currentTimeMillis(), TimeZone.getDefault())
        if (triggerAt == null) {
            cancel(appContext, reminder.id)
            return
        }
        val manager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = pendingIntent(appContext, reminder.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canScheduleExactAlarms(manager) ->
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                else ->
                    manager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: SecurityException) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            }
        }
    }

    fun cancel(context: Context, reminderId: String) {
        val manager = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val pendingIntent = pendingIntent(
            context.applicationContext,
            reminderId,
            PendingIntent.FLAG_NO_CREATE
        ) ?: return
        manager?.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    internal fun nextTriggerAt(
        reminder: GregorianCalendarReminder,
        nowMillis: Long,
        timeZone: TimeZone
    ): Long? {
        if (!isValid(reminder)) return null
        val event = Calendar.getInstance(timeZone).apply {
            clear()
            set(Calendar.YEAR, reminder.year)
            set(Calendar.MONTH, reminder.month)
            set(Calendar.DAY_OF_MONTH, 1)
            val maximumDay = getActualMaximum(Calendar.DAY_OF_MONTH)
            set(Calendar.DAY_OF_MONTH, reminder.day.coerceAtMost(maximumDay))
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var triggerAt = event.timeInMillis - reminder.leadMinutes.coerceAtLeast(0) * 60_000L
        if (event.timeInMillis <= nowMillis) {
            if (reminder.repeat == CalendarReminderRepeat.NONE) return null
        } else if (triggerAt <= nowMillis) {
            // The chosen advance warning time has passed, but the event itself is still upcoming.
            return nowMillis + 5_000L
        }
        if (reminder.repeat == CalendarReminderRepeat.NONE) {
            return triggerAt.takeIf { it > nowMillis }
        }
        var attempts = 0
        while (triggerAt <= nowMillis && attempts < 240) {
            event.set(Calendar.DAY_OF_MONTH, 1)
            when (reminder.repeat) {
                CalendarReminderRepeat.MONTHLY -> event.add(Calendar.MONTH, 1)
                CalendarReminderRepeat.YEARLY -> {
                    event.add(Calendar.YEAR, 1)
                    event.set(Calendar.MONTH, reminder.month)
                }
                CalendarReminderRepeat.NONE -> return null
            }
            val targetDay = reminder.day.coerceAtMost(event.getActualMaximum(Calendar.DAY_OF_MONTH))
            event.set(Calendar.DAY_OF_MONTH, targetDay)
            triggerAt = event.timeInMillis - reminder.leadMinutes.coerceAtLeast(0) * 60_000L
            attempts++
        }
        return triggerAt.takeIf { it > nowMillis }
    }

    private fun isValid(reminder: GregorianCalendarReminder): Boolean =
        reminder.id.isNotBlank() &&
            reminder.title.isNotBlank() &&
            reminder.month in 0..11 &&
            reminder.day in 1..31 &&
            reminder.hour in 0..23 &&
            reminder.minute in 0..59 &&
            reminder.leadMinutes in 0..10080 &&
            reminder.year in 1900..9999

    private fun canScheduleExactAlarms(manager: AlarmManager): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)
        } else {
            true
        }

    private fun pendingIntent(context: Context, id: String, flags: Int): PendingIntent? {
        val intent = Intent(context, GregorianCalendarReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            data = Uri.parse("royalplus://calendar/reminder/" + Uri.encode(id))
            putExtra(EXTRA_REMINDER_ID, id)
        }
        val actualFlags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(
            context,
            id.hashCode() and 0x7fffffff,
            intent,
            actualFlags
        )
    }

    internal fun find(context: Context, id: String): GregorianCalendarReminder? =
        load(context).firstOrNull { it.id == id }

    internal fun showNotification(context: Context, reminder: GregorianCalendarReminder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "ROYAL+ Calendar",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Reminders for saved Gregorian calendar events"
                    enableVibration(true)
                }
            )
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            reminder.id.hashCode() and 0x7fffffff,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = reminder.description.ifBlank {
            when (Locale.getDefault().language) {
                "en" -> "Your occasion is due"
                "tr" -> "Etkinliğinizin zamanı geldi"
                else -> "حان موعد المناسبة"
            }
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context)
                .setPriority(Notification.PRIORITY_HIGH)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(reminder.title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .build()
        manager.notify(reminder.id.hashCode(), notification)
    }
}

internal class GregorianCalendarReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getStringExtra("calendar_reminder_id") ?: return
        val reminder = GregorianCalendarReminderScheduler.find(context, id) ?: return
        GregorianCalendarReminderScheduler.showNotification(context, reminder)
        if (reminder.repeat != CalendarReminderRepeat.NONE) {
            GregorianCalendarReminderScheduler.schedule(context, reminder)
        }
    }
}

internal class GregorianCalendarReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> GregorianCalendarReminderScheduler.rescheduleAll(context)
        }
    }
}
