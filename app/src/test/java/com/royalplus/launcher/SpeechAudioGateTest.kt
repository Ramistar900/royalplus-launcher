package com.royalplus.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechAudioGateTest {

    @Test
    fun rejectsInstrumentalMusicWithoutHumanSpeech() {
        assertTrue(
            SpeechAudioGate.isPredominantlyNonSpeech(
                listOf("Music" to 0.91f, "Musical instrument" to 0.82f, "Speech" to 0.04f)
            )
        )
    }

    @Test
    fun rejectsSingingWhenNoConversationIsDetected() {
        assertTrue(
            SpeechAudioGate.isPredominantlyNonSpeech(
                listOf("Singing" to 0.88f, "Music" to 0.79f, "Conversation" to 0.03f)
            )
        )
    }

    @Test
    fun rejectsStrongNonVerbalVocalizations() {
        assertTrue(
            SpeechAudioGate.isPredominantlyNonSpeech(
                listOf("Groaning" to 0.84f, "Speech" to 0.06f)
            )
        )
    }

    @Test
    fun preservesHumanDialogueOverBackgroundMusic() {
        assertFalse(
            SpeechAudioGate.isPredominantlyNonSpeech(
                listOf("Music" to 0.83f, "Speech" to 0.49f, "Conversation" to 0.42f)
            )
        )
    }

    @Test
    fun preservesClearSpeechEvenWhenMusicIsPresent() {
        assertFalse(
            SpeechAudioGate.isPredominantlyNonSpeech(
                listOf("Speech" to 0.91f, "Music" to 0.21f)
            )
        )
    }

    @Test
    fun doesNotRejectAmbiguousLowConfidenceEvents() {
        assertFalse(
            SpeechAudioGate.isPredominantlyNonSpeech(
                listOf("Music" to 0.44f, "Speech" to 0.18f)
            )
        )
    }
}
