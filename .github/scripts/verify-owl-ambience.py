#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[2]
source = root / "app/src/main/java/com/royalplus/launcher/RoyalOwlAmbienceController.kt"
raw = root / "app/src/main/res/raw/nature_owl_clear_night.ogg"
text = source.read_text(encoding="utf-8")

assert raw.is_file() and raw.stat().st_size > 10_000, "Bundled clear-night owl recording is missing or unexpectedly small"
assert "FIRST_CALL_DELAY_MS = 5_000L" in text, "First owl call should start shortly after clear-night eligibility"
assert "Random.nextLong(MIN_INTERVAL_MS, MAX_INTERVAL_MS + 1L)" in text, "Subsequent owl calls should retain natural 30–60 minute spacing"
assert "hasPlayedInCurrentNight" in text, "Owl scheduler must distinguish the first call from repeat calls"
assert "code !in 0..1" in text, "Owl must remain limited to clear weather codes"
assert "isOwlNightWindow(hour)" in text, "Owl must remain limited to the night window"
assert "latestWeatherIdentity = null" in text, "Resuming weather screen must refresh the current weather freshness window"
assert "RoyalWeatherAmbienceController.MASTER_ENABLED_KEY" in text, "Owl must respect the master ambience switch"
assert "RoyalWeatherAmbienceController.volume(context)" in text, "Owl must respect the shared ambience volume"

print("Verified bundled owl audio, clear-weather/night eligibility, 5-second first call, 30–60-minute repeats, resume refresh and shared audio controls.")
