package com.royalplus.launcher

import java.text.Normalizer
import java.util.Locale

/**
 * Conservative, source-language-independent filtering for Whisper captions that are only
 * sound descriptions. Exact whole-segment matching avoids suppressing real dialogue.
 *
 * Labels cover the common languages encountered by the translator. The filter is deliberately
 * not keyed to the selected translation language: it always examines Whisper's source text.
 */
internal object SpeechCaptionFilter {
    private val exactNonSpeechLabels = setOf(
        // English
        "music", "instrumental music", "background music", "music playing", "music continues",
        "door opening", "opening door", "the door opening", "door opens", "a door opens",
        "the door opens", "door closing", "closing door", "the door closing", "door closes",
        "a door closes", "the door closes", "bell", "the bell", "bell ringing", "ringing bell",
        "a bell rings", "the bell rings", "footsteps", "laughter", "laughing", "applause",
        "sighing", "birds chirping", "sound effect", "sound effects", "wind blowing",
        "rain falling", "thunder", "dog barking", "phone ringing",
        // French
        "musique", "musique instrumentale", "musique de fond", "ouverture de porte",
        "la porte s ouvre", "ouverture de la porte", "porte s ouvre", "porte qui s ouvre",
        "fermeture de porte", "la porte se ferme", "la cloche sonne", "la sonnette sonne",
        "bruit de porte", "bruit de cloche", "bruit de sonnette", "rires", "applaudissements",
        "bruits de pas", "vent qui souffle", "pluie", "tonnerre", "aboiement",
        // Spanish
        "música", "música instrumental", "música de fondo", "suena música",
        "apertura de la puerta", "se abre la puerta", "la puerta se abre",
        "cierre de la puerta", "se cierra la puerta", "suena la campana", "suena el timbre",
        "sonido de campana", "sonido del timbre", "pasos", "risas", "aplausos",
        "viento", "lluvia", "trueno", "ladrido",
        // German
        "musik", "instrumentalmusik", "hintergrundmusik", "tür öffnet sich",
        "öffnen der tür", "tür wird geöffnet", "tür schließt sich", "glocke läutet",
        "klingel läutet", "glockenklingeln", "schritte", "gelächter", "applaus",
        "wind", "regen", "donner", "hundegebell",
        // Italian
        "musica", "musica strumentale", "musica di sottofondo", "la porta si apre",
        "apertura della porta", "la porta si chiude", "chiusura della porta",
        "suona il campanello", "suona la campana", "passi", "risate", "applausi",
        "vento", "pioggia", "tuono", "abbaiare",
        // Portuguese
        "música", "música instrumental", "música de fundo", "a porta se abre",
        "abertura da porta", "a porta se fecha", "toque da campainha", "sino tocando",
        "passos", "risadas", "aplausos", "vento", "chuva", "trovão", "latido",
        // Dutch
        "muziek", "achtergrondmuziek", "instrumentale muziek", "deur gaat open",
        "openen van de deur", "deur gaat dicht", "bel gaat", "voetstappen", "gelach",
        "applaus", "wind", "regen", "donder",
        // Polish
        "muzyka", "muzyka instrumentalna", "muzyka w tle", "otwieranie drzwi",
        "drzwi się otwierają", "zamykanie drzwi", "dzwonek", "dźwięk dzwonka",
        "kroki", "śmiech", "brawa", "wiatr", "deszcz", "grzmot",
        // Ukrainian
        "музика", "інструментальна музика", "фонова музика", "відчиняються двері",
        "відкривання дверей", "зачиняються двері", "дзвінок", "звук дзвінка",
        "кроки", "сміх", "оплески", "вітер", "дощ", "грім",
        // Russian
        "инструментальная музыка", "фоновая музыка", "открывается дверь",
        "дверь открывается", "открывание двери", "звук открывающейся двери",
        "закрывается дверь", "дверь закрывается", "звон колокола", "звонит колокол",
        "колокольчик", "звук звонка", "шаги", "смех", "аплодисменты", "ветер",
        "дождь", "гром", "лай собаки",
        // Turkish
        "müzik", "enstrümantal müzik", "fon müziği", "müzik çalıyor",
        "kapının açılması", "kapı açılışı", "kapı açılıyor", "kapı açılma sesi",
        "açılan kapı sesi", "kapı kapanıyor", "zil çalıyor", "zil sesi", "çan sesi",
        "ayak sesleri", "kahkaha", "alkış", "rüzgar", "yağmur", "gök gürültüsü",
        // Arabic
        "موسيقى", "موسيقى تصويرية", "موسيقى خلفية", "فتح الباب", "الباب يفتح",
        "إغلاق الباب", "الباب يغلق", "رنين الجرس", "صوت الجرس", "خطوات",
        "ضحك", "تصفيق", "صوت الرياح", "صوت المطر", "رعد",
        // Hindi
        "संगीत", "पृष्ठभूमि संगीत", "दरवाज़ा खुलना", "दरवाज़ा बंद होना",
        "घंटी बजना", "कदमों की आवाज़", "हँसी", "तालियाँ",
        // Chinese (simplified/traditional common labels)
        "音乐", "背景音乐", "开门声", "关门声", "铃声", "脚步声", "笑声", "掌声",
        "音樂", "背景音樂", "開門聲", "關門聲", "鈴聲", "腳步聲", "笑聲", "掌聲",
        // Japanese
        "音楽", "背景音楽", "ドアが開く音", "ドアが閉まる音", "ベルの音",
        "足音", "笑い声", "拍手",
        // Korean
        "음악", "배경 음악", "문 여는 소리", "문 닫는 소리", "종소리",
        "발소리", "웃음소리", "박수"
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
