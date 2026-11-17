package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ContinuumLearningStatusReuseTest {
    private fun withCore(run: (File, ContinuumLearningCore) -> Unit) {
        val directory = Files.createTempDirectory("continuum-status-reuse").toFile()
        try { run(directory, ContinuumLearningCore(directory)) }
        finally { directory.deleteRecursively() }
    }

    @Test fun unchangedTelemetryReusesMeasuredHoldoutsAcrossCollectors() = withCore { directory, core ->
        val first = core.status()
        repeat(12) {
            val next = ContinuumLearningCore(directory).status()
            assertEquals(first, next)
            assertSame("Unchanged weights need no reevaluation", first.intentHoldout, next.intentHoldout)
            assertSame(first.semanticHoldout, next.semanticHoldout)
        }
    }

    @Test fun ownerStateRemainsFreshWhileWeightsStayUnchanged() = withCore { directory, core ->
        val before = core.status()
        assertEquals(OwnerFactChange.ADDED, core.confirmOwnerFact("Dieses Testprojekt heißt Nordlicht."))
        val after = ContinuumLearningCore(directory).status()
        assertEquals(before.experiences.userFacts + 1, after.experiences.userFacts)
        assertSame(before.intentHoldout, after.intentHoldout)
        assertSame(before.semanticHoldout, after.semanticHoldout)
    }

    @Test fun changedBytesInvalidateEvenWithEqualLengthTimeAndUpdateCount() = withCore { directory, core ->
        val file = File(directory, "learning-core-v2/subword-v1.bin")
        assertTrue(file.parentFile.mkdirs() || file.parentFile.isDirectory)
        val original = TrainableSubwordEncoder(71L)
        val replacement = TrainableSubwordEncoder(79L)
        file.writeBytes(original.save())
        val timestamp = file.lastModified()
        val first = core.status()
        assertEquals(original.save().size, replacement.save().size)
        assertEquals(original.updates, replacement.updates)
        file.writeBytes(replacement.save())
        assertTrue(file.setLastModified(timestamp))
        val next = ContinuumLearningCore(directory).status()
        assertEquals(replacement.evaluateIntent(ContinuumLearningCore.INTENT_HOLDOUT), next.intentHoldout)
        assertEquals(replacement.evaluateSemantic(ContinuumLearningCore.SEMANTIC_HOLDOUT), next.semanticHoldout)
        assertNotEquals(first.intentHoldout, next.intentHoldout)
        assertNotSame(first.intentHoldout, next.intentHoldout)
    }

    @Test fun corruptCheckpointCannotReusePreviousMeasurements() = withCore { directory, core ->
        val file = File(directory, "learning-core-v2/subword-v1.bin")
        assertTrue(file.parentFile.mkdirs() || file.parentFile.isDirectory)
        file.writeBytes(TrainableSubwordEncoder(83L).save())
        core.status()
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertThrows(IllegalArgumentException::class.java) { core.status() }
    }

    @Test fun restoredCheckpointImmediatelyMatchesOriginalMeasurements() = withCore { directory, core ->
        val file = File(directory, "learning-core-v2/subword-v1.bin")
        assertTrue(file.parentFile.mkdirs() || file.parentFile.isDirectory)
        val original = TrainableSubwordEncoder(89L).save()
        file.writeBytes(original)
        val first = core.status()
        file.writeBytes(TrainableSubwordEncoder(97L).save())
        val changed = core.status()
        assertNotEquals(first.intentHoldout, changed.intentHoldout)
        file.writeBytes(original)
        val restored = core.status()
        assertEquals(first.intentHoldout, restored.intentHoldout)
        assertEquals(first.semanticHoldout, restored.semanticHoldout)
    }

    @Test fun simultaneousTelemetrySharesMeasurements() = withCore { directory, core ->
        val first = core.status()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = pool.invokeAll(List(24) { Callable { ContinuumLearningCore(directory).status() } })
            futures.forEach { future ->
                val actual = future.get(5, TimeUnit.SECONDS)
                assertEquals(first, actual)
                assertSame(first.intentHoldout, actual.intentHoldout)
                assertSame(first.semanticHoldout, actual.semanticHoldout)
            }
        } finally { pool.shutdownNow() }
    }

    @Test fun evaluationCorpusIsPartOfTheReuseKey() = withCore { _, core ->
        val examples = ContinuumLearningCore.INTENT_HOLDOUT as MutableList<SubwordIntentExample>
        val original = examples[0]
        val first = core.status()
        try {
            examples[0] = original.copy(label = 1 - original.label)
            val next = core.status()
            assertEquals(TrainableSubwordEncoder().evaluateIntent(examples), next.intentHoldout)
            assertNotEquals(first.intentHoldout, next.intentHoldout)
        } finally { examples[0] = original }
        assertEquals(first.intentHoldout, core.status().intentHoldout)
    }
}
