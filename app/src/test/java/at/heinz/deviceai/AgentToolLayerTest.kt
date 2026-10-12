package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AgentToolLayerTest {
    @Test
    fun proposalsOnlyAddressRegisteredToolsAndStructuredArguments() {
        assertNotNull(ToolProposal("shell", mapOf("command" to "id")).validate())
        assertNotNull(ToolProposal("run_shell", emptyMap()).validate())
        assertNotNull(ToolProposal("get_device_info", mapOf("command" to "id")).validate())
        assertNotNull(ToolProposal("missing_tool", emptyMap()).validate())
        assertNotNull(ToolProposal("get_device_info", mapOf("data" to mapOf("x" to "y"))).validate())
        assertNotNull(ToolProposal("get_app_info", mapOf("packageOrName" to "")).validate())
        assertNotNull(ToolProposal("read_logcat", mapOf("lines" to Double.NaN)).validate())
        assertNotNull(ToolProposal("get_app_info", mapOf("packageOrName" to 42)).validate())
        assertNotNull(ToolProposal("find_files", mapOf("roots" to listOf(42))).validate())
        assertEquals(null, ToolProposal("get_device_info", emptyMap()).validate())
    }

    private fun <T : Throwable> assertThrows(exceptionClass: Class<T>, block: () -> Unit): T =
        org.junit.Assert.assertThrows(exceptionClass) { block() }

    @Test
    fun sideEffectingRegisteredToolsRequireConfirmationRisk() {
        assertEquals(Risk.CONFIRM, ToolRegistry.get("launch_app")?.risk)
        assertEquals(Risk.CONFIRM, ToolRegistry.get("take_screenshot")?.risk)
    }

    @Test
    fun gateRequiresPolicyAndConfirmationByRisk() {
        val runner = ToolRunner { "safe output" }
        val readOnly = ToolGate(ToolPolicy(readOnlyEnabled = false), runner)
            .execute(ToolProposal("get_device_info", emptyMap()).invocation("1"))
        assertEquals(ToolExecutionStatus.REJECTED, readOnly.record.status)

        val confirm = ToolGate(ToolPolicy(readOnlyEnabled = true), runner)
            .execute(ToolProposal("stop_app", mapOf("packageOrName" to "com.example.app")).invocation("2"))
        assertEquals(ToolExecutionStatus.REJECTED, confirm.record.status)
        val confirmed = ToolGate(ToolPolicy(), runner).execute(
            ToolProposal("stop_app", mapOf("packageOrName" to "com.example.app")).invocation("3"),
            userConfirmed = true
        )
        assertEquals(ToolExecutionStatus.SUCCEEDED, confirmed.record.status)

        val destructive = ToolProposal("delete_file", mapOf("path" to "/sdcard/Download/a.txt")).invocation("4")
        assertEquals(
            ToolExecutionStatus.REJECTED,
            ToolGate(ToolPolicy(readOnlyEnabled = true, destructiveEnabled = true), runner)
                .execute(destructive, userConfirmed = true).record.status
        )
        assertEquals(
            ToolExecutionStatus.SUCCEEDED,
            ToolGate(ToolPolicy(destructiveEnabled = true), runner)
                .execute(destructive, userConfirmed = true, destructiveConfirmed = true).record.status
        )
    }

    @Test
    fun gateEnforcesCallBudgetTimeoutCancellationOutputAndRedaction() {
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = true, maxCalls = 1, maxOutputCharacters = 4), ToolRunner { "123456" })
        val proposal = ToolProposal("get_device_info", emptyMap())
        val first = gate.execute(proposal.invocation("1"))
        assertEquals("1234", first.output)
        assertEquals(4, first.record.outputLength)
        assertEquals(
            ToolExecutionStatus.REJECTED,
            gate.execute(
                ToolInvocation("2", ToolCallRequest("2", "get_device_info", mapOf("token" to "private-value")))
            ).record.status
        )
        assertEquals(
            "[REDACTED]",
            gate.execute(
                ToolInvocation("3", ToolCallRequest("3", "get_device_info", mapOf("token" to "private-value")))
            )
                .record.sanitizedArguments["token"]
        )

        val started = CountDownLatch(1)
        val blockingGate = ToolGate(ToolPolicy(readOnlyEnabled = true, timeoutMillis = 5_000), ToolRunner {
            started.countDown()
            Thread.sleep(10_000)
            "late"
        })
        var result: ToolExecutionResult? = null
        val worker = Thread {
            result = blockingGate.execute(ToolProposal("get_device_info", emptyMap()).invocation("cancel"))
        }
        worker.start()
        assertTrue(started.await(1, TimeUnit.SECONDS))
        blockingGate.cancel()
        worker.join(1_000)
        assertFalse(worker.isAlive)
        assertEquals(ToolExecutionStatus.CANCELLED, result?.record?.status)

        val timeout = ToolGate(ToolPolicy(readOnlyEnabled = true, timeoutMillis = 20), ToolRunner {
            Thread.sleep(300)
            "late"
        }).execute(ToolProposal("get_device_info", emptyMap()).invocation("timeout"))
        assertEquals(ToolExecutionStatus.TIMED_OUT, timeout.record.status)
    }

    @Test
    fun skillGraphRejectsMissingCyclesAndDuplicateVersionsAndRequiresDependencies() {
        val a = Skill(
            SkillId("skill_a"), SkillVersion("1.0.0"), "A", "A skill",
            setOf(SkillCapability.RESEARCH), setOf(SkillDependency(SkillId("skill_b"), SkillVersion("1.0.0")))
        )
        val b = Skill(
            SkillId("skill_b"), SkillVersion("1.0.0"), "B", "B skill",
            setOf(SkillCapability.RESEARCH), setOf(SkillDependency(SkillId("skill_a"), SkillVersion("1.0.0")))
        )
        val graph = SkillGraph()
        assertThrows(IllegalArgumentException::class.java) { graph.install(a) }
        assertThrows(IllegalStateException::class.java) { graph.installAll(listOf(a, b)) }

        val dependency = BuiltInSkills.deviceStatus
        val dependent = a.copy(
            dependencies = setOf(SkillDependency(dependency.id, dependency.version)),
            id = SkillId("skill_c")
        )
        graph.installAll(listOf(dependency, dependent))
        val dependentKey = SkillKey(dependent.id, dependent.version)
        assertThrows(IllegalArgumentException::class.java) { graph.activate(dependentKey) }
        graph.activate(SkillKey(dependency.id, dependency.version))
        graph.activate(dependentKey)
        assertThrows(IllegalArgumentException::class.java) { graph.install(dependency) }
        assertThrows(IllegalArgumentException::class.java) {
            graph.disable(SkillKey(dependency.id, dependency.version))
        }
        graph.disable(dependentKey)
        graph.disable(SkillKey(dependency.id, dependency.version))
        assertTrue(graph.auditTrail().any { it.action == "enabled" })
    }

    @Test
    fun deviceStatusSkillComposesRegisteredReadOnlyTools() {
        val calls = mutableListOf<String>()
        val results = DeviceStatusSkillImplementation().execute(
            emptyMap(),
            ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { calls += it.name; it.name })
        )
        assertEquals(listOf("get_device_info", "get_battery_info"), calls)
        assertTrue(results.all { it.record.status == ToolExecutionStatus.SUCCEEDED })
    }
}
