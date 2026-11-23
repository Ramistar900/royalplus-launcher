package com.royalplus.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherVideoMappingTest {
    private fun weather(code: Int) = WeatherUiState(
        weatherCode = code,
        description = "test weather"
    )

    @Test
    fun selectedCityTimezoneControlsDayAndNightVideo() {
        // 20:30 UTC is 23:30 in Istanbul but 12:30 in Los Angeles in January.
        val instant = java.time.Instant.parse("2026-01-15T20:30:00Z").toEpochMilli()
        val istanbulHour = weatherLocalHour(instant, "Europe/Istanbul")
        val losAngelesHour = weatherLocalHour(instant, "America/Los_Angeles")

        assertEquals(23, istanbulHour)
        assertEquals(12, losAngelesHour)
        assertEquals("weather_clear_night.mp4", weatherVideoAsset(weather(0), istanbulHour))
        assertEquals("weather_clear_sunny.mp4", weatherVideoAsset(weather(0), losAngelesHour))
        assertEquals("weather_partly_cloudy_night.mp4", weatherVideoAsset(weather(2), istanbulHour))
        assertEquals("weather_partly_cloudy_day.mp4", weatherVideoAsset(weather(2), losAngelesHour))
    }

    @Test
    fun hailUsesRainFootageForProceduralHailLayer() {
        assertEquals("weather_rain.mp4", weatherVideoAsset(weather(96), 12))
        assertEquals("weather_rain.mp4", weatherVideoAsset(weather(99), 12))
    }

    @Test
    fun ordinaryThunderstormKeepsThunderstormFootage() {
        assertEquals("weather_thunderstorm.mp4", weatherVideoAsset(weather(95), 12))
        assertEquals("weather_thunderstorm.mp4", weatherVideoAsset(weather(97), 12))
        assertEquals("weather_thunderstorm.mp4", weatherVideoAsset(weather(98), 12))
    }

    @Test
    fun freezingDrizzleAndFreezingRainKeepTheirRainBaseScenes() {
        assertEquals("weather_light_rain.mp4", weatherVideoAsset(weather(56), 12))
        assertEquals("weather_light_rain.mp4", weatherVideoAsset(weather(57), 12))
        assertEquals("weather_rain.mp4", weatherVideoAsset(weather(66), 12))
        assertEquals("weather_rain.mp4", weatherVideoAsset(weather(67), 12))
    }

    @Test
    fun ordinaryDrizzleAndRainKeepTheirExistingScenes() {
        assertEquals("weather_light_rain.mp4", weatherVideoAsset(weather(51), 12))
        assertEquals("weather_light_rain.mp4", weatherVideoAsset(weather(55), 12))
        assertEquals("weather_rain.mp4", weatherVideoAsset(weather(61), 12))
        assertEquals("weather_rain.mp4", weatherVideoAsset(weather(82), 12))
    }

    @Test
    fun snowShowersKeepTheirExistingLightSnowScene() {
        assertEquals("weather_light_snow.mp4", weatherVideoAsset(weather(85), 12))
        assertEquals("weather_light_snow.mp4", weatherVideoAsset(weather(86), 23))
    }
}
