package com.royalplus.launcher

/**
 * AudioSet labels from the local CED classifier are used as a conservative second gate.
 * A music/non-verbal event suppresses a segment only when it is strong and no human
 * speech/conversation event is detected with meaningful confidence.
 */
internal object SpeechAudioGate {
    private const val NON_SPEECH_THRESHOLD = 0.68f
    private const val SPEECH_PRESENCE_THRESHOLD = 0.32f

    private val nonSpeechLabels = listOf(
        "music",
        "singing",
        "musical instrument",
        "instrument",
        "sigh",
        "groan",
        "moan",
        "gasp",
        "whimper",
        "sobbing",
        "crying"
    )

    private val speechLabels = listOf(
        "speech",
        "conversation",
        "narration",
        "talk",
        "monologue"
    )

    fun isPredominantlyNonSpeech(events: List<Pair<String, Float>>): Boolean {
        if (events.isEmpty()) return false

        val normalized = events.map { (name, probability) ->
            name.lowercase().trim() to probability.coerceIn(0f, 1f)
        }
        val nonSpeechProbability = normalized
            .filter { (name, _) -> nonSpeechLabels.any(name::contains) }
            .maxOfOrNull { it.second } ?: 0f
        val speechProbability = normalized
            .filter { (name, _) -> speechLabels.any(name::contains) }
            .maxOfOrNull { it.second } ?: 0f

        return nonSpeechProbability >= NON_SPEECH_THRESHOLD &&
            speechProbability < SPEECH_PRESENCE_THRESHOLD
    }
}
