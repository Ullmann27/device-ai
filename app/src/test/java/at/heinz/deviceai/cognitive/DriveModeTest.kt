package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveModeTest {
    @Test fun wholeUtteranceCommandsAreRecognisedWithCaseAndPunctuationTolerance() {
        for (text in listOf(
            "Fahrmodus an", "fahrmodus an", "FAHRMODUS AN!", "  Fahrmodus an.  ", "Fahrmodus ein", "Fahrmodus, ein.",
            "Fahr-Modus an", "Fahr Modus ein", "Ich fahre jetzt los", "ich fahre jetzt los.", "Ich fahre jetzt los!",
        )) {
            assertEquals(text, DriveMode.Command.ENTER, DriveMode.command(text))
        }
        for (text in listOf("Fahrmodus aus", "Fahrmodus aus.", "fahrmodus AUS", "Fahr-Modus aus!", "Ich bin angekommen", "Ich bin angekommen.")) {
            assertEquals(text, DriveMode.Command.EXIT, DriveMode.command(text))
        }
    }

    @Test fun sentencesThatOnlyMentionOrNegateTheModeAreNotCommands() {
        for (text in listOf(
            "Fahrmodus nicht einschalten",
            "Fahrmodus nicht an",
            "Fahrmodus nicht aus",
            "Kein Fahrmodus an",
            "Fahrmodus bitte nie an",
            "Was ist der Fahrmodus?",
            "Mach bitte den Fahrmodus an",
            "Fahrmodus an und Licht aus",
            "Fahrmodus an oder aus?",
            "Fahrmodus",
            "an",
            "Fahrmodus anders",
            "Fahrmodusan",
            "Ich bin noch nicht angekommen",
            "Ich bin angekommen und parke jetzt",
            "Ich fahre jetzt nicht los",
            "Ich fahre jetzt los nach Wien",
            "Anna schreibt: Fahrmodus aus",
            "Sag Fahrmodus aus",
            "",
            "   ",
            "?!.",
            "Fahrmodus an " + "bitte ".repeat(200),
        )) {
            assertNull(text, DriveMode.command(text))
        }
        assertNull(DriveMode.command(null))
    }

    @Test fun enterAndExitAreExplicitAndIdempotent() {
        val mode = DriveMode()
        assertFalse(mode.active)
        assertNull(mode.effects)
        assertNull(mode.enteredAtMs)
        assertFalse(mode.exit())

        assertTrue(mode.enter(5_000))
        assertTrue(mode.active)
        assertEquals(5_000L, mode.enteredAtMs)
        assertFalse(mode.enter(9_000))
        assertEquals(5_000L, mode.enteredAtMs)

        assertTrue(mode.exit())
        assertFalse(mode.active)
        assertNull(mode.effects)
        assertNull(mode.enteredAtMs)
        assertFalse(mode.exit())
    }

    @Test fun effectsArePlainDocumentedValues() {
        val mode = DriveMode()
        mode.enter(0)
        val effects = mode.effects!!
        assertEquals(ContinuousConversationMode.MAX_SILENCE_WINDOW_MS, effects.silenceWindowMs)
        assertTrue(effects.announcementsRegardlessOfConversation)
        assertEquals(2, effects.maxSpokenSentences)
        assertTrue(effects.deferVisualApprovals)
        assertEquals("Das braucht einen Blick aufs Display. Ich merke es mir, bis du stehst.", effects.visualApprovalSentence)
        assertEquals(DriveMode.EFFECTS, effects)

        // The window is one the conversation mode really accepts, not a value it would clamp.
        val conversation = ContinuousConversationMode()
        conversation.silenceWindowMs = effects.silenceWindowMs
        assertEquals(effects.silenceWindowMs, conversation.silenceWindowMs)
        assertEquals(600_000L, conversation.silenceWindowMs)
    }

    @Test fun situationalPhrasesOnlyCountWhenTheyChangeTheMode() {
        val mode = DriveMode()
        // Parked: "Ich bin angekommen" is ordinary conversation, "Ich fahre jetzt los" starts the mode.
        assertNull(mode.commandFor("Ich bin angekommen."))
        assertEquals(DriveMode.Command.ENTER, mode.commandFor("Ich fahre jetzt los."))
        assertEquals(DriveMode.Command.ENTER, mode.commandFor("Fahrmodus an"))
        assertEquals(DriveMode.Command.EXIT, mode.commandFor("Fahrmodus aus"))

        mode.enter(0)
        assertNull(mode.commandFor("Ich fahre jetzt los."))
        assertEquals(DriveMode.Command.EXIT, mode.commandFor("Ich bin angekommen."))
        assertEquals(DriveMode.Command.ENTER, mode.commandFor("Fahrmodus ein"))
        assertEquals(DriveMode.Command.EXIT, mode.commandFor("Fahrmodus aus"))
        assertNull(mode.commandFor("Ich bin noch nicht angekommen"))
        assertNull(mode.commandFor(null))
    }

    @Test fun visualApprovalsAreNotedOnlyWhileDrivingAndTheListIsBounded() {
        val mode = DriveMode()
        assertNull(mode.deferVisualApproval("Lösche die Datei Bericht.pdf"))
        assertTrue(mode.deferredRequests.isEmpty())

        mode.enter(0)
        for (i in 1..DriveMode.MAX_DEFERRED_REQUESTS) {
            val deferral = mode.deferVisualApproval("Auftrag $i")!!
            assertTrue(deferral.remembered)
            assertEquals(DriveMode.VISUAL_APPROVAL_DEFERRED_SENTENCE, deferral.spokenSentence)
        }
        // Full: the sixth is not noted, the owner is told so, and no earlier one is dropped for it.
        val sixth = mode.deferVisualApproval("Auftrag 6")!!
        assertFalse(sixth.remembered)
        assertEquals(DriveMode.VISUAL_APPROVAL_NOT_REMEMBERED_SENTENCE, sixth.spokenSentence)
        assertTrue(sixth.spokenSentence.startsWith("Das braucht einen Blick aufs Display."))
        assertEquals((1..5).map { "Auftrag $it" }, mode.deferredRequests)

        // The list survives leaving the mode and is handed out exactly once.
        assertTrue(mode.exit())
        assertEquals(5, mode.deferredRequests.size)
        assertEquals((1..5).map { "Auftrag $it" }, mode.takeDeferredRequests())
        assertTrue(mode.takeDeferredRequests().isEmpty())
        assertTrue(mode.deferredRequests.isEmpty())
    }

    @Test fun notedRequestsAreDeduplicatedAndBoundedText() {
        val mode = DriveMode()
        mode.enter(0)
        assertTrue(mode.deferVisualApproval("Installiere   die\nApp\tSignal")!!.remembered)
        // Asking again for the same thing is acknowledged but not stored twice.
        assertTrue(mode.deferVisualApproval("installiere die App signal")!!.remembered)
        assertEquals(listOf("Installiere die App Signal"), mode.deferredRequests)

        assertFalse(mode.deferVisualApproval(" \n\t ")!!.remembered)
        assertEquals(1, mode.deferredRequests.size)

        val emoji = "😀"
        for (shift in 0..1) {
            val probe = DriveMode()
            probe.enter(0)
            probe.deferVisualApproval("x".repeat(shift) + emoji.repeat(300))
            val stored = probe.deferredRequests.single()
            assertTrue(stored.length <= DriveMode.MAX_DEFERRED_REQUEST_CHARS)
            assertTrue(stored.length >= DriveMode.MAX_DEFERRED_REQUEST_CHARS - 1)
            assertFalse(stored.last().isHighSurrogate())
        }
    }

    @Test fun readoutAfterTheDriveIsBoundedAndNeverACommand() {
        assertNull(DriveMode.deferredReadout(emptyList()))
        assertNull(DriveMode.deferredReadout(listOf(" ")))
        assertEquals(
            "Während der Fahrt habe ich mir eine Sache gemerkt, die einen Blick aufs Display braucht: " +
                "„Auftrag 1“. Sag mir, womit ich weitermachen soll.",
            DriveMode.deferredReadout(listOf("Auftrag 1")),
        )
        val many = DriveMode.deferredReadout((1..9).map { "Auftrag $it" })!!
        assertTrue(many.startsWith("Während der Fahrt habe ich mir 5 Dinge gemerkt, die einen Blick aufs Display brauchen:"))
        assertTrue(many.contains("„Auftrag 5“."))
        assertFalse(many.contains("Auftrag 6"))
        // The assistant's own sentences must not toggle the mode if they are ever heard back.
        for (sentence in listOf(
            DriveMode.ENTER_CONFIRMATION, DriveMode.EXIT_CONFIRMATION, DriveMode.ALREADY_ACTIVE, DriveMode.ALREADY_INACTIVE,
            DriveMode.VISUAL_APPROVAL_DEFERRED_SENTENCE, DriveMode.VISUAL_APPROVAL_NOT_REMEMBERED_SENTENCE, many,
        )) {
            assertNull(sentence, DriveMode.command(sentence))
        }
    }
}
