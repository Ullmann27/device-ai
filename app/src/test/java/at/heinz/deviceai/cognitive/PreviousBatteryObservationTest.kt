package at.heinz.deviceai.cognitive

import at.heinz.deviceai.BootstrapBrain
import at.heinz.deviceai.BootstrapKind
import at.heinz.deviceai.ToolExecutionStatus
import org.junit.Assert.*
import org.junit.Test

class PreviousBatteryObservationTest {
    private val previousQuestion = "Wie viel Prozent Akku hat mein Handy laut deiner gerade ausgeführten Geräteprüfung? Antworte in genau einem Satz. Keine neue Messung, kein Internet."

    private fun outcome(output: String, status: ToolExecutionStatus = ToolExecutionStatus.SUCCEEDED,
                        fresh: Boolean = true, replayed: Boolean = false,
                        verified: Boolean = true) = AgentRunResult(
        response = "Akku prüfen:\n$output",
        state = AgentState.RESPOND, auditTrail = emptyList(), experience = null,
        taskEvidence = AgentTaskEvidence(
            taskId = "battery-session", attempt = 0, requestSha256 = "hash",
            plannedToolNames = listOf("get_battery_info"),
            calls = listOf(AgentToolEvidence(
                callId = "battery-call", toolName = "get_battery_info",
                argumentFingerprint = "fingerprint", approvalRequested = false, ownerApproved = null,
                status = status, outputSha256 = "fresh-digest", freshOutput = fresh,
                replayed = replayed
            )),
            events = emptyList(), evaluatorSucceeded = verified, evaluationConfirmsOutcome = false,
            outcomeVerified = false, learningCandidateExperienceId = null
        )
    )

    @Test fun previousBatteryRequestMustNotStartAnotherProbe() {
        assertTrue(OwnerPreviousBatteryIntent.isPreviousReadingRequest(previousQuestion))
        assertTrue(OwnerPreviousBatteryIntent.isPreviousReadingRequest("Wie hoch war mein Akku bei der letzten Messung?"))
        assertTrue(OwnerPreviousBatteryIntent.isPreviousReadingRequest("Akku bitte ohne erneute Abfrage nennen."))
        assertTrue(OwnerPreviousBatteryIntent.isPreviousReadingRequest("Akku bitte nicht erneut messen."))
        assertTrue(OwnerPreviousBatteryIntent.isPreviousReadingRequest("Wie viel Akku haben wir vorhin gemessen?"))
        for (q in listOf("Prüfe jetzt meinen Akku", "Wie viel Akku habe ich aktuell?",
            "Zeige meinen Speicher", "Prüfe die Batterie und lade sie auf",
            "Prüf mal eben den Akku", "Check eben die Batterie",
            "Kannst du den Akku nicht prüfen?", "Kannst du nicht den Akku prüfen?")) {
            assertFalse(q, OwnerPreviousBatteryIntent.isPreviousReadingRequest(q))
        }
        val decision = BootstrapBrain.decide(previousQuestion, emptyList())
        assertEquals(BootstrapKind.REPLY, decision.kind)
        assertNull(decision.tool)
    }

    @Test fun lastVerifiedBatteryReadingIsRepeatedWithoutNewMeasurement() {
        val history = PreviousBatteryObservation()
        assertTrue(history.recordIfFresh("session-1",
            outcome("Current Battery Service state:\n  level: 75\n  scale: 100\n")))
        assertTrue(history.answer("session-1").contains("75 %"))
        assertTrue(history.answer("session-1").contains("keine neue Messung gestartet"))
        assertFalse(history.answer("session-1").contains("74 %"))
        assertTrue(history.answer("session-2").contains("kein belegter früherer Akkustand"))
    }

    @Test fun secondFreshReadingReplacesFirstOnlyAfterActualToolExecution() {
        val history = PreviousBatteryObservation()
        assertTrue(history.recordIfFresh("s", outcome("  level: 75\n  scale: 100\n")))
        assertTrue(history.recordIfFresh("s", outcome("  level: 74\n  scale: 100\n")))
        assertTrue(history.answer("s").contains("74 %"))
        assertFalse(history.answer("s").contains("75 %"))
    }

    @Test fun conciseAndroidBatteryOutputIsRecognized() {
        val history = PreviousBatteryObservation()
        assertTrue(history.recordIfFresh("s", outcome(
            "Aktueller Akkustand: 74 % (Android-Batteriedienst).\nLadezustand: entlädt sich.")))
        assertTrue(history.answer("s").contains("74 %"))
    }

    @Test fun invalidAndUnverifiedToolOutputNeverBecomesRememberedBatteryFact() {
        val history = PreviousBatteryObservation()
        assertFalse(history.recordIfFresh("s", outcome("level: 74\nscale: 100", fresh = false)))
        assertFalse(history.recordIfFresh("s", outcome("level: 74\nscale: 100", replayed = true)))
        assertFalse(history.recordIfFresh("s", outcome("level: 74\nscale: 100", verified = false)))
        assertFalse(history.recordIfFresh("s", outcome("level: 74\nscale: 100", status = ToolExecutionStatus.FAILED)))
        assertFalse(history.recordIfFresh("s", outcome("level: 74\nscale: 0")))
        assertFalse(history.recordIfFresh("s", outcome("level: 101\nscale: 100")))
        assertFalse(history.recordIfFresh("s", outcome("Modell sagt: 75 %")))
        assertTrue(history.answer("s").contains("kein belegter früherer Akkustand"))
    }

    @Test fun priorReadingCannotCrossOwnerSessionBoundary() {
        val history = PreviousBatteryObservation()
        assertTrue(history.recordIfFresh("old", outcome("level: 75\nscale: 100")))
        assertFalse(history.answer("new").contains("75 %"))
        assertTrue(history.answer("new").contains("keine neue Messung gestartet"))
    }
    @Test fun freshComparisonMustMeasureExactlyOnceAndUseSessionEvidence() {
        val request = "Miss den Akku nochmal und vergleiche mit der letzten Messung."
        assertFalse(OwnerPreviousBatteryIntent.isPreviousReadingRequest(request))
        assertTrue(OwnerPreviousBatteryIntent.isFreshComparisonRequest(request))
        assertFalse(OwnerPreviousBatteryIntent.isFreshComparisonRequest(previousQuestion))
        assertTrue(OwnerPreviousBatteryIntent.isPreviousReadingRequest("Akku bitte nicht erneut messen."))
        val action = BootstrapBrain.decide(request, emptyList())
        assertEquals(BootstrapKind.DEVICE_TOOL, action.kind)
        assertEquals("get_battery_info", action.tool)
        val observations = PreviousBatteryObservation()
        assertTrue(observations.recordIfFresh("original", outcome("level: 75\nscale: 100\n")))
        assertEquals(75, observations.lastPercent("original"))
        assertNull(observations.lastPercent("new-session"))
        assertTrue(observations.recordIfFresh("original", outcome("level: 74\nscale: 100\n")))
        assertEquals(74, observations.lastPercent("original"))
        assertFalse(OwnerPreviousBatteryIntent.isFreshComparisonRequest("Prüfe nicht den Akku; vergleiche nur die letzte Messung."))
    }

}
