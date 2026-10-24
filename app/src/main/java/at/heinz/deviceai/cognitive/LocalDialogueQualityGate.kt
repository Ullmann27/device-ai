package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Deterministic, tool-free checks for a local model's conversational text.
 *
 * No semantic truth is inferred from lexical similarity, model confidence, or
 * fluent wording. A pass means only that no tested structural defect was found.
 * Neither the candidate text nor this evaluation confirms an owner fact.
 */
object LocalDialogueQualityGate {
    private const val MAX_REFERENCE_CHARS = 2_000
    private val sentenceBoundary = Regex("(?<=[.!?])\\s+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    private val whitespace = Regex("\\s+")

    fun evaluate(
        request: String,
        response: String,
        previousRequest: String? = null,
        previousResponse: String? = null
    ): Evaluation {
        val candidate = response.trim()
        if (candidate.isEmpty()) {
            return Evaluation(false, 0.0, "Keine Antwort erzeugt. Beantworte die aktuelle Frage oder benenne die Wissenslücke.")
        }
        if (candidate.length > 20_000) {
            return Evaluation(false, 0.1, "Antwort übermäßig lang. Kürze sie und beantworte die Frage direkt.")
        }
        val current = normalized(candidate)
        val question = normalized(request)
        if (question.length >= 24 && current == question) {
            return Evaluation(false, 0.1, "Eingabe nur wiederholt. Beantworte sie stattdessen.")
        }

        // Explicit uncertainty is valid. Never pressure a model to invent facts.
        val uncertainty = candidate.startsWith("Ich weiß es nicht", ignoreCase = true) ||
            candidate.startsWith("Das weiß ich nicht", ignoreCase = true) ||
            candidate.startsWith("Dazu habe ich keine", ignoreCase = true) ||
            candidate.startsWith("Das kann ich nicht sicher", ignoreCase = true) ||
            candidate.startsWith("I don't know", ignoreCase = true)

        val distinctFollowUp = !previousRequest.isNullOrBlank() &&
            normalized(previousRequest) != question
        if (distinctFollowUp && !previousResponse.isNullOrBlank() && !uncertainty) {
            val reference = previousResponse.take(MAX_REFERENCE_CHARS)
            if (normalized(reference) == current) {
                return Evaluation(false, 0.1, "Vorherige Antwort wortgleich wiederholt. Beantworte die neue Frage.")
            }
            val oldSentences = fullSentences(reference)
            if (fullSentences(candidate).any { it in oldSentences }) {
                return Evaluation(false, 0.2,
                    "Ein vollständiger längerer Satz aus der vorherigen Antwort wurde wiederholt. " +
                        "Beantworte die neue Frage ohne Textübernahme und ohne unbelegte Ergänzungen.")
            }
        }

        return if (uncertainty) {
            Evaluation(true, 0.5, "Wissenslücke offen benannt; Wahrheit der übrigen Aussagen nicht unabhängig geprüft.")
        } else {
            Evaluation(true, 0.6,
                "Nur Form und Wiederholungen geprüft. Sachliche Richtigkeit, Kausalität und " +
                    "Beleglage sind dadurch ausdrücklich NICHT bestätigt.")
        }
    }

    private fun fullSentences(value: String): Set<String> =
        value.split(sentenceBoundary)
            .map(::normalized)
            .filter { it.length >= 65 && it.split(' ').size >= 10 }
            .toSet()

    private fun normalized(value: String): String =
        separators.replace(value.lowercase(Locale.ROOT), " ")
            .let { whitespace.replace(it, " ").trim() }
}
