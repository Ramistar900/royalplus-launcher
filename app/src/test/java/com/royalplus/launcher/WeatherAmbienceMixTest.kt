package com.royalplus.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherAmbienceMixTest {
    @Test
    fun rainAndWindPlayTogetherWhenWindIsStrongEnough() {
        val mix = resolveWeatherAmbienceMix(
            weatherCode = 63,
            windSpeedKmh = 30,
            precipitationMm = 2.0,
            rainEnabled = true,
            windEnabled = true
        )

        assertTrue(mix.rain)
        assertTrue(mix.wind)
        assertTrue(mix.rainWithWind)
        assertFalse(mix.windForSnow)
    }

    @Test
    fun rainWithoutWindKeepsRainAudioOnly() {
        val mix = resolveWeatherAmbienceMix(
            weatherCode = 61,
            windSpeedKmh = 12,
            precipitationMm = 1.0,
            rainEnabled = true,
            windEnabled = true
        )

        assertTrue(mix.rain)
        assertFalse(mix.wind)
        assertFalse(mix.rainWithWind)
    }

    @Test
    fun snowWithWindPlaysWindButSnowAloneStaysSilent() {
        val windySnow = resolveWeatherAmbienceMix(
            weatherCode = 73,
            windSpeedKmh = 19,
            precipitationMm = 0.0,
            rainEnabled = true,
            windEnabled = true
        )
        val stillSnow = resolveWeatherAmbienceMix(
            weatherCode = 73,
            windSpeedKmh = 4,
            precipitationMm = 0.0,
            rainEnabled = true,
            windEnabled = true
        )

        assertFalse(windySnow.rain)
        assertTrue(windySnow.wind)
        assertTrue(windySnow.windForSnow)
        assertFalse(stillSnow.rain)
        assertFalse(stillSnow.wind)
    }

    @Test
    fun windToggleCanMuteWindWithoutMutingRain() {
        val mix = resolveWeatherAmbienceMix(
            weatherCode = 61,
            windSpeedKmh = 34,
            precipitationMm = 1.5,
            rainEnabled = true,
            windEnabled = false
        )

        assertTrue(mix.rain)
        assertFalse(mix.wind)
        assertFalse(mix.rainWithWind)
    }

    @Test
    fun hailAndFreezingPrecipitationUseRainAmbience() {
        listOf(56, 57, 66, 67, 96, 99).forEach { code ->
            val mix = resolveWeatherAmbienceMix(
                weatherCode = code,
                windSpeedKmh = 0,
                precipitationMm = 0.0,
                rainEnabled = true,
                windEnabled = true
            )
            assertTrue("Expected rain ambience for WMO $code", mix.rain)
        }
    }
}
