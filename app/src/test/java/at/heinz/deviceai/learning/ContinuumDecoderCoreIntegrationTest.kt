package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ContinuumDecoderCoreIntegrationTest {
    @Test fun coordinatorExposesDecoderWithoutActivatingProductionChat() {
        val dir = Files.createTempDirectory("decoder-core-integration").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            assertFalse(core.decoderStatus().available)
            assertFalse(core.decoderStatus().usableForChat)
            assertNull(core.experimentalDecoderContinuation("hallo"))
            val report = core.trainExperimentalDecoder(
                DecoderCorpus(listOf("hallo welt"), DecoderSplit.TRAIN),
                DecoderCorpus(listOf("hallo test"), DecoderSplit.AUDIT),
                steps = 10,
                cancelled = { true }
            )
            assertFalse(report.accepted)
            assertEquals(0, report.completedSteps)
            assertFalse(core.decoderStatus().available)
        } finally { dir.deleteRecursively() }
    }
}
