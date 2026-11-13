package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ContinuumRuntimeStoreTest {
    @Test
    fun genesisAndCheckpointSurviveRestart() {
        val dir = Files.createTempDirectory("continuum-runtime").toFile()
        try {
            val signer = TestSigner()
            val firstStore = store(dir, signer)
            val first = firstStore.openOrCreate()
            assertEquals(1L, first.manifest.generation)

            val memory = MemoryRecord(
                id = "memory-1",
                type = MemoryType.SEMANTIC,
                content = "Continuum keeps its identity when the model changes.",
                provenance = ProvenanceType.USER_PROVIDED,
                confidence = 0.9,
                createdAt = 20,
                updatedAt = 20,
                privacyClass = PrivacyClass.PRIVATE
            )
            val updated = firstStore.checkpoint(first, memories = listOf(memory))
            assertEquals(2L, updated.manifest.generation)

            val restarted = store(dir, signer).openOrCreate()
            assertEquals(updated.seed.currentStateRoot, restarted.seed.currentStateRoot)
            assertEquals(listOf("memory-1"), restarted.archive.memories.map { it.id })
            assertEquals(updated.archive.identityId, restarted.archive.identityId)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun experienceArchiveSurvivesRestartAndCanFeedBoundedRecall() {
        val dir = Files.createTempDirectory("continuum-runtime-experience").toFile()
        try {
            val signer = TestSigner()
            val firstStore = store(dir, signer)
            val first = firstStore.openOrCreate()
            val experience = Experience(
                id = "experience-1",
                taskType = "agent_task",
                requestSummary = "repair battery service",
                sourceIds = listOf("tool-call:agent-1:sha256:" + "a".repeat(64)),
                toolIds = listOf("get_device_info"),
                outcome = ExperienceOutcome.SUCCESS,
                confidenceAfter = .95,
                startedAt = 80,
                completedAt = 90,
                evaluation = Evaluation(true, .95)
            )
            firstStore.checkpoint(first, experiences = listOf(experience))

            val restarted = store(dir, signer).openOrCreate()
            assertEquals(listOf("experience-1"), restarted.archive.experiences.map { it.id })

            val cache = InMemoryExperienceStore()
            restarted.archive.experiences.forEach(cache::store)
            val hints = AgentExperienceRecall.hints(cache, "repair battery service", nowMillis = 100)
            assertEquals(1, hints.size)
            assertTrue("success-hint" in hints.single().tags)
            assertEquals(ValidationState.UNVERIFIED, hints.single().validationState)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun eligibleExperienceSurvivesRestartBehindNewerUnverifiedTurns() {
        val dir = Files.createTempDirectory("continuum-runtime-recall-eligibility").toFile()
        try {
            val signer = TestSigner()
            val runtime = store(dir, signer)
            val initial = runtime.openOrCreate()
            val success = Experience(
                id = "older-success", taskType = "agent_task",
                requestSummary = "repair battery service extra detail",
                sourceIds = listOf("tool-call:probe:sha256:" + "a".repeat(64)),
                toolIds = listOf("get_device_info"), outcome = ExperienceOutcome.SUCCESS,
                startedAt = 70, completedAt = 80, evaluation = Evaluation(true, 0.95)
            )
            val failure = success.copy(id = "older-failure", completedAt = 81,
                outcome = ExperienceOutcome.FAILURE, evaluation = Evaluation(false, 0.1),
                failures = listOf("Observed verification failure"))
            val unverified = List(96) { index ->
                success.copy(id = "unverified-$index", requestSummary = "repair battery service",
                    completedAt = 90, outcome = ExperienceOutcome.PARTIAL)
            }
            runtime.checkpoint(initial, experiences = listOf(success, failure) + unverified)

            val restarted = store(dir, signer).openOrCreate()
            val cache = InMemoryExperienceStore()
            restarted.archive.experiences.forEach(cache::store)
            val hints = AgentExperienceRecall.hints(cache, "repair battery service", nowMillis = 100)

            assertEquals(98, restarted.archive.experiences.size)
            assertEquals(listOf(80L, 81L), hints.map { it.updatedAt })
            assertEquals(1, hints.count { "success-hint" in it.tags })
            assertEquals(1, hints.count { "failure-warning" in it.tags })
            assertTrue(hints.all { it.validationState == ValidationState.UNVERIFIED })
            assertTrue(hints.all { it.provenance == ProvenanceType.MODEL_GENERATED })
            assertEquals(98, cache.all().size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun processDeathBetweenArchiveAndSeedUsesMatchingLkgArchive() {
        val dir = Files.createTempDirectory("continuum-runtime-reconcile").toFile()
        try {
            val signer = TestSigner()
            val runtime = store(dir, signer)
            val genesis = runtime.openOrCreate()
            val committed = runtime.checkpoint(
                genesis,
                memories = listOf(memory("committed", 20))
            )

            // Simulate a crash after CognitiveArchiveStore.save() but before the
            // Continuum seed/state-root commit. This advances the mutable archive
            // while leaving the signed seed on the previous state root.
            val archiveStore = CognitiveArchiveStore(
                File(dir, "cognitive"),
                committed.seed.instanceId.value
            )
            archiveStore.save(
                committed.archive.copy(
                    memories = committed.archive.memories + memory("uncommitted", 30)
                )
            )

            val recovered = store(dir, signer).openOrCreate()
            assertEquals(ArchiveLoadSource.LAST_KNOWN_GOOD, recovered.archiveLoadSource)
            assertEquals(listOf("committed"), recovered.archive.memories.map { it.id })
            assertTrue(recovered.manifest.archiveSha256.isNotBlank())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun store(dir: File, signer: IdentitySigner) = ContinuumRuntimeStore(
        baseDirectory = dir,
        signer = signer,
        ownerIdentityReference = "owner:test",
        localNodeId = ComputeNodeId("test-local"),
        nowMillis = { 100 },
        instanceIdFactory = { ContinuumInstanceId("continuum-runtime-test") }
    )

    private fun memory(id: String, time: Long) = MemoryRecord(
        id = id,
        type = MemoryType.SEMANTIC,
        content = "memory " + id,
        provenance = ProvenanceType.USER_PROVIDED,
        confidence = 0.9,
        createdAt = time,
        updatedAt = time,
        privacyClass = PrivacyClass.PRIVATE
    )

    private class TestSigner : IdentitySigner {
        private val secret = "runtime-secret".toByteArray()
        override fun sign(payload: ByteArray): ByteArray = sha256Bytes(secret + payload)
        override fun verify(payload: ByteArray, signature: ByteArray): Boolean =
            sign(payload).contentEquals(signature)
    }
}
