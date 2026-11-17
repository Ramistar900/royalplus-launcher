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
        // English sound labels and non-verbal vocalizations
        "music", "instrumental", "instrumental music", "background music", "music playing", "music continues",
        "door opening", "opening door", "the door opening", "door closing", "closing door", "the door closing",
        "bell ringing", "ringing bell", "applause",
        "sighing", "sigh", "groaning", "groan", "moaning", "moan", "grunting", "grunt",
        "gasping", "gasp", "panting", "whimpering", "sobbing", "crying", "laughing", "laughter",
        "screaming", "scream", "ah", "ahh", "aah", "hmm", "mm",
        "birds chirping", "sound effect", "sound effects", "wind blowing",
        "rain falling", "thunder", "dog barking", "phone ringing",
        // French
        "musique", "musique instrumentale", "musique de fond", "soupir", "soupirs",
        "gémissement", "gémissements", "grogne", "gémir", "ouverture de porte",
        "ouverture de la porte", "porte qui s ouvre", "fermeture de porte",
        "bruit de porte", "bruit de cloche", "bruit de sonnette", "rires", "applaudissements",
        "bruits de pas", "vent qui souffle", "aboiement",
        // Spanish
        "música", "musica", "música instrumental", "música de fondo",
        "gemido", "gemidos", "quejido", "quejidos", "suspiro", "suspiros",
        "apertura de la puerta", "cierre de la puerta",
        "sonido de campana", "sonido del timbre", "aplausos",
        "viento", "lluvia", "trueno", "ladrido",
        // German
        "musik", "instrumentalmusik", "hintergrundmusik", "stöhnen", "ächzen", "seufzen", "öffnen der tür", "glockenklingeln", "schritte", "gelächter", "applaus",
        "hundegebell",
        // Italian
        "musica", "musica strumentale", "musica di sottofondo", "gemito", "gemiti", "sospiro", "sospiri", "apertura della porta", "chiusura della porta", "applausi",
        "vento", "pioggia", "tuono", "abbaiare",
        // Portuguese
        "música", "musica", "música instrumental", "música de fundo", "gemido", "gemidos", "suspiro", "suspiros", "abertura da porta", "toque da campainha", "sino tocando",
        "risadas", "vento", "chuva", "trovão", "latido",
        // Dutch
        "achtergrondmuziek", "instrumentale muziek", "openen van de deur", "gelach",
        "applaus", "regen", "donder",
        // Polish
        "muzyka instrumentalna", "muzyka w tle", "otwieranie drzwi", "zamykanie drzwi", "dźwięk dzwonka",
        "grzmot",
        // Ukrainian
        "інструментальна музика", "фонова музика", "відкривання дверей", "звук дзвінка",
        "грім",
        // Russian
        "музыка", "инструментальная музыка", "фоновая музыка", "стон", "стоны", "вздох", "вздохи",
        "стонет", "задыхается", "открывание двери", "звук открывающейся двери", "звон колокола",
        "колокольчик", "звук звонка", "ветер",
        "лай собаки",
        // Turkish
        "müzik", "enstrümantal müzik", "fon müziği", "inleme", "inlemeler", "ah", "iç çekme",
        "kapının açılması", "kapı açılışı", "kapı açılma sesi", "açılan kapı sesi", "zil sesi", "çan sesi",
        "gök gürültüsü",
        // Arabic
        "موسيقى", "موسيقى تصويرية", "موسيقى خلفية", "موسيقى آلية", "صوت موسيقى آلية",
        "آه", "اه", "آهات", "أهات", "أنين", "تأوه", "تأوهات", "يئن", "يتألم",
        "صوت ألم", "صوت تألم", "صرخة", "صرخات", "صراخ", "بكاء", "ضحك", "تنهد", "تنهدات",
        "شهقة", "شهقات", "لهاث", "تنفس", "صوت فتح الباب", "إغلاق الباب", "رنين الجرس", "صوت رنين الجرس", "خطوات",
        "رعد",
        // Hindi
        "पृष्ठभूमि संगीत", "दरवाज़ा खुलना", "दरवाज़ा बंद होना",
        "घंटी बजना", "कदमों की आवाज़", "तालियाँ",
        // Chinese (simplified/traditional common labels)
        "背景音乐", "开门声", "关门声", "铃声", "掌声",
        "背景音樂", "開門聲", "關門聲", "鈴聲", "腳步聲", "笑聲", "掌聲",
        // Japanese
        "背景音楽", "ドアが開く音", "ドアが閉まる音", "ベルの音",
        "拍手",
        // Korean
        "배경 음악", "문 여는 소리", "문 닫는 소리", "종소리",
        "박수"
    )

    private val nonSpeechPatterns = listOf(
        Regex("""^(?:(?:the|a|an) )?(?:sound|noise) of (?:(?:a|an|the) )?(?:door|bell)(?: opening| closing| ringing)?$"""),
        Regex("""^(?:(?:the|a) )?door (?:opening|closing)$"""),
        Regex("""^(?:opening|closing) (?:the |a )?door$"""),
        Regex("""^(?:(?:the|a) )?bell ringing$"""),
        Regex("""^(?:ah+|a+h+|uh+|h+m+|m+m+|huh+)$"""),
        Regex("""^(?:(?:a|the) )?(?:person )?(?:groaning|moaning|grunting|gasping|sighing|panting|whimpering|sobbing|crying|laughing|screaming)(?: in pain)?$""")
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
