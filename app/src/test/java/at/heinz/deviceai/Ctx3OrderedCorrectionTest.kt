package at.heinz.deviceai

import at.heinz.deviceai.learning.context.*
import org.junit.Assert.*
import org.junit.Test

class Ctx3OrderedCorrectionTest {
    private val a = Ctx3Entity("a", "Fokus App", setOf("Fokus App"), setOf("sie"), setOf("s1"))
    private val b = Ctx3Entity("b", "Liste App", setOf("Liste App"), setOf("sie"), setOf("s2"))
    private val entities = listOf(a, b)

    private fun prepared() = Ctx3Context().apply {
        decide("Fokus App und Liste App", entities, "v1")
        observe(setOf("s1"), entities)
    }

    @Test fun explicitContrastSelectsOnlyThePositiveAlternative() {
        val result = prepared().decide("Nicht die erste, sondern die zweite Option", entities, "v1")
        assertTrue(result is Ctx3Decision.Query)
        result as Ctx3Decision.Query
        assertEquals(setOf("s2"), result.sourceIds)
        assertEquals("Nicht die erste, sondern Liste App", result.query)
    }

    @Test fun rejectedAlternativeCannotBecomeFocus() {
        for (input in listOf("Nicht die erste", "Die erste meine ich nicht", "Die erste nicht",
            "Die erste ist es nicht", "Nicht die davor")) {
            val context = prepared()
            assertTrue(input, context.decide(input, entities, "v1") is Ctx3Decision.Clarify)
            assertTrue(input, context.decide("Wo steht sie?", entities, "v1") is Ctx3Decision.Clarify)
        }
    }

    @Test fun ambiguousAlternativesRequireClarification() {
        for (input in listOf("Die erste oder die zweite", "Die erste und die zweite", "Nicht die erste und nicht die zweite")) {
            assertTrue(input, prepared().decide(input, entities, "v1") is Ctx3Decision.Clarify)
        }
    }

    @Test fun namedCorrectionKeepsItsExistingSourceBoundary() {
        val result = prepared().decide("Nicht Fokus App, sondern Liste App", entities, "v1") as Ctx3Decision.Query
        assertEquals(setOf("s2"), result.sourceIds)
    }

    @Test fun correctionWithoutAlternativesOrAfterSourceChangeCannotGuess() {
        assertTrue(Ctx3Context().decide("Nicht die erste, sondern die zweite", entities, "v1") is Ctx3Decision.Clarify)
        assertTrue(prepared().decide("Nicht die erste, sondern die zweite", entities, "v2") is Ctx3Decision.Clarify)
    }

    @Test fun reversedContrastSelectsTheFirstAlternative() {
        val result = prepared().decide("Nicht die zweite Option, sondern die erste Variante", entities, "v1") as Ctx3Decision.Query
        assertEquals(setOf("s1"), result.sourceIds)
        assertEquals("Nicht die zweite Option, sondern Fokus App", result.query)
    }

    @Test fun unsupportedCorrectionGrammarNeverSelectsByOrder() {
        for (input in listOf("Nicht die erste, die zweite", "Die zweite, nicht die erste",
            "Nicht die erste oder die zweite", "Die erste und nochmal die erste")) {
            assertTrue(input, prepared().decide(input, entities, "v1") is Ctx3Decision.Clarify)
        }
    }

    @Test fun failedReplacementCannotReviveTheRejectedFocus() {
        val single = Ctx3Context().apply { decide("Fokus App", entities, "v1") }
        assertTrue(single.decide("Nicht die erste, sondern die zweite", entities, "v1") is Ctx3Decision.Clarify)
        assertTrue(single.decide("Wo steht sie?", entities, "v1") is Ctx3Decision.Clarify)
        val first = prepared()
        assertTrue(first.decide("Nicht die erste, sondern die davor", entities, "v1") is Ctx3Decision.Clarify)
        assertTrue(first.decide("Nimm das andere", entities, "v1") is Ctx3Decision.Clarify)
    }
}
