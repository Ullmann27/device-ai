package at.heinz.deviceai.voice

import at.heinz.deviceai.cognitive.JarvisVoiceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextShaperTest {
    private fun s(text: String) = SpeechTextShaper.shape(text)

    @Test fun numbersTimesDatesAndUnitsAreSpokenInGerman() {
        assertEquals("Der Termin ist um 14 Uhr 30.", s("Der Termin ist um 14:30 Uhr."))
        assertEquals("Start um 6 Uhr, Ende 18 Uhr 5.", s("Start um 06:00, Ende 18:05 Uhr."))
        assertEquals("Frei sind 39 Komma 3 Gigabyte von 512 Gigabyte.", s("Frei sind 39,3 GB von 512 GB."))
        assertEquals("Erlaubt sind 130 Kilometer pro Stunde.", s("Erlaubt sind 130 km/h."))
        assertEquals("Akku 87 Prozent, 41 Grad Celsius.", s("Akku 87 %, 41 °C."))
        assertEquals("Am 19. November 2026 kostet es 1250 Euro.", s("Am 19.11.2026 kostet es 1.250 €."))
        assertEquals("Etwa 10 bis 20 Minuten.", s("Etwa 10–20 min."))
        assertEquals("Spielstand 3:1 und Version 0.3.49.", s("Spielstand 3:1 und Version 0.3.49."))
    }

    @Test fun abbreviationsAreSpokenOutAndSentenceEndsArePreserved() {
        assertEquals("Zum Beispiel Doktor Huber, das heißt der Hausarzt.", s("Zum Beispiel Dr. Huber, d. h. der Hausarzt."))
        assertEquals("Obst, zum Beispiel Äpfel, Birnen und so weiter. Dann gehen wir.",
            s("Obst, z.B. Äpfel, Birnen usw. Dann gehen wir."))
        assertEquals("Etwa circa 3 Millionen Nutzer, beziehungsweise mehr.", s("Etwa ca. 3 Mio. Nutzer, bzw. mehr."))
        // Known limit: a capitalised noun after „usw.“ cannot be told from a new sentence; no pause is invented.
        assertEquals("Äpfel, Birnen und so weiter Kunden zahlen.", s("Äpfel, Birnen usw. Kunden zahlen."))
    }

    @Test fun markdownListsAndLinksAreReadNotSpelled() {
        val reply = "### Ergebnis\n**Wichtig:** drei Punkte\n- Akku prüfen\n- Speicher frei machen\n1. Neustart\n2) Test\n" +
            "Mehr unter [Heise](https://www.heise.de/news/x.html) oder https://www.example.org/a/b?c=d."
        assertEquals("Ergebnis.\nWichtig: drei Punkte.\nAkku prüfen.\nSpeicher frei machen.\nErstens, Neustart.\n" +
            "Zweitens, Test.\nMehr unter Heise oder example.org.", s(reply))
        assertFalse(s(reply).contains("*"))
        assertFalse(s(reply).contains("#"))
        assertFalse(s(reply).contains("http"))
    }

    @Test fun emojiAndShoutingAreCalmed() {
        assertEquals("Fertig! Wirklich?", s("Fertig!!! 🎉 Wirklich?!?"))
        assertEquals("Lade…", s("Lade..."))
        assertEquals("Tempo 100 Prozent auf 96 Prozent.", s("Tempo 100 % → 96 %."))
    }

    @Test fun codeBlocksAreLeftToTheChunkerAndShapingIsIdempotent() {
        val reply = "Hier:\n```kotlin\nval x = 39,3 // 14:30\n```\nFertig um 14:30 Uhr, ca. 2,5 GB."
        val shaped = s(reply)
        assertTrue(shaped.contains("val x = 39,3 // 14:30"))
        assertEquals(shaped, s(shaped))
        assertTrue(JarvisVoiceProfile.speakableChunks(shaped).pieces.joinToString(" ").contains("Codebeispiel ausgelassen"))
        for (text in listOf("Der Termin ist um 14:30 Uhr.", "Frei sind 39,3 GB.", "Obst, z.B. Äpfel usw. Dann.",
            "- a\n- b", "Am 19.11.2026 um 9:30 Uhr"))
            assertEquals(text, s(s(text)), s(text))
        assertEquals("", s("   "))
    }
}
