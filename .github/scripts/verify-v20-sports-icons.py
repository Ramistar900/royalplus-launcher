#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/royalplus/launcher"
ASSETS = ROOT / "app/src/main/assets/sports_v20"
ICONS = ["today_matches","live_scores","match_results","fixtures","standings","football","basketball","tennis","volleyball","cricket","trophy","teams","players","stadium","goal","whistle","yellow_card","red_card","medal","statistics","bracket","transfer","sports_news","replay","stopwatch"]
component = (JAVA / "V20SportsIcon.kt").read_text(encoding="utf-8")
screen = (JAVA / "RoyalSportsScreen.kt").read_text(encoding="utf-8")
home = (JAVA / "RoyalHomeScreen.kt").read_text(encoding="utf-8")
settings = (JAVA / "RoyalDeviceSettingsSection.kt").read_text(encoding="utf-8")
gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")

assert len(ICONS) == 25 and len(set(ICONS)) == 25
for name in ICONS:
    path = ASSETS / "static" / f"{name}.svg"
    assert path.is_file(), f"Missing static V20 icon: {name}"
    svg = path.read_text(encoding="utf-8")
    assert "<svg" in svg and "</svg>" in svg, f"Invalid V20 SVG: {path}"
    assert "<animate" not in svg, f"V20 sports artwork must stay static: {name}"
assert not (ASSETS / "animated").exists(), "Animated V20 assets must not be bundled."

assert "SVG.getFromAsset" in component and "svg.renderToCanvas" in component
assert 'sports_v20/static/' in component
assert "RoyalV20SportsMotionOverlay" not in component
assert "rememberInfiniteTransition" not in component
assert "RoyalV20SportsIconPreferences" not in component
assert "royalV20SportsIconSize" in component
assert "LocalRoyalAdaptiveProfile.current" in component
assert "windowWidthDp" in component
assert "RoyalV20SportsIconPreferences" not in settings
assert "V20 sports icon style" not in settings
assert "نمط أيقونات الرياضة V20" not in settings
assert "RoyalV20SportsIconId.FOOTBALL" in screen
assert "RoyalV20SportsIconId.TODAY_MATCHES" in screen
assert "RoyalV20SportsIconId.FIXTURES" in screen
assert "RoyalV20SportsIconId.LIVE_SCORES" in screen
assert "RoyalV20SportsIconId.MATCH_RESULTS" in screen
assert screen.count("royalV20SportsIconSize(") >= 6
assert "sportsIconId = RoyalV20SportsIconId.FOOTBALL" in home
assert "royalV20SportsIconSize(28.dp" in home
assert "com.caverock:androidsvg-aar:1.4" in gradle
for forbidden in ("android.webkit.WebView", "AndroidView", "loadDataWithBaseURL"):
    assert forbidden not in component, f"V20 icon renderer must not use {forbidden}"
print("Verified all 25 V20 sports icons are bundled static-only, Android-native rendered, and adaptively sized by current window/device profile.")
