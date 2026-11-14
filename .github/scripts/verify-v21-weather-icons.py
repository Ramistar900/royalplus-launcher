#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/royalplus/launcher"
component = (JAVA / "V21WeatherAnimatedIcon.kt").read_text(encoding="utf-8")
card = (JAVA / "WeatherGlassCard.kt").read_text(encoding="utf-8")
details = (JAVA / "WeatherDetailsScreen.kt").read_text(encoding="utf-8")

assert component.count('.svg"') == 26, "All 26 weather conditions need a native symbol state"
assert "Canvas(" in component and "rememberInfiniteTransition" in component
assert "shouldAnimate = motionEnabled" in component, "Only rain, snow and wind symbols may animate"
for forbidden in ("android.webkit.WebView", "AndroidView", "loadDataWithBaseURL", "weather_v21/"):
    assert forbidden not in component, f"Weather rendering must not rely on {forbidden}"
assert "RoyalV21WeatherAnimatedIcon(" in card and "RoyalV21WeatherAnimatedIcon(" in details

gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
assert "minSdk = 23" in gradle
workflow = (ROOT / ".github/workflows/build-apk.yml").read_text(encoding="utf-8")
assert "api-level: 24" in workflow and "api-level: 26" in workflow
print("Verified native Compose weather icons and Android 8 compatibility coverage.")
