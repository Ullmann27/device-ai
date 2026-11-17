package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalModelClaimAuditTest {
    private val recall = LocalGroundedRecall(
        "Anna wohnt seit 2024 in Graz. Der Termin mit Dr. Berger ist am 12. März. Das Fahrrad ist nicht blau.",
        "Bestätigte Besitzererinnerung")

    private fun stage(answer: String) = LocalModelClaimAudit.stage(answer, recall)

    @Test fun anAnswerThatOnlyRestatesTheRetrievalIsSupported() {
        assertEquals(TraceStage.CLAIM_AUDIT_SUPPORTED, stage("Anna wohnt seit 2024 in Graz."))
        assertEquals(TraceStage.CLAIM_AUDIT_SUPPORTED, stage("Der Termin mit Dr. Berger ist am 12. März."))
    }

    @Test fun anInventedNumberNameOrDateIsFlagged() {
        assertEquals(TraceStage.CLAIM_AUDIT_UNSUPPORTED, stage("Anna wohnt seit 2023 in Graz."))
        assertEquals(TraceStage.CLAIM_AUDIT_UNSUPPORTED, stage("Anna wohnt seit 2024 in Linz."))
        assertEquals(TraceStage.CLAIM_AUDIT_UNSUPPORTED, stage("Der Termin mit Dr. Huber ist am 12. März."))
        assertEquals(TraceStage.CLAIM_AUDIT_UNSUPPORTED, stage("Der Termin mit Dr. Berger ist am 14. März."))
    }

    @Test fun aDroppedOrAddedNegationIsFlagged() {
        // the retrieval says "nicht blau": an answer without the negation must not pass as supported
        assertEquals(TraceStage.CLAIM_AUDIT_UNSUPPORTED,
            LocalModelClaimAudit.stage("Das Fahrrad ist blau.", LocalGroundedRecall("Das Fahrrad ist nicht blau.", "Erinnerung")))
        assertEquals(TraceStage.CLAIM_AUDIT_UNSUPPORTED,
            LocalModelClaimAudit.stage("Anna wohnt nicht in Graz.", LocalGroundedRecall("Anna wohnt in Graz.", "Erinnerung")))
    }

    @Test fun anEarlierDecisionIsNotSilentlyReplaced() {
        val decision = LocalGroundedRecall("Wir haben am Montag entschieden, Gemma 4 E4B zu behalten.", "Besitzerentscheidung")
        assertEquals(TraceStage.CLAIM_AUDIT_SUPPORTED,
            LocalModelClaimAudit.stage("Am Montag wurde entschieden, Gemma 4 E4B zu behalten.", decision))
        assertEquals(TraceStage.CLAIM_AUDIT_UNSUPPORTED,
            LocalModelClaimAudit.stage("Am Montag wurde entschieden, auf Qwen zu wechseln.", decision))
    }

    @Test fun emptyOrOversizedAnswersAreSkippedNotPassed() {
        assertEquals(TraceStage.CLAIM_AUDIT_SKIPPED, stage(" "))
        assertEquals(TraceStage.CLAIM_AUDIT_SKIPPED, stage("a".repeat(LocalModelClaimAudit.MAX_ANSWER_CHARS + 1)))
    }

    @Test fun theStageTravelsThroughTheExistingMetadataOnlyTraceCodec() {
        val trace = CognitiveTurnTrace("session-1")
        trace.event(TraceStage.LOCAL_MODEL_SYNTHESIS)
        trace.event(stage("Anna wohnt seit 2023 in Graz."))
        val snapshot = trace.finish(TraceOutcome.MODEL_INTERPRETATION)
        val decoded = CognitiveTraceCodec.decode(CognitiveTraceCodec.encode(snapshot))
        assertEquals(listOf(TraceStage.LOCAL_MODEL_SYNTHESIS, TraceStage.CLAIM_AUDIT_UNSUPPORTED), decoded.events.map { it.stage })
        // metadata only: neither the answer nor the evidence text reaches the encoded trace
        val encoded = CognitiveTraceCodec.encode(snapshot)
        listOf("Anna", "Graz", "2023").forEach { assertEquals(false, encoded.contains(it)) }
        assertEquals(0, decoded.modelInvocations)
    }
    @Test fun aNameBoundToTheWrongPersonIsFlagged() {
        val r = LocalGroundedRecall("Anna wohnt in Graz und Markus arbeitet in Linz.", "Erinnerung")
        assertEquals(TraceStage.CLAIM_AUDIT_UNSUPPORTED, LocalModelClaimAudit.stage("Markus arbeitet in Graz.", r))
        assertEquals(TraceStage.CLAIM_AUDIT_SUPPORTED, LocalModelClaimAudit.stage("Markus arbeitet in Linz.", r))
    }

    @Test fun restatingTheSupersededPartIsFlaggedTheCurrentPartIsNot() {
        val r = LocalGroundedRecall("Bis 2023 wohnte Anna in Linz, seit 2024 wohnt sie in Graz.", "Erinnerung")
        assertEquals(TraceStage.CLAIM_AUDIT_UNSUPPORTED, LocalModelClaimAudit.stage("Anna wohnt in Linz.", r))
        assertEquals(TraceStage.CLAIM_AUDIT_SUPPORTED, LocalModelClaimAudit.stage("Anna wohnt in Graz.", r))
        assertEquals(TraceStage.CLAIM_AUDIT_SUPPORTED, LocalModelClaimAudit.stage("Bis 2023 wohnte Anna in Linz.", r))
    }

    @Test fun anActionTheEvidenceNeverMentionsIsFlagged() {
        val r = LocalGroundedRecall("Anna hat am Montag mit Markus telefoniert.", "Erinnerung")
        assertEquals(TraceStage.CLAIM_AUDIT_UNSUPPORTED, LocalModelClaimAudit.stage("Anna hat Markus verlassen.", r))
        assertEquals(TraceStage.CLAIM_AUDIT_SUPPORTED, LocalModelClaimAudit.stage("Anna hat am Montag mit Markus telefoniert.", r))
    }

    @Test fun evidenceThatContradictsItselfIsReportedAsConflictWhateverTheAnswer() {
        val r = LocalGroundedRecall("Anna hat 12 Euro bezahlt. Laut Beleg hat Anna 17 Euro bezahlt.", "Erinnerung")
        assertEquals(TraceStage.CLAIM_AUDIT_CONFLICT, LocalModelClaimAudit.stage("Anna hat 12 Euro bezahlt.", r))
        // two clauses about different people with different numbers are not a conflict
        val ok = LocalGroundedRecall("Anna hat 12 Euro bezahlt und Markus hat 17 Euro bezahlt.", "Erinnerung")
        assertEquals(TraceStage.CLAIM_AUDIT_SUPPORTED, LocalModelClaimAudit.stage("Anna hat 12 Euro bezahlt.", ok))
    }
    @Test fun anOrdinalDateDotEndsNoClause() {
        val r = LocalGroundedRecall("Markus fliegt am 6. Oktober nach Graz.", "Erinnerung")
        assertEquals(TraceStage.CLAIM_AUDIT_SUPPORTED, LocalModelClaimAudit.stage("Nach Graz fliegt Markus am 6. Oktober.", r))
        assertEquals(TraceStage.CLAIM_AUDIT_UNSUPPORTED, LocalModelClaimAudit.stage("Nach Linz fliegt Markus am 6. Oktober.", r))
    }
}
