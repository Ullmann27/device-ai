package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import at.heinz.deviceai.cockpit.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Trusted fixtures; no animation repair claim. */
class OmegaContinuousScenarioTest {
    private val session = "omega-conversation"
    private val request = "Du, schau mal die Animation von vorher. Die ist noch nicht so, wie ich das haben wollte. " +
        "Die andere war besser. Weißt eh welche. Schau dir das nochmal an und mach es besser. " +
        "Aber lass die restlichen Sachen so, wie wir es besprochen haben."

    private fun seed() = WorkingTaskSeed(
        taskId = "animation-review", revision = 1L,
        goal = "Animation verbessern", goalSourceId = "owner-goal-1",
        constraints = listOf(
            WorkingTaskConstraint("avatar", "Originalavatar beibehalten", "owner-constraint-1"),
            WorkingTaskConstraint("orb", "Continuum-Kugel beibehalten", "owner-constraint-2")),
        references = listOf(
            WorkingTaskReference("animation-a", "Animation A", "source-a-v1", setOf("Animation A")),
            WorkingTaskReference("animation-b", "Animation B", "source-b-v1", setOf("Animation B"))),
        selectedReferenceId = "animation-b")

    @Test fun ownerUtteranceFlowsThroughContextReceiptMemoryAndExistingCockpit() {
        val context = LocalConversationContext(workingTaskResearchEnabled = true)
        assertTrue(context.bindWorkingTask(session, seed()))
        context.inspectRequest(session, request)
        val first = context.resolveWorkingTask(session, request)
        val wasClarification = first is WorkingTaskDecision.Clarify
        val decision = if (wasClarification) {
            val clarification = "Ich meine Animation A"
            context.inspectRequest(session, clarification)
            context.resolveWorkingTask(session, clarification)
        } else first
        assertTrue(decision is WorkingTaskDecision.Resolved)
        val plan = (decision as WorkingTaskDecision.Resolved).plan
        assertEquals("animation-a", plan.reference.id)
        assertEquals("source-a-v1", plan.reference.sourceRevision)
        assertEquals("Animation verbessern", plan.goal)
        assertEquals(seed().constraints, plan.constraints)
        assertTrue(context.isWorkingTaskPlanCurrent(session, plan))
        assertEquals(if (wasClarification) "Ich meine Animation A" else request, plan.originalRequest)

        val dir = Files.createTempDirectory("omega-continuous-fixture").toFile()
        try {
            val episodes = InMemoryExperienceStore()
            val receipts = FileToolExecutionReceiptStore(File(dir, "receipts"))
            val events = mutableListOf<CockpitAnimationEvent>()
            val bus = CockpitAnimationBus().also { it.subscribe { event -> events += event } }
            val triggers = CockpitAnimationTriggers(bus)
            var backendCalls = 0
            var deterministicPlanCalls = 0
            val proposal = ToolProposal("find_files", mapOf("query" to "${plan.reference.id}.json",
                "roots" to listOf("/sdcard/Download"), "limit" to 1))
            val inspectionPlan = ReadOnlyToolPlan(proposal)
            val turnId = "omega-continuous-inspect"
            triggers.commandAcknowledged(turnId, plan.originalRequest)
            assertTrue(events.none { it is CockpitAnimationEvent.ActionStarted })
            val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { call ->
                assertTrue(context.isWorkingTaskPlanCurrent(session, plan))
                assertEquals(proposal.arguments, call.arguments)
                backendCalls++
                "HOST_FIXTURE: ${plan.reference.label}, Stand ${plan.reference.sourceRevision}, " +
                    "als Datei gefunden. Nur Dateinachweis geprüft; Animation nicht geändert."
            }, receiptStore = receipts)
            val result = BoundedAgentOrchestrator(AgentModel { task, memories, reflection, observations ->
                deterministicPlanCalls++
                inspectionPlan.generate(task, memories, reflection, observations)
            }, { emptyList() }, gate, inspectionPlan, episodes,
                callIdPrefix = turnId, limits = AgentLimits(maxToolCalls = 1, maxRetries = 0),
                onToolStarted = { callId, name ->
                    assertEquals(ToolReceiptState.DISPATCH_COMMITTED, receipts.get(callId)?.state)
                    triggers.toolStarted(turnId, callId, name)
                }).run(plan.originalRequest)
            val proof = result.taskEvidence!!
            assertEquals(1, backendCalls)
            assertEquals(2, deterministicPlanCalls)
            assertTrue(proof.outcomeVerified)
            assertEquals(sha256Hex(plan.originalRequest), proof.requestSha256)
            val call = proof.calls.single()
            assertEquals(receipts.get(call.callId)!!.outputSha256, call.outputSha256)
            assertTrue(call.freshOutput)
            assertEquals(1, episodes.all().size)
            assertTrue(ExperienceCompiler().compile(episodes.all()).isEmpty())
            assertEquals(result.experience!!.id, proof.learningCandidateExperienceId)
            assertTrue(episodes.all().single().resultSummary.contains("Animation nicht geändert"))
            triggers.toolFinished(turnId, call.callId, call.toolName, true)
            triggers.turnEnded(turnId, proof.outcomeVerified)
            val action = events.filterIsInstance<CockpitAnimationEvent.ActionStarted>().single()
            assertEquals(CockpitAction.TOOL, action.action)
            assertTrue(events.filterIsInstance<CockpitAnimationEvent.TurnEnded>().single().delivered)
            assertTrue(result.response.contains("Animation nicht geändert"))
            assertTrue(context.isWorkingTaskPlanCurrent(session, plan))
            writeEvidence("continuous", "HOST_FIXTURE\ninitialDecision=${first.javaClass.simpleName}\n" +
                "target=${plan.reference.id}\nsource=${plan.reference.sourceRevision}\n" +
                "constraints=${plan.constraints.size}\nbackendCalls=$backendCalls\n" +
                "deterministicPlanCallbacks=$deterministicPlanCalls\nmodelInferenceCalls=0\n" +
                "animationRepair=NOT_RUN\nFold7=NOT_RUN\nresponse=${result.response}\n$proof\n" +
                events.joinToString("\n") + "\n")
        } finally { dir.deleteRecursively() }
    }

    @Test fun missingReferenceClarifiesWithoutCallingToolsOrCreatingWorkEvidence() {
        val context = LocalConversationContext(workingTaskResearchEnabled = true)
        assertTrue(context.bindWorkingTask(session, seed().copy(selectedReferenceId = null)))
        context.inspectRequest(session, request)
        val decision = context.resolveWorkingTask(session, request)
        assertTrue(decision is WorkingTaskDecision.Clarify)
        val bus = CockpitAnimationBus()
        val events = mutableListOf<CockpitAnimationEvent>()
        bus.subscribe { events += it }
        val triggers = CockpitAnimationTriggers(bus)
        triggers.commandAcknowledged("omega-clarify", request)
        triggers.turnEnded("omega-clarify", false)
        assertTrue(events.none { it is CockpitAnimationEvent.ActionStarted })
        // No plan, gate invocation or episode.
        val episodes = InMemoryExperienceStore()
        assertTrue(episodes.all().isEmpty())
        writeEvidence("continuous-clarification", "HOST_FIXTURE\n" +
            "decision=CLARIFY\ntoolDispatches=0\nverifiedEpisodes=0\nmodelInferenceCalls=0\n")
    }

    private fun writeEvidence(name: String, text: String) {
        System.getProperty("omega.scenario.output")?.let { path ->
            File(path).mkdirs()
            File(path, "$name.txt").writeText(text)
        }
    }
}
