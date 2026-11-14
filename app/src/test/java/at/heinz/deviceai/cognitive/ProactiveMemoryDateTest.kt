package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ProactiveMemoryDateTest {
    private val zone = ZoneId.of("Europe/Vienna")

    @Test fun daySuffixCannotBecomeTodaysEvent() {
        val today = LocalDate.of(2026, 11, 4)
        for (date in listOf("14.11.", "24.11.", "104.11.")) {
            assertNull(date, quest("Termin am $date", today))
        }
        assertNotNull(quest("Termin am 4.11.", today))
    }

    @Test fun explicitYearMustMatchToday() {
        val today = LocalDate.of(2026, 11, 14)
        for (date in listOf("14.11.2025", "14.11.2027", "14.11. 2027")) {
            assertNull(date, quest("Termin am $date", today))
        }
        assertNotNull(quest("Termin am 14.11.2026", today))
    }

    @Test fun supportedDateFormsPreserveCurrentEvent() {
        val today = LocalDate.of(2026, 1, 4)
        for (date in listOf("4.1.", "04.01.", "4.1.2026", "04.01. 2026")) {
            assertEquals(date, "event", quest("Termin am $date um 18 Uhr", today)?.memoryId)
        }
    }

    @Test fun malformedNumericExtensionsDoNotBecomeYearlessDates() {
        val today = LocalDate.of(2026, 11, 14)
        for (date in listOf("14.11.26", "14.11.20260", "14.11. 20260", "14.11.2026abc")) {
            assertNull(date, quest("Termin am $date", today))
        }
    }

    @Test fun explicitClockTailDoesNotBecomeAShortYear() {
        val today = LocalDate.of(2026, 11, 14)
        for (time in listOf("0:00", "00:00", "9:05", "18:00", "23:59", "18:00 Uhr")) {
            assertNotNull(time, quest("Termin am 14.11. $time", today))
        }
    }

    @Test fun explicitHourTailDoesNotBecomeAShortYear() {
        val today = LocalDate.of(2026, 11, 14)
        for (time in listOf("0 Uhr", "9 Uhr", "18 Uhr", "23 Uhr")) {
            assertNotNull(time, quest("Termin am 14.11. $time", today))
        }
    }

    @Test fun invalidTimesAndBareShortYearsRemainRejected() {
        val today = LocalDate.of(2026, 11, 14)
        for (tail in listOf("24:00", "23:60", "18:000", "18:00:00", "24 Uhr", "18 Uhrzeit", "18", "26")) {
            assertNull(tail, quest("Termin am 14.11. $tail", today))
        }
    }

    @Test fun identifiersAndDateFragmentsAreNotCalendarDates() {
        val today = LocalDate.of(2026, 11, 14)
        for (date in listOf("x14.11.2026", "2026-14.11.", "7/14.11.", "14.11.2026/7")) {
            assertNull(date, quest("Termin-Kennung $date", today))
        }
    }

    @Test fun multipleDatesRequireAnActualCurrentDate() {
        val today = LocalDate.of(2026, 11, 14)
        assertNull(quest("Termin am 14.11.2025 und 14.11.2027", today))
        assertNotNull(quest("Termin am 13.11.2026 oder 14.11.2026", today))
    }

    @Test fun leapDayUsesTheWrittenYear() {
        val today = LocalDate.of(2028, 2, 29)
        assertNotNull(quest("Termin am 29.2.2028", today))
        assertNull(quest("Termin am 29.2.2027", today))
        assertNull(quest("Termin am 30.2.2028", today))
    }

    @Test fun localDayAndRelativeFreshnessRemainEnforced() {
        val now = Instant.parse("2026-11-13T23:30:00Z").toEpochMilli()
        val today = LocalDate.of(2026, 11, 14)
        val engine = ProactiveSideQuestAndCountdownEngine(zone)
        assertNotNull(engine.findTimelySideQuestOrNull(listOf(record("Termin am 14.11.2026", now)), now))
        assertNull(quest("Termin am 13.11.2026", today))
        assertNull(ProactiveSideQuestAndCountdownEngine(zone).findTimelySideQuestOrNull(
            listOf(record("Heute Abend Termin", now - 86_400_000)), now))
    }

    @Test fun dateMatchingDoesNotBroadenTrustOrFormats() {
        val today = LocalDate.of(2026, 11, 14)
        val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        for (record in listOf(
            record("Termin am 14.11.2026", now).copy(validationState = ValidationState.CONTRADICTED),
            record("Termin am 14.11.2026", now).copy(provenance = ProvenanceType.MODEL_GENERATED),
            record("Termin am 14.11.2026", now).copy(privacyClass = PrivacyClass.SENSITIVE)
        )) assertNull(ProactiveSideQuestAndCountdownEngine(zone).findTimelySideQuestOrNull(listOf(record), now))
        assertNull(quest("Termin 2026-11-14T18:00:00+01:00", today))
    }

    private fun quest(content: String, today: LocalDate): ProactiveSideQuestAndCountdownEngine.ProactiveSideQuest? {
        val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        return ProactiveSideQuestAndCountdownEngine(zone).findTimelySideQuestOrNull(listOf(record(content, now)), now)
    }

    private fun record(content: String, now: Long) = MemoryRecord(
        id = "event", type = MemoryType.EPISODIC, content = content,
        provenance = ProvenanceType.USER_PROVIDED, confidence = 0.95,
        createdAt = now, updatedAt = now, tags = listOf("heute", "abend", "termin"),
        privacyClass = PrivacyClass.PRIVATE
    )
}
