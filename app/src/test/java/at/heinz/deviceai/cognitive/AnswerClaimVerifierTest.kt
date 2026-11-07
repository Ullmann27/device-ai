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
