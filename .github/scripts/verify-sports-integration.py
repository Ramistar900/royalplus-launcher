#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPORTS = ROOT / "app/src/main/java/com/royalplus/launcher/RoyalSportsScreen.kt"
HOME = ROOT / "app/src/main/java/com/royalplus/launcher/RoyalHomeScreen.kt"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
BACKEND = ROOT / "supabase/functions/royalplus-sports/index.ts"
CACHE_MIGRATION = ROOT / "supabase/migrations/20261112043947_royalplus_sports_response_cache.sql"
APP_GRADLE = ROOT / "app/build.gradle.kts"

for path in (SPORTS, HOME, MANIFEST, BACKEND, CACHE_MIGRATION, APP_GRADLE):
    assert path.is_file(), f"Required file is missing: {path.relative_to(ROOT)}"

sports = SPORTS.read_text(encoding="utf-8")
home = HOME.read_text(encoding="utf-8")
manifest = MANIFEST.read_text(encoding="utf-8")
backend = BACKEND.read_text(encoding="utf-8")
migration = CACHE_MIGRATION.read_text(encoding="utf-8")
gradle = APP_GRADLE.read_text(encoding="utf-8")

# The mobile app must contain only the public Supabase client key.
for forbidden in ("API_SPORTS_KEY", "SUPABASE_SERVICE_ROLE_KEY", "service_role", "x-apisports-key", "v3.football.api-sports.io"):
    assert forbidden not in sports, f"Server-only credential/provider detail leaked into app source: {forbidden}"
assert "sb_publishable_" in sports
assert "AndroidKeyStore" in sports and "AES/GCM/NoPadding" in sports
assert "/auth/v1/signup" in sports and "refresh_token" in sports
assert "/functions/v1/royalplus-sports" in sports

# Sports must be a first-party launcher destination; weather, audio and wallpapers are not replaced.
assert "SPORTS" in home and "RoyalSportsScreen(language = selectedLanguage)" in home
assert "Icons.Rounded.SportsSoccer" in home
assert "ACCESS_NETWORK_STATE" in manifest and "android.permission.INTERNET" in manifest
assert "minSdk = 23" in gradle

# Server-side authentication, global budget, query bounds and cache must remain enforced.
assert "admin.auth.getUser(token)" in backend
assert "consume_royalplus_sports_request" in backend
assert "royalplus_sports_response_cache" in backend
assert "canonicalQuery.length > 512" in backend
assert "API_SPORTS_KEY" in backend and "x-apisports-key" in backend
assert "verify_jwt" not in backend  # Gateway verification is configured as true at deployment.

# The response cache is private to the service role.
assert "enable row level security" in migration.lower()
assert "revoke all" in migration.lower()
assert "grant select, insert, update, delete" in migration.lower()

print("ROYAL+ sports security, backend cache, launcher navigation and Android compatibility guards passed.")
