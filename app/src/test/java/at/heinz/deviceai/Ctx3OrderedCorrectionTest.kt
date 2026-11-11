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

    @Test fun relativeContrastCannotSelectTheRejectedAlternative() {
        for ((input, selected) in listOf(
            "Nicht die zweite, sondern die andere" to "s1",
            "Nicht die erste, sondern die davor" to "s2",
            "Nicht die erste, sondern die andere" to "s2",
            "Nicht die zweite, sondern die davor" to "s2"
        )) {
            val context = prepared().apply { observe(setOf(selected), entities) }
            assertTrue(input, context.decide(input, entities, "v1") is Ctx3Decision.Clarify)
            assertTrue(input, context.decide("Nimm das andere", entities, "v1") is Ctx3Decision.Clarify)
        }
    }

    @Test fun repeatingTheRejectedOrdinalIsNotAReplacement() {
        for (input in listOf("Nicht die erste, sondern die erste", "Nicht die zweite, sondern die zweite")) {
            val context = prepared()
            assertTrue(input, context.decide(input, entities, "v1") is Ctx3Decision.Clarify)
            assertTrue(input, context.decide("Nimm das andere", entities, "v1") is Ctx3Decision.Clarify)
        }
    }

    @Test fun negatedPredicateRetainsTheExplicitOrdinalAndAlternatives() {
        for ((input, source, nextSource) in listOf(
            Triple("warum startet die zweite nicht?", "s2", "s1"),
            Triple("geht die erste nicht?", "s1", "s2"),
            Triple("die erste startet nicht?", "s1", "s2"),
            Triple("die zweite geht nicht?", "s2", "s1")
        )) {
            val context = prepared()
            val result = context.decide(input, entities, "v1")
            assertTrue(input, result is Ctx3Decision.Query)
            assertEquals(input, setOf(source), (result as Ctx3Decision.Query).sourceIds)
            assertTrue(input, result.query.endsWith("nicht?"))
            val next = context.decide("Nimm das andere", entities, "v1") as Ctx3Decision.Query
            assertEquals(input, setOf(nextSource), next.sourceIds)
        }
    }

    @Test fun sentenceInitialQuestionWordsRetainTheOrdinalReferent() {
        for ((input, source) in listOf(
            "Warum startet die zweite nicht?" to "s2",
            "Startet die zweite nicht?" to "s2",
            "Geht die erste nicht?" to "s1"
        )) {
            val result = prepared().decide(input, entities, "v1")
            assertTrue(input, result is Ctx3Decision.Query)
            assertEquals(input, setOf(source), (result as Ctx3Decision.Query).sourceIds)
        }
        assertTrue(prepared().decide("Warum startet Gustav nicht?", entities, "v1") is Ctx3Decision.Abstain)
    }

    @Test fun explicitSelectionRejectionsStillInvalidateTheCandidateSet() {
        for (input in listOf("Die erste nicht", "Die erste ist es nicht", "Nimm die erste nicht",
            "Nein, die zweite nicht", "Bitte die zweite nicht", "Die erste meine ich nicht")) {
            val context = prepared()
            assertTrue(input, context.decide(input, entities, "v1") is Ctx3Decision.Clarify)
            assertTrue(input, context.decide("Wo steht sie?", entities, "v1") is Ctx3Decision.Clarify)
            assertTrue(input, context.decide("Nimm das andere", entities, "v1") is Ctx3Decision.Clarify)
        }
    }

    @Test fun otherNegatedChoiceVerbsCannotSelectTheirRejectedCandidate() {
        for (input in listOf("ich nehme die erste nicht", "wähle die zweite nicht", "ich will die zweite nicht")) {
            val context = prepared()
            assertTrue(input, context.decide(input, entities, "v1") is Ctx3Decision.Clarify)
            assertTrue(input, context.decide("Nimm das andere", entities, "v1") is Ctx3Decision.Clarify)
        }
    }
}
