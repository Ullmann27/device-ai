package at.heinz.deviceai.learning

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ContinuumRuntimeEventJournalTest {
    @Test fun modelTextIsNeverRecordedAndOnlyBoundedOutcomesSurviveReboot() {
        val dir=Files.createTempDirectory("runtime-journal").toFile()
        try {
            val journal=ContinuumRuntimeEventJournal(dir)
            assertTrue(journal.read().recent.isEmpty())
            for(i in 0..20)journal.recordLocalOutcome(i%3!=0,i.toLong()+100)
            val reopened=ContinuumRuntimeEventJournal(dir).read()
            assertEquals(16,reopened.recent.size)
            assertEquals(16,reopened.successes+reopened.failures)
            assertTrue(reopened.recent.all {
                it.matches(Regex("[0-9]+\\tLOCAL_MODEL_(OK|FAILED)"))
            })
            val persisted=dir.resolve("learning-core-v2/runtime-events-v1.tsv").readText()
            assertFalse(persisted.contains("Generated model text"))
            assertTrue(persisted.length<1500)
        } finally { dir.deleteRecursively() }
    }

    @Test fun malformedEventJournalFailsClosed() {
        val dir=Files.createTempDirectory("runtime-journal-corrupt").toFile()
        try {
            val file=dir.resolve("learning-core-v2/runtime-events-v1.tsv")
            file.parentFile!!.mkdirs()
            file.writeText("secret user content\tMODEL_OK\n")
            assertThrows(IllegalArgumentException::class.java) {
                ContinuumRuntimeEventJournal(dir).read()
            }
        } finally { dir.deleteRecursively() }
    }
}
