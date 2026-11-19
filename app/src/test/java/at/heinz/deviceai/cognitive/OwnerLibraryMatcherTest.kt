package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Name comparison, ranking and the open/ask/near/none decision of [OwnerLibraryMatcher], and its wording of file facts. */
class OwnerLibraryMatcherTest {
    private fun file(name: String, folder: String = "Download", day: String? = null, size: Long = 1_000) = OwnerLibraryFile(name, folder,
        "/storage/emulated/0/$folder/$name", size, day?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() } ?: 0L)

    private fun names(decision: OwnerLibraryMatcher.Decision): String = when (decision) {
        is OwnerLibraryMatcher.Decision.Open -> "OPEN " + decision.hit.file.name + decision.others.joinToString("") { " +" + it.file.name }
        is OwnerLibraryMatcher.Decision.Ask -> "ASK " + decision.hits.joinToString(" | ") { it.file.name }
        is OwnerLibraryMatcher.Decision.Near -> "NEAR " + decision.hits.joinToString(" | ") { it.file.name }
        OwnerLibraryMatcher.Decision.None -> "NONE"
    }

    @Test fun namesAreComparedTheWaySpeechRecognitionWritesThem() {
        listOf("Argon4", "argon 4", "ARGON-4", "Argon_4.pdf", "Argon vier", "argon4.PDF", " Argon  4 ").forEach {
            assertEquals(it, "argon4", OwnerLibraryMatcher.key(it))
        }
        assertEquals(listOf("argon", "4", "datenblatt"), OwnerLibraryMatcher.tokens("Argon4-Datenblatt.pdf"))
        // Umlauts and ß equal their transcriptions – in both directions.
        setOf("Müller Straße", "Mueller Strasse", "Muller Strasse", "MÜLLER-STRAßE").map(OwnerLibraryMatcher::key).let {
            assertEquals(setOf("mullerstrasse"), it.toSet())
        }
        assertEquals(OwnerLibraryMatcher.key("Protokoll 12"), OwnerLibraryMatcher.key("Protokoll zwölf"))
        assertEquals(OwnerLibraryMatcher.key("Kapitel 5"), OwnerLibraryMatcher.key("Kapitel fünf"))
        assertEquals("cafe2", OwnerLibraryMatcher.key("Café zwei"))
        // A number word inside a longer word is left alone.
        assertEquals("vierteljahr", OwnerLibraryMatcher.key("Vierteljahr"))
        assertEquals("", OwnerLibraryMatcher.key(".pdf"))
    }

    @Test fun theRankIsExactThenPrefixThenContainsAndNearMissesOnlyWhenNothingMatches() {
        val files = listOf(file("Messung Argon4.pdf"), file("Argon4 Datenblatt.pdf"), file("Argon4.pdf"), file("Organ4.pdf"), file("Xenon.pdf"))
        assertEquals(listOf("Argon4.pdf" to OwnerLibraryMatcher.Tier.EXACT, "Argon4 Datenblatt.pdf" to OwnerLibraryMatcher.Tier.PREFIX,
            "Messung Argon4.pdf" to OwnerLibraryMatcher.Tier.CONTAINS),
            OwnerLibraryMatcher.rank("Argon4", files).map { it.file.name to it.tier })
        // Words in another order still name the file.
        assertEquals(OwnerLibraryMatcher.Tier.CONTAINS, OwnerLibraryMatcher.rank("Datenblatt Argon4", files).single().tier)
        // Within one rank the newer file comes first, then the name.
        val dated = listOf(file("Argon4 alt.pdf", day = "2026-01-05"), file("Argon4 neu.pdf", day = "2026-11-17"), file("Argon4 b.pdf"), file("Argon4 a.pdf"))
        assertEquals(listOf("Argon4 neu.pdf", "Argon4 alt.pdf", "Argon4 a.pdf", "Argon4 b.pdf"), OwnerLibraryMatcher.rank("Argon4", dated).map { it.file.name })
        // Near misses: small edit distance, also against the beginning of a longer name; never mixed with real hits.
        assertEquals(listOf(OwnerLibraryMatcher.Tier.NEAR), OwnerLibraryMatcher.rank("Organ 4", listOf(file("Argon4.pdf"), file("Xenon.pdf"))).map { it.tier })
        assertEquals("Argon4 Datenblatt.pdf", OwnerLibraryMatcher.rank("Argom4", listOf(file("Argon4 Datenblatt.pdf"))).single().file.name)
        assertTrue(OwnerLibraryMatcher.rank("Argon4", listOf(file("Organ4.pdf"), file("Argon4.pdf"))).none { it.tier == OwnerLibraryMatcher.Tier.NEAR })
        assertTrue(OwnerLibraryMatcher.rank("Abc", listOf(file("Abd.pdf"))).isEmpty())
        assertTrue(OwnerLibraryMatcher.rank("Argon4", listOf(file("Xenon.pdf"), file("Rechnung.pdf"))).isEmpty())
        assertTrue(OwnerLibraryMatcher.rank("  ", files).isEmpty())
    }

    @Test fun onlyAClearHitIsOpened() {
        assertEquals("OPEN Argon4.pdf", names(OwnerLibraryMatcher.decide("Argon4", listOf(file("Argon4.pdf"), file("Xenon.pdf")))))
        // One exact name wins; the other real hits are kept to be named.
        assertEquals("OPEN Argon4.pdf +Argon4 Datenblatt.pdf +Messung Argon4.pdf", names(OwnerLibraryMatcher.decide("Argon4",
            listOf(file("Messung Argon4.pdf"), file("Argon4 Datenblatt.pdf"), file("Argon4.pdf")))))
        // The only real hit is clear even when it is not the exact name.
        assertEquals("OPEN Argon4 Datenblatt.pdf", names(OwnerLibraryMatcher.decide("Argon4", listOf(file("Argon4 Datenblatt.pdf")))))
        // Two of equal standing, or the same name twice: the owner chooses.
        assertEquals("ASK Argon4 Datenblatt.pdf | Argon4 Messprotokoll.pdf", names(OwnerLibraryMatcher.decide("Argon4",
            listOf(file("Argon4 Messprotokoll.pdf"), file("Argon4 Datenblatt.pdf")))))
        assertEquals("ASK Argon4.pdf | Argon4.pdf | Argon4 Datenblatt.pdf", names(OwnerLibraryMatcher.decide("Argon4",
            listOf(file("Argon4.pdf", "Documents"), file("Argon4.pdf", "Download"), file("Argon4 Datenblatt.pdf")))))
        // A near miss is a question, never a hit.
        assertEquals("NEAR Argon4.pdf", names(OwnerLibraryMatcher.decide("Organ 4", listOf(file("Argon4.pdf")))))
        assertEquals("NONE", names(OwnerLibraryMatcher.decide("Argon4", emptyList())))
    }

    @Test fun aFolderHintNarrowsRealHitsButNeverCreatesOne() {
        val both = listOf(file("Argon4.pdf", "Documents/Labor"), file("Argon4.pdf", "Download"))
        assertEquals("/storage/emulated/0/Download/Argon4.pdf",
            (OwnerLibraryMatcher.decide("Argon4", both, OwnerLibraryMatcher.FOLDER_DOWNLOAD) as OwnerLibraryMatcher.Decision.Open).hit.file.path)
        assertEquals("/storage/emulated/0/Documents/Labor/Argon4.pdf",
            (OwnerLibraryMatcher.decide("Argon4", both, OwnerLibraryMatcher.FOLDER_DOCUMENTS) as OwnerLibraryMatcher.Decision.Open).hit.file.path)
        // Nothing lies in the named folder: the hint is ignored rather than hiding real hits.
        assertEquals("OPEN Argon4.pdf", names(OwnerLibraryMatcher.decide("Argon4", listOf(file("Argon4.pdf", "DCIM")), OwnerLibraryMatcher.FOLDER_DOWNLOAD)))
        assertEquals("NONE", names(OwnerLibraryMatcher.decide("Argon4", listOf(file("Xenon.pdf")), OwnerLibraryMatcher.FOLDER_DOWNLOAD)))
    }

    @Test fun aFragmentPicksAmongShownHitsByNameOrFolder() {
        val hits = listOf(file("Argon4 Datenblatt.pdf"), file("Argon4 Messprotokoll.pdf", "Documents/Labor"), file("Argon4 Angebot.pdf"))
        assertEquals(listOf("Argon4 Messprotokoll.pdf"), OwnerLibraryMatcher.pick("Messprotokoll", hits).map { it.name })
        assertEquals(listOf("Argon4 Datenblatt.pdf"), OwnerLibraryMatcher.pick("argon 4 datenblatt", hits).map { it.name })
        assertEquals(listOf("Argon4 Messprotokoll.pdf"), OwnerLibraryMatcher.pick("Labor", hits).map { it.name })
        assertEquals(3, OwnerLibraryMatcher.pick("Argon4", hits).size)
        assertTrue(OwnerLibraryMatcher.pick("Xenon", hits).isEmpty())
        assertTrue(OwnerLibraryMatcher.pick("4", hits).isEmpty())
    }

    @Test fun editDistanceCountsInsertDeleteReplaceAndSwap() {
        assertEquals(0, OwnerLibraryMatcher.editDistance("argon4", "argon4"))
        assertEquals(1, OwnerLibraryMatcher.editDistance("argon4", "argon5"))
        assertEquals(1, OwnerLibraryMatcher.editDistance("argon4", "agron4"))
        assertEquals(1, OwnerLibraryMatcher.editDistance("argon4", "argon"))
        assertEquals(2, OwnerLibraryMatcher.editDistance("organ4", "argon4"))
        assertEquals(3, OwnerLibraryMatcher.editDistance("abcdef", "uvwxyz", limit = 2))
    }

    @Test fun fileFactsAreWordedLikeAndroidShowsThem() {
        assertEquals("512 Byte", OwnerLibraryMatcher.sizeText(512))
        assertEquals("310 KB", OwnerLibraryMatcher.sizeText(317_440))
        assertEquals("1,2 MB", OwnerLibraryMatcher.sizeText(1_258_291))
        assertEquals("2,5 GB", OwnerLibraryMatcher.sizeText(2_684_354_560))
        val zone = ZoneId.of("Europe/Vienna")
        assertEquals("„Argon4.pdf“ – Ordner Download · 310 KB · geändert am 17.11.2026",
            OwnerLibraryMatcher.describe(file("Argon4.pdf", day = "2026-11-17", size = 317_440), zone))
        // The storage root has no folder name; an unknown date is left out, not invented.
        assertEquals("„Argon4.pdf“ – Hauptordner des Speichers · 2 KB", OwnerLibraryMatcher.describe(file("Argon4.pdf", folder = "", size = 2_000), zone))
    }
}
