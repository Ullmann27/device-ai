package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.ConversationRegister.CASUAL
import at.heinz.deviceai.cognitive.ConversationRegister.DISTRESSED
import at.heinz.deviceai.cognitive.ConversationRegister.PLAYFUL
import at.heinz.deviceai.cognitive.ConversationRegister.SERIOUS
import at.heinz.deviceai.cognitive.ConversationRegister.TASK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Code63 voice round: human conversational timing and register. The numbers under test are the published
 * ones documented in [HumanSpeechTiming]; the tests pin the behaviour derived from them.
 */
class HumanConversationStyleTest {
    @Test fun registerFollowsTheOwnersToneAndSeriousBeatsPlayful() {
        assertEquals(CASUAL, ConversationStyle.detect("Na, was machst du so?"))
        assertEquals(PLAYFUL, ConversationStyle.detect("Haha, blöde Frage: können Fische eigentlich rülpsen?"))
        assertEquals(PLAYFUL, ConversationStyle.detect("Erzähl mir einen Witz 😂"))
        assertEquals(SERIOUS, ConversationStyle.detect("Ich habe heute die Kündigung bekommen."))
        assertEquals(SERIOUS, ConversationStyle.detect("Haha, die Mahnung vom Inkasso ist da."))
        assertEquals(DISTRESSED, ConversationStyle.detect("Mir geht's nicht gut, ich kann nicht mehr."))
        assertEquals(DISTRESSED, ConversationStyle.detect("Haha, ich kann nicht mehr, mein Opa ist gestorben."))
        assertEquals(TASK, ConversationStyle.detect("Schreib mir eine E-Mail an den Vermieter wegen der Heizung."))
        assertEquals(TASK, ConversationStyle.detect("x".repeat(401)))
        assertEquals(CASUAL, ConversationStyle.detect(""))
    }

    @Test fun ordinaryWordsDoNotFlipTheRegister() {
        // „Steuerung“ is not tax, „Anzeige“ is a display, „Quatsch“ is a correction, „!!“ is emphasis.
        assertEquals(CASUAL, ConversationStyle.detect("Die Steuerung vom Rollladen spinnt wieder."))
        assertEquals(CASUAL, ConversationStyle.detect("Die Anzeige am Herd blinkt."))
        assertEquals(CASUAL, ConversationStyle.detect("Quatsch, ich meinte den anderen."))
        assertEquals(CASUAL, ConversationStyle.detect("Das war super!!"))
        assertEquals(CASUAL, ConversationStyle.detect("Wir waren beim Klagenfurter Stadtfest."))
    }

    @Test fun spokenStyleIsAFriendOnThePhoneAndNeverAClaimOfFeelings() {
        val casual = ConversationStyle.promptBlock(CASUAL, spoken = true)
        assertTrue(casual.contains("wie ein guter Freund am Telefon"))
        assertTrue(casual.contains("ein bis drei kurze Sätze"))
        assertTrue(casual.contains("keine Aufzählungszeichen"))
        listOf(PLAYFUL, CASUAL, TASK, SERIOUS, DISTRESSED).forEach { register ->
            val block = ConversationStyle.promptBlock(register, spoken = true)
            assertTrue("$register", block.contains("Behaupte keine eigenen Gefühle"))
            assertTrue("$register", block.contains("betrifft nur die Form, nie Fakten, Belege oder Freigaben"))
        }
    }

    @Test fun humourIsInvitedOnlyWhenTheOwnerStartedItAndForbiddenWhenItHurts() {
        val playful = ConversationStyle.promptBlock(PLAYFUL, spoken = true)
        assertTrue(playful.contains("freche oder witzige Reaktion ist willkommen"))
        assertTrue(playful.contains("danach kommt die echte Antwort"))
        assertTrue(playful.contains("keine erfundenen Fakten für eine Pointe"))
        assertTrue(ConversationStyle.promptBlock(SERIOUS, spoken = true).contains("kein Humor"))
        val distressed = ConversationStyle.promptBlock(DISTRESSED, spoken = true)
        assertTrue(distressed.contains("kein Humor"))
        assertTrue(distressed.contains("keine Liste mit Ratschlägen"))
        assertFalse(ConversationStyle.promptBlock(CASUAL, spoken = true).contains("willkommen"))
    }

    @Test fun aWrittenWorkOrderKeepsTheExistingDetailedInstruction() {
        assertEquals("", ConversationStyle.promptBlock(TASK, spoken = false))
        assertTrue(ConversationStyle.promptBlock(CASUAL, spoken = false).contains("So kurz wie möglich, so lang wie nötig."))
        assertFalse(ConversationStyle.promptBlock(CASUAL, spoken = false).contains("Du wirst vorgelesen"))
        // Driving: a hard, bounded length.
        assertTrue(ConversationStyle.promptBlock(CASUAL, spoken = true, maxSentences = 2).contains("Höchstens 2 kurze Sätze."))
        assertTrue(ConversationStyle.promptBlock(CASUAL, spoken = true, maxSentences = 99).contains("Höchstens 6 kurze Sätze."))
    }

    @Test fun pausesStayInTheMediumBandAndGrowWithTheBoundary() {
        fun pause(finished: String, v: Int = 3, r: ConversationRegister = CASUAL) =
            HumanSpeechTiming.pauseBetweenMs(finished, "Weiter geht es.", r, v)
        val clause = pause("Erstens die Frist,")
        val sentence = pause("Das war der erste Punkt.")
        val question = pause("Passt das für dich?")
        val paragraph = pause("Das war der erste Teil.\n\n")
        assertTrue(clause < sentence && sentence < question && question < paragraph)
        (0..40).forEach { v ->
            listOf(PLAYFUL, CASUAL, SERIOUS, DISTRESSED).forEach { r ->
                listOf("Wort,", "Satz.", "Frage?", "Absatz.\n\n").forEach { text ->
                    val ms = pause(text, v, r)
                    assertTrue("$text/$r/$v → $ms", ms in HumanSpeechTiming.MIN_PAUSE_MS..HumanSpeechTiming.MAX_PAUSE_MS)
                }
            }
        }
        // No pause after the last piece.
        assertEquals(0L, HumanSpeechTiming.pauseBetweenMs("Ende.", "", CASUAL, 1))
    }

    @Test fun pausesVaryLikeSpeechButAreReproducible() {
        val values = (0..6).map { HumanSpeechTiming.pauseBetweenMs("Ein Satz.", "Noch einer.", CASUAL, it) }
        assertEquals("seven distinct pause lengths", 7, values.toSet().size)
        assertEquals(values, (0..6).map { HumanSpeechTiming.pauseBetweenMs("Ein Satz.", "Noch einer.", CASUAL, it) })
        assertEquals(values[2], HumanSpeechTiming.pauseBetweenMs("Ein Satz.", "Noch einer.", CASUAL, 2 + 7))
        // Negative seeds are valid.
        assertTrue(HumanSpeechTiming.pauseBetweenMs("Ein Satz.", "Noch einer.", CASUAL, -5) >= HumanSpeechTiming.MIN_PAUSE_MS)
        // Serious talk leaves more room than banter.
        assertTrue(HumanSpeechTiming.pauseBetweenMs("Ein Satz.", "Noch einer.", SERIOUS, 3) >
            HumanSpeechTiming.pauseBetweenMs("Ein Satz.", "Noch einer.", PLAYFUL, 3))
    }

    @Test fun theFirstSignOfLifeStartsBeforeSilenceIsHeardAsTrouble() {
        assertTrue(HumanSpeechTiming.FIRST_CUE_DELAY_MS < HumanSpeechTiming.TROUBLE_GAP_MS)
        assertTrue(HumanSpeechTiming.withinToleratedGap(599))
        assertFalse(HumanSpeechTiming.withinToleratedGap(600))
        assertEquals(HumanSpeechTiming.FIRST_CUE_DELAY_MS, HumanSpeechTiming.cueDelayMs(0))
        assertEquals(HumanSpeechTiming.SECOND_CUE_DELAY_MS, HumanSpeechTiming.cueDelayMs(1))
        assertNull(HumanSpeechTiming.cueDelayMs(HumanSpeechTiming.MAX_CUES_PER_TURN))
    }

    @Test fun rateChangesStaySmallEnoughToRemainOneSpeaker() {
        ConversationRegister.entries.forEach { register ->
            assertTrue("$register", HumanSpeechTiming.rateFactor(register) in 0.92f..1.06f)
        }
        assertTrue(HumanSpeechTiming.rateFactor(PLAYFUL) > HumanSpeechTiming.rateFactor(SERIOUS))
    }

    @Test fun cuesNeverRepeatBackToBackFitTheToneAndCarryNoAnswerContent() {
        val cue = ConversationalLatencyCue()
        var previous: String? = null
        repeat(12) { turn ->
            val text = cue.next(0, CASUAL, variation = 0)!!
            assertNotEquals("turn $turn", previous, text)
            previous = text
        }
        assertTrue(ConversationalLatencyCue().next(0, DISTRESSED, 0) in ConversationalLatencyCue.FIRST_CALM)
        assertTrue(ConversationalLatencyCue().next(0, PLAYFUL, 1) in ConversationalLatencyCue.FIRST_PLAYFUL)
        assertTrue(ConversationalLatencyCue().next(1, SERIOUS, 0) in ConversationalLatencyCue.SECOND_CALM)
        assertNull(ConversationalLatencyCue().next(2, CASUAL, 0))
        // A cue is a sign of life: short, no digits, no claim about the result.
        ConversationalLatencyCue.ALL.forEach { text ->
            assertTrue(text, text.length <= 32 && text.none { it.isDigit() })
            assertFalse(text, Regex("(?i)gefunden|ergebnis|antwort ist|fertig\\.$").containsMatchIn(text) && text != "Fast fertig.")
        }
    }
}
