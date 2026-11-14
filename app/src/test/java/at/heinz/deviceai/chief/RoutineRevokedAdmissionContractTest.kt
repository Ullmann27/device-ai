package at.heinz.deviceai.chief

import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.cognitive.AgentLimits
import at.heinz.deviceai.cognitive.InMemoryExperienceStore
import at.heinz.deviceai.cognitive.ReadOnlyToolPlan
import at.heinz.deviceai.routines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class RoutineRevokedAdmissionContractTest {
    @Test fun admissionCallbackRevocationPreventsLaterToolStart() {
        val root = Files.createTempDirectory("revoked-admission-").toFile()
        val calls = AtomicInteger()
        val book = RoutineBook(stateStore = FileRoutineStateStore(root))
        val routine = Routine("battery", "Pruefe Akku",
            RoutineTrigger.Every(RoutineBook.MIN_PERIOD_MILLIS, 0), setOf("get_battery_info"))
        assertTrue(book.addApproved(routine, RoutineOwnerApproval.confirmed(routine, 0)))
        val workers = ChiefWorkerPool(1, 0) { brief ->
            val plan = ReadOnlyToolPlan(ToolProposal("get_battery_info", emptyMap()))
            ChiefJobs.agentJob(brief, ChiefJobDeps(plan,
                ToolRunner { calls.incrementAndGet(); "Akku 50 %" },
                ToolPolicy(readOnlyEnabled = true, maxCalls = 1), null, plan,
                InMemoryExperienceStore(), UnattendedApproval,
                limits = AgentLimits(maxSteps = 24, maxToolCalls = 1, maxRetries = 0)))
        }
        try {
            val dispatcher = RoutineDispatcher(book, workers, AgentMailbox(), clock = { 10L },
                onAdmission = { assertEquals(listOf("battery"), book.removeAll()) })
            dispatcher.tick()
            assertTrue(workers.awaitIdle(5_000))
            assertTrue(book.registrations().isEmpty())
            assertEquals("Revocation completed before pool submission", 0, calls.get())
        } finally {
            workers.shutdown()
            root.deleteRecursively()
        }
    }
}
