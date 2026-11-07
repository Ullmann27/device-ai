package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class DevelopmentReviewRegressionTest {
    private val source = SourceSnapshot.of("app/Counter.kt", "fun increment(value: Int): Int = value - 1\n")
    private val replacement = "fun increment(value: Int): Int = value + 1\n"
    private fun task() = DevelopmentTask("counter-test", "Correct increment(1) to return 2", listOf(source))
    private fun developer() = """{"summary":"Fix increment","edits":[{"path":"${source.path}","expectedBaseSha256":"${source.sha256}","replacement":${jsonString(replacement)},"rationale":"Wrong operator"}]}"""
    private val accept = """{"verdict":"ACCEPT","findings":[]}"""
    private val tests = """{"tests":[{"name":"incrementOne","description":"Assert increment(1) == 2; not executed"}]}"""
    private val recommend = """{"decision":"RECOMMEND","rationale":"Reviewed replacement"}"""

    private fun runChain(critic: String = accept, dev: String = developer(), judge: String = recommend): DevelopmentOutcome {
        val responses = ArrayDeque(listOf(dev, critic, tests, judge))
        return DevelopmentCoordinator({ ReasoningPassResult("scripted-fake@1", responses.removeFirst(), 0) }, {}).run(task())
    }

    @Test fun actualReplacementAndTestDescriptionsReachReviewers() {
        val requests = mutableListOf<ReasoningPassRequest>()
        val responses = ArrayDeque(listOf(developer(), accept, tests, recommend))
        val original = source.content
        val result = DevelopmentCoordinator({ request ->
            requests += request
            ReasoningPassResult("scripted-fake@1", responses.removeFirst(), 0)
        }, {}).run(task())
        assertEquals(DevelopmentJobState.COMPLETED, result.state)
        assertEquals(4, requests.size)
        for (request in requests.drop(1)) {
            assertTrue(request.prompt.contains(jsonString(developer())))
            assertTrue(request.prompt.contains(jsonString(source.content)))
        }
        assertTrue(requests.last().prompt.contains(jsonString(tests)))
        assertEquals(original, source.content)
        assertTrue(result.proposal!!.render().contains("NOT EXECUTED"))
    }

    @Test fun recommendationCannotOverrideCriticRejectOrRevise() {
        for (verdict in listOf("REJECT", "REVISE")) {
            val critic = """{"verdict":"$verdict","findings":[]}"""
            val result = runChain(critic)
            assertEquals(DevelopmentJobState.FAILED, result.state)
            assertEquals(DevelopmentFailureKind.POLICY_REJECTED, result.failure!!.kind)
            assertNull(result.proposal)
        }
    }

    @Test fun acceptedVerdictWithBlockerCannotBeRecommended() {
        val critic = """{"verdict":"ACCEPT","findings":[{"severity":"BLOCKER","message":"Still unsafe"}]}"""
        assertEquals(DevelopmentFailureKind.POLICY_REJECTED, runChain(critic).failure!!.kind)
    }

    @Test fun emptyEditsCannotBeRecommended() {
        assertEquals(DevelopmentFailureKind.POLICY_REJECTED,
            runChain(dev = """{"summary":"No changes","edits":[]}""").failure!!.kind)
    }

    @Test fun rejectionCanStillProduceAnExplicitReviewOnlyReport() {
        val result = runChain("""{"verdict":"REJECT","findings":[]}""",
            judge = """{"decision":"REJECT","rationale":"Not suitable"}""")
        assertEquals(DevelopmentJobState.COMPLETED, result.state)
        assertEquals(JudgeDecision.REJECT, result.proposal!!.judge.decision)
    }

    @Test fun cancellationFromStateObserverPreventsFirstModelCall() {
        var calls = 0
        lateinit var coordinator: DevelopmentCoordinator
        coordinator = DevelopmentCoordinator({ calls++; ReasoningPassResult("fake", developer(), 0) }, {})
        val result = coordinator.run(task()) { state ->
            if (state == DevelopmentJobState.RUNNING_DEVELOPER) coordinator.cancel()
        }
        assertEquals(DevelopmentJobState.CANCELLED, result.state)
        assertEquals(0, calls)
    }

    @Test fun monotonicDeadlineSurvivesWallClockRollback() {
        var wall = 1_000L
        var nanos = 0L
        var calls = 0
        val result = DevelopmentCoordinator({
            calls++; wall = 0; nanos = 60_000_000_000L
            ReasoningPassResult("fake", developer(), 0)
        }, {}, { wall }, { nanos }).run(task())
        assertEquals(DevelopmentJobState.TIMED_OUT, result.state)
        assertEquals(1, calls)
    }

    @Test fun mutableSourceListCannotChangeTheActiveSnapshot() {
        val backing = mutableListOf(source)
        val input = DevelopmentTask("mutable-test", "Fix increment", backing)
        val responses = ArrayDeque(listOf(developer(), accept, tests, recommend))
        val result = DevelopmentCoordinator({
            backing.clear()
            ReasoningPassResult("fake", responses.removeFirst(), 0)
        }, {}).run(input)
        assertEquals(DevelopmentJobState.COMPLETED, result.state)
    }

    @Test fun observerExceptionsDoNotLeaveAnUnfinishedJob() {
        val responses = ArrayDeque(listOf(developer(), accept, tests, recommend))
        val result = DevelopmentCoordinator({ ReasoningPassResult("fake", responses.removeFirst(), 0) }, {})
            .run(task()) { throw IllegalStateException("UI detached") }
        assertEquals(DevelopmentJobState.COMPLETED, result.state)
    }

    @Test fun sourceBudgetIsEnforcedDuringSelectionInUtf8Bytes() {
        val controller = DevelopmentUiController { "ui" }
        assertTrue(controller.addSource("A.kt", "ä".repeat(1_999)))
        assertFalse(controller.addSource("B.kt", "abc"))
        assertEquals(1, controller.state.sources.size)
        assertNotNull(controller.state.error)
    }

    @Test fun changingInputsInvalidatesPreviousExport() {
        val controller = DevelopmentUiController { "ui" }
        controller.setGoal("Fix increment")
        controller.addSource(source.path, source.content)
        assertNotNull(controller.start(true))
        controller.onOutcome(runChain())
        assertNotNull(controller.exportText())
        controller.setGoal("Another task")
        assertNull(controller.exportText())
        assertEquals(DevelopmentUiPhase.IDLE, controller.state.phase)
    }
}
