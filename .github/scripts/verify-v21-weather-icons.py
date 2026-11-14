#!/usr/bin/env python3
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/royalplus/launcher"
SVG_ASSETS = ROOT / "app/src/main/assets/weather_v21"
PNG_ASSETS = ROOT / "app/src/main/assets/weather"

FALLBACK_ICONS = [
    "sunny", "partly_cloudy", "cloudy", "rain", "drizzle", "heavy_rain",
    "thunderstorm", "lightning", "snow", "heavy_snow", "sleet", "hail",
    "wind", "strong_wind", "fog", "mist", "dust", "hot", "cold",
    "sunrise", "sunset", "clear_night", "tornado", "temperature",
    "humidity", "rainbow",
]
UPLOADED_3D_PNG_ICONS = [
    "blizzard", "clear_night", "cloudy", "fog", "heavy_snow",
    "partly_cloudy", "rain", "snow", "strong_wind", "sunny", "thunderstorm",
]

component = (JAVA / "V21WeatherAnimatedIcon.kt").read_text(encoding="utf-8")
settings = (JAVA / "RoyalDeviceSettingsSection.kt").read_text(encoding="utf-8")
card = (JAVA / "WeatherGlassCard.kt").read_text(encoding="utf-8")
details = (JAVA / "WeatherDetailsScreen.kt").read_text(encoding="utf-8")
gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/build-apk.yml").read_text(encoding="utf-8")

assert len(FALLBACK_ICONS) == 26 and len(set(FALLBACK_ICONS)) == 26
for name in FALLBACK_ICONS:
    path = SVG_ASSETS / f"{name}.svg"
    assert path.is_file(), f"Missing original V21 SVG fallback: {path}"
    svg = path.read_text(encoding="utf-8")
    assert "<svg" in svg and "<defs>" in svg, f"Invalid V21 SVG: {path}"

assert len(UPLOADED_3D_PNG_ICONS) == 11 and len(set(UPLOADED_3D_PNG_ICONS)) == 11
for name in UPLOADED_3D_PNG_ICONS:
    path = PNG_ASSETS / f"weather_{name}.png"
    assert path.is_file(), f"Missing uploaded 3D weather artwork: {path}"
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", f"Invalid PNG signature: {path}"
    width, height = struct.unpack(">II", data[16:24])
    assert width > 0 and height > 0, f"Invalid PNG dimensions: {path}"

assert "com.caverock:androidsvg-aar:1.4" in gradle
assert "BitmapFactory.decodeStream" in component
assert "weather_" in component and ".name.lowercase(java.util.Locale.ROOT)" in component
assert 'context.assets.open("weather/$pngName")' in component
assert 'SVG.getFromAsset(context.assets, "weather_v21/$fallbackFileName")' in component
assert "svg.renderToCanvas" in component
assert "RoyalV21WeatherMotionOverlay" not in component
assert "rememberInfiniteTransition" not in component
assert "rememberV21WeatherAnimationEnabled" not in component
assert "RoyalV21WeatherIconPreferences" not in component
assert "weatherIconsAnimated" not in settings
assert "V21 weather icon style" not in settings
assert "RoyalV21WeatherAnimatedIcon(" in card and "RoyalV21WeatherAnimatedIcon(" in details

for forbidden in ("android.webkit.WebView", "AndroidView", "loadDataWithBaseURL", "file:///android_asset/"):
    assert forbidden not in component, f"Weather icon renderer must not use {forbidden}"

assert "minSdk = 23" in gradle
assert "api-level: 24" in workflow and "api-level: 26" in workflow
print("Verified 11 static 3D PNG weather icons, 26 SVG fallbacks, native rendering, and Android 8 coverage.")
