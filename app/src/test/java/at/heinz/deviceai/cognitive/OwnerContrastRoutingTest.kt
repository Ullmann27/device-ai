package at.heinz.deviceai.cognitive

import at.heinz.deviceai.BootstrapBrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Code63: an explicit contrast names what to open AND what to leave closed. Before the resolver the
 * app lanes saw only "a negation" and dropped the whole request (probe on 3613a23a7):
 * „Öffne nicht WhatsApp, sondern Chrome“ → NotComputerCommand / „Der verneinte App-Auftrag wird nicht ausgeführt.“
 */
class OwnerContrastRoutingTest {
    private fun computer(text: String) = OwnerComputerIntentParser.parse(text)
    private fun opened(text: String): ComputerApp? =
        (computer(text) as? OwnerComputerIntentParser.Result.Ready)?.intent?.takeIf { it.goal == ComputerGoal.OPEN }?.app

    @Test fun theAffirmativePartOfAContrastIsOpenedAndTheNegatedAppNever() {
        listOf(
            "Öffne nicht WhatsApp, sondern Chrome",
            "Öffne nicht WhatsApp sondern Chrome",
            "Öffne Chrome statt WhatsApp",
            "Öffne Chrome anstatt WhatsApp",
            "Öffne Chrome, nicht WhatsApp",
            "Öffne Chrome, aber nicht WhatsApp",
            "Bitte öffne nicht WhatsApp, sondern Chrome.",
            "Starte bitte nicht WhatsApp, sondern Chrome"
        ).forEach { utterance ->
            assertEquals(utterance, ComputerApp.CHROME, opened(utterance))
        }
    }

    @Test fun aNegatedAppIsNeverOpenedWithoutAnAffirmativeTarget() {
        listOf("Öffne nicht WhatsApp", "Öffne WhatsApp nicht", "Öffne keinesfalls Chrome",
            "Öffne nicht WhatsApp und nicht Chrome").forEach { utterance ->
            assertFalse(utterance, computer(utterance) is OwnerComputerIntentParser.Result.Ready)
        }
    }

    @Test fun contradictoryOrAmbiguousContrastsStartNothing() {
        listOf(
            "Öffne nicht Chrome, sondern Chrome",
            "Öffne nicht Chrome, sondern Google Chrome",
            "Öffne nicht WhatsApp, sondern nicht Chrome",
            "Öffne nicht WhatsApp, sondern Chrome und Facebook",
            "Öffne nicht WhatsApp, sondern Chrome und suche nach Wetter",
            "Öffne Chrome statt WhatsApp statt Facebook"
        ).forEach { utterance ->
            assertFalse(utterance, computer(utterance) is OwnerComputerIntentParser.Result.Ready)
        }
    }

    @Test fun aContrastNeverTurnsIntoASearchOrAMessage() {
        // Only a plain open is accepted from a contrast; anything richer keeps the refusal.
        assertFalse(computer("Suche nicht in Chrome, sondern in WhatsApp nach Anna")
            is OwnerComputerIntentParser.Result.Ready)
        assertFalse(computer("Schreibe nicht Anna, sondern Bernd in WhatsApp: Hallo")
            is OwnerComputerIntentParser.Result.Ready)
    }

    @Test fun courtesyWordsAreNotASecondStep() {
        assertEquals(ComputerApp.WHATSAPP, opened("Öffne WhatsApp bitte"))
        assertEquals(ComputerApp.CHROME, opened("Öffne mal Chrome"))
        // A real second step is still refused.
        assertTrue(computer("Öffne WhatsApp und dann lösche alle Chats")
            is OwnerComputerIntentParser.Result.Unsupported)
    }

    @Test fun genericAppLookupKeepsTheWantedAppAndDropsTheExcludedOne() {
        fun lookup(text: String) = OwnerAppLookupIntent.inspect(text) as? OwnerAppLookupIntent.Intake.Lookup
        assertEquals("Signal", lookup("Öffne nicht Telegram, sondern Signal")?.query)
        assertEquals("Signal", lookup("Öffne Signal statt Telegram")?.query)
        assertEquals("Kamera", lookup("Mach nicht die Galerie auf, sondern die Kamera")?.query)
        assertTrue(lookup("Öffne nicht Telegram, sondern Signal")!!.opening)
        // Unchanged refusals: plain negation, contradiction, positional reference under a contrast.
        assertNull(OwnerAppLookupIntent.inspect("Öffne nicht Telegram"))
        assertNull(OwnerAppLookupIntent.inspect("Öffne nicht Signal, sondern Signal"))
        assertNull(OwnerAppLookupIntent.inspect("Öffne nicht die zweite, sondern die erste"))
    }

    @Test fun downloadsInsteadOfPicturesNeverUsesAPictureTool() {
        // Owner regression „Downloads statt Bilder“: the read-only route lists Downloads, nothing else.
        listOf(
            "Öffne nicht die Bilder, sondern die Downloads",
            "Öffne die Downloads statt der Bilder",
            "Downloads statt Bilder",
            "Zeig ma ned die Büdl, sondern die Downloads"
        ).forEach { utterance ->
            assertFalse(utterance, computer(utterance) is OwnerComputerIntentParser.Result.Ready &&
                (computer(utterance) as OwnerComputerIntentParser.Result.Ready).intent.app == ComputerApp.GALLERY)
            val decision = BootstrapBrain.decide(utterance, emptyList())
            assertEquals(utterance, "list_files", decision.tool)
            assertEquals(utterance, "/sdcard/Download", decision.arguments["path"])
        }
    }

    @Test fun resolverRefusesEverythingThatIsNotOneWantedAndOneExcludedTarget() {
        assertNotNull(OwnerContrastResolver.resolve("Öffne nicht WhatsApp, sondern Chrome"))
        assertEquals("Öffne Chrome", OwnerContrastResolver.resolve("Öffne nicht WhatsApp, sondern Chrome")?.affirmative)
        assertEquals("WhatsApp", OwnerContrastResolver.resolve("Öffne Chrome statt WhatsApp")?.rejected)
        assertEquals("Mach die Kamera auf",
            OwnerContrastResolver.resolve("Mach nicht die Galerie auf, sondern die Kamera")?.affirmative)
        listOf(
            "Öffne Chrome",
            "Ich will nicht, dass du Chrome öffnest, sondern warte",
            "Öffne nicht WhatsApp, sondern schreibe Anna: nicht jetzt",
            "Warum öffnest du nicht Chrome statt WhatsApp?",
            "Öffne nicht WhatsApp, sondern Chrome, sondern Facebook"
        ).forEach { assertNull(it, OwnerContrastResolver.resolve(it)) }
    }
}
