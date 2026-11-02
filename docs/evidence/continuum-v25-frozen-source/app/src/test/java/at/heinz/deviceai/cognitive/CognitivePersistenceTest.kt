package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64

class CognitivePersistenceTest {
    @Test fun archiveRoundTripsMemoriesAndExperiencesDeterministically() {
        val dir = Files.createTempDirectory("cognitive-archive").toFile()
        try {
            val store = CognitiveArchiveStore(dir, "identity-1")
            val archive = archive("one")
            val exported = store.export(archive)
            assertEquals(exported, store.export(archive))
            assertEquals(archive, store.import(exported))
            store.save(archive)
            assertEquals(ArchiveLoadResult(archive, ArchiveLoadSource.PRIMARY), store.load())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun corruptPrimaryRecoversLastKnownGoodArchive() {
        val dir = Files.createTempDirectory("cognitive-recovery").toFile()
        try {
            val store = CognitiveArchiveStore(dir, "identity-1")
            val first = archive("first")
            val second = archive("second")
            store.save(first)
            store.save(second)
            File(dir, "cognitive-state.bin").writeBytes(byteArrayOf(1, 2, 3))
            assertEquals(ArchiveLoadResult(first, ArchiveLoadSource.LAST_KNOWN_GOOD), store.load())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun importRejectsCorruptionSchemaIdentityAndInvalidRanges() {
        val dir = Files.createTempDirectory("cognitive-invalid").toFile()
        try {
            val store = CognitiveArchiveStore(dir, "identity-1")
            val encoded = store.export(archive("valid"))
            assertThrows(IllegalArgumentException::class.java) { store.import("not base64!") }
            assertThrows(IllegalArgumentException::class.java) {
                CognitiveArchiveStore(dir, "another-identity").import(encoded)
            }
            val bytes = Base64.getDecoder().decode(encoded)
            val versionOffset = 2 + "DEVICE_AI_COGNITIVE_ARCHIVE".toByteArray().size
            bytes[versionOffset + 3] = 2
            assertThrows(IllegalArgumentException::class.java) {
                store.import(Base64.getEncoder().encodeToString(bytes))
            }
            assertThrows(IllegalArgumentException::class.java) {
                MemoryRecord(
                    "invalid", MemoryType.SEMANTIC, "text", ProvenanceType.USER_PROVIDED,
                    Double.NaN, 1, 1
                )
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun archiveRejectsDuplicateIdsAndStoreRejectsIdentityMismatch() {
        val dir = Files.createTempDirectory("cognitive-identity").toFile()
        try {
            val store = CognitiveArchiveStore(dir, "identity-1")
            val valid = archive("duplicate")
            assertThrows(IllegalArgumentException::class.java) {
                valid.copy(memories = listOf(valid.memories.single(), valid.memories.single()))
            }
            assertThrows(IllegalArgumentException::class.java) {
                val otherGenome = CognitiveGenome("identity-2")
                store.save(valid.copy(
                    identityState = IdentityState.initial(otherGenome),
                    genome = otherGenome
                ))
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun archive(suffix: String) = CognitiveArchive(
        identityState = identityState(),
        genome = CognitiveGenome("identity-1"),
        memories = listOf(
            MemoryRecord(
                id = "memory-$suffix",
                type = MemoryType.SEMANTIC,
                content = "A private remembered fact",
                provenance = ProvenanceType.USER_PROVIDED,
                confidence = 0.8,
                createdAt = 10,
                updatedAt = 20,
                tags = listOf("fact", "private"),
                parentIds = listOf("parent"),
                sourceReference = "source-1",
                validationState = ValidationState.CORROBORATED,
                privacyClass = PrivacyClass.PRIVATE,
                validationEvidenceReferences = listOf("corroborating-source")
            )
        ),
        experiences = listOf(
            Experience(
                id = "experience-$suffix",
                taskType = "coding",
                requestSummary = "fix parser",
                memoryIds = listOf("memory-$suffix"),
                modelIds = listOf("mock"),
                sourceIds = listOf("docs"),
                toolIds = listOf("unit-test"),
                planSummary = "inspect, patch, test",
                resultSummary = "tests passed",
                failures = listOf("first attempt failed"),
                retries = 1,
                evaluatorScores = mapOf("quality" to 0.9),
                feedback = "clear",
                confidenceBefore = 0.4,
                confidenceAfter = 0.8,
                outcome = ExperienceOutcome.SUCCESS,
                startedAt = 100,
                completedAt = 200,
                evaluation = Evaluation(true, 0.9, "passed")
            )
        )
    )

    private fun identityState(): IdentityState {
        val initial = IdentityState.initial(CognitiveGenome("identity-1"))
        return initial.evolve { state ->
            state.copy(
                chosenName = "Nova",
                communicationProfile = mapOf("style" to "concise"),
                behaviorProfile = state.behaviorProfile.with(BehaviorTrait.CURIOSITY, 0.7),
                modelReputation = mapOf("mock" to 0.8),
                sourceReputation = mapOf("docs.example" to 0.75),
                selfModel = state.selfModel.withEvidence(
                    CapabilityEvidence("task-1", Capability.CODING, 0.8, 0.9, "unit-test", 10),
                    0.2
                ),
                preferredProblemSolvingMethods = listOf("test-first"),
                skillRegistryRoot = "skills-root",
                memoryRoot = "memory-root",
                experienceRoot = "experience-root"
            )
        }
    }
}
