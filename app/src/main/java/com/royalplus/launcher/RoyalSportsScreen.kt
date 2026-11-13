package com.royalplus.launcher

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.SportsVolleyball
import androidx.compose.material.icons.rounded.SportsHandball
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material.icons.rounded.SportsMotorsports
import androidx.compose.material.icons.rounded.SportsMma
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val SPORTS_SUPABASE_URL = "https://ymujjcdsdclptbpgckll.supabase.co"
private const val SPORTS_SUPABASE_PUBLISHABLE_KEY = "sb_publishable_rltTQCQ_SlGkj_eRecMVaQ_gu-Sw_bW"

private enum class RoyalSportsTab {
    MATCHES, SCHEDULE
}

internal data class RoyalSportsFixture(
    val id: Long,
    val league: String,
    val country: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeGoals: Int?,
    val awayGoals: Int?,
    val status: String,
    val elapsed: Int?,
    val date: String
)

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

private object RoyalSportsRepository {
    private const val SESSION_PREFS = "royalplus_sports_secure_session"
    private const val SESSION_VALUE = "encrypted_session"
    private const val KEY_ALIAS = "royalplus.sports.session.v1"
    private const val LOCAL_CACHE_PREFS = "royalplus_sports_local_cache"
    private val sessionLock = Any()

    suspend fun loadFixtures(context: Context, tab: RoyalSportsTab): RoyalSportsLoadResult {
        return withContext(Dispatchers.IO) {
            val cacheKey = if (tab == RoyalSportsTab.MATCHES) "matches_today" else "schedule_next"
            try {
                var session = ensureSession(context)
                val query = buildQuery(tab)
                val endpoint = "$SPORTS_SUPABASE_URL/functions/v1/royalplus-sports?$query"
                val envelope = try {
                    requestJson(endpoint, "GET", session.accessToken, null)
                } catch (error: RoyalSportsHttpException) {
                    if (error.statusCode != 401) throw error
                    session = renewSession(context)
                    requestJson(endpoint, "GET", session.accessToken, null)
                }
                val result = parseEnvelope(envelope)
                saveLocalCache(context, cacheKey, envelope)
                result
            } catch (error: Exception) {
                val saved = readLocalCache(context, cacheKey)
                if (saved != null) {
                    saved.copy(stale = true)
                } else {
                    throw error
                }
            }
        }
    }

    private fun buildQuery(tab: RoyalSportsTab): String {
        val timezone = TimeZone.getDefault().id
        val timeZoneParam = "timezone=" + URLEncoder.encode(timezone, "UTF-8")
        return if (tab == RoyalSportsTab.MATCHES) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            "date=$today&$timeZoneParam"
        } else {
            "next=20&$timeZoneParam"
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
                    homeTeam = home.optString("name", "Home"),
                    awayTeam = away.optString("name", "Away"),
                    homeGoals = if (goals.isNull("home")) null else goals.optInt("home"),
                    awayGoals = if (goals.isNull("away")) null else goals.optInt("away"),
                    status = status.optString("short", "NS"),
                    elapsed = if (status.isNull("elapsed")) null else status.optInt("elapsed"),
                    date = fixture.optString("date", "")
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
    var selectedTab by remember { mutableStateOf(RoyalSportsTab.MATCHES) }
    var loaded by remember { mutableStateOf<RoyalSportsLoadResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var errorCode by remember { mutableStateOf<String?>(null) }
    var refreshSequence by remember { mutableStateOf(0) }

    LaunchedEffect(selectedTab, refreshSequence) {
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
            delay(5 * 60 * 1000L)
        }
    }

    val title = when {
        arabic -> "الرياضة"
        turkish -> "SPOR"
        else -> "SPORTS"
    }
    val matchesLabel = when {
        arabic -> "مباريات اليوم"
        turkish -> "Bugünün maçları"
        else -> "Today's matches"
    }
    val scheduleLabel = when {
        arabic -> "الجدول القادم"
        turkish -> "Gelecek fikstür"
        else -> "Upcoming schedule"
    }
    val emptyLabel = when {
        arabic -> "لا توجد مباريات في هذه الفترة"
        turkish -> "Bu aralıkta maç bulunamadı"
        else -> "No matches found for this period"
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
        arabic -> "نعرض آخر البيانات المحفوظة لتوفير الحصة"
        turkish -> "Kotayı korumak için kayıtlı son veriler gösteriliyor"
        else -> "Showing saved results to protect the API quota"
    }
    val errorLabel = when (errorCode) {
        "anonymous_provider_disabled", "Anonymous sign-ins are disabled" -> when {
            arabic -> "فعّل الدخول المجهول في إعدادات Supabase ثم أعد المحاولة"
            turkish -> "Supabase ayarlarından anonim girişi etkinleştirin"
            else -> "Enable Anonymous Sign-Ins in Supabase, then retry"
        }
        "daily_or_rate_limit_reached" -> when {
            arabic -> "اكتملت حصة الطلبات الحالية؛ ستظهر البيانات المحفوظة إن توفرت"
            turkish -> "İstek kotası doldu; varsa kayıtlı veriler gösterilecek"
            else -> "The request quota is exhausted; saved data will be shown when available"
        }
        "request_budget_unavailable", "sports_cache_unavailable" -> when {
            arabic -> "خدمة الرياضة غير متاحة مؤقتاً؛ لم يتم الاتصال بمزوّد المباريات"
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
    Column(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF0121721), Color(0xF9080C12))))
            .border(1.dp, RoyalColors.Gold.copy(alpha = 0.28f), RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.SportsSoccer, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = RoyalColors.Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        arabic -> "المباريات والنتائج وجدول المواجهات"
                        turkish -> "Maçlar, skorlar ve fikstür"
                        else -> "Live scores, results and fixtures"
                    },
                    color = RoyalColors.Muted,
                    fontSize = 11.sp
                )
            }
            Row(
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF272116))
                    .border(1.dp, RoyalColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .clickable { refreshSequence += 1 }
                    .focusable()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = RoyalColors.Gold, modifier = Modifier.size(16.dp))
                Text(refreshLabel, color = RoyalColors.Pearl, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SportsTabButton(
                label = matchesLabel,
                icon = Icons.Rounded.SportsSoccer,
                selected = selectedTab == RoyalSportsTab.MATCHES,
                modifier = Modifier.weight(1f)
            ) { selectedTab = RoyalSportsTab.MATCHES }
            SportsTabButton(
                label = scheduleLabel,
                icon = Icons.Rounded.CalendarToday,
                selected = selectedTab == RoyalSportsTab.SCHEDULE,
                modifier = Modifier.weight(1f)
            ) { selectedTab = RoyalSportsTab.SCHEDULE }
        }
        Spacer(Modifier.height(10.dp))
        SportsCategoryStrip(language = language)
        Spacer(Modifier.height(10.dp))

        val result = loaded
        if (result?.stale == true) {
            Text(staleLabel, color = Color(0xFFFFD27A), fontSize = 10.sp, modifier = Modifier.padding(bottom = 7.dp))
        } else if (result?.remaining != null) {
            val quotaText = when {
                arabic -> "المتبقي من الحصة: " + result.remaining
                turkish -> "Kalan istek: " + result.remaining
                else -> "Requests remaining: " + result.remaining
            }
            Text(quotaText, color = RoyalColors.Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 7.dp))
        }
        if (errorLabel != null) {
            Text(errorLabel, color = Color(0xFFFFB4A9), fontSize = 11.sp, modifier = Modifier.padding(bottom = 7.dp))
        }

        if (result?.fixtures.isNullOrEmpty()) {
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(result!!.fixtures, key = { it.id }) { fixture ->
                    SportsFixtureCard(fixture = fixture, arabic = arabic, turkish = turkish)
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
}

private data class RoyalSportsCategory(
    val icon: ImageVector,
    val arabicName: String,
    val turkishName: String,
    val englishName: String,
    val available: Boolean = false
)

@Composable
private fun SportsCategoryStrip(language: String) {
    val arabic = language == "ar"
    val turkish = language == "tr"
    val categories = listOf(
        RoyalSportsCategory(Icons.Rounded.SportsSoccer, "كرة القدم", "Futbol", "Football", available = true),
        RoyalSportsCategory(Icons.Rounded.SportsBasketball, "كرة السلة", "Basketbol", "Basketball"),
        RoyalSportsCategory(Icons.Rounded.SportsTennis, "التنس", "Tenis", "Tennis"),
        RoyalSportsCategory(Icons.Rounded.SportsVolleyball, "الكرة الطائرة", "Voleybol", "Volleyball"),
        RoyalSportsCategory(Icons.Rounded.SportsHandball, "كرة اليد", "Hentbol", "Handball"),
        RoyalSportsCategory(Icons.Rounded.SportsGymnastics, "الجمباز", "Jimnastik", "Gymnastics"),
        RoyalSportsCategory(Icons.Rounded.SportsMotorsports, "رياضة المحركات", "Motor sporları", "Motorsports"),
        RoyalSportsCategory(Icons.Rounded.SportsMma, "الفنون القتالية", "Dövüş sporları", "Martial arts")
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        categories.forEach { category ->
            val selected = category.available
            val label = when {
                arabic -> category.arabicName
                turkish -> category.turkishName
                else -> category.englishName
            }
            Column(
                modifier = Modifier.width(82.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Color(0xFF3A2D17) else Color(0x66171D27))
                    .border(
                        1.dp,
                        if (selected) RoyalColors.Gold else RoyalColors.Gold.copy(alpha = 0.16f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 5.dp, vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = label,
                    tint = if (selected) RoyalColors.Gold else RoyalColors.Muted,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    label,
                    color = if (selected) RoyalColors.Gold else RoyalColors.Pearl,
                    fontSize = 9.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!selected) {
                    Text(
                        when {
                            arabic -> "قريباً"
                            turkish -> "Yakında"
                            else -> "Soon"
                        },
                        color = RoyalColors.Muted,
                        fontSize = 8.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
    Text(
        when {
            arabic -> "بيانات المباريات والنتائج متاحة حالياً لكرة القدم فقط."
            turkish -> "Maç ve sonuç verileri şu anda yalnızca futbol için kullanılabilir."
            else -> "Live match and result data is currently available for football only."
        },
        color = RoyalColors.Muted,
        fontSize = 9.sp,
        modifier = Modifier.padding(top = 5.dp)
    )
}

@Composable
private fun SportsTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = modifier.clip(shape)
            .background(if (selected) Color(0xFF3A2D17) else Color(0x99171D27))
            .border(1.dp, if (selected) RoyalColors.Gold else RoyalColors.Gold.copy(alpha = 0.18f), shape)
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) RoyalColors.Gold else RoyalColors.Pearl,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = if (selected) RoyalColors.Gold else RoyalColors.Pearl,
            fontSize = 12.sp,
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
    turkish: Boolean
) {
    val shape = RoundedCornerShape(15.dp)
    val statusText = when (fixture.status) {
        "1H", "2H", "ET", "LIVE" -> when {
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
    Column(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(Color(0xD91A222E))
            .border(1.dp, RoyalColors.Gold.copy(alpha = 0.14f), shape)
            .focusable()
            .padding(horizontal = 13.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (fixture.country.isBlank()) {
                    localizedFootballName(fixture.league, arabic, turkish)
                } else {
                    localizedFootballName(fixture.league, arabic, turkish) + " • " +
                        localizedFootballName(fixture.country, arabic, turkish)
                },
                color = RoyalColors.Gold,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (fixture.elapsed != null && fixture.elapsed > 0 && fixture.status in setOf("1H", "2H", "ET", "LIVE")) {
                Text(fixture.elapsed.toString() + "'", color = Color(0xFFFFB36B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                localizedFootballName(fixture.homeTeam, arabic, turkish),
                color = RoyalColors.Pearl,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
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
        }
        Spacer(Modifier.height(6.dp))
        Text(statusText, color = RoyalColors.Muted, fontSize = 10.sp)
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
