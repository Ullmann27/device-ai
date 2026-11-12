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
}
