from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HOME = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalHomeScreen.kt").read_text(encoding="utf-8")
SHARE = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalShareUpdatesSection.kt").read_text(encoding="utf-8")
GRADLE = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/build-apk.yml").read_text(encoding="utf-8")

for category in (
    "appearance", "motion", "icons", "sounds", "prayer",
    "clock", "language", "device", "privacy", "share", "about",
):
    assert f'if (selectedSettingsCategory == "{category}")' in HOME, (
        f"Settings content is not isolated for category: {category}"
    )

assert HOME.index("val homeWallpapers = listOf(") < HOME.index(
    'if (selectedSettingsCategory == "appearance")'
), "Shared wallpaper choices must remain available to the screensaver category"

for existing_setting in (
    "adhan_enabled", "motion_home", "launcher_wallpaper",
    "ui_click_sounds_enabled", "screensaver_delay_seconds",
    "launcher_icon_style", "launcher_24_hour_clock",
    "launcher_show_date", "launcher_language", "launcher_font",
    "ACTION_WIFI_SETTINGS", "RoyalDeviceSettingsSection", "launcher_theme_mode",
    "launcher_text_scale", "RoyalPrivacySettingsSection", "RoyalAboutSettingsSection",
    "onChooseDefaultHome",
):
    assert existing_setting in HOME, f"Existing setting was removed: {existing_setting}"

assert "MultiFormatWriter" in SHARE and "BarcodeFormat.QR_CODE" in SHARE
assert "releases/latest" in SHARE and "api.github.com/repos/Ramistar900/royalplus-launcher/releases/latest" in SHARE
assert "royalplus-(\\\\d+)$" in SHARE
assert "Intent.ACTION_SEND" in SHARE
assert "noPublishedRelease" in SHARE and "failed = true" in SHARE
DEVICE_SETTINGS = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalDeviceSettingsSection.kt").read_text(encoding="utf-8")
assert "showDeviceSettings: Boolean = true" in DEVICE_SETTINGS
assert "Clear ROYAL+ cache" in DEVICE_SETTINGS
assert "showAudioSettings: Boolean = true" in DEVICE_SETTINGS
assert "showDeviceSettings = false" in HOME and "showAudioSettings = true" in HOME
assert "showDeviceSettings = true" in HOME and "showAudioSettings = false" in HOME
COLORS = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalColors.kt").read_text(encoding="utf-8")
MAIN = (ROOT / "app/src/main/java/com/royalplus/launcher/MainActivity.kt").read_text(encoding="utf-8")
PRIVACY_ABOUT = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalPrivacyAboutSettingsSection.kt").read_text(encoding="utf-8")
assert "RoyalColors.applyLightTheme" in MAIN and "RoyalAppTheme" in MAIN
assert "lightColorScheme" in COLORS and "darkColorScheme" in COLORS
assert "canDrawOverlays" in PRIVACY_ABOUT and "canScheduleExactAlarms" in PRIVACY_ABOUT
assert "versionName" in PRIVACY_ABOUT and "Ramistar900/royalplus-launcher" in PRIVACY_ABOUT
assert "launcher_text_scale" in HOME and "LocalDensity provides Density" in HOME
assert "launcher_theme_mode" in HOME and "RoyalColors.Surface" in HOME
assert "versionCode = royalPlusVersionCode" in GRADLE
assert 'minSdk = 24' in GRADLE
assert 'minSdk = 23' not in GRADLE
PROGUARD = (ROOT / "app/proguard-rules.pro").read_text(encoding="utf-8")
assert "-keep class com.royalplus.launcher.RoyalHomeScreenKt$RoyalHomeScreen$17$1$3 { *; }" in PROGUARD, "Keep the Android 7 verifier-failing Compose lambda out of R8 optimization"
assert WORKFLOW.count('-ProyalplusVersionCode="$ROYALPLUS_VERSION_CODE"') >= 2
assert "ROYALPLUS_VERSION_CODE: ${{ github.event_name == 'push' && github.ref == 'refs/heads/main'" in WORKFLOW
assert 'gh release create "$TAG"' in WORKFLOW and '--latest' in WORKFLOW
# Settings remains reachable from the top bar but is not a sidebar destination.
sidebar_start = HOME.index("val labels = when (language)")
sidebar_end = HOME.index("@Composable\\nprivate fun StatusIcon", sidebar_start)
sidebar = HOME[sidebar_start:sidebar_end]
assert "Manage apps" in sidebar and "Settings" not in sidebar
assert "royal_settings_luxe" not in sidebar
assert "onOpenSettings = { showWallpaperSettings = true }" in HOME

print("Verified: all settings categories, retained controls, QR/share actions, official-release-only checks, and dynamic production version codes.")
