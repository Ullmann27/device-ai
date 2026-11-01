package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Conservative public-topic extraction for owner-enabled web research.
 * Do not send raw chat transcripts, account details or private memory to a
 * search engine. Uncertain/personal questions stay on the existing local path.
 */
internal object PublicKnowledgeTopicRouter {
    private val startsAsKnowledgeQuestion = Regex(
        "^(was|wie|welche|welcher|welches|wer|wann|wo|what|how|which|who|when|where)\\b",
        RegexOption.IGNORE_CASE
    )
    private val privateMarkers = Regex(
        "(?i)(@|https?://|[/\\\\]|\\b(passwort|password|pin|token|geheim|privat|mein|meine|meinen|meiner|meinem|konto|telefon|adresse|wohnort|sozialversicherung|iban|krankheit|arzt|meine\\s+tochter)\\b)"
    )
    private val topicPattern = Regex("[\\p{L}][\\p{L}\\p{N} .,'()\\-]{2,79}")

    fun topic(message: String): String? {
        val cleaned = message.trim().replace(Regex("\\s+"), " ")
        if (cleaned.length !in 8..220 || !startsAsKnowledgeQuestion.containsMatchIn(cleaned) ||
            privateMarkers.containsMatchIn(cleaned) ||
            cleaned.any { it.isISOControl() }) return null
        val result = ResearchQuestionFocus.searchText(cleaned)
            .removeSuffix("?").trim().take(80).trim()
        if (!topicPattern.matches(result)) return null
        val normalized = result.lowercase(Locale.ROOT)
        if (normalized in setOf("wie gehts", "was kannst du", "wer bin ich", "wie geht es dir")) return null
        return result
    }
}
