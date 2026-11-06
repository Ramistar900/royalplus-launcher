package com.royalplus.launcher

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads the multilingual Whisper Tiny int8 model only after the user enables captions.
 * All recognition is then performed locally through sherpa-onnx.
 */
internal object WhisperModelRepository {
    private const val MODEL_REVISION = "18e7b4cce9526312a66981ec3af7b9446b70db66"
    private const val MODEL_BASE =
        "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/$MODEL_REVISION/"

    private const val ENCODER_NAME = "tiny-encoder.int8.onnx"
    private const val DECODER_NAME = "tiny-decoder.int8.onnx"
    private const val TOKENS_NAME = "tiny-tokens.txt"

    private const val ENCODER_SIZE = 12_937_678L
    private const val DECODER_SIZE = 89_855_289L
    private const val ENCODER_SHA256 = "dd5531121627e7e184a6b8f6a6e353edf39b40e328032dfdc146b5a359a3e3d0"
    private const val DECODER_SHA256 = "dd29c14f2104c14c3aca7dc8e13308190bbe4c65be21c82f4b86a27d8326b26b"

    fun createRecognizer(
        context: Context,
        onProgress: (String) -> Unit
    ): OfflineRecognizer {
        val directory = File(context.filesDir, "royal_whisper_tiny").apply {
            if (!exists() && !mkdirs()) {
                throw IllegalStateException("تعذر إنشاء مجلد نموذج الصوت")
            }
        }

        val encoder = ensureFile(
            directory = directory,
            name = ENCODER_NAME,
            expectedBytes = ENCODER_SIZE,
            expectedSha256 = ENCODER_SHA256,
            onProgress = { onProgress("تنزيل نموذج الصوت 1/3: $it") }
        )
        val decoder = ensureFile(
            directory = directory,
            name = DECODER_NAME,
            expectedBytes = DECODER_SIZE,
            expectedSha256 = DECODER_SHA256,
            onProgress = { onProgress("تنزيل نموذج الصوت 2/3: $it") }
        )
        val tokens = ensureFile(
            directory = directory,
            name = TOKENS_NAME,
            expectedBytes = 0L,
            expectedSha256 = null,
            onProgress = { onProgress("تنزيل قاموس الصوت 3/3: $it") }
        )

        onProgress("تهيئة محرك التعرّف المحلي…")
        val whisper = OfflineWhisperModelConfig(
            encoder = encoder.absolutePath,
            decoder = decoder.absolutePath,
            language = "",
            task = "transcribe",
            tailPaddings = 1000,
            enableSegmentTimestamps = true
        )
        val model = OfflineModelConfig(
            whisper = whisper,
            tokens = tokens.absolutePath,
            numThreads = 2,
            debug = false,
            provider = "cpu"
        )
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = model
        )
        return OfflineRecognizer(config = config)
    }

    private fun ensureFile(
        directory: File,
        name: String,
        expectedBytes: Long,
        expectedSha256: String?,
        onProgress: (String) -> Unit
    ): File {
        val destination = File(directory, name)
        if (isValid(destination, expectedBytes, expectedSha256, name == TOKENS_NAME)) {
            onProgress("جاهز")
            return destination
        }

        destination.delete()
        val temporary = File(directory, "$name.part")
        temporary.delete()
        val connection = (URL(MODEL_BASE + name).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ROYALPlusLauncher/1.0")
        }

        try {
            connection.connect()
            val response = connection.responseCode
            if (response !in 200..299) {
                throw IllegalStateException("تعذر تنزيل نموذج الصوت (HTTP $response)")
            }
            val totalBytes = connection.getHeaderFieldLong("Content-Length", -1L)
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L

            BufferedInputStream(connection.inputStream).use { input ->
                BufferedOutputStream(FileOutputStream(temporary)).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedException("تم إيقاف تنزيل نموذج الصوت")
                        }
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        downloaded += count
                        if (totalBytes > 0L) {
                            val percent = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            onProgress("$percent%")
                        } else {
                            onProgress("\${downloaded / (1024 * 1024)} MB")
                        }
                    }
                }
            }

            if (expectedBytes > 0L && downloaded != expectedBytes) {
                throw IllegalStateException("حجم ملف نموذج الصوت غير مطابق؛ أُعيد تنزيله عند المحاولة التالية")
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (expectedSha256 != null && !actualHash.equals(expectedSha256, ignoreCase = true)) {
                throw IllegalStateException("فشل التحقق من سلامة ملف نموذج الصوت")
            }
            if (name == TOKENS_NAME && downloaded !in 700_000L..1_000_000L) {
                throw IllegalStateException("ملف قاموس الصوت غير مكتمل")
            }
            if (!temporary.renameTo(destination)) {
                throw IllegalStateException("تعذر حفظ ملف نموذج الصوت")
            }
            return destination
        } catch (error: Exception) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun isValid(
        file: File,
        expectedBytes: Long,
        expectedSha256: String?,
        isTokens: Boolean
    ): Boolean {
        if (!file.isFile) return false
        if (expectedBytes > 0L && file.length() != expectedBytes) return false
        if (isTokens && file.length() !in 700_000L..1_000_000L) return false
        if (expectedSha256 == null) return true
        return sha256(file).equals(expectedSha256, ignoreCase = true)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file)).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private const val SAMPLE_RATE = 16_000
}
