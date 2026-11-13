#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets/weather_v21"
KOTLIN = ROOT / "app/src/main/java/com/royalplus/launcher"
ICONS = [
    "sunny", "partly_cloudy", "cloudy", "rain", "drizzle", "heavy_rain",
    "thunderstorm", "lightning", "snow", "heavy_snow", "sleet", "hail",
    "wind", "strong_wind", "fog", "mist", "dust", "hot", "cold",
    "sunrise", "sunset", "clear_night", "tornado", "temperature",
    "humidity", "rainbow",
]

assert len(ICONS) == 26 and len(set(ICONS)) == 26
for name in ICONS:
    path = ASSETS / f"{name}.svg"
    assert path.is_file(), f"Missing V21 animated weather asset: {path}"
    svg = path.read_text(encoding="utf-8")
    assert "<svg" in svg, f"Invalid SVG root: {path}"
    assert re.search(r"<animate(?:Transform|Motion)?\b", svg, re.IGNORECASE), (
        f"V21 asset is not animated: {path}"
    )

component = (KOTLIN / "V21WeatherAnimatedIcon.kt").read_text(encoding="utf-8")
assert all(f'"{name}.svg"' in component for name in ICONS), "Missing icon resolver entry"
assert "javaScriptEnabled = false" in component
assert "blockNetworkLoads = true" in component
assert "file:///android_asset/weather_v21/" in component

glass = (KOTLIN / "WeatherGlassCard.kt").read_text(encoding="utf-8")
details = (KOTLIN / "WeatherDetailsScreen.kt").read_text(encoding="utf-8")
assert "RoyalV21WeatherAnimatedIcon(" in glass and "RoyalV21WeatherAnimatedIcon(" in details
assert "weatherSymbol(" not in details, "Old emoji weather helper remains"

gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
assert re.search(r"minSdk\s*=\s*23", gradle), "Existing minimum SDK changed"
workflow = (ROOT / ".github/workflows/build-apk.yml").read_text(encoding="utf-8")
assert "api-level: 24" in workflow, "Android 7/API 24 smoke test missing"
assert "api-level: 26" in workflow, "Android 8/API 26 smoke test missing"

print(f"Verified all {len(ICONS)} bundled animated V21 SVG assets and Android API 24/26 test coverage.")
