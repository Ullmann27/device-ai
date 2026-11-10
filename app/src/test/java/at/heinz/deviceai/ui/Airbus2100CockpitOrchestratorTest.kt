package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.AlarmKind
import at.heinz.deviceai.cockpit.ChartKind
import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.DocumentDraftAction
import at.heinz.deviceai.cockpit.DocumentDraftReadyEvent
import at.heinz.deviceai.cockpit.DocumentPipelineStepEvent
import at.heinz.deviceai.cockpit.MEMORY_TURN
import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Airbus2100CockpitOrchestratorTest {
    @Test
    fun realTurnUsesOneMonotonicPhaseAndLiveMissionTimer() {
        var now = 0L
        val scheduled = mutableListOf<Runnable>()
        val scenes = mutableListOf<JarvisActionScenes.FuiProfile>()
        val telemetry = mutableListOf<String?>()
        val payloads = mutableListOf<HollywoodFuiLiveData?>()
        val finished = mutableListOf<String>()
        var cues = 0
        val orchestrator = Airbus2100CockpitOrchestrator(
            schedule = { runnable, _ -> scheduled += runnable },
            cancel = { scheduled.remove(it) },
            onSceneProfile = { scenes += it.fuiProfile },
            onLiveData = { payloads += it },
            onTelemetry = { telemetry += it },
            onHoloCue = { cues++ },
            onPostMission = { finished += it },
            clockNanos = { now }
        )

        orchestrator.render(CockpitAnimationEvent.CommandFlyIn("t1", 0L, "Baue die App"))
        assertEquals(1, orchestrator.phaseRankForTest())
        assertEquals("t1", orchestrator.activeTurnForTest())
        assertTrue(telemetry.last()!!.contains("0,0"))
        assertEquals(1, scheduled.size)

        now = 49_700_000_000L
        scheduled.removeAt(0).run()
        assertTrue(telemetry.last()!!.contains("49,7"))

        orchestrator.render(CockpitAnimationEvent.ActionStarted(
            "t1", 1L, CockpitAction.DEVELOP, "build", stepKey = "dev"))
        assertEquals(JarvisActionScenes.FuiProfile.CODE_FORGE, scenes.last())
        assertTrue(payloads.last() is HollywoodFuiLiveData.Timeline)
        assertEquals("build", (payloads.last() as HollywoodFuiLiveData.Timeline).lines.single().label)
        assertEquals(2, orchestrator.phaseRankForTest())

        orchestrator.render(CockpitAnimationEvent.ProcessTimeline(
            "t1", 2L, listOf(ProcessStep("approval", "Freigabe", StepState.WAITING))))
        assertEquals(3, orchestrator.phaseRankForTest())
        val waiting = payloads.last() as HollywoodFuiLiveData.Timeline
        assertEquals("Freigabe", waiting.lines.single().label)
        assertEquals(StepState.WAITING, waiting.lines.single().state)

        // A later THINK callback must never downgrade the in-place scene.
        orchestrator.render(CockpitAnimationEvent.ActionStarted(
            "t1", 3L, CockpitAction.THINK, "weiter"))
        assertEquals(3, orchestrator.phaseRankForTest())

        orchestrator.render(CockpitAnimationEvent.TurnEnded("t1", 4L, delivered = true))
        assertNull(orchestrator.activeTurnForTest())
        assertEquals(listOf("t1"), finished)
        assertTrue(telemetry.last()!!.contains("49,7 SEK"))
        assertTrue(cues >= 1)
    }

    @Test
    fun realEventFamiliesRouteToTheSevenExistingProfilesWithoutInventingTools() {
        var now = 0L
        val scenes = mutableListOf<JarvisActionScenes.FuiProfile>()
        val payloads = mutableListOf<HollywoodFuiLiveData?>()
        val orchestrator = Airbus2100CockpitOrchestrator(
            schedule = { _, _ -> },
            cancel = {},
            onSceneProfile = { scenes += it.fuiProfile },
            onLiveData = { payloads += it },
            onTelemetry = {},
            onHoloCue = {},
            onPostMission = {},
            clockNanos = { now }
        )
        orchestrator.render(CockpitAnimationEvent.CommandFlyIn("t", 0L, "prüfen"))

        orchestrator.render(CockpitAnimationEvent.ActionStarted("t", 1L, CockpitAction.TOOL, "launch_app"))
        orchestrator.render(CockpitAnimationEvent.AccountCardFlyIn("t", 2L, "Gmail", "owner@example.invalid"))
        orchestrator.render(CockpitAnimationEvent.DocumentQuestion(
            "t", 3L, "x.pdf", "application/pdf", "Was tun?", pageCount = 4
        ))
        orchestrator.render(CockpitAnimationEvent.CalculationShown(
            "t", 4L, "1+1", listOf(at.heinz.deviceai.cockpit.CalcStep("Summe", 2.0)),
            2.0, null, ChartKind.NONE
        ))
        orchestrator.render(CockpitAnimationEvent.AlarmRaised(
            "alarm", 5L, AlarmKind.SECURITY_FINDING, "Package scan", 1.0, 0.0
        ))

        assertTrue(JarvisActionScenes.FuiProfile.APP_CONDUIT in scenes)
        assertTrue(JarvisActionScenes.FuiProfile.DOCUMENT_BLUEPRINT in scenes)
        assertTrue(JarvisActionScenes.FuiProfile.CHRONO_GAUGE in scenes)
        assertTrue(JarvisActionScenes.FuiProfile.SECURITY_XRAY in scenes)
        assertTrue(payloads.any { it is HollywoodFuiLiveData.App })
        assertTrue(payloads.any { it is HollywoodFuiLiveData.Document })
        assertTrue(payloads.any { it is HollywoodFuiLiveData.Calculation })
        assertTrue(payloads.any { it is HollywoodFuiLiveData.Security })
        val app = payloads.filterIsInstance<HollywoodFuiLiveData.App>().single()
        assertEquals("Gmail", app.provider)
        val calculation = payloads.filterIsInstance<HollywoodFuiLiveData.Calculation>().single()
        assertEquals(2.0, calculation.result, 0.0)
        assertEquals("Summe", calculation.steps.single().label)
        val security = payloads.filterIsInstance<HollywoodFuiLiveData.Security>().single()
        assertEquals("Package scan", security.sourceLabel)
        val document = payloads.filterIsInstance<HollywoodFuiLiveData.Document>().single()
        assertEquals(4, document.pageCount)
        assertEquals("x.pdf", document.documentName)
    }

    @Test
    fun backgroundStopsTimerTicksUntilForegroundReturns() {
        val scheduled = mutableListOf<Runnable>()
        val orchestrator = Airbus2100CockpitOrchestrator(
            schedule = { runnable, _ -> scheduled += runnable },
            cancel = { scheduled.remove(it) },
            onSceneProfile = {},
            onLiveData = {},
            onTelemetry = {},
            onHoloCue = {},
            onPostMission = {}
        )
        orchestrator.render(CockpitAnimationEvent.CommandFlyIn("t", 0L, "x"))
        assertEquals(1, scheduled.size)
        orchestrator.setForeground(false)
        assertTrue(scheduled.isEmpty())
        orchestrator.setForeground(true)
        assertEquals(1, scheduled.size)
        orchestrator.close()
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun documentQuestionAttachesAcrossTurnIdsAndKeepsTheRealQuestion() {
        val scenes = mutableListOf<JarvisActionScenes.FuiProfile>()
        val payloads = mutableListOf<HollywoodFuiLiveData?>()
        val orchestrator = Airbus2100CockpitOrchestrator(
            schedule = { _, _ -> },
            cancel = {},
            onSceneProfile = { scenes += it.fuiProfile },
            onLiveData = { payloads += it },
            onTelemetry = {},
            onHoloCue = {},
            onPostMission = {}
        )
        orchestrator.render(CockpitAnimationEvent.CommandFlyIn("verbal-turn", 0L, "Lies diese PDF"))
        assertEquals(JarvisActionScenes.FuiProfile.GENERIC, scenes.last())

        orchestrator.render(CockpitAnimationEvent.DocumentQuestion(
            "document-w-1", 1L, "Projektübersicht.pdf", "application/pdf",
            "Lies die PDF und erstelle eine Zusammenfassung als Entwurf.", pageCount = 4
        ))
        val document = payloads.filterIsInstance<HollywoodFuiLiveData.Document>().last()
        val visible = requireNotNull(DocumentBlueprintView.visible(document))
        assertEquals("Lies die PDF und erstelle eine Zusammenfassung als Entwurf.", visible.question)
        assertEquals("1/4", visible.pageLabel)
        assertEquals(JarvisActionScenes.FuiProfile.DOCUMENT_BLUEPRINT, scenes.last())
        assertTrue(visible.buttons.isEmpty())
        assertNull(visible.draftTitle)

        orchestrator.render(CockpitAnimationEvent.ProcessTimeline(
            "verbal-turn", 2L, listOf(ProcessStep("work", "Bearbeitung", StepState.ACTIVE))
        ))
        val afterTimeline = payloads.filterIsInstance<HollywoodFuiLiveData.Document>().last()
        assertEquals(
            "Lies die PDF und erstelle eine Zusammenfassung als Entwurf.",
            DocumentBlueprintView.visible(afterTimeline)!!.question
        )

        orchestrator.render(DocumentPipelineStepEvent(
            "document-w-1", 3L, DocumentPipelineStepEvent.KEY_ANALYZE, "Analysieren", StepState.DONE
        ))
        orchestrator.render(DocumentDraftReadyEvent(
            "document-w-1", 4L,
            "Ziel: Arbeitsabläufe vereinfachen.\nVorgehen: Schritte klar gliedern.",
            coveredChars = 10,
            totalChars = 20,
            actions = setOf(DocumentDraftAction.OPEN)
        ))
        val drafted = DocumentBlueprintView.visible(
            payloads.filterIsInstance<HollywoodFuiLiveData.Document>().last()
        )!!
        assertEquals("Arbeitsabläufe vereinfachen.", drafted.draftGoal)
        assertEquals(listOf("Entwurf öffnen"), drafted.buttons)
        assertEquals(50, drafted.progressPercent)
        assertEquals("Lies die PDF und erstelle eine Zusammenfassung als Entwurf.", drafted.question)
    }

    @Test
    fun memoryAndWorkerEventsReachTheirWorldsWithRealCounts() {
        val scenes = mutableListOf<JarvisActionScenes.FuiProfile>()
        val payloads = mutableListOf<HollywoodFuiLiveData?>()
        val orchestrator = Airbus2100CockpitOrchestrator(
            schedule = { _, _ -> },
            cancel = {},
            onSceneProfile = { scenes += it.fuiProfile },
            onLiveData = { payloads += it },
            onTelemetry = {},
            onHoloCue = {},
            onPostMission = {}
        )
        orchestrator.render(CockpitAnimationEvent.CommandFlyIn("t", 0L, "arbeite"))
        orchestrator.render(CockpitAnimationEvent.ActionStarted("worker-a", 1L, CockpitAction.WORKER, "Datei lesen"))
        orchestrator.render(CockpitAnimationEvent.ActionStarted("worker-b", 2L, CockpitAction.WORKER, "Tests prüfen"))
        assertEquals(JarvisActionScenes.FuiProfile.SWARM_GRID, scenes.last())
        val swarm = payloads.filterIsInstance<HollywoodFuiLiveData.Timeline>().last()
        assertEquals(2, swarm.lines.size)
        assertEquals("Datei lesen", swarm.lines[0].label)
        assertEquals("Tests prüfen", swarm.lines[1].label)

        orchestrator.render(CockpitAnimationEvent.RecallHits(MEMORY_TURN, 3L, "q", 3, 0.8f))
        assertEquals(JarvisActionScenes.FuiProfile.EVIDENCE_CONSTELLATION, scenes.last())
        val evidence = payloads.filterIsInstance<HollywoodFuiLiveData.Evidence>().single()
        assertEquals(3, evidence.count)
        assertEquals(0.8f, evidence.topScore)

        orchestrator.render(CockpitAnimationEvent.ActionStarted("t", 4L, CockpitAction.THINK, "weiterdenken"))
        // A stronger world is already showing; THINK must not overwrite it.
        assertEquals(JarvisActionScenes.FuiProfile.EVIDENCE_CONSTELLATION, scenes.last())
    }

    @Test
    fun documentDraftSurvivesVerbalCompletionAndRejectsStaleDocumentTurns() {
        val payloads = mutableListOf<HollywoodFuiLiveData?>()
        val orchestrator = Airbus2100CockpitOrchestrator(
            schedule = { _, _ -> }, cancel = {},
            onSceneProfile = {}, onLiveData = { payloads += it },
            onTelemetry = {}, onHoloCue = {}, onPostMission = {}
        )
        orchestrator.render(CockpitAnimationEvent.CommandFlyIn("speech", 0L, "PDF lesen"))
        orchestrator.render(CockpitAnimationEvent.DocumentQuestion(
            "document-1", 1L, "Akten.pdf", "application/pdf", "Bitte analysieren", pageCount = 4
        ))
        orchestrator.render(CockpitAnimationEvent.TurnEnded("speech", 2L, delivered = true))
        assertNull(orchestrator.activeTurnForTest())

        val count = payloads.size
        orchestrator.render(DocumentPipelineStepEvent(
            "document-stale", 3L, DocumentPipelineStepEvent.KEY_ANALYZE,
            "Analysieren", StepState.DONE
        ))
        orchestrator.render(DocumentDraftReadyEvent(
            "document-stale", 4L, "Ziel: Falsches Dokument", actions = setOf(DocumentDraftAction.OPEN)
        ))
        assertEquals(count, payloads.size)

        orchestrator.render(DocumentPipelineStepEvent(
            "document-1", 5L, DocumentPipelineStepEvent.KEY_ANALYZE,
            "Analysieren", StepState.DONE
        ))
        orchestrator.render(DocumentDraftReadyEvent(
            "document-1", 6L, "Ziel: Korrektes Dokument",
            coveredChars = 5, totalChars = 10, actions = setOf(DocumentDraftAction.OPEN)
        ))
        val document = requireNotNull(payloads.last() as? HollywoodFuiLiveData.Document)
        val visible = requireNotNull(DocumentBlueprintView.visible(document))
        assertEquals("Akten.pdf", document.documentName)
        assertEquals("Bitte analysieren", visible.question)
        assertEquals("Korrektes Dokument", visible.draftGoal)
        assertEquals(50, visible.progressPercent)
        assertEquals(listOf("Entwurf öffnen"), visible.buttons)
    }

    @Test
    fun unrelatedMemoryLearnedCannotCreateDocumentWorld() {
        val scenes = mutableListOf<JarvisActionScenes.FuiProfile>()
        val payloads = mutableListOf<HollywoodFuiLiveData?>()
        val orchestrator = Airbus2100CockpitOrchestrator(
            schedule = { _, _ -> }, cancel = {},
            onSceneProfile = { scenes += it.fuiProfile },
            onLiveData = { payloads += it },
            onTelemetry = {}, onHoloCue = {}, onPostMission = {}
        )
        orchestrator.render(CockpitAnimationEvent.CommandFlyIn("speech", 0L, "Rechne"))
        val beforeScenes = scenes.size
        val beforePayloads = payloads.size
        orchestrator.render(CockpitAnimationEvent.DocumentLearned(
            MEMORY_TURN, 1L, "fremdes-archiv", "pdf", 10
        ))
        assertEquals(beforeScenes, scenes.size)
        assertEquals(beforePayloads, payloads.size)
        assertTrue(payloads.none { it is HollywoodFuiLiveData.Document })
    }

    @Test
    fun newDocumentClearsPreviousDraftAndAbortedTurnRejectsLateCallbacks() {
        val payloads = mutableListOf<HollywoodFuiLiveData?>()
        val orchestrator = Airbus2100CockpitOrchestrator(
            schedule = { _, _ -> }, cancel = {},
            onSceneProfile = {}, onLiveData = { payloads += it },
            onTelemetry = {}, onHoloCue = {}, onPostMission = {}
        )
        orchestrator.render(CockpitAnimationEvent.CommandFlyIn("speech", 0L, "Erste PDF"))
        orchestrator.render(CockpitAnimationEvent.DocumentQuestion(
            "document-1", 1L, "alt.pdf", "application/pdf", "Alte Frage"
        ))
        orchestrator.render(DocumentDraftReadyEvent(
            "document-1", 2L, "Ziel: Alter Entwurf", actions = setOf(DocumentDraftAction.OPEN)
        ))
        orchestrator.render(CockpitAnimationEvent.DocumentQuestion(
            "document-2", 3L, "neu.pdf", "application/pdf", "Neue Frage"
        ))
        val latest = requireNotNull(DocumentBlueprintView.visible(
            payloads.last() as HollywoodFuiLiveData.Document
        ))
        assertEquals("Neue Frage", latest.question)
        assertNull(latest.draftGoal)
        assertTrue(latest.buttons.isEmpty())

        orchestrator.render(CockpitAnimationEvent.TurnEnded("speech", 4L, delivered = false))
        assertNull(payloads.last())
        val count = payloads.size
        orchestrator.render(DocumentDraftReadyEvent(
            "document-2", 5L, "Ziel: Zu spät", actions = setOf(DocumentDraftAction.OPEN)
        ))
        assertEquals(count, payloads.size)
    }

    @Test
    fun thinkOpensLocalReasoningOnlyWhileNoStrongerWorldIsShowing() {
        val scenes = mutableListOf<JarvisActionScenes.FuiProfile>()
        val orchestrator = Airbus2100CockpitOrchestrator(
            schedule = { _, _ -> },
            cancel = {},
            onSceneProfile = { scenes += it.fuiProfile },
            onLiveData = {},
            onTelemetry = {},
            onHoloCue = {},
            onPostMission = {}
        )
        orchestrator.render(CockpitAnimationEvent.CommandFlyIn("t", 0L, "denk nach"))
        orchestrator.render(CockpitAnimationEvent.ActionStarted("t", 1L, CockpitAction.THINK, "lokal"))
        assertEquals(JarvisActionScenes.FuiProfile.GENERIC, scenes.last())
        orchestrator.render(CockpitAnimationEvent.ActionStarted("t", 2L, CockpitAction.DEVELOP, "build"))
        assertEquals(JarvisActionScenes.FuiProfile.CODE_FORGE, scenes.last())
        orchestrator.render(CockpitAnimationEvent.ActionStarted("t", 3L, CockpitAction.THINK, "weiter"))
        assertEquals(JarvisActionScenes.FuiProfile.CODE_FORGE, scenes.last())
    }


    @Test
    fun openedDocumentCannotBeHijackedByUnscopedOrForgedMemoryLearning() {
        val scenes = mutableListOf<JarvisActionScenes.FuiProfile>()
        val payloads = mutableListOf<HollywoodFuiLiveData?>()
        val orchestrator = Airbus2100CockpitOrchestrator(
            schedule = { _, _ -> }, cancel = {},
            onSceneProfile = { scenes += it.fuiProfile },
            onLiveData = { payloads += it },
            onTelemetry = {}, onHoloCue = {}, onPostMission = {}
        )
        orchestrator.render(CockpitAnimationEvent.CommandFlyIn("verbal", 0L, "Bitte PDF lesen"))
        orchestrator.render(CockpitAnimationEvent.DocumentQuestion(
            "document-1", 1L, "Akten.pdf", "application/pdf", "Lies diese PDF", pageCount = 4
        ))
        val before = requireNotNull(orchestrator.documentSnapshot())
        val beforeScenes = scenes.size
        val beforePayloads = payloads.size
        assertNull(before.entriesAdded)

        // The real memory adapter emits MEMORY_TURN, not the selected PDF's episode.
        orchestrator.render(CockpitAnimationEvent.DocumentLearned(
            MEMORY_TURN, 2L, "unrelated-ledger-id", "application/pdf", entriesAdded = 15
        ))
        // Even a coincidentally named or turn-tagged event cannot prove ledger-to-intake identity.
        orchestrator.render(CockpitAnimationEvent.DocumentLearned(
            "document-1", 3L, "Akten.pdf", "application/pdf", entriesAdded = 99
        ))
        assertEquals(beforeScenes, scenes.size)
        assertEquals(beforePayloads, payloads.size)
        assertEquals(before, orchestrator.documentSnapshot())

        // Only a properly correlated, actual document pipeline event updates this scene.
        orchestrator.render(DocumentPipelineStepEvent(
            "document-1", 4L, DocumentPipelineStepEvent.KEY_CAPTURE,
            "Inhalte erfassen", StepState.DONE
        ))
        val after = requireNotNull(orchestrator.documentSnapshot())
        assertNull(after.entriesAdded)
        assertTrue(after.steps.any { it.key == DocumentPipelineStepEvent.KEY_CAPTURE && it.state == StepState.DONE })
        orchestrator.close()
    }

}
