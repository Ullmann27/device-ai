package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import at.heinz.deviceai.cockpit.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Host fixtures; no model/device execution. */
class OmegaDownstreamScenarioTest {
    private val request = "Du, schau mal die Animation von vorher. Die ist noch nicht so, wie ich das haben wollte. " +
        "Die andere war besser. Weißt eh welche. Schau dir das nochmal an und mach es besser. " +
        "Aber lass die restlichen Sachen so, wie wir es besprochen haben."

    private class RunFixture(val dir: File) {
        val store = InMemoryExperienceStore()
        val receipts = FileToolExecutionReceiptStore(File(dir, "receipts"))
        val visualEvents = mutableListOf<CockpitAnimationEvent>()
        val cognitiveEvents = mutableListOf<AgentCognitiveEvent>()
        val bus = CockpitAnimationBus()
        val triggers = CockpitAnimationTriggers(bus)
        var backendCalls = 0
        var proposalCalls = 0
        var actionsAtBackend = -1
        var executingReceiptState: ToolReceiptState? = null
        init { bus.subscribe { synchronized(visualEvents) { visualEvents += it } } }

        fun run(id: String, text: String, allowed: Boolean = true,
                outcomeScope: Boolean = true, failed: Boolean = false,
                cancelBeforeDispatch: Boolean = false, maxCalls: Int = 1): AgentRunResult {
            val proposal = ToolProposal("find_files", mapOf("query" to "animation-A.json",
                "roots" to listOf("/sdcard/Download"), "limit" to 1))
            val plan = ReadOnlyToolPlan(proposal)
            lateinit var gate: ToolGate
            gate = ToolGate(ToolPolicy(readOnlyEnabled = allowed, maxCalls = maxCalls), ToolRunner { call ->
                backendCalls++
                assertEquals(proposal.toolName, call.name)
                assertEquals(proposal.arguments, call.arguments)
                actionsAtBackend = visualEvents.count { it is CockpitAnimationEvent.ActionStarted }
                if (failed) throw ToolBackendFailure(ToolExecutionStatus.FAILED, "HOST_FIXTURE read failed")
                "HOST_FIXTURE: /sdcard/Download/animation-A.json; source-revision=fixture-a-v1; read-only listing"
            }, receiptStore = receipts)
            triggers.commandAcknowledged(id, text)
            val countingPlan = AgentModel { task, memories, reflection, observations ->
                proposalCalls++
                plan.generate(task, memories, reflection, observations)
            }
            val result = BoundedAgentOrchestrator(countingPlan, { emptyList() }, gate, plan, store,
                limits = AgentLimits(maxRetries = 0, maxToolCalls = 1), callIdPrefix = id,
                evaluationConfirmsOutcome = outcomeScope,
                eventObserver = { event ->
                    cognitiveEvents += event
                    if (event.state == CognitiveWorkState.EXECUTING)
                        executingReceiptState = receipts.get(event.callId!!)?.state
                    if (event.state == CognitiveWorkState.EXECUTING && cancelBeforeDispatch) gate.cancel()
                }, onToolStarted = { callId, toolName ->
                    assertEquals(ToolReceiptState.DISPATCH_COMMITTED, receipts.get(callId)?.state)
                    triggers.toolStarted(id, callId, toolName)
                }).run(text)
            val proof = checkNotNull(result.taskEvidence)
            proof.calls.forEach { call ->
                triggers.toolFinished(id, call.callId, call.toolName,
                    call.status == ToolExecutionStatus.SUCCEEDED && call.freshOutput)
            }
            // Test adapter uses outcome, not delivery.
            triggers.turnEnded(id, proof.outcomeVerified)
            return result
        }
    }

    private fun withFixture(block: (RunFixture) -> Unit) {
        val dir = Files.createTempDirectory("omega-host-fixture").toFile()
        try { block(RunFixture(dir)) } finally { dir.deleteRecursively() }
    }

    @Test fun explicitInspectionHasFreshReceiptAndReviewOnlyExperience() = withFixture { f ->
        val result = f.run("omega-inspection", request)
        val proof = result.taskEvidence!!
        assertEquals(1, f.backendCalls)
        assertEquals(2, f.proposalCalls) // Deterministic callbacks, zero inference.
        assertEquals(1, f.actionsAtBackend)
        assertEquals(sha256Hex(request), proof.requestSha256)
        assertTrue(proof.outcomeVerified)
        assertTrue(proof.events.map { it.sequence } == proof.events.indices.toList())
        assertTrue(proof.events.all { it.taskId == "omega-inspection" && it.attempt == 0 })
        val call = proof.calls.single()
        val receipt = f.receipts.get(call.callId)!!
        assertEquals(ToolReceiptState.SUCCEEDED, receipt.state)
        assertEquals(receipt.outputSha256, call.outputSha256)
        assertTrue(call.freshOutput)
        assertEquals(result.experience!!.id, proof.learningCandidateExperienceId)
        assertEquals(1, f.store.all().size)
        assertTrue(ExperienceCompiler().compile(f.store.all()).all { it.requiresOwnerApproval })
        val action = f.visualEvents.filterIsInstance<CockpitAnimationEvent.ActionStarted>().single()
        assertEquals(CockpitAction.TOOL, action.action)
        assertFalse(f.visualEvents.any { it is CockpitAnimationEvent.ActionStarted &&
            it.action in setOf(CockpitAction.RESEARCH, CockpitAction.DEVELOP) })
        assertTrue(f.visualEvents.filterIsInstance<CockpitAnimationEvent.TurnEnded>().single().delivered)
        assertTrue(result.response.contains("HOST_FIXTURE"))
        assertFalse(result.response.contains("verbessert"))
        writeEvidence("inspection", "backendCalls=${f.backendCalls}\nmodelInferenceCalls=0\n" +
            "deterministicPlanCallbacks=${f.proposalCalls}\n$proof\n$receipt\n" +
            "${f.visualEvents.joinToString("\n")}\n")
    }

    @Test fun deniedInspectionHasNoDispatchAnimationOrSuccessfulMemory() = withFixture { f ->
        val result = f.run("omega-denied", request, allowed = false)
        assertEquals(0, f.backendCalls)
        assertFalse(result.taskEvidence!!.outcomeVerified)
        assertNull(result.taskEvidence!!.learningCandidateExperienceId)
        assertTrue(f.store.all().none { it.evaluation.success })
        assertTrue(f.visualEvents.none { it is CockpitAnimationEvent.ActionStarted })
        assertFalse(f.visualEvents.filterIsInstance<CockpitAnimationEvent.TurnEnded>().single().delivered)
    }

    @Test fun genericAssentNeverApprovesAnUnrelatedToolProposal() {
        for (text in listOf("Ja", "Ja, das meinte ich", "Genau, die andere Animation")) {
            var backendCalls = 0
            val store = InMemoryExperienceStore()
            val events = mutableListOf<CockpitAnimationEvent>()
            val bus = CockpitAnimationBus().also { it.subscribe { event -> events += event } }
            val triggers = CockpitAnimationTriggers(bus)
            val id = "assent-${text.length}"
            triggers.commandAcknowledged(id, text)
            val proposal = ToolProposal("launch_app", mapOf("packageOrName" to "org.example.unrelated"))
            val result = BoundedAgentOrchestrator(AgentModel { _, _, _, observations ->
                if (observations.isEmpty()) AgentModelOutput("HOST_FIXTURE", "", proposal)
                else AgentModelOutput("HOST_FIXTURE", "Keine Ausführung bestätigt.")
            }, { emptyList() }, ToolGate(ToolPolicy(), ToolRunner { backendCalls++; "must not execute" }),
                AgentEvaluator { _, _ -> Evaluation(true, 1.0) }, store,
                limits = AgentLimits(maxRetries = 0), callIdPrefix = id,
                onToolStarted = { callId, name -> triggers.toolStarted(id, callId, name) }).run(text)
            assertEquals(0, backendCalls)
            assertFalse(result.taskEvidence!!.outcomeVerified)
            assertEquals(false, result.taskEvidence!!.calls.single().ownerApproved)
            assertTrue(events.none { it is CockpitAnimationEvent.ActionStarted })
            assertTrue(store.all().none { it.evaluation.success })
        }
    }

    @Test fun exactWebTicketRejectsReplacementAndCannotAuthorizeDifferentArguments() {
        val consent = InternetConsentPolicy(InternetAccessMode.ASK)
        val web = ApprovedWebToolGate(consent)
        val a = WebToolRequest.Search("animation timing reference")
        val b = WebToolRequest.Search("unrelated account action")
        val old = web.prepare(a)!!
        val replacement = web.prepare(b)!!
        var staleCallbacks = 0
        assertFalse(web.completeApproval(old, ResearchMode.QUICK, true) { staleCallbacks++ })
        assertEquals(0, staleCallbacks)
        // A positive UI decision owns this ticket.
        var accepted: ApprovedWebResearchSession? = null
        assertTrue(web.completeApproval(replacement, ResearchMode.QUICK, true) { accepted = it })
        assertNotNull(accepted)
        var executed = 0
        assertThrows(IllegalStateException::class.java) { accepted!!.execute(a) { executed++ } }
        assertEquals(0, executed)
        accepted!!.execute(b) { executed++ }
        assertEquals(1, executed)
        assertThrows(IllegalStateException::class.java) { accepted!!.execute(b) { executed++ } }
        assertEquals(1, executed)
        consent.setMode(InternetAccessMode.OFF)
        assertFalse(accepted!!.isAuthorized())
        assertNull(web.prepare(a))
    }

    @Test fun replayCannotCreateAnotherDispatchOrFreshMemory() = withFixture { f ->
        val first = f.run("omega-replay", request)
        assertTrue(first.taskEvidence!!.outcomeVerified)
        val count = f.store.all().size
        val second = f.run("omega-replay", request)
        assertEquals(1, f.backendCalls)
        assertFalse(second.taskEvidence!!.outcomeVerified)
        assertFalse(second.taskEvidence!!.calls.single().freshOutput)
        assertNull(second.taskEvidence!!.learningCandidateExperienceId)
        assertEquals(count, f.store.all().size)
    }

    @Test fun failedOutputAndFormOnlyJudgementCannotVerifyWork() {
        for ((failed, scope) in listOf(true to true, false to false)) withFixture { f ->
            val result = f.run("omega-unverified", request, failed = failed, outcomeScope = scope)
            assertFalse(result.taskEvidence!!.outcomeVerified)
            assertNull(result.taskEvidence!!.learningCandidateExperienceId)
            assertTrue(f.store.all().none { it.outcome == ExperienceOutcome.SUCCESS })
            assertFalse(f.visualEvents.filterIsInstance<CockpitAnimationEvent.TurnEnded>().single().delivered)
        }
    }

    @Test fun executionSignalFollowsDurableDispatchCommit() = withFixture { f ->
        val result = f.run("omega-committed-signal", request)
        assertTrue(result.taskEvidence!!.outcomeVerified)
        assertEquals(ToolReceiptState.DISPATCH_COMMITTED, f.executingReceiptState)
    }

    @Test fun cancellationAtExecutionSignalIsAlreadyCommitted() = withFixture { f ->
        val result = f.run("omega-cancel", request, cancelBeforeDispatch = true)
        assertTrue(f.cognitiveEvents.any { it.state == CognitiveWorkState.EXECUTING })
        assertEquals(ToolReceiptState.DISPATCH_COMMITTED, f.executingReceiptState)
        assertTrue(result.taskEvidence!!.calls.single().outcomeUncertain)
        assertFalse(result.taskEvidence!!.outcomeVerified)
        assertNull(result.taskEvidence!!.learningCandidateExperienceId)
        writeEvidence("cancel-at-committed-signal", "HOST_FIXTURE\n" +
            "backendCalls=${f.backendCalls}\ncommitted=true\noutcome=UNKNOWN\n" +
            "${result.taskEvidence}\n")
    }

    @Test fun failedDurableCommitNeverSignalsExecution() {
        val dir = Files.createTempDirectory("omega-commit-failure").toFile()
        try {
            val delegate = FileToolExecutionReceiptStore(dir)
            val receipts = object : ToolExecutionReceiptStore by delegate {
                override fun transition(callId: String, expected: ToolReceiptState, next: ToolReceiptState,
                    atMillis: Long, outputLength: Int, outputSha256: String?): ToolExecutionReceipt? =
                    if (next == ToolReceiptState.DISPATCH_COMMITTED) null else
                        delegate.transition(callId, expected, next, atMillis, outputLength, outputSha256)
            }
            val events = mutableListOf<AgentCognitiveEvent>()
            var backendCalls = 0
            val plan = ReadOnlyToolPlan(ToolProposal("get_storage_info", emptyMap()))
            val result = BoundedAgentOrchestrator(plan, { emptyList() },
                ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { backendCalls++; "must not run" },
                    receiptStore = receipts), plan, InMemoryExperienceStore(),
                limits = AgentLimits(maxRetries = 0), eventObserver = { events += it }).run("Prüfe Speicher")
            assertEquals(0, backendCalls)
            assertFalse(events.any { it.state == CognitiveWorkState.EXECUTING })
            assertFalse(result.taskEvidence!!.outcomeVerified)
            assertNull(result.taskEvidence!!.learningCandidateExperienceId)
        } finally { dir.deleteRecursively() }
    }

    @Test fun zeroBudgetAndLateVisualsCannotStartWork() = withFixture { f ->
        val result = f.run("omega-budget", request, maxCalls = 0)
        assertEquals(0, f.backendCalls)
        assertFalse(result.taskEvidence!!.outcomeVerified)
        val eventCount = f.visualEvents.size
        f.triggers.toolStarted("omega-budget", "stale-call", "web_search")
        f.triggers.documentDraftReady("omega-budget", "stale result")
        f.triggers.turnEnded("omega-budget", true)
        assertEquals(eventCount, f.visualEvents.size)
    }

    private fun writeEvidence(name: String, text: String) {
        System.getProperty("omega.scenario.output")?.let { path ->
            File(path).mkdirs()
            File(path, "$name.txt").writeText(text)
        }
    }
}
