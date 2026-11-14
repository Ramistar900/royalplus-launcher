#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/royalplus/launcher"
GRADLE = ROOT / "app/build.gradle.kts"
PROGUARD = ROOT / "app/proguard-rules.pro"

gradle = GRADLE.read_text(encoding="utf-8")
proguard = PROGUARD.read_text(encoding="utf-8")
assert "proguard-rules.pro" in gradle
assert "-keep class com.k2fsa.sherpa.onnx.** { *; }" in proguard
assert "-keep interface com.k2fsa.sherpa.onnx.** { *; }" in proguard

weather = (JAVA / "RoyalWeatherAtmosphereOverlay.kt").read_text(encoding="utf-8")
rain_guard = weather.split("if (isRain) {", 1)[1].split("val isSnow", 1)[0]
rain_renderer = weather.split("private fun DrawScope.drawRoyalRain(", 1)[1].split("private fun DrawScope.drawRoyalSnow(", 1)[0]
assert "drawRoyalRain(" in rain_guard and "return@Canvas" in rain_guard
for forbidden in ("drawRoyalClouds", "drawRoyalSun", "drawRoyalFog", "drawRoyalNightSky", "drawRoyalLightning"):
    assert forbidden not in weather, f"Unwanted procedural weather layer remains: {forbidden}"
assert "drawRoyalSnow(" in weather and "drawRoyalWind(" in weather
assert "cubicTo(" in rain_renderer and "drawPath(" in rain_renderer
assert "drawLine(" not in rain_renderer and "drawRect(" not in rain_renderer

for name in ("WeatherGlassCard.kt", "WeatherDetailsScreen.kt", "RoyalSmartScreensaver.kt"):
    assert "RoyalWeatherAtmosphereOverlay(" in (JAVA / name).read_text(encoding="utf-8")

service = (JAVA / "FloatingTranslationOverlayService.kt").read_text(encoding="utf-8")
assert "SEGMENT_SECONDS = 2" in service
assert "SEGMENT_OVERLAP_MILLIS = 350" in service
assert "SEGMENT_OVERLAP_SAMPLES" in service and "System.arraycopy(" in service
assert "removeOverlappingWords(" in service and "MAX_OVERLAP_WORDS = 4" in service
assert "hasMeaningfulAudio(" in service and "consecutiveSilentSegments" in service
assert "AudioPlaybackCaptureConfiguration.Builder" in service
assert "else createMicrophoneRecord()" in service and "VOICE_RECOGNITION" in service
assert "normalizeDetectedLanguage" in service
assert "TranslateLanguage.fromLanguageTag(normalizedLanguage)" in service
assert "translator === activeTranslator" in service
assert "checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)" in service

home = (JAVA / "RoyalHomeScreen.kt").read_text(encoding="utf-8")
assert "LaunchedEffect(showWeatherDetails, showSmartScreensaver)" in home
assert "RoyalWeatherAmbienceController.onBackground()" in home
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
assert "FOREGROUND_SERVICE_MEDIA_PROJECTION" in manifest
assert 'foregroundServiceType="microphone|mediaProjection"' in manifest
print("Verified rain/snow/wind-only atmosphere, low-latency translation and Android 8 microphone route.")
