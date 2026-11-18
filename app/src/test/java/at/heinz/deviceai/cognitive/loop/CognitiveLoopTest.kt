package at.heinz.deviceai.cognitive.loop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CognitiveLoopTest {
    private var now = 1_000L
    private val events = mutableListOf<CognitiveLoopEvent>()
    private val battery = PlannedStep("Akku lesen", "get_battery_info")
    private val search = PlannedStep("Suchen", "web_search", mapOf("query" to "wetter wien"))
    private val delete = PlannedStep("Löschen", "delete_file", mapOf("path" to "Download/a.txt"))

    private inner class FakeTools(val onRun: (PlannedStep) -> ToolOutcome = { ToolOutcome(ToolOutcomeStatus.SUCCEEDED, "85 %") }) : LoopToolPort {
        val calls = mutableListOf<Pair<PlannedStep, Boolean>>()
        var cancelled = false
        override fun execute(turnId: String, callId: String, step: PlannedStep, ownerConfirmed: Boolean, budget: LoopBudget): ToolOutcome {
            calls += step to ownerConfirmed
            return onRun(step)
        }
        override fun cancel() { cancelled = true }
    }

    private inner class FakeMemory(val hits: List<RecallHit> = emptyList()) : LoopMemoryPort {
        val log = mutableListOf<String>()
        override fun recall(goal: String, k: Int): List<RecallHit> { log += "recall:$goal:$k"; return hits }
        override fun recordOutcome(turnId: String, stepIndex: Int, toolName: String, ok: Boolean, summary: String) {
            log += "record:$turnId:$stepIndex:$toolName:$ok:$summary"
        }
    }

    private fun ports(
        planner: LoopPlanner,
        tools: LoopToolPort = FakeTools(),
        approval: OwnerApprovalPort = OwnerApprovalPort { _, _, _, _ -> OwnerDecision.DENIED },
        question: OwnerQuestionPort = OwnerQuestionPort { _, _, _, _ -> OwnerAnswer.NoAnswer },
        verifier: ResultVerifier = ResultVerifier { _, _, o, _ -> Verification(o.ok, if (o.ok) "ok" else "fehlgeschlagen") },
        memory: LoopMemoryPort = FakeMemory()
    ) = LoopPorts(planner, tools, verifier, memory, approval, question)

    private fun loop(p: LoopPorts, limits: LoopLimits = LoopLimits(), extra: List<CognitiveLoopListener> = emptyList()) =
        CognitiveLoop("t1", p, limits, { now }, listOf(CognitiveLoopListener { events += it }) + extra)

    private fun planOnceThenDone(step: PlannedStep) = LoopPlanner { _, history, _, _ ->
        if (history.isEmpty()) PlanOutcome.Plan(listOf(step)) else PlanOutcome.Done("fertig")
    }

    @Test fun readOnlyToolRunsWithoutOwnerQuestionAndEmitsStepsInOrder() {
        val tools = FakeTools()
        val outcome = loop(ports(planOnceThenDone(battery), tools)).run("Akkustand?")
        assertEquals(LoopOutcome.Finished("fertig", 1), outcome)
        assertEquals(listOf(battery to false), tools.calls)
        assertEquals(listOf(
            CognitiveLoopEvent.GoalSet("t1", 1_000, 0, "Akkustand?"),
            CognitiveLoopEvent.PlanMade("t1", 1_000, 1, listOf(battery)),
            CognitiveLoopEvent.ToolRequested("t1", 1_000, 1, "get_battery_info"),
            CognitiveLoopEvent.ToolResult("t1", 1_000, 1, true, "85 %"),
            CognitiveLoopEvent.ResultVerified("t1", 1_000, 1, true, "ok"),
            CognitiveLoopEvent.MemoryWritten("t1", 1_000, 1, null),
            CognitiveLoopEvent.Finished("t1", 1_000, 2, "fertig")), events)
    }

    @Test fun externalEffectNeedsOwnerYesAndDenialAbortsCalmlyWithoutRunning() {
        val tools = FakeTools()
        val outcome = loop(ports(planOnceThenDone(search), tools)).run("Wetter?")
        assertTrue(tools.calls.isEmpty())
        assertEquals(AbortReason.GATE_DENIED, (outcome as LoopOutcome.Aborted).reason)
        val ask = events.filterIsInstance<CognitiveLoopEvent.AskOwner>().single()
        assertEquals(listOf("ja", "nein"), ask.options.map { it.id })
        assertTrue(events.last() is CognitiveLoopEvent.Aborted)
    }

    @Test fun ownerYesRunsTheToolConfirmed() {
        val tools = FakeTools()
        val outcome = loop(ports(planOnceThenDone(search), tools,
            approval = { _, tool, _, _ -> if (tool == "web_search") OwnerDecision.APPROVED else OwnerDecision.DENIED })).run("Wetter?")
        assertEquals(listOf(search to true), tools.calls)
        assertTrue(outcome is LoopOutcome.Finished)
    }

    @Test fun destructiveOrUnknownToolsAreNeverRunNorOffered() {
        for (step in listOf(delete, PlannedStep("x", "erfundenes_tool"))) {
            events.clear()
            val tools = FakeTools()
            var asked = false
            val outcome = loop(ports(planOnceThenDone(step), tools,
                approval = { _, _, _, _ -> asked = true; OwnerDecision.APPROVED })).run("weg damit")
            assertTrue(tools.calls.isEmpty()); assertFalse(asked)
            assertEquals(AbortReason.GATE_DENIED, (outcome as LoopOutcome.Aborted).reason)
        }
    }

    @Test fun gateRejectionAbortsAsGateDenied() {
        val tools = FakeTools { ToolOutcome(ToolOutcomeStatus.REJECTED_BY_GATE, "Read-only tools are disabled by operator policy.") }
        val outcome = loop(ports(planOnceThenDone(battery), tools)).run("Akku")
        assertEquals(AbortReason.GATE_DENIED, (outcome as LoopOutcome.Aborted).reason)
    }

    @Test fun failedToolIsVerifiedAndThePlannerSeesItInTheHistory() {
        val seen = mutableListOf<List<StepRecord>>()
        val tools = FakeTools { ToolOutcome(ToolOutcomeStatus.FAILED, "kaputt") }
        val planner = LoopPlanner { _, h, _, _ -> seen += h; if (h.isEmpty()) PlanOutcome.Plan(listOf(battery)) else PlanOutcome.Done("aufgegeben") }
        loop(ports(planner, tools)).run("Akku")
        assertEquals(false, seen[1].single().verification!!.passed)
        assertEquals(ToolOutcomeStatus.FAILED, seen[1].single().tool!!.status)
        assertTrue(CognitiveLoopEvent.ToolResult("t1", 1_000, 1, false, "kaputt") in events)
    }

    @Test fun maxStepsEndsWithBudget() {
        val tools = FakeTools()
        val outcome = loop(ports({ _, _, _, _ -> PlanOutcome.Plan(listOf(battery)) }, tools), LoopLimits(maxSteps = 3)).run("endlos")
        assertEquals(3, tools.calls.size)
        assertEquals(LoopOutcome.Aborted(AbortReason.BUDGET, (outcome as LoopOutcome.Aborted).calmNote, 3), outcome)
    }

    @Test fun stepTimeoutDiscardsTheLateResult() {
        var verified = false
        val tools = FakeTools { now += 6_000; ToolOutcome(ToolOutcomeStatus.SUCCEEDED, "spät") }
        val outcome = loop(ports(planOnceThenDone(battery), tools, verifier = { _, _, _, _ -> verified = true; Verification(true, "") }),
            LoopLimits(stepTimeoutMillis = 5_000)).run("Akku")
        assertEquals(AbortReason.TIMEOUT, (outcome as LoopOutcome.Aborted).reason)
        assertFalse(verified)
        assertTrue(events.none { it is CognitiveLoopEvent.ToolResult })
    }

    @Test fun portsGetTheStepDeadline() {
        var deadline = -1L
        val planner = LoopPlanner { _, h, _, b -> deadline = b.deadlineAtMillis; if (h.isEmpty()) PlanOutcome.Plan(listOf(battery)) else PlanOutcome.Done("ok") }
        loop(ports(planner), LoopLimits(stepTimeoutMillis = 5_000, totalTimeoutMillis = 7_000)).run("Akku")
        assertEquals(1_000L + 5_000, deadline)
        now = 4_000
        loop(ports(planner), LoopLimits(stepTimeoutMillis = 5_000, totalTimeoutMillis = 7_000)).run("Akku")
        assertEquals(4_000L + 5_000, deadline)
    }

    @Test fun totalTimeoutEndsTheLoop() {
        val tools = FakeTools { now += 4_000; ToolOutcome(ToolOutcomeStatus.SUCCEEDED, "ok") }
        val outcome = loop(ports({ _, _, _, _ -> PlanOutcome.Plan(listOf(battery)) }, tools),
            LoopLimits(stepTimeoutMillis = 5_000, totalTimeoutMillis = 10_000)).run("lang")
        assertEquals(AbortReason.TIMEOUT, (outcome as LoopOutcome.Aborted).reason)
        assertTrue(tools.calls.size in 2..3)
    }

    @Test fun cancelStopsAtTheNextCheckpointAndCancelsTheTool() {
        lateinit var l: CognitiveLoop
        val tools = FakeTools { l.cancel(); ToolOutcome(ToolOutcomeStatus.SUCCEEDED, "ok") }
        l = loop(ports({ _, _, _, _ -> PlanOutcome.Plan(listOf(battery)) }, tools))
        val outcome = l.run("Akku")
        assertEquals(AbortReason.CANCELLED, (outcome as LoopOutcome.Aborted).reason)
        assertTrue(tools.cancelled)
        assertTrue(events.none { it is CognitiveLoopEvent.MemoryWritten })
    }

    @Test fun cancelBeforeRunDoesNothing() {
        val tools = FakeTools()
        val l = loop(ports(planOnceThenDone(battery), tools)); l.cancel()
        assertEquals(AbortReason.CANCELLED, (l.run("Akku") as LoopOutcome.Aborted).reason)
        assertTrue(tools.calls.isEmpty())
    }

    @Test fun uncertainPlannerAsksTheOwnerAndUsesTheAnswer() {
        val options = listOf(LoopOption("akku", "Akku"), LoopOption("speicher", "Speicher"))
        val planner = LoopPlanner { _, h, _, _ ->
            when {
                h.isEmpty() -> PlanOutcome.Uncertain("Was soll ich prüfen?", options)
                h.size == 1 && h[0].ownerAnswer?.id == "akku" -> PlanOutcome.Plan(listOf(battery))
                else -> PlanOutcome.Done("ok")
            }
        }
        val tools = FakeTools()
        val outcome = loop(ports(planner, tools, question = { _, _, o, _ -> OwnerAnswer.Chosen(o.first()) })).run("prüf mal")
        assertTrue(outcome is LoopOutcome.Finished)
        assertEquals(listOf(battery to false), tools.calls)
        assertEquals(CognitiveLoopEvent.AskOwner("t1", 1_000, 1, "Was soll ich prüfen?", options),
            events.filterIsInstance<CognitiveLoopEvent.AskOwner>().single())
    }

    @Test fun unansweredQuestionTimesOutCalmly() {
        val outcome = loop(ports({ _, _, _, _ -> PlanOutcome.Uncertain("?", listOf(LoopOption("a", "A"))) })).run("x")
        assertEquals(AbortReason.TIMEOUT, (outcome as LoopOutcome.Aborted).reason)
    }

    @Test fun portErrorsEndCalmlyAndBrokenListenersAreIsolated() {
        val outcome = loop(ports({ _, _, _, _ -> error("planner kaputt") }),
            extra = listOf(CognitiveLoopListener { throw IllegalStateException("render kaputt") })).run("x")
        val aborted = outcome as LoopOutcome.Aborted
        assertEquals(AbortReason.ERROR, aborted.reason)
        assertTrue(aborted.calmNote.isNotBlank())
        assertEquals(aborted.calmNote, (events.last() as CognitiveLoopEvent.Aborted).calmNote)
    }

    @Test fun plannerFailureIsAnErrorNotAnAlarm() {
        val outcome = loop(ports({ _, _, _, _ -> PlanOutcome.Failed("Lokales Modell nicht geladen") })).run("x")
        assertEquals(AbortReason.ERROR, (outcome as LoopOutcome.Aborted).reason)
        assertTrue(outcome.calmNote.contains("Lokales Modell nicht geladen"))
    }

    @Test fun aLoopRunsOnlyOnce() {
        val l = loop(ports(planOnceThenDone(battery)))
        l.run("a")
        try { l.run("b"); throw AssertionError("second run allowed") } catch (_: IllegalStateException) { }
    }

    @Test fun recallComesBeforeEveryPlanAndOutcomeIsRecordedAfterVerify() {
        val hits = listOf(RecallHit("e7", 0.9f, 1))
        val memory = FakeMemory(hits)
        val order = mutableListOf<String>()
        val seenHits = mutableListOf<List<RecallHit>>()
        val planner = LoopPlanner { _, h, recalled, _ ->
            order += "plan"; seenHits += recalled
            if (h.isEmpty()) PlanOutcome.Plan(listOf(battery)) else PlanOutcome.Done("fertig")
        }
        val verifier = ResultVerifier { _, _, o, _ -> order += "verify"; Verification(o.ok, "ok") }
        val tracking = object : LoopMemoryPort {
            override fun recall(goal: String, k: Int): List<RecallHit> { order += "recall"; return memory.recall(goal, k) }
            override fun recordOutcome(turnId: String, stepIndex: Int, toolName: String, ok: Boolean, summary: String) {
                order += "record"; memory.recordOutcome(turnId, stepIndex, toolName, ok, summary)
            }
        }
        loop(ports(planner, verifier = verifier, memory = tracking), LoopLimits(recallK = 3)).run("Akku")
        assertEquals(listOf("recall", "plan", "verify", "record", "recall", "plan"), order)
        assertEquals(listOf(hits, hits), seenHits)
        assertEquals(listOf("recall:Akku:3", "record:t1:1:get_battery_info:true:85 %", "recall:Akku:3"), memory.log)
    }

    @Test fun failedToolOutcomeIsRecordedAsNotOk() {
        val memory = FakeMemory()
        loop(ports(planOnceThenDone(battery), FakeTools { ToolOutcome(ToolOutcomeStatus.FAILED, "kaputt") }, memory = memory)).run("Akku")
        assertTrue("record:t1:1:get_battery_info:false:kaputt" in memory.log)
    }
}
