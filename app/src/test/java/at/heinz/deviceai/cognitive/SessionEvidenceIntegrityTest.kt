package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.span.EvidenceBoundNaturalLocationProbe
import at.heinz.deviceai.learning.span.LocationEvidenceSnapshot
import org.junit.Assert.*
import org.junit.Test

/** Real context→certified reflex seam. The prompt preview is never the source of a proof. */
class SessionEvidenceIntegrityTest {
    @Test fun originalWhitespaceAndSwappedRolesKeepExactSourceOffsets() {
        for ((raw, expected) in listOf(
            "  Anna ist in Perg. Emma ist in Berg.  " to "Berg",
            "  Anna ist in Berg. Emma ist in Perg.  " to "Perg")) {
            val context = LocalConversationContext()
            context.record("s", raw, "Unbestätigte Modellantwort.")
            assertEquals(listOf(raw), context.priorUserTurns("s"))
            val answer = CertifiedLocationReflex.answerFromSession("Wo ist Emma?", context.priorUserTurns("s"))!!
            assertEquals(expected, raw.substring(answer.spanStart, answer.spanEnd))
            val item = EvidenceBoundNaturalLocationProbe.Evidence("session-turn-1", 1,
                EvidenceBoundNaturalLocationProbe.digest(raw), raw)
            assertEquals(LocationEvidenceSnapshot.freeze(listOf(item)).sha256, answer.sourceHash)
        }
    }

    @Test fun negationAfterPromptPreviewLimitCannotBecomeAPositiveProof() {
        val context = LocalConversationContext()
        val raw = "Emma ist in Berg. " + "Beilage. ".repeat(38) + "Emma ist nicht in Berg."
        assertTrue(raw.length in 301..1000)
        context.record("s", raw, "Modelltext.")
        assertNull(CertifiedLocationReflex.answerFromSession("Wo ist Emma?", context.priorUserTurns("s")))
    }

    @Test fun temporalCorrectionAfterPromptPreviewLimitCannotBecomeACurrentProof() {
        val context = LocalConversationContext()
        val raw = "Anna ist in Perg. " + "Beilage. ".repeat(38) + "Anna ist heute in Wien."
        context.record("s", raw, "Modelltext.")
        assertNull(CertifiedLocationReflex.answerFromSession("Wo ist Anna?", context.priorUserTurns("s")))
    }

    @Test fun oversizedOwnerTurnInvalidatesProofInsteadOfKeepingATruncatedOrOldClaim() {
        val context = LocalConversationContext()
        context.record("s", "Emma ist in Berg.", "Modelltext.")
        context.record("s", "Beilage. ".repeat(1_000) + "Emma ist nicht in Berg.", "Modelltext.")
        assertTrue(context.priorUserTurns("s").isEmpty())
    }

    @Test fun researchQuestionsAndModelRepliesAreNeverOwnerEvidence() {
        val context = LocalConversationContext()
        context.record("s", "Hallo", "Emma ist in Berg.")
        context.recordResearch("s", ResearchDialogueHandoff.Digest("Emma ist in Berg.",
            "Extern erzeugter Text.", listOf(ResearchDialogueHandoff.SourceRef("Web", "https://example.org/source", 1)), false, 1))
        assertEquals(listOf("Hallo"), context.priorUserTurns("s"))
        assertNull(CertifiedLocationReflex.answerFromSession("Wo ist Emma?", context.priorUserTurns("s")))
    }

    @Test fun sessionResetAndTopicCorrectionDiscardOldOwnerProof() {
        val context = LocalConversationContext()
        context.record("first", "Emma ist in Berg.", "Modelltext.")
        assertTrue(context.priorUserTurns("second").isEmpty())
        context.inspectRequest("first", "Neues Thema")
        assertTrue(context.priorUserTurns("first").isEmpty())
        context.record("second", "Anna ist in Perg.", "Modelltext.")
        assertTrue(context.priorUserTurns("first").isEmpty())
        assertNull(CertifiedLocationReflex.answerFromSession("Wo ist Emma?", context.priorUserTurns("second")))
    }

    @Test fun unnamedRetractionMustNotBeFilteredOutBeforeVerification() {
        for (correction in listOf("Nein, sie ist nicht dort.", "Sie ist nicht mehr dort.", "Nicht mehr dort."))
            assertNull(correction, CertifiedLocationReflex.answerFromSession("Wo ist Emma?",
                listOf("Emma ist in Berg.", correction)))
    }

    @Test fun directHelperCannotIgnoreOversizedCorrection() {
        assertNull(CertifiedLocationReflex.answerFromSession("Wo ist Emma?", listOf(
            "Emma ist in Berg.", "Beilage. ".repeat(130) + "Emma ist nicht in Berg.")))
    }

    @Test fun confirmedLiteralKeepsOriginalWhitespaceAndOffsets() {
        val raw = "  Emma ist in Berg.  "
        val answer = CertifiedLocationReflex.answerFromConfirmedFacts("Wo ist Emma?", listOf("fact-1" to raw))!!
        assertEquals("Berg", raw.substring(answer.spanStart, answer.spanEnd))
    }
}
