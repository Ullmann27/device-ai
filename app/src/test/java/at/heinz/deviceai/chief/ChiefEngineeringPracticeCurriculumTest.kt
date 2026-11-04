package at.heinz.deviceai.chief

import at.heinz.deviceai.routines.AgentMailbox
import at.heinz.deviceai.routines.AgentMessage
import at.heinz.deviceai.routines.AgentPriority
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class ChiefEngineeringPracticeCurriculumTest {
    private fun exercise(agentId: String, id: String): ChiefBotPracticeExercise =
        ChiefEngineeringPracticeCurriculum.exercises(agentId).single { it.id == id }

    private fun scope() = WorkspaceTaskScope(
        "bot-link", "t-" + "a".repeat(16), "b".repeat(40),
        "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "c".repeat(64),
        "dddddddd-dddd-4ddd-8ddd-dddddddddddd", "node-continuum", 256, 1, 64, 1_000
    )

    private fun brief(scope: WorkspaceTaskScope) = WorkerBrief(
        ChiefWorkspaceWiring.briefId(scope), "developer-only curriculum oracle", emptySet(), 2_000
    )

    private fun observation(scope: WorkspaceTaskScope) = WorkspaceObservation(
        scope, "continuum-" + "e".repeat(24), "f".repeat(64), true, 0
    )

    private fun report(scope: WorkspaceTaskScope) = WorkerReport(
        ChiefWorkspaceWiring.briefId(scope), WorkerStatus.DONE, "synthetic observation only", 1
    )

    private fun jobReturning(scope: WorkspaceTaskScope, result: WorkspaceObservation): WorkerJob =
        ChiefWorkspaceWiring.job(brief(scope), scope, object : ChiefWorkspacePort {
            override fun execute(scope: WorkspaceTaskScope) = result
            override fun cancel(scope: WorkspaceTaskScope) = Unit
        })

    @Test fun eachExistingEngineeringBotHasFourExercisesAndTwoSeparateTransferCases() {
        val all = listOf("bot-kora", "bot-link").flatMap { agentId ->
            val cases = ChiefEngineeringPracticeCurriculum.exercises(agentId)
            assertEquals(6, cases.size)
            assertEquals(4, cases.count { !it.transfer })
            assertEquals(2, cases.count { it.transfer })
            assertTrue(cases.all { it.agentId == agentId })
            assertEquals(cases.size, cases.map { it.prompt }.distinct().size)
            cases
        }
        assertEquals(12, all.map { it.id }.distinct().size)
        assertTrue(all.all { it.id.endsWith("-v1") })
        for (unknown in listOf("", "bot-forge", "BOT-KORA", "bot-kora-extra")) {
            assertTrue(ChiefEngineeringPracticeCurriculum.exercises(unknown).isEmpty())
        }
    }

    @Test fun promptsAndAnswersStayBoundedAndKeepTheAnswerKeySeparate() {
        for (agentId in listOf("bot-kora", "bot-link")) {
            for (case in ChiefEngineeringPracticeCurriculum.exercises(agentId)) {
                assertTrue(case.id, Regex("[a-z0-9][a-z0-9._:-]{0,79}").matches(case.id))
                assertTrue(case.id, case.title.isNotBlank() && case.title.length <= 160)
                assertTrue(case.id, case.prompt.toByteArray(Charsets.UTF_8).size < 1_800)
                assertTrue(case.id, case.expected.size in 1..8)
                assertTrue(case.id, case.expected.keys.all { Regex("[a-z][a-z0-9_]{0,39}").matches(it) })
                assertTrue(case.id, case.expected.values.all { it.isNotEmpty() && it.length <= 160 })
                assertTrue(case.id, case.explanation.isNotBlank() && case.explanation.toByteArray(Charsets.UTF_8).size <= 1_200)
                val answer = case.expected.entries.joinToString(",", "{", "}") { "\"${it.key}\":\"${it.value}\"" }
                assertFalse(case.id, case.prompt.contains(answer))
                assertFalse(case.id, case.prompt.contains(case.explanation))
                assertTrue(case.id, case.prompt.contains("alle Werte als Strings"))
                assertTrue(case.id, case.prompt.contains("führe nichts aus"))
                if (agentId == "bot-link") assertTrue(case.id, case.prompt.contains("Vertrag:"))
            }
        }
    }

    @Test fun zeroDefaultAnswerPreservesExplicitValuesAndRejectsBothWrongAlternatives() {
        val case = exercise("bot-kora", "kora-preserve-zero-v1")
        val strategies: Map<String, (Int?) -> Int> = mapOf(
            "ELVIS" to { value: Int? -> value ?: 20 },
            "ZERO_DEFAULT" to { value: Int? -> if (value == null || value == 0) 20 else value },
            "ALWAYS_DEFAULT" to { _: Int? -> 20 }
        )
        val inputs = listOf(null, 0, 7, -2)
        val required = listOf(20, 0, 7, -2)
        val selected = requireNotNull(strategies[case.expected.getValue("fix")])
        assertEquals(required, inputs.map(selected))
        assertEquals(selected(0).toString(), case.expected["zero"])
        assertEquals(selected(null).toString(), case.expected["missing"])
        for ((id, strategy) in strategies.filterKeys { it != case.expected["fix"] }) {
            assertNotEquals(id, required, inputs.map(strategy))
        }
    }

    @Test fun minimalLimitAnswerPreservesOrderIncludingEmptyAndOversizedLimits() {
        val case = exercise("bot-kora", "kora-minimal-limit-v1")
        val strategies: Map<String, (List<Int>, Int) -> List<Int>> = mapOf(
            "A" to { values: List<Int>, limit: Int -> values.take(limit) },
            "B" to { values: List<Int>, limit: Int -> values.sorted().take(limit) },
            "C" to { _: List<Int>, _: Int -> emptyList() }
        )
        val values = listOf(7, 2, 9)
        val selected = requireNotNull(strategies[case.expected.getValue("patch")])
        assertEquals(listOf(7, 2), selected(values, 2))
        assertEquals(case.expected["items"], selected(values, 2).joinToString(","))
        assertEquals(emptyList<Int>(), selected(values, 0))
        assertEquals(values, selected(values, 20))
        for ((id, strategy) in strategies.filterKeys { it != case.expected["patch"] }) {
            assertNotEquals(id, listOf(7, 2), strategy(values, 2))
        }
        assertEquals(listOf(7, 2, 9), values)
    }

    @Test fun falsyTransferAnswerUsesDifferentInputsWithoutCollapsingThem() {
        val case = exercise("bot-kora", "kora-transfer-falsy-v1")
        fun correct(value: Any?): Any = value ?: "auto"
        fun flawed(value: Any?): Any = if (value == null || value == false || value == "") "auto" else value
        fun render(value: Any): String = value.toString().ifEmpty { "<empty>" }
        assertEquals("NULL_ONLY", case.expected["rule"])
        assertEquals(render(correct(false)), case.expected["flag"])
        assertEquals(render(correct("")), case.expected["text"])
        assertEquals(render(correct(null)), case.expected["missing"])
        assertNotEquals(case.expected["flag"], render(flawed(false)))
        assertNotEquals(case.expected["text"], render(flawed("")))
        assertTrue(case.transfer)
    }

    @Test fun absentReproductionAndAbsentIndependentTestsNeverHavePositiveAnswerKeys() {
        val reproduce = exercise("bot-kora", "kora-reproduce-v1")
        assertEquals("REPRODUCE", reproduce.expected["next"])
        assertEquals("no", reproduce.expected["verified"])
        for (id in listOf("kora-independent-check-v1", "kora-transfer-zero-tests-v1")) {
            val case = exercise("bot-kora", id)
            assertEquals("INDEPENDENT_TEST", case.expected["next"])
            assertEquals("no", case.expected["verified"])
        }
        assertEquals("ZERO_EXECUTED", exercise("bot-kora", "kora-transfer-zero-tests-v1").expected["reason"])
    }

    @Test fun reportIdentityAnswerMatchesTheExistingProductionMailboxAdapter() {
        val case = exercise("bot-link", "link-report-identity-v1")
        val scope = scope()
        val mailbox = AgentMailbox()
        val callbacks = mutableListOf<WorkspaceDelivery>()
        val sink = ChiefWorkspaceWiring.mailboxReporter(mailbox, scope, onDelivery = { callbacks += it })
        sink(report(scope).copy(id = "workspace-eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"))
        assertEquals(case.expected["delivery"], callbacks.single().status.name)
        assertEquals(case.expected["queued"], mailbox.pending("chief").toString())
    }

    @Test fun idempotenceAnswerMatchesTwoCallsToTheSameProductionReporter() {
        val case = exercise("bot-link", "link-idempotent-report-v1")
        val scope = scope()
        val mailbox = AgentMailbox(maxPerAgent = 2)
        val callbacks = mutableListOf<WorkspaceDelivery>()
        val sink = ChiefWorkspaceWiring.mailboxReporter(mailbox, scope, onDelivery = { callbacks += it })
        val report = report(scope)
        sink(report)
        sink(report)
        assertEquals(case.expected["first"], callbacks[0].status.name)
        assertEquals(case.expected["second"], callbacks[1].status.name)
        assertEquals(case.expected["queued"], mailbox.pending("chief").toString())
    }

    @Test fun fullMailboxAnswerPreservesRetryAfterAnObservedDeliveryRejection() {
        val case = exercise("bot-link", "link-full-mailbox-v1")
        val scope = scope()
        val mailbox = AgentMailbox(maxPerAgent = 1)
        assertTrue(mailbox.send(AgentMessage("occupied", "fixture", "chief", AgentPriority.INFO, "occupied")))
        val callbacks = mutableListOf<WorkspaceDelivery>()
        val sink = ChiefWorkspaceWiring.mailboxReporter(mailbox, scope, onDelivery = { callbacks += it })
        sink(report(scope))
        assertEquals(case.expected["first"], callbacks.single().status.name)
        assertEquals("occupied", mailbox.drain("chief").single().id)
        sink(report(scope))
        assertEquals(case.expected["second"], callbacks[1].status.name)
        assertEquals(case.expected["queued"], mailbox.pending("chief").toString())
    }

    @Test fun cancellationAnswerMatchesTheProductionCheckAfterExecuteReturns() {
        val case = exercise("bot-link", "link-cancelled-result-v1")
        val scope = scope()
        var cancels = 0
        lateinit var job: WorkerJob
        val port = object : ChiefWorkspacePort {
            override fun execute(scope: WorkspaceTaskScope): WorkspaceObservation {
                job.cancel()
                return observation(scope)
            }
            override fun cancel(scope: WorkspaceTaskScope) { cancels++ }
        }
        job = ChiefWorkspaceWiring.job(brief(scope), scope, port)
        assertThrows(CancellationException::class.java) { job.run() }
        assertEquals(1, cancels)
        assertEquals(WorkerStatus.CANCELLED.name, case.expected["result"])
        assertEquals("no", case.expected["success"])
    }

    @Test fun requestHashTransferAnswerMatchesTheProductionScopeRejection() {
        val case = exercise("bot-link", "link-transfer-request-binding-v1")
        val scope = scope()
        val wrong = observation(scope.copy(requestHash = "d".repeat(64)))
        val failure = assertThrows(IllegalStateException::class.java) { jobReturning(scope, wrong).run() }
        assertEquals(case.expected["reason"], failure.message)
        assertEquals("BLOCKED", case.expected["result"])
        assertTrue(case.transfer)
    }

    @Test fun uncertainTeardownTransferAnswerMatchesTheProductionResourceBarrier() {
        val case = exercise("bot-link", "link-transfer-teardown-v1")
        val scope = scope()
        val unknown = observation(scope).copy(teardownConfirmed = false)
        val failure = assertThrows(IllegalStateException::class.java) { jobReturning(scope, unknown).run() }
        assertEquals(case.expected["reason"], failure.message)
        assertEquals("BLOCKED", case.expected["result"])
        assertEquals("no", case.expected["success"])
        assertTrue(case.transfer)
    }
}
