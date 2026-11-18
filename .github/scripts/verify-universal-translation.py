#!/usr/bin/env python3
"""Structural checks for free, on-device multilingual floating translation."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/royalplus/launcher"
service = (JAVA / "FloatingTranslationOverlayService.kt").read_text(encoding="utf-8")
models = (JAVA / "WhisperModelRepository.kt").read_text(encoding="utf-8")

# Multilingual Whisper must auto-detect the source language locally.
assert 'language = ""' in models, "Whisper must keep automatic language detection"
assert 'task = "transcribe"' in models

# Keep direct playback capture when Android/device policy allows it, plus mic fallback.
assert "AudioPlaybackCaptureConfiguration.Builder" in service
assert "createMicrophoneRecord()" in service
assert "MediaRecorder.AudioSource.VOICE_RECOGNITION" in service

# Do not maintain an app-owned allowlist; let ML Kit report supported local languages.
normalizer = service.split("private fun normalizeDetectedLanguage", 1)[1].split(
    "private fun translateCaption", 1
)[0]
assert "when (normalized)" not in normalizer
assert ".substringBefore('-')" in normalizer
assert "TranslateLanguage.fromLanguageTag(normalizedLanguage)" in service
assert "sourceLanguage.isNullOrBlank()" in service
assert "translationSessions.getOrPut(pending.sourceLanguage)" in service
assert "session.translator.downloadModelIfNeeded(" in service

# No cloud translator, Google API key, or remote translation function is permitted.
assert "RoyalCloudTranslationClient" not in service
assert "translateQueuedCaptionWithCloud" not in service
assert "cloudTranslationExecutor" not in service
assert "googleapis.com" not in service
assert not (JAVA / "RoyalCloudTranslationClient.kt").exists()
assert not (ROOT / "supabase/functions/royalplus-translate/index.ts").exists()

print("Free on-device multilingual translation checks passed.")
