package at.heinz.deviceai.chief

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Code56: KI-Team failures must name a fixed cause; free exception text never travels. */
class ChiefFailureCodeTest {
    @Test fun onlyFixedCodesAreExtracted() {
        assertEquals("BOT_TEAM_MODEL_CHANGED", ChiefFailureCode.of("BOT_TEAM_MODEL_CHANGED"))
        assertNull(ChiefFailureCode.of("kaputt"))
        assertNull(ChiefFailureCode.of("Passwort ABCDEF123 geheim"))
        assertEquals("LOCAL_MODEL_CONTEXT_UNVERIFIED_OR_FULL", ChiefFailureCode.fromSummary(
            "Fehlgeschlagen: IllegalStateException (LOCAL_MODEL_CONTEXT_UNVERIFIED_OR_FULL)"))
        assertNull(ChiefFailureCode.fromSummary("Fehlgeschlagen: IllegalStateException"))
    }

    @Test fun workerFailureSummaryCarriesTheCodeButNotFreeText() {
        val reports = CopyOnWriteArrayList<WorkerReport>()
        val pool = ChiefWorkerPool(2, 2) { brief ->
            when (brief.id) {
                "coded" -> WorkerJob { error("LOCAL_MODEL_CONTEXT_UNVERIFIED_OR_FULL") }
                else -> WorkerJob { error("privater Text 4711") }
            }
        }
        try {
            pool.submit(WorkerBrief("coded", "Text prüfen", emptySet(), 5_000)) { reports += it }
            pool.submit(WorkerBrief("free", "Text prüfen", emptySet(), 5_000)) { reports += it }
            assertTrue(pool.awaitIdle(5_000))
            val byId = reports.associateBy { it.id }
            assertEquals(WorkerStatus.FAILED, byId.getValue("coded").status)
            assertTrue(byId.getValue("coded").summary.endsWith("(LOCAL_MODEL_CONTEXT_UNVERIFIED_OR_FULL)"))
            assertFalse(byId.getValue("free").summary.contains("4711"))
            assertNull(ChiefFailureCode.fromSummary(byId.getValue("free").summary))
        } finally { pool.shutdown() }
    }
}
