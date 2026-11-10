package at.heinz.deviceai.cockpit

import at.heinz.deviceai.chief.TurnMilestone
import at.heinz.deviceai.chief.WorkerReport
import at.heinz.deviceai.chief.WorkerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real lifecycle / tool / worker facts → exactly the matching typed animation event (never invented). */
class CockpitAnimationTriggersTest {
    private val events = mutableListOf<CockpitAnimationEvent>()
    private val bus = CockpitAnimationBus().also { it.subscribe { e -> events += e } }
    private val triggers = CockpitAnimationTriggers(bus, clock = { 1_000L })

    private inline fun <reified T> only(): List<T> = events.filterIsInstance<T>()

    @Test fun acknowledgedCommandUsesOneTimelineWithoutDuplicateThinkAction() {
        triggers.commandAcknowledged("t1", "Zeig mir\u0007 die Downloads")
        val fly = only<CockpitAnimationEvent.CommandFlyIn>().single()
        assertEquals("t1", fly.turnId)
        assertEquals("Zeig mir die Downloads", fly.commandPreview)
        assertTrue(only<CockpitAnimationEvent.ActionStarted>().isEmpty())
        val steps = only<CockpitAnimationEvent.ProcessTimeline>().last().steps
        assertEquals(listOf(StepState.DONE, StepState.ACTIVE), steps.map { it.state })
        assertEquals("Auftrag angenommen", steps.first().label)
    }

    @Test fun toolCallsMapToResearchOrToolAndCompleteTheirStep() {
        triggers.commandAcknowledged("t1", "Recherche")
        triggers.toolStarted("t1", "c1", "web_search")
        triggers.toolStarted("t1", "c2", "list_downloads")
        val actions = only<CockpitAnimationEvent.ActionStarted>().map { it.action }
        assertEquals(listOf(CockpitAction.RESEARCH, CockpitAction.TOOL), actions)
        triggers.toolFinished("t1", "c1", "web_search", succeeded = true)
        triggers.toolFinished("t1", "c2", "list_downloads", succeeded = false)
        val steps = triggers.timeline("t1").associateBy { it.key }
        assertEquals(StepState.DONE, steps.getValue("tool:c1").state)
        assertEquals(StepState.FAILED, steps.getValue("tool:c2").state)
        assertEquals(steps.values.toList(), only<CockpitAnimationEvent.ProcessTimeline>().last().steps)
    }

    @Test fun approvalAndBlockedMilestonesAreVisibleSteps() {
        triggers.commandAcknowledged("t1", "Datei löschen")
        triggers.milestone("t1", TurnMilestone.APPROVAL_NEEDED)
        assertEquals(StepState.WAITING, triggers.timeline("t1").last().state)
        triggers.milestone("t1", TurnMilestone.BLOCKED, "Keine Freigabe")
        val last = triggers.timeline("t1").last()
        assertEquals(StepState.BLOCKED, last.state)
        assertTrue(last.label.contains("Keine Freigabe"))
    }

    @Test fun endedTurnSettlesStepsAndDropsLateEvents() {
        triggers.commandAcknowledged("t1", "Hallo")
        triggers.turnEnded("t1", delivered = true)
        assertTrue(only<CockpitAnimationEvent.TurnEnded>().single().delivered)
        assertTrue(triggers.timeline("t1").none { it.state == StepState.ACTIVE })
        val before = events.size
        triggers.toolStarted("t1", "late", "web_read")
        triggers.milestone("t1", TurnMilestone.TOOL_STARTED, "spät")
        assertEquals("No animation for a closed turn", before, events.size)
    }

    @Test fun deliveryAloneDoesNotConfirmWorkOrToolOutcome() {
        triggers.commandAcknowledged("unverified", "Prüfe die Datei")
        triggers.toolStarted("unverified", "pending", "list_downloads")
        triggers.turnEnded("unverified", delivered = true)
        val states = triggers.timeline("unverified").associate { it.key to it.state.name }
        assertEquals("UNVERIFIED", states["work"])
        assertEquals("UNVERIFIED", states["tool:pending"])
    }

    @Test fun deliveredReplyCannotInventApprovalStatus() {
        triggers.commandAcknowledged("waiting", "Lösche die Datei")
        triggers.milestone("waiting", TurnMilestone.APPROVAL_NEEDED)
        triggers.turnEnded("waiting", delivered = true)
        assertEquals(StepState.UNVERIFIED, triggers.timeline("waiting").last().state)
    }

    @Test fun observedToolSuccessDoesNotLeaveFalseApprovalBlock() {
        triggers.commandAcknowledged("approved", "Lies die Datei")
        triggers.milestone("approved", TurnMilestone.APPROVAL_NEEDED)
        triggers.toolStarted("approved", "read", "read_file")
        triggers.toolFinished("approved", "read", "read_file", succeeded = true)
        triggers.turnEnded("approved", delivered = true)
        val steps = triggers.timeline("approved")
        assertEquals(StepState.DONE, steps.single { it.key == "tool:read" }.state)
        assertEquals(StepState.UNVERIFIED,
            steps.single { it.key.startsWith("m:APPROVAL_NEEDED:") }.state)
    }

    @Test fun failedTurnKeepsUnresolvedApprovalBlocked() {
        triggers.commandAcknowledged("waiting", "Lösche die Datei")
        triggers.milestone("waiting", TurnMilestone.APPROVAL_NEEDED)
        triggers.turnEnded("waiting", delivered = false)
        assertEquals(StepState.BLOCKED, triggers.timeline("waiting").last().state)
    }

    @Test fun deliveryPreservesPreviouslyObservedToolFailure() {
        triggers.commandAcknowledged("failed-tool", "Lies die Datei")
        triggers.toolStarted("failed-tool", "failure", "list_downloads")
        triggers.toolFinished("failed-tool", "failure", "list_downloads", succeeded = false)
        triggers.turnEnded("failed-tool", delivered = true)
        assertEquals(StepState.FAILED, triggers.timeline("failed-tool").last().state)
    }

    @Test fun verifiedOutcomeConfirmsWorkButDoesNotInventToolReceipt() {
        triggers.commandAcknowledged("verified", "Prüfe die Datei")
        triggers.toolStarted("verified", "known", "list_downloads")
        triggers.toolFinished("verified", "known", "list_downloads", succeeded = true)
        triggers.toolStarted("verified", "unknown", "list_downloads")
        triggers.turnEnded("verified", delivered = true, outcomeVerified = true)
        val states = triggers.timeline("verified").associate { it.key to it.state }
        assertEquals(StepState.DONE, states["work"])
        assertEquals(StepState.DONE, states["tool:known"])
        assertEquals(StepState.UNVERIFIED, states["tool:unknown"])
    }

    @Test fun undeliveredOutcomeNeverConfirmsWork() {
        triggers.commandAcknowledged("dropped", "Prüfe die Datei")
        triggers.turnEnded("dropped", delivered = false, outcomeVerified = true)
        assertEquals(StepState.FAILED, triggers.timeline("dropped").last().state)
    }

    @Test fun workerStartAndReportAreTheirOwnTimeline() {
        triggers.workerStarted("status-1", "Teamleiterstatus lesen")
        assertEquals(CockpitAction.WORKER, only<CockpitAnimationEvent.ActionStarted>().single().action)
        triggers.workerReported(WorkerReport("status-1", WorkerStatus.TIMED_OUT, "Frist", 15_000))
        assertEquals(StepState.FAILED, triggers.timeline("status-1").single().state)
        assertEquals("status-1", only<CockpitAnimationEvent.TurnEnded>().single().turnId)
    }

    @Test fun accountAndDocumentCardsOnlyFromTheirExplicitRealPaths() {
        triggers.commandAcknowledged("t1", "Lies meine Mails und das PDF")
        triggers.toolStarted("t1", "c1", "gmail_read")
        assertTrue("A tool name alone never fakes the account card", only<CockpitAnimationEvent.AccountCardFlyIn>().isEmpty())
        assertTrue(only<CockpitAnimationEvent.DocumentQuestion>().isEmpty())
        triggers.accountToolUsed("t1", "Gmail", "heinz@example.invalid")
        triggers.documentReceived("t1", "Vertrag.pdf", "application/pdf", "Soll ich das Dokument zusammenfassen?")
        assertEquals("Gmail", only<CockpitAnimationEvent.AccountCardFlyIn>().single().provider)
        assertEquals("Vertrag.pdf", only<CockpitAnimationEvent.DocumentQuestion>().single().documentName)
    }

    @Test fun brokenRendererDoesNotBreakOthersAndExtensionsPassThrough() {
        val local = CockpitAnimationBus()
        val seen = mutableListOf<CockpitAnimationEvent>()
        local.subscribe { error("kaputter Renderer") }
        local.subscribe { seen += it }
        class MathPreview : CockpitAnimationEvent.Extension("math") {
            override val turnId = "t9"; override val atMillis = 5L
        }
        local.emit(MathPreview())
        assertEquals("math", (seen.single() as CockpitAnimationEvent.Extension).kind)
    }
    @Test fun documentEventCarriesOnlyMeasuredPageCount() {
        triggers.commandAcknowledged("doc", "PDF prüfen")
        triggers.documentReceived("doc", "vertrag.pdf", "application/pdf", "Bitte prüfen", 7)
        val event = only<CockpitAnimationEvent.DocumentQuestion>().single()
        assertEquals(7, event.pageCount)

        triggers.commandAcknowledged("bad-doc", "PDF prüfen")
        triggers.documentReceived("bad-doc", "x.pdf", "application/pdf", "Bitte prüfen", -1)
        assertNull(only<CockpitAnimationEvent.DocumentQuestion>().last().pageCount)
    }


    @Test fun documentPipelineHelpersEmitOnlyCanonicalFacts() {
        triggers.commandAcknowledged("doc", "PDF")
        triggers.documentPipelineStep("doc", "unknown", "irgendwas", StepState.DONE)
        assertTrue(only<DocumentPipelineStepEvent>().isEmpty())
        triggers.documentDraftReady("doc", "   ")
        assertTrue(only<DocumentDraftReadyEvent>().isEmpty())
        triggers.documentPipelineStep("doc", "analyze", "Analysieren", StepState.ACTIVE)
        val step = only<DocumentPipelineStepEvent>().single()
        assertEquals("analyze", step.key)
        assertEquals(StepState.ACTIVE, step.state)
        triggers.documentDraftReady("doc", "Ziel: Klarheit.", coveredChars = 5, totalChars = 10,
            actions = setOf(DocumentDraftAction.SAVE))
        val draft = only<DocumentDraftReadyEvent>().single()
        assertEquals("Ziel: Klarheit.", draft.text)
        assertEquals(setOf(DocumentDraftAction.SAVE), draft.actions)
    }

}
