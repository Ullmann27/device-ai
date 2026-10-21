package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.File

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
    @Test fun pairedPrefixContainsEvidenceAndQueryBeforeFirstTargetByte() {
        val pair=DecoderEvidencePair("Lara wohnt in Graz.","Wo wohnt Lara?","In Graz.")
        assertEquals("Beleg: Lara wohnt in Graz.\nFrage: Wo wohnt Lara?\nAntwort: ",pair.prefix())
        assertEquals("Lara wohnt in Graz.\nWo wohnt Lara?",pair.conditionerText())
        assertThrows(IllegalArgumentException::class.java) {
            DecoderEvidencePair("Lara\nfalscher Kontext","Wo?","In Graz.")
        }
    }

    @Test fun pairedTrainingObservesIndividualEvidenceConditions() {
        val decoder=TinyContextDecoder()
        val first=DecoderEvidencePair("Anna wohnt in Linz.","Wo wohnt Anna?","Linz.")
        val second=DecoderEvidencePair("Berta wohnt in Graz.","Wo wohnt Berta?","Graz.")
        val corpus=DecoderEvidenceCorpus(listOf(first,second),DecoderSplit.TRAIN)
        val observed=mutableSetOf<String>()
        val report=decoder.trainEvidencePairs(corpus,steps=8,maxMillis=5000,
            conditionerFor={ pair ->
                observed.add(pair.conditionerText())
                DoubleArray(TinyContextDecoder.CONDITION) {
                    if(pair===first) 0.125 else -0.125
                }
            })
        assertEquals(setOf(first.conditionerText(),second.conditionerText()),observed)
        assertEquals(8,report.steps)
        assertTrue(report.lossAfter.isFinite())
    }

    @Test fun pairedBridgeRejectsInputLeakageAndNeverClaimsChatApproval() {
        val dir=Files.createTempDirectory("continuum-paired-bridge").toFile()
        try {
            val bridge=ContinuumDecoderBridge(dir)
            val pair=DecoderEvidencePair("Lara wohnt in Graz.","Wo wohnt Lara?","In Graz.")
            val leaked=DecoderEvidencePair(pair.evidence,pair.question,"Das ist geheim.")
            val train=DecoderEvidenceCorpus(listOf(pair),DecoderSplit.TRAIN)
            val audit=DecoderEvidenceCorpus(listOf(leaked),DecoderSplit.AUDIT)
            val encoder=TrainableSubwordEncoder().save()
            assertThrows(IllegalArgumentException::class.java) {
                bridge.trainPairedExperiment(train,audit,encoder)
            }
            assertFalse(bridge.pairedStatus().available)
            assertFalse(bridge.pairedStatus().usableForChat)
            assertNull(bridge.experimentalPairedContinuation(pair.evidence,pair.question,encoder))
            val safeAudit=DecoderEvidenceCorpus(listOf(
                DecoderEvidencePair("Nora ist Ärztin.","Was macht Nora?","Sie ist Ärztin.")
            ),DecoderSplit.AUDIT)
            val stopped=bridge.trainPairedExperiment(train,safeAudit,encoder,
                cancelled={true})
            assertFalse(stopped.accepted)
            assertFalse(bridge.pairedStatus().available)
        } finally { dir.deleteRecursively() }
    }

    @Test fun cdx1EncoderPinSurvivesIndependentSwb1Training() {
        val dir=Files.createTempDirectory("continuum-dual-core-binding").toFile()
        try {
            val bridge=ContinuumDecoderBridge(dir)
            val modelFile=File(dir,"context-decoder-paired.cdx")
            TinyContextDecoder(seed=91L).saveTo(modelFile)
            val originalEncoder=TrainableSubwordEncoder(seed=73L).save()
            val updatedEncoder=TrainableSubwordEncoder(seed=101L).save()
            val bindingFile=File(dir,"context-decoder-paired.swb1-binding")
            val originalDecoder=modelFile.readBytes()
            ContinuumPairedEncoderBinding.write(bindingFile,originalDecoder,originalEncoder)

            assertEquals("PINNED_CURRENT",
                bridge.pairedStatus(originalEncoder).conditioningState)
            assertEquals("PINNED_PREVIOUS",
                bridge.pairedStatus(updatedEncoder).conditioningState)
            val evidence="Roboter Z77 steht in Graz."
            val question="Wo steht Z77?"
            val expected=bridge.experimentalPairedContinuation(
                evidence,question,originalEncoder,20)
            val afterSwb1Update=bridge.experimentalPairedContinuation(
                evidence,question,updatedEncoder,20)
            assertNotNull(expected)
            assertEquals(expected,afterSwb1Update)
            // A cancelled update must remain possible with the frozen encoder,
            // even after SWB1's live weights have changed.
            val train=DecoderEvidenceCorpus(listOf(
                DecoderEvidencePair(evidence,question,"Graz")
            ),DecoderSplit.TRAIN)
            val audit=DecoderEvidenceCorpus(listOf(
                DecoderEvidencePair("Roboter Y24 steht in Linz.",
                    "Wo steht Y24?","Linz")
            ),DecoderSplit.AUDIT)
            val report=bridge.trainPairedExperiment(train,audit,updatedEncoder,
                steps=10,cancelled={true})
            assertFalse(report.accepted)
            assertEquals(0,report.completedSteps)
            assertArrayEquals(originalDecoder,modelFile.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun damagedOrMismatchedPinBlocksCdx1WithoutChangingWeights() {
        val dir=Files.createTempDirectory("continuum-dual-core-corruption").toFile()
        try {
            val bridge=ContinuumDecoderBridge(dir)
            val modelFile=File(dir,"context-decoder-paired.cdx")
            TinyContextDecoder().saveTo(modelFile)
            val modelBytes=modelFile.readBytes()
            val swb1=TrainableSubwordEncoder().save()
            val bindingFile=File(dir,"context-decoder-paired.swb1-binding")
            ContinuumPairedEncoderBinding.write(bindingFile,modelBytes,swb1)
            assertEquals("PINNED_CURRENT",bridge.pairedStatus(swb1).conditioningState)
            val bad=TinyContextDecoder(seed=13L).save()
            ContinuumPairedEncoderBinding.write(bindingFile,bad,swb1)
            assertEquals("CORRUPT",bridge.pairedStatus(swb1).conditioningState)
            assertNull(bridge.experimentalPairedContinuation(
                "Roboter A steht in Wien.","Wo steht A?",swb1))
            val damaged=bindingFile.readBytes()
            damaged[8]=(damaged[8].toInt() xor 1).toByte()
            bindingFile.writeBytes(damaged)
            assertEquals("CORRUPT",bridge.pairedStatus(swb1).conditioningState)
            assertThrows(IllegalArgumentException::class.java) {
                bridge.trainPairedExperiment(
                    DecoderEvidenceCorpus(listOf(DecoderEvidencePair(
                        "Roboter A steht in Wien.","Wo steht A?","Wien")),DecoderSplit.TRAIN),
                    DecoderEvidenceCorpus(listOf(DecoderEvidencePair(
                        "Roboter B steht in Linz.","Wo steht B?","Linz")),DecoderSplit.AUDIT),
                    swb1,steps=10)
            }
            assertArrayEquals(modelBytes,modelFile.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun historicalUnboundCheckpointRemainsResearchOnly() {
        val dir=Files.createTempDirectory("continuum-dual-core-legacy").toFile()
        try {
            val modelFile=File(dir,"context-decoder-paired.cdx")
            TinyContextDecoder().saveTo(modelFile)
            val bridge=ContinuumDecoderBridge(dir)
            val swb1=TrainableSubwordEncoder().save()
            val status=bridge.pairedStatus(swb1)
            assertTrue(status.available)
            assertEquals("LEGACY_UNBOUND",status.conditioningState)
            assertFalse(status.usableForChat)
        } finally { dir.deleteRecursively() }
    }

}
