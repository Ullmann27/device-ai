package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class OwnerDeviceClockReplyTest {
    private val now = Instant.parse("2026-11-12T11:27:00Z")
    @Test fun timeUsesDeviceZone() {
        assertEquals("Es ist 12:27 Uhr. (Geräteuhr · Europe/Vienna)",
            OwnerDeviceClockReply.answer("Wie spät ist es?", now, ZoneId.of("Europe/Vienna")))
    }
    @Test fun dateUsesActualLocalDay() {
        assertEquals("Heute ist Freitag, 13. November 2026. (Geräteuhr · Pacific/Auckland)",
            OwnerDeviceClockReply.answer("Welcher Tag ist heute?", now, ZoneId.of("Pacific/Auckland")))
    }
    @Test fun otherTimesAndCompoundActionsStayOutside() {
        for (text in listOf("Wie spät war es gestern?", "Wie spät ist es in Tokio?", "Wie spät ist es und öffne WhatsApp", "Ändere die Uhrzeit"))
            assertNull(text, OwnerDeviceClockReply.answer(text, now, ZoneId.of("UTC")))
    }

    @Test fun midnightUsesTheSuppliedDeviceDateRatherThanUtcDate() {
        val midnight = Instant.parse("2026-12-31T23:30:00Z")
        assertEquals("Heute ist Freitag, 1. Januar 2027, 00:30 Uhr. (Geräteuhr · Europe/Vienna)",
            OwnerDeviceClockReply.answer("Datum und Uhrzeit", midnight, ZoneId.of("Europe/Vienna")))
    }

    @Test fun daylightSavingTransitionUsesDeviceZoneRules() {
        val before = Instant.parse("2026-03-29T00:59:00Z")
        val after = Instant.parse("2026-03-29T01:00:00Z")
        assertEquals("Es ist 01:59 Uhr. (Geräteuhr · Europe/Vienna)",
            OwnerDeviceClockReply.answer("Uhrzeit", before, ZoneId.of("Europe/Vienna")))
        assertEquals("Es ist 03:00 Uhr. (Geräteuhr · Europe/Vienna)",
            OwnerDeviceClockReply.answer("Uhrzeit", after, ZoneId.of("Europe/Vienna")))
    }

    @Test fun clockReplyNeverSwallowsAnAdditionalOwnerAction() {
        for (request in listOf("Datum und Uhrzeit und lösche die Datei", "Sag mir die Uhrzeit und sende Anna eine Nachricht",
                "Wie spät ist es nicht?", "Datum morgen", "Ändere das Datum auf morgen")) {
            assertNull(request, OwnerDeviceClockReply.answer(request, now, ZoneId.of("Europe/Vienna")))
        }
    }
}
