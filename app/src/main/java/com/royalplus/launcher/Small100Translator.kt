package com.royalplus.launcher

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * SMaLL-100 INT8 encoder/merged-decoder runtime. All inference runs locally.
 * The caller must invoke translate() on a worker thread, never the Android main thread.
 */
internal class Small100Translator private constructor(
    private val environment: OrtEnvironment,
    private val encoder: OrtSession,
    private val decoder: OrtSession,
    private val tokenizer: Small100BpeTokenizer,
    private val languageToId: Map<String, Long>
) : Closeable {

    @Volatile
    private var closed = false

    @Synchronized
    fun translate(text: String, targetLanguage: String = "ar", maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS): String {
        check(!closed) { "محرك الترجمة مغلق." }
        if (text.isBlank()) return ""
        val targetId = languageToId[targetLanguage]
            ?: throw IllegalArgumentException("لغة الهدف غير مدعومة في نموذج SMaLL-100: $targetLanguage")

        val encodedText = tokenizer.encode(text)
        val inputIdsArray = LongArray(encodedText.size + 2)
        inputIdsArray[0] = targetId
        encodedText.copyInto(inputIdsArray, destinationOffset = 1)
        inputIdsArray[inputIdsArray.lastIndex] = EOS_ID
        val attentionMaskArray = LongArray(inputIdsArray.size) { 1L }

        val inputIds = longTensor(inputIdsArray)
        val attentionMask = longTensor(attentionMaskArray)
        var encoderResult: OrtSession.Result? = null
        var firstDecoderResult: OrtSession.Result? = null
        var latestDecoderResult: OrtSession.Result? = null
        val emptyPast = createEmptyPast()

        try {
            encoderResult = encoder.run(
                mapOf(
                    "input_ids" to inputIds,
                    "attention_mask" to attentionMask
                )
            )
            val encoderHiddenStates = encoderResult.get(0) as OnnxTensor
            val outputIds = ArrayList<Long>()
            var currentToken = DECODER_START_ID
            var useCache = false
            val tokenLimit = maxOutputTokens.coerceIn(16, 128)

            for (step in 0 until tokenLimit) {
                val stepInput = longTensor(longArrayOf(currentToken))
                val cacheFlag = booleanTensor(useCache)
                val feeds = HashMap<String, OnnxTensor>(4 + LAYER_COUNT * 4)
                feeds["encoder_attention_mask"] = attentionMask
                feeds["input_ids"] = stepInput
                feeds["encoder_hidden_states"] = encoderHiddenStates
                feeds["use_cache_branch"] = cacheFlag

                for (layer in 0 until LAYER_COUNT) {
                    for (kind in listOf("decoder", "encoder")) {
                        for (keyValue in listOf("key", "value")) {
                            val inputName = "past_key_values.$layer.$kind.$keyValue"
                            val cacheTensor = when {
                                firstDecoderResult == null -> emptyPast.getValue(inputName)
                                kind == "encoder" -> outputTensor(
                                    firstDecoderResult!!,
                                    "present.$layer.encoder.$keyValue"
                                )
                                else -> outputTensor(
                                    latestDecoderResult!!,
                                    "present.$layer.decoder.$keyValue"
                                )
                            }
                            feeds[inputName] = cacheTensor
                        }
                    }
                }

                val result = try {
                    decoder.run(feeds)
                } finally {
                    stepInput.close()
                    cacheFlag.close()
                }

                if (firstDecoderResult == null) {
                    firstDecoderResult = result
                    latestDecoderResult = result
                } else {
                    val oldLatest = latestDecoderResult
                    if (oldLatest != null && oldLatest !== firstDecoderResult) {
                        oldLatest.close()
                    }
                    latestDecoderResult = result
                }

                val nextToken = argmaxLast(outputTensor(result, "logits"))
                if (nextToken == EOS_ID) break
                outputIds.add(nextToken)
                if (isDegenerate(outputIds)) break
                currentToken = nextToken
                useCache = true
            }
            return tokenizer.decode(outputIds.toLongArray())
        } finally {
            emptyPast.values.forEach { runCatching { it.close() } }
            val first = firstDecoderResult
            val latest = latestDecoderResult
            if (latest != null && latest !== first) runCatching { latest.close() }
            if (first != null) runCatching { first.close() }
            runCatching { encoderResult?.close() }
            inputIds.close()
            attentionMask.close()
        }
    }

    private fun outputTensor(result: OrtSession.Result, name: String): OnnxTensor =
        result.get(name).orElseThrow { IllegalStateException("مخرج ONNX مفقود: $name") } as OnnxTensor

    private fun longTensor(values: LongArray): OnnxTensor =
        OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(values),
            longArrayOf(1L, values.size.toLong())
        )

    private fun booleanTensor(value: Boolean): OnnxTensor {
        val buffer = ByteBuffer.allocateDirect(1).order(ByteOrder.nativeOrder())
        buffer.put(if (value) 1.toByte() else 0.toByte())
        buffer.flip()
        return OnnxTensor.createTensor(
            environment,
            buffer,
            longArrayOf(1L),
            OnnxJavaType.BOOL
        )
    }

    private fun createEmptyPast(): Map<String, OnnxTensor> {
        val result = HashMap<String, OnnxTensor>(LAYER_COUNT * 4)
        for (layer in 0 until LAYER_COUNT) {
            for (kind in listOf("decoder", "encoder")) {
                for (keyValue in listOf("key", "value")) {
                    val name = "past_key_values.$layer.$kind.$keyValue"
                    result[name] = OnnxTensor.createTensor(
                        environment,
                        FloatBuffer.allocate(0),
                        longArrayOf(1L, HEAD_COUNT.toLong(), 0L, HEAD_DIM.toLong())
                    )
                }
            }
        }
        return result
    }

    private fun argmaxLast(logits: OnnxTensor): Long {
        val shape = logits.info.shape
        require(shape.size == 3 && shape[0] == 1L && shape[2] > 0L) {
            "شكل logits غير صالح في نموذج الترجمة."
        }
        val vocabularySize = shape[2].toInt()
        val start = (shape[1].toInt() - 1) * vocabularySize
        val values = logits.floatBuffer
        var bestIndex = -1
        var bestValue = Float.NEGATIVE_INFINITY
        for (index in 0 until vocabularySize) {
            if (index == PAD_ID.toInt()) continue
            val value = values.get(start + index)
            if (value > bestValue) {
                bestValue = value
                bestIndex = index
            }
        }
        check(bestIndex >= 0 && bestValue.isFinite()) { "تعذر استخراج رمز من مخرجات الترجمة." }
        return bestIndex.toLong()
    }

    private fun isDegenerate(tokens: List<Long>): Boolean {
        val count = tokens.size
        if (count >= 6 && (count - 6 until count - 1).all { tokens[it] == tokens[it + 1] }) {
            return true
        }
        for (period in 2..4) {
            if (count < period * 3) continue
            var repeating = true
            for (offset in 0 until period * 2) {
                if (tokens[count - 1 - offset] != tokens[count - 1 - offset - period]) {
                    repeating = false
                    break
                }
            }
            if (repeating) return true
        }
        return false
    }

    fun supportsSourceLanguage(language: String): Boolean =
        languageToId.containsKey(language.lowercase())

    override fun close() {
        synchronized(this) {
            if (closed) return
            closed = true
            runCatching { decoder.close() }
            runCatching { encoder.close() }
        }
    }

    companion object {
        private const val LAYER_COUNT = 3
        private const val HEAD_COUNT = 16
        private const val HEAD_DIM = 64
        private const val DECODER_START_ID = 2L
        private const val EOS_ID = 2L
        private const val PAD_ID = 1L
        private const val DEFAULT_MAX_OUTPUT_TOKENS = 64

        fun create(files: Small100ModelRepository.ModelFiles): Small100Translator {
            val tokenizer = Small100BpeTokenizer.fromFile(files.tokenizer)
            val langJson = JSONObject(files.languageTokens.readText(Charsets.UTF_8))
            val rawLanguages = langJson.getJSONObject("lang_to_id")
            val languageToId = HashMap<String, Long>(rawLanguages.length())
            val iterator = rawLanguages.keys()
            while (iterator.hasNext()) {
                val code = iterator.next()
                languageToId[code] = rawLanguages.getLong(code)
            }
            require(languageToId.size == 100 && languageToId.containsKey("ar")) {
                "قائمة اللغات في نموذج SMaLL-100 غير مكتملة."
            }

            val environment = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions()
            options.setIntraOpNumThreads(2)
            options.setInterOpNumThreads(1)
            var encoder: OrtSession? = null
            try {
                encoder = environment.createSession(files.encoder.absolutePath, options)
                val decoder = environment.createSession(files.decoder.absolutePath, options)
                return Small100Translator(environment, encoder, decoder, tokenizer, languageToId)
            } catch (error: Exception) {
                runCatching { encoder?.close() }
                throw error
            } finally {
                options.close()
            }
        }
    }
}
