package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class AgentCheckpointStoreTest {
    @Test
    fun fileStoreRoundTripsAndRejectsCorruption() {
        val directory = createTempDirectory("device-ai-agent-checkpoint-").toFile()
        try {
            val store = FileAgentCheckpointStore(directory)
            val checkpoint = AgentCheckpoint(
                schemaVersion = 1,
                task = "resume safely",
                state = AgentState.PLAN,
                steps = 3,
                toolCalls = 0,
                retries = 0,
                auditTrail = listOf(
                    AgentAuditEvent(0, AgentState.OBSERVE, "observed"),
                    AgentAuditEvent(1, AgentState.RETRIEVE, "retrieved"),
                    AgentAuditEvent(2, AgentState.PLAN, "planned")
                )
            )

            store.save(checkpoint)
            assertEquals(checkpoint, store.load())

            val file = File(directory, "agent-checkpoint.bin")
            val bytes = file.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
            file.writeBytes(bytes)

            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                store.load()
            }

            store.clear()
            assertNull(store.load())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun safeCheckpointResumesAndToolBoundaryRequiresOperatorReview() {
        val directory = createTempDirectory("device-ai-agent-resume-").toFile()
        try {
            val store = FileAgentCheckpointStore(directory)
            val safe = AgentCheckpoint(
                schemaVersion = 1,
                task = "finish task",
                state = AgentState.PLAN,
                steps = 3,
                toolCalls = 0,
                retries = 0,
                auditTrail = listOf(
                    AgentAuditEvent(0, AgentState.OBSERVE, "observed"),
                    AgentAuditEvent(1, AgentState.RETRIEVE, "retrieved"),
                    AgentAuditEvent(2, AgentState.PLAN, "planned")
                )
            )
            store.save(safe)

            val orchestrator = BoundedAgentOrchestrator(
                model = AgentModel { _, _, _, _ -> AgentModelOutput("model", "done") },
                memories = { emptyList() },
                toolGate = ToolGate(ToolPolicy(), ToolRunner { error("No tool expected") }),
                evaluator = AgentEvaluator { response, _ -> Evaluation(response == "done", 1.0) },
                experienceStore = InMemoryExperienceStore(),
                checkpointStore = store
            )
            val resumed = orchestrator.resumeSaved()
            assertNotNull(resumed)
            assertEquals("done", resumed?.response)
            assertNull(resumed?.blocker)
            assertNull(store.load())

            val boundary = safe.copy(
                state = AgentState.GATED_EXECUTION,
                steps = 4,
                auditTrail = safe.auditTrail + AgentAuditEvent(3, AgentState.GATED_EXECUTION, "authorized")
            )
            store.save(boundary)

            val blocked = orchestrator.resumeSaved()
            assertNotNull(blocked?.blocker)
            assertTrue(blocked?.blocker?.contains("side effects", ignoreCase = true) == true)
            assertEquals(AgentState.GATED_EXECUTION, blocked?.state)
        } finally {
            directory.deleteRecursively()
        }
    }
}
