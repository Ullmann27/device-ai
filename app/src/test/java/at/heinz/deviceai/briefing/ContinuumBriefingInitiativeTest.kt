package at.heinz.deviceai.briefing

import at.heinz.deviceai.briefing.ContinuumBriefingHarness.Companion.TODAY
import at.heinz.deviceai.briefing.ContinuumBriefingHarness.Companion.moment
import at.heinz.deviceai.cognitive.InMemoryOwnerInitiativeStore
import at.heinz.deviceai.cognitive.OwnerInitiative
import at.heinz.deviceai.cognitive.OwnerInitiativeLevel
import at.heinz.deviceai.cognitive.OwnerSuggestionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The briefing lane's own offers – the daily offer and „Soll ich A, B oder C?“ – pass the owner's one initiative
 * policy instead of a second mechanism: nothing in „Zurückhaltend“, and a no is remembered. Real director, real
 * [ContinuumNextSteps] wording; the policy is wired to the harness exactly as the Activity wires it. Without a policy
 * (the host default) the lane behaves as before – that is what every other briefing test runs on.
 */
class ContinuumBriefingInitiativeTest {
    private val h = ContinuumBriefingHarness()
    private val policy = OwnerInitiative(InMemoryOwnerInitiativeStore()) { h.clock.toEpochMilli() }.also { h.initiative = it }

    private fun briefingWithOpenWork(): String {
        h.work = listOf("Argon4.pdf: Entwurf prüfen und speichern")
        assertTrue(h.say("Gib mir mein Briefing"))
        return h.spoken.last()
    }

    @Test fun inNormalTheBriefingEndsWithItsOneQuestionAndCountsAsOneOffer() {
        val spoken = briefingWithOpenWork()
        assertTrue(spoken, spoken.endsWith("Soll ich die offene Arbeit zeigen?"))
        assertEquals(listOf("die offene Arbeit zeigen"), ContinuumBriefingBoard.offers().map { it.label })
        assertEquals(1, h.briefingsGiven)
    }

    @Test fun inZurueckhaltendTheBriefingComesWithoutTheQuestionAndTheDailyOfferIsNotMade() {
        policy.setLevel(OwnerInitiativeLevel.RESERVED)
        val spoken = briefingWithOpenWork()
        assertFalse(spoken, spoken.contains("Soll ich"))
        assertTrue(ContinuumBriefingBoard.offers().isEmpty())
        assertEquals("the briefing itself was asked for and is given", 1, h.briefingsGiven)
        // A „ja“ afterwards is no answer to anything of this lane.
        assertFalse(h.say("ja"))
        h.clock = moment(TODAY.plusDays(1), 7)
        assertNull(h.director.dueOffer(h.clock))
        assertNull(h.director.briefingOffer())
    }

    @Test fun hisNoToTheNextStepsIsRememberedByThePolicy() {
        assertTrue(briefingWithOpenWork().endsWith("Soll ich die offene Arbeit zeigen?"))
        assertTrue(h.say("nein"))
        assertTrue(policy.resting(OwnerSuggestionKind.NEXT_STEPS))
        // The same day he asks again: the facts are said, the question he just declined is not.
        h.clock = moment(TODAY, 15)
        h.work = listOf("Argon4.pdf: Entwurf prüfen und speichern")
        assertTrue(h.say("Was steht heute an?"))
        assertFalse(h.spoken.last(), h.spoken.last().contains("Soll ich"))
        // Two days later the lane asks again.
        h.clock = moment(TODAY.plusDays(2), 9)
        assertTrue(h.say("Gib mir mein Briefing"))
        assertTrue(h.spoken.last(), h.spoken.last().endsWith("Soll ich die offene Arbeit zeigen?"))
    }

    @Test fun hisNoToTheDailyOfferRestsItForADay() {
        assertEquals(ContinuumBriefingDirector.OFFER_TEXT, h.director.briefingOffer())
        assertTrue(h.say("nein danke"))
        assertTrue(policy.resting(OwnerSuggestionKind.BRIEFING))
        h.clock = moment(TODAY.plusDays(1), 6)
        assertNull("within a day of his no", h.director.dueOffer(h.clock))
        h.clock = moment(TODAY.plusDays(1), 9)
        assertNotNull(h.director.dueOffer(h.clock))
    }

    @Test fun hisYesIsNotedAndStartsTheBriefingAsBefore() {
        assertEquals(ContinuumBriefingDirector.OFFER_TEXT, h.director.briefingOffer())
        assertTrue(h.say("ja"))
        assertFalse(policy.resting(OwnerSuggestionKind.BRIEFING))
        assertEquals(1, h.briefingsGiven)
    }
}
