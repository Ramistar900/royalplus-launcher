package com.royalplus.launcher

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Pure-Kotlin implementation of the SMaLL-100 SentencePiece-BPE tokenizer.
 * It reads the model's tokenizer.json and has no native tokenizer dependency.
 */
internal class Small100BpeTokenizer(
    private val vocab: Map<String, Long>,
    private val idToToken: Array<String>,
    private val mergeRank: Map<String, Int>,
    private val specialIds: Set<Long>,
    private val unknownId: Long
) {
    fun encode(text: String): LongArray {
        if (text.isBlank()) return longArrayOf()
        val ids = ArrayList<Long>()
        var previousWasUnknown = false

        normalize(text).split(WHITESPACE).forEach { piece ->
            if (piece.isEmpty()) return@forEach
            bpe(METASPACE + piece).forEach { symbol ->
                val id = vocab[symbol]
                if (id == null) {
                    if (!previousWasUnknown) ids.add(unknownId)
                    previousWasUnknown = true
                } else {
                    ids.add(id)
                    previousWasUnknown = false
                }
            }
        }
        return ids.toLongArray()
    }

    fun decode(ids: LongArray): String {
        val output = StringBuilder()
        ids.forEach { id ->
            if (id !in specialIds && id >= 0L && id < idToToken.size.toLong()) {
                output.append(idToToken[id.toInt()])
            }
        }
        return output.toString().replace(METASPACE, " ").trim()
    }

    private fun normalize(text: String): String {
        val output = StringBuilder(text.length)
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            val character = String(Character.toChars(codePoint))
            output.append(PUNCTUATION[character] ?: character)
            offset += Character.charCount(codePoint)
        }
        return output.toString()
    }

    private fun bpe(word: String): List<String> {
        val symbols = ArrayList<String>()
        var offset = 0
        while (offset < word.length) {
            val codePoint = word.codePointAt(offset)
            symbols.add(String(Character.toChars(codePoint)))
            offset += Character.charCount(codePoint)
        }
        if (symbols.size < 2) return symbols

        while (true) {
            var bestRank = Int.MAX_VALUE
            var bestIndex = -1
            for (index in 0 until symbols.size - 1) {
                val rank = mergeRank["${symbols[index]} ${symbols[index + 1]}"] ?: continue
                if (rank < bestRank) {
                    bestRank = rank
                    bestIndex = index
                }
            }
            if (bestIndex < 0) break
            symbols[bestIndex] = symbols[bestIndex] + symbols[bestIndex + 1]
            symbols.removeAt(bestIndex + 1)
        }
        return symbols
    }

    companion object {
        private const val METASPACE = "▁"
        private val WHITESPACE = Regex("\\s+")
        private val PUNCTUATION = mapOf(
            "，" to ",",
            "？" to "?",
            "！" to "!",
            "：" to ":",
            "；" to ";",
            "（" to "(",
            "）" to ")",
            "％" to "%",
            "　" to " "
        )

        fun fromFile(file: File): Small100BpeTokenizer {
            require(file.isFile && file.length() in 5_000_000L..7_000_000L) {
                "ملف قاموس الترجمة غير مكتمل"
            }
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val model = root.getJSONObject("model")
            val rawVocab = model.getJSONObject("vocab")
            val vocab = HashMap<String, Long>(rawVocab.length())
            var maximumId = 0
            val keys = rawVocab.keys()
            while (keys.hasNext()) {
                val token = keys.next()
                val id = rawVocab.getInt(token)
                vocab[token] = id.toLong()
                if (id > maximumId) maximumId = id
            }
            require(vocab.size >= 128_000 && vocab.containsKey("<unk>")) {
                "قاموس SMaLL-100 لا يطابق الإصدار المتوقع"
            }

            val idToToken = Array(maximumId + 1) { "" }
            vocab.forEach { (token, id) ->
                if (id >= 0L && id < idToToken.size.toLong()) idToToken[id.toInt()] = token
            }

            val mergesJson = model.getJSONArray("merges")
            val merges = HashMap<String, Int>(mergesJson.length())
            for (index in 0 until mergesJson.length()) {
                val item = mergesJson.get(index)
                val key = when (item) {
                    is String -> item
                    is JSONArray -> "${item.getString(0)} ${item.getString(1)}"
                    else -> throw IllegalArgumentException("صيغة دمج غير معروفة في tokenizer.json")
                }
                merges[key] = index
            }

            val specialIds = HashSet<Long>()
            val addedTokens = root.optJSONArray("added_tokens") ?: JSONArray()
            for (index in 0 until addedTokens.length()) {
                val item = addedTokens.getJSONObject(index)
                if (item.optBoolean("special", false)) {
                    specialIds.add(item.getLong("id"))
                }
            }
            return Small100BpeTokenizer(
                vocab = vocab,
                idToToken = idToToken,
                mergeRank = merges,
                specialIds = specialIds,
                unknownId = vocab["<unk>"] ?: 3L
            )
        }
    }
}
