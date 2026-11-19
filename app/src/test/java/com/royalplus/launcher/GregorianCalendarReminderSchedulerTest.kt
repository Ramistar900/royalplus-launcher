package com.royalplus.launcher

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GregorianCalendarReminderSchedulerTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun oneTimeReminderHonorsAdvanceWarning() {
        val reminder = reminder(year = 2030, month = Calendar.JUNE, day = 15, hour = 12, minute = 0, lead = 60)
        val now = utcMillis(2030, Calendar.JUNE, 14, 8, 0)

        val actual = GregorianCalendarReminderScheduler.nextTriggerAt(reminder, now, utc)

        assertEquals(utcMillis(2030, Calendar.JUNE, 15, 11, 0), actual)
    }

    @Test
    fun upcomingEventStillNotifiesWhenAdvanceWarningTimeHasPassed() {
        val reminder = reminder(year = 2030, month = Calendar.JUNE, day = 15, hour = 12, minute = 0, lead = 1440)
        val now = utcMillis(2030, Calendar.JUNE, 15, 10, 30)

        val actual = GregorianCalendarReminderScheduler.nextTriggerAt(reminder, now, utc)

        assertEquals(now + 5_000L, actual)
    }

    @Test
    fun monthlyReminderClampsToLastDayOfShorterMonth() {
        val reminder = reminder(
            year = 2027,
            month = Calendar.JANUARY,
            day = 31,
            hour = 10,
            minute = 0,
            lead = 0,
            repeat = CalendarReminderRepeat.MONTHLY
        )
        val now = utcMillis(2027, Calendar.FEBRUARY, 1, 0, 0)

        val actual = GregorianCalendarReminderScheduler.nextTriggerAt(reminder, now, utc)

        assertEquals(utcMillis(2027, Calendar.FEBRUARY, 28, 10, 0), actual)
    }

    @Test
    fun yearlyLeapDayReminderUsesFebruaryTwentyEighthInNonLeapYear() {
        val reminder = reminder(
            year = 2028,
            month = Calendar.FEBRUARY,
            day = 29,
            hour = 9,
            minute = 30,
            lead = 0,
            repeat = CalendarReminderRepeat.YEARLY
        )
        val now = utcMillis(2028, Calendar.MARCH, 1, 0, 0)

        val actual = GregorianCalendarReminderScheduler.nextTriggerAt(reminder, now, utc)

        assertEquals(utcMillis(2029, Calendar.FEBRUARY, 28, 9, 30), actual)
    }

    @Test
    fun pastOneTimeReminderIsNotScheduledAgain() {
        val reminder = reminder(year = 2027, month = Calendar.JANUARY, day = 1, hour = 9, minute = 0, lead = 0)
        val now = utcMillis(2027, Calendar.JANUARY, 2, 0, 0)

        assertNull(GregorianCalendarReminderScheduler.nextTriggerAt(reminder, now, utc))
    }

    private fun reminder(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        lead: Int,
        repeat: CalendarReminderRepeat = CalendarReminderRepeat.NONE
    ) = GregorianCalendarReminder(
        id = "calendar-test",
        title = "Test occasion",
        description = "",
        year = year,
        month = month,
        day = day,
        hour = hour,
        minute = minute,
        leadMinutes = lead,
        repeat = repeat
    )

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }.timeInMillis
}
