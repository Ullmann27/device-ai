package at.heinz.deviceai.chief

import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.cognitive.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class ChiefWorkerFollowupTest {
    private fun proposal() = AgentFollowupProposal.create("Prüfe den zweiten Text.", "text.analyze", emptySet(), "Offene Textfrage")
    private fun envelope() = JSONObject().put("schema", AgentFollowupEnvelope.SCHEMA).put("answer", "Ungeprüfte Antwort")
        .put("next", JSONObject().put("goal", proposal().goal).put("capability", "text.analyze")
            .put("tools", JSONArray()).put("reason", proposal().reason))

    @Test fun optionalEnvelopeIsStrictAndCannotImportAuthorityFields() {
        val raw = envelope().toString()
        assertEquals(proposal(), AgentFollowupEnvelope.parse(raw)?.proposal)
        assertEquals("Ungeprüfte Antwort", AgentFollowupEnvelope.parse(raw)?.answer)
        val rejected = listOf("Normale Antwort", raw + "\n{}", raw + " trailing",
            raw.replace("\"schema\":", "\"schema\":\"duplicate\",\"schema\":"),
            envelope().put("ownerApproved", true).toString(),
            envelope().apply { getJSONObject("next").put("grant", "model-claims-permission") }.toString(),
            envelope().apply { getJSONObject("next").put("tools", JSONArray().put("delete_file").put("delete_file")) }.toString(),
            envelope().apply { getJSONObject("next").put("goal", "x".repeat(1_801)) }.toString())
        rejected.forEach { assertNull(it, AgentFollowupEnvelope.parse(it)) }
    }

    @Test fun typedProposalDefensivelyCopiesScopeAndRejectsMalformedText() {
        val tools = linkedSetOf("get_battery_status")
        val p = AgentFollowupProposal.create("Prüfe Akku", "device.battery", tools, "Kontrollfrage")
        tools += "delete_file"
        assertEquals(setOf("get_battery_status"), p.requestedTools)
        assertThrows(UnsupportedOperationException::class.java) { (p.requestedTools as MutableSet).clear() }
        assertThrows(IllegalArgumentException::class.java) {
            AgentFollowupProposal.create("bad\uD800", "text.analyze", emptySet(), "Grund")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AgentFollowupProposal.create("Text", "text.analyze", emptySet(), "bad\u0000")
        }
    }

    @Test fun realAgentJobReturnsOneTypedReportWithoutStartingTheProposedTask() {
        val calls = AtomicInteger()
        val reports = CopyOnWriteArrayList<WorkerReport>()
        val outcomes = CopyOnWriteArrayList<AgentRunResult>()
        val pool = ChiefWorkerPool(1, 1) { brief -> ChiefJobs.agentJob(brief, ChiefJobDeps(
            model = AgentModel { _, _, _, _ -> calls.incrementAndGet(); AgentModelOutput("controlled", "Antwort", followup = proposal()) },
            runner = ToolRunner { error("No tool authorized") }, policy = ToolPolicy(maxCalls = 0), memory = null,
            evaluator = AgentEvaluator { _, _ -> Evaluation(true, 0.0, "FORM_ONLY") },
            experienceStore = InMemoryExperienceStore(), approvalProvider = AgentToolApprovalProvider { _, _ -> error("No approval") },
            limits = AgentLimits(maxToolCalls = 0, maxRetries = 0), onResult = { outcomes += it }
        )) }
        try {
            assertTrue(pool.submit(WorkerBrief("source", "Text prüfen", emptySet(), 5_000)) { reports += it })
            assertTrue(pool.awaitIdle(10_000))
            assertEquals(1, calls.get())
            assertEquals(proposal(), outcomes.single().followup)
            assertEquals(WorkerStatus.DONE, reports.single().status)
            assertEquals(proposal(), reports.single().followup)
            assertTrue(pool.active().isEmpty())
            assertFalse(outcomes.single().taskEvidence!!.outcomeVerified)
        } finally { pool.shutdown() }
    }

    @Test fun rejectedEvaluationDoesNotDeliverWorkerFollowup() {
        val reports = CopyOnWriteArrayList<WorkerReport>()
        val pool = ChiefWorkerPool(1, 1) { brief -> ChiefJobs.agentJob(brief, ChiefJobDeps(
            model = AgentModel { _, _, _, _ -> AgentModelOutput("controlled", "unbrauchbar", followup = proposal()) },
            runner = ToolRunner { error("No tool") }, policy = ToolPolicy(maxCalls = 0), memory = null,
            evaluator = AgentEvaluator { _, _ -> Evaluation(false, 0.0, "NOT_ACCEPTED") },
            experienceStore = InMemoryExperienceStore(), approvalProvider = AgentToolApprovalProvider { _, _ -> error("No approval") },
            limits = AgentLimits(maxToolCalls = 0, maxRetries = 0)
        )) }
        try {
            assertTrue(pool.submit(WorkerBrief("rejected", "Text prüfen", emptySet(), 5_000)) { reports += it })
            assertTrue(pool.awaitIdle(10_000))
            assertEquals(WorkerStatus.BLOCKED, reports.single().status)
            assertNull(reports.single().followup)
        } finally { pool.shutdown() }
    }

    @Test fun ephemeralScreenOutcomeCannotBecomeDurableFollowup() {
        val store = InMemoryExperienceStore()
        val dispatched = CopyOnWriteArrayList<String>()
        val result = BoundedAgentOrchestrator(
            model = AgentModel { _, _, _, observations ->
                if (observations.isEmpty()) AgentModelOutput("controlled", "", ToolProposal("read_app_screen",
                    mapOf("packageOrName" to "com.android.settings")))
                else AgentModelOutput("controlled", "PRIVATE_SCREEN_RESULT", followup = proposal())
            }, memories = { emptyList() }, toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
                dispatched += it.name; "PRIVATE_SCREEN_RESULT"
            }), evaluator = AgentEvaluator { _, _ -> Evaluation(true, 0.0, "FORM_ONLY") },
            approvalProvider = AgentToolApprovalProvider { _, _ -> AgentToolApproval(true) },
            experienceStore = store
        ).run("Prüfe diesen sichtbaren Bildschirm")
        assertNull(result.blocker)
        assertEquals(listOf("read_app_screen"), dispatched)
        assertNull(result.followup)
        assertTrue(store.all().isEmpty())
    }
}
