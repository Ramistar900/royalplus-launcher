package com.royalplus.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionPhraseBufferTest {

    @Test
    fun combinesShortRecognitionFragmentsBeforeTranslation() {
        val buffer = CaptionPhraseBuffer()
        assertTrue(buffer.append("I wanted to", "en").isEmpty())
        val emitted = buffer.append("tell you something important.", "en")
        assertEquals(listOf(CaptionPhrase("I wanted to tell you something important.", "en")), emitted)
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun flushesPreviousPhraseWhenTheDetectedLanguageChanges() {
        val buffer = CaptionPhraseBuffer()
        buffer.append("We should leave", "en")
        val emitted = buffer.append("Hadi gidelim", "tr")
        assertEquals(listOf(CaptionPhrase("We should leave", "en")), emitted)
        assertFalse(buffer.isEmpty())
        assertEquals(CaptionPhrase("Hadi gidelim", "tr"), buffer.flush())
    }

    @Test
    fun flushesLongPhrasesWithoutDroppingTheLastWords() {
        val buffer = CaptionPhraseBuffer(maxCharacters = 24)
        val emitted = buffer.append("This is a longer sentence that keeps going", "en")
        assertEquals(1, emitted.size)
        assertTrue(emitted.single().text.endsWith("going"))
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun waitsForEnoughWordsBeforeTreatingPunctuationAsSentenceEnd() {
        val buffer = CaptionPhraseBuffer(minimumWordsForSentenceEnd = 5)
        assertTrue(buffer.append("Yes.", "en").isEmpty())
        val emitted = buffer.append("I really mean it.", "en")
        assertEquals(CaptionPhrase("Yes. I really mean it.", "en"), emitted.single())
    }
}

class ArabicCaptionFormatterTest {

    @Test
    fun normalizesWhitespaceAndArabicPunctuation() {
        assertEquals(
            "السلام عليكم، كيف حالك؟",
            ArabicCaptionFormatter.format("  السلام   عليكم , كيف حالك ?  ")
        )
    }

    @Test
    fun leavesArabicWordsUnchanged() {
        assertEquals("سأعود إلى المنزل الآن.", ArabicCaptionFormatter.format("سأعود إلى المنزل الآن."))
    }

    @Test
    fun removesRandomLatinAndChineseFragmentsFromArabicCaptions() {
        assertEquals(
            "مرحباً يا أخي. كيف حالك؟",
            ArabicCaptionFormatter.format("مرحباً يا أخي .gkgdioj或者词语 كيف حالك?")
        )
    }

    @Test
    fun rejectsCaptionsThatContainNoArabicWords() {
        assertEquals("", ArabicCaptionFormatter.format("gkgdioj 音乐 123"))
    }

    @Test
    fun removesLatinLettersEvenWhenTheyTouchArabicWords() {
        assertEquals("مرحبا", ArabicCaptionFormatter.format("مرحبagkgdioj"))
    }
}
