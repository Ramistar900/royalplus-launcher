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
for style in ("static", "animated"):
    for name in ICONS:
        path = ASSETS / style / f"{name}.svg"
        assert path.is_file(), f"Missing V20 {style} icon: {name}"
        svg = path.read_text(encoding="utf-8")
        assert "<svg" in svg and "</svg>" in svg, f"Invalid V20 SVG: {path}"
        if style == "animated":
            assert "<animate" in svg, f"V20 animated source missing motion: {name}"
        else:
            assert "<animate" not in svg, f"V20 static source contains animation: {name}"

assert "SVG.getFromAsset" in component and "svg.renderToCanvas" in component
assert 'sports_v20/$style/' in component
assert "RoyalV20SportsMotionOverlay" in component
assert "rememberInfiniteTransition" in component
assert "RoyalV20SportsIconPreferences.KEY_ANIMATED" in component
assert "RoyalV20SportsIconPreferences.setAnimated" in settings
assert "sportsIconsAnimated = !sportsIconsAnimated" in settings
assert "RoyalV20SportsIconId.FOOTBALL" in screen
assert "RoyalV20SportsIconId.TODAY_MATCHES" in screen
assert "RoyalV20SportsIconId.FIXTURES" in screen
assert "RoyalV20SportsIconId.LIVE_SCORES" in screen
assert "RoyalV20SportsIconId.MATCH_RESULTS" in screen
assert "sportsIconId = RoyalV20SportsIconId.FOOTBALL" in home
assert "com.caverock:androidsvg-aar:1.4" in gradle
for forbidden in ("android.webkit.WebView", "AndroidView", "loadDataWithBaseURL"):
    assert forbidden not in component, f"V20 icon renderer must not use {forbidden}"
print("Verified the complete 25-icon V20 collection in static and animated styles, native Android rendering, live saved setting, and Sports screen bindings.")
