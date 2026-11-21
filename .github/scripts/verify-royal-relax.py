from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/royalplus/launcher"
HOME = (JAVA / "RoyalHomeScreen.kt").read_text(encoding="utf-8")
RELAX = (JAVA / "RoyalRelaxScreen.kt").read_text(encoding="utf-8")
SIDEBAR = HOME[HOME.index("private fun RoyalSidePanel("):HOME.index("private fun StatusIcon(")]

assert "LauncherDestination.RELAX -> RoyalRelaxScreen(language = selectedLanguage)" in HOME
assert 'labels[6], Icons.Rounded.WbSunny, selectedDestination == LauncherDestination.RELAX' in SIDEBAR
assert "ROYAL RELAX" in SIDEBAR
assert "List(11) { FocusRequester() }" in HOME
assert 'RoyalSettingsNavigationItem(Icons.Rounded.WbSunny, "ROYAL RELAX"' not in HOME
assert 'if (selectedSettingsCategory == "relax")' not in HOME

for asset in (
    "app/src/main/assets/fireplace_burning_fire_loop.mp4",
    "app/src/main/assets/weather_videos/weather_rain.mp4",
    "app/src/main/assets/weather_videos/weather_snow_day.mp4",
    "app/src/main/assets/weather_videos/weather_clear_sunny.mp4",
    "app/src/main/assets/weather_videos/weather_clear_night.mp4",
    "app/src/main/res/raw/weather_rain_natural.ogg",
    "app/src/main/res/raw/weather_thunderstorm_thunder.ogg",
    "app/src/main/res/raw/nature_birds_morning.ogg",
    "app/src/main/res/raw/nature_owl_clear_night.ogg",
    "app/src/main/res/raw/nature_wind_gentle.ogg",
):
    assert (ROOT / asset).is_file(), f"Missing bundled ROYAL RELAX media: {asset}"

for required in (
    "RoyalRelaxVideoView", "RoyalRelaxVideoPlayer", "MediaPlayer",
    "setLifecycleActive", "player.release()", "soundEnabled",
    "soundVolume", "selectedSound", "weather_rain_natural",
    "nature_birds_morning", "nature_owl_clear_night",
):
    assert required in RELAX, f"ROYAL RELAX playback/control missing: {required}"

assert "Live nature cameras" in RELAX or "كاميرات الطبيعة المباشرة" in RELAX
assert "will appear only after" in RELAX or "ستُفعّل بعد ربط مصادر مسموح بها" in RELAX
print("Verified standalone ROYAL RELAX sidebar navigation, offline video scenes, nature audio controls, lifecycle cleanup and explicit source-permission boundaries.")
