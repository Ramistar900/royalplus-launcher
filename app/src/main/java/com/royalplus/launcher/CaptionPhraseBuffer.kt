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
    fun format(text: String): String {
        // Replace unsupported scripts with a separator, not an empty string. This prevents
        // unrelated words on either side of a noisy fragment from being glued together.
        val sanitized = buildString(text.length) {
            text.forEach { character ->
                append(if (character.isAllowedCaptionCharacter()) character else ' ')
            }
        }

        // Build the result character-by-character instead of relying on replacement-group
        // syntax. This gives us deterministic spacing around punctuation on every JVM.
        val result = StringBuilder(sanitized.length)
        var pendingSpace = false

        sanitized.forEach { rawCharacter ->
            if (rawCharacter.isWhitespace()) {
                pendingSpace = result.isNotEmpty()
            } else {
                val character = when (rawCharacter) {
                    ',' -> '،'
                    ';' -> '؛'
                    '?' -> '؟'
                    else -> rawCharacter
                }

                if (character.isCaptionPunctuation()) {
                    while (result.isNotEmpty() && result.last().isWhitespace()) {
                        result.deleteCharAt(result.lastIndex)
                    }
                    result.append(character)
                    pendingSpace = false
                } else {
                    if (
                        result.isNotEmpty() &&
                        (pendingSpace || result.last().isCaptionPunctuation())
                    ) {
                        if (!result.last().isWhitespace()) result.append(' ')
                    }
                    result.append(character)
                    pendingSpace = false
                }
            }
        }

        val normalized = result.toString().trim()
        return normalized.takeIf { value -> value.any { it.isArabicLetter() } }.orEmpty()
    }

    private fun Char.isAllowedCaptionCharacter(): Boolean =
        this in '\u0600'..'\u06FF' ||
            this in '\u0750'..'\u077F' ||
            this in '\u08A0'..'\u08FF' ||
            this in '\uFB50'..'\uFDFF' ||
            this in '\uFE70'..'\uFEFF' ||
            isDigit() ||
            isWhitespace() ||
            this in setOf(',', ';', '.', '!', '?', ':', '…', '\'', '"', '(', ')', '-')

    private fun Char.isCaptionPunctuation(): Boolean =
        this in setOf('،', '؛', '؟', ',', ';', '.', '!', '?', ':', '…')

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
