package at.heinz.deviceai.intelligence.maxultra

import org.junit.Assert.*
import org.junit.Test

class Ctx2NegationTest {
    private val facts=listOf("Konrad ist mein Bruder.","Konrad wohnt in Linz.","Paul ist mein Onkel.","Paul wohnt in Wels.","Max ist mein Cousin.")
    @Test fun fullyNegativeNamesNeverBecomeQueriesOrFutureReferents() {
        for(text in listOf("Nicht Konrad.","Ich meinte nicht Max.","Nicht Konrad und nicht Paul.")) {
            val c=Ctx1DialogueContext(facts,true)
            assertEquals(text,Ctx1DialogueContext.Clarify,c.decide(text))
            assertEquals(text,Ctx1DialogueContext.Clarify,c.decide("Wo wohnt er?"))
        }
    }
    @Test fun negativePronounInvalidatesItsEarlierTarget() {
        val c=Ctx1DialogueContext(facts,true)
        c.decide("Wo wohnt Konrad?")
        assertEquals(Ctx1DialogueContext.Clarify,c.decide("Nein, nicht ihn."))
        assertEquals(Ctx1DialogueContext.Clarify,c.decide("Und wo wohnt er?"))
    }
    @Test fun positiveContrastRepeatsQuestionOnlyForUniqueKnownTarget() {
        val c=Ctx1DialogueContext(facts,true)
        c.decide("Wo wohnt Konrad?")
        val q=c.decide("Nicht Konrad, sondern Paul.") as Ctx1DialogueContext.Ask
        assertTrue(q.query.contains("Paul")); assertFalse(q.query.contains("Konrad"))
        assertEquals(setOf(facts[2],facts[3]),q.scope)
        assertEquals(Ctx1DialogueContext.Clarify,c.decide("Nicht Paul, sondern Konrad und Gustav."))
    }
    @Test fun unknownPositiveTargetDoesNotBorrowKnownFacts() {
        val c=Ctx1DialogueContext(facts,true)
        c.decide("Wo wohnt Konrad?")
        assertEquals(Ctx1DialogueContext.Unknown,c.decide("Nicht Konrad, sondern Gustav."))
    }
}
