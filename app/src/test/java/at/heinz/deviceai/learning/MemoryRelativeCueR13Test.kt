package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class MemoryRelativeCueR13Test {
    private val zone = ZoneId.of("Europe/Vienna")
    private val now = Instant.parse("2026-11-05T12:00:00Z").toEpochMilli()
    private val temporal = TemporalCue(zone)
    private val phrases = listOf(
        "vor 2 Wochen", "vor 3 Monaten", "vor 5 Jahren", "seit 2 Tagen",
        "in 3 Tagen", "nach 2 Stunden", "vor 1 Tag", "vor 7 Minuten",
    )

    private fun window(days: Long): LongRange {
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusDays(days)
        return date.atStartOfDay(zone).toInstant().toEpochMilli() until
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun assertNumericRecall(retriever: ContinuumMemoryRetriever) {
        retriever.index(listOf(
            MemoryDoc("wrong", "3 Schrauben gekauft", now, true),
            MemoryDoc("right", "4 Schrauben gekauft", now, false),
        ))
        for (phrase in phrases) {
            val hits = retriever.search("$phrase 4 Schrauben gekauft", now, 10)
            assertEquals(phrase, listOf("right"), hits.map { it.doc.id })
            assertTrue(hits.none { it.confirmed })
        }
    }

    @Test fun relativeCountsAreNotFactualNumbers() {
        for (phrase in phrases) {
            assertEquals(phrase, setOf("4"), GermanText.numericEvidenceTerms("$phrase 4 Schrauben"))
        }
    }

    @Test fun bm25KeepsFactsAfterDurationExemption() {
        assertNumericRecall(Bm25GermanRetriever(temporal))
    }

    @Test fun ngramKeepsFactsAfterDurationExemption() {
        assertNumericRecall(CharNgramHashRetriever(temporal))
    }

    @Test fun bareDurationsDoNotEraseLexicalRecall() {
        for (retriever in listOf(Bm25GermanRetriever(temporal), CharNgramHashRetriever(temporal))) {
            retriever.index(listOf(MemoryDoc("bolt", "Schrauben gekauft", now, true)))
            for (phrase in phrases) {
                assertEquals(phrase, listOf("bolt"), retriever.search("$phrase Schrauben gekauft", now, 3).map { it.doc.id })
            }
        }
    }

    @Test fun dayCuesAcceptOrdinaryWhitespace() {
        for (phrase in listOf("vor  2  Tagen", "vor\t2\tTagen", "vor\n2\nTagen")) {
            assertEquals(phrase, window(2), temporal.window(phrase, now))
            assertEquals(emptySet<String>(), GermanText.numericEvidenceTerms(phrase))
        }
    }

    @Test fun dayCuesAcceptUnicodeSpacing() {
        for (space in listOf("\u00a0", "\u202f", "\u2009")) {
            val phrase = "vor${space}2${space}Tagen"
            assertEquals(phrase, window(2), temporal.window(phrase, now))
            assertEquals(emptySet<String>(), GermanText.numericEvidenceTerms(phrase))
        }
    }

    @Test fun singularDaysHaveOneDayWindow() {
        for (phrase in listOf("vor 1 Tag", "vor einem Tag", "VOR EINEM TAG")) {
            assertEquals(phrase, window(1), temporal.window(phrase, now))
            assertEquals(emptySet<String>(), GermanText.numericEvidenceTerms(phrase))
        }
    }

    @Test fun dayCuesNeverMatchInsideLongerWords() {
        for (text in listOf("bevor 2 Tagen", "zuvor 2 Tagen", "vor 2 Tagenbuch")) {
            assertEquals(text, null, temporal.window(text, now))
            assertEquals(setOf("2"), GermanText.numericEvidenceTerms(text))
        }
    }

    @Test fun supportedDaySpellingsRemainEquivalent() {
        for (phrase in listOf("vor 2 Tagen", "VOR ZWEI TAGEN", "vor 02 Tagen")) {
            assertEquals(phrase, window(2), temporal.window(phrase, now))
        }
    }

    @Test fun unsupportedWindowsAreNotInvented() {
        for (phrase in listOf("vor 2 Wochen", "in 3 Tagen", "nach 2 Stunden")) {
            assertEquals(phrase, null, temporal.window(phrase, now))
        }
    }

    @Test fun leadingZeroIdentifiersStayDistinct() {
        assertEquals(setOf("007"), GermanText.numericEvidenceTerms("Code 007"))
        for (retriever in listOf(Bm25GermanRetriever(), CharNgramHashRetriever())) {
            retriever.index(listOf(
                MemoryDoc("seven", "Code 7", now, true),
                MemoryDoc("padded", "Code 007", now, false),
            ))
            assertEquals(listOf("padded"), retriever.search("Code 007", now, 10).map { it.doc.id })
            assertEquals(listOf("seven"), retriever.search("Code 7", now, 10).map { it.doc.id })
        }
    }

    @Test fun oversizedDayCountsStillAbstain() {
        for (number in listOf("9223372036854775807", "9223372036854775808")) {
            assertEquals(null, temporal.window("vor $number Tagen", now))
        }
    }

    @Test fun bothRetrieversActuallyApplyWhitespaceDayWeight() {
        for (retriever in listOf(Bm25GermanRetriever(temporal), CharNgramHashRetriever(temporal))) {
            retriever.index(listOf(
                MemoryDoc("outside", "4 Schrauben gekauft", window(1).first, true),
                MemoryDoc("inside", "4 Schrauben gekauft", window(2).first, false),
            ))
            val hits = retriever.search("vor\t2\tTagen 4 Schrauben gekauft", now, 10)
            assertEquals(listOf("inside", "outside"), hits.map { it.doc.id })
            assertTrue(!hits.first().confirmed)
            assertTrue(hits.first().score > hits.last().score)
        }
    }

    @Test fun nonTemporalNumericFactsStayRequired() {
        assertEquals(setOf("2", "4"), GermanText.numericEvidenceTerms("2 Pakete mit 4 Schrauben"))
        assertEquals(setOf("2025"), GermanText.numericEvidenceTerms("Rechnung 2025"))
        assertEquals(setOf("4"), GermanText.numericEvidenceTerms("vor 2 Tagen 4 Schrauben"))
    }
}
