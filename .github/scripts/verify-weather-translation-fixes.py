#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/royalplus/launcher"

weather = (JAVA / "RoyalWeatherAtmosphereOverlay.kt").read_text(encoding="utf-8")
rain_guard = weather.split("if (isRainScene) {", 1)[1].split("return@Canvas", 1)[0]
rain_renderer = weather.split("private fun DrawScope.drawRoyalRain(", 1)[1].split("\nprivate fun DrawScope.drawRoyalSnow(", 1)[0]
assert "drawRoyalRain(" in rain_guard
assert "return@Canvas" in weather.split("if (isRainScene) {", 1)[1].split("\n        }", 1)[0]
assert "drawRoyalClouds" not in rain_guard and "drawRoyalWind" not in rain_guard
assert "descriptionIndicatesRain" in weather
assert "cubicTo(" in rain_renderer and "drawPath(" in rain_renderer
assert "drawLine(" not in rain_renderer and "drawRect(" not in rain_renderer

card = (JAVA / "WeatherGlassCard.kt").read_text(encoding="utf-8")
smart = (JAVA / "RoyalSmartScreensaver.kt").read_text(encoding="utf-8")
details = (JAVA / "WeatherDetailsScreen.kt").read_text(encoding="utf-8")
assert "RoyalWeatherAtmosphereOverlay(" in card
assert "RoyalWeatherAtmosphereOverlay(" in smart and "RoyalWeatherAtmosphereOverlay(" in details
assert "drawClouds = false" in card, "Home weather card must hide only the drawn decorative clouds"
assert "drawClouds = false" not in smart and "drawClouds = false" not in details
assert "drawClouds: Boolean = true" in weather
cloud_calls = [line.strip() for line in weather.splitlines() if "drawRoyalClouds(progress" in line]
assert len(cloud_calls) == 4 and all(line.startswith("if (drawClouds)") for line in cloud_calls)
assert "RoyalWeatherAmbienceEffect(weather)" not in card
assert "RoyalOwlAmbienceEffect(weather)" not in card
assert "RoyalWeatherAmbienceEffect(weather)" not in smart
assert "RoyalOwlAmbienceEffect(weather)" not in smart
assert "RoyalWeatherAmbienceEffect(weather)" in details
assert "RoyalOwlAmbienceEffect(weather)" in details

ambience = (JAVA / "RoyalWeatherAmbienceController.kt").read_text(encoding="utf-8")
assert "foregroundOwner" in ambience and "latestWeather = null" in ambience
assert "weatherReceivedAtElapsedMs = null" in ambience
# Audio effects must be owned by the details screen, never by the home card.
service = (JAVA / "FloatingTranslationOverlayService.kt").read_text(encoding="utf-8")
assert "reportRecognizerInitializationFailure" in service
assert "OutOfMemoryError" in service and "LinkageError" in service
assert "Log.e(TAG, \"Whisper recognizer initialization failed\", error)" in service
assert "AudioPlaybackCaptureConfiguration.Builder" in service
assert "getMediaProjection(" in service and "VOICE_RECOGNITION" in service
assert "return AudioRecord(" in service
assert "hasMeaningfulAudio(" in service and "consecutiveSilentSegments" in service
assert "checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)" in service
assert "CONTROL_IDLE_HIDE_MILLIS = 10_000L" in service
assert "scheduleControlHide()" in service and "hideControlAndShowEdge()" in service
assert "START_NOT_STICKY" in service and "EXTRA_PROJECTION_DATA" in service
activity = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")
assert "createScreenCaptureIntent()" in activity
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
assert "FOREGROUND_SERVICE_MEDIA_PROJECTION" in manifest
assert 'foregroundServiceType="microphone|mediaProjection"' in manifest

print("Weather isolation and translation capture regression checks passed.")

# Home and smart screensaver must hard-stop weather ambience; details is the sole owner.
home = (JAVA / "RoyalHomeScreen.kt").read_text(encoding="utf-8")
assert "LaunchedEffect(showWeatherDetails, showSmartScreensaver)" in home
assert "RoyalWeatherAmbienceController.onBackground()" in home
# Normalize Whisper language labels before requesting the ML Kit Arabic model.
assert "normalizeDetectedLanguage" in service
assert "TranslateLanguage.fromLanguageTag(normalizedLanguage)" in service
# Successful async translations must not be discarded by a newer audio segment.
assert "translator === activeTranslator" in service
# Normalized weather classification must drive fog and snow intensity.
assert "drawRoyalFog(progress, width, height, dense = effectiveWeatherCode == 48)" in weather
assert "val intensity = when (effectiveWeatherCode)" in weather
