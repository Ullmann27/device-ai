package at.heinz.deviceai.learning

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ContinuumSourceRoundJournalTest {
    @Test fun aStartedArchivedSourceRemainsPendingAcrossFreshReads() = withDirectory { dir ->
        val excerpt="Licht wird an glatten Oberflächen reflektiert und an Grenzflächen gebrochen."
        val source=source(1).copy(sha=ContinuumTemporalMemory.sha(excerpt),excerpt=excerpt)
        ContinuumSourceNovelty.markStarted(dir,listOf(source))
        assertTrue(journal(dir).readLines().contains("?"+source.sha))
        val archived=ContinuumKnowledgeArchive(dir).save(
            ContinuumKnowledgeArchive.Kind.PUBLIC_WEB_EXCERPT,source.subject,source.url,
            "Synthetische Testquelle",source.excerpt)
        assertEquals(source.sha,archived.sha256)
        repeat(2) {
            assertEquals(listOf(source),ContinuumSourceNovelty.pending(listOf(source),
                ContinuumSourceNovelty.knownHashes(dir)))
        }
        assertTrue(ContinuumSourceNovelty.markCompleted(dir,listOf(source)))
        assertTrue(ContinuumSourceNovelty.pending(listOf(source),
            ContinuumSourceNovelty.knownHashes(dir)).isEmpty())
    }

    @Test fun cancellationBeforeJournalReplaceKeepsTheOpenRound() = withDirectory { dir ->
        val source=source(1)
        ContinuumSourceNovelty.markStarted(dir,listOf(source))
        val before=journal(dir).readBytes()
        val temp=File(journal(dir).parentFile,journal(dir).name+".tmp")
        assertFalse(ContinuumSourceNovelty.markCompleted(dir,listOf(source)) { temp.exists() })
        assertArrayEquals(before,journal(dir).readBytes())
        assertFalse(temp.exists())
        assertTrue(ContinuumSourceNovelty.completedHashes(dir).isEmpty())
    }

    @Test fun aFullOpenJournalRefusesWithoutDroppingWork() = withDirectory { dir ->
        val open=(1..120).map(::source)
        ContinuumSourceNovelty.markStarted(dir,open)
        val before=journal(dir).readBytes()
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumSourceNovelty.markStarted(dir,listOf(source(121)))
        }
        assertArrayEquals(before,journal(dir).readBytes())
        assertEquals(120,journal(dir).readLines().count { it.startsWith("?") })
        assertTrue(ContinuumSourceNovelty.markCompleted(dir,listOf(open.first())))
        ContinuumSourceNovelty.markStarted(dir,listOf(source(121)))
        assertEquals(120,journal(dir).readLines().count { it.startsWith("?") })
    }

    @Test fun compactionRetainsOpenMarkersAndBoundsAllRecords() = withDirectory { dir ->
        val open=source(1)
        ContinuumSourceNovelty.markStarted(dir,listOf(open))
        ContinuumSourceNovelty.markCompleted(dir,(2..151).map(::source))
        val lines=journal(dir).readLines()
        assertEquals(120,lines.size)
        assertEquals(119,ContinuumSourceNovelty.completedHashes(dir).size)
        assertEquals(listOf("?"+open.sha),lines.filter { it.startsWith("?") })
        assertEquals(listOf(open),ContinuumSourceNovelty.pending(listOf(open),
            ContinuumSourceNovelty.knownHashes(dir)))
    }

    @Test fun legacyCompletionsRemainCompletedWhenNewWorkStarts() = withDirectory { dir ->
        val old=source(1)
        journal(dir).parentFile.mkdirs()
        journal(dir).writeText(old.sha+"\n")
        ContinuumSourceNovelty.markStarted(dir,listOf(old,source(2)))
        assertEquals(setOf(old.sha),ContinuumSourceNovelty.completedHashes(dir))
        assertEquals(listOf(source(2)),ContinuumSourceNovelty.pending(listOf(old,source(2)),
            ContinuumSourceNovelty.knownHashes(dir)))
    }

    @Test fun storageFailureCannotAcknowledgeAStartedRound() = withDirectory { dir ->
        File(dir,"learning-core-v2").writeText("synthetic-storage-blocker")
        assertThrows(IllegalStateException::class.java) {
            ContinuumSourceNovelty.markStarted(dir,listOf(source(1)))
        }
        assertEquals("synthetic-storage-blocker",File(dir,"learning-core-v2").readText())
    }

    @Test fun malformedOrOverlappingMarkersFailWithoutRewriting() = withDirectory { dir ->
        val source=source(1)
        journal(dir).parentFile.mkdirs()
        for(text in listOf("?bad\n",source.sha+"\n?"+source.sha+"\n")) {
            journal(dir).writeText(text)
            assertThrows(IllegalArgumentException::class.java) {
                ContinuumSourceNovelty.knownHashes(dir)
            }
            assertEquals(text,journal(dir).readText())
        }
    }

    @Test fun revokedMarkersAreRetiredBeforeRetryCapacityIsChecked() = withDirectory { dir ->
        val revoked=(1..120).map(::source)
        FileLearningExperienceStore(File(dir,"learning-core-v2")).appendMissing(revoked.map {
            LearningExperienceFactory.create(LearningExperienceType.SOURCE_TEXT,
                LearningProvenance.PUBLIC_WEB_SOURCE,LearningTrustClass.SOURCE_OBSERVATION,
                LearningStatus.REVOKED,it.excerpt,annotation=it.subject,
                sourceRef=it.url+"#sha256="+it.sha,now=1L)
        })
        journal(dir).writeText(revoked.joinToString("\n",postfix="\n") { "?"+it.sha })
        assertTrue(ContinuumSourceNovelty.pending(revoked,
            ContinuumSourceNovelty.knownHashes(dir)).isEmpty())
        val fresh=source(121)
        ContinuumSourceNovelty.markStarted(dir,listOf(fresh))
        assertEquals(listOf("?"+fresh.sha),journal(dir).readLines())
        assertTrue(ContinuumSourceNovelty.completedHashes(dir).isEmpty())
        assertEquals(listOf(fresh),ContinuumSourceNovelty.pending(revoked+fresh,
            ContinuumSourceNovelty.knownHashes(dir)))
    }

    private fun source(id:Int)=MentorSource("Optik","https://example.org/source-$id",
        "%064x".format(id),"Licht wird an Oberflächen reflektiert und an Grenzflächen gebrochen.")
    private fun journal(dir:File)=File(dir,"learning-core-v2/source-attempts-v1.txt")
    private fun withDirectory(block:(File)->Unit) {
        val dir=Files.createTempDirectory("continuum-source-round-journal").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }
}
