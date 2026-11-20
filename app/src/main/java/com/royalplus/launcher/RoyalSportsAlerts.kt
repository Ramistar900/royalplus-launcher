package com.royalplus.launcher

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

internal data class RoyalSportsFavoriteTeam(
    val id: Int,
    val name: String,
    val logo: String
)

internal data class RoyalSportsAlertSettings(
    val matchReminders: Boolean = false,
    val goalAlerts: Boolean = false,
    val resultAlerts: Boolean = false
) {
    val anyEnabled: Boolean
        get() = matchReminders || goalAlerts || resultAlerts
}

internal object RoyalSportsFavorites {
    private const val PREFS = "royalplus_sports_favorites"
    private const val KEY_TEAMS = "teams_json"
    private const val KEY_MATCH_REMINDERS = "match_reminders"
    private const val KEY_GOALS = "goal_alerts"
    private const val KEY_RESULTS = "result_alerts"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_STATE_PREFIX = "fixture_state_"
    private val lock = Any()

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<RoyalSportsFavoriteTeam> {
        val raw = preferences(context).getString(KEY_TEAMS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optInt("id", 0)
                    val name = item.optString("name", "").trim()
                    if (id > 0 && name.isNotBlank()) {
                        add(
                            RoyalSportsFavoriteTeam(
                                id = id,
                                name = name,
                                logo = item.optString("logo", "")
                            )
                        )
                    }
                }
            }.distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    fun toggle(context: Context, team: RoyalSportsFavoriteTeam): List<RoyalSportsFavoriteTeam> =
        synchronized(lock) {
            val current = load(context).toMutableList()
            val existing = current.indexOfFirst { it.id == team.id }
            if (existing >= 0) {
                current.removeAt(existing)
            } else if (team.id > 0 && team.name.isNotBlank() && current.size < 32) {
                current.add(team)
            }
            saveTeams(context, current)
            current.toList()
        }

    fun remove(context: Context, teamId: Int): List<RoyalSportsFavoriteTeam> =
        synchronized(lock) {
            val updated = load(context).filterNot { it.id == teamId }
            saveTeams(context, updated)
            updated
        }

    private fun saveTeams(context: Context, teams: List<RoyalSportsFavoriteTeam>) {
        val array = JSONArray()
        teams.forEach { team ->
            array.put(
                JSONObject()
                    .put("id", team.id)
                    .put("name", team.name)
                    .put("logo", team.logo)
            )
        }
        preferences(context).edit().putString(KEY_TEAMS, array.toString()).apply()
    }

    fun loadAlertSettings(context: Context): RoyalSportsAlertSettings {
        val prefs = preferences(context)
        return RoyalSportsAlertSettings(
            matchReminders = prefs.getBoolean(KEY_MATCH_REMINDERS, false),
            goalAlerts = prefs.getBoolean(KEY_GOALS, false),
            resultAlerts = prefs.getBoolean(KEY_RESULTS, false)
        )
    }

    fun saveAlertSettings(context: Context, settings: RoyalSportsAlertSettings) {
        preferences(context).edit()
            .putBoolean(KEY_MATCH_REMINDERS, settings.matchReminders)
            .putBoolean(KEY_GOALS, settings.goalAlerts)
            .putBoolean(KEY_RESULTS, settings.resultAlerts)
            .apply()
    }

    fun setLanguage(context: Context, language: String) {
        preferences(context).edit().putString(KEY_LANGUAGE, language).apply()
    }

    fun language(context: Context): String =
        preferences(context).getString(KEY_LANGUAGE, Locale.getDefault().language) ?: "ar"

    fun previousFixtureState(context: Context, fixtureId: Long): String? =
        preferences(context).getString(KEY_STATE_PREFIX + fixtureId, null)

    fun saveFixtureState(context: Context, fixtureId: Long, status: String, homeGoals: Int, awayGoals: Int) {
        preferences(context).edit()
            .putString(KEY_STATE_PREFIX + fixtureId, "$status|$homeGoals|$awayGoals")
            .apply()
    }
}

internal object RoyalSportsAlertsScheduler {
    private const val PERIODIC_WORK = "royalplus-sports-favorite-alerts"
    private const val INITIAL_WORK = "royalplus-sports-favorite-alerts-initial"
    internal const val REMINDER_TAG = "royalplus-sports-match-reminders"
    private const val POLL_INTERVAL_MINUTES = 30L

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        val manager = WorkManager.getInstance(appContext)
        val hasFavorites = RoyalSportsFavorites.load(appContext).isNotEmpty()
        val hasAlerts = RoyalSportsFavorites.loadAlertSettings(appContext).anyEnabled
        if (!hasFavorites || !hasAlerts) {
            manager.cancelUniqueWork(PERIODIC_WORK)
            manager.cancelUniqueWork(INITIAL_WORK)
            manager.cancelAllWorkByTag(REMINDER_TAG)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<RoyalSportsAlertsWorker>(
            POLL_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
        manager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )

        // Run once after enabling alerts or changing favorites; periodic work then
        // keeps reminders and score snapshots current without a foreground service.
        manager.cancelAllWorkByTag(REMINDER_TAG)
        val initial = OneTimeWorkRequestBuilder<RoyalSportsAlertsWorker>()
            .setConstraints(constraints)
            .build()
        manager.enqueueUniqueWork(
            INITIAL_WORK,
            ExistingWorkPolicy.REPLACE,
            initial
        )
    }
}

internal class RoyalSportsAlertsWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val favorites = RoyalSportsFavorites.load(context)
        val settings = RoyalSportsFavorites.loadAlertSettings(context)
        if (favorites.isEmpty() || !settings.anyEnabled) return Result.success()
        if (!RoyalSportsNotificationCenter.canPost(context)) return Result.success()

        return try {
            if (settings.goalAlerts || settings.resultAlerts) {
                // One shared current-day fixture query drives both goal and final-result
                // alerts, reusing the same 20-minute server cache as the Live tab.
                val today = RoyalSportsRepository.loadFixtures(context, RoyalSportsTab.LIVE)
                if (!today.stale) {
                    if (settings.resultAlerts) {
                        processFinishedResults(context, today.fixtures, favorites)
                    }
                    processLiveScores(
                        context,
                        today.fixtures,
                        favorites,
                        sendGoalAlerts = settings.goalAlerts
                    )
                }
            }

            if (settings.matchReminders) {
                val upcoming = RoyalSportsRepository.loadFixtures(context, RoyalSportsTab.UPCOMING)
                if (!upcoming.stale) scheduleUpcomingReminders(context, upcoming.fixtures, favorites)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun processLiveScores(
        context: Context,
        fixtures: List<RoyalSportsFixture>,
        favorites: List<RoyalSportsFavoriteTeam>,
        sendGoalAlerts: Boolean
    ) {
        val favoriteIds = favorites.mapTo(HashSet()) { it.id }
        fixtures.forEach { fixture ->
            if (fixture.homeTeamId !in favoriteIds && fixture.awayTeamId !in favoriteIds) return@forEach
            val home = fixture.homeGoals ?: return@forEach
            val away = fixture.awayGoals ?: return@forEach
            val status = fixture.status.uppercase(Locale.ROOT)
            val previous = RoyalSportsFavorites.previousFixtureState(context, fixture.id)
            val previousParts = previous?.split('|')
            val previousHome = previousParts?.getOrNull(1)?.toIntOrNull()
            val previousAway = previousParts?.getOrNull(2)?.toIntOrNull()

            val homeScored = previousHome != null && home > previousHome
            val awayScored = previousAway != null && away > previousAway
            if (
                sendGoalAlerts &&
                (homeScored || awayScored) &&
                status in LIVE_STATUSES
            ) {
                val scoringTeam = when {
                    homeScored && awayScored -> fixture.homeTeam + " / " + fixture.awayTeam
                    homeScored -> fixture.homeTeam
                    awayScored -> fixture.awayTeam
                    else -> ""
                }
                val language = RoyalSportsFavorites.language(context)
                val message = if (scoringTeam.isNotBlank()) {
                    localized(
                        language,
                        "$scoringTeam سجّل • النتيجة $home : $away",
                        "$scoringTeam gol attı • Skor $home : $away",
                        "$scoringTeam scored • Score $home : $away"
                    )
                } else {
                    localized(
                        language,
                        "تغيرت النتيجة • $home : $away",
                        "Skor değişti • $home : $away",
                        "The score changed • $home : $away"
                    )
                }
                RoyalSportsNotificationCenter.show(
                    context,
                    goalNotificationId(fixture.id, home, away),
                    localized(language, "هدف لفريقك المفضل!", "Favori takımın gol attı!", "Favorite team goal!"),
                    message
                )
            }
            RoyalSportsFavorites.saveFixtureState(context, fixture.id, status, home, away)
        }
    }

    private fun processFinishedResults(
        context: Context,
        fixtures: List<RoyalSportsFixture>,
        favorites: List<RoyalSportsFavoriteTeam>
    ) {
        val favoriteById = favorites.associateBy { it.id }
        fixtures.forEach { fixture ->
            val homeFavorite = favoriteById[fixture.homeTeamId]
            val awayFavorite = favoriteById[fixture.awayTeamId]
            if (homeFavorite == null && awayFavorite == null) return@forEach
            val status = fixture.status.uppercase(Locale.ROOT)
            if (status !in FINISHED_STATUSES) return@forEach
            val home = fixture.homeGoals ?: return@forEach
            val away = fixture.awayGoals ?: return@forEach

            val previous = RoyalSportsFavorites.previousFixtureState(context, fixture.id)
            val previousStatus = previous?.substringBefore('|').orEmpty()
            if (previousStatus.isNotBlank() && previousStatus !in FINISHED_STATUSES) {
                val language = RoyalSportsFavorites.language(context)
                val outcome = listOfNotNull(homeFavorite, awayFavorite).joinToString(" / ") { favorite ->
                    val won = if (favorite.id == fixture.homeTeamId) home > away else away > home
                    val lost = if (favorite.id == fixture.homeTeamId) home < away else away < home
                    val result = when {
                        home == away -> localized(language, "تعادل", "Berabere", "Draw")
                        won -> localized(language, "فاز", "Kazandı", "Won")
                        lost -> localized(language, "خسر", "Kaybetti", "Lost")
                        else -> ""
                    }
                    (result + " " + favorite.name).trim()
                }
                RoyalSportsNotificationCenter.show(
                    context,
                    resultNotificationId(fixture.id),
                    localized(language, "النتيجة النهائية لفريقك", "Favori takımının maç sonucu", "Favorite team final result"),
                    "$outcome • $home : $away"
                )
            }
            RoyalSportsFavorites.saveFixtureState(context, fixture.id, status, home, away)
        }
    }

    private fun scheduleUpcomingReminders(
        context: Context,
        fixtures: List<RoyalSportsFixture>,
        favorites: List<RoyalSportsFavoriteTeam>
    ) {
        val favoriteIds = favorites.mapTo(HashSet()) { it.id }
        val now = System.currentTimeMillis()
        val manager = WorkManager.getInstance(context)
        manager.cancelAllWorkByTag(RoyalSportsAlertsScheduler.REMINDER_TAG)

        fixtures.forEach { fixture ->
            if (fixture.status.uppercase(Locale.ROOT) !in setOf("NS", "TBD")) return@forEach
            if (fixture.homeTeamId !in favoriteIds && fixture.awayTeamId !in favoriteIds) return@forEach
            val kickoff = parseFixtureDate(fixture.date) ?: return@forEach
            if (kickoff <= now + 60_000L || kickoff > now + TimeUnit.DAYS.toMillis(8)) return@forEach
            val reminderAt = kickoff - TimeUnit.MINUTES.toMillis(30)
            val delayMillis = (reminderAt - now).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<RoyalSportsMatchReminderWorker>()
                .setInputData(
                    workDataOf(
                        "fixture_id" to fixture.id,
                        "home_team_id" to fixture.homeTeamId,
                        "away_team_id" to fixture.awayTeamId,
                        "home_team" to fixture.homeTeam,
                        "away_team" to fixture.awayTeam,
                        "kickoff_at" to kickoff
                    )
                )
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .addTag(RoyalSportsAlertsScheduler.REMINDER_TAG)
                .build()
            manager.enqueueUniqueWork(
                "royalplus-match-reminder-" + fixture.id,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    private fun parseFixtureDate(value: String): Long? = runCatching {
        val normalized = value
            .replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
            .replace(Regex("Z$"), "+0000")
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ssZ")
        patterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }.parse(normalized)?.time
            }.getOrNull()
        }
    }.getOrNull()

    private fun goalNotificationId(fixtureId: Long, home: Int, away: Int): Int =
        (fixtureId xor (home.toLong() shl 16) xor away.toLong()).toInt() and Int.MAX_VALUE

    private fun resultNotificationId(fixtureId: Long): Int =
        (fixtureId xor 0x5A5A5A5AL).toInt() and Int.MAX_VALUE

    companion object {
        private val LIVE_STATUSES = setOf("1H", "2H", "ET", "LIVE", "HT", "BT", "P", "SUSP", "INT")
        private val FINISHED_STATUSES = setOf("FT", "AET", "PEN", "AWD", "WO")
    }
}

internal class RoyalSportsMatchReminderWorker(
    context: Context,
    parameters: WorkerParameters
) : Worker(context, parameters) {
    override fun doWork(): Result {
        val context = applicationContext
        val settings = RoyalSportsFavorites.loadAlertSettings(context)
        if (!settings.matchReminders || !RoyalSportsNotificationCenter.canPost(context)) return Result.success()

        val fixtureId = inputData.getLong("fixture_id", 0L)
        val homeId = inputData.getInt("home_team_id", 0)
        val awayId = inputData.getInt("away_team_id", 0)
        val favoriteIds = RoyalSportsFavorites.load(context).mapTo(HashSet()) { it.id }
        if (fixtureId <= 0L || (homeId !in favoriteIds && awayId !in favoriteIds)) return Result.success()

        val home = inputData.getString("home_team").orEmpty()
        val away = inputData.getString("away_team").orEmpty()
        val kickoff = inputData.getLong("kickoff_at", 0L)
        if (kickoff <= 0L) return Result.success()

        val language = RoyalSportsFavorites.language(context)
        val minutesUntilKickoff = TimeUnit.MILLISECONDS.toMinutes(kickoff - System.currentTimeMillis())
        val title = if (minutesUntilKickoff <= 30L) {
            localized(
                language,
                "مباراة فريقك المفضل ستبدأ قريباً",
                "Favori takımının maçı yakında başlayacak",
                "Your favorite team's match is starting soon"
            )
        } else {
            localized(
                language,
                "مباراة فريقك المفضل بعد 30 دقيقة",
                "Favori takımının maçı 30 dakika sonra",
                "Your favorite team's match starts in 30 minutes"
            )
        }
        val details = if (System.currentTimeMillis() >= kickoff) {
            localized(language, "موعد مباراة: $home × $away", "Maç başladı: $home × $away", "Match time: $home × $away")
        } else {
            "$home × $away • " + formatKickoffTime(kickoff)
        }
        RoyalSportsNotificationCenter.show(
            context,
            (fixtureId xor 0x3C3C3C3CL).toInt() and Int.MAX_VALUE,
            title,
            details
        )
        return Result.success()
    }

    private fun formatKickoffTime(value: Long): String = runCatching {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(value))
    }.getOrDefault("")
}

internal object RoyalSportsNotificationCenter {
    private const val CHANNEL_ID = "royalplus_favorite_football_alerts"

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun show(context: Context, notificationId: Int, title: String, message: String) {
        if (!canPost(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureChannel(context, manager)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }
        builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .setCategory(Notification.CATEGORY_EVENT)
            .setPriority(Notification.PRIORITY_HIGH)
        manager.notify(notificationId, builder.build())
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val language = RoyalSportsFavorites.language(context)
        val channel = NotificationChannel(
            CHANNEL_ID,
            localized(language, "تنبيهات الفرق المفضلة", "Favori takım bildirimleri", "Favorite team alerts"),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = localized(
                language,
                "مواعيد المباريات والأهداف والنتائج النهائية لفرقك المفضلة",
                "Favori takımların maç saatleri, golleri ve sonuçları",
                "Match reminders, goals and final results for your favorite teams"
            )
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }
}

private fun localized(language: String, arabic: String, turkish: String, english: String): String =
    when (language.lowercase(Locale.ROOT)) {
        "ar" -> arabic
        "tr" -> turkish
        else -> english
    }
