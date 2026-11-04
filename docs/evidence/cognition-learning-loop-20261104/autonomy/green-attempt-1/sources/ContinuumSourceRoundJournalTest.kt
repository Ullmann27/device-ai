package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class ContinuumSourceRoundJournalTest {
    private fun source(index:Int):MentorSource {
        val excerpt="Synthetische Testquelle Nummer $index zu öffentlichen Grundlagen der Optik."
        val sha=MessageDigest.getInstance("SHA-256").digest(excerpt.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return MentorSource("Optik $index","https://example.org/optik-$index",sha,excerpt)
    }

    private fun archive(base:File,source:MentorSource) {
        ContinuumKnowledgeArchive(base).save(ContinuumKnowledgeArchive.Kind.PUBLIC_WEB_EXCERPT,
            source.subject,source.url,"Synthetischer Testauszug",source.excerpt)
    }

    private fun journal(base:File)=File(base,"learning-core-v2/source-attempts-v1.txt")

    private fun temporary(base:File)=File(base,"learning-core-v2/source-attempts-v1.txt.tmp")

    private fun record(base:File,source:MentorSource,status:LearningStatus):LearningExperience {
        val record=LearningExperienceFactory.create(
            LearningExperienceType.SOURCE_TEXT,LearningProvenance.PUBLIC_WEB_SOURCE,
            LearningTrustClass.SOURCE_OBSERVATION,status,source.excerpt,source.subject,
            source.url+"#sha256="+source.sha
        )
        assertTrue(FileLearningExperienceStore(File(base,"learning-core-v2")).append(record))
        return record
    }

    private inline fun inDirectory(test:(File)->Unit) {
        val dir=Files.createTempDirectory("source-round-journal").toFile()
        try { test(dir) } finally { dir.deleteRecursively() }
    }

    @Test fun evaluatedAcceptedAndRejectedRoundsRemainKnown() = inDirectory { dir ->
        listOf(LearningStatus.LEARNED,LearningStatus.REPLAY_PENDING).forEachIndexed { index,status ->
            val source=source(index)
            ContinuumSourceNovelty.markStarted(dir,listOf(source))
            archive(dir,source)
            record(dir,source,status)
            assertFalse(source.sha in ContinuumSourceNovelty.knownHashes(dir))
            ContinuumSourceNovelty.markCompleted(dir,listOf(source))
            assertTrue(source.sha in ContinuumSourceNovelty.completedHashes(dir))
            assertTrue(source.sha in ContinuumSourceNovelty.knownHashes(File(dir.absolutePath)))
            ContinuumSourceNovelty.markStarted(dir,listOf(source))
            assertTrue(source.sha in ContinuumSourceNovelty.knownHashes(dir))
        }
        assertFalse(journal(dir).readText().contains('?'))
    }

    @Test fun otherCompletionsCannotCompactAnOpenRound() = inDirectory { dir ->
        val open=source(1)
        val finished=source(2)
        ContinuumSourceNovelty.markStarted(dir,listOf(open,finished))
        archive(dir,open)
        archive(dir,finished)
        ContinuumSourceNovelty.markCompleted(dir,listOf(finished))
        val high=(1..125).map { index ->
            source(index+10).copy(sha="f"+"%063x".format(index))
        }
        ContinuumSourceNovelty.markCompleted(dir,high)
        assertEquals(119,ContinuumSourceNovelty.completedHashes(dir).size)
        assertEquals(120,journal(dir).readLines().size)
        assertTrue(journal(dir).length()<=8192)
        assertTrue("?"+open.sha in journal(dir).readLines())
        assertFalse(open.sha in ContinuumSourceNovelty.knownHashes(dir))
        assertTrue(finished.sha in ContinuumSourceNovelty.knownHashes(dir))
        ContinuumSourceNovelty.markCompleted(dir,listOf(open))
        assertTrue(open.sha in ContinuumSourceNovelty.knownHashes(dir))
        assertEquals(120,ContinuumSourceNovelty.completedHashes(dir).size)
    }

    @Test fun fullOpenJournalRefusesInsteadOfLosingProgress() = inDirectory { dir ->
        val started=(1..120).map(::source)
        ContinuumSourceNovelty.markStarted(dir,started)
        val before=journal(dir).readBytes()
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumSourceNovelty.markStarted(dir,listOf(source(121)))
        }
        assertArrayEquals(before,journal(dir).readBytes())
        assertEquals(120,journal(dir).readLines().size)
        assertTrue(ContinuumSourceNovelty.completedHashes(dir).isEmpty())
        ContinuumSourceNovelty.markCompleted(dir,listOf(started.first()))
        ContinuumSourceNovelty.markStarted(dir,listOf(source(121)))
        assertTrue("?"+source(121).sha in journal(dir).readLines())
    }

    @Test fun invalidHashCannotPartiallyStartABatch() = inDirectory { dir ->
        val open=source(1)
        ContinuumSourceNovelty.markStarted(dir,listOf(open))
        val before=journal(dir).readBytes()
        val invalid=source(3).copy(sha="../not-a-source-hash")
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumSourceNovelty.markStarted(dir,listOf(source(2),invalid))
        }
        assertArrayEquals(before,journal(dir).readBytes())
        assertTrue(ContinuumSourceNovelty.pending(listOf(invalid),emptySet()).isEmpty())
        ContinuumSourceNovelty.markCompleted(dir,listOf(invalid))
        assertArrayEquals(before,journal(dir).readBytes())
    }

    @Test fun markerWriteFailureStopsBeforeArchive() = inDirectory { dir ->
        val source=source(1)
        assertTrue(temporary(dir).mkdirs())
        File(temporary(dir),"blocked").writeText("Synthetic write failure")
        assertThrows(java.io.IOException::class.java) {
            ContinuumSourceNovelty.markStarted(dir,listOf(source))
            archive(dir,source)
        }
        assertFalse(journal(dir).exists())
        assertFalse(File(dir,"continuum-knowledge-v1").exists())
    }

    @Test fun failedCompletionWriteLeavesRoundOpen() = inDirectory { dir ->
        val source=source(1)
        ContinuumSourceNovelty.markStarted(dir,listOf(source))
        archive(dir,source)
        val before=journal(dir).readBytes()
        assertTrue(temporary(dir).mkdir())
        File(temporary(dir),"blocked").writeText("Synthetic write failure")
        assertThrows(java.io.IOException::class.java) {
            ContinuumSourceNovelty.markCompleted(dir,listOf(source))
        }
        assertArrayEquals(before,journal(dir).readBytes())
        assertFalse(source.sha in ContinuumSourceNovelty.knownHashes(dir))
        assertTrue(temporary(dir).deleteRecursively())
        ContinuumSourceNovelty.markCompleted(dir,listOf(source))
        assertTrue(source.sha in ContinuumSourceNovelty.knownHashes(dir))
    }

    @Test fun resumingCannotRevokeARevocation() = inDirectory { dir ->
        val source=source(1)
        ContinuumSourceNovelty.markStarted(dir,listOf(source))
        archive(dir,source)
        val revoked=record(dir,source,LearningStatus.REVOKED)
        ContinuumSourceNovelty.markStarted(dir,listOf(source))
        ContinuumSourceNovelty.knownHashes(dir)
        val reread=FileLearningExperienceStore(File(dir,"learning-core-v2")).all().single()
        assertEquals(revoked,reread)
        assertEquals(LearningStatus.REVOKED,reread.status)
        assertTrue(ContinuumSourceNovelty.completedHashes(dir).isEmpty())
    }

    @Test fun historicalArchiveWithoutMarkerRemainsKnown() = inDirectory { dir ->
        val source=source(1)
        archive(dir,source)
        assertFalse(journal(dir).exists())
        assertTrue(source.sha in ContinuumSourceNovelty.knownHashes(dir))
        assertTrue(ContinuumSourceNovelty.completedHashes(dir).isEmpty())
    }

    @Test fun corruptStartedMarkerFailsClosed() = inDirectory { dir ->
        val source=source(1)
        ContinuumSourceNovelty.markStarted(dir,listOf(source))
        journal(dir).writeText("?invalid\n")
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumSourceNovelty.knownHashes(dir)
        }
    }
}
