package com.royalplus.launcher

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private enum class RoyalSportsRankingKind {
    NATIONAL_TEAMS, DOMESTIC_LEAGUES, INTERNATIONAL_GROUPS
}

private data class RoyalSportsRankingCompetition(
    val id: Int,
    val name: String,
    val season: Int,
    val country: String
) {
    val logoUrl: String
        get() = "https://media.api-sports.io/football/leagues/$id.png"
}

private data class RoyalSportsFifaTeam(
    val rank: Int,
    val country: String,
    val code: String,
    val points: Double,
    val movement: Int?,
    val previousRank: Int?,
    val flagUrl: String?
)

private data class RoyalSportsStandingEntry(
    val rank: Int,
    val teamId: Int,
    val team: String,
    val logo: String,
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalsDiff: Int,
    val points: Int,
    val description: String,
    val movement: Int?
) {
    val isRelegationZone: Boolean
        get() = description.contains("relegation", ignoreCase = true) ||
            description.contains("relegated", ignoreCase = true)
}

private data class RoyalSportsStandingGroup(
    val name: String,
    val entries: List<RoyalSportsStandingEntry>
)

private data class RoyalSportsStandings(
    val league: String,
    val leagueLogo: String,
    val season: Int,
    val updatedAt: String,
    val groups: List<RoyalSportsStandingGroup>
)

private object RoyalSportsRankingCatalog {
    private const val SEASON = 2026

    val domesticLeagues = listOf(
        RoyalSportsRankingCompetition(39, "Premier League", SEASON, "England"),
        RoyalSportsRankingCompetition(140, "La Liga", SEASON, "Spain"),
        RoyalSportsRankingCompetition(135, "Serie A", SEASON, "Italy"),
        RoyalSportsRankingCompetition(78, "Bundesliga", SEASON, "Germany"),
        RoyalSportsRankingCompetition(61, "Ligue 1", SEASON, "France"),
        RoyalSportsRankingCompetition(203, "Süper Lig", SEASON, "Türkiye"),
        RoyalSportsRankingCompetition(307, "Saudi Pro League", SEASON, "Saudi Arabia"),
        // API-Football v3 league 425; 2026 season is listed with Syrian standings.
        RoyalSportsRankingCompetition(425, "Premier League", SEASON, "Syria")
    )

    val internationalCompetitions = listOf(
        RoyalSportsRankingCompetition(1, "FIFA World Cup", 2026, "International"),
        RoyalSportsRankingCompetition(2, "UEFA Champions League", SEASON, "Europe"),
        RoyalSportsRankingCompetition(3, "UEFA Europa League", SEASON, "Europe"),
        RoyalSportsRankingCompetition(4, "UEFA European Championship", 2024, "Europe"),
        RoyalSportsRankingCompetition(6, "Africa Cup of Nations", 2025, "Africa"),
        RoyalSportsRankingCompetition(9, "Copa América", 2024, "South America"),
        RoyalSportsRankingCompetition(17, "AFC Asian Cup", 2023, "Asia")
    )
}

private object RoyalSportsRankingRepository {
    suspend fun loadFifaTop100(context: Context): Pair<List<RoyalSportsFifaTeam>, String> =
        withContext(Dispatchers.IO) {
            val envelope = RoyalSportsRepository.loadSportsJson(
                context,
                "resource=fifa_rankings&gender=1",
                "fifa_men_top100"
            )
            val data = envelope.optJSONObject("data") ?: envelope
            val rows = data.optJSONArray("response") ?: JSONArray()
            val teams = ArrayList<RoyalSportsFifaTeam>(rows.length())
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val rank = row.optInt("rank", 0)
                if (rank !in 1..100) continue
                teams += RoyalSportsFifaTeam(
                    rank = rank,
                    country = row.optString("country", "").trim(),
                    code = row.optString("code", "").trim().uppercase(Locale.ROOT),
                    points = row.optDouble("points", 0.0),
                    movement = if (row.has("movement") && !row.isNull("movement")) row.optInt("movement") else null,
                    previousRank = if (row.has("previousRank") && !row.isNull("previousRank")) row.optInt("previousRank") else null,
                    flagUrl = row.optString("flag", "").takeIf { it.startsWith("https://") }
                )
            }
            val published = data.optString("rankingDate", "")
            teams.sortedBy { it.rank }.take(100) to published
        }

    suspend fun loadStandings(
        context: Context,
        competition: RoyalSportsRankingCompetition
    ): RoyalSportsStandings = withContext(Dispatchers.IO) {
        val envelope = RoyalSportsRepository.loadSportsJson(
            context,
            "resource=standings&league=${competition.id}&season=${competition.season}",
            "standings_${competition.id}_${competition.season}"
        )
        val data = envelope.optJSONObject("data") ?: envelope
        val response = data.optJSONArray("response") ?: JSONArray()
        val result = response.optJSONObject(0) ?: JSONObject()
        val league = result.optJSONObject("league") ?: JSONObject()
        val standings = league.optJSONArray("standings") ?: JSONArray()
        val groups = ArrayList<RoyalSportsStandingGroup>(standings.length())

        for (groupIndex in 0 until standings.length()) {
            val table = standings.optJSONArray(groupIndex) ?: continue
            val entries = ArrayList<RoyalSportsStandingEntry>(table.length())
            var groupName = ""
            for (rowIndex in 0 until table.length()) {
                val row = table.optJSONObject(rowIndex) ?: continue
                val team = row.optJSONObject("team") ?: JSONObject()
                val all = row.optJSONObject("all") ?: JSONObject()
                val goals = all.optJSONObject("goals") ?: JSONObject()
                val rank = row.optInt("rank", rowIndex + 1)
                val teamId = team.optInt("id", 0)
                val teamName = team.optString("name", "—")
                // API-Football standings do not provide a previous-rank movement field.
                // Keep movement unknown rather than inventing arrows from local fetch timing.
                val movement: Int? = null
                if (groupName.isBlank()) {
                    groupName = row.optString("group", "")
                }
                entries += RoyalSportsStandingEntry(
                    rank = rank,
                    teamId = teamId,
                    team = teamName,
                    logo = team.optString("logo", ""),
                    played = all.optInt("played", 0),
                    wins = all.optInt("win", 0),
                    draws = all.optInt("draw", 0),
                    losses = all.optInt("lose", 0),
                    goalsFor = goals.optInt("for", 0),
                    goalsAgainst = goals.optInt("against", 0),
                    goalsDiff = row.optInt("goalsDiff", 0),
                    points = row.optInt("points", 0),
                    description = row.optString("description", ""),
                    movement = movement
                )
            }
            if (entries.isNotEmpty()) {
                val fallbackName = if (standings.length() > 1) {
                    "Group ${('A'.code + groupIndex).toChar()}"
                } else {
                    "Overall standings"
                }
                groups += RoyalSportsStandingGroup(
                    name = groupName.ifBlank { fallbackName },
                    entries = entries.sortedBy { it.rank }
                )
            }
        }

        RoyalSportsStandings(
            league = league.optString("name", competition.name),
            leagueLogo = league.optString("logo", competition.logoUrl),
            season = league.optInt("season", competition.season),
            updatedAt = envelope.optJSONObject("cache")?.optString("cached_at", "").orEmpty(),
            groups = groups
        )
    }
}

@Composable
internal fun RoyalSportsRankingsScreen(language: String, refreshSequence: Int) {
    val context = LocalContext.current.applicationContext
    val arabic = language == "ar"
    val turkish = language == "tr"
    var selectedKind by remember { mutableStateOf(RoyalSportsRankingKind.NATIONAL_TEAMS) }
    var selectedCompetitionId by remember { mutableStateOf(RoyalSportsRankingCatalog.domesticLeagues.first().id) }
    var fifaTeams by remember { mutableStateOf<List<RoyalSportsFifaTeam>>(emptyList()) }
    var fifaDate by remember { mutableStateOf("") }
    var standings by remember { mutableStateOf<RoyalSportsStandings?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val competitions = when (selectedKind) {
        RoyalSportsRankingKind.DOMESTIC_LEAGUES -> RoyalSportsRankingCatalog.domesticLeagues
        RoyalSportsRankingKind.INTERNATIONAL_GROUPS -> RoyalSportsRankingCatalog.internationalCompetitions
        RoyalSportsRankingKind.NATIONAL_TEAMS -> emptyList()
    }
    val selectedCompetition = competitions.firstOrNull { it.id == selectedCompetitionId }
        ?: competitions.firstOrNull()

    LaunchedEffect(selectedKind, selectedCompetitionId, refreshSequence) {
        loading = true
        error = null
        try {
            when (selectedKind) {
                RoyalSportsRankingKind.NATIONAL_TEAMS -> {
                    val result = RoyalSportsRankingRepository.loadFifaTop100(context)
                    fifaTeams = result.first
                    fifaDate = result.second
                }
                RoyalSportsRankingKind.DOMESTIC_LEAGUES,
                RoyalSportsRankingKind.INTERNATIONAL_GROUPS -> {
                    val competition = selectedCompetition ?: return@LaunchedEffect
                    standings = RoyalSportsRankingRepository.loadStandings(context, competition)
                }
            }
        } catch (failure: Exception) {
            error = when (failure) {
                is RoyalSportsHttpException -> failure.errorCode
                else -> "ranking_data_unavailable"
            }
        } finally {
            loading = false
        }
    }

    val labels = when {
        arabic -> listOf("المنتخبات", "الدوريات", "المجموعات")
        turkish -> listOf("Milli takımlar", "Ligler", "Gruplar")
        else -> listOf("National teams", "Leagues", "Groups")
    }
    val emptyText = when {
        arabic -> "لا توجد بيانات تصنيف متاحة لهذه البطولة حالياً"
        turkish -> "Bu yarışma için şu anda sıralama verisi yok"
        else -> "No ranking data is currently available for this competition"
    }
    val categoryOptions = listOf(
        RoyalSportsRankingKind.NATIONAL_TEAMS,
        RoyalSportsRankingKind.DOMESTIC_LEAGUES,
        RoyalSportsRankingKind.INTERNATIONAL_GROUPS
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categoryOptions.forEachIndexed { index, kind ->
                RankingCategoryButton(
                    label = labels[index],
                    selected = selectedKind == kind,
                    modifier = Modifier.weight(1f)
                ) {
                    selectedKind = kind
                    when (kind) {
                        RoyalSportsRankingKind.NATIONAL_TEAMS -> Unit
                        RoyalSportsRankingKind.DOMESTIC_LEAGUES ->
                            selectedCompetitionId = RoyalSportsRankingCatalog.domesticLeagues.first().id
                        RoyalSportsRankingKind.INTERNATIONAL_GROUPS ->
                            selectedCompetitionId = RoyalSportsRankingCatalog.internationalCompetitions.first().id
                    }
                }
            }
        }

        if (selectedKind != RoyalSportsRankingKind.NATIONAL_TEAMS) {
            Spacer(Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(competitions, key = { it.id }) { competition ->
                    CompetitionChoice(
                        competition = competition,
                        selected = selectedCompetitionId == competition.id,
                        arabic = arabic,
                        turkish = turkish,
                        onClick = { selectedCompetitionId = competition.id }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        when {
            selectedKind == RoyalSportsRankingKind.NATIONAL_TEAMS -> {
                RankingSectionHeading(
                    title = if (arabic) "تصنيف FIFA العالمي • أفضل 100 منتخب" else if (turkish) "FIFA Dünya Sıralaması • İlk 100" else "FIFA World Ranking • Top 100",
                    subtitle = if (fifaDate.isNotBlank()) fifaDate else if (arabic) "أحدث إصدار رسمي متاح" else "Latest available official edition"
                )
                if (fifaTeams.isEmpty()) {
                    RankingEmptyState(
                loading, error, emptyText,
                if (arabic) "جارٍ تحميل التصنيفات..." else if (turkish) "Sıralamalar yükleniyor..." else "Loading rankings...",
                if (arabic) "تعذر تحميل بيانات التصنيف" else if (turkish) "Sıralama verileri yüklenemedi" else "Ranking data could not be loaded"
            )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(fifaTeams, key = { "${it.rank}-${it.code}" }) { team ->
                            FifaRankingRow(team)
                        }
                    }
                }
            }
            else -> {
                val current = standings
                val selectedName = selectedCompetition?.name.orEmpty()
                RankingSectionHeading(
                    title = localizedRankingCompetitionName(selectedName, selectedCompetition?.country.orEmpty(), arabic, turkish),
                    subtitle = if (arabic) "ترتيب الفرق والنقاط ومناطق التأهل والهبوط" else if (turkish) "Takım sıralaması, puanlar ve düşme hattı" else "Team table, points, qualification and relegation zones"
                )
                if (current == null || current.groups.isEmpty()) {
                    RankingEmptyState(
                        loading, error, emptyText,
                        if (arabic) "جارٍ تحميل التصنيفات..." else if (turkish) "Sıralamalar yükleniyor..." else "Loading rankings...",
                        if (arabic) "تعذر تحميل بيانات التصنيف" else if (turkish) "Sıralama verileri yüklenemedi" else "Ranking data could not be loaded"
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        current.groups.forEachIndexed { groupIndex, group ->
                            item(key = "standing-group-${groupIndex}-${group.name}") {
                                Text(
                                    localizedRankingGroupName(group.name, arabic, turkish),
                                    color = RoyalColors.Gold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xAA164A39))
                                        .border(1.dp, Color(0x558EE6B4), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                            item(key = "standing-head-$groupIndex") {
                                StandingsColumnHeader(arabic, turkish)
                            }
                            items(group.entries, key = { "$groupIndex-${it.teamId}-${it.rank}" }) { entry ->
                                StandingsEntryRow(entry, arabic, turkish)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingCategoryButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier.clip(shape)
            .background(if (selected) Color(0xCC1B6848) else Color(0x77204435))
            .border(1.dp, if (selected) Color(0xFF9BF1B8) else Color(0x448EE6B4), shape)
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 5.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (selected) RoyalColors.Gold else RoyalColors.Pearl,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompetitionChoice(
    competition: RoyalSportsRankingCompetition,
    selected: Boolean,
    arabic: Boolean,
    turkish: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier.clip(shape)
            .background(if (selected) Color(0xCC1B6848) else Color(0x88304A3D))
            .border(1.dp, if (selected) Color(0xFF9BF1B8) else Color(0x448EE6B4), shape)
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RoyalSportsProviderImage(
            url = competition.logoUrl,
            modifier = Modifier.size(23.dp),
            fallback = RoyalSportsGlyph.RESULTS,
            description = competition.name
        )
        Text(
            localizedRankingCompetitionName(competition.name, competition.country, arabic, turkish),
            color = if (selected) RoyalColors.Gold else RoyalColors.Pearl,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun RankingSectionHeading(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x80204435))
            .border(1.dp, Color(0x448EE6B4), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(title, color = RoyalColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(subtitle, color = RoyalColors.Muted, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun RankingEmptyState(
    loading: Boolean,
    error: String?,
    emptyText: String,
    loadingText: String,
    errorText: String
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            when {
                loading -> loadingText
                error != null -> "$errorText ($error)"
                else -> emptyText
            },
            color = if (error != null) Color(0xFFFFB4A9) else RoyalColors.Pearl,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun FifaRankingRow(team: RoyalSportsFifaTeam) {
    val movementText = when {
        team.movement == null || team.movement == 0 -> "—"
        team.movement > 0 -> "▲ ${team.movement}"
        else -> "▼ ${-team.movement}"
    }
    val movementColor = when {
        team.movement == null || team.movement == 0 -> RoyalColors.Muted
        team.movement > 0 -> Color(0xFF62E6A4)
        else -> Color(0xFFFF7777)
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0x99203F34))
            .border(1.dp, Color(0x335BD5B0), RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(team.rank.toString(), color = RoyalColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
        Box(
            modifier = Modifier.size(width = 36.dp, height = 23.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF274D42)),
            contentAlignment = Alignment.Center
        ) {
            if (!team.flagUrl.isNullOrBlank() && RoyalSportsProviderImageCache.isAllowed(team.flagUrl)) {
                RoyalSportsProviderImage(team.flagUrl, Modifier.fillMaxSize(), RoyalSportsGlyph.TEAMS, team.country)
            } else {
                Text(team.code.take(3), color = RoyalColors.Pearl, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            team.country,
            color = RoyalColors.Pearl,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(movementText, color = movementColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
        Text(String.format(Locale.US, "%.2f", team.points), color = Color(0xFFB7F5F1), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(58.dp))
    }
}

@Composable
private fun StandingsColumnHeader(arabic: Boolean, turkish: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xAA122F27))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (arabic) "م" else "Pos", color = RoyalColors.Gold, fontSize = 8.sp, modifier = Modifier.width(25.dp))
        Text(if (arabic) "النادي" else "Club", color = RoyalColors.Gold, fontSize = 8.sp, modifier = Modifier.weight(1f))
        Text(if (arabic) "لعب" else if (turkish) "O" else "P", color = RoyalColors.Gold, fontSize = 8.sp, modifier = Modifier.width(28.dp))
        Text(if (arabic) "ف-ت-خ" else if (turkish) "G-B-M" else "W-D-L", color = RoyalColors.Gold, fontSize = 8.sp, modifier = Modifier.width(48.dp))
        Text("+/-", color = RoyalColors.Gold, fontSize = 8.sp, modifier = Modifier.width(32.dp))
        Text(if (arabic) "نقاط" else if (turkish) "Puan" else "Pts", color = RoyalColors.Gold, fontSize = 8.sp, modifier = Modifier.width(30.dp))
    }
}

@Composable
private fun StandingsEntryRow(
    entry: RoyalSportsStandingEntry,
    arabic: Boolean,
    turkish: Boolean
) {
    val movementText = when {
        entry.movement == null || entry.movement == 0 -> "—"
        entry.movement > 0 -> "▲"
        else -> "▼"
    }
    val movementColor = when {
        (entry.movement ?: 0) < 0 -> Color(0xFFFF7777)
        (entry.movement ?: 0) > 0 -> Color(0xFF62E6A4)
        else -> RoyalColors.Muted
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (entry.isRelegationZone) Color(0x66A52E38) else Color(0x99203F34))
            .border(1.dp, if (entry.isRelegationZone) Color(0x77FF6E73) else Color(0x223DD1A0), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(entry.rank.toString(), color = RoyalColors.Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(25.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            RoyalSportsProviderImage(
                url = entry.logo,
                modifier = Modifier.size(20.dp),
                fallback = RoyalSportsGlyph.TEAMS,
                description = entry.team
            )
            Text(
                entry.team,
                color = RoyalColors.Pearl,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(entry.played.toString(), color = RoyalColors.Pearl, fontSize = 9.sp, modifier = Modifier.width(28.dp))
        Text("${entry.wins}-${entry.draws}-${entry.losses}", color = RoyalColors.Pearl, fontSize = 8.sp, modifier = Modifier.width(48.dp))
        Text(
            if (entry.isRelegationZone) "●" else movementText,
            color = if (entry.isRelegationZone) Color(0xFFFF7777) else movementColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp)
        )
        Text(entry.points.toString(), color = RoyalColors.Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
    }
}

private fun localizedRankingGroupName(value: String, arabic: Boolean, turkish: Boolean): String {
    val normalized = value.lowercase(Locale.ROOT)
    if (arabic) {
        return when {
            normalized.contains("group a") -> "المجموعة A"
            normalized.contains("group b") -> "المجموعة B"
            normalized.contains("group c") -> "المجموعة C"
            normalized.contains("group d") -> "المجموعة D"
            normalized.contains("group e") -> "المجموعة E"
            normalized.contains("group f") -> "المجموعة F"
            normalized.contains("overall") || normalized.contains("league") -> "الترتيب العام / مرحلة الدوري"
            normalized.contains("relegation") -> "مجموعة الهبوط"
            else -> value
        }
    }
    if (turkish) {
        return when {
            normalized.contains("group") -> value.replace("Group", "Grup")
            normalized.contains("overall") -> "Genel sıralama"
            else -> value
        }
    }
    return value
}


private fun localizedRankingCompetitionName(value: String, country: String, arabic: Boolean, turkish: Boolean): String {
    val key = value.lowercase(Locale.ROOT)
    val isSyria = country.equals("Syria", ignoreCase = true)
    if (arabic) return when {
        isSyria && key.contains("premier league") -> "الدوري السوري الممتاز"
        key.contains("premier league") -> "الدوري الإنجليزي الممتاز"
        key.contains("la liga") -> "الدوري الإسباني"
        key.contains("serie a") -> "الدوري الإيطالي"
        key.contains("bundesliga") -> "الدوري الألماني"
        key.contains("ligue 1") -> "الدوري الفرنسي"
        key.contains("süper lig") -> "الدوري التركي الممتاز"
        key.contains("saudi pro") -> "الدوري السعودي للمحترفين"
        key.contains("world cup") -> "كأس العالم"
        key.contains("champions league") -> "دوري أبطال أوروبا"
        key.contains("europa league") -> "الدوري الأوروبي"
        key.contains("european championship") -> "بطولة أمم أوروبا"
        key.contains("africa cup") -> "كأس أمم إفريقيا"
        key.contains("copa américa") -> "كوبا أمريكا"
        key.contains("asian cup") -> "كأس آسيا"
        else -> value
    }
    if (turkish) return when {
        isSyria && key.contains("premier league") -> "Suriye Premier Ligi"
        key.contains("premier league") -> "İngiltere Premier Ligi"
        key.contains("la liga") -> "İspanya La Liga"
        key.contains("serie a") -> "İtalya Serie A"
        key.contains("bundesliga") -> "Almanya Bundesliga"
        key.contains("world cup") -> "Dünya Kupası"
        key.contains("champions league") -> "Şampiyonlar Ligi"
        key.contains("european championship") -> "Avrupa Şampiyonası"
        else -> value
    }
    return value
}
