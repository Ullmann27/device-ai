package at.heinz.deviceai.cognitive

import java.math.BigDecimal
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

    private data class NumericOccurrence(
        val value: BigDecimal,
        val start: Int
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

        val expectedNumbers = numericTermsRaw(claim.expectedValue)
        val candidateNumbers = numericTermsRaw(sentence)
        val forbiddenNumbers = claim.forbiddenValues.flatMap(::numericTermsRaw).toSet()

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
            if (hasNegatedExpectedNumber(sentence, expectedNumbers)) {
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

    private fun hasNegatedExpectedNumber(
        candidate: String,
        expectedNumbers: Set<BigDecimal>
    ): Boolean = numericOccurrencesRaw(candidate).any { occurrence ->
        if (occurrence.value !in expectedNumbers) return@any false
        normalize(candidate.substring(0, occurrence.start))
            .split(' ')
            .filter(String::isNotBlank)
            .takeLast(3)
            .any { it in NEGATIONS }
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

    private fun numericTermsRaw(value: String): Set<BigDecimal> =
        numericOccurrencesRaw(value).mapTo(linkedSetOf()) { it.value }

    private fun numericOccurrencesRaw(value: String): List<NumericOccurrence> {
        val lower = value.lowercase(Locale.GERMAN)
        val literals = NUMBER.findAll(lower).mapNotNull { match ->
            parseNumber(match.value)?.let { NumericOccurrence(it, match.range.first) }
        }
        val words = TOKENS.findAll(lower).mapNotNull { match ->
            NUMBER_WORDS[match.value]?.let {
                NumericOccurrence(BigDecimal.valueOf(it), match.range.first)
            }
        }
        return (literals + words).sortedBy(NumericOccurrence::start).toList()
    }

    private fun parseNumber(raw: String): BigDecimal? {
        val lastComma = raw.lastIndexOf(',')
        val lastDot = raw.lastIndexOf('.')
        val canonical = when {
            lastComma >= 0 && lastDot >= 0 -> {
                val decimalAt = maxOf(lastComma, lastDot)
                val integral = raw.substring(0, decimalAt)
                    .replace(",", "")
                    .replace(".", "")
                integral + "." + raw.substring(decimalAt + 1)
            }
            lastComma >= 0 -> raw.replace(',', '.')
            else -> raw
        }
        return canonical.toBigDecimalOrNull()?.stripTrailingZeros()
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.GERMAN)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private val NUMBER = Regex(
        "(?<![\\p{L}\\p{N}])[+-]?(?:" +
            "\\d{1,3}(?:\\.\\d{3})+,\\d+|" +
            "\\d{1,3}(?:,\\d{3})+\\.\\d+|" +
            "\\d+(?:[.,]\\d+)?" +
            ")(?![\\p{L}\\p{N}])"
    )
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
