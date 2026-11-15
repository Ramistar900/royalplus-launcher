package com.royalplus.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechCaptionFilterTest {

    @Test
    fun filtersSoundEffectLabelsAcrossSourceLanguages() {
        listOf(
            "[Instrumental music]", "Background music", "Door opening", "Bell ringing",
            "bruit de cloche", "ouverture de la porte", "Música de fondo",
            "apertura de la puerta", "Instrumentalmusik", "Öffnen der Tür",
            "musica strumentale", "apertura della porta", "Música de fundo",
            "abertura da porta", "Achtergrondmuziek", "openen van de deur",
            "Muzyka w tle", "Otwieranie drzwi", "Фонова музика", "відкривання дверей",
            "Фоновая музыка", "открывание двери", "Звук звонка", "Fon müziği",
            "kapının açılması", "Zil sesi", "صوت موسيقى آلية", "صوت فتح الباب",
            "صوت رنين الجرس", "पृष्ठभूमि संगीत", "दरवाज़ा खुलना", "背景音乐", "开门声",
            "背景音樂", "開門聲", "背景音楽", "ドアが開く音", "배경 음악", "문 여는 소리"
        ).forEach { caption ->
            assertTrue("Expected sound label to be filtered: $caption", SpeechCaptionFilter.isNonSpeechLabel(caption))
        }
    }

    @Test
    fun keepsSungLyricsAndCompleteSpokenSentences() {
        listOf(
            "Music is the rhythm of my heart.",
            "Love me until the morning comes.",
            "Sing with me beneath the stars.",
            "The door opens when I call your name.",
            "The bell rings when we come home.",
            "La porte s'ouvre quand tu arrives.",
            "Se abre la puerta cuando llegas.",
            "La musica vive en mi corazon.",
            "Bu gece seninle sarki soyleyecegim.",
            "أغني لك طوال الليل.",
            "سأفتح الباب عندما تصل.",
            "Я буду петь для тебя всю ночь.",
            "Kapı açılıyor, içeri gel.",
            "La cloche sonne.",
            "Suena música.",
            "Müzik çalıyor.",
            "Звонит колокол.",
            "The music plays while we dance."
        ).forEach { caption ->
            assertFalse("Spoken words and sung lyrics must remain translatable: $caption",
                SpeechCaptionFilter.isNonSpeechLabel(caption))
        }
    }

    @Test
    fun keepsAmbiguousSingleWordsAndShortSpokenPhrases() {
        listOf(
            "Music", "Muziek", "Muzyka", "Музика", "Музыка", "Müzik",
            "موسيقى", "संगीत", "音乐", "音樂", "音楽", "음악",
            "Bell", "Footsteps", "Wind", "فتح الباب", "Open the door"
        ).forEach { caption ->
            assertFalse("Ambiguous spoken or sung words must remain translatable: $caption",
                SpeechCaptionFilter.isNonSpeechLabel(caption))
        }
    }

    @Test
    fun keepsDialogueThatMentionsTheSameThings() {
        listOf(
            "Open the door!",
            "Please close the door.",
            "I can hear the bell.",
            "This music is beautiful.",
            "The door is open.",
            "La musique est magnifique.",
            "Я слышу звонок.",
            "Kapıyı aç lütfen.",
            "Abre la puerta, por favor.",
            "Öffne bitte die Tür.",
            "Por favor, abra a porta.",
            "افتح الباب من فضلك.",
            "请把门打开。",
            "ドアを開けてください。"
        ).forEach { caption ->
            assertFalse("Dialogue must remain translatable: $caption", SpeechCaptionFilter.isNonSpeechLabel(caption))
        }
    }
}
