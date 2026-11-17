package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class OwnerMemoryPerspectiveTest {
    @Test fun confirmedFirstPersonCodeBelongsToUserNotAssistant() {
        val text = OwnerMemoryPerspective.display("Mein Testkürzel lautet NEBEL-731.")
        assertTrue(text.startsWith("Dein Testkürzel lautet NEBEL-731."))
        assertTrue(text.contains("gespeichert: „Mein Testkürzel lautet NEBEL-731.“"))
        assertFalse(text.startsWith("Mein"))
    }

    @Test fun preservesPossessiveCaseAndExactOriginalEvidence() {
        val cases = mapOf(
            "Meine Lieblingsfarbe ist Blau." to "Deine Lieblingsfarbe ist Blau.",
            "Meinem Bruder gefällt Rot." to "Deinem Bruder gefällt Rot.",
            "Meinen Code kenne ich." to "Deinen Code kenne ich.",
            "Meiner Schwester gehört das Fahrrad." to "Deiner Schwester gehört das Fahrrad."
        )
        cases.forEach { (fact, expected) ->
            val shown = OwnerMemoryPerspective.display(fact)
            assertTrue(shown, shown.startsWith(expected))
            assertTrue(shown, shown.contains(fact))
        }
    }

    @Test fun avoidsGrammaticallyWrongTransformationAndDoesNotInventFacts() {
        val original = "Ich wohne in Graz."
        val rendered = OwnerMemoryPerspective.display(original)
        assertEquals("Du hast mir gesagt: „$original“", rendered)
        assertEquals("Orion ist ein Sternbild.",
            OwnerMemoryPerspective.display("Orion ist ein Sternbild."))
        assertEquals("Meinung bleibt unverändert.",
            OwnerMemoryPerspective.display("Meinung bleibt unverändert."))
    }
}
