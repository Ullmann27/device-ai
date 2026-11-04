package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/** M2.5: versioned ledger format LEX3 lifts the 256-record cap; LEX2 files are read losslessly and migrated on next write. */
class LedgerFormatV3Test {
    private fun withDir(block:(File)->Unit) {
        val dir=Files.createTempDirectory("lex3").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }
    private fun fact(i:Int)=LearningExperienceFactory.create(LearningExperienceType.USER_FACT,
        LearningProvenance.USER_CONFIRMED,LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,
        "Bestätigter Fakt Nummer $i",now=i.toLong())

    /** Byte-exact copy of the historical LEX2 writer (magic 0x4c455832, no version field). */
    private fun legacyLex2(items:List<LearningExperience>):ByteArray {
        fun str(out:DataOutputStream,v:String){ val b=v.toByteArray(Charsets.UTF_8);out.writeInt(b.size);out.write(b) }
        val payload=ByteArrayOutputStream().also { buf -> DataOutputStream(buf).use { out ->
            out.writeInt(0x4c455832);out.writeInt(items.size)
            items.forEach { e ->
                out.writeUTF(e.id);out.writeUTF(e.type.name);out.writeUTF(e.provenance.name)
                out.writeUTF(e.trust.name);out.writeUTF(e.status.name);str(out,e.content)
                out.writeBoolean(e.annotation!=null);e.annotation?.let { str(out,it) }
                out.writeBoolean(e.sourceRef!=null);e.sourceRef?.let { str(out,it) }
                out.writeUTF(e.contentHash);out.writeLong(e.createdAt);out.writeLong(e.updatedAt)
            }
        } }.toByteArray()
        return payload+MessageDigest.getInstance("SHA-256").digest(payload)
    }
    private fun magic(f:File)=f.readBytes().let { (it[0].toInt() and 255 shl 24) or (it[1].toInt() and 255 shl 16) or (it[2].toInt() and 255 shl 8) or (it[3].toInt() and 255) }

    @Test fun thousandOwnerRecordsRoundTripWithP95() = withDir { dir ->
        assertTrue(FileLearningExperienceStore.MAX_RECORDS>=1000)
        val items=(0 until 1000).map(::fact)
        val store=FileLearningExperienceStore(dir)
        assertEquals(1000,store.appendMissing(items))
        assertEquals(items,FileLearningExperienceStore(dir).all())
        assertEquals(0x4c455833,magic(File(dir,"experiences-v2.bin")))
        val times=(0 until 40).map { val t=System.nanoTime();FileLearningExperienceStore(dir).all();(System.nanoTime()-t)/1e6 }.sorted()
        println("LEX3 1000 records: cold read p95=%.2f ms".format(times[(times.size*0.95).toInt()-1]))
        assertTrue(store.append(fact(1000)))
        assertEquals(1001,store.all().size)
    }

    @Test fun legacyLex2LedgerIsReadLosslesslyAndMigratedOnNextWrite() = withDir { dir ->
        val old=(0 until 256).map(::fact)
        File(dir,"experiences-v2.bin").writeBytes(legacyLex2(old))
        val store=FileLearningExperienceStore(dir)
        assertEquals(old,store.all())
        assertTrue(store.append(fact(256)))
        assertEquals(0x4c455833,magic(File(dir,"experiences-v2.bin")))
        assertEquals(old+fact(256),FileLearningExperienceStore(dir).all())
        assertEquals("LKG backup keeps the readable legacy generation",old,
            FileLearningExperienceStore.inspectSnapshot(File(dir,"experiences-v2.lkg").readBytes()))
    }

    @Test fun hypotheticalStaysUnconfirmedInLargeLedgerAndCacheTracksIt() = withDir { dir ->
        FileLearningExperienceStore(File(dir,"learning-core-v2")).appendMissing((0 until 300).map(::fact))
        val core=ContinuumLearningCore(dir)
        val before=core.temporalMemoryView()
        assertTrue(core.temporalMemoryView()===before)
        core.confirmOwnerFact("Merke dir: Wenn ich im Lotto gewinne, kaufe ich ein Haus.")
        assertTrue(core.temporalMemoryView()!==before)
        val confirmed=FileLearningExperienceStore(File(dir,"learning-core-v2")).all()
            .filter { it.status==LearningStatus.CONFIRMED }
        assertEquals(300,confirmed.size)
        assertTrue(confirmed.none { "Lotto" in it.content })
    }
}
