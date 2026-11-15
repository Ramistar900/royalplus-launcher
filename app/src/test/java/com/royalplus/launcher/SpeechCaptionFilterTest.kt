package com.royalplus.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechCaptionFilterTest {

    @Test
    fun filtersSoundEffectLabelsAcrossSourceLanguages() {
        listOf(
            "Music", "[Instrumental music]", "Door opening", "The door opens",
            "A bell rings", "Footsteps", "La porte s'ouvre", "La cloche sonne",
            "Música", "Se abre la puerta", "Musik", "Tür öffnet sich",
            "Musica", "La porta si apre", "Música de fundo", "A porta se abre",
            "Muziek", "Deur gaat open", "Muzyka", "Otwieranie drzwi",
            "Музика", "Відчиняються двері", "Музыка", "Открывается дверь",
            "Звук звонка", "Müzik", "Kapı açılıyor", "Zil sesi",
            "موسيقى", "فتح الباب", "صوت الجرس", "संगीत", "दरवाज़ा खुलना",
            "音乐", "开门声", "音樂", "開門聲", "音楽", "ドアが開く音",
            "음악", "문 여는 소리"
        ).forEach { caption ->
            assertTrue("Expected sound label to be filtered: $caption", SpeechCaptionFilter.isNonSpeechLabel(caption))
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
