#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPORTS = ROOT / "app/src/main/java/com/royalplus/launcher/RoyalSportsScreen.kt"
RANKINGS = ROOT / "app/src/main/java/com/royalplus/launcher/RoyalSportsRankings.kt"
ALERTS = ROOT / "app/src/main/java/com/royalplus/launcher/RoyalSportsAlerts.kt"
HOME = ROOT / "app/src/main/java/com/royalplus/launcher/RoyalHomeScreen.kt"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
BACKEND = ROOT / "supabase/functions/royalplus-sports/index.ts"
CACHE_MIGRATION = ROOT / "supabase/migrations/20261112043947_royalplus_sports_response_cache.sql"
PUBLIC_RPC_MIGRATION = ROOT / "supabase/migrations/20261112045800_royalplus_sports_public_budget_rpc.sql"
QUOTA_MIGRATION = ROOT / "supabase/migrations/20261120100000_royalplus_sports_daily_budget_100.sql"
APP_GRADLE = ROOT / "app/build.gradle.kts"

for path in (SPORTS, RANKINGS, ALERTS, HOME, MANIFEST, BACKEND, CACHE_MIGRATION, PUBLIC_RPC_MIGRATION, QUOTA_MIGRATION, APP_GRADLE):
    assert path.is_file(), f"Required file is missing: {path.relative_to(ROOT)}"

sports = SPORTS.read_text(encoding="utf-8")
alerts = ALERTS.read_text(encoding="utf-8")
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
quota_migration = QUOTA_MIGRATION.read_text(encoding="utf-8")
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
assert "sportsBall = true" in home and "royal_sports_luxe" in home
assert "var selectedTab by remember { mutableStateOf(RoyalSportsTab.UPCOMING) }" in sports
assert "Icons.Rounded.SportsSoccer" not in home
assert "ACCESS_NETWORK_STATE" in manifest and "android.permission.INTERNET" in manifest
assert 'android.permission.POST_NOTIFICATIONS' in manifest
assert "minSdk = 24" in gradle
assert "minSdk = 23" not in gradle
assert 'androidx.work:work-runtime-ktx:2.9.1' in gradle

# Server-side authentication, global budget, query bounds and cache must remain enforced.
assert "admin.auth.getUser(token)" in backend
assert "consume_royalplus_sports_request" in backend
assert '.rpc("consume_royalplus_sports_request")' in backend
assert '.schema("private")' not in backend
assert "royalplus_sports_response_cache" in backend
assert "if (cacheKey.length > 512)" in backend
assert "const cacheKey = `${resource}:${view || \"default\"}:${canonicalQuery}`" in backend
assert "API_SPORTS_KEY" in backend and "x-apisports-key" in backend
assert "verify_jwt" not in backend  # Gateway verification is configured as true at deployment.
assert "fifa_rankings" in backend
assert 'view === "upcoming" || params.has("next")' in backend
assert 'view === "finished" || params.has("last")' in backend
assert 'view === "live" && params.get("live") !== "all" && !params.has("date")' in backend
assert 'resource === "fixtures" && (view === "upcoming" || view === "finished" || params.has("from") || params.has("to"))' in backend
assert 'RoyalSportsTab.LIVE -> "date=$today&$timeZoneParam"' in sports
assert 'RoyalSportsTab.UPCOMING -> "from=$today&to=${dateOffset(7)}&$timeZoneParam"' in sports
assert 'RoyalSportsTab.FINISHED -> "from=${dateOffset(-7)}&to=$today&$timeZoneParam"' in sports
assert "loadFixtures(context, RoyalSportsTab.LIVE)" in alerts
assert "loadFixtures(context, RoyalSportsTab.UPCOMING)" in alerts
assert "const dayMs = 24 * 60 * 60 * 1000" in backend
assert "const maxWindowMs = 7 * dayMs" in backend
assert 'view === "upcoming" ? 1 : 8' in backend
assert 'view === "finished" ? 1 : 8' in backend
assert "toTime - fromTime > maxWindowMs" in backend
assert "fixture_view_requires_current_seven_day_window" in backend
assert 'incoming.searchParams.get("view")' in backend
assert 'if (cacheKey.length > 512)' in backend
assert 'key === "resource" || key === "view"' in backend
assert 'cacheKey = `${resource}:${view || "default"}:${canonicalQuery}`' in backend
assert "rankingschedules/all" in backend
assert "rankingsbyschedule" in backend
assert "https://api.fifa.com/api/v3/picture/flags-sq-2/" in backend
assert "countryCode = /^[A-Z]{3}$/.test(code)" in backend
assert "resource === \"standings\"" in backend
assert "resource !== \"fifa_rankings\"" in backend
assert "limit: 100" in backend and "limit: 90" not in backend
assert "100 - v_rolling_count" in quota_migration
assert "v_rolling_count < 100 and v_daily_count < 100 and v_minute_count < 8" in quota_migration
assert "'limit', 100" in quota_migration

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
assert 'leg = fixture.optString("leg", item.optString("leg", ""))' in sports
assert '"date=$today&$timeZoneParam"' in sports
assert '"from=$today&to=${dateOffset(7)}&$timeZoneParam"' in sports
assert '"from=${dateOffset(-7)}&to=$today&$timeZoneParam"' in sports
assert "private fun dateOffset(days: Int)" in sports
assert "import java.util.Calendar" in sports
assert "RoyalSportsFavoriteTeam" in alerts
assert "RoyalSportsFavorites" in alerts
assert "loadAlertSettings" in alerts
assert "matchReminders" in alerts and "goalAlerts" in alerts and "resultAlerts" in alerts
assert "PeriodicWorkRequestBuilder<RoyalSportsAlertsWorker>" in alerts
assert "POLL_INTERVAL_MINUTES = 30L" in alerts
assert "OneTimeWorkRequestBuilder<RoyalSportsMatchReminderWorker>" in alerts
assert "setInitialDelay(delayMillis" in alerts
assert "processFinishedResults" in alerts and "processLiveScores" in alerts
assert "homeScored = previousHome != null && home > previousHome" in alerts
assert "awayScored = previousAway != null && away > previousAway" in alerts
assert "Your favorite team's match is starting soon" in alerts
assert "Your favorite team's match starts in 30 minutes" in alerts
assert "NotificationChannel" in alerts and "POST_NOTIFICATIONS" in alerts
assert "RoyalSportsAlertsScheduler.refresh(this)" in (ROOT / "app/src/main/java/com/royalplus/launcher/RoyalPlusApplication.kt").read_text(encoding="utf-8")
assert 'homeTeamId = home.optInt("id", 0)' in sports and 'awayTeamId = away.optInt("id", 0)' in sports
assert "favoriteTeamIds" in sports and "SportsFavoriteTeamToggle" in sports
assert ".clickable { onOpenDetails(fixture) }" in sports
assert "SportsMatchDetailsPage(" in sports
assert "BackHandler(enabled = true)" in sports
assert "SportsMatchDetailLine" in sports
assert 'venue = fixture.optJSONObject("venue")' in sports
assert 'venueName = venue.optString("name", "")' in sports
assert 'venueCity = venue.optString("city", "")' in sports
assert 'leg = fixture.optString("leg", item.optString("leg", ""))' in sports
assert 'timeZone = TimeZone.getDefault()' in sports
assert "private fun localizedCompetitionWeek" in sports
assert "private fun localizedFixtureLeg" in sports
assert "Opening this file sends no additional API request." in sports
assert "The current fixture provider does not supply channel names for this match" in sports
assert "SportsFavoritesDialog" in sports and "onToggleAlert" in sports
assert "مباراة فريقك المفضل بعد 30 دقيقة" in alerts
assert "localizedFootballRound" in sports
assert "localizedFootballLeagueName(leagueName, countryName, arabic, turkish)" in sports
assert 'isSyria && isPremierLeague' in sports
assert '"الدوري السوري الممتاز"' in sports

rankings = RANKINGS.read_text(encoding="utf-8")
assert "favoriteTeamIds: Set<Int>" in rankings
assert "onToggleFavoriteTeam: (RoyalSportsFavoriteTeam) -> Unit" in rankings
assert "RoyalSportsFavoriteTeam(entry.teamId, entry.team, entry.logo)" in rankings
assert "isFavorite = entry.teamId in favoriteTeamIds" in rankings
assert "if (isFavorite) \"★\" else \"☆\"" in rankings
for required in (
    "NATIONAL_TEAMS", "DOMESTIC_LEAGUES", "INTERNATIONAL_GROUPS",
    "loadFifaTop100", "loadStandings", "take(100)",
    "FIFA World Ranking", "relegation", "RoyalSportsProviderImage",
):
    assert required.lower() in rankings.lower(), f"Missing rankings feature: {required}"
assert "calculateLocalStandingsMovement" in rankings
assert "return synchronized(preferences)" in rankings
assert '"royalplus_sports_standings"' in rankings
assert "oldRank - currentRank" in rankings
assert '"_movements"' in rankings
assert "reminderSentForKickoff" in alerts and "markReminderSent" in alerts
assert "calculateStandingMovement" in rankings
# Keep the requested Saudi league and verify the Syrian league's API-Football v3 ID.
assert 'RoyalSportsRankingCompetition(307, "Saudi Pro League", SEASON, "Saudi Arabia")' in rankings
assert 'RoyalSportsRankingCompetition(425, "Premier League", SEASON, "Syria")' in rankings
assert 'RoyalSportsRankingCompetition(5, "UEFA Nations League", SEASON, "Europe")' in rankings
assert 'key.contains("nations league") -> "دوري الأمم الأوروبية"' in rankings
assert 'key.contains("nations league") -> "UEFA Uluslar Ligi"' in rankings
assert 'Regex("""(?i)\\bleague\\s+([a-d])(?:\\s*[-–]?\\s*group\\s*(\\d+))?\\b""")' in rankings
assert "Lig $league • Grup $groupNumber" in rankings
assert "الدوري $league • المجموعة $groupNumber" in rankings
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
