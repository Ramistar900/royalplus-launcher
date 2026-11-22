package com.royalplus.launcher

import android.Manifest
import android.annotation.SuppressLint
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class WeatherCitySuggestion(
    val id: Long,
    val name: String,
    val admin1: String?,
    val admin2: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double
) {
    val displayName: String
        get() = listOfNotNull(name, admin1, admin2, country)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("، ")
}

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
    val precipitationMm: Double? = null,
    val weatherCode: Int? = null,
    val description: String = "جارٍ تحميل الطقس",
    val timeZoneId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val forecast: List<WeatherForecastDay> = emptyList(),
    val attributionText: String? = null,
    val attributionUrl: String? = null,
    val licenseUrl: String? = null,
    val isLoading: Boolean = true,
    val needsPermission: Boolean = false,
    val isManualLocation: Boolean = false
)

internal object RoyalWeatherRepository {
    private const val PREFS = "royalplus_launcher_preferences"
    private const val LEGACY_CACHE_KEY = "weather_last_successful_payload"
    private const val CACHE_KEY_PREFIX = "weather_city_cache_"
    private const val LOCATION_MAX_AGE_MS = 8 * 24 * 60 * 60 * 1000L
    private const val WEATHER_CACHE_MAX_AGE_MS = 8 * 24 * 60 * 60 * 1000L
    private const val WEATHER_REFRESH_INTERVAL_MS = 3 * 60 * 60 * 1000L
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000
    private val executor = Executors.newSingleThreadExecutor()

    fun fetch(context: Context, onResult: (WeatherUiState) -> Unit) {
        val appContext = context.applicationContext
        executor.execute {
            cachedWeatherForLastLocation(appContext)?.let { onResult(it.copy(isLoading = false)) }
            val result = try {
                // Android TV devices may have no GPS/network provider or location permission.
                // In that case, use the approximate location of the device's internet connection.
                val location = try {
                    findLocation(appContext)
                } catch (_: Exception) {
                    null
                }
                if (location != null) {
                    readWeatherCache(appContext, location)?.first?.let { onResult(it.copy(isLoading = false)) }
                    fetchCurrentWeather(appContext, location, findCity(appContext, location))
                } else {
                    val ipLocation = try {
                        findLocationByIp()
                    } catch (_: Exception) {
                        null
                    }
                    if (ipLocation != null) {
                        fetchCurrentWeather(appContext, ipLocation.first, ipLocation.second)
                    } else {
                        val fallbackCity = fallbackCityForDeviceTimeZone()
                        if (fallbackCity != null) {
                            fetchCityWeather(appContext, fallbackCity).copy(isManualLocation = true)
                        } else {
                            cachedWeatherForLastLocation(appContext) ?: WeatherUiState(
                                city = "اختر مدينتك",
                                description = "تعذّر تحديد موقع الجهاز عبر الشبكة؛ اختر مدينتك لعرض الطقس ومواقيت الصلاة",
                                isLoading = false
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                cachedWeatherForLastLocation(appContext) ?: WeatherUiState(
                    city = "تعذّر تحميل الطقس",
                    description = "تعذّر الاتصال بمصادر الطقس؛ تحقق من الإنترنت أو اختر مدينة أخرى",
                    isLoading = false
                )
            }
            onResult(result)
        }
    }

    fun searchCities(cityQuery: String, onResult: (List<WeatherCitySuggestion>) -> Unit) {
        val query = cityQuery.trim()
        if (query.length < 2) {
            onResult(emptyList())
            return
        }
        executor.execute {
            val matches = try {
                val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
                val language = Locale.getDefault().language.lowercase(Locale.ROOT).ifBlank { "en" }
                val url = URL(
                    "https://geocoding-api.open-meteo.com/v1/search" +
                        "?name=$encodedQuery&count=100&language=$language&format=json"
                )
                val results = requestJson(url).optJSONArray("results") ?: JSONArray()
                (0 until results.length()).mapNotNull { index ->
                    val place = results.optJSONObject(index) ?: return@mapNotNull null
                    val latitude = place.optDouble("latitude", Double.NaN)
                    val longitude = place.optDouble("longitude", Double.NaN)
                    val name = place.optString("name").trim()
                    if (name.isBlank() || !latitude.isFinite() || !longitude.isFinite() ||
                        latitude !in -90.0..90.0 || longitude !in -180.0..180.0
                    ) return@mapNotNull null
                    val id = place.optLong("id", -1L)
                    if (id <= 0L) return@mapNotNull null
                    WeatherCitySuggestion(
                        id = id,
                        name = name,
                        admin1 = place.optString("admin1").takeIf { it.isNotBlank() },
                        admin2 = place.optString("admin2").takeIf { it.isNotBlank() },
                        country = place.optString("country").takeIf { it.isNotBlank() },
                        latitude = latitude,
                        longitude = longitude
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
            onResult(matches)
        }
    }

    fun fetchForCity(context: Context, cityQuery: String, onResult: (WeatherUiState) -> Unit) {
        val appContext = context.applicationContext
        val requestedCity = cityQuery.trim()
        executor.execute {
            val savedCity = findSavedWeatherCity(appContext, requestedCity)
            val savedLocation = savedCity?.let {
                Location("royalplus-saved-city").apply {
                    latitude = it.latitude
                    longitude = it.longitude
                }
            }
            val result = try {
                if (savedCity != null && savedLocation != null) {
                    fetchCurrentWeather(appContext, savedLocation, savedCity.displayName)
                        .copy(isManualLocation = true)
                } else {
                    fetchCityWeather(appContext, requestedCity).copy(isManualLocation = true)
                }
            } catch (_: Exception) {
                val cached = savedLocation?.let { readWeatherCache(appContext, it)?.first }
                cached?.copy(
                    city = savedCity?.displayName ?: requestedCity,
                    isLoading = false,
                    isManualLocation = true
                ) ?: WeatherUiState(
                    city = requestedCity.ifBlank { "مدينة غير محددة" },
                    description = "تعذّر العثور على المدينة أو تحميل الطقس؛ تحقق من الاسم والإنترنت",
                    isLoading = false,
                    isManualLocation = true
                )
            }
            onResult(result)
        }
    }


    fun fetchForLocation(
        context: Context,
        cityName: String,
        latitude: Double,
        longitude: Double,
        onResult: (WeatherUiState) -> Unit
    ) {
        val appContext = context.applicationContext
        val normalizedCity = cityName.trim()
        executor.execute {
            val location = Location("royalplus-manual").apply {
                this.latitude = latitude
                this.longitude = longitude
            }
            val cached = if (
                normalizedCity.isNotBlank() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0
            ) readWeatherCache(appContext, location)?.first else null

            // Show this city's own saved forecast immediately while a network refresh runs.
            cached?.let {
                onResult(it.copy(city = normalizedCity, isLoading = false, isManualLocation = true))
            }

            val result = try {
                require(normalizedCity.isNotBlank())
                require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
                fetchCurrentWeather(appContext, location, normalizedCity).copy(isManualLocation = true)
            } catch (_: Exception) {
                cached?.copy(
                    city = normalizedCity,
                    isLoading = false,
                    isManualLocation = true
                ) ?: WeatherUiState(
                    city = normalizedCity.ifBlank { "مدينة غير محددة" },
                    description = "تعذّر تحميل طقس المدينة المختارة؛ لا توجد بيانات محفوظة صالحة",
                    latitude = latitude,
                    longitude = longitude,
                    isLoading = false,
                    isManualLocation = true
                )
            }
            onResult(result)
        }
    }

    /**
     * Refresh each saved city's rolling seven-day forecast independently.
     * Existing city caches are only replaced after a successful forecast response.
     */
    fun refreshStoredWeather(context: Context): Boolean {
        val appContext = context.applicationContext
        return try {
            val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val savedArray = try {
                JSONArray(preferences.getString("weather_saved_cities", "[]") ?: "[]")
            } catch (_: Exception) {
                JSONArray()
            }
            val cities = mutableListOf<WeatherCitySuggestion>()
            for (index in 0 until savedArray.length()) {
                val item = savedArray.optJSONObject(index) ?: continue
                val latitude = item.optDouble("latitude", Double.NaN)
                val longitude = item.optDouble("longitude", Double.NaN)
                val name = item.optString("name").trim()
                if (name.isBlank() || !latitude.isFinite() || !longitude.isFinite() ||
                    latitude !in -90.0..90.0 || longitude !in -180.0..180.0
                ) continue
                cities += WeatherCitySuggestion(
                    id = item.optLong("id"),
                    name = name,
                    admin1 = item.optString("admin1").takeIf { it.isNotBlank() },
                    admin2 = item.optString("admin2").takeIf { it.isNotBlank() },
                    country = item.optString("country").takeIf { it.isNotBlank() },
                    latitude = latitude,
                    longitude = longitude
                )
            }

            val manualCity = preferences.getString("weather_manual_city", null)?.trim()
            val manualLatitude = preferences.getString("weather_manual_latitude", null)?.toDoubleOrNull()
            val manualLongitude = preferences.getString("weather_manual_longitude", null)?.toDoubleOrNull()
            if (!manualCity.isNullOrBlank() && manualLatitude != null && manualLongitude != null &&
                manualLatitude in -90.0..90.0 && manualLongitude in -180.0..180.0 &&
                cities.none {
                    abs(it.latitude - manualLatitude) < 0.001 &&
                        abs(it.longitude - manualLongitude) < 0.001
                }
            ) {
                cities += WeatherCitySuggestion(
                    id = 0L,
                    name = manualCity,
                    admin1 = null,
                    admin2 = null,
                    country = null,
                    latitude = manualLatitude,
                    longitude = manualLongitude
                )
            }

            if (cities.isNotEmpty()) {
                var hasUsableForecast = false
                cities.take(12).forEach { city ->
                    val location = Location("royalplus-saved-city-refresh").apply {
                        latitude = city.latitude
                        longitude = city.longitude
                    }
                    try {
                        val updated = fetchCurrentWeather(
                            appContext, location, city.displayName, saveAsLastLocation = false
                        )
                        if (updated.forecast.size >= 7) hasUsableForecast = true
                    } catch (_: Exception) {
                        // Keep this city's previous cache and continue refreshing the others.
                        val cachedForecastSize = readWeatherCache(appContext, location)?.first?.forecast?.size ?: 0
                        if (cachedForecastSize >= 7) hasUsableForecast = true
                    }
                }
                return hasUsableForecast
            }

            if (!manualCity.isNullOrBlank()) {
                val updated = if (
                    manualLatitude != null && manualLongitude != null &&
                    manualLatitude in -90.0..90.0 && manualLongitude in -180.0..180.0
                ) {
                    val location = Location("royalplus-saved-manual").apply {
                        latitude = manualLatitude
                        longitude = manualLongitude
                    }
                    fetchCurrentWeather(appContext, location, manualCity).copy(isManualLocation = true)
                } else {
                    fetchCityWeather(appContext, manualCity).copy(isManualLocation = true)
                }
                return updated.forecast.size >= 7
            }

            val location = readLastLocation(appContext) ?: findLocation(appContext) ?: return false
            val updated = fetchCurrentWeather(appContext, location, findCity(appContext, location))
            updated.forecast.size >= 7
        } catch (_: Exception) {
            false
        }
    }

    private fun findSavedWeatherCity(context: Context, query: String): WeatherCitySuggestion? {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isBlank()) return null
        return try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("weather_saved_cities", null) ?: return null
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val latitude = item.optDouble("latitude", Double.NaN)
                val longitude = item.optDouble("longitude", Double.NaN)
                val name = item.optString("name").trim()
                if (name.isBlank() || !latitude.isFinite() || !longitude.isFinite() ||
                    latitude !in -90.0..90.0 || longitude !in -180.0..180.0
                ) return@mapNotNull null
                WeatherCitySuggestion(
                    id = item.optLong("id"),
                    name = name,
                    admin1 = item.optString("admin1").takeIf { it.isNotBlank() },
                    admin2 = item.optString("admin2").takeIf { it.isNotBlank() },
                    country = item.optString("country").takeIf { it.isNotBlank() },
                    latitude = latitude,
                    longitude = longitude
                )
            }.firstOrNull { city ->
                val shortName = city.name.substringBefore("،").substringBefore(",").trim()
                city.name.equals(query.trim(), ignoreCase = true) ||
                    city.displayName.equals(query.trim(), ignoreCase = true) ||
                    shortName.equals(query.trim(), ignoreCase = true)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findLocationByIp(): Pair<Location, String>? {
        val root = requestJson(URL("https://ipapi.co/json/"))
        if (root.optString("error").equals("true", ignoreCase = true)) return null
        val latitude = root.optDouble("latitude", Double.NaN)
        val longitude = root.optDouble("longitude", Double.NaN)
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) return null
        val city = root.optString("city").takeIf { it.isNotBlank() } ?: return null
        val country = root.optString("country_name").takeIf { it.isNotBlank() }
        val displayCity = if (country != null) "$city، $country" else city
        val location = Location("royalplus-ip").apply {
            this.latitude = latitude
            this.longitude = longitude
            time = System.currentTimeMillis()
        }
        return location to displayCity
    }

    private fun fallbackCityForDeviceTimeZone(): String? =
        when (TimeZone.getDefault().id) {
            "Europe/Istanbul", "Turkey" -> "Istanbul"
            else -> null
        }

    private fun fetchCityWeather(context: Context, requestedCity: String): WeatherUiState {
        require(requestedCity.isNotBlank()) { "City name is blank" }
        val encodedCity = URLEncoder.encode(requestedCity, Charsets.UTF_8.name())
        val geocodingUrl = URL(
            "https://geocoding-api.open-meteo.com/v1/search" +
                "?name=$encodedCity&count=1&language=${Locale.getDefault().language}&format=json"
        )
        val locationAndName = requestJson(geocodingUrl).let { root ->
            val first = root.optJSONArray("results")?.optJSONObject(0)
                ?: throw IllegalArgumentException("City was not found")
            val location = Location("royalplus-manual").apply {
                latitude = first.getDouble("latitude")
                longitude = first.getDouble("longitude")
            }
            val resolvedCity = first.optString("name").ifBlank { requestedCity }
            val country = first.optString("country")
            val displayCity = if (country.isNotBlank()) "$resolvedCity، $country" else resolvedCity
            location to displayCity
        }
        return fetchCurrentWeather(context, locationAndName.first, locationAndName.second)
    }

    private fun findLocation(context: Context): Location? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Location permission is not granted")
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val enabledProviders = try {
            manager.getProviders(true).filter {
                it == LocationManager.NETWORK_PROVIDER ||
                    it == LocationManager.GPS_PROVIDER ||
                    it == LocationManager.PASSIVE_PROVIDER
            }.distinct()
        } catch (_: Exception) {
            emptyList()
        }

        val cached = (enabledProviders + LocationManager.NETWORK_PROVIDER +
            LocationManager.GPS_PROVIDER + LocationManager.PASSIVE_PROVIDER).distinct()
            .mapNotNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (_: Exception) {
                    null
                }
            }
            .filter { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
            .maxByOrNull { it.time }

        val now = System.currentTimeMillis()
        if (cached != null && now - cached.time in 0..(30 * 60 * 1000L)) {
            saveLastLocation(context, cached)
            return cached
        }

        val activeProviders = enabledProviders.filter {
            it == LocationManager.NETWORK_PROVIDER || it == LocationManager.GPS_PROVIDER
        }
        val fresh = if (activeProviders.isEmpty()) {
            null
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getCurrentLocation(context, manager, activeProviders)
        } else {
            getLocationWithUpdates(context, manager, activeProviders)
        }

        val selected = fresh ?: cached?.takeIf { now - it.time in 0..LOCATION_MAX_AGE_MS }
            ?: readLastLocation(context)
        if (selected != null) saveLastLocation(context, selected)
        return selected
    }

    private fun saveLastLocation(context: Context, location: Location) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("weather_last_latitude", location.latitude.toString())
            .putString("weather_last_longitude", location.longitude.toString())
            .putLong("weather_last_location_time", location.time)
            .apply()
    }

    private fun readLastLocation(context: Context): Location? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val time = prefs.getLong("weather_last_location_time", 0L)
        if (time <= 0L || System.currentTimeMillis() - time !in 0..LOCATION_MAX_AGE_MS) return null
        val latitude = prefs.getString("weather_last_latitude", null)?.toDoubleOrNull() ?: return null
        val longitude = prefs.getString("weather_last_longitude", null)?.toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return Location("royalplus-cached").apply {
            this.latitude = latitude
            this.longitude = longitude
            this.time = time
        }
    }


    // Re-check at the point of use: permission may be revoked after findLocation() starts.
    @SuppressLint("MissingPermission")
    private fun getCurrentLocation(context: Context, manager: LocationManager, providers: List<String>): Location? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        // LocationManager.getCurrentLocation() was added in API 30.
        // Android 8–10 use the legacy listener path instead.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return getLocationWithUpdates(context, manager, providers)
        }
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
                        val previous = best.get()
                        if (previous == null || location.accuracy < previous.accuracy) best.set(location)
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

    // Re-check at the point of use: this helper is also called asynchronously from a worker.
    @SuppressLint("MissingPermission", "DEPRECATION")
    private fun getLocationWithUpdates(context: Context, manager: LocationManager, providers: List<String>): Location? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        val latch = CountDownLatch(1)
        val result = AtomicReference<Location?>(null)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val previous = result.get()
                if (previous == null || location.accuracy < previous.accuracy) result.set(location)
                latch.countDown()
            }

            override fun onProviderDisabled(provider: String) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        providers.forEach { provider ->
            try {
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            } catch (_: Exception) {
                // Continue with the other available provider.
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        try {
            manager.removeUpdates(listener)
        } catch (_: Exception) {
            // A provider may have been disabled while waiting.
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
            val city = address?.locality ?: address?.subAdminArea ?: address?.adminArea
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

    private fun fetchCurrentWeather(
        context: Context,
        location: Location,
        city: String,
        saveAsLastLocation: Boolean = true
    ): WeatherUiState {
        if (saveAsLastLocation) saveLastLocation(context, location)
        val cached = readWeatherCache(context, location)
        if (cached != null && System.currentTimeMillis() - cached.second < WEATHER_REFRESH_INTERVAL_MS) {
            return cached.first.copy(city = city, isLoading = false)
        }
        try {
            val weather = fetchOpenMeteo(location, city).copy(
                attributionText = "Open-Meteo",
                attributionUrl = "https://open-meteo.com/",
                licenseUrl = "https://creativecommons.org/licenses/by/4.0/"
            )
            require(weather.forecast.size >= 7) { "Open-Meteo returned fewer than seven forecast days" }
            saveWeatherCache(context, weather)
            return weather
        } catch (_: Exception) {
            // Open-Meteo is the primary source. Continue to the independent backup.
        }
 
        return try {
            val backup = fetchMetNorway(location, city).copy(
                attributionText = "MET Norway",
                attributionUrl = "https://api.met.no/",
                licenseUrl = "https://creativecommons.org/licenses/by/4.0/"
            )
            require(backup.forecast.size >= 7) { "MET Norway returned fewer than seven forecast days" }
            saveWeatherCache(context, backup)
            backup
        } catch (_: Exception) {
            cached?.first?.copy(city = city) ?: throw IllegalStateException("Both weather sources failed")
        }
    }

    private fun fetchOpenMeteo(location: Location, city: String): WeatherUiState {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${location.latitude}&longitude=${location.longitude}" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,precipitation" +
                "&wind_speed_unit=kmh" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&forecast_days=7&timezone=auto"
        )
        val root = requestJson(url)
        val current = root.getJSONObject("current")
        val temperature = current.getDouble("temperature_2m").roundToInt()
        val apparent = current.optDouble("apparent_temperature", Double.NaN)
            .takeIf { it.isFinite() }?.roundToInt()
        val humidity = current.optInt("relative_humidity_2m", -1).takeIf { it in 0..100 }
        val wind = current.optDouble("wind_speed_10m", Double.NaN)
            .takeIf { it.isFinite() && it >= 0.0 }?.roundToInt()
        val precipitation = current.optDouble("precipitation", Double.NaN)
            .takeIf { it.isFinite() && it >= 0.0 }
        val code = current.getInt("weather_code")
        val daily = root.optJSONObject("daily")
        val dates = daily?.optJSONArray("time")
        val codes = daily?.optJSONArray("weather_code")
        val highs = daily?.optJSONArray("temperature_2m_max")
        val lows = daily?.optJSONArray("temperature_2m_min")
        val forecast = if (dates != null && codes != null && highs != null && lows != null) {
            (0 until minOf(7, dates.length(), codes.length(), highs.length(), lows.length())).map { index ->
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
            apparentTemperatureC = apparent,
            humidityPercent = humidity,
            windSpeedKmh = wind,
            precipitationMm = precipitation,
            weatherCode = code,
            description = weatherDescription(code),
            timeZoneId = timeZone,
            latitude = location.latitude,
            longitude = location.longitude,
            forecast = forecast,
            isLoading = false
        )
    }

    private fun fetchMetNorway(location: Location, city: String): WeatherUiState {
        // MET Norway requires an identifiable, non-generic User-Agent.
        val url = URL(
            "https://api.met.no/weatherapi/locationforecast/2.0/compact" +
                "?lat=${"%.4f".format(Locale.US, location.latitude)}" +
                "&lon=${"%.4f".format(Locale.US, location.longitude)}"
        )
        val root = requestJson(url, metNorway = true)
        val series = root.getJSONObject("properties").getJSONArray("timeseries")
        if (series.length() == 0) throw IllegalStateException("MET Norway returned an empty forecast")

        val first = series.getJSONObject(0)
        val firstData = first.getJSONObject("data")
        val instant = firstData.getJSONObject("instant").getJSONObject("details")
        val temperature = instant.optDouble("air_temperature", Double.NaN)
        if (!temperature.isFinite()) throw IllegalStateException("MET Norway temperature is missing")

        val symbol = firstData.optJSONObject("next_1_hours")?.optJSONObject("summary")?.optString("symbol_code")
            ?.takeIf { !it.isNullOrBlank() }
            ?: firstData.optJSONObject("next_6_hours")?.optJSONObject("summary")?.optString("symbol_code")
                ?.takeIf { !it.isNullOrBlank() }
        val code = metSymbolToWmo(symbol.orEmpty())
        val precipitation = firstData.optJSONObject("next_1_hours")?.optJSONObject("details")
            ?.optDouble("precipitation_amount", Double.NaN)
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?: firstData.optJSONObject("next_6_hours")?.optJSONObject("details")
                ?.optDouble("precipitation_amount", Double.NaN)
                ?.takeIf { it.isFinite() && it >= 0.0 }
        val humidity = instant.optDouble("relative_humidity", Double.NaN)
            .takeIf { it.isFinite() && it in 0.0..100.0 }?.roundToInt()
        val wind = instant.optDouble("wind_speed", Double.NaN)
            .takeIf { it.isFinite() && it >= 0.0 }?.roundToInt()
        val apparent = instant.optDouble("apparent_air_temperature", Double.NaN)
            .takeIf { it.isFinite() }?.roundToInt()

        val dailyBuckets = linkedMapOf<String, MutableList<Pair<Int, Int>>>()
        for (index in 0 until series.length()) {
            val item = series.optJSONObject(index) ?: continue
            val date = item.optString("time").substringBefore("T")
            if (date.isBlank()) continue
            val details = item.optJSONObject("data")?.optJSONObject("instant")?.optJSONObject("details")
                ?: continue
            val temp = details.optDouble("air_temperature", Double.NaN)
            if (!temp.isFinite()) continue
            val itemData = item.optJSONObject("data") ?: continue
            val itemSymbol = itemData.optJSONObject("next_6_hours")?.optJSONObject("summary")
                ?.optString("symbol_code")
                ?: itemData.optJSONObject("next_1_hours")?.optJSONObject("summary")
                    ?.optString("symbol_code").orEmpty()
            val itemCode = metSymbolToWmo(itemSymbol)
            dailyBuckets.getOrPut(date) { mutableListOf() }.add(temp.roundToInt() to itemCode)
        }
        val forecast = dailyBuckets.entries.take(7).map { (date, values) ->
            WeatherForecastDay(
                date = date,
                weatherCode = values.getOrNull(values.size / 2)?.second ?: code,
                maxTemperatureC = values.maxOf { it.first },
                minTemperatureC = values.minOf { it.first }
            )
        }

        return WeatherUiState(
            city = city,
            temperatureC = temperature.roundToInt(),
            apparentTemperatureC = apparent,
            humidityPercent = humidity,
            windSpeedKmh = wind,
            precipitationMm = precipitation,
            weatherCode = code,
            description = weatherDescription(code),
            latitude = location.latitude,
            longitude = location.longitude,
            forecast = forecast,
            isLoading = false
        )
    }

    private fun metSymbolToWmo(symbol: String): Int {
        val value = symbol.lowercase(Locale.ROOT)
        return when {
            "thunder" in value -> 95
            "snow" in value -> if ("showers" in value) 85 else 71
            "sleet" in value -> 66
            "rain" in value && "showers" in value -> 80
            "rain" in value || "drizzle" in value -> 61
            "fog" in value -> 45
            "cloudy" in value -> 3
            "partlycloudy" in value -> 2
            "fair" in value -> 1
            "clearsky" in value -> 0
            else -> 3
        }
    }

    private fun requestJson(url: URL, metNorway: Boolean = false): JSONObject {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            if (metNorway) {
                setRequestProperty(
                    "User-Agent",
                    "ROYALPlusLauncher/1.0 (Android TV; https://github.com/Ramistar900/royalplus-launcher)"
                )
            }
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("Weather service HTTP $status")
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun weatherCacheKey(location: Location): String {
        val coordinates = String.format(
            Locale.US,
            "%.3f_%.3f",
            location.latitude,
            location.longitude
        )
        return CACHE_KEY_PREFIX + coordinates.replace("-", "m").replace(".", "_")
    }

    private fun saveWeatherCache(context: Context, weather: WeatherUiState) {
        val latitude = weather.latitude ?: return
        val longitude = weather.longitude ?: return
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return
        val forecast = JSONArray().apply {
            weather.forecast.forEach { day ->
                put(JSONObject().apply {
                    put("date", day.date)
                    put("code", day.weatherCode)
                    put("high", day.maxTemperatureC)
                    put("low", day.minTemperatureC)
                })
            }
        }
        val json = JSONObject().apply {
            put("savedAt", System.currentTimeMillis())
            put("latitude", latitude)
            put("longitude", longitude)
            put("city", weather.city)
            put("temperature", weather.temperatureC)
            put("apparent", weather.apparentTemperatureC)
            put("humidity", weather.humidityPercent)
            put("wind", weather.windSpeedKmh)
            put("precipitationMm", weather.precipitationMm)
            put("code", weather.weatherCode)
            put("description", weather.description)
            put("timezone", weather.timeZoneId)
            put("attributionText", weather.attributionText)
            put("attributionUrl", weather.attributionUrl)
            put("licenseUrl", weather.licenseUrl)
            put("forecast", forecast)
        }
        val location = Location("royalplus-weather-cache").apply {
            this.latitude = latitude
            this.longitude = longitude
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(weatherCacheKey(location), json.toString())
            .apply()
    }

    private fun readWeatherCache(context: Context, location: Location): Pair<WeatherUiState, Long>? {
        return try {
            val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val cityCacheKey = weatherCacheKey(location)
            var raw = preferences.getString(cityCacheKey, null)
            var migratedLegacyCache = false
            if (raw == null) {
                // One-time compatibility path for installations with the former single-city cache.
                raw = preferences.getString(LEGACY_CACHE_KEY, null)
                migratedLegacyCache = raw != null
            }
            raw ?: return null
            val json = JSONObject(raw)
            val savedAt = json.getLong("savedAt")
            val age = System.currentTimeMillis() - savedAt
            if (age !in 0..WEATHER_CACHE_MAX_AGE_MS) return null
            val latitude = json.getDouble("latitude")
            val longitude = json.getDouble("longitude")
            // New city caches must match the rounded-coordinate key very closely.
            // The legacy single-city cache gets a slightly wider one-time migration window.
            val coordinateTolerance = if (migratedLegacyCache) 0.005 else 0.0011
            if (abs(latitude - location.latitude) > coordinateTolerance ||
                abs(longitude - location.longitude) > coordinateTolerance
            ) return null

            val forecastJson = json.optJSONArray("forecast") ?: JSONArray()
            val forecast = (0 until forecastJson.length()).mapNotNull { index ->
                val day = forecastJson.optJSONObject(index) ?: return@mapNotNull null
                val date = day.optString("date")
                if (date.isBlank()) return@mapNotNull null
                WeatherForecastDay(
                    date = date,
                    weatherCode = day.optInt("code", 3),
                    maxTemperatureC = day.optInt("high"),
                    minTemperatureC = day.optInt("low")
                )
            }
            if (forecast.isEmpty()) return null
            val weather = WeatherUiState(
                city = json.optString("city", "الموقع الحالي"),
                temperatureC = json.optIntOrNull("temperature"),
                apparentTemperatureC = json.optIntOrNull("apparent"),
                humidityPercent = json.optIntOrNull("humidity"),
                windSpeedKmh = json.optIntOrNull("wind"),
                precipitationMm = json.optDouble("precipitationMm", Double.NaN)
                    .takeIf { it.isFinite() && it >= 0.0 },
                weatherCode = json.optIntOrNull("code"),
                description = json.optString("description", "حالة الطقس الحالية"),
                timeZoneId = json.optString("timezone").takeIf { it.isNotBlank() && it != "null" },
                attributionText = json.optString("attributionText").takeIf { it.isNotBlank() && it != "null" },
                attributionUrl = json.optString("attributionUrl").takeIf { it.startsWith("https://") },
                licenseUrl = json.optString("licenseUrl").takeIf { it.startsWith("https://") },
                latitude = latitude,
                longitude = longitude,
                forecast = forecast,
                isLoading = false
            )
            if (migratedLegacyCache) {
                preferences.edit().putString(cityCacheKey, raw).apply()
            }
            weather to savedAt
        } catch (_: Exception) {
            null
        }
    }

    private fun cachedWeatherForLastLocation(context: Context): WeatherUiState? {
        val location = readLastLocation(context) ?: return null
        return readWeatherCache(context, location)?.first
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        val value = opt(key)
        return when (value) {
            null, JSONObject.NULL -> null
            is Number -> value.toInt()
            else -> value.toString().toIntOrNull()
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
