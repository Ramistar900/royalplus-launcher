#!/usr/bin/env python3
"""Structural checks for free, on-device SMaLL-100 floating translation."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/royalplus/launcher"
service = (JAVA / "FloatingTranslationOverlayService.kt").read_text(encoding="utf-8")
activity = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")
capability = (JAVA / "TranslationDeviceCapability.kt").read_text(encoding="utf-8")
capability_tests = (ROOT / "app/src/test/java/com/royalplus/launcher/TranslationDeviceCapabilityTest.kt").read_text(encoding="utf-8")
models = (JAVA / "WhisperModelRepository.kt").read_text(encoding="utf-8")
translator = (JAVA / "Small100Translator.kt").read_text(encoding="utf-8")
repository = (JAVA / "Small100ModelRepository.kt").read_text(encoding="utf-8")
tokenizer = (JAVA / "Small100BpeTokenizer.kt").read_text(encoding="utf-8")
gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/build-apk.yml").read_text(encoding="utf-8")
tokenizer_tests = (ROOT / "app/src/test/java/com/royalplus/launcher/Small100BpeTokenizerTest.kt").read_text(encoding="utf-8")

# Whisper must auto-detect source speech language locally.
assert 'language = ""' in models
assert 'task = "transcribe"' in models

# Keep playback capture and microphone fallback.
assert "AudioPlaybackCaptureConfiguration.Builder" in service
assert "createMicrophoneRecord()" in service
assert "MediaRecorder.AudioSource.VOICE_RECOGNITION" in service

# Speech must reach Whisper without a VAD or event-classifier hard gate.
assert "ArrayBlockingQueue<FloatArray>(4)" in service
assert "SEGMENT_SECONDS = 2" in service
assert "SEGMENT_OVERLAP_MILLIS = 350" in service
assert "audioSegments.offer(completed)" in service
assert "audioSegments.poll()" in service
assert "MAX_OVERLAP_WORDS = 4" in service
assert "hasMeaningfulAudio(samples)" in service
assert "SpeechCaptionFilter.isNonSpeechLabel(recognized)" in service
assert "SpeechAudioGate.isPredominantlyNonSpeech" not in service
assert "createVoiceActivityDetector" not in models
assert "createAudioTagger" not in models
assert "SileroVadModelConfig" not in models
assert "AudioTaggingModelConfig" not in models
assert "queueCaptionPhrase(freshText, language)" in service
assert "افتح إشعار ROYAL+" in service

# SMaLL-100 local model, 100-language token map, and pure Kotlin tokenizer.
assert 'libs/onnxruntime-android-1.28.2.aar' in gradle
assert 'pickFirsts += "**/libonnxruntime.so"' in gradle
assert workflow.count("onnxruntime-android-1.28.2.zip") >= 2
assert "run_mode:" in workflow and "default: verify" in workflow
assert "inputs.run_mode == 'verify'" in workflow
assert "inputs.run_mode == 'signed-release'" in workflow
assert 'TAG="royalplus-test-translation-local"' in workflow
assert "feature/weather-tv-load-optimization-main-rebased" in workflow
assert "01518867f78241138b6aa25925802e843a4fa9085af8d303d49e35bbb52aff4d" in workflow
assert "cmp \"/tmp/royal-onnx/sherpa-$abi.so\" \"/tmp/royal-onnx/ort-$abi.so\"" in workflow
assert "com.google.mlkit:translate" not in gradle
assert "Small100Translator.create(files)" in service
assert "translationExecutor.execute" in service
assert "ACTION_DOWNLOAD_TRANSLATION_MODEL" in service
assert "createVerifiedTranslationEngine(files)" in service
assert "TranslationDeviceCapability.checkBeforeModelLoad(applicationContext)" in service
assert "TranslationDeviceCapability.benchmark(applicationContext, engine)" in service
assert "consecutiveSlowRecognitionSegments" in service
assert "engine.decode(stream)" in service
assert "TranslationDeviceCapability.slowSpeechRecognition()" in service
assert "failTranslationForCapability(assessment)" in service
assert "ACTION_DEVICE_CAPABILITY_FAILED" in service
assert "translationCapabilityFailed" in service
assert "TranslationDeviceCapability.checkHardware(this)" in activity
assert "showTranslationCapabilityDialog" in activity
assert "RECEIVER_NOT_EXPORTED" in activity
assert "evaluateHardware" in capability and "evaluateAvailableMemory" in capability
assert "evaluateBenchmark" in capability and "SystemClock.elapsedRealtime()" in capability
assert "MAX_AVERAGE_TRANSLATION_MILLIS" in capability
assert "NET_CAPABILITY_NOT_METERED" not in capability
assert "rejectsSlowLocalInference" in capability_tests
assert "reportsSustainedSlowSpeechRecognition" in capability_tests
assert "rejectsInsufficientAvailableMemoryBeforeModelLoad" in capability_tests
assert "NET_CAPABILITY_NOT_METERED" in repository
assert "map.length() == 100" in repository
assert "languageToId.containsKey(\"ar\")" in translator
assert "model.getJSONArray(\"merges\")" in tokenizer
assert "HuggingFaceTokenizer" not in tokenizer
assert "small100-tokenizer.json" in workflow
assert "pinnedModelTokenizerRoundTripsArabicTurkishAndEnglish" in tokenizer_tests
assert "مرحبا بالعالم" in tokenizer_tests and "Merhaba dünya!" in tokenizer_tests

# No cloud translator, API key, or remote translation endpoint.
for source in (service, translator, repository):
    assert "RoyalCloudTranslationClient" not in source
    assert "googleapis.com" not in source
assert not (JAVA / "RoyalCloudTranslationClient.kt").exists()
assert not (ROOT / "supabase/functions/royalplus-translate/index.ts").exists()
assert "ACCESS_NETWORK_STATE" in manifest

print("Free SMaLL-100 on-device translation and device-capability checks passed.")
