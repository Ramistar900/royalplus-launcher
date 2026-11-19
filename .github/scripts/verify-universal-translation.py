#!/usr/bin/env python3
"""Structural checks for free, on-device SMaLL-100 floating translation."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/royalplus/launcher"
service = (JAVA / "FloatingTranslationOverlayService.kt").read_text(encoding="utf-8")
models = (JAVA / "WhisperModelRepository.kt").read_text(encoding="utf-8")
translator = (JAVA / "Small100Translator.kt").read_text(encoding="utf-8")
repository = (JAVA / "Small100ModelRepository.kt").read_text(encoding="utf-8")
tokenizer = (JAVA / "Small100BpeTokenizer.kt").read_text(encoding="utf-8")
gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

# Whisper must auto-detect source speech language locally.
assert 'language = ""' in models
assert 'task = "transcribe"' in models

# Keep playback capture and microphone fallback.
assert "AudioPlaybackCaptureConfiguration.Builder" in service
assert "createMicrophoneRecord()" in service
assert "MediaRecorder.AudioSource.VOICE_RECOGNITION" in service

# SMaLL-100 local model, 100-language token map, and pure Kotlin tokenizer.
assert 'libs/onnxruntime-android-1.28.2.aar' in gradle
assert "com.google.mlkit:translate" not in gradle
assert "Small100Translator.create(files)" in service
assert "translationExecutor.execute" in service
assert "ACTION_DOWNLOAD_TRANSLATION_MODEL" in service
assert "NET_CAPABILITY_NOT_METERED" in repository
assert "map.length() == 100" in repository
assert "languageToId.containsKey(\"ar\")" in translator
assert "model.getJSONArray(\"merges\")" in tokenizer
assert "HuggingFaceTokenizer" not in tokenizer

# No cloud translator, API key, or remote translation endpoint.
for source in (service, translator, repository):
    assert "RoyalCloudTranslationClient" not in source
    assert "googleapis.com" not in source
assert not (JAVA / "RoyalCloudTranslationClient.kt").exists()
assert not (ROOT / "supabase/functions/royalplus-translate/index.ts").exists()
assert "ACCESS_NETWORK_STATE" in manifest

print("Free SMaLL-100 on-device translation checks passed.")
