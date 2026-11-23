from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HOME = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalHomeScreen.kt").read_text(encoding="utf-8")
ACTIVITY = (ROOT / "app/src/main/java/com/royalplus/launcher/MainActivity.kt").read_text(encoding="utf-8")
ROLE = (ROOT / "app/src/main/java/com/royalplus/launcher/HomeRoleCompat.kt").read_text(encoding="utf-8")

assert ".clickable { onChooseDefaultHome() }" in HOME, "Default launcher card is not wired to its callback"
assert "onChooseDefaultHome = { openDefaultHomeSettings() }" in ACTIVITY, "Activity callback is not connected"
assert "HomeRoleCompat.openDefaultHomeSettings(this)" in ACTIVITY
assert "packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)" in ACTIVITY
assert "Intent.createChooser(homeIntent" in ACTIVITY, "Missing HOME chooser fallback"
assert "RoleManager.ROLE_HOME" in ROLE
assert "roleManager.isRoleAvailable(RoleManager.ROLE_HOME)" in ROLE
assert "roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)" in ROLE
assert "تعذر فتح اختيار اللانشر تلقائياً" in ACTIVITY
print("Verified launcher activation click wiring, Android HOME role request, settings resolution, chooser fallback, and visible failure guidance.")
