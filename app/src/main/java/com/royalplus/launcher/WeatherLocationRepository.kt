package com.royalplus.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

internal data class WeatherForecastDay(
    val date: String,
    val weatherCode: Int,
    val maxTemperatureC: Int,
    val minTemperatureC: Int
)

internal data class WeatherUiState(
    val city: String = "جارٍ تحديد الموقع",
    val temperatureC: Int? = null,
    val apparentTemperatureC: Int? = null,
    val humidityPercent: Int? = null,
    val windSpeedKmh: Int? = null,
    val weatherCode: Int? = null,
    val description: String = "جارٍ تحميل الطقس",
    val timeZoneId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val forecast: List<WeatherForecastDay> = emptyList(),
    val isLoading: Boolean = true,
    val needsPermission: Boolean = false
)

internal object RoyalWeatherRepository {
    private val executor = Executors.newSingleThreadExecutor()

    fun fetch(context: Context, onResult: (WeatherUiState) -> Unit) {
        val appContext = context.applicationContext
        executor.execute {
            val result = try {
                val location = findLocation(appContext)
                    ?: throw IllegalStateException("Location unavailable")
                val city = findCity(appContext, location)
                fetchCurrentWeather(location, city)
            } catch (_: SecurityException) {
                WeatherUiState(
                    city = "الموقع غير متاح",
                    description = "تحقق من صلاحية الموقع",
                    isLoading = false,
                    needsPermission = true
                )
            } catch (_: Exception) {
                WeatherUiState(
                    city = "تعذّر تحديد المنطقة",
                    description = "تحقق من خدمات الموقع والإنترنت",
                    isLoading = false
                )
            }
            onResult(result)
        }
    }

    private fun findLocation(context: Context): Location? {
        if (
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = try {
            manager.getProviders(true)
                .filter { it == LocationManager.NETWORK_PROVIDER || it == LocationManager.GPS_PROVIDER }
        } catch (_: Exception) {
            emptyList()
        }

        // Include Android's passive provider for devices that receive location
        // from another system component even when GPS/network providers are disabled.
        val cachedProviders = (providers + LocationManager.PASSIVE_PROVIDER).distinct()
        val cached = cachedProviders.mapNotNull { provider ->
            try {
                manager.getLastKnownLocation(provider)
            } catch (_: Exception) {
                null
            }
        }.maxByOrNull { it.time }

        if (cached != null && System.currentTimeMillis() - cached.time < 30 * 60 * 1000L) {
            return cached
        }

        if (providers.isEmpty()) return cached

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getCurrentLocation(manager, providers) ?: cached
        } else {
            getLocationWithUpdates(manager, providers) ?: cached
        }
    }

    private fun getCurrentLocation(
        manager: LocationManager,
        providers: List<String>
    ): Location? {
        val latch = CountDownLatch(1)
        val best = AtomicReference<Location?>(null)
        val remaining = AtomicInteger(providers.size)
        val mainExecutor = java.util.concurrent.Executor { command ->
            Handler(Looper.getMainLooper()).post(command)
        }

        providers.forEach { provider ->
            try {
                manager.getCurrentLocation(provider, null, mainExecutor) { location ->
                    if (location != null) {
                        best.set(location)
                        latch.countDown()
                    } else if (remaining.decrementAndGet() <= 0) {
                        latch.countDown()
                    }
                }
            } catch (_: Exception) {
                if (remaining.decrementAndGet() <= 0) latch.countDown()
            }
        }

        latch.await(12, TimeUnit.SECONDS)
        return best.get()
    }

    @Suppress("DEPRECATION")
    private fun getLocationWithUpdates(
        manager: LocationManager,
        providers: List<String>
    ): Location? {
        val latch = CountDownLatch(1)
        val result = AtomicReference<Location?>(null)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                result.set(location)
                latch.countDown()
            }

            override fun onProviderDisabled(provider: String) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        providers.forEach { provider ->
            try {
                manager.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            } catch (_: Exception) {
                // Try the next available provider.
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        try {
            manager.removeUpdates(listener)
        } catch (_: Exception) {
            // Ignore providers that stopped while the request was active.
        }
        return result.get()
    }

    @Suppress("DEPRECATION")
    private fun findCity(context: Context, location: Location): String {
        return try {
            if (!Geocoder.isPresent()) return "الموقع الحالي"
            val addresses: List<Address>? = Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
            val address = addresses?.firstOrNull()
            val city = address?.locality
                ?: address?.subAdminArea
                ?: address?.adminArea
            val country = address?.countryName
            when {
                !city.isNullOrBlank() && !country.isNullOrBlank() -> "$city، $country"
                !city.isNullOrBlank() -> city
                !country.isNullOrBlank() -> country
                else -> "الموقع الحالي"
            }
        } catch (_: Exception) {
            "الموقع الحالي"
        }
    }

    private fun fetchCurrentWeather(location: Location, city: String): WeatherUiState {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${location.latitude}" +
                "&longitude=${location.longitude}" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
                "&wind_speed_unit=kmh" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&forecast_days=5" +
                "&timezone=auto"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }

        try {
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)
            val current = root.getJSONObject("current")
            val temperature = current.getDouble("temperature_2m").roundToInt()
            val apparentTemperature = current.optDouble("apparent_temperature", Double.NaN)
                .takeIf { it.isFinite() }?.roundToInt()
            val humidity = current.optInt("relative_humidity_2m", -1).takeIf { it in 0..100 }
            val windSpeed = current.optDouble("wind_speed_10m", Double.NaN)
                .takeIf { it.isFinite() && it >= 0.0 }?.roundToInt()
            val weatherCode = current.getInt("weather_code")
            val daily = root.optJSONObject("daily")
            val dates = daily?.optJSONArray("time")
            val codes = daily?.optJSONArray("weather_code")
            val highs = daily?.optJSONArray("temperature_2m_max")
            val lows = daily?.optJSONArray("temperature_2m_min")
            val forecast = if (dates != null && codes != null && highs != null && lows != null) {
                (0 until minOf(5, dates.length(), codes.length(), highs.length(), lows.length())).map { index ->
                    WeatherForecastDay(
                        date = dates.getString(index),
                        weatherCode = codes.getInt(index),
                        maxTemperatureC = highs.getDouble(index).roundToInt(),
                        minTemperatureC = lows.getDouble(index).roundToInt()
                    )
                }
            } else emptyList()
            val timeZone = root.optString("timezone")
                .takeIf { it.isNotBlank() && TimeZone.getTimeZone(it).id == it }

            return WeatherUiState(
                city = city,
                temperatureC = temperature,
                apparentTemperatureC = apparentTemperature,
                humidityPercent = humidity,
                windSpeedKmh = windSpeed,
                weatherCode = weatherCode,
                description = weatherDescription(weatherCode),
                timeZoneId = timeZone,
                latitude = location.latitude,
                longitude = location.longitude,
                forecast = forecast,
                isLoading = false
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun weatherDescription(code: Int): String = when (code) {
        0 -> "سماء صافية"
        1 -> "صحو غالباً"
        2 -> "غائم جزئياً"
        3 -> "غائم"
        45, 48 -> "ضباب"
        51, 53, 55 -> "رذاذ"
        56, 57 -> "رذاذ متجمد"
        61, 63, 65 -> "أمطار"
        66, 67 -> "مطر متجمد"
        71, 73, 75, 77 -> "ثلوج"
        80, 81, 82 -> "زخات مطر"
        85, 86 -> "زخات ثلج"
        95, 96, 99 -> "عواصف رعدية"
        else -> "حالة الطقس الحالية"
    }
}
