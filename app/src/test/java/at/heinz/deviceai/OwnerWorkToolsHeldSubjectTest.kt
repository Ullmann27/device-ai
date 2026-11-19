package at.heinz.deviceai

import at.heinz.deviceai.cognitive.NewsTestFeeds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * „Soll ich daraus einen PDF-Bericht machen?“ – „ja“: the export order the owner's yes hands to the work tools must
 * mean the result that was shown before the suggestion, never the suggestion sentence and never another result.
 * [OwnerWorkTools.heldIsSubject] is what the Activity asks before the offer is made at all. Productive work tools on
 * their host harness.
 */
class OwnerWorkToolsHeldSubjectTest {
    private val result = "Wärmepumpen werden 2026 weiter gefördert: Der Bund zahlt bis zu 7.500 Euro, dazu kommen Landesmittel. " +
        "Der Antrag muss vor dem Einbau gestellt werden."

    private fun tools(): Pair<OwnerWorkToolsHarness, OwnerWorkTools> {
        val host = OwnerWorkToolsHarness()
        return host to OwnerWorkTools(host, now = { NewsTestFeeds.NOW }, zone = { NewsTestFeeds.ZONE })
    }

    @Test fun afterTheOwnersYesTheHeldResultIsSavedNotTheSuggestion() {
        val (host, tools) = tools()
        host.foreignTurn("Recherchiere die Förderung für Wärmepumpen", result)
        tools.holdSubject()
        assertTrue(tools.heldIsSubject)
        // A notice and the suggestion follow the result; then his „ja“ is shown as his turn.
        host.log += "REPLY:Ich habe 3 Quellen-Aussagen als Lernkandidaten erkannt."
        host.log += "REPLY:Soll ich daraus einen PDF-Bericht machen?"
        host.acceptOwnerTurn("ja")
        assertTrue(tools.handleOnHeld(OwnerTalk.PDF_ORDER))
        val saved = host.downloads.values.single().toString(Charsets.UTF_8)
        assertTrue(saved, saved.contains("Der Bund zahlt bis zu 7.500 Euro"))
        assertFalse(saved, saved.contains("PDF-Bericht machen") || saved.contains("Lernkandidaten"))
        assertEquals(listOf("ja", OwnerTalk.PDF_ORDER), host.lines("USER:").takeLast(2))
    }

    @Test fun aSentenceOfTheAppOrAReportIsNothingToOfferAReportOf() {
        val (host, tools) = tools()
        assertFalse("nothing was held", tools.heldIsSubject)
        host.systemTurn("Recherchiere die Förderung für Wärmepumpen", "Der Internetzugang ist AUS, deshalb habe ich nichts abgerufen.")
        tools.holdSubject()
        assertFalse(tools.heldIsSubject)
        // Another chat: what was held there is not this chat's subject.
        host.foreignTurn("Recherchiere die Förderung für Wärmepumpen", result)
        tools.holdSubject()
        host.sessionId = "chat-2"
        assertFalse(tools.heldIsSubject)
    }

    @Test fun whileAResultOfTheWorkToolsIsTheSubjectTheOfferIsNotMade() {
        val (host, tools) = tools()
        host.model = { OwnerWorkGeneration(OwnerMissionHarness.BRIEFING, null, true) }
        assertTrue(tools.handle("Fass mir die Nachrichten von heute zusammen"))
        // The news overview is this lane's own result and still what „das“ means: an export would save it, so a
        // report offer about anything else must not be made on this turn.
        tools.holdSubject()
        assertFalse(tools.heldIsSubject)
    }
}
