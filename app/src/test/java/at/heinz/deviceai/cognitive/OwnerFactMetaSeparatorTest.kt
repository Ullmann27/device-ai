package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.OwnerMemoryPerspective
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerFactMetaSeparatorTest {
    @Test
    fun separatesTrailingDanachFragenInstruction() {
        val split = OwnerFactMetaSeparator.split(
            "Mein Testwort lautet Nebel 731.“ Danach fragen: „Wie lautet mein Testwort?“"
        )
        assertEquals("Mein Testwort lautet Nebel 731.", split.fact)
        assertTrue(split.instruction!!.startsWith("Danach fragen:"))
    }

    @Test
    fun separatesFragMichSpaeterVariant() {
        val split = OwnerFactMetaSeparator.split(
            "Mein Lieblingsgericht ist Kaiserschmarrn. Frag mich später: Was esse ich gern?"
        )
        assertEquals("Mein Lieblingsgericht ist Kaiserschmarrn.", split.fact)
        assertEquals("Frag mich später: Was esse ich gern?", split.instruction)
    }

    @Test
    fun cleanFactStaysUntouched() {
        val clean = "Mein Testwort lautet Nebel 731."
        val split = OwnerFactMetaSeparator.split(clean)
        assertEquals(clean, split.fact)
        assertNull(split.instruction)
        assertFalse(OwnerFactMetaSeparator.hasInstruction("Meine Tochter fragt oft nach Mathe-Hilfe."))
        assertFalse(OwnerFactMetaSeparator.hasInstruction("Ich prüfe jeden Montag: die Backups."))
    }

    @Test
    fun displayOfLegacyRecordShowsOnlyFactAndMarksQuotation() {
        val shown = OwnerMemoryPerspective.display(
            "Mein Testwort lautet Nebel 731.“ Danach fragen: „Wie lautet mein Testwort?“"
        )
        assertTrue(shown.startsWith("Dein Testwort lautet Nebel 731."))
        assertFalse(shown.contains("Danach fragen"))
        assertTrue(shown.contains("gespeichert: „Mein Testwort lautet Nebel 731.“"))
    }
}
