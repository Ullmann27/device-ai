package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ProactiveDateTailRegressionTest {
    private val zone = ZoneId.of("Europe/Vienna")
    private val today = LocalDate.of(2026, 11, 14)
    private val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun dottedClockValuesPreserveToday() {
        for (clock in listOf("0.00", "00.00", "9.05", "18.30", "23.59")) {
            for (suffix in listOf("", " Uhr")) {
                val text = "Termin am 14.11. $clock$suffix"
                assertEquals(text, "event", quest(text)?.memoryId)
            }
        }
    }

    @Test fun quantitiesStaySeparateFromTheYear() {
        for (count in listOf(1, 3, 12, 24, 100, 999)) {
            for (unit in listOf("Gäste", "Personen", "Kinder")) {
                val text = "Geburtstag am 14.11. $count $unit"
                assertNotNull(text, quest(text))
                assertNull("Attached number", quest("Geburtstag am 14.11.$count $unit"))
            }
        }
    }

    @Test fun invalidClocksAndIdentifiersDoNotBecomeQuantities() {
        for (tail in listOf(
            "24:00", "24.00 Uhr", "23.60 Uhr", "18.300 Uhr", "18:000", "18:00:00",
            "18.30.2026", "24 Uhr", "18 Uhrzeit", "18", "26", "18.30/7", "18.30-7"
        )) assertNull(tail, quest("Termin am 14.11. $tail"))
        assertNull(quest("Termin am 14.11.18.30 Uhr"))
        assertNull(quest("Termin am 14.11.3 Gäste"))
        assertNull(quest("Termin-Kennung 7/14.11. 18.30 Uhr"))
    }

    @Test fun explicitYearAndCalendarDayRemainAuthoritative() {
        for (date in listOf("14.11.2025", "14.11. 2027", "13.11.", "24.11.", "14.12.")) {
            assertNull(date, quest("Termin am $date 18.30 Uhr"))
            assertNull(date, quest("Geburtstag am $date 3 Gäste"))
        }
        for (date in listOf("14.11.26", "14.11.20260", "14.11. 20260", "14.11.2026abc")) {
            assertNull(date, quest("Termin am $date"))
        }
        assertNotNull(quest("Termin am 14.11.2026 18.30 Uhr"))
        assertNotNull(quest("Geburtstag am 14.11. 2026 3 Gäste"))
    }

    @Test fun trustAndRelativeFreshnessAreNotRelaxed() {
        val engine = ProactiveSideQuestAndCountdownEngine(zone)
        val text = "Termin am 14.11. 18.30 Uhr"
        for (memory in listOf(
            record(text).copy(provenance = ProvenanceType.MODEL_GENERATED),
            record(text).copy(validationState = ValidationState.CONTRADICTED),
            record(text).copy(privacyClass = PrivacyClass.SENSITIVE),
            record("Heute Abend Termin").copy(updatedAt = now - 86_400_000L)
        )) assertNull(engine.findTimelySideQuestOrNull(listOf(memory), now))
        assertNotNull(engine.findTimelySideQuestOrNull(listOf(record(text)), now))
    }

    @Test fun correctedDateStillRespectsAnnouncementCooldown() {
        val engine = ProactiveSideQuestAndCountdownEngine(zone)
        val memory = record("Geburtstag am 14.11. 3 Gäste")
        assertNotNull(engine.findTimelySideQuestOrNull(listOf(memory), now))
        assertNull(engine.findTimelySideQuestOrNull(listOf(memory), now + 60_000L))
    }

    private fun quest(content: String) = ProactiveSideQuestAndCountdownEngine(zone)
        .findTimelySideQuestOrNull(listOf(record(content)), now)

    private fun record(content: String) = MemoryRecord(
        id = "event", type = MemoryType.EPISODIC, content = content,
        provenance = ProvenanceType.USER_PROVIDED, confidence = 0.95,
        createdAt = now, updatedAt = now, tags = listOf("heute", "abend", "termin"),
        privacyClass = PrivacyClass.PRIVATE
    )
}
