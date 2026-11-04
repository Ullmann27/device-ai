package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LearningExperienceStoreTest {
    @Test fun persistentTypedReplayIsBoundedAndDeduplicated() {
        val dir=Files.createTempDirectory("device-ai-experience").toFile()
        try {
            val store=FileLearningExperienceStore(dir)
            val item=LearningExperienceFactory.create(
                LearningExperienceType.INTENT_LABEL,
                LearningProvenance.USER_CONFIRMED,
                LearningTrustClass.OWNER_CONFIRMED,
                LearningStatus.CONFIRMED,
                "Kannst du mir helfen?",
                "1",
                now=10
            )
            assertTrue(store.append(item))
            assertFalse(store.append(item))
            assertEquals(item,FileLearningExperienceStore(dir).all().single())
            assertEquals(item.id,store.replay(LearningExperienceType.INTENT_LABEL).single().id)
            assertTrue(store.updateStatus(item.id,LearningStatus.LEARNED,20))
            assertEquals(LearningStatus.LEARNED,store.all().single().status)
            assertEquals(1,store.stats().learned)
        } finally { dir.deleteRecursively() }
    }

    @Test fun checksumAndContentHashRejectTampering() {
        val dir=Files.createTempDirectory("device-ai-experience-tamper").toFile()
        try {
            val store=FileLearningExperienceStore(dir)
            val item=LearningExperienceFactory.create(
                LearningExperienceType.SOURCE_TEXT,
                LearningProvenance.MENTOR_WIKIPEDIA,
                LearningTrustClass.SOURCE_OBSERVATION,
                LearningStatus.OBSERVED,
                "Pflanzen wandeln Lichtenergie in chemische Energie um.",
                "Biologie",
                "https://de.wikipedia.org/wiki/Fotosynthese",
                10
            )
            store.append(item)
            val file=File(dir,"experiences-v2.bin")
            val bytes=file.readBytes()
            bytes[bytes.size/2]=(bytes[bytes.size/2].toInt() xor 1).toByte()
            file.writeBytes(bytes)
            assertThrows(Exception::class.java) { FileLearningExperienceStore(dir).all() }
        } finally { dir.deleteRecursively() }
    }

    @Test fun storeKeepsOnlyNewestBoundedReplayWindow() {
        val dir=Files.createTempDirectory("device-ai-experience-bound").toFile()
        try {
            val store=FileLearningExperienceStore(dir)
            val total=FileLearningExperienceStore.MAX_RECORDS+24
            fun audit(i:Int)=LearningExperienceFactory.create(
                    LearningExperienceType.TRAINING_AUDIT,
                    LearningProvenance.LOCAL_TRAINING,
                    LearningTrustClass.SYSTEM_MEASUREMENT,
                    LearningStatus.LEARNED,
                    "audit-$i",
                    now=i.toLong())
            store.appendMissing((0 until total-2).map(::audit))
            store.append(audit(total-2));store.append(audit(total-1))
            val all=store.all()
            assertEquals(FileLearningExperienceStore.MAX_RECORDS,all.size)
            assertEquals("audit-24",all.first().content)
            assertEquals("audit-${total-1}",all.last().content)
        } finally { dir.deleteRecursively() }
    }

    @Test fun fullOwnerLedgerRefusesLowTrustChurnAndFailsBeforeLosingConfirmedRecords() {
        val dir=Files.createTempDirectory("device-ai-protected-experience-bound").toFile()
        try {
            val store=FileLearningExperienceStore(dir)
            fun fact(index:Int)=LearningExperienceFactory.create(
                LearningExperienceType.USER_FACT,LearningProvenance.USER_CONFIRMED,
                LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,
                "Bestätigter persönlicher Fakt $index",now=index.toLong())
            assertEquals(FileLearningExperienceStore.MAX_RECORDS,
                store.appendMissing((0 until FileLearningExperienceStore.MAX_RECORDS).map(::fact)))
            val before=File(dir,"experiences-v2.bin").readBytes()
            val audit=LearningExperienceFactory.create(LearningExperienceType.TRAINING_AUDIT,
                LearningProvenance.LOCAL_TRAINING,LearningTrustClass.SYSTEM_MEASUREMENT,
                LearningStatus.LEARNED,"new audit",now=1000L)
            assertFalse("a declined observation must not claim persistence",store.append(audit))
            assertEquals(0,store.appendMissing(listOf(audit)))
            assertThrows(IllegalStateException::class.java) { store.append(fact(FileLearningExperienceStore.MAX_RECORDS+1)) }
            assertArrayEquals("capacity failures must leave the ledger intact",before,
                File(dir,"experiences-v2.bin").readBytes())
            assertEquals(FileLearningExperienceStore.MAX_RECORDS,FileLearningExperienceStore(dir).all().size)
        } finally { dir.deleteRecursively() }
    }
}
