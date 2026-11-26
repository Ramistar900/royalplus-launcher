package com.royalplus.launcher

import android.content.Context
import com.sinjartv.rstv.RemotePlaylistSource
import com.sinjartv.rstv.RSTVChannel
import com.sinjartv.rstv.RemotePlaylistMerger
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

internal data class SinjarCatalogSourceDefinition(
    val source: RemotePlaylistSource,
    val autoSync: Boolean,
    val refreshIntervalMinutes: Int
)

internal data class SinjarRemoteCatalogSnapshot(
    val revision: String,
    val refreshMinutes: Int,
    val sources: List<SinjarCatalogSourceDefinition>,
    val channels: List<RSTVChannel>
)

/** Read-only shared catalog consumed by both RS TV and ROYAL+ without APK updates. */
internal object SinjarRemoteCatalog {
    private const val CATALOG_URL =
        "https://raw.githubusercontent.com/Ramistar900/rs-tv/experiment/channel-position-resume/catalog/remote-sources.json"
    private const val PREFS = "sinjar_remote_iptv_catalog"
    private const val CACHE_KEY = "catalog_json"
    private const val MAX_CATALOG_CHARS = 1_000_000

    fun loadOrCached(context: Context): SinjarRemoteCatalogSnapshot {
        val freshJson = runCatching { downloadCatalog() }.getOrNull()
        if (freshJson != null) {
            val parsed = runCatching { parse(freshJson) }.getOrNull()
            if (parsed != null) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(CACHE_KEY, freshJson).apply()
                return parsed
            }
        }
        val cached = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CACHE_KEY, null)
        return cached?.let { runCatching { parse(it) }.getOrNull() }
            ?: SinjarRemoteCatalogSnapshot("empty", 15, emptyList(), emptyList())
    }

    private fun downloadCatalog(): String {
        val connection = (URL(CATALOG_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val code = connection.responseCode
            require(code in 200..299) { "Remote catalog HTTP $code" }
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            require(text.length <= MAX_CATALOG_CHARS) { "Remote catalog is too large" }
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(text: String): SinjarRemoteCatalogSnapshot {
        val root = JSONObject(text)
        require(root.optInt("schemaVersion", 0) == 1) { "Unsupported catalog schema" }

        val sources = buildList {
            val array = root.optJSONArray("sources") ?: return@buildList
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                if (!item.optBoolean("enabled", true)) continue
                val id = item.optString("id").trim()
                val name = item.optString("name").trim()
                val playlistUrl = item.optString("playlistUrl").trim()
                val provider = item.optString("provider").trim()
                val uri = runCatching { URI(playlistUrl) }.getOrNull() ?: continue
                val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: continue
                if (!id.matches(Regex("[a-z0-9][a-z0-9._-]{1,79}")) ||
                    name.isBlank() || provider.isBlank() ||
                    uri.scheme?.equals("https", true) != true || uri.userInfo != null
                ) continue

                val allowedHosts = buildSet {
                    val declared = item.optJSONArray("allowedHosts")
                    if (declared != null) {
                        for (hostIndex in 0 until declared.length()) {
                            val allowed = declared.optString(hostIndex).trim().lowercase()
                            if (allowed.matches(Regex("[a-z0-9.-]{1,253}"))) add(allowed)
                        }
                    }
                    add(host)
                }
                add(
                    SinjarCatalogSourceDefinition(
                        source = RemotePlaylistSource(
                            id = id,
                            name = name,
                            description = item.optString("description"),
                            playlistUrl = playlistUrl,
                            provider = provider,
                            attribution = item.optString("attribution")
                                .takeIf { it.isNotBlank() && it != "null" },
                            allowedHosts = allowedHosts
                        ),
                        autoSync = item.optBoolean("autoSync", true),
                        refreshIntervalMinutes = item.optInt("refreshIntervalMinutes", 360)
                            .coerceIn(60, 10_080)
                    )
                )
            }
        }.distinctBy { it.source.id }

        val channels = buildList {
            val array = root.optJSONArray("channels") ?: return@buildList
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                if (!item.optBoolean("enabled", true)) continue
                val name = item.optString("name").trim()
                val streamUrl = item.optString("streamUrl").trim()
                val uri = runCatching { URI(streamUrl) }.getOrNull() ?: continue
                if (name.isBlank() || uri.scheme?.equals("https", true) != true ||
                    uri.host.isNullOrBlank() || uri.userInfo != null
                ) continue
                add(
                    RSTVChannel(
                        name = name,
                        tvgId = item.optString("tvgId").takeIf { it.isNotBlank() && it != "null" },
                        group = item.optString("group", "قنوات إضافية").takeIf { it.isNotBlank() },
                        logoUrl = item.optString("logoUrl").takeIf { it.startsWith("https://") },
                        country = item.optString("country").takeIf { it.isNotBlank() && it != "null" },
                        contentRating = item.optString("contentRating").takeIf { it.isNotBlank() && it != "null" },
                        streamUrl = streamUrl,
                        sourceIds = setOf("remote-catalog"),
                        sourceNames = setOf("الكتالوج المركزي"),
                        sourceProviders = setOf(item.optString("provider", "الكتالوج المركزي"))
                    )
                )
            }
        }.distinctBy { RemotePlaylistMerger.canonicalStreamKey(it.streamUrl, it.httpHeaders) }

        return SinjarRemoteCatalogSnapshot(
            revision = root.optString("revision", root.optString("updatedAt", "unknown")),
            refreshMinutes = root.optInt("refreshMinutes", 15).coerceIn(5, 240),
            sources = sources,
            channels = channels
        )
    }
}

internal data class SinjarRamiViewerChannel(
    val id: String,
    val number: Long,
    val name: String,
    val logoUrl: String?
)

internal object SinjarRamiNetworkViewer {
    private const val BASE_URL =
        "https://ymujjcdsdclptbpgckll.supabase.co/functions/v1/rami-network-viewer"
    private const val PUBLIC_API_KEY = "sb_publishable_rltTQCQ_SlGkj_eRecMVaQ_gu-Sw_bW"

    fun loadChannels(): List<SinjarRamiViewerChannel> {
        val response = get("$BASE_URL?action=channels")
        val array = response.optJSONArray("channels") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SinjarRamiViewerChannel(
                id = id,
                number = item.optLong("number"),
                name = item.optString("name", "RAMİ TV"),
                logoUrl = item.optString("logoUrl").takeIf { it.startsWith("https://") }
            )
        }
    }

    fun loadState(channelId: String) =
        get("$BASE_URL?action=state&channel_id=${java.net.URLEncoder.encode(channelId, "UTF-8")}")

    private fun get(endpoint: String): JSONObject {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("apikey", PUBLIC_API_KEY)
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            require(code in 200..299) { "RAMİ TV server HTTP $code" }
            return JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }
}
