package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ContinuumDecoderBridgeTest {
    @Test fun emptyBridgeDoesNotExposeChatOrClaimTraining() {
        val dir = Files.createTempDirectory("continuum-bridge-empty").toFile()
        try {
            val bridge = ContinuumDecoderBridge(dir)
            assertFalse(bridge.status().available)
            assertFalse(bridge.status().usableForChat)
            assertNull(bridge.experimentalContinuation("hallo", TrainableSubwordEncoder().save()))
        } finally { dir.deleteRecursively() }
    }

    @Test fun rejectsAuditLeakageAndKeepsStateUnchanged() {
        val dir = Files.createTempDirectory("continuum-bridge-audit").toFile()
        try {
            val bridge = ContinuumDecoderBridge(dir)
            val corpus = DecoderCorpus(listOf("hallo welt"), DecoderSplit.TRAIN)
            val audit = DecoderCorpus(listOf("hallo welt"), DecoderSplit.AUDIT)
            assertThrows(IllegalArgumentException::class.java) {
                bridge.trainExperiment(corpus, audit, TrainableSubwordEncoder().save())
            }
            assertFalse(bridge.status().available)
        } finally { dir.deleteRecursively() }
    }

    @Test fun cancellationNeverPromotesCandidate() {
        val dir = Files.createTempDirectory("continuum-bridge-cancel").toFile()
        try {
            val bridge = ContinuumDecoderBridge(dir)
            val result = bridge.trainExperiment(
                DecoderCorpus(listOf("hallo welt"), DecoderSplit.TRAIN),
                DecoderCorpus(listOf("hallo test"), DecoderSplit.AUDIT),
                TrainableSubwordEncoder().save(), cancelled = { true }
            )
            assertFalse(result.accepted)
            assertEquals(0, result.completedSteps)
            assertFalse(bridge.status().available)
        } finally { dir.deleteRecursively() }
    }
}
