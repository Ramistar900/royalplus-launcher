package com.royalplus.launcher

import android.content.Context
import org.json.JSONObject

internal data class QuranVerse(
    val number: Int,
    val text: String
)

internal data class QuranSurah(
    val number: Int,
    val name: String,
    val revelationType: String,
    val verses: List<QuranVerse>
) {
    val numberedVerseCount: Int
        get() = verses.count { it.number > 0 }
}

internal object QuranTextRepository {
    fun load(context: Context): List<QuranSurah> {
        val json = context.assets.open("quran-uthmani.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val root = JSONObject(json)
        val source = root.getJSONObject("metadata").optString("source")
        require(source == "Tanzil Project") { "Unexpected Quran text source" }
        val surahArray = root.getJSONArray("surahs")
        require(surahArray.length() == 114) { "Incomplete Quran chapter index" }

        return buildList {
            for (surahIndex in 0 until surahArray.length()) {
                val surahObject = surahArray.getJSONObject(surahIndex)
                val verseArray = surahObject.getJSONArray("ayahs")
                val verses = buildList {
                    for (verseIndex in 0 until verseArray.length()) {
                        val verseObject = verseArray.getJSONObject(verseIndex)
                        val number = verseObject.getInt("number")
                        val text = verseObject.getString("text")
                        require(number >= 0 && text.isNotBlank()) { "Invalid Quran verse entry" }
                        add(QuranVerse(number, text))
                    }
                }
                add(
                    QuranSurah(
                        number = surahObject.getInt("number"),
                        name = surahObject.getString("name"),
                        revelationType = surahObject.optString("loc"),
                        verses = verses
                    )
                )
            }
        }.sortedBy { it.number }
    }

    fun normalizeForSearch(value: String): String =
        value.lowercase()
            .replace("ـ", "")
            .replace(Regex("[\u064B-\u065F\u0670\u06D6-\u06ED]"), "")
            .trim()
}
