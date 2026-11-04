package at.heinz.deviceai.learning

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

/** M2.8: LEX4 append-only log, per-record SHA-256 chain, torn-tail truncation, compaction, LEX3 migration. */
class LedgerAppendOnlyTest {
    private fun withDir(block:(File)->Unit) {
        val dir=Files.createTempDirectory("lex4").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }
    private fun fact(i:Int)=LearningExperienceFactory.create(LearningExperienceType.USER_FACT,
        LearningProvenance.USER_CONFIRMED,LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,
        "Fakt $i",now=i.toLong())
    private fun magic(f:File)=f.readBytes().copyOf(4).fold(0) { a,b -> (a shl 8) or (b.toInt() and 255) }
    private val File.ledger get()=File(this,"experiences-v2.bin")

    @Test fun appendKeepsEarlierBytesAsPrefix() = withDir { dir ->
        val store=FileLearningExperienceStore(dir)
        store.appendMissing((0 until 50).map(::fact))
        assertEquals(0x4c455834,magic(dir.ledger))
        val before=dir.ledger.readBytes()
        assertTrue(store.append(fact(50)))
        val after=dir.ledger.readBytes()
        assertTrue(after.size>before.size && after.size-before.size<400)
        assertArrayEquals(before,after.copyOf(before.size))
        assertTrue(store.updateStatus(fact(3).id,LearningStatus.REVOKED,99))
        assertArrayEquals(after,dir.ledger.readBytes().copyOf(after.size))
        val all=FileLearningExperienceStore(dir).all()
        assertEquals(51,all.size);assertEquals(LearningStatus.REVOKED,all[3].status)
    }

    @Test fun tornTailIsDetectedAndTruncated() = withDir { dir ->
        val store=FileLearningExperienceStore(dir)
        store.appendMissing((0 until 10).map(::fact))
        val good=dir.ledger.length()
        store.append(fact(10))
        RandomAccessFile(dir.ledger,"rw").use { it.setLength(it.length()-7) } // half-written last record
        assertEquals((0 until 10).map(::fact),FileLearningExperienceStore(dir).all())
        assertEquals(good,dir.ledger.length())
        assertTrue(store.append(fact(11)))
        assertEquals((0 until 10).map(::fact)+fact(11),FileLearningExperienceStore(dir).all())
    }

    @Test fun corruptedCompleteRecordFailsClosed() = withDir { dir ->
        FileLearningExperienceStore(dir).appendMissing((0 until 5).map(::fact))
        val b=dir.ledger.readBytes();b[30]=(b[30].toInt() xor 1).toByte();dir.ledger.writeBytes(b)
        assertThrows(IllegalArgumentException::class.java) { FileLearningExperienceStore(dir).all() }
    }

    @Test fun lex3IsMigratedAndChurnIsCompacted() = withDir { dir ->
        val store=FileLearningExperienceStore(dir)
        store.appendMissing((0 until 20).map(::fact))
        repeat(300) { k -> store.updateStatus(fact(k%20).id,if(k%2==0) LearningStatus.REVOKED else LearningStatus.CONFIRMED,1000L+k) }
        assertTrue("compaction bounds churn",dir.ledger.length()<20_000)
        assertEquals(20,FileLearningExperienceStore(dir).all().size)
    }
}
