package at.heinz.deviceai.ui

import at.heinz.deviceai.cognitive.*
import org.junit.Assert.*
import org.junit.Test

class ComputerPanelPresentationTest {
    private val intent = OwnerComputerIntent(ComputerApp.WHATSAPP, ComputerGoal.SEARCH, "Testkontakt")
    private val reducer = CockpitWorkReducer().apply { bind("task", 7) }
    private var sequence = 0L
    private var now = 1_000_000L

    private fun event(stage: CockpitWorkEvent.Stage, step: String? = null,
                      action: CockpitWorkEvent.Action? = null, revision: Long = 0, frame: Long = 0) {
        now++
        assertTrue(reducer.apply(CockpitWorkEvent("task", 7, ++sequence, now, 1L, stage,
            step, action, observationRevision = revision, frameSequence = frame), now))
    }

    private fun state(result: OwnerComputerWorkflow.Result? = null): ComputerPanelState =
        checkNotNull(ComputerPanelState.from("task", 7, intent, reducer.snapshot(now), result))

    @Test fun acceptedTaskShowsGoalAndPendingVerification() {
        event(CockpitWorkEvent.Stage.ACCEPTED)
        val state = state()
        assertEquals("WhatsApp", state.appLabel)
        assertEquals("Suche prüfen", state.goalLabel)
        assertEquals("Auftrag angenommen", state.planningRows().single { it.label == "Ablauf" }.value)
        assertFalse(state.verified)
        assertEquals("Ziel noch nicht bestätigt", state.resultRows().first().value)
        assertTrue(state.resultRows().none { it.label == "Beobachtung" || it.label == "Livebild" })
        assertTrue(state.planningRows().all { it.fraction == null })
    }

    @Test fun checkedStepIsDistinctFromCheckedWholeTask() {
        event(CockpitWorkEvent.Stage.ACCEPTED)
        event(CockpitWorkEvent.Stage.DISPATCHED, "step", CockpitWorkEvent.Action.SET_TEXT, 3)
        assertFalse(state().verified)
        event(CockpitWorkEvent.Stage.OBSERVED, "step", revision = 4, frame = 12)
        assertEquals("Ziel noch nicht bestätigt", state().resultRows().first().value)
        event(CockpitWorkEvent.Stage.VERIFIED, "step", revision = 4, frame = 12)
        val step = state()
        assertFalse(step.verified)
        assertEquals("Letzter Schritt bestätigt", step.resultRows().first().value)
        assertEquals(4L, step.snapshot.observationRevision)
        assertEquals(12L, step.snapshot.frameSequence)
        assertEquals("Beobachtete Android-Ansicht", step.resultRows().single { it.label == "Quelle" }.value)
        assertTrue(step.resultRows().none { it.value.startsWith("#") })
        event(CockpitWorkEvent.Stage.COMPLETED)
        assertFalse(state().verified)
        val completed = state(OwnerComputerWorkflow.Result(OwnerComputerWorkflow.Status.VERIFIED,
            "Suchfeld wurde geprüft."))
        assertTrue(completed.verified)
        assertEquals("Auftragsziel bestätigt", completed.resultRows().first().value)
        assertEquals("Suchfeld wurde geprüft.", completed.resultMessage)
    }

    @Test fun mismatchedTasksSessionsAndPrematureResultsAreRejected() {
        assertNull(ComputerPanelState.from("task", 7, intent, reducer.snapshot(now)))
        event(CockpitWorkEvent.Stage.ACCEPTED)
        val snapshot = reducer.snapshot(now)
        assertNull(ComputerPanelState.from("successor", 7, intent, snapshot))
        assertNull(ComputerPanelState.from("task", 8, intent, snapshot))
        assertNull(ComputerPanelState.from("task", 7, intent, snapshot,
            OwnerComputerWorkflow.Result(OwnerComputerWorkflow.Status.VERIFIED, "Erledigt")))
    }

    @Test fun ownerQuestionNeverClaimsSuccessfulSending() {
        event(CockpitWorkEvent.Stage.ACCEPTED)
        event(CockpitWorkEvent.Stage.ASK_OWNER)
        val send = OwnerComputerIntent(ComputerApp.WHATSAPP, ComputerGoal.SEND_MESSAGE)
        val state = checkNotNull(ComputerPanelState.from("task", 7, send, reducer.snapshot(now),
            OwnerComputerWorkflow.Result(OwnerComputerWorkflow.Status.ASK_OWNER,
                "Entwurf geprüft; bitte selbst senden.", true)))
        assertFalse(state.verified)
        assertEquals("Entwurf prüfen · Versand offen", state.goalLabel)
        assertEquals("Rückfrage an Besitzer", state.planningRows().single { it.label == "Ablauf" }.value)
        assertTrue(state.resultRows().none { it.value == "Auftragsziel bestätigt" })
    }

    @Test fun stopAndPauseCannotKeepConfirmedOutcomeOrPendingAction() {
        event(CockpitWorkEvent.Stage.ACCEPTED)
        event(CockpitWorkEvent.Stage.DISPATCHED, "step", CockpitWorkEvent.Action.CLICK, 3)
        event(CockpitWorkEvent.Stage.PAUSED)
        val paused = state(OwnerComputerWorkflow.Result(OwnerComputerWorkflow.Status.PAUSED, "Bitte prüfen."))
        assertFalse(paused.verified)
        assertTrue(paused.planningRows().none { it.label == "Aktion" })
        event(CockpitWorkEvent.Stage.STOPPED)
        val stopped = state(OwnerComputerWorkflow.Result(OwnerComputerWorkflow.Status.CANCELLED, "Abgebrochen."))
        assertFalse(stopped.verified)
        assertEquals("Auftrag abgebrochen", stopped.resultRows().first().value)
        assertNull(ComputerPanelState.from("task", 7, intent, reducer.snapshot(now),
            OwnerComputerWorkflow.Result(OwnerComputerWorkflow.Status.VERIFIED, "Alter Erfolg")))
    }

    @Test fun bothExistingSideSurfacesDrawActualWorkWithoutDocument() {
        event(CockpitWorkEvent.Stage.ACCEPTED)
        val painter = JarvisPresentationRecordingPainter()
        val dashboard = ContinuumDashboardState(computer = state())
        val shell = CinematicWorkstationLayout.create(884f, 760f)
        val content = JarvisShellContent.forState({ dashboard }, shell = { shell })
        JarvisShellRenderer.drawForeground(painter, shell, content)
        val text = painter.texts.joinToString(" ")
        assertTrue(painter.texts.contains("Auftrag"))
        assertTrue(painter.texts.contains("Ergebnis"))
        assertTrue(painter.texts.any { it.contains("WhatsApp") })
        assertTrue(text.contains("Suche prüfen"))
        assertTrue(text.contains("noch nicht bestätigt"))
        assertFalse(painter.texts.contains("Dokument"))
        assertFalse(painter.texts.contains("Entwurf"))
    }


    @Test fun retainedPanelsRequireTheirOriginalCaptureSource() {
        val binding = LiveFrameBinding("task", 7, "com.whatsapp", "at.heinz.deviceai.continuumlocal",
            LiveFrameOrigin.ACCESSIBILITY_WINDOW)
        val source = LiveMonitorStatus(LiveMonitorState.LIVE, null, binding, null, 12, 0)
        fun accepts(status: LiveMonitorStatus) =
            ComputerPanelState.sourceMatches("task", "com.whatsapp", 7, status)

        assertTrue(accepts(source))
        assertFalse(accepts(source.copy(binding = binding.copy(taskId = "successor"))))
        assertFalse(accepts(source.copy(binding = binding.copy(captureSession = 8))))
        assertFalse(accepts(source.copy(binding = binding.copy(targetPackage = "com.android.chrome"))))
        assertFalse(accepts(source.copy(binding = null)))
        assertFalse(accepts(source.copy(state = LiveMonitorState.NO_SOURCE)))
        assertFalse(accepts(source.copy(state = LiveMonitorState.ENDED,
            reason = LiveMonitorReason.CAPTURE_STOPPED)))
        assertFalse(accepts(source.copy(state = LiveMonitorState.PAUSED,
            reason = LiveMonitorReason.PERMISSION_REVOKED)))
        assertFalse(ComputerPanelState.sourceMatches("task", "com.whatsapp", 0, source))
    }

    @Test fun pendingAndPausedWorkAreNotClaimedAsLiveVideo() {
        val binding = LiveFrameBinding("task", 7, "com.whatsapp", "at.heinz.deviceai.continuumlocal",
            LiveFrameOrigin.ACCESSIBILITY_WINDOW)
        val waiting = LiveMonitorStatus(LiveMonitorState.WAITING, null, binding, null, 0, null)
        assertTrue(ComputerPanelState.sourceMatches("task", "com.whatsapp", 7, waiting))
        assertTrue(ComputerPanelState.sourceMatches("task", "com.whatsapp", 7,
            waiting.copy(state = LiveMonitorState.PAUSED, reason = LiveMonitorReason.FRAME_STALE)))
        event(CockpitWorkEvent.Stage.ACCEPTED)
        assertFalse(state().verified)
        event(CockpitWorkEvent.Stage.PAUSED)
        assertEquals("Arbeit pausiert", state().planningRows().single { it.label == "Ablauf" }.value)
        assertFalse(state().verified)
    }

    @Test fun clearingComputerStateReturnsToExistingDocumentContent() {
        val painter = JarvisPresentationRecordingPainter()
        val dashboard = ContinuumDashboardState(document = HollywoodFuiLiveData.document(
            "Synthetisch.pdf", "application/pdf", null, pageCount = 2, currentPage = 1))
        JarvisCockpitPanels.drawDocumentPreview(painter, JarvisShellLayout.Box(0f, 0f, 240f, 240f), dashboard, true)
        assertTrue(painter.texts.contains("Synthetisch.pdf"))
        assertFalse(painter.texts.contains("WhatsApp"))
    }
}
