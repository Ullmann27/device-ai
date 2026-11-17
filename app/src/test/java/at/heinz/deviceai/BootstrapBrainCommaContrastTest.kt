package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** M1-Grenze: Komma-Kontrast ", nicht …" trennte keine Klausel. */
class BootstrapBrainCommaContrastTest {
    private fun tool(s: String) = BootstrapBrain.decide(s, emptyList()).tool

    @Test fun commaContrastKeepsTheAffirmativeHead() {
        assertEquals("list_files", tool("Zeig mir die Downloads, nicht die Bilder"))
        assertEquals("get_battery_info", tool("Prüfe den Akku, aber nicht den Speicher"))
        assertEquals("get_storage_info", tool("Prüfe den Speicher, jedoch keine Akkuwerte"))
    }

    @Test fun subordinateCommaAfterNegationStaysNegated() {
        assertNull(tool("Ich will nicht, dass du meine Downloads öffnest"))
        assertNull(tool("Zeig mir nicht, welche Downloads ich habe"))
        assertNull(tool("Zeig nicht die Downloads, nicht die Apps"))
    }
}
