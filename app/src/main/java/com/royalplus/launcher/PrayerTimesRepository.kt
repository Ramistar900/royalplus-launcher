package com.royalplus.launcher

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

internal data class PrayerTimesData(
    val hijriDate: String,
    val gregorianDate: String,
    val city: String,
    val times: Map<String, String>,
    val error: String? = null
)

internal object PrayerTimesRepository {
    private val executor = Executors.newSingleThreadExecutor()
    private val prayerNames = linkedMapOf(
        "Fajr" to "الفجر",
        "Sunrise" to "الشروق",
        "Dhuhr" to "الظهر",
        "Asr" to "العصر",
        "Maghrib" to "المغرب",
        "Isha" to "العشاء"
    )
    private val hijriMonths = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )

    /** On-device calculation: MWL angles (Fajr 18°, Isha 17°), standard Asr shadow. */
    fun fetch(latitude: Double, longitude: Double, city: String, hijriDayOffset: Int = 0, onResult: (PrayerTimesData) -> Unit) {
        executor.execute {
            val result = try {
                require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
                val zone = timeZoneFor(city)
                val calendar = GregorianCalendar(zone, Locale.US).apply { timeInMillis = System.currentTimeMillis() }
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH) + 1
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
                val daysInYear = if (calendar.isLeapYear(year)) 366.0 else 365.0
                val gamma = 2.0 * PI / daysInYear * (dayOfYear - 1.0)
                val equationOfTime = 229.18 * (
                    0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
                        0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma)
                    )
                val declination = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
                    0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
                    0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
                val offsetMinutes = zone.getOffset(calendar.timeInMillis) / 60_000.0
                val solarNoon = 720.0 - 4.0 * longitude - equationOfTime + offsetMinutes
                val latRad = Math.toRadians(latitude)
                fun hourAngleForAltitude(altitudeDegrees: Double): Double? {
                    val altitude = Math.toRadians(altitudeDegrees)
                    val denominator = cos(latRad) * cos(declination)
                    if (kotlin.math.abs(denominator) < 1e-9) return null
                    val cosine = (sin(altitude) - sin(latRad) * sin(declination)) / denominator
                    if (cosine !in -1.0..1.0) return null
                    return Math.toDegrees(acos(cosine))
                }
                fun minutesAtAngle(altitude: Double, morning: Boolean): Double? {
                    val angle = hourAngleForAltitude(altitude) ?: return null
                    return solarNoon + (if (morning) -1.0 else 1.0) * angle * 4.0
                }
                val asrAltitude = Math.toDegrees(
                    atan(1.0 / (1.0 + tan(kotlin.math.abs(latRad - declination))))
                )
                val asrAngle = hourAngleForAltitude(asrAltitude)
                fun formatMinutes(value: Double): String {
                    val rounded = ((floor(value + 0.5).toInt() % 1440) + 1440) % 1440
                    return String.format(Locale.US, "%02d:%02d", rounded / 60, rounded % 60)
                }
                val times = linkedMapOf(
                    "Fajr" to formatMinutes(minutesAtAngle(-18.0, true) ?: solarNoon - 90.0),
                    "Sunrise" to formatMinutes(minutesAtAngle(-0.833, true) ?: solarNoon - 360.0),
                    "Dhuhr" to formatMinutes(solarNoon + 1.0),
                    "Asr" to formatMinutes((asrAngle?.let { solarNoon + it * 4.0 }) ?: solarNoon + 180.0),
                    "Maghrib" to formatMinutes(minutesAtAngle(-0.833, false) ?: solarNoon + 360.0),
                    "Isha" to formatMinutes(minutesAtAngle(-17.0, false) ?: solarNoon + 90.0)
                )
                val gregorianDate = SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH).apply { timeZone = zone }
                    .format(calendar.time)
                val hijriDate = calculateHijriDate(year, month, day, zone, hijriDayOffset)
                PrayerTimesData(hijriDate, gregorianDate, city, times)
            } catch (_: Exception) {
                PrayerTimesData("", "", city, emptyMap(), "تعذّر حساب المواقيت محليًا. اختر مدينة صحيحة وحاول مجددًا.")
            }
            onResult(result)
        }
    }

    private fun timeZoneFor(city: String): TimeZone {
        val zoneId = when (city.trim().lowercase(Locale.ROOT)) {
            "إسطنبول", "istanbul", "istanbul, türkiye", "istanbul, turkey" -> "Europe/Istanbul"
            "دمشق", "damascus" -> "Asia/Damascus"
            "مكة المكرمة", "مكة", "mecca", "makkah", "الرياض", "riyadh" -> "Asia/Riyadh"
            "بغداد", "baghdad" -> "Asia/Baghdad"
            else -> TimeZone.getDefault().id
        }
        return TimeZone.getTimeZone(zoneId)
    }

    private fun calculateHijriDate(year: Int, month: Int, day: Int, zone: TimeZone, dayOffset: Int): String {
        val gregorian = GregorianCalendar(zone, Locale.US).apply {
            clear()
            set(year, month - 1, day, 12, 0, 0)
        }
        val julianDay = floor(gregorian.timeInMillis / 86_400_000.0 + 2440587.5 + 0.5).toInt() + dayOffset
        fun islamicToJulian(y: Int, m: Int, d: Int): Int =
            d + kotlin.math.ceil(29.5 * (m - 1)).toInt() + (y - 1) * 354 +
                floor((3.0 + 11.0 * y) / 30.0).toInt() + 1948439 - 1

        var hijriYear = floor((30.0 * (julianDay - 1948439) + 10646.0) / 10631.0).toInt()
        while (julianDay < islamicToJulian(hijriYear, 1, 1)) hijriYear--
        while (julianDay >= islamicToJulian(hijriYear + 1, 1, 1)) hijriYear++

        var hijriMonth = 1
        while (hijriMonth < 12 && julianDay >= islamicToJulian(hijriYear, hijriMonth + 1, 1)) {
            hijriMonth++
        }
        val hijriDay = julianDay - islamicToJulian(hijriYear, hijriMonth, 1) + 1
        return "$hijriDay ${hijriMonths[hijriMonth - 1]} $hijriYear هـ"
    }

    fun displayNames(): Map<String, String> = prayerNames
}
