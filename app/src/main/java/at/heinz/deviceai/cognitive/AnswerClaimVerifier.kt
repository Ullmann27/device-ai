package at.heinz.deviceai.cognitive

import java.util.Locale

/** Deterministic checks for one already-resolved factual claim. No truth is inferred here. */
object AnswerClaimVerifier {
    enum class Verdict {
        PASS,
        CONTRADICTION,
        UNSUPPORTED_CAUSALITY,
        INSUFFICIENT_EVIDENCE
    }

    enum class CausalEvidence {
        NONE,
        TEMPORAL_ONLY,
        CORRELATION,
        MECHANISTIC,
        INTERVENTION_CONFIRMED
    }

    data class GroundedClaim(
        val subject: String,
        val predicate: String,
        val expectedValue: String,
        val forbiddenValues: Set<String> = emptySet(),
        val causalEvidence: CausalEvidence = CausalEvidence.NONE,
        val sourceIds: Set<String>
    ) {
        init {
            require(subject.isNotBlank() && subject.length <= 160)
            require(predicate.isNotBlank() && predicate.length <= 160)
            require(expectedValue.isNotBlank() && expectedValue.length <= 240)
            require(forbiddenValues.none(String::isBlank))
            require(sourceIds.isNotEmpty() && sourceIds.none(String::isBlank))
        }
    }

    data class Result(
        val verdict: Verdict,
        val reason: String,
        val citedSourceIds: Set<String>
    )

    fun verifyFocusedSentence(sentence: String, claim: GroundedClaim): Result {
        val candidate = normalize(sentence)
        if (candidate.isBlank()) {
            return Result(Verdict.INSUFFICIENT_EVIDENCE, "EMPTY_CANDIDATE", claim.sourceIds)
        }

        if (containsCausalAssertion(candidate) &&
            claim.causalEvidence != CausalEvidence.INTERVENTION_CONFIRMED) {
            return Result(
                Verdict.UNSUPPORTED_CAUSALITY,
                "CAUSAL_LANGUAGE_EXCEEDS_EVIDENCE_" + claim.causalEvidence.name,
                claim.sourceIds
            )
        }

        val expected = normalize(claim.expectedValue)
        val forbidden = claim.forbiddenValues.map(::normalize).filter(String::isNotBlank).toSet()

        val expectedNumbers = numericTerms(expected)
        val candidateNumbers = numericTerms(candidate)
        val forbiddenNumbers = forbidden.flatMap(::numericTerms).toSet()

        if (candidateNumbers.intersect(forbiddenNumbers).isNotEmpty()) {
            return Result(
                Verdict.CONTRADICTION,
                "FORBIDDEN_SUPERSEDED_NUMERIC_VALUE",
                claim.sourceIds
            )
        }

        if (expectedNumbers.isNotEmpty() && candidateNumbers.isNotEmpty()) {
            if (candidateNumbers.intersect(expectedNumbers).isEmpty()) {
                return Result(
                    Verdict.CONTRADICTION,
                    "NUMERIC_VALUE_MISMATCH",
                    claim.sourceIds
                )
            }
            if (hasNegatedExpectedNumber(candidate, expectedNumbers)) {
                return Result(
                    Verdict.CONTRADICTION,
                    "EXPECTED_NUMERIC_VALUE_EXPLICITLY_NEGATED",
                    claim.sourceIds
                )
            }
            if ((candidateNumbers - expectedNumbers).isNotEmpty()) {
                return Result(
                    Verdict.INSUFFICIENT_EVIDENCE,
                    "MULTIPLE_NUMERIC_VALUES_IN_FOCUSED_SENTENCE",
                    claim.sourceIds
                )
            }
        }

        if (forbidden.any { containsWhole(candidate, it) }) {
            return Result(
                Verdict.CONTRADICTION,
                "FORBIDDEN_SUPERSEDED_VALUE",
                claim.sourceIds
            )
        }

        if (containsNegatedExpected(candidate, expected)) {
            return Result(
                Verdict.CONTRADICTION,
                "EXPECTED_VALUE_EXPLICITLY_NEGATED",
                claim.sourceIds
            )
        }

        if (containsWhole(candidate, expected)) {
            return Result(Verdict.PASS, "EXACT_VALUE_PRESENT", claim.sourceIds)
        }

        // A numeric claim can still pass when the canonical value is written as a word/digit variant.
        if (expectedNumbers.isNotEmpty() && candidateNumbers.intersect(expectedNumbers).isNotEmpty()) {
            return Result(Verdict.PASS, "NUMERIC_VALUE_PRESENT", claim.sourceIds)
        }

        return Result(
            Verdict.INSUFFICIENT_EVIDENCE,
            "FOCUSED_CLAIM_NOT_EXPLICITLY_SUPPORTED",
            claim.sourceIds
        )
    }

    private fun containsCausalAssertion(value: String): Boolean =
        CAUSAL.any { it.containsMatchIn(value) }

    private fun hasNegatedExpectedNumber(candidate: String, expectedNumbers: Set<Long>): Boolean {
        val words = candidate.split(' ').filter(String::isNotBlank)
        words.forEachIndexed { index, word ->
            val number = word.toLongOrNull() ?: NUMBER_WORDS[word]
            if (number !in expectedNumbers) return@forEachIndexed
            val from = maxOf(0, index - 3)
            if (words.subList(from, index).any { it in NEGATIONS }) return true
        }
        return false
    }

    private fun containsNegatedExpected(candidate: String, expected: String): Boolean {
        val words = candidate.split(' ').filter(String::isNotBlank)
        val valueWords = expected.split(' ').filter(String::isNotBlank)
        if (valueWords.isEmpty() || words.size < valueWords.size) return false
        for (i in 0..words.size - valueWords.size) {
            if (words.subList(i, i + valueWords.size) != valueWords) continue
            val from = maxOf(0, i - 3)
            if (words.subList(from, i).any { it in NEGATIONS }) return true
        }
        return false
    }

    private fun containsWhole(candidate: String, value: String): Boolean {
        if (value.isBlank()) return false
        val escaped = Regex.escape(value).replace("\\ ", "\\s+")
        return Regex("(?<![\\p{L}\\p{N}])$escaped(?![\\p{L}\\p{N}])").containsMatchIn(candidate)
    }

    private fun numericTerms(value: String): Set<Long> =
        TOKENS.findAll(value).mapNotNull { token ->
            val raw = token.value.lowercase(Locale.GERMAN)
            raw.toLongOrNull() ?: NUMBER_WORDS[raw]
        }.toSet()

    private fun normalize(value: String): String =
        value.lowercase(Locale.GERMAN)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private val TOKENS = Regex("[\\p{L}\\p{N}]+")
    private val NEGATIONS = setOf("nicht", "kein", "keine", "keinen", "keinem", "keiner", "nie", "niemals")
    private val NUMBER_WORDS = mapOf(
        "null" to 0L, "ein" to 1L, "eins" to 1L, "eine" to 1L, "einen" to 1L,
        "zwei" to 2L, "drei" to 3L, "vier" to 4L, "fünf" to 5L, "fuenf" to 5L,
        "sechs" to 6L, "sieben" to 7L, "acht" to 8L, "neun" to 9L, "zehn" to 10L
    )
    private val CAUSAL = listOf(
        Regex("\\bverursacht\\b"),
        Regex("\\bbewirkt\\b"),
        Regex("\\bführt\\s+zu\\b"),
        Regex("\\bist\\s+die\\s+ursache\\b"),
        Regex("\\bdeshalb\\b")
    )
}
