package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ContinuumKnowledgeArchiveTest {
    @Test fun indexedOfflineArchiveStoresCitationsAndRevisions() {
        val temp=Files.createTempDirectory("continuum-archive").toFile()
        try {
            val archive=ContinuumKnowledgeArchive(temp)
            val source="https://de.wikipedia.org/wiki/Robotik"
            val original=archive.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,
                "Robotik",source,"Wikipedia-Mitwirkende · CC BY-SA 4.0",
                "Robotik untersucht die Entwicklung und den Einsatz technischer Roboter.")
            val duplicate=archive.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,
                "Robotik",source,"Wikipedia-Mitwirkende · CC BY-SA 4.0",
                "Robotik untersucht die Entwicklung und den Einsatz technischer Roboter.")
            assertEquals(original.id,duplicate.id)
            assertEquals(1,archive.stats()[ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT])
            val found=ContinuumKnowledgeArchive(temp).search("robotik")
            assertEquals(1,found.size)
            assertEquals(source,found.single().source.url)
            assertEquals(original.sha256,found.single().source.sha256)
            val changed=archive.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,
                "Robotik",source,"Wikipedia-Mitwirkende · CC BY-SA 4.0",
                "Die moderne Robotik verbindet Automatisierung, Sensoren und Antriebstechnik.")
            assertNotEquals(original.id,changed.id)
            assertEquals(2,archive.stats()[ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT])
        } finally { temp.deleteRecursively() }
    }
    @Test fun invalidDomainsAndUnsafeReferencesAreRejected() {
        val temp=Files.createTempDirectory("continuum-archive-guard").toFile()
        try {
            val archive=ContinuumKnowledgeArchive(temp)
            val text="Ein ausreichend langer amtlicher Rechtsauszug zu einem Sachverhalt."
            for(url in listOf("https://evil.example/RIS","http://www.ris.bka.gv.at/gesetz",
                "https://www.ris.bka.gv.at.evil.example/gesetz",
                "https://user:pass@www.ris.bka.gv.at/doc")) {
                try {
                    archive.save(ContinuumKnowledgeArchive.Kind.RIS_LEGISLATION,
                        "Paragraph eins",url,"Rechtsinformationssystem",text)
                    fail("Untrusted domain accepted")
                } catch (_: IllegalArgumentException) {}
            }
            assertTrue(archive.stats().values.all { it==0 })
        } finally { temp.deleteRecursively() }
    }
    @Test fun legalArchiveIsNotMisreportedAsLegalValidity() {
        val temp=Files.createTempDirectory("continuum-ris-source").toFile()
        try {
            val archive=ContinuumKnowledgeArchive(temp)
            val record=archive.save(ContinuumKnowledgeArchive.Kind.RIS_CASE_LAW,
                "Musterentscheid", "https://www.ris.bka.gv.at/JustizEntscheidung.wxe",
                "RIS · Rechtsinformationssystem des Bundes / BKA",
                "Dies ist ein öffentlicher, nicht verbindlich verifizierter Beispielauszug.",
                sourceDate="2026-10-20")
            assertEquals("2026-10-20",record.sourceDate)
            assertEquals(1,archive.search("Musterentscheid",
                ContinuumKnowledgeArchive.Kind.RIS_CASE_LAW).size)
        } finally { temp.deleteRecursively() }
    }
}
