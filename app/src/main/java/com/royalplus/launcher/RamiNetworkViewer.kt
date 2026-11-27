package com.royalplus.launcher

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class RamiViewerChannel(
    val id: String,
    val number: Long,
    val name: String,
    val logoUrl: String?
)

internal object RamiNetworkViewer {
    private const val BASE_URL =
        "https://ymujjcdsdclptbpgckll.supabase.co/functions/v1/rami-network-viewer"

    fun loadChannels(): List<RamiViewerChannel> {
        val response = get("$BASE_URL?action=channels")
        val array = response.optJSONArray("channels") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isBlank()) return@mapNotNull null
            RamiViewerChannel(
                id = id,
                number = item.optLong("number"),
                name = item.optString("name", "RAMİ TV"),
                logoUrl = item.optString("logoUrl").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }

    fun loadState(channelId: String): JSONObject =
        get("$BASE_URL?action=state&channel_id=$channelId")

    private fun get(endpoint: String): JSONObject {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("apikey", "sb_publishable_rltTQCQ_SlGkj_eRecMVaQ_gu-Sw_bW")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("RAMİ TV server HTTP $status")
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}
