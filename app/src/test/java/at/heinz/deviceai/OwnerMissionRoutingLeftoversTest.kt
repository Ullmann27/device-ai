package at.heinz.deviceai

import at.heinz.deviceai.briefing.ContinuumBriefingHarness
import at.heinz.deviceai.cognitive.OwnerMissionPlanner
import at.heinz.deviceai.cognitive.OwnerTaskChainRecipes
import at.heinz.deviceai.sending.FakeContacts
import at.heinz.deviceai.sending.OwnerSendProgress
import at.heinz.deviceai.ui.Command03SendWork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Routing leftovers of the multi-step work (19.11.2026): no whole-sentence lane swallows a further order, the lanes
 * that ask say themselves that they wait, and the stage gets the send lane's real steps.
 */
class OwnerMissionRoutingLeftoversTest {
    // ── Task chain recipes decline a sentence with a further order ──────────────────────────────────────────

    @Test fun aRecipeThatOnlyReadsTheBeginningDeclinesWhenAFurtherOrderFollows() {
        // Said alone: the recipe.
        assertEquals(OwnerTaskChainRecipes.NEWEST_FILE, OwnerTaskChainRecipes.recognise("Such das neueste PDF in Downloads", "Jarvis")?.recipe)
        // With an order behind it the recipe used to run and the rest was dropped without a word.
        assertNull(OwnerTaskChainRecipes.recognise("Such das neueste PDF in Downloads und schick es Anna auf WhatsApp", "Jarvis"))
        assertNull(OwnerTaskChainRecipes.recognise("Such das neueste PDF in Downloads, druck es aus", "Jarvis"))
        assertNull(OwnerTaskChainRecipes.recognise("Zeig mir das neueste Bild in Downloads und lösche es danach", "Jarvis"))
        // The sentence is then read as what it is: a sequence of its orders – never half an order.
        assertEquals(listOf("FILE:NEWEST", "OPEN_FOUND"), OwnerMissionPlanner.plan("Such das neueste PDF in Downloads und öffne es", "Jarvis")?.codes)
        // Both orders are seen by the reading; none of them is lost.
        val reading = checkNotNull(OwnerMissionPlanner.read("Such das neueste PDF in Downloads, druck es aus", "Jarvis"))
        assertEquals(2, reading.orders.size)
        assertNull(reading.plan)
    }

    @Test fun aTailTheRecipeAnswersItselfStaysItsOwn() {
        // Telling or showing what was found is the recipe's own answer.
        assertEquals(OwnerTaskChainRecipes.NEWEST_FILE, OwnerTaskChainRecipes.recognise("Such die neueste PDF in Downloads und sag mir, wie groß sie ist", "Jarvis")?.recipe)
        assertEquals(OwnerTaskChainRecipes.LATEST_APK, OwnerTaskChainRecipes.recognise("Finde die neueste APK und zeig mir die Infos", "Jarvis")?.recipe)
        // The APK recipe never installs and says so itself.
        assertEquals(OwnerTaskChainRecipes.LATEST_APK, OwnerTaskChainRecipes.recognise("Such die neueste APK und installiere sie", "Jarvis")?.recipe)
        // „Rechnung und Mahnung“ is a name, not an order.
        assertNotNull(OwnerTaskChainRecipes.recognise("Such die Datei Rechnung und Mahnung", "Jarvis"))
        // Recipes that match the whole sentence are untouched.
        assertEquals(OwnerTaskChainRecipes.FILE_SEARCH, OwnerTaskChainRecipes.recognise("Such die Datei Vertrag und sag mir, wo sie liegt", "Jarvis")?.recipe)
    }

    // ── The lanes that ask say that they wait ───────────────────────────────────────────────────────────────

    @Test fun theBriefingLaneWaitsWhileItsQuestionIsOpenForTheVeryNextTurn() {
        val h = ContinuumBriefingHarness()
        assertFalse(h.director.awaitingAnswer)
        h.phones = listOf(FakeContacts.phone(1, "Anna Beispiel", "+43 660 1111111"), FakeContacts.phone(2, "Anna Muster", "+43 660 2222222"))
        assertTrue(h.director.handle("Ruf Anna an"))
        assertTrue(h.chat.last(), h.director.awaitingAnswer)
        // Another owner turn of the chat: the question is over.
        h.ownerTurn++
        assertFalse(h.director.awaitingAnswer)
    }

    @Test fun theDeskWaitsWhileItAsksWhichFileIsMeant() {
        val h = OwnerMissionHarness()
        h.deskHost.put("Argon4 Experimente.pdf")
        h.deskHost.put("Argon4 Messreihe.pdf")
        assertFalse(h.desk.awaitingAnswer)
        assertEquals("DESK", h.owner("Öffne das PDF Argon4"))
        assertTrue(h.lastSaid, h.desk.awaitingAnswer)
        h.toolsHost.foreignTurn("Wie spät ist es?", "Es ist kurz nach neun.")
        assertFalse(h.desk.awaitingAnswer)
    }

    // ── Stage: the send lane's three real events ────────────────────────────────────────────────────────────

    @Test fun theStageShowsRecipientChannelAndVerifiedDraftOnlyWhenTheLaneReportedThem() {
        val work = Command03SendWork()
        assertNull(work.snapshot("chat-1"))
        work.started("chat-1", 10L)
        val begun = work.snapshot("chat-1")!!
        assertEquals("RUNNING", begun.getString("phase"))
        assertFalse(begun.getBoolean("recipientResolved") || begun.getBoolean("channelChosen") || begun.getBoolean("draftVerified"))
        assertTrue(begun.isNull("to") && begun.isNull("channel"))
        work.progress("chat-1", OwnerSendProgress.RecipientResolved("Thomas Muster"), 11L)
        work.progress("chat-1", OwnerSendProgress.ChannelChosen("WhatsApp"), 12L)
        val resolved = work.snapshot("chat-1")!!
        assertEquals("Thomas Muster", resolved.getString("to"))
        assertEquals("WhatsApp", resolved.getString("channel"))
        assertTrue(resolved.getBoolean("recipientResolved") && resolved.getBoolean("channelChosen"))
        assertFalse(resolved.getBoolean("draftVerified"))
        work.progress("chat-1", OwnerSendProgress.DraftVerified, 13L)
        assertTrue(work.snapshot("chat-1")!!.getBoolean("draftVerified"))
        assertEquals(13L, work.snapshot("chat-1")!!.getLong("at"))
        // Another chat's event changes nothing, and a new order starts without the old flags.
        work.progress("chat-2", OwnerSendProgress.RecipientResolved("Jemand"), 14L)
        assertEquals("Thomas Muster", work.snapshot("chat-1")!!.getString("to"))
        work.settled("chat-1", OwnerLaneOutcome.Done(OwnerLaneResult.Delivered("WhatsApp", "Thomas Muster", null, OwnerLaneResult.Delivered.State.SENT)), 15L)
        assertEquals("SENT", work.snapshot("chat-1")!!.getString("phase"))
        // After the end no late event revives the order.
        work.progress("chat-1", OwnerSendProgress.DraftVerified, 16L)
        assertEquals("SENT", work.snapshot("chat-1")!!.getString("phase"))
        work.started("chat-1", 20L)
        assertFalse(work.snapshot("chat-1")!!.getBoolean("draftVerified") || work.snapshot("chat-1")!!.getBoolean("recipientResolved"))
        // Nothing but phase, names and flags.
        assertEquals(setOf("phase", "channel", "to", "detail", "at", "recipientResolved", "channelChosen", "draftVerified"),
            work.snapshot("chat-1")!!.keys().asSequence().toSet())
    }
}
