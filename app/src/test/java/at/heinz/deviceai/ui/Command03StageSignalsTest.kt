package at.heinz.deviceai.ui

import at.heinz.deviceai.avatar.AvatarRuntimeSignals
import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.CockpitAnimationBus
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.MEMORY_TURN
import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.ui.Command03StageSignals.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Code63: the productive HTML stage receives presence, quality tier and the real running activity.
 * Before, research/learning/approval/blocked, thermal state, memory recall and tool steps reached
 * only native renderers that are hidden while the HTML stage is shown.
 */
class Command03StageSignalsTest {
    @Test fun presenceFollowsTheAvatarDirectorPrecedence() {
        fun presence(build: AvatarRuntimeSignals) = Command03StageSignals.presence(build)
        assertEquals("IDLE", presence(AvatarRuntimeSignals()))
        assertEquals("THINKING", presence(AvatarRuntimeSignals(busy = true)))
        assertEquals("LEARNING", presence(AvatarRuntimeSignals(busy = true, learning = true)))
        assertEquals("IDLE", presence(AvatarRuntimeSignals(learning = true)))
        assertEquals("RESEARCHING", presence(AvatarRuntimeSignals(busy = true, researching = true)))
        assertEquals("BLOCKED", presence(AvatarRuntimeSignals(busy = true, researching = true, blocked = true)))
        assertEquals("SPEAKING", presence(AvatarRuntimeSignals(speaking = true, blocked = true)))
        assertEquals("LISTENING", presence(AvatarRuntimeSignals(listening = true, speaking = true)))
        assertEquals("WAITING_FOR_APPROVAL",
            presence(AvatarRuntimeSignals(listening = true, busy = true, awaitingApproval = true)))
    }

    @Test fun qualityLowersOnlyOnRealThermalOrPowerSaveSignals() {
        assertEquals("FULL", Command03StageSignals.quality(null, null, Profile.AUTO))
        assertEquals("FULL", Command03StageSignals.quality(0, false, Profile.AUTO))
        // Fold7 heat finding (Code64): AUTO steps down one status earlier – before the thermal guard stops the model.
        assertEquals("BALANCED", Command03StageSignals.quality(1, false, Profile.AUTO))
        assertEquals("REDUCED", Command03StageSignals.quality(2, false, Profile.AUTO))
        assertEquals("REDUCED", Command03StageSignals.quality(3, false, Profile.AUTO))
        assertEquals("REDUCED", Command03StageSignals.quality(0, true, Profile.AUTO))
        // An out-of-range platform value is not evidence of heat.
        assertEquals("FULL", Command03StageSignals.quality(99, false, Profile.AUTO))
        assertEquals("FULL", Command03StageSignals.quality(-1, null, Profile.AUTO))
    }

    @Test fun refreshRateIsLoweredUnlessTheOwnerChoseFullQuality() {
        assertEquals(60f, Command03StageSignals.preferredRefreshRate(Profile.AUTO), 0f)
        assertEquals(60f, Command03StageSignals.preferredRefreshRate(Profile.SAVER), 0f)
        assertEquals(0f, Command03StageSignals.preferredRefreshRate(Profile.FULL), 0f)
    }

    @Test fun ownerProfilesAreRespectedButCriticalHeatAlwaysReduces() {
        assertEquals("FULL", Command03StageSignals.quality(3, true, Profile.FULL))
        assertEquals("REDUCED", Command03StageSignals.quality(4, false, Profile.FULL))
        assertEquals("REDUCED", Command03StageSignals.quality(0, false, Profile.SAVER))
        assertEquals(Profile.AUTO, Profile.of(null))
        assertEquals(Profile.AUTO, Profile.of("unbekannt"))
        assertEquals(Profile.SAVER, Profile.of("SAVER"))
    }

    @Test fun noEventMeansNoActivity() {
        assertNull(Command03ActivityProjection().snapshot())
    }

    @Test fun aRealToolTurnShowsItsWorkStepsAndSettlesWhenTheTurnEnds() {
        val projection = Command03ActivityProjection()
        val bus = CockpitAnimationBus().apply { subscribe(projection) }
        bus.emit(CockpitAnimationEvent.ActionStarted("t1", 10L, CockpitAction.TOOL, "list_files /sdcard/Download"))
        bus.emit(CockpitAnimationEvent.ProcessTimeline("t1", 11L, listOf(
            ProcessStep("a", "Auftrag verstanden", StepState.DONE),
            ProcessStep("b", "Downloads lesen", StepState.ACTIVE),
            ProcessStep("c", "Ergebnis prüfen", StepState.WAITING))))
        val running = projection.snapshot()!!
        assertEquals("TOOL", running.getString("kind"))
        assertEquals("Werkzeug läuft", running.getString("title"))
        assertEquals("list_files /sdcard/Download", running.getString("detail"))
        assertTrue(running.getBoolean("active"))
        val steps = running.getJSONArray("steps")
        assertEquals(3, steps.length())
        assertEquals("ACTIVE", steps.getJSONObject(1).getString("state"))
        assertEquals("Ergebnis prüfen", steps.getJSONObject(2).getString("label"))

        // A stale turn's end does not settle the running one.
        bus.emit(CockpitAnimationEvent.TurnEnded("t0", 12L, delivered = true))
        assertTrue(projection.snapshot()!!.getBoolean("active"))
        bus.emit(CockpitAnimationEvent.TurnEnded("t1", 13L, delivered = true))
        val settled = projection.snapshot()!!
        assertFalse(settled.getBoolean("active"))
        assertTrue(settled.getLong("revision") > running.getLong("revision"))
        assertEquals(3, settled.getJSONArray("steps").length())
    }

    @Test fun memoryRecallReportsTheRealCountAndNeverInventsHits() {
        val projection = Command03ActivityProjection()
        projection.render(CockpitAnimationEvent.RecallHits(MEMORY_TURN, 1L, "hash", 3, 0.8125f))
        val hits = projection.snapshot()!!
        assertEquals("MEMORY", hits.getString("kind"))
        assertEquals("Gedächtnis durchsucht", hits.getString("title"))
        assertEquals("3 Treffer · beste Übereinstimmung 0,81", hits.getString("detail"))
        assertFalse(hits.getBoolean("active"))

        projection.render(CockpitAnimationEvent.RecallHits(MEMORY_TURN, 2L, "hash", 0, null))
        assertEquals("Keine Treffer", projection.snapshot()!!.getString("detail"))
        projection.render(CockpitAnimationEvent.MemoryStored(MEMORY_TURN, 3L, "e1", "CONFIRMED", 1))
        assertEquals("1 Eintrag", projection.snapshot()!!.getString("detail"))
    }

    @Test fun aMemoryEventInsideARunningTurnKeepsThatTurnsStepsAndRunningState() {
        val projection = Command03ActivityProjection()
        projection.render(CockpitAnimationEvent.ActionStarted("t1", 1L, CockpitAction.THINK, "Antwort wird erstellt"))
        projection.render(CockpitAnimationEvent.ProcessTimeline("t1", 2L,
            listOf(ProcessStep("a", "Gedächtnis prüfen", StepState.ACTIVE))))
        projection.render(CockpitAnimationEvent.RecallHits(MEMORY_TURN, 3L, "hash", 2, null))
        val snapshot = projection.snapshot()!!
        assertEquals("MEMORY", snapshot.getString("kind"))
        assertEquals("2 Treffer", snapshot.getString("detail"))
        assertTrue(snapshot.getBoolean("active"))
        assertEquals(1, snapshot.getJSONArray("steps").length())
        projection.render(CockpitAnimationEvent.TurnEnded("t1", 4L, delivered = true))
        assertFalse(projection.snapshot()!!.getBoolean("active"))
    }

    @Test fun documentIntakeNamesTheRealFileAndMeasuredPages() {
        val projection = Command03ActivityProjection()
        projection.render(CockpitAnimationEvent.DocumentQuestion("t2", 1L, "Argon4.pdf", "application/pdf",
            "Erkläre den wichtigsten Inhalt", pageCount = 12))
        assertEquals("Argon4.pdf · 12 Seiten", projection.snapshot()!!.getString("detail"))
        projection.render(CockpitAnimationEvent.DocumentQuestion("t3", 2L, "Notiz.pdf", "application/pdf", "?", null))
        assertEquals("Notiz.pdf", projection.snapshot()!!.getString("detail"))
        assertEquals(0, projection.snapshot()!!.getJSONArray("steps").length())
    }

    @Test fun textsAndStepsAreBoundedForThePage() {
        val projection = Command03ActivityProjection()
        projection.render(CockpitAnimationEvent.ActionStarted("t1", 1L, CockpitAction.EXECUTE,
            "x".repeat(500) + "\n\u0007ende"))
        val detail = projection.snapshot()!!.getString("detail")
        assertTrue(detail.length <= Command03ActivityProjection.MAX_DETAIL)
        assertTrue(detail.endsWith("…"))
        val many = (1..12).map { ProcessStep("k$it", "Schritt $it", if (it == 9) StepState.ACTIVE else
            if (it < 9) StepState.DONE else StepState.WAITING) }
        projection.render(CockpitAnimationEvent.ProcessTimeline("t1", 2L, many))
        val steps = projection.snapshot()!!.getJSONArray("steps")
        assertEquals(Command03ActivityProjection.MAX_STEPS, steps.length())
        val labels = (0 until steps.length()).map { steps.getJSONObject(it).getString("label") }
        // The running step and the next one are inside the window.
        assertTrue(labels.contains("Schritt 9"))
        assertTrue(labels.contains("Schritt 10"))
    }

    @Test fun anAnnouncedNotificationIsShownWithoutEndingARunningTurn() {
        val projection = Command03ActivityProjection()
        projection.render(CockpitAnimationEvent.ActionStarted("t1", 1L, CockpitAction.THINK, "Antwort wird erstellt"))
        val before = projection.snapshot()!!.getLong("revision")
        projection.notification("WhatsApp", "Nachricht von Anna.")
        val shown = projection.snapshot()!!
        assertEquals("NOTIFICATION", shown.getString("kind"))
        assertEquals("WhatsApp", shown.getString("title"))
        assertEquals("Nachricht von Anna.", shown.getString("detail"))
        assertTrue(shown.getBoolean("active"))
        assertTrue(shown.getLong("revision") > before)
        projection.render(CockpitAnimationEvent.TurnEnded("t1", 2L, delivered = true))
        assertFalse(projection.snapshot()!!.getBoolean("active"))
    }

    @Test fun clearRemovesTheActivityAndAdvancesTheRevision() {
        val projection = Command03ActivityProjection()
        projection.render(CockpitAnimationEvent.CalculationShown("t1", 1L, "2 + 2", emptyList(), 4.0, null,
            at.heinz.deviceai.cockpit.ChartKind.NONE))
        assertEquals("2 + 2 = 4", projection.snapshot()!!.getString("detail"))
        projection.clear()
        assertNull(projection.snapshot())
    }
}
