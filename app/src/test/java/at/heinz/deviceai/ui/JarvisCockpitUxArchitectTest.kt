package at.heinz.deviceai.ui

import at.heinz.deviceai.chief.ChiefWorkerPool
import at.heinz.deviceai.chief.TurnPhase
import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisCockpitUxArchitectTest {
    private val foldInner = 884f to 760f
    private val foldOuter = 412f to 915f
    private val tablet = 1280f to 800f
    private val phone = 412f to 915f

    @Test fun toolDockReplacesTheLeftRailAndKeepsEveryRailTarget() {
        assertEquals(CockpitGlassButtons.buttons.map { it.target }, JarvisTiles.bottomNav.map { it.target })
        assertEquals(listOf("home", "chat", "memory", "research", "tasks", "web",
            "learning", "device"), JarvisTiles.bottomNav.map { it.target })
        assertEquals(listOf("logic", "agents", "system"), CockpitGlassButtons.secondaryTargets)
        assertTrue(at.heinz.deviceai.ContinuumWorkAreaNavigationSpec.moreEntries.map { it.target }
            .containsAll(CockpitGlassButtons.secondaryTargets))
        for (item in JarvisTiles.bottomNav) assertTrue(item.target in JarvisShellContent.routes)
        assertFalse("Dateien is not a real route", JarvisTiles.bottomNav.any { it.label == "Dateien" })
        assertFalse("PDF is not a real route", JarvisTiles.bottomNav.any { it.label.equals("PDF", true) })
    }

    @Test fun foldInnerAndTabletExposeTheDockWithoutARail() {
        for ((w, h) in listOf(foldInner, tablet, 692f to 700f)) {
            val shell = JarvisShellLayout.create(w, h)
            assertNull("$w×$h left rail", shell[Slot.NAV_RAIL])
            val dock = requireNotNull(shell[Slot.BOTTOM_NAV])
            assertTrue("$w×$h dock under work panels", dock.top >= shell[Slot.WORK_DOCUMENT]!!.bottom - .01f)
            assertTrue("$w×$h status under dock", shell[Slot.STATUS]!!.top >= dock.bottom - .01f)
            val hits = JarvisTiles.bottomNavItemBoxes(dock).mapIndexed { i, box ->
                JarvisShellContent.hitTarget(shell, JarvisTiles.reference, box.centerX, box.centerY)
            }
            assertEquals(JarvisTiles.bottomNav.map { it.target }, hits)
        }
    }

    @Test fun foldOuterAndPhoneReachEveryRailTargetThroughTheCinemaDock() {
        for ((w, h, label) in listOf(Triple(foldOuter.first, foldOuter.second, "outer"),
            Triple(phone.first, phone.second, "phone"))) {
            val shell = JarvisShellLayout.create(w, h)
            assertNull("$label rail", shell[Slot.NAV_RAIL])
            assertFalse("$label must not keep the overflowing 10-tile reflow", shell.hasFeatureTiles)
            val dock = requireNotNull(shell[Slot.BOTTOM_NAV])
            assertEquals(JarvisTiles.bottomNav.map { it.target },
                JarvisTiles.bottomNavItemBoxes(dock).map { box ->
                    JarvisShellContent.hitTarget(shell, JarvisTiles.reference, box.centerX, box.centerY)
                })
            assertTrue("home remains a routed destination", "home" in JarvisShellContent.routes)
        }
        val withBar = JarvisShellLayout.create(412f, 915f, includeBottomNavigation = true)
        val dock = requireNotNull(withBar[Slot.BOTTOM_NAV])
        assertEquals(JarvisTiles.bottomNav.map { it.target },
            JarvisTiles.bottomNavItemBoxes(dock).map { box ->
                JarvisShellContent.hitTarget(withBar, JarvisTiles.reference, box.centerX, box.centerY)
            })
    }

    @Test fun telemetryNeverInventsGpuOrMissingReadings() {
        val empty = JarvisCockpitPanels.telemetryTiles(ContinuumDashboardState())
        assertEquals(listOf("cpu", "ram", "gpu", "battery", "temp", "perf"), empty.map { it.id })
        assertTrue(empty.all { it.value == JarvisCockpitPanels.NONE })
        assertNull(empty.first { it.id == "gpu" }.fraction)
        val live = JarvisCockpitPanels.telemetryTiles(ContinuumDashboardState(
            system = ContinuumDashboardState.SystemStatus(
                appCpuPercent = 18, memoryPercent = 68, batteryPercent = 95,
                temperatureC = 34f, thermalStatus = 0)))
        assertEquals("18 %", live.first { it.id == "cpu" }.value)
        assertEquals("68 %", live.first { it.id == "ram" }.value)
        assertEquals(JarvisCockpitPanels.NONE, live.first { it.id == "gpu" }.value)
        assertEquals("95 %", live.first { it.id == "battery" }.value)
        assertEquals("34 °C", live.first { it.id == "temp" }.value)
        assertEquals("Stabil", live.first { it.id == "perf" }.value)
        assertEquals(JarvisCockpitPanels.NONE, JarvisCockpitPanels.performanceLabel(null))
        assertEquals("Gedrosselt", JarvisCockpitPanels.performanceLabel(4))
    }

    @Test fun statusLineDrawsEveryRealSegmentAndFixesBatteryVersusTemperature() {
        val empty = JarvisCockpitPanels.statusSegments(ContinuumDashboardState())
        assertEquals(listOf(
            JarvisCockpitPanels.Seg.SYSTEM, JarvisCockpitPanels.Seg.DEVICE, JarvisCockpitPanels.Seg.LOCAL_AI,
            JarvisCockpitPanels.Seg.SYNC, JarvisCockpitPanels.Seg.PRIVACY, JarvisCockpitPanels.Seg.LOAD),
            empty.map { it.kind })
        val load = empty.last().text
        assertTrue(load.contains("App-CPU —"))
        assertTrue(load.contains("RAM —"))
        assertTrue("battery and temperature stay separate: $load", load.contains("Akku — · —"))
        val measured = JarvisCockpitPanels.statusSegments(ContinuumDashboardState(
            system = ContinuumDashboardState.SystemStatus(
                appCpuPercent = 18, memoryPercent = 68, batteryPercent = 95, temperatureC = 34f,
                networkOnline = true, openIssues = 0),
            model = ContinuumDashboardState.ModelStatus(stateLabel = "Bereit", online = true),
            storage = ContinuumDashboardState.StorageStatus(syncLabel = "Bereit"),
            presentation = ContinuumDashboardState.PresentationStatus(localOnly = true)))
        assertTrue(measured.first { it.kind == JarvisCockpitPanels.Seg.DEVICE }.text.contains("online"))
        assertTrue(measured.first { it.kind == JarvisCockpitPanels.Seg.PRIVACY }.text.contains("Sicherheit: Aktiv"))
        assertTrue(measured.last().text.contains("Akku 95 %"))
        assertTrue(measured.last().text.contains("34 °C"))
        val painter = JarvisPresentationRecordingPainter()
        val shell = JarvisShellLayout.create(884f, 760f)
        JarvisCockpitGridContent.draw(painter, Slot.STATUS, shell[Slot.STATUS]!!, shell.grid,
            ContinuumDashboardState(), 0L, shell)
        assertTrue(painter.texts.any { it.startsWith("Systemstatus:") })
        assertTrue(painter.texts.any { it.startsWith("Netz:") })
        assertTrue(painter.texts.any { it.startsWith("Lokale KI:") })
        assertTrue(painter.texts.any { it.startsWith("Continuum-Sync:") })
        assertTrue(painter.texts.any { it.startsWith("Sicherheit:") })
        assertTrue(painter.texts.any { it.contains("CPU") })
        assertTrue(painter.texts.any { it == "GPU" })
    }

    @Test fun activeTaskBarAppearsOnlyForARealRunningTask() {
        val idle = ContinuumDashboardState()
        assertFalse(JarvisCockpitPanels.isOwnerTaskRunning(idle))
        val idlePainter = JarvisPresentationRecordingPainter()
        val shell = JarvisShellLayout.create(884f, 760f)
        JarvisCockpitGridContent.draw(idlePainter, Slot.INPUT, shell[Slot.INPUT]!!, shell.grid, idle, 0L, shell)
        assertFalse(idlePainter.texts.any { it.startsWith("Aktiver Auftrag") })

        val running = ContinuumDashboardState(
            cockpit = CockpitStatus(
                turnPhase = TurnPhase.WORKING,
                dialog = listOf(CockpitStatus.DialogLine(true, "Lies die PDF und erstelle eine Zusammenfassung.")),
                steps = listOf(ProcessStep("open", "Dokument öffnen", StepState.DONE),
                    ProcessStep("read", "Inhalt erfassen", StepState.ACTIVE)),
                workers = listOf(ChiefWorkerPool.WorkerSnapshot("w1", "Zusammenfassen", true, false, 84_000L, 120_000L))))
        assertTrue(JarvisCockpitPanels.isOwnerTaskRunning(running))
        assertEquals("Lies die PDF und erstelle eine Zusammenfassung.", JarvisCockpitPanels.activeTaskText(running))
        assertEquals(1 to 2, JarvisCockpitPanels.activeTaskProgress(running))
        assertEquals("00:01:24", JarvisCockpitPanels.formatElapsed(JarvisCockpitPanels.activeTaskElapsedMillis(running)))
        val painter = JarvisPresentationRecordingPainter()
        JarvisCockpitGridContent.draw(painter, Slot.INPUT, shell[Slot.INPUT]!!, shell.grid, running, 0L, shell)
        assertTrue(painter.texts.any { it == "Aktiver Auftrag" })
        assertTrue(painter.texts.any { it.contains("Lies die PDF") })
        assertTrue(painter.texts.any { it == "00:01:24" })
    }

    @Test fun stopTargetIsBoundToTheExistingRightDialogControlOnlyWhileATaskRuns() {
        val shell = JarvisShellLayout.create(884f, 760f)
        val stop = shell.dialogControlBoxes().last()
        val idle = ContinuumDashboardState()
        assertFalse(JarvisCockpitPanels.isOwnerTaskRunning(idle))
        val running = ContinuumDashboardState(cockpit = CockpitStatus(turnPhase = TurnPhase.WORKING,
            dialog = listOf(CockpitStatus.DialogLine(true, "Analysiere die Datei."))))
        assertTrue(JarvisCockpitPanels.isOwnerTaskRunning(running))
        assertEquals(JarvisCockpitPanels.STOP_TARGET, "stop")
        assertTrue("stop box stays on the existing send/STOP control", stop.width >= 48f - .01f && stop.height >= 48f - .01f)
        assertTrue(stop.right <= shell[Slot.INPUT]!!.right + .01f)
    }

    @Test fun liveCardWaveformStaysFlatWithoutMeasuredAudio() {
        assertTrue(JarvisCockpitGridContent.liveWaveformHeights(null).all { it == 0f })
        val measured = JarvisCockpitGridContent.liveWaveformHeights(.5f)
        assertTrue(measured.all { it > 0f })
        assertTrue(measured.maxOrNull()!! <= .5f + .001f)
        val silent = ContinuumDashboardState(
            presentation = ContinuumDashboardState.PresentationStatus(audioLevel = null),
            cockpit = CockpitStatus(dialog = listOf(CockpitStatus.DialogLine(true, "Auftrag angenommen."))))
        val loud = silent.copy(presentation = ContinuumDashboardState.PresentationStatus(speaking = true, audioLevel = .8f))
        val painter = JarvisPresentationRecordingPainter()
        val shell = JarvisShellLayout.create(884f, 760f)
        JarvisCockpitGridContent.draw(painter, Slot.DIALOG, shell[Slot.DIALOG]!!, shell.grid, loud, 0L, shell)
        assertTrue(painter.texts.any { it == "JARVIS" })
        assertTrue(painter.texts.any { it == "Status" })
        assertFalse("Avatar badge cannot imply a live app feed", painter.texts.any { it == "Live" })
        assertTrue(painter.texts.any { it.contains("Auftrag angenommen") })
        assertEquals("Auftrag angenommen.", JarvisCockpitGridContent.liveTaskText(silent))
        assertNull(silent.presentation.measuredAudio)
        assertEquals(.8f, loud.presentation.measuredAudio)
    }
}
