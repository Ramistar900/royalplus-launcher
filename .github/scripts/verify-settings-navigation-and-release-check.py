from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HOME = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalHomeScreen.kt").read_text(encoding="utf-8")
SHARE = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalShareUpdatesSection.kt").read_text(encoding="utf-8")
GRADLE = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/build-apk.yml").read_text(encoding="utf-8")

for category in (
    '"appearance"', '"motion"', '"icons"', '"sounds"', '"prayer"',
    '"clock"', '"language"', '"device"', '"share"',
):
    assert category in HOME, f"Missing settings category: {category}"

for existing_setting in (
    "adhan_enabled", "motion_home", "launcher_wallpaper",
    "ui_click_sounds_enabled", "screensaver_delay_seconds",
    "launcher_icon_style", "launcher_24_hour_clock",
    "launcher_show_date", "launcher_language", "launcher_font",
    "ACTION_WIFI_SETTINGS", "RoyalDeviceSettingsSection",
):
    assert existing_setting in HOME, f"Existing setting was removed: {existing_setting}"

assert "MultiFormatWriter" in SHARE and "BarcodeFormat.QR_CODE" in SHARE
assert "releases/latest" in SHARE and "api.github.com/repos/Ramistar900/royalplus-launcher/releases/latest" in SHARE
assert "royalplus-(\\\\d+)$" in SHARE
assert "Intent.ACTION_SEND" in SHARE
assert "noPublishedRelease" in SHARE and "failed = true" in SHARE
assert "versionCode = royalPlusVersionCode" in GRADLE
assert 'minSdk = 23' in GRADLE
assert WORKFLOW.count('-ProyalplusVersionCode="$ROYALPLUS_VERSION_CODE"') >= 2
assert "ROYALPLUS_VERSION_CODE: ${{ github.event_name == 'push' && github.ref == 'refs/heads/main'" in WORKFLOW
assert 'gh release create "$TAG"' in WORKFLOW and '--latest' in WORKFLOW
print("Verified: all settings categories, retained controls, QR/share actions, official-release-only checks, and dynamic production version codes.")
