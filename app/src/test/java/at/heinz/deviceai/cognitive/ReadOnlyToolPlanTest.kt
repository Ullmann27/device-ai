package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import java.io.File
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import org.junit.Assert.*
import org.junit.Test

class ReadOnlyToolPlanTest {
    @Test fun naturalRequestRunsRealStorageProbeAndPersistsReceiptBoundEpisodeAcrossRestart() {
        val dir = Files.createTempDirectory("continuum-real-action").toFile()
        try {
            val request = "Continuum, prüfe bitte den freien Speicher."
            val decision = BootstrapBrain.decide(request, listOf("Speicherprüfungen sind nur lesend."))
            assertEquals(BootstrapKind.DEVICE_TOOL, decision.kind)
            assertEquals("get_storage_info", decision.tool)
            val plan = ReadOnlyToolPlan(ToolProposal(checkNotNull(decision.tool), decision.arguments))
            val memory = MemoryRecord("confirmed-storage-preference", MemoryType.SEMANTIC,
                "Speicher nur lesend prüfen", ProvenanceType.USER_PROVIDED, 0.9, 1, 1,
                privacyClass = PrivacyClass.DEVICE_LOCAL_ONLY)
            val key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
            // Actual JDK crypto on disposable test keys, not the owner's Android key.
            val signer = object : IdentitySigner {
                override fun sign(payload: ByteArray): ByteArray = Signature.getInstance("Ed25519").run {
                    initSign(key.private); update(payload); sign()
                }
                override fun verify(payload: ByteArray, signature: ByteArray): Boolean = Signature.getInstance("Ed25519").run {
                    initVerify(key.public); update(payload); verify(signature)
                }
            }
            val runtime = ContinuumRuntimeStore(File(dir, "state"), signer)
            val initial = runtime.openOrCreate()
            val state = runtime.checkpoint(initial, memories = listOf(memory))
            val episodes = InMemoryExperienceStore()
            val receipts = FileToolExecutionReceiptStore(File(dir, "receipts"))
            var actualRuns = 0
            var actualOutput = ""
            val started = System.currentTimeMillis()
            val result = BoundedAgentOrchestrator(
                model = plan, memories = { state.archive.memories },
                toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { call ->
                    assertEquals("get_storage_info", call.name)
                    actualRuns++
                    DeviceStorageProbe.read(dir).also { actualOutput = it }
                }, receiptStore = receipts),
                evaluator = plan, experienceStore = episodes,
                limits = AgentLimits(maxSteps = 16, maxToolCalls = 1, maxRetries = 0),
                callIdPrefix = "native-storage-e2e"
            ).run(request)
            val finished = System.currentTimeMillis()
            assertNull(result.blocker)
            assertEquals(1, actualRuns)
            assertEquals("Speicher prüfen:\n$actualOutput", result.response)
            assertEquals(ExperienceOutcome.SUCCESS, result.experience!!.outcome)
            assertEquals(listOf(memory.id), result.experience.memoryIds)
            assertEquals(listOf("get_storage_info"), result.experience.toolIds)
            assertEquals(listOf("deterministic-read-only-plan-v1"), result.experience.modelIds)
            val digest = MessageDigest.getInstance("SHA-256").digest(actualOutput.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val receipt = FileToolExecutionReceiptStore(File(dir, "receipts")).get("native-storage-e2e-1")!!
            assertEquals(ToolReceiptState.SUCCEEDED, receipt.state)
            assertEquals(digest, receipt.outputSha256)
            assertEquals(listOf("tool-call:native-storage-e2e-1:sha256:$digest"), result.experience.sourceIds)
            // Independent JDK FileStore oracle for the actual same host volume.
            val total = Regex("Gesamt: (\\d+) Byte").find(actualOutput)!!.groupValues[1].toLong()
            val available = Regex("Für die App nutzbar: (\\d+) Byte").find(actualOutput)!!.groupValues[1].toLong()
            val observedAt = Regex("Zeitpunkt: (\\d+)").find(actualOutput)!!.groupValues[1].toLong()
            assertEquals(Files.getFileStore(dir.toPath()).totalSpace, total)
            assertTrue(available in 0..total)
            assertTrue(observedAt in started..finished)
            val committed = runtime.checkpoint(state, experiences = episodes.all())
            val restored = ContinuumRuntimeStore(File(dir, "state"), signer).openOrCreate()
            assertEquals(committed.seed.currentStateRoot, restored.seed.currentStateRoot)
            assertEquals(listOf(memory), restored.archive.memories) // no inferred owner fact
            assertEquals(episodes.all(), restored.archive.experiences)
            assertTrue(ExperienceCompiler().compile(restored.archive.experiences).isEmpty())
            System.getProperty("continuum.action.evidenceDir")?.let { output ->
                File(output, "actual-storage.txt").writeText(actualOutput + "\n")
                File(output, "action-receipt.txt").writeText(
                    "request=$request\ncallId=${receipt.callId}\nstate=${receipt.state}\nsha256=$digest\n" +
                    "experience=${result.experience.id}\noutcome=${result.experience.outcome}\n" +
                    "source=${result.experience.sourceIds.single()}\nstateRoot=${restored.seed.currentStateRoot}\n" +
                    "executions=$actualRuns\npersistedAfterRestart=true\nownerFactsUnchanged=true\nweightsUpdated=false\n")
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun deniedPermissionAndDuplicateReceiptNeverConfirmAnObservation() {
        val dir = Files.createTempDirectory("continuum-action-denials").toFile()
        try {
            val proposal = ToolProposal("get_storage_info", emptyMap())
            val receipts = FileToolExecutionReceiptStore(dir)
            var calls = 0
            fun run(enabled: Boolean, prefix: String): AgentRunResult {
                val plan = ReadOnlyToolPlan(proposal)
                return BoundedAgentOrchestrator(plan, { emptyList() },
                    ToolGate(ToolPolicy(readOnlyEnabled = enabled), ToolRunner { calls++; DeviceStorageProbe.read(dir) }, receiptStore = receipts),
                    plan, InMemoryExperienceStore(), callIdPrefix = prefix,
                    limits = AgentLimits(maxRetries = 0, maxToolCalls = 1)).run("Prüfe den Speicher")
            }
            assertNotNull(run(false, "denied").blocker)
            assertEquals(0, calls)
            assertNull(run(true, "once").blocker)
            val replay = run(true, "once")
            assertNotNull(replay.blocker)
            assertEquals(1, calls)
            assertEquals(ExperienceOutcome.FAILURE, replay.experience!!.outcome)
            assertTrue(replay.experience.sourceIds.isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun cancellationAndMissingVolumeProduceNoPositiveEpisode() {
        val dir = Files.createTempDirectory("continuum-action-cancel").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) { DeviceStorageProbe.read(File(dir, "missing")) }
            val plan = ReadOnlyToolPlan(ToolProposal("get_storage_info", emptyMap()))
            val episodes = InMemoryExperienceStore()
            val cancellation = DeliberationCancellation().also { it.cancel() }
            assertThrows(java.util.concurrent.CancellationException::class.java) {
                BoundedAgentOrchestrator(plan, { emptyList() },
                    ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { error("cancelled") }),
                    plan, episodes, cancellation = cancellation).run("Prüfe den Speicher")
            }
            assertTrue(episodes.all().isEmpty())
            val failed = BoundedAgentOrchestrator(
                plan, { emptyList() }, ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { throw java.io.IOException("probe failed") }),
                plan, episodes, limits = AgentLimits(maxRetries = 0)).run("Prüfe den Speicher")
            assertNotNull(failed.blocker)
            assertEquals(ExperienceOutcome.FAILURE, failed.experience!!.outcome)
            assertTrue(failed.experience.sourceIds.isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun memoriesCannotAuthorizeChangesAndForgedResponseCannotPassEvaluation() {
        assertThrows(IllegalArgumentException::class.java) { ReadOnlyToolPlan(ToolProposal("delete_file", mapOf("path" to "/sdcard/Download/x"))) }
        assertThrows(IllegalArgumentException::class.java) { ReadOnlyToolPlan(ToolProposal("run_shell", mapOf("command" to "id"))) }
        assertThrows(IllegalArgumentException::class.java) { ReadOnlyToolPlan(ToolProposal("get_storage_info", mapOf("path" to "/private"))) }
        val plan = ReadOnlyToolPlan(ToolProposal("get_storage_info", emptyMap()))
        val record = ToolExecutionRecord("test-1", "get_storage_info", Risk.READ_ONLY, emptyMap(), "allowed", ToolExecutionStatus.SUCCEEDED, 1, 6)
        assertFalse(plan.evaluate("invented success", record).success)
        val response = plan.generate("Prüfe Speicher", emptyList(), null,
            listOf(AgentToolObservation("get_storage_info", ToolExecutionStatus.SUCCEEDED, "result")))
        assertTrue(plan.evaluate(response.response, record).success)
        assertFalse(plan.evaluate(response.response + " invented", record).success)
        assertFalse(plan.evaluate(response.response, record.copy(status = ToolExecutionStatus.REJECTED)).success)
        assertFalse(plan.evaluate(response.response, record.copy(toolName = "get_device_info")).success)
        assertFalse(plan.evaluate(response.response, record.copy(outputLength = 0)).success)
    }
}
