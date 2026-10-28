package at.heinz.deviceai.learning

import java.util.Locale

/**
 * A lexical archive match is not evidence that the excerpt answers the user.
 * In particular, unresolved follow-ups must use the active conversation rather
 * than an unrelated public source that happened to contain common words.
 * This gate never changes stored sources, owner facts, or model weights.
 */
internal object ContinuumArchiveAnswerPolicy {
    private val separator = Regex("[^\\p{L}\\p{N}]+")
    private val unresolvedReference = Regex(
        "(?iu)\\b(?:dort|dazu|darauf|darüber|daraus|davon|dadurch|damit|dessen|deren|hierzu|diesbezüglich)\\b"
    )
    private val continuation = Regex("(?iu)^\\s*(?:und|außerdem|weiter|nochmal|nochmals)\\b")
    private val demonstrativeFollowUp = Regex(
        "(?iu)\\b(?:das|dies|diese|dieser|es)\\s*(?:[?!.,;:]|$|\\b(?:für|bedeutet|heißt|ist|war|hat|macht|kann|wäre|soll|kostet)\\b)"
    )
    // A matching article title is not an answer to a capability question, a
    // how-to request or a reasoning task. Keep these turns on the normal
    // conversation/model route; public excerpts remain unverified evidence.
    // This protects both model-ready and model-unavailable paths from a
    // misleading citation-only response.
    private val generativeRequest = Regex(
        "(?iu)\\b(?:wie|warum|wieso|weshalb|wozu|wann|kannst|könntest|würdest|" +
            "hilfst|helfen|unterstützen|plane|planen|vergleiche|analysiere|bewerte)\\b"
    )
    private val nonTopics = setOf(
        "aber", "alle", "allen", "alles", "auch", "beim", "bitte", "dann", "dass",
        "deine", "deinen", "deiner", "dies", "diese", "diesem", "diesen", "dieser",
        "einem", "einen", "einer", "etwas", "frage", "fragen", "haben", "hatte",
        "hier", "ihnen", "ihrem", "ihren", "jetzt", "kann", "kannst", "mehr",
        "meine", "meinem", "meinen", "meiner", "möchte", "möchten", "nicht",
        "noch", "oder", "sagen", "sowie", "über", "unter", "welche", "welchen",
        "welcher", "welches", "wenn", "werden", "wieso", "warum", "würde",
        "würden", "wissen", "wurde", "womit", "wofür", "woher"
    )

    /**
     * Only a non-anaphoric question naming a topic in the actual source title
     * may replace the ordinary model response with an archival quotation.
     * An excerpt is still a labelled, unverified observation, never a fact.
     */
    fun select(
        question: String,
        candidates: List<ContinuumKnowledgeArchive.Hit>
    ): ContinuumKnowledgeArchive.Hit? {
        if (question.length !in 6..240 ||
            unresolvedReference.containsMatchIn(question) ||
            continuation.containsMatchIn(question) ||
            demonstrativeFollowUp.containsMatchIn(question) ||
            generativeRequest.containsMatchIn(question)
        ) return null

        val topic = topicTerms(question)
        if (topic.isEmpty()) return null
        return candidates.firstOrNull { candidate ->
            candidate.score >= 5 && topic.any { it in topicTerms(candidate.source.title) }
        }
    }

    private fun topicTerms(value: String): Set<String> =
        value.lowercase(Locale.ROOT).split(separator)
            .filter { it.length >= 4 && it !in nonTopics }
            .toSet()
}
