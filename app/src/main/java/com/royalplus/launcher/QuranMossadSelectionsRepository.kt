package com.royalplus.launcher

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class QuranMossadSelection(
    val id: String,
    val title: String,
    val sourceUrl: String,
    val category: String
)

internal object QuranMossadSelectionsRepository {
    val selections = listOf(
        QuranMossadSelection("maryam_65_98", "سورة مريم • الآيات 65–98", "https://download.tvquran.com/download/selections/372/62159b8d57c51.mp3", "تلاوات نادرة"),
        QuranMossadSelection("maryam_61_68", "سورة مريم • الآيات 61–68", "https://download.tvquran.com/download/selections/372/62159b0b0953a.mp3", "تلاوات نادرة"),
        QuranMossadSelection("noor_36_38", "سورة النور • الآيات 36–38", "https://download.tvquran.com/download/selections/372/62159a3449b73.mp3", "تلاوات خاشعة"),
        QuranMossadSelection("nahl_97_99", "سورة النحل • الآيات 97–99", "https://download.tvquran.com/download/selections/372/6215999e8e319.mp3", "تلاوات خاشعة"),
        QuranMossadSelection("nahl_52_64", "سورة النحل • الآيات 52–64", "https://download.tvquran.com/download/selections/372/6215991772083.mp3", "تلاوات خاشعة"),
        QuranMossadSelection("ankaboot_46_69", "سورة العنكبوت • الآيات 46–69", "https://download.tvquran.com/download/selections/372/621597c672436.mp3", "تلاوات نادرة"),
        QuranMossadSelection("ankaboot_1_3", "سورة العنكبوت • الآيات 1–3", "https://download.tvquran.com/download/selections/372/621596ef62776.mp3", "تلاوات نادرة"),
        QuranMossadSelection("sajdah_17_19", "سورة السجدة • الآيات 17–19", "https://download.tvquran.com/download/selections/372/62159648895ba.mp3", "تلاوات نادرة"),
        QuranMossadSelection("sajdah_1_12", "سورة السجدة • الآيات 1–12", "https://download.tvquran.com/download/selections/372/621595e7a2b58.mp3", "تلاوات نادرة"),
        QuranMossadSelection("raad_28", "سورة الرعد • الآية 28", "https://download.tvquran.com/download/selections/372/6214992a959f2.mp3", "تلاوات نادرة"),
        QuranMossadSelection("raad_12_13", "سورة الرعد • الآيات 12–13", "https://download.tvquran.com/download/selections/372/6214985b0068d.mp3", "تلاوات نادرة"),
        QuranMossadSelection("raad_8", "سورة الرعد • الآية 8", "https://download.tvquran.com/download/selections/372/621498055ea51.mp3", "تلاوات نادرة"),
        QuranMossadSelection("baqarah_194_202", "سورة البقرة • الآيات 194–202", "https://download.tvquran.com/download/selections/372/621496f3baa5f.mp3", "تلاوات خاشعة"),
        QuranMossadSelection("araf_196_206", "سورة الأعراف • الآيات 196–206", "https://download.tvquran.com/download/selections/372/6214962203a7b.mp3", "تلاوات خاشعة"),
        QuranMossadSelection("imran_1_14", "سورة آل عمران • الآيات 1–14", "https://download.tvquran.com/download/selections/372/6214955a11d95.mp3", "تلاوات نادرة"),
        QuranMossadSelection("ibrahim_42_47", "سورة إبراهيم • الآيات 42–47", "https://download.tvquran.com/download/selections/372/62149512104b5.mp3", "تلاوات خاشعة"),
        QuranMossadSelection("muzzammil_full", "سورة المزمل • تلاوة كاملة", "https://download.tvquran.com/download/selections/180/5f9b61cc1f6c4.mp3", "تلاوات نادرة")
    )

    fun downloadedIds(context: Context): Set<String> =
        selections.asSequence().filter { fileFor(context, it).let { file -> file.isFile && file.length() > 1_024 } }
            .map { it.id }.toSet()

    fun fileFor(context: Context, selection: QuranMossadSelection): File =
        File(File(context.filesDir, "quran_audio/mossad_selections"), "${selection.id}.mp3")

    suspend fun download(context: Context, selection: QuranMossadSelection): File? =
        withContext(Dispatchers.IO) {
            val destination = fileFor(context, selection)
            if (destination.isFile && destination.length() > 1_024) return@withContext destination
            val directory = destination.parentFile ?: return@withContext null
            if (!directory.exists() && !directory.mkdirs()) return@withContext null
            val temporary = File(directory, "${selection.id}.mp3.part")
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(selection.sourceUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 45_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "ROYALPlusLauncher/1.0")
                    setRequestProperty("Accept", "audio/mpeg,*/*")
                }
                if (connection.responseCode !in 200..299) return@withContext null
                connection.inputStream.use { input ->
                    FileOutputStream(temporary).use { output ->
                        input.copyTo(output, 64 * 1024)
                        output.fd.sync()
                    }
                }
                if (temporary.length() <= 1_024) {
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
                destination.takeIf { it.isFile && it.length() > 1_024 }
            } catch (_: Exception) {
                temporary.delete()
                null
            } finally {
                connection?.disconnect()
            }
        }
}
