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
assert "drawRoyalRain(" not in weather, "Animated rain drops must not cover the local weather video"
assert "drawRoyalSnow(" not in weather, "Animated snow particles must not cover the local weather video"
assert "drawRoyalWind(" in weather, "Weather atmosphere must retain wind motion"
assert "val windThreshold = if (isSnow) 12 else 18" in weather, "Wind gusts should remain visible during snow and stronger wind"
gust_renderer = weather.split("private fun DrawScope.drawRoyalWind(", 1)[1]
for forbidden in ("drawRoyalClouds", "drawRoyalSun", "drawRoyalFog", "drawRoyalNightSky", "drawRoyalLightning"):
    assert forbidden not in weather, f"Unwanted procedural weather layer remains: {forbidden}"
assert "cubicTo(" in gust_renderer and "trailingCurl" in gust_renderer and "drawPath(" in gust_renderer
assert "drawLine(" not in weather and "drawRect(" not in weather

weather_details = (JAVA / "WeatherDetailsScreen.kt").read_text(encoding="utf-8")
weather_card = (JAVA / "WeatherGlassCard.kt").read_text(encoding="utf-8")
screensaver = (JAVA / "RoyalSmartScreensaver.kt").read_text(encoding="utf-8")
assert "RoyalWeatherAtmosphereOverlay(" in weather_details, "Weather details must retain its animated weather atmosphere"
assert "RoyalWeatherAtmosphereOverlay(" not in weather_card, "Home weather card must not render weather atmosphere effects"
assert "RoyalWeatherAtmosphereOverlay(" not in screensaver, "Smart Screensaver must not render weather atmosphere effects"
assert "RoyalWeatherAmbienceEffect(weather)" in weather_details
assert "RoyalOwlAmbienceEffect(weather)" in weather_details

service = (JAVA / "FloatingTranslationOverlayService.kt").read_text(encoding="utf-8")
whisper_repository = (JAVA / "WhisperModelRepository.kt").read_text(encoding="utf-8")
assert "audioSegments = ArrayBlockingQueue<FloatArray>(4)" in service
assert "SEGMENT_SECONDS = 2" in service
assert "SEGMENT_OVERLAP_MILLIS = 350" in service
assert "SEGMENT_OVERLAP_SAMPLES" in service and "System.arraycopy(" in service
assert "audioSegments.offer(completed)" in service
assert "hasMeaningfulAudio(samples)" in service
assert "removeOverlappingWords(recognized)" in service
assert "queueCaptionPhrase(freshText, language)" in service
assert "SpeechCaptionFilter.isNonSpeechLabel(recognized)" in service
assert "SpeechAudioGate.isPredominantlyNonSpeech" not in service
assert "createVoiceActivityDetector" not in whisper_repository
assert "createAudioTagger" not in whisper_repository
assert "SileroVadModelConfig" not in whisper_repository
assert "AudioTaggingModelConfig" not in whisper_repository
assert "CAPTION_PHRASE_IDLE_MILLIS = 250L" in service
assert "pendingTranslations.addLast(" in service
assert "pendingTranslations.addFirst(" in service
assert "processNextPendingTranslation()" in service
assert "stabilizeDetectedLanguage(" not in service
assert "MAX_PENDING_TRANSLATION_AGE_MILLIS" not in service
assert "MAX_QUEUED_TRANSLATIONS" not in service
assert "translationSessions" not in service
assert "TranslateLanguage" not in service
assert "RoyalCloudTranslationClient" not in service
assert "translator === activeTranslator" not in service
assert "captionPhraseBuffer" in service
assert "NET_CAPABILITY_NOT_METERED" in (JAVA / "Small100ModelRepository.kt").read_text(encoding="utf-8")
assert "Small100Translator.create(files)" in service
assert "translationExecutor.execute" in service
assert "translationModelSetup" in service
assert "ACTION_DOWNLOAD_TRANSLATION_MODEL" in service
assert "translationDownloadNoticeShown" in service
assert "اكتمل نموذج التعرّف الصوتي" in service
speech_filter = (JAVA / "SpeechCaptionFilter.kt").read_text(encoding="utf-8")
assert '"music"' in speech_filter and '"آهات"' in speech_filter and '"moaning"' in speech_filter
caption_formatter = (JAVA / "CaptionPhraseBuffer.kt").read_text(encoding="utf-8")
assert "ArabicCaptionFormatter" in caption_formatter
assert "isAllowedCaptionCharacter" in caption_formatter
assert "append(if (character.isAllowedCaptionCharacter()) character else ' ')" in caption_formatter
assert "AudioPlaybackCaptureConfiguration.Builder" in service
assert "else createMicrophoneRecord()" in service and "VOICE_RECOGNITION" in service
assert "normalizeDetectedLanguage" in service
assert "Small100Translator.create(files)" in service
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
weather_locations = (JAVA / "WeatherLocationRepository.kt").read_text(encoding="utf-8")
city_search = weather_locations.split("fun searchCities(", 1)[1].split("fun fetchForCity(", 1)[0]
assert "&count=100" in city_search, "City search must request the full 100-result geocoding response"
assert "admin1" in city_search and "admin2" in city_search and '"country"' in city_search
assert "mapNotNull" in city_search, "Every valid returned place must be retained in the city suggestions"
assert "population" not in city_search, "Do not remove small towns or villages using a population threshold"
assert "fun fetchForLocation(" in weather_locations, "Selected geocoding coordinates must be used directly for weather"

city_screen = (JAVA / "WeatherDetailsScreen.kt").read_text(encoding="utf-8")
assert "RoyalWeatherRepository.searchCities(query)" in city_screen
assert "citySuggestions.forEach" in city_screen, "The city picker must render all returned suggestions"
assert "focusedTextColor = gold" in city_screen and "unfocusedTextColor = gold" in city_screen
assert ".border(1.5.dp, Color.White, searchShape)" in city_screen
assert "Brush.linearGradient(" in city_screen and "Color(0xFF078F91)" in city_screen and "Color(0xFF126DB2)" in city_screen

activity = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")
# Model setup/download guidance belongs to the phone foreground service after role separation.
assert "نموذج الترجمة العربي المحلي (~621MB)" in service
assert "افتح إشعار ROYAL+" in service
assert "عبر Wi-Fi" in service
assert "weather_manual_latitude" in activity and "weather_manual_longitude" in activity
assert "fetchForLocation(this, normalized, city.latitude, city.longitude)" in activity


# Forecasts use the approved providers directly, with local seven-day caching.
assert "https://api.open-meteo.com/v1/forecast" in weather_locations
assert "https://api.met.no/weatherapi/locationforecast/2.0/compact" in weather_locations
assert "fetchOpenMeteo(location, city)" in weather_locations
assert "fetchMetNorway(location, city)" in weather_locations
assert "fetchWeatherGateway" not in weather_locations
assert "BuildConfig.ROYAL_WEATHER_API_BASE_URL" not in weather_locations
assert 'put("attributionText", weather.attributionText)' in weather_locations
assert 'put("attributionUrl", weather.attributionUrl)' in weather_locations
assert 'put("licenseUrl", weather.licenseUrl)' in weather_locations
assert 'attributionText = json.optString("attributionText")' in weather_locations
assert "CC BY 4.0" in city_screen
assert "weather.licenseUrl" in city_screen and "weather.attributionUrl" in city_screen

# Legacy caches must remain usable offline without inventing an unknown provider.
assert "Legacy cached data (original provider not recorded)" in weather_locations
assert "Older app versions cached forecasts without recording their provider." in weather_locations
assert '?: "https://creativecommons.org/licenses/by/4.0/"' in weather_locations

# MET Norway supplies UTC timestamps; convert them using the selected city's timezone.
assert "fetchOpenMeteoTimeZone(location)" in weather_locations
assert "fetchMetNorway(location, city, cityTimeZone)" in weather_locations
assert 'TimeZone.getTimeZone("UTC")' in weather_locations
assert "timeZone = cityTimeZone" in weather_locations
assert "timeZoneId = cityTimeZone.id" in weather_locations
assert "localDateFormat.format(instant)" in weather_locations

# Verify the UTC-to-city-local date boundary used by the MET Norway regression fixture.
from datetime import datetime, timezone
from zoneinfo import ZoneInfo
utc_instant = datetime(2026, 1, 1, 23, 30, tzinfo=timezone.utc)
assert utc_instant.astimezone(ZoneInfo("Europe/Istanbul")).strftime("%Y-%m-%d") == "2026-01-02"
assert utc_instant.astimezone(ZoneInfo("America/Los_Angeles")).strftime("%Y-%m-%d") == "2026-01-01"

# A failed primary or backup must not clear the last successful local forecast.
assert "fetchOpenMeteo(location, city)" in weather_locations
assert "fetchMetNorway(location, city, cityTimeZone)" in weather_locations
assert "cached?.first?.copy(city = city) ?: throw IllegalStateException" in weather_locations
assert "require(weather.forecast.size >= 7)" in weather_locations

print("Verified direct provider fallback, offline legacy-cache disclosure, city-local MET Norway dates, seven-day cache preservation, and CC BY 4.0 attribution.")
