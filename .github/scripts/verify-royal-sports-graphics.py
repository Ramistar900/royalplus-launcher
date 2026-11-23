#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/royalplus/launcher"
GRAPHICS = JAVA / "RoyalSportsGraphics.kt"
SCREEN = JAVA / "RoyalSportsScreen.kt"
RANKINGS = JAVA / "RoyalSportsRankings.kt"
HOME = JAVA / "RoyalHomeScreen.kt"
WORKFLOW = ROOT / ".github/workflows/build-apk.yml"
GRADLE = ROOT / "app/build.gradle.kts"

for path in (GRAPHICS, SCREEN, RANKINGS, HOME, WORKFLOW, GRADLE):
    assert path.is_file(), f"Required sports graphics file is missing: {path.relative_to(ROOT)}"

graphics = GRAPHICS.read_text(encoding="utf-8")
screen = SCREEN.read_text(encoding="utf-8")
home = HOME.read_text(encoding="utf-8")
workflow = WORKFLOW.read_text(encoding="utf-8")
gradle = GRADLE.read_text(encoding="utf-8")

# Sports artwork is now drawn by native Compose primitives, not the V20 asset pack.
for required in (
    "enum class RoyalSportsGlyph",
    "drawSoccerBall()",
    "drawCalendar()",
    "drawFixtureList()",
    "drawLiveBoard()",
    "drawTrophy()",
    "drawTeams()",
    "drawGoal()",
    "drawRefresh()",
    "Canvas(Modifier.fillMaxSize())",
):
    assert required in graphics, f"Missing native sports artwork: {required}"

# The launcher-side football emblem rotates smoothly and independently.
assert "rememberInfiniteTransition" in graphics
assert "infiniteRepeatable" in graphics
assert "LinearEasing" in graphics
assert "rotate(degrees = rotationDegrees, pivot = Offset(32f, 32f))" in graphics
assert "rotationDegrees = rotation" in graphics
assert "durationMillis = 2800" in graphics
assert "sportsBall = true" in home
assert "royal_sports_luxe" in home
assert "ROYAL SPORTS" in home
assert (ROOT / "app/src/main/res/drawable/royal_sports_luxe.xml").is_file()

# Competition headings separate and sort fixtures into clear football-league groups.
assert "groupBy { Triple(it.leagueId, it.country.trim(), it.league.trim()) }" in screen
assert 'round = league.optString("round", "")' in screen
assert 'leg = fixture.optString("leg", item.optString("leg", ""))' in screen
assert "private fun localizedFixtureLeg" in screen
assert "SportsMatchDetailsPage(" in screen
assert "localizedFootballRound" in screen
for tab in ("LIVE", "UPCOMING", "FINISHED", "RANKINGS"):
    assert "RoyalSportsTab." + tab in screen
rankings = (JAVA / "RoyalSportsRankings.kt").read_text(encoding="utf-8")
assert "NATIONAL_TEAMS" in rankings
assert "DOMESTIC_LEAGUES" in rankings
assert "INTERNATIONAL_GROUPS" in rankings
assert "RoyalSportsRankingRepository.loadFifaTop100" in rankings
assert "RoyalSportsRankingRepository.loadStandings" in rankings
assert "isRelegationZone" in rankings
assert "val isArchived: Boolean = false" in rankings
assert rankings.count("isArchived = true") == 5
assert " • Archive" in rankings
assert "RoyalSportsMatchBucket.LIVE" in screen
assert "RoyalSportsMatchBucket.NOT_STARTED" in screen
assert "RoyalSportsMatchBucket.PLAYED" in screen
assert "leagueLogo = league.optString(\"logo\", \"\")" in screen
assert "countryFlag = league.optString(\"flag\", \"\")" in screen
assert "homeLogo = home.optString(\"logo\", \"\")" in screen
assert "awayLogo = away.optString(\"logo\", \"\")" in screen
assert "RoyalSportsProviderImage(" in screen
assert "media.api-sports.io" in screen
assert "api.fifa.com" in screen
assert "connection.contentType" in screen
assert '"image/svg+xml"' in screen
assert "bounds.outWidth <= 0 || bounds.outHeight <= 0" in screen
assert "SVG.getFromString" in screen
assert "SportsPitchGlassBackground" in graphics
assert "drawCircle(line" in graphics and "drawRect(line" in graphics
assert "Brush.verticalGradient" in screen
assert "RoyalSportsIconId" not in screen
assert "sortedBy { entry -> entry.value.firstOrNull()?.date.orEmpty() }" in screen
assert "SportsLeagueHeader(" in screen
for glyph in ("FOOTBALL", "TODAY_MATCHES", "FIXTURES", "LIVE", "RESULTS", "TEAMS", "GOAL", "REFRESH"):
    assert "RoyalSportsGlyph." + glyph in screen or "RoyalSportsGlyph." + glyph in graphics

# Old V20 source, runtime assets, and build-time download/verification steps are no longer used.
for forbidden in ("RoyalV20SportsIcon", "RoyalV20SportsIconId", "sports_v20", "prepare-v20-sports-icons.py", "verify-v20-sports-icons.py"):
    assert forbidden not in graphics + screen + home + workflow, f"Legacy sports icon reference remains: {forbidden}"
assert "verify-royal-sports-graphics.py" in workflow
assert "com.caverock:androidsvg-aar:1.4" in gradle

print("Verified native football and match illustrations, smooth rotating sidebar ball, grouped fixtures, and removal of V20 sports assets.")
