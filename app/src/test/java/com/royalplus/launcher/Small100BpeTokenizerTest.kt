package com.royalplus.launcher

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Small100BpeTokenizerTest {
    @Test
    fun mergesByLowestRankAndDecodesMetaspace() {
        val tokenizer = Small100BpeTokenizer(
            vocab = mapOf("▁hi!" to 42L, "<unk>" to 3L),
            idToToken = Array(43) { "" }.also { it[42] = "▁hi!" },
            mergeRank = mapOf(
                "▁ h" to 0,
                "▁h i" to 1,
                "▁hi !" to 2
            ),
            specialIds = setOf(1L, 2L, 3L),
            unknownId = 3L
        )

        val encoded = tokenizer.encode("hi!")
        assertArrayEquals(longArrayOf(42L), encoded)
        assertEquals("hi!", tokenizer.decode(encoded))
    }

    @Test
    fun unknownSymbolsAreFusedIntoOneUnknownToken() {
        val tokenizer = Small100BpeTokenizer(
            vocab = mapOf("<unk>" to 3L, "▁" to 4L, "a" to 5L),
            idToToken = arrayOf("", "", "", "<unk>", "▁", "a"),
            mergeRank = emptyMap(),
            specialIds = setOf(1L, 2L, 3L),
            unknownId = 3L
        )

        assertArrayEquals(longArrayOf(4L, 5L, 3L), tokenizer.encode("ab"))
    }
}
