#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPORTS = ROOT / "app/src/main/java/com/royalplus/launcher/RoyalSportsScreen.kt"
RANKINGS = ROOT / "app/src/main/java/com/royalplus/launcher/RoyalSportsRankings.kt"
HOME = ROOT / "app/src/main/java/com/royalplus/launcher/RoyalHomeScreen.kt"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
BACKEND = ROOT / "supabase/functions/royalplus-sports/index.ts"
CACHE_MIGRATION = ROOT / "supabase/migrations/20261112043947_royalplus_sports_response_cache.sql"
PUBLIC_RPC_MIGRATION = ROOT / "supabase/migrations/20261112045800_royalplus_sports_public_budget_rpc.sql"
APP_GRADLE = ROOT / "app/build.gradle.kts"

for path in (SPORTS, RANKINGS, HOME, MANIFEST, BACKEND, CACHE_MIGRATION, PUBLIC_RPC_MIGRATION, APP_GRADLE):
    assert path.is_file(), f"Required file is missing: {path.relative_to(ROOT)}"

sports = SPORTS.read_text(encoding="utf-8")
home = HOME.read_text(encoding="utf-8")

# The only user-facing sports category is football; both match tabs remain.
assert "LIVE, UPCOMING, FINISHED, RANKINGS" in sports
assert 'arabic -> "كرة القدم"' in sports
assert 'turkish -> "FUTBOL"' in sports and 'else -> "FOOTBALL"' in sports
assert "RoyalSportsGlyph.FOOTBALL" in sports
assert "RoyalSportsGlyph.FIXTURES" in sports
assert "RoyalSportsGlyph.LIVE" in sports
assert "RoyalSportsGlyph.RESULTS" in sports
assert "RoyalSportsTab.LIVE" in sports
assert "RoyalSportsTab.UPCOMING" in sports
assert "RoyalSportsTab.FINISHED" in sports
assert "RoyalSportsTab.RANKINGS" in sports
assert "RoyalSportsMatchBucket.LIVE" in sports
assert "RoyalSportsMatchBucket.NOT_STARTED" in sports
assert "RoyalSportsMatchBucket.PLAYED" in sports
assert "media.api-sports.io" in sports
assert "api.fifa.com" in sports
assert "connection.contentType" in sports
assert '"image/svg+xml"' in sports
assert "bounds.outWidth <= 0 || bounds.outHeight <= 0" in sports
assert "leagueLogo = league.optString(\"logo\", \"\")" in sports
assert "countryFlag = league.optString(\"flag\", \"\")" in sports
assert "homeLogo = home.optString(\"logo\", \"\")" in sports
assert "awayLogo = away.optString(\"logo\", \"\")" in sports
assert "Icons.Rounded.SportsSoccer" not in sports
for other_sport in ("Basketball", "Tennis", "Volleyball", "Cricket", "Baseball"):
    assert other_sport.lower() not in sports.lower(), f"Unexpected sport category: {other_sport}"
assert "v3.football.api-sports.io" in (ROOT / "supabase/functions/royalplus-sports/index.ts").read_text(encoding="utf-8")
assert '"/standings"' in (ROOT / "supabase/functions/royalplus-sports/index.ts").read_text(encoding="utf-8") or '"standings"' in (ROOT / "supabase/functions/royalplus-sports/index.ts").read_text(encoding="utf-8")
manifest = MANIFEST.read_text(encoding="utf-8")
backend = BACKEND.read_text(encoding="utf-8")
migration = CACHE_MIGRATION.read_text(encoding="utf-8")
public_rpc_migration = PUBLIC_RPC_MIGRATION.read_text(encoding="utf-8")
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
assert "sportsBall = true" in home and "RoyalRotatingFootballIcon" in home
assert "Icons.Rounded.SportsSoccer" not in home
assert "ACCESS_NETWORK_STATE" in manifest and "android.permission.INTERNET" in manifest
assert "minSdk = 23" in gradle

# Server-side authentication, global budget, query bounds and cache must remain enforced.
assert "admin.auth.getUser(token)" in backend
assert "consume_royalplus_sports_request" in backend
assert '.rpc("consume_royalplus_sports_request")' in backend
assert '.schema("private")' not in backend
assert "royalplus_sports_response_cache" in backend
assert "canonicalQuery.length + resource.length + 1 > 512" in backend
assert "API_SPORTS_KEY" in backend and "x-apisports-key" in backend
assert "verify_jwt" not in backend  # Gateway verification is configured as true at deployment.
assert "fifa_rankings" in backend
assert "params.has(\"next\") || params.has(\"from\") || params.has(\"to\")) return 3 * 60 * 60" in backend
assert "params.has(\"last\")) return 6 * 60 * 60" in backend
assert "rankingschedules/all" in backend
assert "rankingsbyschedule" in backend
assert "https://api.fifa.com/api/v3/picture/flags-sq-2/" in backend
assert "countryCode = /^[A-Z]{3}$/.test(code)" in backend
assert "resource === \"standings\"" in backend
assert "resource !== \"fifa_rankings\"" in backend

# The response cache is private to the service role.
assert "enable row level security" in migration.lower()
assert "revoke all" in migration.lower()
assert "grant select, insert, update, delete" in migration.lower()
assert "security invoker" in public_rpc_migration.lower()
assert "grant execute" in public_rpc_migration.lower()

graphics = (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalSportsGraphics.kt").read_text(encoding="utf-8")
assert "rememberInfiniteTransition" in graphics and "rotationDegrees = rotation" in graphics
assert "groupBy { Triple(it.leagueId, it.country.trim(), it.league.trim()) }" in sports
assert 'round = league.optString("round", "")' in sports
assert 'leg = ""' in sports
assert 'next=99&$timeZoneParam' in sports
assert 'last=99&$timeZoneParam' in sports
assert "localizedFootballRound" in sports
assert "localizedFootballLeagueName(leagueName, countryName, arabic, turkish)" in sports
assert 'isSyria && isPremierLeague' in sports
assert '"الدوري السوري الممتاز"' in sports

rankings = RANKINGS.read_text(encoding="utf-8")
for required in (
    "NATIONAL_TEAMS", "DOMESTIC_LEAGUES", "INTERNATIONAL_GROUPS",
    "loadFifaTop100", "loadStandings", "take(100)",
    "FIFA World Ranking", "relegation", "RoyalSportsProviderImage",
):
    assert required.lower() in rankings.lower(), f"Missing rankings feature: {required}"
assert "royalplus_sports_standings_movement" not in rankings
assert "API-Football standings do not provide a previous-rank movement field" in rankings
# Keep the requested Saudi league and verify the Syrian league's API-Football v3 ID.
assert 'RoyalSportsRankingCompetition(307, "Saudi Pro League", SEASON, "Saudi Arabia")' in rankings
assert 'RoyalSportsRankingCompetition(425, "Premier League", SEASON, "Syria")' in rankings
assert 'RoyalSportsRankingCompetition(307, "Saudi Pro League", SEASON, "Saudi Arabia")' in rankings
assert "val isArchived: Boolean = false" in rankings
assert rankings.count("isArchived = true") == 5
assert "selectedCompetition?.isArchived == true" in rankings
assert " • أرشيف" in rankings
assert rankings.index('RoyalSportsRankingCompetition(2, "UEFA Champions League"') < rankings.index('RoyalSportsRankingCompetition(1, "FIFA World Cup"')
assert 'isSyria && key.contains("premier league") -> "الدوري السوري الممتاز"' in rankings
assert 'isSyria && key.contains("premier league") -> "Suriye Premier Ligi"' in rankings
assert "topScorers" not in rankings and "topAssists" not in rankings
assert "RoyalV20SportsIcon" not in sports + home + graphics
assert "SportsPitchGlassBackground" in graphics
assert "RoyalSportsIconId" not in sports
print("ROYAL+ sports security, native graphics, grouped fixtures, rotating navigation ball and Android compatibility guards passed.")
