package at.heinz.deviceai.learning

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class DecoderGenerativePromotionTest {
    @Test fun lowerTeacherForcedLossCannotPublishIncorrectFreeAnswers() {
        val directory = Files.createTempDirectory("cdx1-free-answer-gate").toFile()
        try {
            val bridge = ContinuumDecoderBridge(directory)
            val encoder = TrainableSubwordEncoder().save()
            val train = DecoderEvidenceCorpus(listOf(
                DecoderEvidencePair("Anna wohnt in Graz.", "Wo wohnt Anna?", "Graz")
            ), DecoderSplit.TRAIN)
            val audit = DecoderEvidenceCorpus(listOf(
                DecoderEvidencePair("Berta wohnt in Linz.", "Wo wohnt Berta?", "Linz")
            ), DecoderSplit.AUDIT)
            val result = bridge.trainPairedExperiment(train, audit, encoder, steps = 120)
            assertEquals(120, result.completedSteps)
            assertTrue("Reproduces the misleading teacher-forced improvement",
                result.trainingAfter < result.trainingBefore && result.auditAfter < result.auditBefore)
            assertFalse("An inaccurate free generator must not become the active checkpoint", result.accepted)
            assertFalse(bridge.pairedStatus(encoder).available)
            assertFalse(directory.resolve("context-decoder-paired.bundle").exists())
        } finally { directory.deleteRecursively() }
    }

    @Test fun firstByteCannotDistinguishQuestionsWithoutConditioning() {
        val decoder = TinyContextDecoder()
        val first = decoderEvidencePrefix("Anna wohnt in Graz.", "Wo wohnt Anna?")
        val second = decoderEvidencePrefix("Berta wohnt in Linz.", "Wo wohnt Berta?")
        assertEquals("ntwort: ", first.takeLast(TinyContextDecoder.CONTEXT))
        assertArrayEquals(decoder.nextTokenProbabilities(first),
            decoder.nextTokenProbabilities(second), 0.0)
    }
}
