package at.heinz.deviceai.intelligence

import at.heinz.deviceai.learning.span.ContinuumResidualSpanCore

/**
 * Research-only adapter for the real CSX3 `predict(evidence, question)` API.
 *
 * CSX3 proposes a literal token span, not a factual proof. This adapter is
 * deliberately fail-closed until confidence/abstention have been calibrated
 * on independent labeled evidence. No production chat switch or weight update.
 */
class ResidualSpanResearchAdapter(
    private val model: ContinuumResidualSpanCore,
    private val minimumResearchConfidence: Double = 0.9
) : ContinuumCognitiveSpine.SpanProposalPort {
    init {
        require(minimumResearchConfidence.isFinite() &&
            minimumResearchConfidence in 0.5..1.0)
    }

    override fun propose(
        question: String,
        source: ContinuumCognitiveSpine.Source
    ): ContinuumCognitiveSpine.SpanProposal? {
        // The existing CSX3 has hard independent byte limits; refuse rather
        // than truncate evidence or alter the question and misattribute a quote.
        if (question.isBlank() || question.toByteArray(Charsets.UTF_8).size > 256 ||
            source.text.isBlank() || source.text.toByteArray(Charsets.UTF_8).size > 1024) return null

        val predicted = runCatching { model.predict(source.text, question) }.getOrNull()
            ?: return null
        if (predicted.answer.isNullOrBlank() || !predicted.confidence.isFinite() ||
            predicted.confidence < minimumResearchConfidence ||
            predicted.start < 0 || predicted.end <= predicted.start ||
            predicted.end > source.text.length || predicted.end - predicted.start > 256
        ) return null
        if (source.text.substring(predicted.start, predicted.end) != predicted.answer) return null
        return ContinuumCognitiveSpine.SpanProposal(source.id, predicted.start, predicted.end)
    }
}
