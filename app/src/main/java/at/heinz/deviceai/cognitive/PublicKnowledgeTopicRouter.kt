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
    /**
     * #567: a question about what the owner told the assistant, or what the assistant did in this dialogue
     * („Welches Testwort habe ich dir gerade genannt?“, „den du gerade erfunden hast“), is personal dialogue.
     * It is never sent to a search engine; it stays on the local memory/model path.
     */
    private val dialogueReference = Regex(
        "(?iu)\\b(ich|wir)\\b.*\\b(dir|dich|euch)\\b|\\b(du|ihr)\\b.*\\b(dir|mir|uns|hast|hattest|habt)\\b|\\b(habe|hab|hatte)\\s+ich\\b"
    )

    fun topic(message: String): String? {
        val cleaned = message.trim().replace(Regex("\\s+"), " ")
        if (OwnerChatIntentRouter.route(cleaned) != null) return null
        if (cleaned.length !in 8..220 || !startsAsKnowledgeQuestion.containsMatchIn(cleaned) ||
            privateMarkers.containsMatchIn(cleaned) || dialogueReference.containsMatchIn(cleaned) ||
            cleaned.any { it.isISOControl() }) return null
        val result = ResearchQuestionFocus.searchText(cleaned)
            .removeSuffix("?").trim().take(80).trim()
        if (!topicPattern.matches(result)) return null
        val normalized = result.lowercase(Locale.ROOT)
        if (normalized in setOf("wie gehts", "was kannst du", "wer bin ich", "wie geht es dir")) return null
        return result
    }
}
