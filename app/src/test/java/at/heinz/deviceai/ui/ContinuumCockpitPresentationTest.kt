package at.heinz.deviceai.ui

import at.heinz.deviceai.chief.ChiefWorkerPool
import at.heinz.deviceai.chief.TurnPhase
import at.heinz.deviceai.chief.WorkerBrief
import at.heinz.deviceai.chief.WorkerJob
import at.heinz.deviceai.chief.WorkerReport
import at.heinz.deviceai.chief.WorkerStatus
import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** View-state of the landscape cockpit: real sources map 1:1, missing sources say "nicht verfügbar". */
class ContinuumCockpitPresentationTest {
    private val na = ContinuumCockpitPresentation.NOT_AVAILABLE

    @Test fun missingDeviceSourcesAreNotAvailableNeverZero() {
        val rows = ContinuumCockpitPresentation.systemRows(ContinuumDashboardState())
        assertEquals(listOf("CPU (App)", "RAM", "Speicher", "Akku", "Akku-Temp."), rows.map { it.label })
        assertTrue(rows.all { it.value == na && it.fraction == null })
    }

    @Test fun measuredDeviceValuesAreShownExactly() {
        val s = ContinuumDashboardState(system = ContinuumDashboardState.SystemStatus(
            appCpuPercent = 7, memoryPercent = 61, storagePercent = 44, batteryPercent = 83, temperatureC = 31.5f))
        val rows = ContinuumCockpitPresentation.systemRows(s).associateBy { it.label }
        assertEquals("7 %", rows.getValue("CPU (App)").value)
        assertEquals(.61f, rows.getValue("RAM").fraction!!, 1e-4f)
        assertEquals("31,5 °C", rows.getValue("Akku-Temp.").value)
        assertNull(ContinuumCockpitPresentation.batteryTemperature(Int.MIN_VALUE))
        assertNull("implausible sensor value", ContinuumCockpitPresentation.batteryTemperature(1_500))
        assertEquals(31.5f, ContinuumCockpitPresentation.batteryTemperature(315)!!, 1e-4f)
    }

    @Test fun turnPhaseDrivesBrainLabelAndRail() {
        assertEquals("Noch kein Auftrag", ContinuumCockpitPresentation.phaseLabel(null))
        assertEquals(-1, ContinuumCockpitPresentation.phaseIndex(null))
        assertEquals("Arbeitet", ContinuumCockpitPresentation.phaseLabel(TurnPhase.WORKING))
        assertEquals(2, ContinuumCockpitPresentation.phaseIndex(TurnPhase.ENDED))
        val brain = ContinuumCockpitPresentation.brainRows(ContinuumDashboardState()).associateBy { it.label }
        assertEquals("Offline", brain.getValue("Zustand").value)
        assertEquals(na, brain.getValue("Antwortzeit").value)
    }

    @Test fun agentsComeFromTheRealPoolWithDeadlineShareNotFakeProgress() {
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        val pool = ChiefWorkerPool(1, 2) { WorkerJob { started.countDown(); release.await(5, TimeUnit.SECONDS); "ok" } }
        try {
            assertEquals("Agenten-Pool", ContinuumCockpitPresentation.agentRows(CockpitStatus()).single().label)
            assertTrue(pool.submit(WorkerBrief("w1", "Teamleiterstatus lesen", emptySet(), 60_000)) {})
            assertTrue(pool.submit(WorkerBrief("w2", "Zweiter Auftrag", emptySet(), 60_000)) {})
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val snap = pool.snapshot()
            val status = CockpitStatus(workers = snap, workerCapacity = pool.capacity)
            assertEquals("Aktive Agenten (1 / 3)", ContinuumCockpitPresentation.agentsTitle(status))
            val rows = ContinuumCockpitPresentation.agentRows(status)
            assertEquals(listOf("Teamleiterstatus lesen", "Zweiter Auftrag"), rows.map { it.label })
            assertTrue(rows[0].value.startsWith("läuft") && rows[0].value.contains("Frist"))
            assertTrue(rows[0].fraction!! in 0f..1f)
            assertEquals("wartet", rows[1].value)
            assertNull("A queued worker has no bar", rows[1].fraction)
        } finally { release.countDown(); pool.awaitIdle(5_000); pool.shutdown() }
        assertTrue(pool.snapshot().isEmpty())
        val done = CockpitStatus(workers = emptyList(), lastReport = WorkerReport("status-1", WorkerStatus.TIMED_OUT, "", 15_000))
        assertEquals("Frist überschritten · 15 s", ContinuumCockpitPresentation.agentRows(done).single().value)
    }

    @Test fun processesAndDialogShowRealStepsOrHonestEmptyState() {
        assertEquals("Keine laufenden Prozesse", ContinuumCockpitPresentation.processRows(CockpitStatus()).single().label)
        // Open work from the journal stays visible while nothing runs – as open, never as running (owner brief 06.11.).
        val open = ContinuumCockpitPresentation.processRows(CockpitStatus(openWork = listOf("Bericht.pdf: Entwurf neu starten")))
        assertEquals("Offen · Bericht.pdf: Entwurf neu starten", open.single().label)
        assertEquals("offen", open.single().value)
        assertTrue(open.none { it.state == ContinuumCockpitPresentation.Tone.ACTIVE })
        val steps = (1..6).map { ProcessStep("s$it", "Schritt $it", if (it == 6) StepState.WAITING else StepState.DONE) }
        val rows = ContinuumCockpitPresentation.processRows(CockpitStatus(steps = steps))
        assertEquals(4, rows.size)
        assertEquals("wartet auf Freigabe", rows.last().value)
        assertEquals("Noch kein Gespräch", ContinuumCockpitPresentation.dialogRows(CockpitStatus()).single().label)
        val dialog = ContinuumCockpitPresentation.dialogRows(CockpitStatus(dialog = listOf(
            CockpitStatus.DialogLine(true, "a"), CockpitStatus.DialogLine(true, "Wie spät?"),
            CockpitStatus.DialogLine(false, "Es ist 08:50\u0000"))))
        assertEquals(listOf("DU", "JARVIS"), dialog.map { it.label })
        assertFalse(dialog.last().value.contains('\u0000'))
    }
}

class ContinuumCockpitMemoryTest {
    @Test fun memoryFiguresComeFromTheLedgerOrSayNotAvailable() {
        val none = ContinuumCockpitPresentation.memoryRows(ContinuumDashboardState(), 0L)
        assertEquals(ContinuumCockpitPresentation.NOT_AVAILABLE, none.single().value)
        val stats = at.heinz.deviceai.learning.ContinuumMemoryStats(12, 3, java.time.Instant.ofEpochMilli(0L), 4, 2, 1, 0)
        val rows = ContinuumCockpitPresentation.memoryRows(ContinuumDashboardState(memory = stats), 5 * 60_000L)
        assertEquals("12 Einträge · +3 heute", rows[0].value)
        assertEquals("4 / 3", rows[1].value)
        assertEquals("vor 5 Min.", rows[2].value)
    }

    @Test fun ledgerIsReadAtMostOncePerIntervalAndFailuresAreNull() {
        var reads = 0
        val throttled = ThrottledRead(15_000L) { reads++; reads }
        assertEquals(1, throttled.get(0L)); assertEquals(1, throttled.get(14_999L)); assertEquals(2, throttled.get(15_000L))
        val broken = ThrottledRead<Int>(1L) { error("unlesbar") }
        assertNull(broken.get(0L))
        // A real (empty) ledger directory gives 0, never an invented number.
        val dir = java.nio.file.Files.createTempDirectory("cockpit-mem").toFile()
        try {
            val stats = at.heinz.deviceai.learning.ContinuumMemoryStatsReader(dir).read()
            assertEquals(0, stats.totalEntries); assertNull(stats.lastActivity)
        } finally { dir.deleteRecursively() }
    }
}

/** Security review #481 (2): CockpitStatus is filled live from the real TurnLifecycle / ChiefWorkerPool. */
class ContinuumCockpitLiveStatusTest {
    @Test fun realPhaseAndRealJobChangeTheCockpitState() {
        val bus = at.heinz.deviceai.cockpit.CockpitAnimationBus()
        val triggers = at.heinz.deviceai.cockpit.CockpitAnimationTriggers(bus) { 1L }
        val before = CockpitStatus.live(null, null, null, triggers, emptyList())
        assertEquals("noch nicht gestartet", ContinuumCockpitPresentation.agentRows(before).single().value)
        assertNull(before.turnPhase)

        val turn = at.heinz.deviceai.chief.TurnLifecycle("t1")
        turn.acknowledge(); triggers.commandAcknowledged("t1", "Status prüfen")
        val release = CountDownLatch(1); val started = CountDownLatch(1)
        val pool = ChiefWorkerPool(1, 1) { WorkerJob { started.countDown(); release.await(5, TimeUnit.SECONDS); "ok" } }
        try {
            assertTrue(pool.submit(WorkerBrief("w1", "Echter Auftrag", emptySet(), 60_000)) {})
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val live = CockpitStatus.live(turn, pool, null, triggers, emptyList())
            assertEquals(TurnPhase.ACKNOWLEDGED, live.turnPhase)
            assertEquals("Echter Auftrag", ContinuumCockpitPresentation.agentRows(live).single().label)
            assertTrue(live.steps.isNotEmpty())
            assertTrue(ContinuumCockpitPresentation.processRows(live).none { it.label == "Keine laufenden Prozesse" })
        } finally { release.countDown(); pool.awaitIdle(5_000); pool.shutdown() }
    }
}
