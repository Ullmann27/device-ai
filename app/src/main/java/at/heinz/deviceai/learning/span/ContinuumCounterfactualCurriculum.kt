package at.heinz.deviceai.learning.span

/**
 * Offline-only synthetic curriculum builder.
 * It never infers semantic truth from a scraped document: the negative evidence
 * must have been reviewed by the dataset author independently.
 */
object ContinuumCounterfactualCurriculum {
    data class ReviewedFact(
        val id: String,
        val positiveEvidence: String,
        val question: String,
        val answer: String,
        val replacement: String,
        val reviewedNegativeEvidence: String
    )

    data class Triplet(
        val original: ContinuumResidualSpanCore.Example,
        val swapped: ContinuumResidualSpanCore.Example,
        val reviewedNegative: ContinuumResidualSpanCore.Example
    ) {
        fun asTrainingExamples(): List<ContinuumResidualSpanCore.Example> =
            listOf(original, swapped, reviewedNegative)
    }

    /**
     * Structural gate, not a semantic proof that the negative is really unanswerable.
     * The caller is responsible for an independent human/author review.
     */
    fun fromReviewedFact(
        fact: ReviewedFact,
        candidateModel: ContinuumResidualSpanCore
    ): Triplet {
        require(fact.id.length in 1..64 && fact.id.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        require(fact.answer.isNotBlank() && fact.replacement.isNotBlank())
        require(fact.answer != fact.replacement && !fact.answer.equals(fact.replacement, ignoreCase = true))
        require(!fact.answer.contains(fact.replacement, ignoreCase = true))
        require(!fact.replacement.contains(fact.answer, ignoreCase = true))
        require(fact.positiveEvidence.isNotBlank() && fact.question.isNotBlank())
        require(fact.reviewedNegativeEvidence.isNotBlank())
        require(countOccurrences(fact.positiveEvidence, fact.answer) == 1)
        require(!fact.positiveEvidence.contains(fact.replacement, ignoreCase = true))
        val swapped = fact.positiveEvidence.replaceFirst(fact.answer, fact.replacement)
        require(!swapped.contains(fact.answer, ignoreCase = true))
        val neg = fact.reviewedNegativeEvidence
        require(!neg.contains(fact.answer, ignoreCase = true) &&
            !neg.contains(fact.replacement, ignoreCase = true))
        val trio = Triplet(
            ContinuumResidualSpanCore.Example(fact.positiveEvidence, fact.question, fact.answer),
            ContinuumResidualSpanCore.Example(swapped, fact.question, fact.replacement),
            ContinuumResidualSpanCore.Example(neg, fact.question, null)
        )
        candidateModel.loss(trio.original)
        candidateModel.loss(trio.swapped)
        candidateModel.loss(trio.reviewedNegative)
        return trio
    }

    private fun countOccurrences(haystack: String, needle: String): Int {
        var count = 0
        var start = 0
        while (true) {
            val i = haystack.indexOf(needle, start, ignoreCase = true)
            if (i < 0) return count
            count++
            start = i + needle.length
        }
    }
}
