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

assert not (JAVA / "V21WeatherAnimatedIcon.kt").exists(), "V21 icon component must be removed"
assert not (ASSETS / "weather_v21").exists(), "V21 SVG icon assets must be removed"
weather_assets = ASSETS / "weather"
assert not weather_assets.exists() or not list(weather_assets.glob("weather_*.png")), "Uploaded 3D weather PNGs must be removed"

for name in ("drawSun", "drawWeatherCloud", "drawRain", "drawSnow", "drawFogLines"):
    assert name in component, f"Missing classic weather artwork primitive: {name}"
assert "RoyalClassicWeatherIcon(" in card
assert details.count("RoyalClassicWeatherIcon(") >= 2, "Current and forecast icons must use the classic renderer"
assert "RoyalClassicWeatherIcon(" in screensaver, "Screensaver must use the classic renderer"
assert "RoyalV21Weather" not in card + details + screensaver + settings + component
# The classic renderer keeps its vector/Canvas artwork while the sun disc rotates.
assert "rememberInfiniteTransition" in component, "Sun rotation animation must remain enabled"
assert "animateFloat" in component and "rotationDegrees = sunRotation" in component, "Sun rotation must be wired into the icon renderer"
assert "rotate(degrees = rotationDegrees, pivot = center)" in component, "The complete sun disc must rotate around its center"
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
assert "RoyalMotionImage(" in card, "Home weather card must retain its background image"
assert "RoyalWeatherAtmosphereOverlay(" not in card, "Home weather card must not animate rain/snow/wind"
assert "RoyalWeatherAtmosphereOverlay(" in details, "Weather details must retain its atmosphere"
assert "RoyalWeatherAtmosphereOverlay(" not in screensaver, "Screensaver must not render weather atmosphere"

print("Verified classic Canvas weather artwork, rotating sun disc, V21 and 11 PNG icon removal, and preserved weather backgrounds/effects.")
