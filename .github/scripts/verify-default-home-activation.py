from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HOME = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalHomeScreen.kt").read_text(encoding="utf-8")
ACTIVITY = (ROOT / "app/src/main/java/com/royalplus/launcher/MainActivity.kt").read_text(encoding="utf-8")
ROLE = (ROOT / "app/src/main/java/com/royalplus/launcher/HomeRoleCompat.kt").read_text(encoding="utf-8")

assert ".clickable { onChooseDefaultHome() }" in HOME
assert "onChooseDefaultHome = { openDefaultHomeSettings() }" in ACTIVITY
assert "HomeRoleCompat.openDefaultHomeSettings(this)" in ACTIVITY
assert "packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)" in ACTIVITY
assert "Intent.createChooser(homeIntent" in ACTIVITY
assert "RoleManager.ROLE_HOME" in ROLE
assert "roleManager.isRoleAvailable(RoleManager.ROLE_HOME)" in ROLE
assert "roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)" in ROLE
assert "تعذر فتح اختيار اللانشر تلقائياً" in ACTIVITY
print("Verified launcher activation click wiring and Android HOME role request.")
