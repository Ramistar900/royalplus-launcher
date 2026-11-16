package com.royalplus.launcher

/**
 * Groups adjacent speech-recognition fragments into complete subtitle phrases before
 * translation. This gives the translation engine enough sentence context to preserve
 * Arabic word order and phrasing instead of translating every short audio window alone.
 */
internal data class CaptionPhrase(
    val text: String,
    val language: String
)

internal class CaptionPhraseBuffer(
    private val maxCharacters: Int = 110,
    private val minimumWordsForSentenceEnd: Int = 5
) {
    private val buffer = StringBuilder()
    private var currentLanguage: String? = null

    fun isEmpty(): Boolean = buffer.isEmpty()

    fun append(fragment: String, language: String): List<CaptionPhrase> {
        val ready = mutableListOf<CaptionPhrase>()
        val normalized = fragment.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return ready

        if (buffer.isNotEmpty() && currentLanguage != language) {
            flush()?.let(ready::add)
        }
        if (buffer.isEmpty()) currentLanguage = language

        if (buffer.isNotEmpty() && !normalized.first().isPunctuation()) {
            buffer.append(' ')
        }
        buffer.append(normalized)

        val wordCount = buffer.toString().split(Regex("\\s+")).count { it.isNotBlank() }
        val sentenceEnded = buffer.lastOrNull() in setOf('.', '!', '?', '؟', '۔', '…')
        if (
            buffer.length >= maxCharacters ||
            (sentenceEnded && wordCount >= minimumWordsForSentenceEnd)
        ) {
            flush()?.let(ready::add)
        }
        return ready
    }

    fun flush(): CaptionPhrase? {
        val text = buffer.toString().trim()
        val language = currentLanguage
        buffer.clear()
        currentLanguage = null
        return if (text.isNotBlank() && !language.isNullOrBlank()) CaptionPhrase(text, language) else null
    }

    fun clear() {
        buffer.clear()
        currentLanguage = null
    }

    private fun Char.isPunctuation(): Boolean =
        this in setOf(',', '.', '!', '?', '؟', '،', ';', '؛', ':', '…')
}

/** Conservative punctuation and spacing cleanup for translated Arabic subtitles. */
internal object ArabicCaptionFormatter {
    fun format(text: String): String =
        text.replace(Regex("\\s+"), " ")
            .trim()
            .replace(Regex("\\s+([,;!?])"), "$1")
            .replace(",", "،")
            .replace(";", "؛")
            .replace("?", "؟")
            .replace(Regex("([،؛؟.!:])(?=\\S)"), "$1 ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
