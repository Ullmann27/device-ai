package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class TinyContextDecoderTest {
    private val training=DecoderCorpus(listOf("hallo welt", "hallo heinz", "hallo dan", "lernen macht freude", "lernen braucht zeit"),DecoderSplit.TRAIN)
    private val holdout=DecoderCorpus(listOf("hallo lerner", "lernen macht sinn"),DecoderSplit.AUDIT)

    @Test fun learnsNextBytesAndImprovesUntrainedAuditWithoutPretrainedWeights() {
        val model=TinyContextDecoder(91)
        val before=model.evaluate(training)
        val auditBefore=model.evaluate(holdout)
        val report=model.train(training,steps=6000,maxMillis=5000)
        assertEquals(6000,report.steps)
        assertFalse(report.interrupted)
        assertTrue("training loss",model.evaluate(training)<before*.65)
        assertTrue("untrained audit loss",model.evaluate(holdout)<auditBefore)
        assertEquals(7746,model.parameterCount)
        assertEquals(6000,model.updates)
        assertThrows(IllegalArgumentException::class.java) { model.train(holdout) }
    }

    @Test fun deterministicTrainingGenerationAndChecksummedSnapshotRoundTrip() {
        val a=TinyContextDecoder(91);val b=TinyContextDecoder(91)
        a.train(training,steps=1500,maxMillis=5000);b.train(training,steps=1500,maxMillis=5000)
        assertArrayEquals(a.save(),b.save())
        val restored=TinyContextDecoder.restore(a.save())
        assertArrayEquals(a.save(),restored.save())
        assertEquals(a.generate("hallo ",32),restored.generate("hallo ",32))
        val damaged=a.save().also { it[80]=(it[80].toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) { TinyContextDecoder.restore(damaged) }
        assertThrows(IllegalArgumentException::class.java) { TinyContextDecoder.restore(a.save()+byteArrayOf(0)) }
    }

    @Test fun conditioningAcceptsSharedEncoderRepresentationAndBudgetsBoundWork() {
        val encoder=TrainableSubwordEncoder()
        val condition=encoder.representation("sprache lernen")
        assertEquals(16,condition.size)
        val model=TinyContextDecoder()
        val zero=model.nextTokenProbabilities("hallo")
        val conditioned=model.nextTokenProbabilities("hallo",condition)
        assertTrue(zero.indices.any { kotlin.math.abs(zero[it]-conditioned[it])>1e-9 })
        assertEquals(1.0,conditioned.sum(),1e-12)
        val before=model.save()
        val cancelled=model.train(training,steps=100,cancelled={true})
        assertEquals(0,cancelled.steps);assertTrue(cancelled.interrupted)
        assertArrayEquals(before,model.save())
        assertTrue(model.generate("hallo",8).bytesGenerated<=8)
        assertThrows(IllegalArgumentException::class.java) { model.generate("x",129) }
        assertThrows(IllegalArgumentException::class.java) { model.nextTokenProbabilities("x",DoubleArray(16) { Double.NaN }) }
    }
    @Test fun fileSnapshotsKeepExactLastKnownGoodWeights() {
        val dir=java.nio.file.Files.createTempDirectory("decoder-snapshots").toFile()
        try {
            val file=java.io.File(dir,"context-decoder.cdx")
            val model=TinyContextDecoder()
            val initial=model.save()
            model.saveTo(file)
            model.train(training,steps=300,maxMillis=5000)
            model.saveTo(file)
            assertArrayEquals(initial,java.io.File(dir,"context-decoder.cdx.lkg").readBytes())
            assertArrayEquals(model.save(),TinyContextDecoder.restoreFrom(file).save())
        } finally { dir.deleteRecursively() }
    }

    @Test fun committedOwnTrainedSnapshotRestoresAnActualLearnedContinuation() {
        val bytes=java.util.Base64.getDecoder().decode(javaClass.getResourceAsStream("/context-decoder-cdx1.base64")!!.bufferedReader().readText().trim())
        val model=TinyContextDecoder.restore(bytes)
        assertEquals(6000,model.updates)
        assertEquals("welt",model.generate("hallo ",32).text)
        assertArrayEquals(bytes,model.save())
    }

}
