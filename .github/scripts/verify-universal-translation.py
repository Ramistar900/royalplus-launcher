#!/usr/bin/env python3
"""Structural checks for the universal-language translation routing."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/royalplus/launcher"
service = (JAVA / "FloatingTranslationOverlayService.kt").read_text(encoding="utf-8")
models = (JAVA / "WhisperModelRepository.kt").read_text(encoding="utf-8")
client = (JAVA / "RoyalCloudTranslationClient.kt").read_text(encoding="utf-8")
sports = (JAVA / "RoyalSportsScreen.kt").read_text(encoding="utf-8")
edge = (ROOT / "supabase/functions/royalplus-translate/index.ts").read_text(encoding="utf-8")

# Keep the existing multilingual on-device Whisper and eligible playback capture.
assert 'language = ""' in models, "Whisper must keep automatic language detection"
assert 'task = "transcribe"' in models
assert "AudioPlaybackCaptureConfiguration.Builder" in service
assert "createMicrophoneRecord()" in service

# Do not hard-code a finite language allowlist into the language normalizer.
normalizer = service.split("private fun normalizeDetectedLanguage", 1)[1].split("private fun translateCaption", 1)[0]
assert "when (normalized)" not in normalizer
assert ".substringBefore('-')" in normalizer
assert "TranslateLanguage.fromLanguageTag(normalizedLanguage)" in service
assert "if (localSourceLanguage == null)" in service
assert "translateQueuedCaptionWithCloud(pending)" in service

# Cloud fallback receives only recognized text and uses the existing user-scoped auth.
assert 'JSONObject().put("text", normalized)' in client
assert 'setRequestProperty("apikey", auth.publishableKey)' in client
assert 'setRequestProperty("Authorization", "Bearer ${auth.accessToken}")' in client
assert "SUPABASE_SERVICE_ROLE" not in client
assert "internal fun translationAuth(context: Context)" in sports

# Server validates the Supabase JWT and keeps the Google key server-side.
assert 'Deno.env.get("GOOGLE_TRANSLATE_API_KEY")' in edge
assert '/auth/v1/user' in edge
assert 'parameters.set("target", "ar")' in edge
assert 'parameters.set("source"' not in edge, "Cloud provider must auto-detect source language"
assert "audio" not in client.lower(), "The app must not upload captured audio to translation service"
print("Universal translation routing checks passed.")
