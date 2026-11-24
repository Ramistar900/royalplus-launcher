from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HOME = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalHomeScreen.kt").read_text(encoding="utf-8")
ACTIVITY = (ROOT / "app/src/main/java/com/royalplus/launcher/MainActivity.kt").read_text(encoding="utf-8")
ROLE = (ROOT / "app/src/main/java/com/royalplus/launcher/HomeRoleCompat.kt").read_text(encoding="utf-8")

# The visible home-screen control must invoke the callback supplied by MainActivity.
assert ".clickable { onChooseDefaultHome() }" in HOME
assert "onChooseDefaultHome = { openDefaultHomeSettings() }" in ACTIVITY

# Android 10+ uses RoleManager; older Android versions fall back to system HOME settings.
assert "HomeRoleCompat.openDefaultHomeSettings(this)" in ACTIVITY
assert "Settings.ACTION_HOME_SETTINGS" in ACTIVITY
assert "Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS" in ACTIVITY
assert "Settings.ACTION_SETTINGS" in ACTIVITY
assert "RoleManager.ROLE_HOME" in ROLE
assert "roleManager.isRoleAvailable(RoleManager.ROLE_HOME)" in ROLE
assert "roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)" in ROLE
assert "context.startActivity(intent)" in ROLE

print("Verified default launcher button wiring, Android HOME role request, and legacy settings fallbacks.")
