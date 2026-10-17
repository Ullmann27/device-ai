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

}
