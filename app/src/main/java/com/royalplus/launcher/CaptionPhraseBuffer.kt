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

/** Removes foreign-script fragments and normalizes the remaining Arabic subtitle. */
internal object ArabicCaptionFormatter {
    private val nonArabicCharacters = Regex(
        """[^\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF\uFB50-\uFDFF\uFE70-\uFEFF0-9٠-٩۰-۹\s،؛؟.!:…'"()\-]"""
    )

    fun format(text: String): String {
        val arabicOnly = text.replace(nonArabicCharacters, "")
        val normalized = arabicOnly
            .replace(Regex("""\s+"""), " ")
            .trim()
            .replace(Regex("""\s+([,;!?،؛؟.!:])"""), "$1")
            .replace(",", "،")
            .replace(";", "؛")
            .replace("?", "؟")
            .replace(Regex("""([،؛؟.!:])(?=\S)"""), "$1 ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        return normalized.takeIf { value -> value.any { it.isArabicLetter() } }.orEmpty()
    }

    private fun Char.isArabicLetter(): Boolean =
        isLetter() && (
            this in '\u0621'..'\u064A' ||
                this in '\u066E'..'\u06D3' ||
                this in '\u06FA'..'\u06FC' ||
                this in '\u0750'..'\u077F' ||
                this in '\u08A0'..'\u08FF' ||
                this in '\uFB50'..'\uFDFF' ||
                this in '\uFE70'..'\uFEFC'
            )
}
