package at.heinz.deviceai.research.m12

import at.heinz.deviceai.cognitive.LocalGroundedRecall
import at.heinz.deviceai.cognitive.TraceStage

import at.heinz.deviceai.intelligence.ewm.Ewm1ClaimCheck

/** FROZEN copy of LocalModelClaimAudit as of PR #244 (d10b8332): the M12 baseline. Do not edit.
 *
 * SHADOW audit of a finished local-model answer against the retrieval it was given in the same turn.
 *
 * It connects the existing deterministic claim check (EWM2 H4) to the generative Gemma path, which never ran it:
 * names/nouns, numbers, time words and colours of the answer must occur in the evidence, and the polarity of each
 * answer sentence must match the evidence sentence it overlaps most. (Ewm1ClaimCheck compares polarity across the
 * whole evidence, which was right for its one-fact answers but flags every answer once a multi-sentence retrieval
 * contains any "nicht"; the frozen check itself is not changed.)
 *
 * The result is ONE trace stage in the existing metadata-only turn trace, nothing else: the visible answer, the
 * prompt, model calls, tools and memories are unchanged, and no token of the answer or the evidence is stored.
 *
 * Limits, stated so nobody reads more into it: this is token and polarity matching, not entailment. A flag means
 * "a detail is not carried by the retrieval" (the model may have added a legitimate conclusion); a pass does not
 * prove the answer correct. It becomes a quality signal only together with owner feedback on the same trace.
 */
object ClaimAuditV1Frozen {
    /** Answers longer than the model's bounded reply are not audited (the stage then says so). */
    const val MAX_ANSWER_CHARS = 8_000
    private val POLARITY = setOf("Verneinung fehlt", "unbelegte Verneinung", "Ja trotz Verneinung")
    private val SENTENCE_END = Regex("(?<=[.!?])\\s+")
    private val CONTENT_WORD = Regex("\\p{L}{4,}")

    fun stage(answer: String, recall: LocalGroundedRecall): TraceStage {
        if (answer.isBlank() || answer.length > MAX_ANSWER_CHARS) return TraceStage.CLAIM_AUDIT_SKIPPED
        return runCatching {
            if (unsupportedDetails(answer, listOf(recall.answer, recall.provenance)) ||
                polarityMismatch(answer, sentences(recall.answer))) TraceStage.CLAIM_AUDIT_UNSUPPORTED
            else TraceStage.CLAIM_AUDIT_SUPPORTED
        }.getOrDefault(TraceStage.CLAIM_AUDIT_SKIPPED)
    }

    private fun unsupportedDetails(answer: String, evidence: List<String>): Boolean =
        Ewm1ClaimCheck.check(answer, evidence).unsupported.any { it !in POLARITY }

    private fun polarityMismatch(answer: String, evidence: List<String>): Boolean = sentences(answer).any { sentence ->
        val words = contentWords(sentence)
        val best = evidence.maxByOrNull { e -> contentWords(e).count { it in words } }
        best != null && contentWords(best).any { it in words } &&
            Ewm1ClaimCheck.check(sentence, listOf(best)).unsupported.any { it in POLARITY }
    }

    private fun sentences(text: String): List<String> = text.split(SENTENCE_END).map { it.trim() }.filter { it.isNotEmpty() }
    private fun contentWords(text: String): Set<String> = CONTENT_WORD.findAll(text.lowercase()).map { it.value }.toSet()
}
