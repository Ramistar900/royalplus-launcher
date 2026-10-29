package com.royalplus.launcher

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

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

    fun fetch(latitude: Double, longitude: Double, city: String, onResult: (PrayerTimesData) -> Unit) {
        executor.execute {
            val result = try {
                val timestamp = System.currentTimeMillis() / 1000L
                val endpoint = URL(
                    "https://api.aladhan.com/v1/timings/$timestamp" +
                        "?latitude=$latitude&longitude=$longitude&method=3"
                )
                val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val root = JSONObject(body)
                    if (root.optInt("code", -1) != 200) throw IllegalStateException("Prayer service unavailable")
                    val data = root.getJSONObject("data")
                    val timings = data.getJSONObject("timings")
                    val date = data.getJSONObject("date")
                    val hijri = date.getJSONObject("hijri")
                    val hijriDate = listOf(
                        hijri.optString("day"),
                        hijri.getJSONObject("month").optString("ar"),
                        hijri.getJSONObject("year").optString("ar")
                    ).filter { it.isNotBlank() }.joinToString(" ")
                    val gregorian = date.getJSONObject("gregorian")
                    val gregorianDate = listOf(
                        gregorian.optString("day"),
                        gregorian.getJSONObject("month").optString("en"),
                        gregorian.optString("year")
                    ).filter { it.isNotBlank() }.joinToString(" ")
                    val values = prayerNames.mapValues { (apiName, _) ->
                        timings.optString(apiName).substringBefore(" ").trim()
                    }
                    if (values.values.any { !it.matches(Regex("\\d{1,2}:\\d{2}")) }) {
                        throw IllegalStateException("Invalid prayer time data")
                    }
                    PrayerTimesData(hijriDate, gregorianDate, city, values)
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                PrayerTimesData("", "", city, emptyMap(), "تعذّر تحميل المواقيت. تحقق من الإنترنت ثم حدّث الصفحة.")
            }
            onResult(result)
        }
    }

    fun displayNames(): Map<String, String> = prayerNames
}
