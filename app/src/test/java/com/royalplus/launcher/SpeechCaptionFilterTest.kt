package com.royalplus.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechCaptionFilterTest {

    @Test
    fun filtersCommonSoundEffectLabels() {
        listOf(
            "Music",
            "[Instrumental music]",
            "Door opening",
            "The door opens",
            "A bell rings",
            "Footsteps",
            "La porte s'ouvre",
            "La cloche sonne",
            "Музыка",
            "Открывается дверь",
            "Звук звонка",
            "Kapı açılıyor",
            "Zil sesi"
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
            "Kapıyı aç lütfen."
        ).forEach { caption ->
            assertFalse("Dialogue must remain translatable: $caption", SpeechCaptionFilter.isNonSpeechLabel(caption))
        }
    }
}
