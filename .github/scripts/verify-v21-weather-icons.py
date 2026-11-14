#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/royalplus/launcher"
ASSETS = ROOT / "app/src/main/assets/weather_v21"
ICONS = [
    "sunny", "partly_cloudy", "cloudy", "rain", "drizzle", "heavy_rain",
    "thunderstorm", "lightning", "snow", "heavy_snow", "sleet", "hail",
    "wind", "strong_wind", "fog", "mist", "dust", "hot", "cold",
    "sunrise", "sunset", "clear_night", "tornado", "temperature",
    "humidity", "rainbow",
]

component = (JAVA / "V21WeatherAnimatedIcon.kt").read_text(encoding="utf-8")
settings = (JAVA / "RoyalDeviceSettingsSection.kt").read_text(encoding="utf-8")
card = (JAVA / "WeatherGlassCard.kt").read_text(encoding="utf-8")
details = (JAVA / "WeatherDetailsScreen.kt").read_text(encoding="utf-8")
gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")

assert len(ICONS) == 26 and len(set(ICONS)) == 26
for name in ICONS:
    path = ASSETS / f"{name}.svg"
    assert path.is_file(), f"Missing original V21 artwork: {path}"
    svg = path.read_text(encoding="utf-8")
    assert "<svg" in svg and "<defs>" in svg, f"Invalid V21 SVG: {path}"
    assert "<animate" in svg, f"V21 animated source was lost: {path}"

assert "com.caverock:androidsvg-aar:1.4" in gradle
assert "SVG.getFromAsset" in component and "svg.renderToCanvas" in component
assert 'weather_v21/${icon.fileName}' in component
assert "RoyalV21WeatherMotionOverlay" in component
assert "rememberInfiniteTransition" in component
assert "RoyalV21WeatherIconPreferences.KEY_ANIMATED" in component
assert "SharedPreferences.OnSharedPreferenceChangeListener" in component
assert "RoyalV21WeatherIconPreferences.setAnimated" in settings
assert "weatherIconsAnimated = !weatherIconsAnimated" in settings
assert "RoyalV21WeatherAnimatedIcon(" in card and "RoyalV21WeatherAnimatedIcon(" in details
for forbidden in ("android.webkit.WebView", "AndroidView", "loadDataWithBaseURL", "file:///android_asset/"):
    assert forbidden not in component, f"Weather icon renderer must not use {forbidden}"

assert "minSdk = 23" in gradle
workflow = (ROOT / ".github/workflows/build-apk.yml").read_text(encoding="utf-8")
assert "api-level: 24" in workflow and "api-level: 26" in workflow
print("Verified 26 original V21 SVG assets, native Android rendering, persistent static/animated setting, and Android 8 coverage.")
