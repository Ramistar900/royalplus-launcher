package com.royalplus.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechCaptionFilterTest {

    @Test
    fun filtersSoundEffectLabelsAcrossSourceLanguages() {
        listOf(
            "Music", "[Instrumental music]", "Door opening", "Bell ringing", "Footsteps",
            "bruit de cloche", "ouverture de la porte", "Música de fondo",
            "apertura de la puerta", "Instrumentalmusik", "Öffnen der Tür",
            "musica strumentale", "apertura della porta", "Música de fundo",
            "abertura da porta", "Muziek", "openen van de deur",
            "Muzyka", "Otwieranie drzwi", "Музика", "відкривання дверей",
            "Музыка", "открывание двери", "Звук звонка", "Müzik",
            "kapının açılması", "Zil sesi", "موسيقى", "فتح الباب",
            "صوت الجرس", "संगीत", "दरवाज़ा खुलना", "音乐", "开门声",
            "音樂", "開門聲", "音楽", "ドアが開く音", "음악", "문 여는 소리"
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
