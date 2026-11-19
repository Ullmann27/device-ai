package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.OwnerInternetAccess.Ask
import at.heinz.deviceai.cognitive.OwnerInternetAccess.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerInternetAccessTest {
    /** The owner's own sentence from the Fold7 (19.11.2026), as dictated. */
    private val field = "ja wie mache ich das jetzt dass du den Zugriff kriegst hier im Chat einfach ohne dass du sagst ich muss " +
        "internetrecherche aktivieren und das geht nicht und damit du frei im Internet wenn ich mit dir hier Konversation " +
        "führe im Chat du wenn du Informationen brauchst im Hintergrund einfach im Internet nachsehen kannst"

    @Test fun theFieldSentenceAndItsRelativesAreAboutTheAccess() {
        for (said in listOf(field, "Darfst du ins Internet?", "Du darfst immer ins Internet", "Wie gebe ich dir Zugriff aufs Internet?",
            "Aktiviere die Internetrecherche", "Schalte den Internetzugriff ein", "Continuum, ich erlaube dir das Internet dauerhaft",
            "Wie bekommst du Zugang zum Internet?")) assertEquals(said, Ask.ALLOW, OwnerInternetAccess.parse(said))
        for (said in listOf("Internet aus", "Schalte das Internet aus", "Stell dein Internet bitte ab"))
            assertEquals(said, Ask.TURN_OFF, OwnerInternetAccess.parse(said))
        for (said in listOf("Internet auf Nachfrage", "Stell das Internet nur auf Nachfrage",
            "Frag mich vorher, bevor du ins Internet gehst")) assertEquals(said, Ask.ASK_EACH_TIME, OwnerInternetAccess.parse(said))
    }

    @Test fun anOrderForSomethingFromTheInternetKeepsItsRoute() {
        for (said in listOf("Such im Internet nach dem Wetter in Linz", "Recherchiere die neuesten Entwicklungen zu Android-KI",
            "Kannst du im Internet nachsehen, wie das Wetter wird?", "Prüf meine Internetverbindung", "Wie schnell ist mein Internet?",
            "Antworte ohne Internet", "Geh dafür nicht ins Internet", "Öffne Chrome", "Hallo", "Was ist ein Netzteil?",
            "Schalte das WLAN aus", "Internet aus und dann such nach Linz", ""))
            assertNull(said, OwnerInternetAccess.parse(said))
        assertNull(OwnerInternetAccess.parse("du darfst ins internet ".repeat(30)))
    }

    @Test fun wideningIsOnlyOfferedAndNarrowingIsDoneAtOnce() {
        val asking = State(InternetAccessMode.ASK, cloudBrain = true, cloudWebSearch = false)
        val offer = OwnerInternetAccess.reply(Ask.ALLOW, asking)
        assertTrue(offer.offer)
        assertNull("nothing is switched without the owner's yes", offer.set)
        assertTrue(offer.text, offer.text.contains("„Auf Nachfrage“") && offer.text.contains("Soll ich ihn auf „Immer an“ stellen?"))
        val off = OwnerInternetAccess.reply(Ask.ALLOW, State(InternetAccessMode.OFF, false, false))
        assertTrue(off.offer && off.text.contains("„Aus“"))
        assertEquals(InternetAccessMode.OFF, OwnerInternetAccess.reply(Ask.TURN_OFF, asking).set)
        assertEquals(InternetAccessMode.ASK, OwnerInternetAccess.reply(Ask.ASK_EACH_TIME, State(InternetAccessMode.ALWAYS_ON, false, false)).set)
        assertNull(OwnerInternetAccess.reply(Ask.TURN_OFF, State(InternetAccessMode.OFF, false, false)).set)
        assertNull(OwnerInternetAccess.reply(Ask.ASK_EACH_TIME, asking).set)
    }

    @Test fun theAnswerNamesTheRealStateAndTheMissingCloudSwitch() {
        val open = OwnerInternetAccess.reply(Ask.ALLOW, State(InternetAccessMode.ALWAYS_ON, cloudBrain = true, cloudWebSearch = false))
        assertFalse(open.offer)
        assertTrue(open.text, open.text.contains("steht schon auf „Immer an“") && open.text.contains("Cloud-Websuche ein") &&
            open.text.contains("kostet"))
        val ready = OwnerInternetAccess.reply(Ask.ALLOW, State(InternetAccessMode.ALWAYS_ON, cloudBrain = true, cloudWebSearch = true))
        assertTrue(ready.text, ready.text.contains("sehe im Gespräch selbst nach") && !ready.text.contains("Cloud-Websuche ein"))
        val local = OwnerInternetAccess.reply(Ask.ALLOW, State(InternetAccessMode.ALWAYS_ON, cloudBrain = false, cloudWebSearch = false))
        assertTrue(local.text, local.text.contains("Such im Internet nach") && !local.text.contains("Cloud"))
        // No jargon and no English in any of the sentences.
        for (text in listOf(open.text, ready.text, local.text, OwnerInternetAccess.KEPT,
            OwnerInternetAccess.allowed(State(InternetAccessMode.ALWAYS_ON, true, false))))
            assertFalse(text, Regex("(?i)untrusted|https-quelle|web recherche|ALWAYS_ON").containsMatchIn(text))
    }

    @Test fun onlyAPlainYesOrNoAnswersTheOffer() {
        for (said in listOf("Ja", "ja bitte", "Mach das", "Okay", "Ja, mach das!")) assertEquals(said, true, OwnerInternetAccess.answer(said))
        for (said in listOf("Nein", "lieber nicht", "Lass das")) assertEquals(said, false, OwnerInternetAccess.answer(said))
        for (said in listOf("Ja und such gleich nach Linz", "Wie spät ist es?", "vielleicht")) assertNull(said, OwnerInternetAccess.answer(said))
    }
}
