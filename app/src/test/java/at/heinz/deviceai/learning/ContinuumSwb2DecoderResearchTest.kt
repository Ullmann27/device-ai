package at.heinz.deviceai.learning

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ContinuumSwb2DecoderResearchTest {
    @Test fun experimentIsIsolatedAndRejectsLegacyEncoderSnapshots() {
        val dir=File(System.getProperty("java.io.tmpdir"),"swb2-decoder-"+System.nanoTime())
        dir.mkdirs()
        try {
            val bridge=ContinuumSwb2DecoderResearch(dir)
            assertFalse(bridge.status().available)
            val good=Swb2ExperimentalEncoder(73).save()
            assertNull(bridge.experimentalContinuation("hallo",good))
            val train=DecoderCorpus(listOf("Der Stern leuchtet in der Nacht.","Pflanzen benötigen Licht."),DecoderSplit.TRAIN)
            val audit=DecoderCorpus(listOf("Das Licht scheint am Tag."),DecoderSplit.AUDIT)
            val result=bridge.trainExperiment(train,audit,good,steps=10)
            assertTrue(result.completedSteps in 0..10)
            assertTrue(result.auditBefore.isFinite() && result.auditAfter.isFinite())
            val legacy=TrainableSubwordEncoder(73).save()
            assertThrows(Exception::class.java) { bridge.trainExperiment(train,audit,legacy,steps=10) }
            assertThrows(IllegalArgumentException::class.java) { bridge.experimentalContinuation("Hallo",good,maxBytes=120) }
            assertEquals(result.accepted,bridge.status().available)
            assertFalse(File(dir,"context-decoder.cdx").exists())
        } finally { dir.deleteRecursively() }
    }
}
