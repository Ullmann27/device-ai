package at.heinz.deviceai.intelligence

import java.security.MessageDigest
import java.util.Locale

/**
 * Fail-closed, side-effect-free decision boundary for the optional local intelligence route.
 * A model prediction is NOT a proof of truth. Only a literal quotation is reported.
 * The caller remains responsible for the owner-review status of each source.
 */
class ContinuumCognitiveSpine(private val spanPort: SpanProposalPort) {
    enum class Task { QUESTION, MEMORY_PROPOSAL, CODING_PROPOSAL }
    enum class Status { QUOTED_SOURCE, ABSTAINED, OWNER_CONFIRMATION_REQUIRED }

    data class Source(
        val id: String,
        val text: String,
        val sha256: String,
        val markedReviewedByOwner: Boolean
    )

    data class SpanProposal(val sourceId: String, val start: Int, val end: Int)
    fun interface SpanProposalPort { fun propose(question: String, source: Source): SpanProposal? }

    data class Resolution(
        val status: Status,
        val answer: String,
        val quotedSourceId: String?,
        val quotedText: String?,
        val receiptDigest: String
    )

    fun resolve(query: String, task: Task, sources: List<Source> = emptyList()): Resolution {
        require(query.isNotBlank() && query.length <= 1200 && query.none { it == '\u0000' })
        // Never infer privileged intent from free text; the UI must select this task explicitly.
        if (task != Task.QUESTION) {
            val label = if (task == Task.MEMORY_PROPOSAL)
                "Speicheranfrage vorbereitet. Es wurde noch nichts gespeichert oder trainiert."
            else "Coding-Anfrage vorbereitet. Kein Worker gestartet; Besitzerfreigabe erforderlich."
            return result(Status.OWNER_CONFIRMATION_REQUIRED, label, null, null)
        }
        if (sources.size != 1) return abstain()
        val source = sources.single()
        if (!isAdmissible(source)) return abstain()
        val suggestion = runCatching { spanPort.propose(query, source) }.getOrNull() ?: return abstain()
        if (suggestion.sourceId != source.id || suggestion.start < 0 ||
            suggestion.end <= suggestion.start || suggestion.end > source.text.length ||
            suggestion.end - suggestion.start > 256 ||
            !isCodePointBoundary(source.text, suggestion.start) ||
            !isCodePointBoundary(source.text, suggestion.end)) return abstain()
        val literal = source.text.substring(suggestion.start, suggestion.end)
        if (literal.isBlank()) return abstain()
        // Not a semantic assertion: the model may have selected the wrong literal span.
        return result(Status.QUOTED_SOURCE,
            "In Quelle " + source.id + " steht wörtlich: „" + literal +
                "“. Die sachliche Zuordnung zur Frage ist nicht unabhängig verifiziert.",
            source.id, literal)
    }

    private fun isAdmissible(source: Source): Boolean =
        source.markedReviewedByOwner && source.id.matches(Regex("[A-Za-z0-9._-]{1,96}")) &&
            source.text.isNotBlank() && source.text.toByteArray(Charsets.UTF_8).size <= 16_384 &&
            source.sha256.matches(Regex("[a-f0-9]{64}")) &&
            digest(source.text) == source.sha256

    private fun isCodePointBoundary(text: String, offset: Int): Boolean =
        offset == 0 || offset == text.length ||
            !(Character.isHighSurrogate(text[offset - 1]) && Character.isLowSurrogate(text[offset]))

    private fun abstain(): Resolution = result(Status.ABSTAINED,
        "Für diese Frage liegt keine ausreichend überprüfte Textstelle vor. Ich enthalte mich einer Tatsachenbehauptung.",
        null, null)

    private fun result(status: Status, answer: String, id: String?, literal: String?): Resolution =
        Resolution(status, answer, id, literal,
            digest(listOf(status.name, answer, id.orEmpty(), literal.orEmpty()).joinToString("\u001f")))

    companion object {
        fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { b -> "%02x".format(Locale.ROOT, b.toInt() and 0xff) }
    }
}
