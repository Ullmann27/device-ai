package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

/** Field screenshot 12.11.2026: raw Markdown in the chat bubble. */
class ChatReplyFormatterTest {
    @Test fun theFieldReplyLosesItsMarkdownMarkers() {
        val raw = "Kurz gesagt: Ich bin ein Assistent, der **auf deinem Gerät läuft**.\n\n" +
            "### Was ich tun kann (Meine Fähigkeiten):\n\n" +
            "*   **Informationen abrufen (Lesen):** Ich kann dir Infos geben, wie zum Beispiel:\n" +
            "    *   Welches Modell du hast (`get_device_info`).\n" +
            "- Aktionen nur mit __Bestätigung__"
        val shown = ChatReplyFormatter.plain(raw)
        assertFalse(shown, shown.contains("**"))
        assertFalse(shown, shown.contains("###"))
        assertFalse(shown, shown.contains("`"))
        assertFalse(shown, shown.contains("__"))
        assertTrue(shown, shown.contains("Was ich tun kann (Meine Fähigkeiten):"))
        assertTrue(shown, shown.contains("• Informationen abrufen (Lesen):"))
        assertTrue(shown, shown.contains("    • Welches Modell du hast (get_device_info)."))
        assertTrue(shown, shown.contains("• Aktionen nur mit Bestätigung"))
    }

    @Test fun plainTextArithmeticAndCodeBlocksStayUntouched() {
        assertEquals("2 * 3 = 6", ChatReplyFormatter.plain("2 * 3 = 6"))
        assertEquals("Akku: 66 %", ChatReplyFormatter.plain("Akku: 66 %"))
        val code = "```kotlin\nval x = **y**\n# not a heading\n```"
        assertEquals(code, ChatReplyFormatter.plain(code))
        assertEquals("", ChatReplyFormatter.plain(""))
    }
}
