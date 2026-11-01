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

    @Test fun qualifyingValidationStillRejectsLossImprovementAndKeepsExistingCheckpoint() {
        val directory = Files.createTempDirectory("cdx1-qualified-free-gate").toFile()
        try {
            val encoder = TrainableSubwordEncoder().save()
            val previous = TinyContextDecoder().save()
            val file = directory.resolve("context-decoder-paired.cdx")
            file.writeBytes(previous)
            ContinuumPairedEncoderBinding.write(directory.resolve("context-decoder-paired.swb1-binding"), previous, encoder)
            val train = DecoderEvidenceCorpus(listOf(
                DecoderEvidencePair("Anna wohnt in Graz.", "Wo wohnt Anna?", "Graz")
            ), DecoderSplit.TRAIN)
            val audit = DecoderEvidenceCorpus((1..20).map {
                DecoderEvidencePair("Person B$it wohnt in Graz.", "Wo wohnt B$it?", "Graz")
            } + (1..5).map {
                DecoderEvidencePair("Person C$it wohnt in Graz.", "Welche Farbe mag C$it?", "unbekannt")
            }, DecoderSplit.AUDIT)
            assertTrue(DecoderGenerativeGate.hasControls(audit))
            val result = ContinuumDecoderBridge(directory).trainPairedExperiment(train, audit, encoder, steps = 120)
            assertTrue(result.trainingAfter < result.trainingBefore)
            assertTrue(result.auditAfter < result.auditBefore)
            assertEquals("FREE_GENERATION_REJECTED", result.generationGate)
            assertEquals(0, result.generationAfter!!.correctSupported)
            assertFalse(result.accepted)
            assertArrayEquals(previous, file.readBytes())
            assertFalse(directory.resolve("context-decoder-paired.bundle").exists())
        } finally { directory.deleteRecursively() }
    }
}
