package com.royalplus.launcher

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import com.caverock.androidsvg.SVG

private const val SPORTS_SUPABASE_URL = "https://ymujjcdsdclptbpgckll.supabase.co"
private const val SPORTS_SUPABASE_PUBLISHABLE_KEY = "sb_publishable_rltTQCQ_SlGkj_eRecMVaQ_gu-Sw_bW"

/**
 * Only official images returned by API-Sports are accepted. The in-memory cache
 * keeps these small illustrations local after the first successful fetch.
 */
internal object RoyalSportsProviderImageCache {
    private const val MAX_IMAGE_BYTES = 512 * 1024
    private const val EDGE_PX = 128
    private const val MAX_CACHE_BYTES = 3 * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun isAllowed(url: String): Boolean = runCatching {
        val uri = URI(url)
        val secure = uri.scheme.equals("https", ignoreCase = true)
        val apiSportsImage = uri.host.equals("media.api-sports.io", ignoreCase = true) &&
            (uri.path.startsWith("/football/leagues/") ||
                uri.path.startsWith("/football/teams/") ||
                uri.path.startsWith("/flags/")) &&
            (uri.path.endsWith(".png", ignoreCase = true) || uri.path.endsWith(".svg", ignoreCase = true))
        val fifaFlag = uri.host.equals("api.fifa.com", ignoreCase = true) &&
            uri.path.matches(Regex("/api/v3/picture/flags-sq-2/[A-Z]{3}"))
        secure && (apiSportsImage || fifaFlag)
    }.getOrDefault(false)

    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        if (!isAllowed(url)) return@withContext null
        cache.get(url)?.let { return@withContext it }

        var connection: HttpURLConnection? = null
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 4_000
            connection.readTimeout = 4_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "image/png,image/svg+xml")
            connection.setRequestProperty("User-Agent", "ROYAL-Launcher/1.0")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

            // FIFA's flag endpoint has no file extension, so detect SVG/raster
            // format from the actual HTTP Content-Type instead of the URL suffix.
            val contentType = connection.contentType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase(Locale.ROOT)
                .orEmpty()
            if (!contentType.startsWith("image/")) return@withContext null

            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_IMAGE_BYTES) {
                        throw IOException("provider_image_too_large")
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            if (bytes.isEmpty()) return@withContext null

            val isSvg = contentType == "image/svg+xml" ||
                url.substringBefore('?').endsWith(".svg", ignoreCase = true)
            val bitmap = if (isSvg) {
                val svg = SVG.getFromString(String(bytes, Charsets.UTF_8))
                svg.setDocumentWidth(EDGE_PX.toFloat())
                svg.setDocumentHeight(EDGE_PX.toFloat())
                Bitmap.createBitmap(EDGE_PX, EDGE_PX, Bitmap.Config.ARGB_8888).also {
                    svg.renderToCanvas(AndroidCanvas(it))
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
                val largestEdge = maxOf(bounds.outWidth, bounds.outHeight)
                val sample = (largestEdge / EDGE_PX).coerceAtLeast(1)
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
            if (bitmap != null) cache.put(url, bitmap)
            bitmap
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}

@Composable
internal fun RoyalSportsProviderImage(
    url: String?,
    modifier: Modifier = Modifier,
    fallback: RoyalSportsGlyph,
    description: String
) {
    val safeUrl = url?.takeIf { RoyalSportsProviderImageCache.isAllowed(it) }
    var image by remember(safeUrl) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(safeUrl) {
        image = if (safeUrl == null) null else {
            RoyalSportsProviderImageCache.load(safeUrl)?.asImageBitmap()
        }
    }
    if (image != null) {
        Image(
            bitmap = image!!,
            contentDescription = description,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        RoyalSportsIcon(
            icon = fallback,
            modifier = modifier,
            contentDescription = description
        )
    }
}

internal enum class RoyalSportsTab {
    LIVE, UPCOMING, FINISHED, RANKINGS
}

internal data class RoyalSportsFixture(
    val id: Long,
    val league: String,
    val country: String,
    val leagueLogo: String,
    val countryFlag: String,
    val homeTeamId: Int,
    val homeTeam: String,
    val homeLogo: String,
    val awayTeamId: Int,
    val awayTeam: String,
    val awayLogo: String,
    val homeGoals: Int?,
    val awayGoals: Int?,
    val status: String,
    val elapsed: Int?,
    val date: String,
    val round: String,
    val leagueId: Int,
    val season: Int,
    val leg: String
)

private enum class RoyalSportsMatchBucket {
    LIVE,
    NOT_STARTED,
    PLAYED
}

private fun sportsMatchBucket(status: String): RoyalSportsMatchBucket = when (status.uppercase(Locale.ROOT)) {
    "1H", "2H", "ET", "LIVE", "HT", "BT", "P", "SUSP", "INT" -> RoyalSportsMatchBucket.LIVE
    "FT", "AET", "PEN", "AWD", "WO", "CANC", "ABD" -> RoyalSportsMatchBucket.PLAYED
    else -> RoyalSportsMatchBucket.NOT_STARTED
}

internal data class RoyalSportsLoadResult(
    val fixtures: List<RoyalSportsFixture>,
    val cacheHit: Boolean,
    val stale: Boolean,
    val remaining: Int?,
    val updatedAt: Long
)

internal class RoyalSportsHttpException(
    val statusCode: Int,
    val errorCode: String
) : IOException(errorCode.ifBlank { "sports_request_failed" })

private data class RoyalSportsSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long
)

internal object RoyalSportsRepository {
    private const val SESSION_PREFS = "royalplus_sports_secure_session"
    private const val SESSION_VALUE = "encrypted_session"
    private const val KEY_ALIAS = "royalplus.sports.session.v1"
    private const val LOCAL_CACHE_PREFS = "royalplus_sports_local_cache"
    private val sessionLock = Any()

    suspend fun loadFixtures(context: Context, tab: RoyalSportsTab): RoyalSportsLoadResult {
        require(tab != RoyalSportsTab.RANKINGS) { "rankings_are_not_fixtures" }
        val cacheKey = when (tab) {
            RoyalSportsTab.LIVE -> "matches_live"
            RoyalSportsTab.UPCOMING -> "matches_upcoming"
            RoyalSportsTab.FINISHED -> "matches_finished"
            RoyalSportsTab.RANKINGS -> error("rankings_are_not_fixtures")
        }
        val envelope = loadSportsJson(context, buildQuery(tab), cacheKey)
        return parseEnvelope(envelope)
    }

    /**
     * All sports data uses the same authenticated Supabase Edge Function.
     * The provider credential remains on the server; this method only sends
     * the public client key and an encrypted anonymous-session bearer token.
     */
    internal suspend fun loadSportsJson(
        context: Context,
        query: String,
        cacheKey: String
    ): JSONObject = withContext(Dispatchers.IO) {
        try {
            var session = ensureSession(context)
            val endpoint = "$SPORTS_SUPABASE_URL/functions/v1/royalplus-sports?$query"
            val envelope = try {
                requestJson(endpoint, "GET", session.accessToken, null)
            } catch (error: RoyalSportsHttpException) {
                if (error.statusCode != 401) throw error
                session = renewSession(context)
                requestJson(endpoint, "GET", session.accessToken, null)
            }
            saveLocalCache(context, cacheKey, envelope)
            envelope
        } catch (error: Exception) {
            val saved = context.getSharedPreferences(LOCAL_CACHE_PREFS, Context.MODE_PRIVATE)
                .getString("payload_$cacheKey", null)
            if (saved != null) {
                val cachedEnvelope = JSONObject(saved)
                val cache = cachedEnvelope.optJSONObject("cache") ?: JSONObject()
                cache.put("hit", true).put("stale", true)
                cachedEnvelope.put("cache", cache)
            } else {
                throw error
            }
        }
    }

    private fun dateOffset(days: Int): String {
        val timeZone = TimeZone.getDefault()
        val calendar = Calendar.getInstance(timeZone, Locale.US).apply {
            add(Calendar.DAY_OF_YEAR, days)
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            this.timeZone = timeZone
        }.format(calendar.time)
    }

    private fun buildQuery(tab: RoyalSportsTab): String {
        val timezone = TimeZone.getDefault().id
        val timeZoneParam = "timezone=" + URLEncoder.encode(timezone, "UTF-8")
        val today = dateOffset(0)
        return when (tab) {
            // Date filters are shared by the screen and background alerts, so both
            // use the same server cache and remain compatible with the deployed fixtures endpoint.
            RoyalSportsTab.LIVE -> "date=$today&$timeZoneParam"
            // Date ranges return every scheduled fixture in the window rather than
            // only the next 99 worldwide, so smaller domestic leagues are not cut off.
            RoyalSportsTab.UPCOMING -> "from=$today&to=${dateOffset(7)}&$timeZoneParam"
            // Keep a full recent week of results across all competitions.
            RoyalSportsTab.FINISHED -> "from=${dateOffset(-7)}&to=$today&$timeZoneParam"
            RoyalSportsTab.RANKINGS -> error("rankings_are_not_fixtures")
        }
    }

    private fun parseEnvelope(envelope: JSONObject): RoyalSportsLoadResult {
        val data = envelope.optJSONObject("data") ?: envelope
        val response = data.optJSONArray("response") ?: JSONArray()
        val fixtures = ArrayList<RoyalSportsFixture>(response.length())
        for (index in 0 until response.length()) {
            val item = response.optJSONObject(index) ?: continue
            val fixture = item.optJSONObject("fixture") ?: continue
            val teams = item.optJSONObject("teams") ?: JSONObject()
            val home = teams.optJSONObject("home") ?: JSONObject()
            val away = teams.optJSONObject("away") ?: JSONObject()
            val league = item.optJSONObject("league") ?: JSONObject()
            val goals = item.optJSONObject("goals") ?: JSONObject()
            val status = fixture.optJSONObject("status") ?: JSONObject()
            val id = fixture.optLong("id", 0L)
            if (id <= 0L) continue
            fixtures.add(
                RoyalSportsFixture(
                    id = id,
                    league = league.optString("name", "Football"),
                    country = league.optString("country", ""),
                    leagueLogo = league.optString("logo", ""),
                    countryFlag = league.optString("flag", ""),
                    homeTeamId = home.optInt("id", 0),
                    homeTeam = home.optString("name", "Home"),
                    homeLogo = home.optString("logo", ""),
                    awayTeamId = away.optInt("id", 0),
                    awayTeam = away.optString("name", "Away"),
                    awayLogo = away.optString("logo", ""),
                    homeGoals = if (goals.isNull("home")) null else goals.optInt("home"),
                    awayGoals = if (goals.isNull("away")) null else goals.optInt("away"),
                    status = status.optString("short", "NS"),
                    elapsed = if (status.isNull("elapsed")) null else status.optInt("elapsed"),
                    date = fixture.optString("date", ""),
                    round = league.optString("round", ""),
                    leagueId = league.optInt("id", 0),
                    season = league.optInt("season", 0),
                    leg = ""
                )
            )
        }
        val cache = envelope.optJSONObject("cache") ?: JSONObject()
        val quota = envelope.optJSONObject("quota") ?: JSONObject()
        return RoyalSportsLoadResult(
            fixtures = fixtures.sortedBy { it.date },
            cacheHit = cache.optBoolean("hit", false),
            stale = cache.optBoolean("stale", false),
            remaining = if (quota.has("rolling_24h_remaining")) quota.optInt("rolling_24h_remaining") else null,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun saveLocalCache(context: Context, key: String, envelope: JSONObject) {
        context.getSharedPreferences(LOCAL_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("payload_$key", envelope.toString())
            .putLong("saved_at_$key", System.currentTimeMillis())
            .apply()
    }

    private fun readLocalCache(context: Context, key: String): RoyalSportsLoadResult? {
        val prefs = context.getSharedPreferences(LOCAL_CACHE_PREFS, Context.MODE_PRIVATE)
        val payload = prefs.getString("payload_$key", null) ?: return null
        return try {
            val parsed = parseEnvelope(JSONObject(payload))
            val savedAt = prefs.getLong("saved_at_$key", parsed.updatedAt)
            parsed.copy(stale = true, updatedAt = savedAt)
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureSession(context: Context): RoyalSportsSession {
        synchronized(sessionLock) {
            val stored = readSession(context)
            if (stored != null && stored.expiresAtMillis > System.currentTimeMillis() + 90_000L) {
                return stored
            }
            if (stored != null && stored.refreshToken.isNotBlank()) {
                try {
                    val refreshed = refreshSession(stored)
                    saveSession(context, refreshed)
                    return refreshed
                } catch (error: RoyalSportsHttpException) {
                    if (error.statusCode !in 400..499) throw error
                }
            }
            val created = createAnonymousSession()
            saveSession(context, created)
            return created
        }
    }

    private fun renewSession(context: Context): RoyalSportsSession {
        synchronized(sessionLock) {
            val stored = readSession(context)
            if (stored != null && stored.refreshToken.isNotBlank()) {
                try {
                    val refreshed = refreshSession(stored)
                    saveSession(context, refreshed)
                    return refreshed
                } catch (error: RoyalSportsHttpException) {
                    if (error.statusCode !in 400..499) throw error
                }
            }
            val created = createAnonymousSession()
            saveSession(context, created)
            return created
        }
    }

    private fun createAnonymousSession(): RoyalSportsSession {
        val result = requestJson(
            "$SPORTS_SUPABASE_URL/auth/v1/signup",
            "POST",
            null,
            JSONObject()
        )
        return sessionFromJson(result)
    }

    private fun refreshSession(session: RoyalSportsSession): RoyalSportsSession {
        val result = requestJson(
            "$SPORTS_SUPABASE_URL/auth/v1/token?grant_type=refresh_token",
            "POST",
            null,
            JSONObject().put("refresh_token", session.refreshToken)
        )
        return sessionFromJson(result)
    }

    private fun sessionFromJson(json: JSONObject): RoyalSportsSession {
        val access = json.optString("access_token", "")
        val refresh = json.optString("refresh_token", "")
        if (access.isBlank() || refresh.isBlank()) {
            throw RoyalSportsHttpException(401, json.optString("error", "anonymous_session_missing"))
        }
        val expiresIn = json.optLong("expires_in", 3600L).coerceAtLeast(60L)
        return RoyalSportsSession(
            accessToken = access,
            refreshToken = refresh,
            expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L
        )
    }

    private fun readSession(context: Context): RoyalSportsSession? {
        val prefs = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        val encoded = prefs.getString(SESSION_VALUE, null) ?: return null
        return try {
            val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
            require(encrypted.isNotEmpty())
            val ivLength = encrypted[0].toInt() and 0xff
            require(ivLength in 12..16 && encrypted.size > ivLength + 1)
            val iv = encrypted.copyOfRange(1, 1 + ivLength)
            val ciphertext = encrypted.copyOfRange(1 + ivLength, encrypted.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeystoreKey(), GCMParameterSpec(128, iv))
            val plain = cipher.doFinal(ciphertext)
            val json = JSONObject(String(plain, Charsets.UTF_8))
            RoyalSportsSession(
                accessToken = json.getString("access_token"),
                refreshToken = json.getString("refresh_token"),
                expiresAtMillis = json.getLong("expires_at")
            )
        } catch (_: Exception) {
            prefs.edit().remove(SESSION_VALUE).apply()
            null
        }
    }

    private fun saveSession(context: Context, session: RoyalSportsSession) {
        val json = JSONObject()
            .put("access_token", session.accessToken)
            .put("refresh_token", session.refreshToken)
            .put("expires_at", session.expiresAtMillis)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val packed = ByteArray(1 + iv.size + ciphertext.size)
        packed[0] = iv.size.toByte()
        System.arraycopy(iv, 0, packed, 1, iv.size)
        System.arraycopy(ciphertext, 0, packed, 1 + iv.size, ciphertext.size)
        context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(SESSION_VALUE, Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun requestJson(
        url: String,
        method: String,
        bearerToken: String?,
        body: JSONObject?
    ): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("apikey", SPORTS_SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            if (!bearerToken.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $bearerToken")
            }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = try {
                if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
            } catch (_: Exception) {
                JSONObject()
            }
            if (status !in 200..299) {
                val code = json.optString(
                    "error",
                    json.optString("error_code", json.optString("message", "request_failed"))
                )
                throw RoyalSportsHttpException(status, code)
            }
            return json
        } finally {
            connection.disconnect()
        }
    }
}

@Composable
internal fun RoyalSportsScreen(language: String) {
    val context = LocalContext.current.applicationContext
    val arabic = language == "ar"
    val turkish = language == "tr"
    var selectedTab by remember { mutableStateOf(RoyalSportsTab.LIVE) }
    var loaded by remember { mutableStateOf<RoyalSportsLoadResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var errorCode by remember { mutableStateOf<String?>(null) }
    var refreshSequence by remember { mutableStateOf(0) }
    var favoriteTeams by remember { mutableStateOf(RoyalSportsFavorites.load(context)) }
    var alertSettings by remember { mutableStateOf(RoyalSportsFavorites.loadAlertSettings(context)) }
    var showFavoritesDialog by remember { mutableStateOf(false) }
    var pendingPermissionUpdate by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingPermissionUpdate
        pendingPermissionUpdate = null
        if (granted && pending != null) {
            val updated = updateSportsAlertSetting(alertSettings, pending.first, pending.second)
            alertSettings = updated
            RoyalSportsFavorites.saveAlertSettings(context, updated)
            RoyalSportsAlertsScheduler.refresh(context)
        } else if (!granted) {
            Toast.makeText(
                context,
                if (arabic) "لم يتم السماح بالإشعارات؛ يمكنك تفعيلها من إعدادات الجهاز" else "Notification permission was not granted",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val favoriteTeamIds = favoriteTeams.mapTo(HashSet()) { it.id }
    val onToggleFavorite: (RoyalSportsFavoriteTeam) -> Unit = { team ->
        favoriteTeams = RoyalSportsFavorites.toggle(context, team)
        RoyalSportsAlertsScheduler.refresh(context)
    }
    val onRemoveFavorite: (Int) -> Unit = { teamId ->
        favoriteTeams = RoyalSportsFavorites.remove(context, teamId)
        RoyalSportsAlertsScheduler.refresh(context)
    }
    val onToggleAlert: (String, Boolean) -> Unit = { key, enabled ->
        val needsPermission = enabled &&
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingPermissionUpdate = key to enabled
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val updated = updateSportsAlertSetting(alertSettings, key, enabled)
            alertSettings = updated
            RoyalSportsFavorites.saveAlertSettings(context, updated)
            RoyalSportsAlertsScheduler.refresh(context)
        }
    }

    LaunchedEffect(language) {
        RoyalSportsFavorites.setLanguage(context, language)
    }

    LaunchedEffect(selectedTab, refreshSequence) {
        if (selectedTab == RoyalSportsTab.RANKINGS) return@LaunchedEffect
        while (true) {
            loading = true
            try {
                loaded = RoyalSportsRepository.loadFixtures(context, selectedTab)
                errorCode = null
            } catch (error: Exception) {
                errorCode = when (error) {
                    is RoyalSportsHttpException -> error.errorCode
                    is SocketTimeoutException -> "network_timeout"
                    else -> "network_unavailable"
                }
            } finally {
                loading = false
            }
            delay(20 * 60 * 1000L)
        }
    }

    val title = when {
        arabic -> "كرة القدم"
        turkish -> "FUTBOL"
        else -> "FOOTBALL"
    }
    val tabLabels = when {
        arabic -> listOf("مباريات الآن", "لم تبدأ", "انتهت", "التصنيفات")
        turkish -> listOf("Canlı", "Başlamadı", "Bitti", "Sıralamalar")
        else -> listOf("Live now", "Upcoming", "Finished", "Rankings")
    }
    val emptyLabel = when {
        arabic -> "لا توجد مباريات في هذا القسم حالياً"
        turkish -> "Bu bölümde şu anda maç yok"
        else -> "No matches are available in this section"
    }
    val loadingLabel = when {
        arabic -> "جارٍ تحميل المباريات..."
        turkish -> "Maçlar yükleniyor..."
        else -> "Loading matches..."
    }
    val refreshLabel = when {
        arabic -> "تحديث"
        turkish -> "Yenile"
        else -> "Refresh"
    }
    val staleLabel = when {
        arabic -> "نعرض آخر البيانات المحفوظة"
        turkish -> "Son kaydedilen veriler gösteriliyor"
        else -> "Showing the latest saved data"
    }
    val errorLabel = when (errorCode) {
        "anonymous_provider_disabled", "Anonymous sign-ins are disabled" -> when {
            arabic -> "فعّل الدخول المجهول في إعدادات Supabase ثم أعد المحاولة"
            turkish -> "Supabase ayarlarından anonim girişi etkinleştirin"
            else -> "Enable Anonymous Sign-Ins in Supabase, then retry"
        }
        "daily_or_rate_limit_reached" -> when {
            arabic -> "اكتملت حصة الطلبات؛ ستظهر البيانات المحفوظة إن توفرت"
            turkish -> "İstek kotası doldu; varsa kayıtlı veriler gösterilecek"
            else -> "The request quota is exhausted; saved data will be shown when available"
        }
        "request_budget_unavailable", "sports_cache_unavailable" -> when {
            arabic -> "خدمة الرياضة غير متاحة مؤقتاً"
            turkish -> "Spor servisi geçici olarak kullanılamıyor"
            else -> "The sports service is temporarily unavailable"
        }
        "network_timeout" -> when {
            arabic -> "انتهت مهلة الاتصال؛ تحقق من الإنترنت"
            turkish -> "Bağlantı zaman aşımına uğradı"
            else -> "The connection timed out"
        }
        null -> null
        else -> when {
            arabic -> "تعذر الاتصال بخدمة الرياضة؛ تحقق من الإنترنت والإعدادات"
            turkish -> "Spor hizmetine bağlanılamadı; interneti ve ayarları kontrol edin"
            else -> "Could not connect to sports; check the internet and configuration"
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(Color(0xD91A7652), Color(0xC40C4937), Color(0xDC082C25))))
                .border(1.dp, Color(0x667FFFC1), RoundedCornerShape(22.dp))
        ) {
            SportsPitchGlassBackground(Modifier.matchParentSize())
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RoyalSportsIcon(RoyalSportsGlyph.FOOTBALL, modifier = Modifier.size(royalSportsIconSize(52.dp)), contentDescription = title)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, color = RoyalColors.Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                arabic -> "المباريات والبطولات والتصنيفات"
                                turkish -> "Maçlar, turnuvalar ve sıralamalar"
                                else -> "Matches, competitions and rankings"
                            },
                            color = RoyalColors.Muted,
                            fontSize = 11.sp
                        )
                    }
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                            .background(Color(0x99214B39))
                            .border(1.dp, Color(0x558EE6B4), RoundedCornerShape(12.dp))
                            .clickable { showFavoritesDialog = true }
                            .focusable()
                            .padding(horizontal = 9.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("★", color = RoyalColors.Gold, fontSize = 14.sp)
                        Text(
                            when {
                                arabic -> "المفضلة " + favoriteTeams.size
                                turkish -> "Favoriler " + favoriteTeams.size
                                else -> "Favorites " + favoriteTeams.size
                            },
                            color = RoyalColors.Pearl,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                            .background(Color(0x99214B39))
                            .border(1.dp, Color(0x558EE6B4), RoundedCornerShape(12.dp))
                            .clickable { refreshSequence += 1 }
                            .focusable()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        RoyalSportsIcon(RoyalSportsGlyph.REFRESH, modifier = Modifier.size(royalSportsIconSize(22.dp)), contentDescription = refreshLabel)
                        Text(refreshLabel, color = RoyalColors.Pearl, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf(RoyalSportsTab.LIVE, RoyalSportsTab.UPCOMING, RoyalSportsTab.FINISHED, RoyalSportsTab.RANKINGS)
                    tabs.forEachIndexed { index, tab ->
                        SportsTabButton(
                            label = tabLabels[index],
                            icon = when (tab) {
                                RoyalSportsTab.LIVE -> RoyalSportsGlyph.LIVE
                                RoyalSportsTab.UPCOMING -> RoyalSportsGlyph.FIXTURES
                                RoyalSportsTab.FINISHED -> RoyalSportsGlyph.RESULTS
                                RoyalSportsTab.RANKINGS -> RoyalSportsGlyph.TEAMS
                            },
                            selected = selectedTab == tab,
                            modifier = Modifier.weight(1f)
                        ) { selectedTab = tab; loaded = null; errorCode = null }
                    }
                }
                Spacer(Modifier.height(10.dp))

                if (selectedTab == RoyalSportsTab.RANKINGS) {
                    RoyalSportsRankingsScreen(
                        language = language,
                        refreshSequence = refreshSequence,
                        favoriteTeamIds = favoriteTeamIds,
                        onToggleFavoriteTeam = onToggleFavorite
                    )
                } else {
                    val result = loaded
                    if (result?.stale == true) {
                        Text(staleLabel, color = Color(0xFFFFD27A), fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))
                    } else if (result?.remaining != null) {
                        val quotaText = when {
                            arabic -> "المتبقي من الحصة: " + result.remaining
                            turkish -> "Kalan istek: " + result.remaining
                            else -> "Requests remaining: " + result.remaining
                        }
                        Text(quotaText, color = RoyalColors.Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    if (errorLabel != null) {
                        Text(errorLabel, color = Color(0xFFFFB4A9), fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
                    }

                    val wantedBucket = when (selectedTab) {
                        RoyalSportsTab.LIVE -> RoyalSportsMatchBucket.LIVE
                        RoyalSportsTab.UPCOMING -> RoyalSportsMatchBucket.NOT_STARTED
                        RoyalSportsTab.FINISHED -> RoyalSportsMatchBucket.PLAYED
                        RoyalSportsTab.RANKINGS -> error("rankings_have_no_fixture_bucket")
                    }
                    val selectedFixtures = result?.fixtures.orEmpty()
                        .filter { sportsMatchBucket(it.status) == wantedBucket }
                    val orderedFixtures = if (selectedTab == RoyalSportsTab.FINISHED) {
                        selectedFixtures.sortedByDescending { it.date }
                    } else {
                        selectedFixtures.sortedBy { it.date }
                    }

                    if (selectedFixtures.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (loading) loadingLabel else (errorLabel ?: emptyLabel),
                                color = RoyalColors.Pearl,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            val leagueGroups = orderedFixtures
                                .groupBy { Triple(it.leagueId, it.country.trim(), it.league.trim()) }
                                .entries
                                .sortedBy { entry -> entry.value.firstOrNull()?.date.orEmpty() }
                            leagueGroups.forEachIndexed { groupIndex, entry ->
                                val leagueName = entry.key.third.ifBlank { title }
                                val countryName = entry.key.second
                                val heading = if (countryName.isBlank()) {
                                    localizedFootballLeagueName(leagueName, countryName, arabic, turkish)
                                } else {
                                    localizedFootballLeagueName(leagueName, countryName, arabic, turkish) + " • " +
                                        localizedFootballName(countryName, arabic, turkish)
                                }
                                val countLabel = when {
                                    arabic -> entry.value.size.toString() + " مباراة"
                                    turkish -> entry.value.size.toString() + " maç"
                                    else -> entry.value.size.toString() + " matches"
                                }
                                val firstFixture = entry.value.firstOrNull()
                                val officialLogo = firstFixture?.leagueLogo
                                    ?.takeIf { RoyalSportsProviderImageCache.isAllowed(it) }
                                    ?: firstFixture?.countryFlag?.takeIf { RoyalSportsProviderImageCache.isAllowed(it) }
                                item(key = "league-" + selectedTab.name + "-" + groupIndex + "-" + entry.key.third) {
                                    SportsLeagueHeader(
                                        title = heading,
                                        caption = if (arabic) "البطولة والمسابقة" else if (turkish) "Lig ve turnuva" else "Competition",
                                        count = countLabel,
                                        imageUrl = officialLogo
                                    )
                                }

                                val roundGroups = entry.value.groupBy { listOf(it.round.trim(), it.leg.trim()).filter { part -> part.isNotBlank() }.joinToString(" • ").ifBlank { "—" } }
                                    .entries
                                    .sortedBy { round -> round.value.firstOrNull()?.date.orEmpty() }
                                roundGroups.forEachIndexed { roundIndex, roundEntry ->
                                    if (roundEntry.key != "—") {
                                        item(key = "round-" + groupIndex + "-" + roundIndex + "-" + roundEntry.key) {
                                            SportsRoundHeader(
                                                title = localizedFootballRound(roundEntry.key, arabic, turkish)
                                            )
                                        }
                                    }
                                    items(roundEntry.value, key = { it.id }) { fixture ->
                                        SportsFixtureCard(
                                            fixture = fixture,
                                            arabic = arabic,
                                            turkish = turkish,
                                            favoriteTeamIds = favoriteTeamIds,
                                            onToggleFavorite = onToggleFavorite
                                        )
                                    }
                                }
                            }
                            if (loading) {
                                item {
                                    Text(
                                        when {
                                            arabic -> "جارٍ التحقق من آخر النتائج..."
                                            turkish -> "Sonuçlar kontrol ediliyor..."
                                            else -> "Checking for updated results..."
                                        },
                                        color = RoyalColors.Muted,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (showFavoritesDialog) {
                SportsFavoritesDialog(
                    favorites = favoriteTeams,
                    settings = alertSettings,
                    arabic = arabic,
                    turkish = turkish,
                    onRemoveFavorite = onRemoveFavorite,
                    onToggleAlert = onToggleAlert,
                    onDismiss = { showFavoritesDialog = false }
                )
            }
        }
    }
}

private fun updateSportsAlertSetting(
    current: RoyalSportsAlertSettings,
    key: String,
    enabled: Boolean
): RoyalSportsAlertSettings = when (key) {
    "match" -> current.copy(matchReminders = enabled)
    "goal" -> current.copy(goalAlerts = enabled)
    "result" -> current.copy(resultAlerts = enabled)
    else -> current
}

@Composable
private fun SportsFavoritesDialog(
    favorites: List<RoyalSportsFavoriteTeam>,
    settings: RoyalSportsAlertSettings,
    arabic: Boolean,
    turkish: Boolean,
    onRemoveFavorite: (Int) -> Unit,
    onToggleAlert: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    arabic -> "الفرق المفضلة والتنبيهات"
                    turkish -> "Favori takımlar ve bildirimler"
                    else -> "Favorite teams and alerts"
                },
                color = RoyalColors.Gold,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    when {
                        favorites.isEmpty() && arabic -> "أضف فريقاً من قائمة المباريات بالضغط على النجمة بجانب اسمه."
                        favorites.isEmpty() && turkish -> "Maç kartındaki takım yıldızına dokunarak favori ekleyin."
                        favorites.isEmpty() -> "Add teams by selecting the star beside their names on match cards."
                        arabic -> "فرقك المختارة"
                        turkish -> "Seçtiğin takımlar"
                        else -> "Your selected teams"
                    },
                    color = RoyalColors.Pearl,
                    fontSize = 12.sp
                )
                favorites.forEach { team ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RoyalSportsProviderImage(
                            url = team.logo,
                            modifier = Modifier.size(30.dp),
                            fallback = RoyalSportsGlyph.TEAMS,
                            description = team.name
                        )
                        Text(
                            team.name,
                            color = RoyalColors.Pearl,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        TextButton(onClick = { onRemoveFavorite(team.id) }) {
                            Text(
                                if (arabic) "إزالة" else if (turkish) "Kaldır" else "Remove",
                                color = Color(0xFFFFB4A9),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    if (arabic) "إعدادات التنبيهات" else if (turkish) "Bildirim ayarları" else "Notification settings",
                    color = RoyalColors.Gold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                SportsAlertSettingRow(
                    title = if (arabic) "تذكير قبل المباراة بـ30 دقيقة" else if (turkish) "Maçtan 30 dakika önce hatırlat" else "Remind me 30 minutes before kickoff",
                    checked = settings.matchReminders,
                    enabled = favorites.isNotEmpty(),
                    onCheckedChange = { onToggleAlert("match", it) }
                )
                SportsAlertSettingRow(
                    title = if (arabic) "تنبيهات الأهداف" else if (turkish) "Gol bildirimleri" else "Goal alerts",
                    checked = settings.goalAlerts,
                    enabled = favorites.isNotEmpty(),
                    onCheckedChange = { onToggleAlert("goal", it) }
                )
                SportsAlertSettingRow(
                    title = if (arabic) "النتيجة النهائية" else if (turkish) "Maç sonu sonucu" else "Final result alerts",
                    checked = settings.resultAlerts,
                    enabled = favorites.isNotEmpty(),
                    onCheckedChange = { onToggleAlert("result", it) }
                )
                Text(
                    when {
                        arabic -> "يتم فحص الأهداف في الخلفية كل 30 دقيقة؛ وقد يتأخر التنبيه حسب اتصال الإنترنت وتحديث مزود النتائج."
                        turkish -> "Goller arka planda 30 dakikada bir kontrol edilir; bildirimler internet ve veri sağlayıcısına bağlı olarak gecikebilir."
                        else -> "Background goal checks run every 30 minutes. Alerts may be delayed by connectivity and provider updates."
                    },
                    color = RoyalColors.Muted,
                    fontSize = 9.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (arabic) "تم" else if (turkish) "Tamam" else "Done", color = RoyalColors.Gold)
            }
        },
        containerColor = Color(0xFF123C2E),
        titleContentColor = RoyalColors.Gold,
        textContentColor = RoyalColors.Pearl
    )
}

@Composable
private fun SportsAlertSettingRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            title,
            color = if (enabled) RoyalColors.Pearl else RoyalColors.Muted,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.focusable()
        )
    }
}

private fun localizedFootballRound(value: String, arabic: Boolean, turkish: Boolean): String {
    val normalized = value.trim().lowercase(Locale.ROOT)
    if (arabic) {
        return when {
            normalized.contains("first leg") || normalized.contains("1st leg") -> "مباراة الذهاب • ${value.substringBefore(" • ")}"
            normalized.contains("second leg") || normalized.contains("2nd leg") -> "مباراة الإياب • ${value.substringBefore(" • ")}"
            normalized.contains("round of 16") -> "دور الـ16"
            normalized.contains("round of 32") -> "دور الـ32"
            normalized.contains("quarter") -> "ربع النهائي"
            normalized.contains("semi") -> "نصف النهائي"
            normalized == "final" || normalized.endsWith(" final") -> "النهائي"
            normalized.contains("group") -> "مرحلة المجموعات • $value"
            normalized.contains("league phase") -> "مرحلة الدوري • $value"
            normalized.contains("regular season") -> value.replace(Regex("(?i)regular season\\s*-?\\s*"), "الجولة ")
            normalized.contains("playoff") -> "الأدوار الإقصائية • $value"
            else -> value
        }
    }
    if (turkish) {
        return when {
            normalized.contains("first leg") || normalized.contains("1st leg") -> "İlk Maç • ${value.substringBefore(" • ")}"
            normalized.contains("second leg") || normalized.contains("2nd leg") -> "Rövanş • ${value.substringBefore(" • ")}"
            normalized.contains("round of 16") -> "Son 16 Turu"
            normalized.contains("quarter") -> "Çeyrek Final"
            normalized.contains("semi") -> "Yarı Final"
            normalized == "final" -> "Final"
            normalized.contains("group") -> "Grup Aşaması • $value"
            normalized.contains("league phase") -> "Lig Aşaması • $value"
            else -> value
        }
    }
    return value
}

@Composable
private fun SportsRoundHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x80324F42))
            .border(1.dp, Color(0x447CDCB0), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color(0xFFBCE9D0), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SportsTabButton(
    label: String,
    icon: RoyalSportsGlyph,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = modifier.clip(shape)
            .background(if (selected) Color(0xCC1A5C3F) else Color(0x77204435))
            .border(1.dp, if (selected) Color(0xFF9BF1B8) else Color(0x448EE6B4), shape)
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 5.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        RoyalSportsIcon(icon = icon, modifier = Modifier.size(royalSportsIconSize(22.dp)), contentDescription = label)
        Spacer(Modifier.width(4.dp))
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
private fun SportsFixtureCard(
    fixture: RoyalSportsFixture,
    arabic: Boolean,
    turkish: Boolean,
    favoriteTeamIds: Set<Int>,
    onToggleFavorite: (RoyalSportsFavoriteTeam) -> Unit
) {
    val shape = RoundedCornerShape(15.dp)
    var showDetails by remember(fixture.id) { mutableStateOf(false) }
    val statusText = when (fixture.status) {
        "1H", "2H", "ET", "LIVE", "P" -> when {
            arabic -> "مباشر"
            turkish -> "Canlı"
            else -> "LIVE"
        }
        "HT" -> when {
            arabic -> "استراحة"
            turkish -> "Devre arası"
            else -> "Half-time"
        }
        "FT", "AET", "PEN" -> when {
            arabic -> "انتهت"
            turkish -> "Bitti"
            else -> "Finished"
        }
        "PST" -> when {
            arabic -> "مؤجلة"
            turkish -> "Ertelendi"
            else -> "Postponed"
        }
        "CANC", "ABD" -> when {
            arabic -> "ألغيت"
            turkish -> "İptal"
            else -> "Cancelled"
        }
        "NS", "TBD" -> when {
            arabic -> "لم تبدأ"
            turkish -> "Başlamadı"
            else -> "Not started"
        }
        "SUSP", "INT" -> when {
            arabic -> "متوقفة"
            turkish -> "Durduruldu"
            else -> "Suspended"
        }
        "AWD", "WO" -> when {
            arabic -> "حُسمت إدارياً"
            turkish -> "Hükmen sonuçlandı"
            else -> "Awarded"
        }
        "BT" -> when {
            arabic -> "استراحة"
            turkish -> "Devre arası"
            else -> "Break"
        }
        else -> formatFixtureTime(fixture.date, arabic, turkish)
    }
    val score = if (fixture.homeGoals == null || fixture.awayGoals == null) {
        "—"
    } else {
        fixture.homeGoals.toString() + " : " + fixture.awayGoals.toString()
    }
    val statusIcon = when (fixture.status) {
        "1H", "2H", "ET", "LIVE", "HT", "BT", "P" -> RoyalSportsGlyph.LIVE
        "FT", "AET", "PEN", "AWD", "WO" -> RoyalSportsGlyph.RESULTS
        else -> RoyalSportsGlyph.FIXTURES
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(Color(0x99102F28))
            .border(1.dp, Color(0x44B8FFD7), shape)
            .clickable { showDetails = true }
            .focusable()
            .padding(horizontal = 13.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoyalSportsIcon(statusIcon, modifier = Modifier.size(royalSportsIconSize(28.dp)), contentDescription = statusText)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    statusText,
                    color = if (fixture.status in setOf("1H", "2H", "ET", "LIVE", "P")) Color(0xFF61E1D0) else RoyalColors.Pearl,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    formatFixtureTime(fixture.date, arabic, turkish),
                    color = RoyalColors.Muted,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
            if (fixture.elapsed != null && fixture.elapsed > 0 && fixture.status in setOf("1H", "2H", "ET", "LIVE")) {
                Text(fixture.elapsed.toString() + "'", color = Color(0xFFFFB36B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            RoyalSportsProviderImage(
                url = fixture.homeLogo,
                modifier = Modifier.size(royalSportsIconSize(30.dp)),
                fallback = RoyalSportsGlyph.TEAMS,
                description = localizedFootballName(fixture.homeTeam, arabic, turkish)
            )
            Text(
                localizedFootballName(fixture.homeTeam, arabic, turkish),
                color = RoyalColors.Pearl,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            SportsFavoriteTeamToggle(
                selected = fixture.homeTeamId in favoriteTeamIds,
                enabled = fixture.homeTeamId > 0,
                onClick = {
                    onToggleFavorite(RoyalSportsFavoriteTeam(fixture.homeTeamId, fixture.homeTeam, fixture.homeLogo))
                }
            )
            RoyalSportsIcon(RoyalSportsGlyph.GOAL, modifier = Modifier.size(royalSportsIconSize(26.dp)), contentDescription = score)
            Text(score, color = RoyalColors.Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                localizedFootballName(fixture.awayTeam, arabic, turkish),
                color = RoyalColors.Pearl,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            SportsFavoriteTeamToggle(
                selected = fixture.awayTeamId in favoriteTeamIds,
                enabled = fixture.awayTeamId > 0,
                onClick = {
                    onToggleFavorite(RoyalSportsFavoriteTeam(fixture.awayTeamId, fixture.awayTeam, fixture.awayLogo))
                }
            )
            RoyalSportsProviderImage(
                url = fixture.awayLogo,
                modifier = Modifier.size(royalSportsIconSize(30.dp)),
                fallback = RoyalSportsGlyph.TEAMS,
                description = localizedFootballName(fixture.awayTeam, arabic, turkish)
            )
        }
    }
    if (showDetails) {
        val detailStatus = when (fixture.status) {
            "1H", "2H", "ET", "LIVE", "P" -> if (arabic) "مباشرة" else if (turkish) "Canlı" else "Live"
            "HT", "BT" -> if (arabic) "استراحة" else if (turkish) "Devre arası" else "Half-time"
            "FT", "AET", "PEN" -> if (arabic) "انتهت" else if (turkish) "Bitti" else "Finished"
            "PST" -> if (arabic) "مؤجلة" else if (turkish) "Ertelendi" else "Postponed"
            "CANC", "ABD" -> if (arabic) "ألغيت" else if (turkish) "İptal" else "Cancelled"
            "NS", "TBD" -> if (arabic) "لم تبدأ" else if (turkish) "Başlamadı" else "Not started"
            "SUSP", "INT" -> if (arabic) "متوقفة" else if (turkish) "Durduruldu" else "Suspended"
            "AWD", "WO" -> if (arabic) "حُسمت إدارياً" else if (turkish) "Hükmen sonuçlandı" else "Awarded"
            else -> fixture.status
        }
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = {
                Text(
                    localizedFootballName(fixture.homeTeam, arabic, turkish) + "  •  " +
                        localizedFootballName(fixture.awayTeam, arabic, turkish),
                    color = RoyalColors.Pearl,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        score,
                        color = RoyalColors.Gold,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    SportsMatchDetailLine(
                        if (arabic) "الحالة" else if (turkish) "Durum" else "Status",
                        detailStatus
                    )
                    SportsMatchDetailLine(
                        if (arabic) "البطولة" else if (turkish) "Lig" else "Competition",
                        localizedFootballLeagueName(fixture.league, fixture.country, arabic, turkish)
                    )
                    if (fixture.country.isNotBlank()) {
                        SportsMatchDetailLine(
                            if (arabic) "الدولة" else if (turkish) "Ülke" else "Country",
                            localizedFootballName(fixture.country, arabic, turkish)
                        )
                    }
                    SportsMatchDetailLine(
                        if (arabic) "التاريخ والوقت" else if (turkish) "Tarih ve saat" else "Date and time",
                        formatFixtureTime(fixture.date, arabic, turkish)
                    )
                    if (fixture.round.isNotBlank()) {
                        SportsMatchDetailLine(
                            if (arabic) "الجولة" else if (turkish) "Tur" else "Round",
                            localizedFootballRound(fixture.round, arabic, turkish)
                        )
                    }
                    if (fixture.season > 0) {
                        SportsMatchDetailLine(
                            if (arabic) "الموسم" else if (turkish) "Sezon" else "Season",
                            fixture.season.toString()
                        )
                    }
                    if (fixture.elapsed != null && fixture.elapsed > 0) {
                        SportsMatchDetailLine(
                            if (arabic) "الدقيقة" else if (turkish) "Dakika" else "Minute",
                            fixture.elapsed.toString() + "'"
                        )
                    }
                    SportsMatchDetailLine(
                        if (arabic) "رقم المباراة" else if (turkish) "Maç numarası" else "Match ID",
                        fixture.id.toString()
                    )
                    Text(
                        if (arabic) "المعلومات المعروضة من بيانات المباراة المحمّلة؛ فتح التفاصيل لا يرسل طلب API إضافياً."
                        else if (turkish) "Gösterilen bilgiler önceden yüklenen maç verilerindendir; ayrıntıları açmak ek API isteği göndermez."
                        else "Details use the already loaded match data; opening this view sends no additional API request.",
                        color = RoyalColors.Muted,
                        fontSize = 10.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text(if (arabic) "إغلاق" else if (turkish) "Kapat" else "Close")
                }
            }
        )
    }
}

@Composable
private fun SportsMatchDetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = RoyalColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = RoyalColors.Pearl,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun SportsFavoriteTeamToggle(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = if (selected) "★" else "☆",
        color = when {
            !enabled -> RoyalColors.Muted
            selected -> RoyalColors.Gold
            else -> Color(0xFFB7D7D0)
        },
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.size(25.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color(0x553F6A45) else Color(0x33264C3B))
            .border(1.dp, if (selected) Color(0x887FE6A5) else Color(0x334F9473), RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .focusable()
            .padding(2.dp)
    )
}

@Composable
private fun SportsMatchBucketHeader(
    title: String,
    caption: String,
    count: String,
    bucket: RoyalSportsMatchBucket
) {
    val accent = when (bucket) {
        RoyalSportsMatchBucket.LIVE -> Color(0xFFFF7B70)
        RoyalSportsMatchBucket.NOT_STARTED -> Color(0xFF8EE6B4)
        RoyalSportsMatchBucket.PLAYED -> Color(0xFFB7D7D0)
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xBB0B3E2E), Color(0x90306045))))
            .border(1.dp, accent.copy(alpha = 0.38f), shape)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(9.dp)
                .clip(RoundedCornerShape(50))
                .background(accent)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = RoyalColors.Pearl, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(caption, color = Color(0xFFD0E8DC), fontSize = 9.sp, maxLines = 1)
        }
        Text(
            count,
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(50))
                .background(Color(0x55203F32))
                .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(50))
                .padding(horizontal = 9.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SportsLeagueHeader(
    title: String,
    caption: String,
    count: String,
    imageUrl: String?
) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xD013563E), Color(0xB3266247))))
            .border(1.dp, Color(0x668EF0B1), shape)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xB20E392B)),
            contentAlignment = Alignment.Center
        ) {
            RoyalSportsProviderImage(
                url = imageUrl,
                modifier = Modifier.fillMaxSize().padding(2.dp),
                fallback = RoyalSportsGlyph.FIXTURES,
                description = title
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = RoyalColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(caption, color = RoyalColors.Muted, fontSize = 9.sp, maxLines = 1)
        }
        Text(
            count,
            color = Color(0xFFB7F5F1),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(50))
                .background(Color(0x99366A4D))
                .border(1.dp, Color(0x558EF0B1), RoundedCornerShape(50))
                .padding(horizontal = 9.dp, vertical = 6.dp)
        )
    }
}

private fun formatFixtureTime(value: String, arabic: Boolean, turkish: Boolean): String {
    if (value.isBlank()) return "—"
    return try {
        // Keep API 23 compatibility: avoid the API-24-only ISO X pattern.
        val normalized = value
            .replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
            .replace(Regex("Z$"), "+0000")
        val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        input.isLenient = false
        val date = input.parse(normalized) ?: return value
        val outputLocale = when {
            arabic -> Locale("ar")
            turkish -> Locale("tr")
            else -> Locale.US
        }
        val pattern = when {
            arabic -> "EEEE، d MMMM، HH:mm"
            turkish -> "d MMMM EEE, HH:mm"
            else -> "EEE, MMM d, HH:mm"
        }
        SimpleDateFormat(pattern, outputLocale).format(date)
    } catch (_: Exception) {
        value
    }
}

private fun localizedFootballLeagueName(
    leagueName: String,
    countryName: String,
    arabic: Boolean,
    turkish: Boolean
): String {
    val isSyria = countryName.trim().equals("Syria", ignoreCase = true)
    val isPremierLeague = leagueName.trim().equals("Premier League", ignoreCase = true) ||
        leagueName.trim().equals("Syrian Premier League", ignoreCase = true)
    if (isSyria && isPremierLeague) {
        return when {
            arabic -> "الدوري السوري الممتاز"
            turkish -> "Suriye Premier Ligi"
            else -> "Syrian Premier League"
        }
    }
    return localizedFootballName(leagueName, arabic, turkish)
}

private fun localizedFootballName(name: String, arabic: Boolean, turkish: Boolean): String {
    val key = name.trim().lowercase(Locale.ROOT)
    if (key.isEmpty()) return name
    val arabicNames = mapOf(
        "premier league" to "الدوري الإنجليزي الممتاز", "english premier league" to "الدوري الإنجليزي الممتاز",
        "la liga" to "الدوري الإسباني", "laliga" to "الدوري الإسباني", "serie a" to "الدوري الإيطالي",
        "bundesliga" to "الدوري الألماني", "ligue 1" to "الدوري الفرنسي",
        "uefa champions league" to "دوري أبطال أوروبا", "champions league" to "دوري أبطال أوروبا",
        "uefa europa league" to "الدوري الأوروبي", "europa league" to "الدوري الأوروبي",
        "uefa conference league" to "دوري المؤتمر الأوروبي", "fifa world cup" to "كأس العالم", "world cup" to "كأس العالم",
        "fa cup" to "كأس الاتحاد الإنجليزي", "efl cup" to "كأس الرابطة الإنجليزية",
        "super lig" to "الدوري التركي الممتاز", "süper lig" to "الدوري التركي الممتاز",
        "turkey" to "تركيا", "türkiye" to "تركيا", "england" to "إنجلترا", "spain" to "إسبانيا",
        "italy" to "إيطاليا", "germany" to "ألمانيا", "france" to "فرنسا", "saudi arabia" to "السعودية",
        "united arab emirates" to "الإمارات", "qatar" to "قطر", "iraq" to "العراق", "syria" to "سوريا",
        "egypt" to "مصر", "morocco" to "المغرب", "argentina" to "الأرجنتين", "brazil" to "البرازيل",
        "portugal" to "البرتغال", "netherlands" to "هولندا", "belgium" to "بلجيكا",
        "manchester city" to "مانشستر سيتي", "manchester united" to "مانشستر يونايتد",
        "liverpool" to "ليفربول", "arsenal" to "أرسنال", "chelsea" to "تشيلسي", "tottenham" to "توتنهام",
        "newcastle" to "نيوكاسل", "real madrid" to "ريال مدريد", "barcelona" to "برشلونة",
        "atletico madrid" to "أتلتيكو مدريد", "sevilla" to "إشبيلية", "girona" to "جيرونا",
        "inter" to "إنتر ميلان", "inter milan" to "إنتر ميلان", "ac milan" to "ميلان",
        "juventus" to "يوفنتوس", "napoli" to "نابولي", "roma" to "روما", "lazio" to "لاتسيو",
        "bayern munich" to "بايرن ميونخ", "borussia dortmund" to "بوروسيا دورتموند", "rb leipzig" to "لايبزيغ",
        "paris saint germain" to "باريس سان جيرمان", "paris saint-germain" to "باريس سان جيرمان", "psg" to "باريس سان جيرمان",
        "marseille" to "مارسيليا", "galatasaray" to "غلطة سراي", "fenerbahce" to "فنربخشة",
        "fenerbahçe" to "فنربخشة", "besiktas" to "بشكتاش", "beşiktaş" to "بشكتاش",
        "trabzonspor" to "طرابزون سبور", "al hilal" to "الهلال", "al nassr" to "النصر",
        "al ittihad" to "الاتحاد", "al ahli" to "الأهلي", "al ahli saudi" to "الأهلي السعودي"
    )
    val turkishNames = mapOf(
        "premier league" to "İngiltere Premier Ligi", "la liga" to "İspanya La Liga",
        "serie a" to "İtalya Serie A", "bundesliga" to "Almanya Bundesliga", "ligue 1" to "Fransa Ligue 1",
        "uefa champions league" to "UEFA Şampiyonlar Ligi", "champions league" to "Şampiyonlar Ligi",
        "uefa europa league" to "UEFA Avrupa Ligi", "europa league" to "Avrupa Ligi",
        "england" to "İngiltere", "spain" to "İspanya", "italy" to "İtalya", "germany" to "Almanya",
        "france" to "Fransa", "saudi arabia" to "Suudi Arabistan",
        "manchester city" to "Manchester City", "manchester united" to "Manchester United",
        "real madrid" to "Real Madrid", "barcelona" to "Barcelona", "bayern munich" to "Bayern Münih",
        "galatasaray" to "Galatasaray", "fenerbahce" to "Fenerbahçe", "besiktas" to "Beşiktaş"
    )
    return when {
        arabic -> arabicNames[key] ?: name
        turkish -> turkishNames[key] ?: name
        else -> name
    }
}
