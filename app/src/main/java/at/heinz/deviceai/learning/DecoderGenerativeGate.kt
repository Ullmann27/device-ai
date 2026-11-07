package at.heinz.deviceai.learning

import java.text.Normalizer
import java.util.Locale

/** Free autoregressive VALIDATION, never a training loss or a claim of TEST generalisation.
 * The independent 25-question intelligence TEST is deliberately not used for selection.
 * An audit answer of "unbekannt" explicitly labels a missing-evidence control.
 */
object DecoderGenerativeGate {
    // Same budget as the existing Android intelligence audit and paired lab.
    const val MAX_BYTES = 48
    data class Quality(val correct: List<Boolean>, val supported: Int,
        val missing: Int, val correctSupported: Int, val correctAbstentions: Int,
        val unsupported: Int, val unterminated: Int) {
        val qualified: Boolean get() = supported >= 20 && missing >= 5 &&
            correctSupported * 5 >= supported * 4 && correctAbstentions == missing &&
            unsupported == 0 && unterminated == 0
    }

    private fun normalize(value: String): String = Normalizer.normalize(
        value.trim().trimEnd('.', '!', '?').trim(), Normalizer.Form.NFC)
        .lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private val abstentions = setOf("unbekannt", "nicht bekannt", "weiß ich nicht",
        "keine angabe", "keine information", "nicht angegeben")

    fun hasControls(corpus: DecoderEvidenceCorpus): Boolean =
        corpus.split == DecoderSplit.AUDIT &&
            corpus.pairs.count { normalize(it.answer) in abstentions } >= 5 &&
            corpus.pairs.count { normalize(it.answer) !in abstentions } >= 20

    fun measure(corpus: DecoderEvidenceCorpus, generate: (DecoderEvidencePair) -> DecoderGeneration): Quality =
        checkNotNull(measureCancellable(corpus, { false }, generate))

    fun measureCancellable(corpus: DecoderEvidenceCorpus, cancelled: () -> Boolean,
        generate: (DecoderEvidencePair) -> DecoderGeneration): Quality? {
        require(corpus.split == DecoderSplit.AUDIT)
        var supported = 0; var missing = 0; var correctSupported = 0
        var correctAbstentions = 0; var unsupported = 0; var unterminated = 0
        val correct = corpus.pairs.map { pair ->
            if (cancelled() || Thread.currentThread().isInterrupted) return null
            val output = generate(pair)
            if (cancelled() || Thread.currentThread().isInterrupted) return null
            val answer = normalize(output.text)
            val isMissing = normalize(pair.answer) in abstentions
            if (!output.ended) unterminated++
            val valid = output.ended && if (isMissing) answer in abstentions
                else answer == normalize(pair.answer)
            if (isMissing) {
                missing++
                if (valid) correctAbstentions++
                if (answer.isNotEmpty() && answer !in abstentions) unsupported++
            } else {
                supported++
                if (valid) correctSupported++
            }
            valid
        }
        return Quality(correct, supported, missing, correctSupported,
            correctAbstentions, unsupported, unterminated)
    }

    /** Fixed absolute floor, a paired gain, and no previously correct case lost.
     * Lower NLL remains a separate necessary condition in the caller.
     */
    fun improves(before: Quality, after: Quality): Boolean =
        after.qualified && before.supported == after.supported && before.missing == after.missing &&
            before.correct.size == after.correct.size &&
            before.correct.indices.all { !before.correct[it] || after.correct[it] } &&
            after.correct.count { it } > before.correct.count { it }
}
