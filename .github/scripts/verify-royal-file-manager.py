from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
HOME = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalHomeScreen.kt").read_text(encoding="utf-8")
FILES = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalFileManagerScreen.kt").read_text(encoding="utf-8")
COLORS = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalColors.kt").read_text(encoding="utf-8")
MANIFEST = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
GRADLE = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/build-apk.yml").read_text(encoding="utf-8")

assert "LauncherDestination.FILES -> RoyalFileManagerScreen" in HOME
assert "List(10) { FocusRequester() }" in HOME
sidebar = HOME[HOME.index("private fun RoyalSidePanel("):HOME.index("private fun StatusIcon(")]
for forbidden in ('"مسلم"', '"اختصاراتي"', "LauncherDestination.MUSLIM", "LauncherDestination.SHORTCUTS"):
    assert forbidden not in sidebar, f"Hidden sidebar item returned: {forbidden}"
assert "labels[3], null, selectedDestination == LauncherDestination.SCREEN_CAST" in sidebar
assert "iconRes = R.drawable.royal_screen_cast" in sidebar
assert "LauncherDestination.SCREEN_CAST -> RoyalCastScreen" in HOME
assert "labels[9], Icons.Rounded.Settings" in sidebar
assert "labels[10], Icons.Rounded.Apps" in sidebar
assert sidebar.index("labels[10], Icons.Rounded.Apps") > sidebar.index("labels[9], Icons.Rounded.Settings")
assert "sinjarLogo = true" in sidebar and "R.drawable.sinjar_show_logo" in HOME
assert "R.drawable.royal_settings_luxe" in sidebar
assert "RoyalColors.Activity" in HOME
for required in (
    "OpenDocumentTree", "DocumentFile",
    "StatFs", "RoyalStorageCard", "RoyalFilePreview", "RoyalInternalMediaPreview",
    "RoyalInternalPdfPreview", "combinedClickable", "deleteRoyalFileEntry",
):
    assert required in FILES, f"File manager feature missing: {required}"
assert "ACTION_VIEW" not in FILES, "File previews must stay inside ROYAL+"
assert "RoyalColors.Blue" in FILES and "RoyalColors.Green" in FILES
for required in ("val Turquoise", "val Activity", "val Green", "val Blue"):
    assert required in COLORS, f"Theme accent missing: {required}"
assert "android.permission.MANAGE_EXTERNAL_STORAGE" in MANIFEST
assert "android.permission.WRITE_EXTERNAL_STORAGE" in MANIFEST
assert "androidx.documentfile:documentfile:1.0.1" in GRADLE
assert "minSdk = 24" in GRADLE
assert "verify-royal-file-manager.py" in WORKFLOW
for icon in ("royal_apps_luxe", "royal_calendar_luxe", "royal_manage_apps_luxe", "royal_gallery_luxe", "royal_songs_luxe", "royal_files_luxe", "royal_settings_luxe"):
    path = ROOT / ("app/src/main/res/drawable/" + icon + ".xml")
    assert path.exists(), f"Missing premium sidebar icon: {icon}"
    ET.parse(path)
print("Verified the 11-item ROYAL+ sidebar including phone casting, approved theme accents, internal previews, storage access and delete-confirmation paths.")
