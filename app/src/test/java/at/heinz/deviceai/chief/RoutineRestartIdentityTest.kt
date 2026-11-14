package at.heinz.deviceai.chief

import at.heinz.deviceai.routines.AgentMailbox
import at.heinz.deviceai.routines.Routine
import at.heinz.deviceai.routines.RoutineBook
import at.heinz.deviceai.routines.RoutineTrigger
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue

class RoutineRestartIdentityTest {
    @Test fun recreatedDispatcherNeverReusesRunOrMessageIdentity() {
        val briefs = ConcurrentLinkedQueue<String>()
        val messages = mutableListOf<String>()
        repeat(2) {
            val book = RoutineBook()
            assertTrue(book.add(Routine("battery", "Prüfe den Akku", RoutineTrigger.OnEvent("app-start"),
                setOf("get_battery_info"))))
            val mailbox = AgentMailbox()
            val pool = ChiefWorkerPool(1, 0) { brief -> WorkerJob {
                briefs += brief.id
                "Akku: 50 %"
            } }
            try {
                val dispatcher = RoutineDispatcher(book, pool, mailbox, clock = { 10L })
                assertEquals(1, dispatcher.onEvent("app-start"))
                assertTrue(pool.awaitIdle(5_000))
                messages += mailbox.drain("chief").single().id
            } finally { pool.shutdown() }
        }
        assertEquals("Restart must not reuse durable tool-call prefixes", 2, briefs.toSet().size)
        assertEquals("Restart must not alias finished messages", 2, messages.toSet().size)
    }
}
