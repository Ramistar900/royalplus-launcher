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

weather_details = (JAVA / "WeatherDetailsScreen.kt").read_text(encoding="utf-8")
weather_card = (JAVA / "WeatherGlassCard.kt").read_text(encoding="utf-8")
screensaver = (JAVA / "RoyalSmartScreensaver.kt").read_text(encoding="utf-8")
assert "RoyalWeatherAtmosphereOverlay(" in weather_details, "Weather details must retain its animated weather atmosphere"
assert "RoyalWeatherAtmosphereOverlay(" not in weather_card, "Home weather card must not render weather atmosphere effects"
assert "RoyalWeatherAtmosphereOverlay(" not in screensaver, "Smart Screensaver must not render weather atmosphere effects"
assert "RoyalWeatherAmbienceEffect(weather)" in weather_details
assert "RoyalOwlAmbienceEffect(weather)" in weather_details

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
assert "translationSessions.getOrPut(sourceLanguage)" in service
assert "RemoteModelManager.getInstance()" in service
assert "getDownloadedModels(TranslateRemoteModel::class.java)" in service
assert "session.translator.downloadModelIfNeeded(" in service
assert "translator === activeTranslator" not in service
assert "queueCaptionPhrase(freshText, language)" in service
assert "captionPhraseBuffer" in service
assert "ArabicCaptionFormatter.format(text)" in service
assert "maxLines = 2" in service
assert "(resources.displayMetrics.widthPixels * 0.90f)" in service
assert "checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)" in service

home = (JAVA / "RoyalHomeScreen.kt").read_text(encoding="utf-8")
assert "LaunchedEffect(showWeatherDetails, showSmartScreensaver)" in home
assert "RoyalWeatherAmbienceController.onBackground()" in home
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
assert "FOREGROUND_SERVICE_MEDIA_PROJECTION" in manifest
assert 'foregroundServiceType="microphone|mediaProjection"' in manifest
print("Verified rain/snow/wind-only atmosphere, low-latency translation and Android 8 microphone route.")
