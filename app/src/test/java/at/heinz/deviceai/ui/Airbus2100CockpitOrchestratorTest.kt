package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.AlarmKind
import at.heinz.deviceai.cockpit.ChartKind
import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
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
        orchestrator.render(CockpitAnimationEvent.DocumentQuestion("t", 3L, "x.pdf", "application/pdf", "Was tun?"))
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
        assertEquals(1, document.pageCount ?: 1)
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
}
