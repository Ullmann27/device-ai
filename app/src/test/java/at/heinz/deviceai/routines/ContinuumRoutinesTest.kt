package at.heinz.deviceai.routines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chief-Muster 6 (Routinen: Zeitplan/Auslöser, melden nur Neues) und 7 (Agent-Nachrichten: dringend weckt, Info wartet). */
class ContinuumRoutinesTest {
    @Test fun scheduledRoutineIsDueOncePerPeriodAndNotBeforeItsStart() {
        val book = RoutineBook()
        assertTrue(book.add(Routine("akku", "Akku prüfen", RoutineTrigger.Every(900_000, 1_000), setOf("get_battery_info"))))
        assertEquals(emptyList<String>(), book.due(999).map { it.id })
        assertEquals(listOf("akku"), book.due(1_000).map { it.id })
        book.report("akku", "80 %", 1_000)
        assertEquals(emptyList<String>(), book.due(30_000).map { it.id })
        assertEquals(listOf("akku"), book.due(901_000).map { it.id })
    }

    @Test fun reportsOnlyWhenSomethingIsNew() {
        val book = RoutineBook(); book.add(Routine("r", "x", RoutineTrigger.Every(900_000, 0)))
        assertEquals(listOf("r"), book.due(0).map { it.id })
        assertTrue(book.report("r", "Neu: 2 Downloads", 0) is RoutineOutcome.News)
        assertEquals(listOf("r"), book.due(900_000).map { it.id })
        assertTrue(book.report("r", "  Neu: 2 Downloads ", 900_000) is RoutineOutcome.Unchanged)
        assertEquals(listOf("r"), book.due(1_800_000).map { it.id })
        assertTrue(book.report("r", "Neu: 3 Downloads", 1_800_000) is RoutineOutcome.News)
    }

    @Test fun eventTriggerAndBoundsAndInvalidRoutines() {
        val book = RoutineBook(maxRoutines = 2)
        assertTrue(book.add(Routine("lade", "Laden melden", RoutineTrigger.OnEvent("charging"))))
        assertFalse(book.add(Routine("lade", "doppelt", RoutineTrigger.OnEvent("charging"))))
        assertFalse(book.add(Routine("bad", "zu oft", RoutineTrigger.Every(1_000, 0))))   // < 15 min Android minimum
        assertFalse(book.add(Routine("", "leer", RoutineTrigger.OnEvent("x"))))
        assertTrue(book.add(Routine("b", "zwei", RoutineTrigger.OnEvent("boot"))))
        assertFalse(book.add(Routine("c", "drei", RoutineTrigger.OnEvent("boot"))))
        assertEquals(listOf("lade"), book.triggered("charging").map { it.id })
        assertEquals(emptyList<String>(), book.due(Long.MAX_VALUE / 2).map { it.id })
        assertTrue(book.remove("lade")); assertEquals(emptyList<String>(), book.triggered("charging"))
    }

    @Test fun urgentMessageWakesInfoWaitsAndDuplicatesAreDropped() {
        val woken = mutableListOf<String>()
        val box = AgentMailbox(maxPerAgent = 2) { woken += it.id }
        assertTrue(box.send(AgentMessage("1", "gedaechtnis", "haupt", AgentPriority.INFO, "Index fertig")))
        assertEquals(emptyList<String>(), woken)
        assertTrue(box.send(AgentMessage("2", "reparateur", "haupt", AgentPriority.URGENT, "RAM kritisch")))
        assertEquals(listOf("2"), woken)
        assertFalse(box.send(AgentMessage("2", "reparateur", "haupt", AgentPriority.URGENT, "RAM kritisch")))
        assertFalse(box.send(AgentMessage("3", "x", "haupt", AgentPriority.INFO, "voll")))     // bounded
        assertEquals(listOf("2", "1"), box.drain("haupt").map { it.id })                        // urgent first
        assertEquals(0, box.pending("haupt"))
    }

    @Test fun aFailingWakeHookNeverLosesTheMessage() {
        val box = AgentMailbox { error("wake failed") }
        assertTrue(box.send(AgentMessage("1", "a", "b", AgentPriority.URGENT, "x")))
        assertEquals(1, box.pending("b"))
        assertFalse(box.send(AgentMessage("2", "a", "b", AgentPriority.INFO, "")))
    }
    @Test fun pollingAgainDoesNotDuplicateAnInFlightScheduledRun() {
        val book = RoutineBook()
        assertTrue(book.add(Routine("safe", "Akku lesen", RoutineTrigger.Every(900_000, 0))))
        assertEquals(listOf("safe"), book.due(0).map { it.id })
        assertEquals(emptyList<String>(), book.due(1).map { it.id })
        assertEquals(emptyList<String>(), book.due(900_000).map { it.id })
        assertTrue(book.release("safe"))
        assertFalse(book.release("safe"))
        assertEquals(listOf("safe"), book.due(900_001).map { it.id })
        assertTrue(book.report("safe", "neu", 900_001) is RoutineOutcome.News)
        assertEquals(emptyList<String>(), book.due(900_002).map { it.id })
        assertEquals(listOf("safe"), book.due(1_800_001).map { it.id })
    }

    @Test fun eventRoutineCannotBeDispatchedAgainBeforeReportOrExplicitRelease() {
        val book = RoutineBook()
        assertTrue(book.add(Routine("app", "App Status", RoutineTrigger.OnEvent("change"))))
        assertEquals(listOf("app"), book.triggered("change").map { it.id })
        assertEquals(emptyList<String>(), book.triggered("change").map { it.id })
        assertTrue(book.report("app", "Status neu", 10) is RoutineOutcome.News)
        assertEquals(listOf("app"), book.triggered("change").map { it.id })
    }

    @Test fun mailboxBoundsRecipientCountTotalMessageCountAndUtf8Bytes() {
        val box = AgentMailbox(maxPerAgent = 2, maxRecipients = 2, maxQueuedMessages = 3,
            maxQueuedBytes = 4_096)
        fun msg(id: String, to: String, body: String = "x") =
            AgentMessage(id, "worker", to, AgentPriority.INFO, body)

        assertTrue(box.send(msg("1", "chief")))
        assertTrue(box.send(msg("2", "memory")))
        assertFalse("third recipient cannot create unbounded queues", box.send(msg("3", "random")))
        assertTrue(box.send(msg("3", "chief")))
        assertFalse("global message bound must hold even when another queue has room",
            box.send(msg("4", "memory")))
        assertEquals(2, box.drain("chief").size)
        assertTrue("drain frees global capacity", box.send(msg("4", "memory")))
        assertTrue("drain also frees recipient slots", box.send(msg("5", "new-recipient")))
        assertFalse("byte budget must be enforced", box.send(msg("6", "new-recipient", "ö".repeat(3_000))))
        assertEquals(1, box.pending("new-recipient"))
    }

    @Test fun routineReportWithoutClaimCannotBeMistakenForCompletedWork() {
        val book = RoutineBook()
        assertTrue(book.add(Routine("guard", "safe", RoutineTrigger.Every(900_000, 0))))
        assertTrue(book.report("guard", "gefälschtes Ergebnis", 0) is RoutineOutcome.Unchanged)
        assertEquals(listOf("guard"), book.due(0).map { it.id })
    }

}
