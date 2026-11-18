package com.royalplus.launcher

import android.content.Context
import android.os.Build
import android.text.Html
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cloud fallback used only when the on-device ML Kit translator does not contain
 * the detected source-language model. Audio remains on-device; only recognized
 * caption text is sent to the authenticated Supabase Edge Function.
 */
internal object RoyalCloudTranslationClient {

    private const val FUNCTION_NAME = "royalplus-translate"
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 25_000
    private const val MAX_TEXT_LENGTH = 2_000

    fun translate(context: Context, text: String): String {
        val normalized = text.trim()
        require(normalized.isNotEmpty()) { "empty_translation_text" }
        require(normalized.length <= MAX_TEXT_LENGTH) { "translation_text_too_long" }

        val auth = RoyalSportsRepository.translationAuth(context.applicationContext)
        val endpoint = auth.projectUrl.trimEnd('/') + "/functions/v1/" + FUNCTION_NAME
        val connection = URL(endpoint).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.useCaches = false
            connection.doOutput = true
            connection.setRequestProperty("apikey", auth.publishableKey)
            connection.setRequestProperty("Authorization", "Bearer ${auth.accessToken}")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")

            val payload = JSONObject().put("text", normalized)
                .toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching {
                if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
            }.getOrElse { JSONObject() }

            if (status !in 200..299) {
                val code = json.optString("error", "translation_http_$status")
                throw IllegalStateException(code)
            }

            val translated = json.optString("translatedText").trim()
            if (translated.isBlank()) {
                throw IllegalStateException(json.optString("error", "empty_translation_response"))
            }

            @Suppress("DEPRECATION")
            val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(translated, Html.FROM_HTML_MODE_LEGACY).toString()
            } else {
                Html.fromHtml(translated).toString()
            }.trim()

            if (decoded.isBlank()) throw IllegalStateException("empty_translation_response")
            return decoded
        } finally {
            connection.disconnect()
        }
    }
}
