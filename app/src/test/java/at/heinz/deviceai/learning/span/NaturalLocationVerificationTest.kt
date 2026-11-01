package at.heinz.deviceai.learning.span

import org.junit.Assert.*
import org.junit.Test

class NaturalLocationVerificationTest {
    private fun source(text: String, id: String = "a") = EvidenceBoundNaturalLocationProbe.Evidence(
        id, 1, EvidenceBoundNaturalLocationProbe.digest(text), text
    )
    private fun forged(e: EvidenceBoundNaturalLocationProbe.Evidence, answer: String) =
        EvidenceBoundNaturalLocationProbe.Decision.Found(
            answer, e.sourceId, e.revision, e.sha256, e.text.indexOf(answer), e.text.indexOf(answer) + answer.length
        )

    @Test fun verifierRechecksNegativeAndConflictingEvidenceWithoutTrustingProducer() {
        for (text in listOf("Mama ist in Graz. Mama ist nicht in Graz.",
            "Mama ist in Graz. Mama ist in Wien.", "Vielleicht ist Mama in Graz.")) {
            val e = source(text)
            assertFalse(text, EvidenceBoundNaturalLocationVerifier.verify("Wo ist Mama?", listOf(e), forged(e, "Graz")))
        }
        val e = source("Mama ist in Graz.")
        assertFalse(EvidenceBoundNaturalLocationVerifier.verify("Wo ist Mama?",
            listOf(e, source("Mama ist in Wien.", "b")), forged(e, "Graz")))
    }

    @Test fun verifierRejectsIncompleteLocationAndInvalidOtherSources() {
        val e = source("Mama ist in Bad Ischl.")
        assertFalse(EvidenceBoundNaturalLocationVerifier.verify("Wo ist Mama?", listOf(e), forged(e, "Bad")))
        val bad = source("Papa ist in Linz.", "b").copy(sha256 = "0".repeat(64))
        assertFalse(EvidenceBoundNaturalLocationVerifier.verify("Wo ist Mama?", listOf(e, bad), forged(e, "Bad Ischl")))
    }

    @Test fun embeddedSpeechAndHypothesesAreNotDirectLocationAssertions() {
        for (text in listOf("Ben behauptet Mama ist in Graz.", "Wenn Mama ist in Graz.",
            "Angenommen Mama ist in Graz.", "Anna-Mama ist in Graz.")) {
            assertTrue(text, EvidenceBoundNaturalLocationProbe.answer("Wo ist Mama?", listOf(source(text)))
                is EvidenceBoundNaturalLocationProbe.Decision.Abstained)
        }
    }

    @Test fun residenceQuestionCannotUseTemporaryPresenceOrStandingRelation() {
        assertTrue(EvidenceBoundNaturalLocationProbe.answer("Wo wohnt Mama?", listOf(source("Mama ist in Graz.")))
            is EvidenceBoundNaturalLocationProbe.Decision.Abstained)
        val e = source("Mama steht in Graz.")
        assertFalse(EvidenceBoundNaturalLocationVerifier.verify("Wo wohnt Mama?", listOf(e), forged(e, "Graz")))
    }
}
