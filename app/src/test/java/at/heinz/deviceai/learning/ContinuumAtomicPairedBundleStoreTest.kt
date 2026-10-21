package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ContinuumAtomicPairedBundleStoreTest {
    @Test fun interruptedPublishNeverLosesPreviouslyVerifiedPair() {
        val dir=Files.createTempDirectory("cdx-atomic-publish").toFile()
        try {
            val store=ContinuumAtomicPairedBundleStore(dir)
            val swb1=TrainableSubwordEncoder(seed=73L).save()
            val decoder=TinyContextDecoder(seed=91L).save()
            val first=store.commit(decoder,swb1,expectedGeneration=0L)
            assertEquals(1L,first.generation)
            var injected=false
            assertThrows(IllegalStateException::class.java) {
                store.commit(TinyContextDecoder(seed=93L).save(),
                    TrainableSubwordEncoder(seed=75L).save(),
                    expectedGeneration=1L,faultBeforePublish={
                        injected=true
                        throw IllegalStateException("simulated process death")
                    })
            }
            assertTrue(injected)
            val survivor=store.loadVerified()!!
            assertEquals(1L,survivor.generation)
            assertFalse(survivor.recoveredFromBackup)
            assertArrayEquals(decoder,survivor.decoder)
            assertArrayEquals(swb1,survivor.encoder)
            assertFalse(File(dir,"context-decoder-paired.bundle.lkg").let {
                it.exists() && it.length()==0L
            })
        } finally { dir.deleteRecursively() }
    }

    @Test fun corruptedActivePairRecoversExactLastKnownGoodButCannotTrainOverCorruption() {
        val dir=Files.createTempDirectory("cdx-atomic-rollback").toFile()
        try {
            val store=ContinuumAtomicPairedBundleStore(dir)
            val original=TinyContextDecoder(seed=91L).save()
            val bound=TrainableSubwordEncoder(seed=73L).save()
            store.commit(original,bound,0L)
            val newer=TinyContextDecoder(seed=131L).save()
            val newerEncoder=TrainableSubwordEncoder(seed=117L).save()
            store.commit(newer,newerEncoder,1L)
            assertEquals(2L,store.loadVerified()!!.generation)
            val file=File(dir,"context-decoder-paired.bundle")
            val damaged=file.readBytes()
            damaged[damaged.size-1]=(damaged.last().toInt() xor 0x01).toByte()
            file.writeBytes(damaged)
            val fallback=store.loadVerified()!!
            assertEquals(1L,fallback.generation)
            assertTrue(fallback.recoveredFromBackup)
            assertArrayEquals(original,fallback.decoder)
            assertArrayEquals(bound,fallback.encoder)
            assertThrows(IllegalArgumentException::class.java) {
                store.commit(TinyContextDecoder(seed=137L).save(),
                    newerEncoder,expectedGeneration=1L)
            }
            val bridge=ContinuumDecoderBridge(dir)
            val status=bridge.pairedStatus(newerEncoder)
            assertEquals("RECOVERED_LKG",status.conditioningState)
            assertFalse(status.usableForChat)
            assertThrows(IllegalArgumentException::class.java) {
                bridge.trainPairedExperiment(
                    DecoderEvidenceCorpus(listOf(DecoderEvidencePair(
                        "X01 steht in Graz.","Wo steht X01?","Graz")),DecoderSplit.TRAIN),
                    DecoderEvidenceCorpus(listOf(DecoderEvidencePair(
                        "Y02 steht in Linz.","Wo steht Y02?","Linz")),DecoderSplit.AUDIT),
                    newerEncoder,steps=3)
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun failedFirstCommitLeavesNoActiveBundleOrLegacyChanges() {
        val dir=Files.createTempDirectory("cdx-atomic-first").toFile()
        try {
            val legacy=File(dir,"context-decoder-paired.cdx")
            val legacyEncoder=File(dir,"context-decoder-paired.swb1-binding")
            val weights=TinyContextDecoder().save()
            val encoder=TrainableSubwordEncoder().save()
            legacy.writeBytes(weights)
            ContinuumPairedEncoderBinding.write(legacyEncoder,weights,encoder)
            val oldBinding=legacyEncoder.readBytes()
            val store=ContinuumAtomicPairedBundleStore(dir)
            assertThrows(IllegalStateException::class.java) {
                store.commit(weights,encoder,0L,faultBeforePublish={
                    throw IllegalStateException("simulated first commit abort")
                })
            }
            assertNull(store.loadVerified())
            assertArrayEquals(weights,legacy.readBytes())
            assertArrayEquals(oldBinding,legacyEncoder.readBytes())
            assertEquals("PINNED_CURRENT",
                ContinuumDecoderBridge(dir).pairedStatus(encoder).conditioningState)
        } finally { dir.deleteRecursively() }
    }

    @Test fun generationIsOptimisticallyLockedAndSymlinkedActiveFileIsRefused() {
        val dir=Files.createTempDirectory("cdx-atomic-lock").toFile()
        try {
            val store=ContinuumAtomicPairedBundleStore(dir)
            val weights=TinyContextDecoder().save()
            val encoder=TrainableSubwordEncoder().save()
            store.commit(weights,encoder,0L)
            assertThrows(IllegalStateException::class.java) {
                store.commit(weights,encoder,expectedGeneration=0L)
            }
            val file=File(dir,"context-decoder-paired.bundle")
            val backup=File(dir,"external.bin")
            backup.writeBytes(file.readBytes())
            assertTrue(file.delete())
            Files.createSymbolicLink(file.toPath(),backup.toPath())
            assertThrows(IllegalStateException::class.java) {
                store.commit(weights,encoder,expectedGeneration=1L)
            }
            assertThrows(IllegalStateException::class.java) {
                store.loadVerified()
            }
        } finally { dir.deleteRecursively() }
    }
}
