package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerClaimVerifierTest {
    private fun claim(
        expected: String,
        forbidden: Set<String> = emptySet(),
        causal: AnswerClaimVerifier.CausalEvidence = AnswerClaimVerifier.CausalEvidence.NONE
    ) = AnswerClaimVerifier.GroundedClaim(
        subject = "owner",
        predicate = "child_count",
        expectedValue = expected,
        forbiddenValues = forbidden,
        causalEvidence = causal,
        sourceIds = setOf("memory-1")
    )

    @Test fun exactNumericFactPassesAndDifferentNumberContradicts() {
        assertEquals(
            AnswerClaimVerifier.Verdict.PASS,
            AnswerClaimVerifier.verifyFocusedSentence("Du hast zwei Kinder.", claim("2")).verdict
        )
        assertEquals(
            AnswerClaimVerifier.Verdict.CONTRADICTION,
            AnswerClaimVerifier.verifyFocusedSentence("Du hast drei Kinder.", claim("2")).verdict
        )
    }

    @Test fun numericWordNegationAndMixedNumbersFailClosed() {
        assertEquals(
            AnswerClaimVerifier.Verdict.CONTRADICTION,
            AnswerClaimVerifier.verifyFocusedSentence("Du hast nicht zwei Kinder.", claim("2")).verdict
        )
        assertEquals(
            AnswerClaimVerifier.Verdict.INSUFFICIENT_EVIDENCE,
            AnswerClaimVerifier.verifyFocusedSentence("Du hast zwei Kinder und bist 33 Jahre alt.", claim("2")).verdict
        )
        assertEquals(
            AnswerClaimVerifier.Verdict.CONTRADICTION,
            AnswerClaimVerifier.verifyFocusedSentence(
                "Du hast zwei, nicht drei Kinder.", claim("2", forbidden=setOf("3"))
            ).verdict
        )
    }

    @Test fun decimalValuesRemainAtomicAcrossSeparators() {
        assertEquals(
            AnswerClaimVerifier.Verdict.CONTRADICTION,
            AnswerClaimVerifier.verifyFocusedSentence("Es sind 3 Euro.", claim("3,5 Euro")).verdict
        )
        assertEquals(
            AnswerClaimVerifier.Verdict.CONTRADICTION,
            AnswerClaimVerifier.verifyFocusedSentence("Es sind 5,3 Euro.", claim("3,5 Euro")).verdict
        )
        assertEquals(
            AnswerClaimVerifier.Verdict.PASS,
            AnswerClaimVerifier.verifyFocusedSentence("Es sind 3.50 Euro.", claim("3,5 Euro")).verdict
        )
    }

    @Test fun correctDecimalDoesNotTextuallyMatchForbiddenInteger() {
        val current = claim("3,5 Euro", forbidden = setOf("5 Euro"))
        assertEquals(
            AnswerClaimVerifier.Verdict.PASS,
            AnswerClaimVerifier.verifyFocusedSentence("Es sind 3,5 Euro.", current).verdict
        )
        assertEquals(
            AnswerClaimVerifier.Verdict.CONTRADICTION,
            AnswerClaimVerifier.verifyFocusedSentence("Es sind 5 Euro.", current).verdict
        )
    }

    @Test fun groupedDecimalFormatsCompareAsOneValue() {
        assertEquals(
            AnswerClaimVerifier.Verdict.PASS,
            AnswerClaimVerifier.verifyFocusedSentence(
                "Es sind 1,234.50 Euro.",
                claim("1234,5 Euro")
            ).verdict
        )
        assertEquals(
            AnswerClaimVerifier.Verdict.PASS,
            AnswerClaimVerifier.verifyFocusedSentence(
                "Es sind 1.234,50 Euro.",
                claim("1234.5 Euro")
            ).verdict
        )
    }

    @Test fun negatedAndSignedDecimalsStayFailClosed() {
        assertEquals(
            AnswerClaimVerifier.Verdict.CONTRADICTION,
            AnswerClaimVerifier.verifyFocusedSentence("Es sind nicht 3,5 Euro.", claim("3.5 Euro")).verdict
        )
        assertEquals(
            AnswerClaimVerifier.Verdict.CONTRADICTION,
            AnswerClaimVerifier.verifyFocusedSentence("Die Temperatur ist 5 Grad.", claim("-5 Grad")).verdict
        )
        assertEquals(
            AnswerClaimVerifier.Verdict.INSUFFICIENT_EVIDENCE,
            AnswerClaimVerifier.verifyFocusedSentence(
                "Es sind 3,5 Euro plus 4 Euro.",
                claim("3,5 Euro")
            ).verdict
        )
    }

    @Test fun supersededAndNegatedValuesAreRejected() {
        assertEquals(
            AnswerClaimVerifier.Verdict.CONTRADICTION,
            AnswerClaimVerifier.verifyFocusedSentence(
                "Dein Wohnort ist Wien.",
                AnswerClaimVerifier.GroundedClaim(
                    "owner","residence","Graz",setOf("Wien"),
                    sourceIds=setOf("memory-2")
                )
            ).verdict
        )
        assertEquals(
            AnswerClaimVerifier.Verdict.CONTRADICTION,
            AnswerClaimVerifier.verifyFocusedSentence(
                "Du wohnst nicht in Graz.",
                AnswerClaimVerifier.GroundedClaim(
                    "owner","residence","Graz",sourceIds=setOf("memory-2")
                )
            ).verdict
        )
    }

    @Test fun causalLanguageRequiresInterventionConfirmedEvidence() {
        assertEquals(
            AnswerClaimVerifier.Verdict.UNSUPPORTED_CAUSALITY,
            AnswerClaimVerifier.verifyFocusedSentence(
                "Der Shader verursacht den Absturz.",
                AnswerClaimVerifier.GroundedClaim(
                    "app","crash_relation","shader",
                    causalEvidence=AnswerClaimVerifier.CausalEvidence.CORRELATION,
                    sourceIds=setOf("probe-1")
                )
            ).verdict
        )
        assertEquals(
            AnswerClaimVerifier.Verdict.PASS,
            AnswerClaimVerifier.verifyFocusedSentence(
                "Der Shader verursacht den Absturz.",
                AnswerClaimVerifier.GroundedClaim(
                    "app","crash_relation","shader",
                    causalEvidence=AnswerClaimVerifier.CausalEvidence.INTERVENTION_CONFIRMED,
                    sourceIds=setOf("probe-1","ablation-1")
                )
            ).verdict
        )
    }

    @Test fun absenceOfFocusedValueDoesNotPretendToVerifyTruth() {
        assertEquals(
            AnswerClaimVerifier.Verdict.INSUFFICIENT_EVIDENCE,
            AnswerClaimVerifier.verifyFocusedSentence(
                "Dazu habe ich mehrere Informationen.",
                claim("2")
            ).verdict
        )
    }
}
