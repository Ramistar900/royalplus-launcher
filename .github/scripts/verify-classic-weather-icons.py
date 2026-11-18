#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/royalplus/launcher"
ASSETS = ROOT / "app/src/main/assets"

component = (JAVA / "WeatherClassicIcon.kt").read_text(encoding="utf-8")
card = (JAVA / "WeatherGlassCard.kt").read_text(encoding="utf-8")
details = (JAVA / "WeatherDetailsScreen.kt").read_text(encoding="utf-8")
screensaver = (JAVA / "RoyalSmartScreensaver.kt").read_text(encoding="utf-8")
settings = (JAVA / "RoyalDeviceSettingsSection.kt").read_text(encoding="utf-8")
atmosphere = (JAVA / "RoyalWeatherAtmosphereOverlay.kt").read_text(encoding="utf-8")
audio = (JAVA / "RoyalWeatherAmbienceController.kt").read_text(encoding="utf-8")

assert not (JAVA / "V21WeatherAnimatedIcon.kt").exists(), "V21 icon component must be removed"
assert not (ASSETS / "weather_v21").exists(), "V21 SVG icon assets must be removed"
weather_assets = ASSETS / "weather"
assert not weather_assets.exists() or not list(weather_assets.glob("weather_*.png")), "Uploaded 3D weather PNGs must be removed"

for name in ("drawSun", "drawWeatherCloud", "drawRain", "drawSnow", "drawFogLines"):
    assert name in component, f"Missing classic weather artwork primitive: {name}"
assert "RoyalClassicWeatherIcon(" not in card, "Home weather card must not draw weather icons or decorative marks"
assert "weather_rain" not in card and "weather_snow" not in card and "weather_storm" not in card, "Home weather card must not show static weather illustrations"
assert "WeatherVideoBackground(" in card and "weatherVideoAsset(" in card, "Home weather card must retain local weather videos"
assert details.count("RoyalClassicWeatherIcon(") >= 2, "Current and forecast icons must use the classic renderer"
assert "RoyalClassicWeatherIcon(" in screensaver, "Screensaver must use the classic renderer"
assert "RoyalV21Weather" not in card + details + screensaver + settings + component
# The classic renderer keeps its vector/Canvas artwork while the sun disc rotates.
assert "rememberInfiniteTransition" in component, "Sun rotation animation must remain enabled"
assert "animateFloat" in component and "rotationDegrees = sunRotation" in component, "Sun rotation must be wired into the icon renderer"
assert "rotate(degrees = rotationDegrees, pivot = center)" in component, "The complete sun disc must rotate around its center"
assert "horizon = true" not in component, "Sunrise/sunset must not clip the sun rays"
assert "drawLine(Color.White.copy(alpha = 0.88f)" not in component, "Remove the unexplained white horizon line"
assert "drawRoyalRain(" not in atmosphere and "drawRoyalSnow(" not in atmosphere, "Animated rain and snow particles must stay off the weather video"
assert "drawRoyalWind(" in atmosphere and "trailingCurl" in atmosphere, "Keep clear curved wind-gust animation"
assert "WIND_AUDIO_VOLUME_MULTIPLIER = 0.52f" in audio, "Lower wind ambience independently"
assert "HEAVY_SNOW_WIND_VOLUME_MULTIPLIER = 0.46f" in audio, "Lower heavy-snow wind ambience independently"
assert "BitmapFactory" not in component and "SVG.getFromAsset" not in component

# Keep scenic weather backgrounds and render animated atmosphere only in Weather details.
for name in (
    "weather_clear_day", "weather_clear_night", "weather_partly_cloudy_day",
    "weather_overcast", "weather_rain", "weather_snow", "weather_storm",
):
    background_dir = ROOT / "app/src/main/res/drawable-nodpi"
    assert any((background_dir / f"{name}.{extension}").is_file() for extension in ("jpg", "webp")), (
        f"Missing preserved background: {name}.jpg or optimized {name}.webp"
    )
assert "RoyalMotionImage(" not in card, "Home weather card must not show static weather artwork"
assert "RoyalWeatherAtmosphereOverlay(" not in card, "Home weather card must not animate rain/snow/wind"
assert "RoyalWeatherAtmosphereOverlay(" in details, "Weather details must retain its atmosphere"
assert "RoyalWeatherAtmosphereOverlay(" not in screensaver, "Screensaver must not render weather atmosphere"

print("Verified full animated sun rays without the white horizon line, wind-only curved atmosphere, no animated rain/snow particles, reduced wind audio, and preserved local weather videos.")
