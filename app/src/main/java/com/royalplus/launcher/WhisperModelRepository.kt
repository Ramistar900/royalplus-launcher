package com.royalplus.launcher

import android.content.Context
import com.k2fsa.sherpa.onnx.AudioTagging
import com.k2fsa.sherpa.onnx.AudioTaggingConfig
import com.k2fsa.sherpa.onnx.AudioTaggingModelConfig
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
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
    private const val VAD_BASE =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"
    private const val AUDIO_TAGGING_BASE =
        "https://huggingface.co/k2-fsa/sherpa-onnx-ced-tiny-audio-tagging-2024-04-19/resolve/main/"

    private const val AUDIO_TAGGING_MODEL_NAME = "model.int8.onnx"
    private const val AUDIO_TAGGING_LABELS_NAME = "class_labels_indices.csv"
    private const val AUDIO_TAGGING_MODEL_SHA256 =
        "73aa22e783115f6a4ae169b36089907e07d0fd44795595eddea9c1bfc74cc945"

    private const val ENCODER_NAME = "tiny-encoder.int8.onnx"
    private const val DECODER_NAME = "tiny-decoder.int8.onnx"
    private const val TOKENS_NAME = "tiny-tokens.txt"
    private const val VAD_NAME = "silero_vad.onnx"
    private const val VAD_SIZE = 643_854L
    private const val VAD_SHA256 = "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"

    private const val ENCODER_SIZE = 12_937_678L
    private const val DECODER_SIZE = 89_855_289L
    private const val ENCODER_SHA256 = "dd5531121627e7e184a6b8f6a6e353edf39b40e328032dfdc146b5a359a3e3d0"
    private const val DECODER_SHA256 = "dd29c14f2104c14c3aca7dc8e13308190bbe4c65be21c82f4b86a27d8326b26b"

    /**
     * A small on-device speech detector. Whisper remains the multilingual recognizer;
     * Silero only decides where a natural speech segment ends, so no fixed 1.5 s
     * cuts are imposed on a sentence.
     */
    fun createVoiceActivityDetector(
        context: Context,
        onProgress: (String) -> Unit
    ): Vad {
        val directory = File(context.filesDir, "royal_whisper_tiny").apply {
            if (!exists() && !mkdirs()) {
                throw IllegalStateException("تعذر إنشاء مجلد نموذج الصوت")
            }
        }
        val model = ensureFile(
            directory = directory,
            name = VAD_NAME,
            expectedBytes = VAD_SIZE,
            expectedSha256 = VAD_SHA256,
            baseUrl = VAD_BASE,
            onProgress = { onProgress("تهيئة كاشف الكلام: $it") }
        )
        val config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = model.absolutePath,
                threshold = 0.45f,
                minSilenceDuration = 0.45f,
                minSpeechDuration = 0.25f,
                windowSize = 512,
                // Safety valve for unusually long monologues; ordinary sentences end
                // on detected silence and are not cut at a fixed short interval.
                maxSpeechDuration = 14.0f
            ),
            sampleRate = SAMPLE_RATE,
            numThreads = 1,
            debug = false,
            provider = "cpu"
        )
        return Vad(config = config)
    }

    /**
     * Downloads a compact CED-tiny AudioSet classifier. It is used only as a gate:
     * discard a segment when music/singing or a non-verbal vocalization dominates
     * and the classifier does not detect a speech event.
     */
    fun createAudioTagger(
        context: Context,
        onProgress: (String) -> Unit
    ): AudioTagging {
        val directory = File(context.filesDir, "royal_audio_classifier").apply {
            if (!exists() && !mkdirs()) {
                throw IllegalStateException("تعذر إنشاء مجلد تصنيف الصوت")
            }
        }
        val model = ensureFile(
            directory = directory,
            name = AUDIO_TAGGING_MODEL_NAME,
            expectedBytes = 0L,
            expectedSha256 = AUDIO_TAGGING_MODEL_SHA256,
            baseUrl = AUDIO_TAGGING_BASE,
            onProgress = { onProgress("تنزيل مصنف الصوت: $it") }
        )
        val labels = ensureFile(
            directory = directory,
            name = AUDIO_TAGGING_LABELS_NAME,
            expectedBytes = 0L,
            expectedSha256 = null,
            baseUrl = AUDIO_TAGGING_BASE,
            onProgress = { onProgress("تنزيل قاموس مصنف الصوت: $it") }
        )
        if (!isAudioTaggingLabelsValid(labels)) {
            labels.delete()
            throw IllegalStateException("ملف تصنيفات الصوت غير صالح")
        }

        val config = AudioTaggingConfig(
            model = AudioTaggingModelConfig(
                ced = model.absolutePath,
                numThreads = 1,
                debug = false,
                provider = "cpu"
            ),
            labels = labels.absolutePath,
            topK = 5
        )
        return AudioTagging(config = config)
    }

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
        baseUrl: String = MODEL_BASE,
        onProgress: (String) -> Unit
    ): File {
        val destination = File(directory, name)
        if (
            isValid(destination, expectedBytes, expectedSha256, name == TOKENS_NAME) &&
            (name != AUDIO_TAGGING_LABELS_NAME || isAudioTaggingLabelsValid(destination))
        ) {
            onProgress("جاهز")
            return destination
        }

        destination.delete()
        val temporary = File(directory, "$name.part")
        temporary.delete()
        val connection = (URL(baseUrl + name).openConnection() as HttpURLConnection).apply {
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
            val totalBytes = (connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L)
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
                            onProgress("${downloaded / (1024 * 1024)} MB")
                        }
                    }
                }
            }

            if (expectedBytes > 0L && downloaded != expectedBytes) {
                throw IllegalStateException("حجم ملف نموذج الصوت غير مطابق؛ أُعيد تنزيله عند المحاولة التالية")
            }
            val actualHash = digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
            if (expectedSha256 != null && !actualHash.equals(expectedSha256, ignoreCase = true)) {
                throw IllegalStateException("فشل التحقق من سلامة ملف نموذج الصوت")
            }
            if (name == TOKENS_NAME && downloaded !in 700_000L..1_000_000L) {
                throw IllegalStateException("ملف قاموس الصوت غير مكتمل")
            }
            if (name == AUDIO_TAGGING_LABELS_NAME && !isAudioTaggingLabelsValid(temporary)) {
                throw IllegalStateException("ملف تصنيفات الصوت غير مكتمل")
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

    private fun isAudioTaggingLabelsValid(file: File): Boolean {
        if (!file.isFile || file.length() !in 10_000L..20_000L) return false
        val header = runCatching {
            BufferedInputStream(FileInputStream(file)).bufferedReader().use { it.readLine().orEmpty() }
        }.getOrDefault("")
        return header.contains("index", ignoreCase = true) &&
            header.contains("display_name", ignoreCase = true)
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
