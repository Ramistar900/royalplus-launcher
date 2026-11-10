#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/royalplus/launcher"

weather = (JAVA / "RoyalWeatherAtmosphereOverlay.kt").read_text(encoding="utf-8")
rain_branch = weather.split("in 51..57 -> {", 1)[1].split("in 71..77", 1)[0]
storm_branch = weather.split("in 95..99 -> {", 1)[1].split("\n            }", 1)[0]
rain_renderer = weather.split("private fun DrawScope.drawRoyalRain(", 1)[1].split("\nprivate fun DrawScope.drawRoyalSnow(", 1)[0]
assert "drawRoyalClouds" not in rain_branch and "drawRoyalWind" not in rain_branch
assert "drawRoyalClouds" not in storm_branch and "drawRoyalWind" not in storm_branch
assert "drawRoyalLightning" not in storm_branch
assert "isRainScene" in weather and "!isRainScene" in weather
assert "cubicTo(" in rain_renderer and "drawPath(" in rain_renderer
assert "drawLine(" not in rain_renderer and "drawRect(" not in rain_renderer

card = (JAVA / "WeatherGlassCard.kt").read_text(encoding="utf-8")
details = (JAVA / "WeatherDetailsScreen.kt").read_text(encoding="utf-8")
assert "RoyalWeatherAmbienceEffect(weather)" not in card
assert "RoyalOwlAmbienceEffect(weather)" not in card
assert "RoyalWeatherAmbienceEffect(weather)" in details
assert "RoyalOwlAmbienceEffect(weather)" in details

ambience = (JAVA / "RoyalWeatherAmbienceController.kt").read_text(encoding="utf-8")
assert "foregroundOwner" in ambience and "latestWeather = null" in ambience
assert "weatherReceivedAtElapsedMs = null" in ambience
home = (JAVA / "RoyalHomeScreen.kt").read_text(encoding="utf-8")
assert "RoyalWeatherAmbienceController.onBackground()" in home
assert "weatherDetailsAreTheOnlyForegroundWeatherScreen" in home

service = (JAVA / "FloatingTranslationOverlayService.kt").read_text(encoding="utf-8")
assert "AudioPlaybackCaptureConfiguration.Builder" in service
assert "getMediaProjection(" in service and "VOICE_RECOGNITION" in service
assert "hasMeaningfulAudio(" in service and "consecutiveSilentSegments" in service
assert "START_NOT_STICKY" in service and "EXTRA_PROJECTION_DATA" in service
activity = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")
assert "createScreenCaptureIntent()" in activity
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
assert "FOREGROUND_SERVICE_MEDIA_PROJECTION" in manifest
assert 'foregroundServiceType="microphone|mediaProjection"' in manifest

print("Weather isolation and translation capture regression checks passed.")
