package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ContinuumSourceNoveltyTest {
    @Test fun onlyNovelHashesAreTrainedOnceAcrossRestarts() {
        val dir=Files.createTempDirectory("continuum-source-novelty").toFile()
        try {
            val first=MentorSource("Mathematik","https://de.wikipedia.org/wiki/Algebra",
                "a".repeat(64),"Algebra beschreibt mathematische Strukturen und Beziehungen.")
            val repeated=MentorSource("Mathematik","https://de.wikipedia.org/wiki/Algebra",
                "a".repeat(64),"Algebra beschreibt mathematische Strukturen und Beziehungen.")
            val next=MentorSource("Physik","https://de.wikipedia.org/wiki/Optik",
                "b".repeat(64),"Optik beschreibt die Ausbreitung und Eigenschaften des Lichtes.")
            assertEquals(2,ContinuumSourceNovelty.pending(listOf(first,repeated,next),emptySet()).size)
            ContinuumSourceNovelty.markCompleted(dir,listOf(first))
            assertEquals(setOf("a".repeat(64)),ContinuumSourceNovelty.completedHashes(dir))
            assertEquals(listOf(next),ContinuumSourceNovelty.pending(listOf(first,next),
                ContinuumSourceNovelty.completedHashes(dir)))
        } finally { dir.deleteRecursively() }
    }

    @Test fun malformedHashesAreNotQueuedOrPersisted() {
        val dir=Files.createTempDirectory("continuum-source-invalid").toFile()
        try {
            val invalid=MentorSource("Test","https://example.org/test",
                "not-a-sha","Ein Beispiel für ungültige Herkunft.")
            assertTrue(ContinuumSourceNovelty.pending(listOf(invalid),emptySet()).isEmpty())
            ContinuumSourceNovelty.markCompleted(dir,listOf(invalid))
            assertTrue(ContinuumSourceNovelty.completedHashes(dir).isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun archivedHashSurvivesJournalCompaction() {
        val dir=Files.createTempDirectory("continuum-source-known").toFile()
        try {
            val archive=ContinuumKnowledgeArchive(dir)
            val excerpt="Photovoltaikmodule erzeugen Gleichstrom aus Sonnenlicht durch den photoelektrischen Effekt."
            val record=archive.save(ContinuumKnowledgeArchive.Kind.PUBLIC_WEB_EXCERPT,
                "Photovoltaik","https://solar.example.org/pv-effekt",
                "Öffentliche HTTPS-Quelle · unbestätigter kurzer Auszug",excerpt)
            val high=(1..125).map { idx -> MentorSource("Thema $idx",
                "https://uni.example.org/thema-$idx","f"+"%063x".format(idx),
                "Ein ausreichend langer wissenschaftlicher Quellentext für Thema Nummer $idx.") }
            ContinuumSourceNovelty.markCompleted(dir,high)
            assertEquals(120,ContinuumSourceNovelty.completedHashes(dir).size)
            val known=ContinuumSourceNovelty.knownHashes(dir)
            assertTrue(record.sha256 in known)
            assertTrue(ContinuumSourceNovelty.pending(
                listOf(MentorSource("Photovoltaik","https://solar.example.org/pv-effekt",record.sha256,excerpt)),
                known).isEmpty())
        } finally { dir.deleteRecursively() }
    }

}
