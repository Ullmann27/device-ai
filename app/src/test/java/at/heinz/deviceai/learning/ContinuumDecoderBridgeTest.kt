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

    @Test fun symlinkedBindingIsNeverTrustedOrOverwritten() {
        val dir=Files.createTempDirectory("continuum-dual-core-symlink").toFile()
        try {
            val real=File(dir,"external-state")
            real.writeText("not a model")
            val link=File(dir,"context-decoder-paired.swb1-binding")
            Files.createSymbolicLink(link.toPath(),real.toPath())
            val swb1=TrainableSubwordEncoder().save()
            val decoder=TinyContextDecoder().save()
            val check=ContinuumPairedEncoderBinding.inspect(link,decoder,swb1)
            assertEquals(ContinuumPairedEncoderBinding.State.CORRUPT,check.state)
            assertThrows(IllegalArgumentException::class.java) {
                ContinuumPairedEncoderBinding.write(link,decoder,swb1)
            }
            assertEquals("not a model",real.readText())
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


    @Test fun unreadableLegacyWeightsAreReportedCorruptAndCannotBeReinitialized() {
        val dir=Files.createTempDirectory("continuum-legacy-corrupt").toFile()
        try {
            val legacy=File(dir,"context-decoder-paired.cdx")
            val original="damaged saved weights".toByteArray(Charsets.UTF_8)
            legacy.writeBytes(original)
            val bridge=ContinuumDecoderBridge(dir)
            val swb1=TrainableSubwordEncoder().save()
            val status=bridge.pairedStatus(swb1)
            assertFalse(status.available)
            assertEquals("CORRUPT",status.conditioningState)
            assertNull(bridge.experimentalPairedContinuation(
                "Robot A steht in Wien.","Wo steht Robot A?",swb1))
            val train=DecoderEvidenceCorpus(listOf(
                DecoderEvidencePair("Robot A steht in Wien.","Wo steht Robot A?","Wien")
            ),DecoderSplit.TRAIN)
            val audit=DecoderEvidenceCorpus(listOf(
                DecoderEvidencePair("Robot B steht in Graz.","Wo steht Robot B?","Graz")
            ),DecoderSplit.AUDIT)
            assertThrows(IllegalStateException::class.java) {
                bridge.trainPairedExperiment(train,audit,swb1,steps=5)
            }
            assertArrayEquals(original,legacy.readBytes())
            assertFalse(File(dir,"context-decoder-paired.bundle").exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun conservativeResearchRetryCannotBypassProtectedAuditsOrCommitOnCancellation() {
        val dir=Files.createTempDirectory("continuum-cdx-retry-guard").toFile()
        try {
            val bridge=ContinuumDecoderBridge(dir)
            val training=DecoderEvidenceCorpus(listOf(
                DecoderEvidencePair("Person P11 wohnt in Graz.","Wo wohnt P11?","P11 wohnt in Graz.")
            ),DecoderSplit.TRAIN)
            val audit=DecoderEvidenceCorpus(listOf(
                DecoderEvidencePair("Person Q21 wohnt in Linz.","Wo wohnt Q21?","Q21 wohnt in Linz.")
            ),DecoderSplit.AUDIT)
            val protected=DecoderEvidenceCorpus(listOf(
                DecoderEvidencePair("Person Z31 ist Tischlerin.","Was arbeitet Z31?","Z31 ist Tischlerin.")
            ),DecoderSplit.AUDIT)
            val encoder=TrainableSubwordEncoder().save()
            val illegalRates=listOf(Double.NaN,Double.POSITIVE_INFINITY,0.0,0.081)
            illegalRates.forEach { rate ->
                assertThrows(IllegalArgumentException::class.java) {
                    bridge.trainPairedExperiment(training,audit,encoder,steps=8,
                        protectedAudits=listOf(protected),learningRate=rate)
                }
            }
            val leaked=DecoderEvidenceCorpus(listOf(
                DecoderEvidencePair(training.pairs.first().evidence,
                    training.pairs.first().question,"geheim")
            ),DecoderSplit.AUDIT)
            assertThrows(IllegalArgumentException::class.java) {
                bridge.trainPairedExperiment(training,audit,encoder,steps=8,
                    protectedAudits=listOf(leaked),learningRate=.012)
            }
            val stopped=bridge.trainPairedExperiment(training,audit,encoder,
                steps=8,cancelled={true},protectedAudits=listOf(protected),
                learningRate=.012)
            assertFalse(stopped.accepted)
            assertEquals(0,stopped.completedSteps)
            assertFalse(bridge.pairedStatus(encoder).available)
            assertFalse(File(dir,"context-decoder-paired.bundle").exists())
        } finally {dir.deleteRecursively()}
    }

    @Test fun oldCheckpointBackupIsReadableOnlyAndNeverSilentlyPromoted() {
        val dir=Files.createTempDirectory("continuum-legacy-recovery").toFile()
        try {
            val legacy=File(dir,"context-decoder-paired.cdx")
            val backup=File(dir,"context-decoder-paired.cdx.lkg")
            val old=TinyContextDecoder(seed=93L).save()
            val damaged=byteArrayOf(1,2,3,4,5)
            backup.writeBytes(old)
            legacy.writeBytes(damaged)
            val bridge=ContinuumDecoderBridge(dir)
            val swb1=TrainableSubwordEncoder().save()
            val status=bridge.pairedStatus(swb1)
            assertTrue(status.available)
            assertEquals("RECOVERED_LKG",status.conditioningState)
            assertFalse(status.usableForChat)
            val train=DecoderEvidenceCorpus(listOf(
                DecoderEvidencePair("Robot A steht in Wien.","Wo steht Robot A?","Wien")
            ),DecoderSplit.TRAIN)
            val audit=DecoderEvidenceCorpus(listOf(
                DecoderEvidencePair("Robot B steht in Graz.","Wo steht Robot B?","Graz")
            ),DecoderSplit.AUDIT)
            assertThrows(IllegalArgumentException::class.java) {
                bridge.trainPairedExperiment(train,audit,swb1,steps=5)
            }
            assertArrayEquals(damaged,legacy.readBytes())
            assertArrayEquals(old,backup.readBytes())
            assertFalse(File(dir,"context-decoder-paired.bundle").exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun orphanedLegacyEncoderBindingIsNotMistakenForNeverTrainedModel() {
        val dir=Files.createTempDirectory("continuum-orphan-binding").toFile()
        try {
            File(dir,"context-decoder-paired.swb1-binding").writeText("orphan")
            val bridge=ContinuumDecoderBridge(dir)
            assertEquals("CORRUPT",
                bridge.pairedStatus(TrainableSubwordEncoder().save()).conditioningState)
        } finally { dir.deleteRecursively() }
    }

    @Test fun readOnlyStorageInventoryDoesNotRewriteOrRestoreAnyCheckpoint() {
        val dir=Files.createTempDirectory("continuum-cdx-storage-evidence").toFile()
        try {
            val saved=File(dir,"context-decoder-paired.cdx")
            val raw=byteArrayOf(1,4,9,16)
            saved.writeBytes(raw)
            val before=dir.list()?.sorted()
            val report=ContinuumDecoderBridge(dir).storageEvidence()
            assertTrue(report.contains("Alter CDX1-Checkpoint: vorhanden (4 Byte)"))
            assertTrue(report.contains("Alte CDX1-Sicherung: fehlt"))
            assertTrue(report.contains("Neues Paarbundle: fehlt"))
            assertArrayEquals(raw,saved.readBytes())
            assertEquals(before,dir.list()?.sorted())
        } finally { dir.deleteRecursively() }
    }
}
