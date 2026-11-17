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
vad_repository = (JAVA / "WhisperModelRepository.kt").read_text(encoding="utf-8")
audio_gate = (JAVA / "SpeechAudioGate.kt").read_text(encoding="utf-8")
assert "createVoiceActivityDetector" in vad_repository
assert "SileroVadModelConfig" in vad_repository
assert "VAD_SIZE = 643_854L" in vad_repository
assert "VAD_BASE" in vad_repository
assert "createAudioTagger" in vad_repository
assert "AudioTaggingModelConfig" in vad_repository
assert "AUDIO_TAGGING_MODEL_SHA256" in vad_repository
assert "CAPTION_PHRASE_IDLE_MILLIS = 250L" in service
assert "VAD_WINDOW_SAMPLES = 512" in service
assert "detector.acceptWaveform(frame)" in service and "detector.flush()" in service
assert "speechSegments = LinkedBlockingQueue<FloatArray>()" in service
assert "enqueueReadySpeechSegments(detector)" in service
assert "audioGapDetected" not in service and "audioSegments" not in service
assert "MAX_PENDING_TRANSLATION_AGE_MILLIS" not in service
assert "MAX_QUEUED_TRANSLATIONS" not in service
assert "pendingTranslations.addLast(" in service
assert "pendingTranslations.addFirst(" in service
assert "processNextPendingTranslation()" in service
assert "stabilizeDetectedLanguage(" not in service
assert "queueCaptionPhrase(recognized, language)" in service
assert "SpeechAudioGate.isPredominantlyNonSpeech" in service
assert "tagger.compute(stream, AUDIO_TAGGING_TOP_K)" in service
assert '"singing"' in audio_gate and '"music"' in audio_gate
assert "SPEECH_PRESENCE_THRESHOLD" in audio_gate
assert "hasMeaningfulAudio(" not in service
speech_filter = (JAVA / "SpeechCaptionFilter.kt").read_text(encoding="utf-8")
assert '"music"' in speech_filter and '"آهات"' in speech_filter and '"moaning"' in speech_filter
assert "nonArabicCharacters" in (JAVA / "CaptionPhraseBuffer.kt").read_text(encoding="utf-8")
assert "AudioPlaybackCaptureConfiguration.Builder" in service
assert "else createMicrophoneRecord()" in service and "VOICE_RECOGNITION" in service
assert "normalizeDetectedLanguage" in service
assert "TranslateLanguage.fromLanguageTag(normalizedLanguage)" in service
assert "translationSessions.getOrPut(sourceLanguage)" in service
assert "RemoteModelManager.getInstance()" in service
assert "getDownloadedModels(TranslateRemoteModel::class.java)" in service
assert "session.translator.downloadModelIfNeeded(" in service
assert "translator === activeTranslator" not in service
assert "queueCaptionPhrase(recognized, language)" in service
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
print("Verified rain/snow/wind-only atmosphere, VAD speech turns, audio-event filtering, lossless ordered captions and Android 8 microphone route.")
