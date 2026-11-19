package com.royalplus.launcher

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads SMaLL-100 into private app storage only after an explicit user action.
 * No model weights are bundled into the APK and downloads require an unmetered network.
 */
internal object Small100ModelRepository {
    private const val REVISION = "a0d8498bbf489866eeff5e53b53f6997d2b299db"
    private const val BASE_URL =
        "https://huggingface.co/casawolice/small100-onnx/resolve/$REVISION/"
    private const val MODEL_DIRECTORY = "royal_small100"
    private const val MIN_FREE_BYTES = 680L * 1024L * 1024L

    data class ModelFiles(
        val encoder: File,
        val decoder: File,
        val tokenizer: File,
        val languageTokens: File
    )

    private data class RemoteFile(
        val name: String,
        val relativePath: String,
        val minimumBytes: Long,
        val maximumBytes: Long
    )

    private val remoteFiles = listOf(
        RemoteFile("encoder_model.onnx", "onnx/encoder_model.onnx", 270_000_000L, 315_000_000L),
        RemoteFile("decoder_model_merged.onnx", "onnx/decoder_model_merged.onnx", 300_000_000L, 350_000_000L),
        RemoteFile("tokenizer.json", "tokenizer.json", 5_000_000L, 7_000_000L),
        RemoteFile("lang_tokens.json", "lang_tokens.json", 1_000L, 5_000L)
    )

    fun installedFiles(context: Context): ModelFiles? {
        val directory = File(context.filesDir, MODEL_DIRECTORY)
        val files = remoteFiles.associateBy { it.name }
        val encoder = File(directory, files.getValue("encoder_model.onnx").name)
        val decoder = File(directory, files.getValue("decoder_model_merged.onnx").name)
        val tokenizer = File(directory, files.getValue("tokenizer.json").name)
        val languageTokens = File(directory, files.getValue("lang_tokens.json").name)
        if (!remoteFiles.all { spec ->
                val file = File(directory, spec.name)
                file.isFile && file.length() in spec.minimumBytes..spec.maximumBytes
            }
        ) return null
        if (!validLanguageTokens(languageTokens)) return null
        return ModelFiles(encoder, decoder, tokenizer, languageTokens)
    }

    fun ensureDownloaded(
        context: Context,
        onProgress: (String) -> Unit
    ): ModelFiles {
        check(isUnmeteredNetwork(context)) {
            "لحماية باقة الإنترنت، تنزيل نموذج الترجمة الكبير متاح عبر Wi-Fi أو اتصال غير محدود فقط."
        }
        val directory = File(context.filesDir, MODEL_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("تعذر إنشاء مجلد نموذج الترجمة")
        }

        val stat = StatFs(context.filesDir.absolutePath)
        if (stat.availableBytes < MIN_FREE_BYTES) {
            throw IllegalStateException("يلزم توفير 680 ميغابايت على الأقل من المساحة الحرة لتنزيل نموذج الترجمة.")
        }

        remoteFiles.forEachIndexed { index, spec ->
            val destination = File(directory, spec.name)
            if (destination.isFile && destination.length() in spec.minimumBytes..spec.maximumBytes) {
                onProgress("${index + 1}/4 — ${spec.name}: جاهز")
                return@forEachIndexed
            }
            download(spec, destination, index + 1, onProgress)
        }

        val files = installedFiles(context)
            ?: throw IllegalStateException("لم تكتمل ملفات نموذج الترجمة أو فشل التحقق منها.")
        // Parse both metadata files before creating ONNX sessions.
        Small100BpeTokenizer.fromFile(files.tokenizer)
        check(validLanguageTokens(files.languageTokens)) {
            "ملف لغات SMaLL-100 غير صالح."
        }
        return files
    }

    private fun download(
        spec: RemoteFile,
        destination: File,
        fileNumber: Int,
        onProgress: (String) -> Unit
    ) {
        val partial = File(destination.parentFile, destination.name + ".part")
        var existingBytes = if (partial.isFile) partial.length() else 0L
        if (existingBytes !in 0L until spec.maximumBytes) {
            partial.delete()
            existingBytes = 0L
        }

        val connection = (URL(BASE_URL + spec.relativePath).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 180_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ROYALPlusLauncher/1.0")
            if (existingBytes > 0L) setRequestProperty("Range", "bytes=$existingBytes-")
        }

        try {
            connection.connect()
            val response = connection.responseCode
            if (response !in 200..299) {
                throw IllegalStateException("تعذر تنزيل ${spec.name} (HTTP $response)")
            }
            val append = existingBytes > 0L && response == HttpURLConnection.HTTP_PARTIAL
            if (!append) {
                partial.delete()
                existingBytes = 0L
            }

            val responseLength = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            val expectedTotal = if (append) {
                connection.getHeaderField("Content-Range")
                    ?.substringAfterLast('/')
                    ?.toLongOrNull()
                    ?: if (responseLength > 0L) existingBytes + responseLength else -1L
            } else {
                responseLength
            }
            if (expectedTotal > 0L && expectedTotal !in spec.minimumBytes..spec.maximumBytes) {
                throw IllegalStateException("حجم ${spec.name} لا يطابق النموذج المتوقع.")
            }

            var downloaded = existingBytes
            BufferedInputStream(connection.inputStream).use { input ->
                BufferedOutputStream(FileOutputStream(partial, append)).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedException("تم إيقاف تنزيل نموذج الترجمة.")
                        }
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        val percent = if (expectedTotal > 0L) {
                            ((downloaded * 100L) / expectedTotal).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }
                        onProgress(
                            if (percent >= 0) "${fileNumber}/4 — ${spec.name}: $percent%"
                            else "${fileNumber}/4 — ${spec.name}: ${downloaded / (1024 * 1024)} MB"
                        )
                    }
                }
            }

            if (partial.length() !in spec.minimumBytes..spec.maximumBytes) {
                throw IllegalStateException("لم يكتمل تنزيل ${spec.name}; ستتم متابعة التنزيل عند المحاولة التالية.")
            }
            if (!partial.renameTo(destination)) {
                throw IllegalStateException("تعذر حفظ ${spec.name} في مساحة التطبيق.")
            }
        } catch (error: Exception) {
            // Keep a partial download so a later explicit retry can resume it.
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun validLanguageTokens(file: File): Boolean = runCatching {
        val root = JSONObject(file.readText(Charsets.UTF_8))
        val map = root.getJSONObject("lang_to_id")
        map.length() == 100 && map.has("ar") && root.optInt("eos", -1) == 2
    }.getOrDefault(false)

    private fun isUnmeteredNetwork(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
