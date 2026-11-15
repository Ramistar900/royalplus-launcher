package com.royalplus.launcher

import java.text.Normalizer
import java.util.Locale

/**
 * Filters short Whisper outputs that describe background sound instead of transcribing speech.
 *
 * Keep this deliberately conservative: a caption is filtered only when the whole recognized
 * segment looks like a common sound label. Natural dialogue mentioning a door, bell, or music
 * remains eligible for translation.
 */
internal object SpeechCaptionFilter {
    private val exactNonSpeechLabels = setOf(
        // English
        "music",
        "instrumental music",
        "background music",
        "music playing",
        "music continues",
        "door opening",
        "opening door",
        "the door opening",
        "door opens",
        "a door opens",
        "the door opens",
        "door closing",
        "closing door",
        "the door closing",
        "door closes",
        "a door closes",
        "the door closes",
        "bell",
        "the bell",
        "bell ringing",
        "ringing bell",
        "a bell rings",
        "the bell rings",
        "footsteps",
        "laughter",
        "laughing",
        "applause",
        "sighing",
        "birds chirping",
        "sound effect",
        "sound effects",
        // French
        "musique",
        "musique instrumentale",
        "musique de fond",
        "ouverture de porte",
        "la porte s ouvre",
        "porte qui s ouvre",
        "fermeture de porte",
        "la porte se ferme",
        "la cloche sonne",
        "la sonnette sonne",
        "bruit de porte",
        "bruit de cloche",
        "bruit de sonnette",
        "pas",
        "rires",
        "applaudissements",
        // Russian
        "музыка",
        "инструментальная музыка",
        "фоновая музыка",
        "открывается дверь",
        "дверь открывается",
        "звук открывающейся двери",
        "закрывается дверь",
        "дверь закрывается",
        "звонок",
        "звон колокола",
        "звук звонка",
        "шаги",
        "смех",
        "аплодисменты",
        // Turkish
        "müzik",
        "enstrümantal müzik",
        "fon müziği",
        "kapı açılıyor",
        "kapı açılma sesi",
        "açılan kapı sesi",
        "kapı kapanıyor",
        "zil çalıyor",
        "zil sesi",
        "çan sesi",
        "ayak sesleri",
        "kahkaha",
        "alkış"
    )

    private val nonSpeechPatterns = listOf(
        Regex("""^(?:(?:the|a|an) )?(?:sound|noise) of (?:(?:a|an|the) )?(?:door|bell)(?: opening| closing| ringing)?$"""),
        Regex("""^(?:(?:the|a) )?door (?:opening|opens|closing|closes)$"""),
        Regex("""^(?:opening|closing) (?:the |a )?door$"""),
        Regex("""^(?:(?:the|a) )?bell (?:ringing|rings)$""")
    )

    fun isNonSpeechLabel(text: String): Boolean {
        val normalized = normalize(text)
        if (normalized.isBlank()) return false
        return normalized in exactNonSpeechLabels || nonSpeechPatterns.any { it.matches(normalized) }
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("""[\p{P}\p{S}]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
