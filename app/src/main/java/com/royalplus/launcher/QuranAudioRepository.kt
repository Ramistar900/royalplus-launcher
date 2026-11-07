package com.royalplus.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class QuranVerseTiming(
    val ayah: Int,
    val startMs: Int,
    val endMs: Int
)

internal data class QuranAudioTrack(
    val audioUrl: String,
    val timings: List<QuranVerseTiming>,
    val timingAvailable: Boolean,
    val localFile: File? = null
)

internal object QuranAudioRepository {
    private const val API = "https://mp3quran.net/api/v3"
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MIN_VALID_AUDIO_BYTES = 1_024

    private data class ReadSource(
        val name: String,
        val server: String,
        val surahList: Set<Int>
    )

    suspend fun loadTrack(
        context: Context,
        readerKey: String,
        surahNumber: Int
    ): QuranAudioTrack? {
        require(surahNumber in 1..114)
        if (readerKey == "mossad") return null

        val targetName = when (readerKey) {
            "afasy" -> listOf("مشاري العفاسي", "Mishary Alafasi", "Mishary Rashid Alafasy")
            "maher" -> listOf("ماهر المعيقلي", "Maher Al Meaqli", "Maher Al-Muaiqly")
            "sudais" -> listOf("عبدالرحمن السديس", "عبد الرحمن السديس", "Abdulrahman Alsudaes")
            else -> return null
        }
        val normalizedTargets = targetName.map(::normalizeName)

        val recitersJson = requestJson("$API/reciters?language=ar") as? JSONObject ?: return null
        val reciters = recitersJson.optJSONArray("reciters") ?: return null
        var selected: ReadSource? = null

        for (index in 0 until reciters.length()) {
            val reciter = reciters.optJSONObject(index) ?: continue
            val reciterName = normalizeName(reciter.optString("name"))
            if (normalizedTargets.none { target -> reciterName.contains(target) || target.contains(reciterName) }) continue
            val moshafs = reciter.optJSONArray("moshaf") ?: continue
            for (moshafIndex in 0 until moshafs.length()) {
                val moshaf = moshafs.optJSONObject(moshafIndex) ?: continue
                val server = moshaf.optString("server").trim()
                if (!server.startsWith("https://")) continue
                val surahList = moshaf.optString("surah_list")
                    .split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                val total = moshaf.optInt("surah_total", 0)
                if (surahList.isNotEmpty() && surahNumber !in surahList) continue
                if (surahList.isEmpty() && total < surahNumber) continue
                selected = ReadSource(reciter.optString("name"), server.trimEnd('/') + "/", surahList)
                break
            }
            if (selected != null) break
        }

        val source = selected ?: return null
        val audioUrl = source.server + "%03d.mp3".format(surahNumber)
        val timings = loadCachedTimings(context, readerKey, surahNumber)
            ?: loadMatchingTimings(source, surahNumber)?.also {
                cacheTimings(context, readerKey, surahNumber, it)
            }.orEmpty()

        return QuranAudioTrack(
            audioUrl = audioUrl,
            timings = timings,
            timingAvailable = timings.isNotEmpty()
        )
    }

    fun downloadedSurahNumbers(context: Context, readerKey: String): Set<Int> {
        val directory = audioDirectory(context, readerKey)
        return directory.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.length() >= MIN_VALID_AUDIO_BYTES && it.extension.equals("mp3", true) }
            .mapNotNull { it.nameWithoutExtension.toIntOrNull()?.takeIf { number -> number in 1..114 } }
            .toSet()
    }

    fun downloadedFile(context: Context, readerKey: String, surahNumber: Int): File? {
        if (surahNumber !in 1..114) return null
        val file = File(audioDirectory(context, readerKey), "%03d.mp3".format(surahNumber))
        return file.takeIf { it.isFile && it.length() >= MIN_VALID_AUDIO_BYTES }
    }

    fun loadDownloadedTrack(
        context: Context,
        readerKey: String,
        surahNumber: Int
    ): QuranAudioTrack? {
        val file = downloadedFile(context, readerKey, surahNumber) ?: return null
        val timings = loadCachedTimings(context, readerKey, surahNumber).orEmpty()
        return QuranAudioTrack(
            audioUrl = file.absolutePath,
            timings = timings,
            timingAvailable = timings.isNotEmpty(),
            localFile = file
        )
    }

    suspend fun downloadTrack(
        context: Context,
        readerKey: String,
        surahNumber: Int
    ): QuranAudioTrack? = withContext(Dispatchers.IO) {
        require(surahNumber in 1..114)
        downloadedFile(context, readerKey, surahNumber)?.let { existing ->
            return@withContext loadDownloadedTrack(context, readerKey, surahNumber)
                ?: QuranAudioTrack(existing.absolutePath, emptyList(), false, existing)
        }

        val sourceTrack = loadTrack(context, readerKey, surahNumber) ?: return@withContext null
        val directory = audioDirectory(context, readerKey)
        if (!directory.exists() && !directory.mkdirs()) return@withContext null
        val destination = File(directory, "%03d.mp3".format(surahNumber))
        val temporary = File(directory, "%03d.mp3.part".format(surahNumber))
        var connection: HttpURLConnection? = null

        try {
            connection = (URL(sourceTrack.audioUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "ROYALPlusLauncher/1.0")
                setRequestProperty("Accept", "audio/mpeg,*/*")
            }
            if (connection.responseCode !in 200..299) return@withContext null
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                    output.fd.sync()
                }
            }
            if (temporary.length() < MIN_VALID_AUDIO_BYTES) {
                temporary.delete()
                return@withContext null
            }
            if (destination.exists() && !destination.delete()) {
                temporary.delete()
                return@withContext null
            }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            if (!destination.isFile || destination.length() < MIN_VALID_AUDIO_BYTES) {
                destination.delete()
                return@withContext null
            }
            sourceTrack.copy(
                audioUrl = destination.absolutePath,
                localFile = destination
            )
        } catch (_: Exception) {
            temporary.delete()
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun audioDirectory(context: Context, readerKey: String): File {
        val safeReader = readerKey.takeIf { it in setOf("afasy", "maher", "sudais", "mossad") } ?: "afasy"
        return File(File(context.filesDir, "quran_audio"), safeReader)
    }

    private fun loadMatchingTimings(source: ReadSource, surahNumber: Int): List<QuranVerseTiming>? {
        val reads = requestJson("$API/ayat_timing/reads") as? JSONArray ?: return null
        val sourceFolder = normalizeServer(source.server)
        var matchingReadId: Int? = null
        for (index in 0 until reads.length()) {
            val read = reads.optJSONObject(index) ?: continue
            val readName = normalizeName(read.optString("name"))
            val folder = normalizeServer(read.optString("folder_url"))
            val nameMatches = readName == normalizeName(source.name) ||
                readName.contains(normalizeName(source.name)) ||
                normalizeName(source.name).contains(readName)
            if (nameMatches && folder == sourceFolder) {
                matchingReadId = read.optInt("id").takeIf { it > 0 }
                break
            }
        }
        val readId = matchingReadId ?: return null
        val response = requestJson("$API/ayat_timing?surah=$surahNumber&read=$readId") as? JSONArray
            ?: return null
        val result = buildList {
            for (index in 0 until response.length()) {
                val item = response.optJSONObject(index) ?: continue
                val ayah = item.optInt("ayah", -1)
                val start = item.optInt("start_time", -1)
                val end = item.optInt("end_time", -1)
                if (ayah in 1..300 && start >= 0 && end > start) {
                    add(QuranVerseTiming(ayah, start, end))
                }
            }
        }.distinctBy { it.ayah }.sortedBy { it.startMs }
        return result.takeIf { it.isNotEmpty() }
    }

    private fun loadCachedTimings(
        context: Context,
        readerKey: String,
        surahNumber: Int
    ): List<QuranVerseTiming>? {
        val key = "quran_verse_timing_v1_${readerKey}_${surahNumber}"
        val raw = context.getSharedPreferences("royalplus_quran_audio", Context.MODE_PRIVATE)
            .getString(key, null) ?: return null
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        QuranVerseTiming(
                            item.getInt("ayah"),
                            item.getInt("start"),
                            item.getInt("end")
                        )
                    )
                }
            }.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun cacheTimings(
        context: Context,
        readerKey: String,
        surahNumber: Int,
        timings: List<QuranVerseTiming>
    ) {
        val array = JSONArray()
        timings.forEach { timing ->
            array.put(
                JSONObject()
                    .put("ayah", timing.ayah)
                    .put("start", timing.startMs)
                    .put("end", timing.endMs)
            )
        }
        context.getSharedPreferences("royalplus_quran_audio", Context.MODE_PRIVATE)
            .edit()
            .putString("quran_verse_timing_v1_${readerKey}_${surahNumber}", array.toString())
            .apply()
    }

    private fun requestJson(address: String): Any? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(address).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "ROYALPlusLauncher/1.0")
            }
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val trimmed = body.trim()
            when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> JSONObject(trimmed)
                else -> null
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun normalizeServer(value: String): String =
        value.trim().trimEnd('/').lowercase()

    private fun normalizeName(value: String): String {
        val noMarks = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noMarks.lowercase()
            .replace("ـ", "")
            .replace(Regex("[\\s\\p{Punct}\\u0640]+"), "")
            .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
            .replace("ى", "ي")
    }
}
