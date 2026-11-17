package at.heinz.deviceai.chief

import at.heinz.deviceai.ToolBackendFailure
import at.heinz.deviceai.ToolCallRequest
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.cognitive.AgentModel
import at.heinz.deviceai.cognitive.AgentModelOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ChiefWorkerScopeAdmissionTest {
    @Test
    fun queuedAssignmentDoesNotGainToolsAddedAfterSubmission() {
        val tools = mutableSetOf("get_battery_info", "get_system_info")
        val result = queuedAssignment(tools, "delete_file") { it.add("delete_file") }

        assertEquals(WorkerStatus.BLOCKED, result.report.status)
        assertEquals(0, result.executions)
    }

    @Test
    fun queuedAssignmentKeepsToolsRemovedAfterSubmission() {
        val tools = mutableSetOf("get_battery_info", "get_system_info")
        val result = queuedAssignment(tools, "get_battery_info") { it.remove("get_battery_info") }

        assertEquals(WorkerStatus.DONE, result.report.status)
        assertEquals("executed", result.report.summary)
        assertEquals(1, result.executions)
    }

    @Test
    fun directRunnerDoesNotGainToolsAddedAfterCreation() {
        val tools = mutableSetOf("get_battery_info", "get_system_info")
        var executions = 0
        val runner = WorkerScope.runner(brief("runner_add", tools), ToolRunner {
            executions++
            "executed"
        })
        tools.add("delete_file")

        val failure = runCatching { runner.execute(call("delete_file")) }.exceptionOrNull()

        assertTrue(failure is ToolBackendFailure)
        assertEquals(0, executions)
    }

    @Test
    fun directRunnerKeepsToolsRemovedAfterCreation() {
        val tools = mutableSetOf("get_battery_info", "get_system_info")
        var executions = 0
        val runner = WorkerScope.runner(brief("runner_remove", tools), ToolRunner {
            executions++
            "executed"
        })
        tools.remove("get_battery_info")

        assertEquals("executed", runner.execute(call("get_battery_info")))
        assertEquals(1, executions)
    }

    @Test
    fun directModelDoesNotGainToolsAddedAfterCreation() {
        val tools = mutableSetOf("get_battery_info", "get_system_info")
        val model = WorkerScope.model(brief("model_add", tools), proposing("delete_file"))
        tools.add("delete_file")

        val failure = runCatching {
            model.generate("task", emptyList(), null, emptyList())
        }.exceptionOrNull()

        assertTrue(failure is WorkerBlocked)
    }

    @Test
    fun directModelKeepsToolsRemovedAfterCreation() {
        val tools = mutableSetOf("get_battery_info", "get_system_info")
        val model = WorkerScope.model(brief("model_remove", tools), proposing("get_battery_info"))
        tools.remove("get_battery_info")

        val output = model.generate("task", emptyList(), null, emptyList())

        assertEquals("get_battery_info", output.proposal?.toolName)
    }

    private data class AdmissionResult(val report: WorkerReport, val executions: Int)

    private fun queuedAssignment(
        tools: MutableSet<String>,
        requestedTool: String,
        mutate: (MutableSet<String>) -> Unit
    ): AdmissionResult {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val targetFinished = CountDownLatch(1)
        val targetReport = AtomicReference<WorkerReport>()
        val executions = AtomicInteger()
        val pool = ChiefWorkerPool(maxConcurrent = 1, maxQueued = 1) { assignment ->
            if (assignment.id == "first") {
                WorkerJob {
                    firstStarted.countDown()
                    releaseFirst.await()
                    "first completed"
                }
            } else {
                val model = WorkerScope.model(assignment, proposing(requestedTool))
                val runner = WorkerScope.runner(assignment, ToolRunner {
                    executions.incrementAndGet()
                    "executed"
                })
                WorkerJob {
                    model.generate("task", emptyList(), null, emptyList())
                    runner.execute(call(requestedTool))
                }
            }
        }
        try {
            assertTrue(pool.submit(brief("first", emptySet())) {})
            assertTrue("The first assignment did not start", firstStarted.await(2, TimeUnit.SECONDS))
            assertTrue(pool.submit(brief("target", tools)) { report ->
                targetReport.set(report)
                targetFinished.countDown()
            })

            mutate(tools)
            releaseFirst.countDown()

            assertTrue("The queued assignment did not finish", targetFinished.await(2, TimeUnit.SECONDS))
            return AdmissionResult(requireNotNull(targetReport.get()), executions.get())
        } finally {
            releaseFirst.countDown()
            pool.shutdown()
        }
    }

    private fun brief(id: String, tools: Set<String>) =
        WorkerBrief(id, "Aufgabe $id", tools, deadlineMillis = 5_000)

    private fun call(tool: String) = ToolCallRequest("call", tool, emptyMap())

    private fun proposing(tool: String) = AgentModel { _, _, _, _ ->
        AgentModelOutput("model", "", ToolProposal(tool, emptyMap()))
    }
}
