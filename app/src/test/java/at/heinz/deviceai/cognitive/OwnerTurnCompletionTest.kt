package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.ContinuousConversationMode.Decision
import at.heinz.deviceai.cognitive.ContinuousConversationMode.State
import at.heinz.deviceai.cognitive.OwnerTurnAssembler.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Code63 voice round: a thinking pause is not the end of a turn. The recognizer cuts after a fixed
 * silence; an audibly unfinished fragment is held and joined with what follows instead of being answered.
 */
class OwnerTurnCompletionTest {
    @Test fun endingsThatCannotCloseAGermanSentenceAreUnfinished() {
        listOf(
            "Ich möchte das wissen weil",
            "Erklär mir bitte wie das mit der Heizung geht und",
            "Ich glaube dass",
            "Schick das an Anna wenn",
            "Ich war gestern beim Arzt ähm",
            "Also ich brauche das für",
            "Das liegt im Keller,",
            "Ich habe drei Fragen:",
            "Hm"
        ).forEach { assertTrue(it, OwnerTurnCompletion.looksUnfinished(it)) }
    }

    @Test fun completeGermanSentencesWithParticlesPronounsAndTagQuestionsAreFinished() {
        listOf(
            "Mach das Licht an",
            "Ruf Anna an",
            "Schalt die Musik aus",
            "Hör mir zu",
            "Komm mit",
            "Was ist das",
            "Ich nehme die",
            "Das passt so, oder",
            "Das passt so oder",
            "Wie spät ist es",
            "Ja",
            "Nein",
            "Und",
            "Weil ich es so will.",
            "Ich weiß nicht ob.",
            "Stopp",
            "Warte kurz",
            ""
        ).forEach { assertFalse(it, OwnerTurnCompletion.looksUnfinished(it)) }
    }

    @Test fun aFragmentIsHeldAndJoinedWithItsContinuation() {
        val assembler = OwnerTurnAssembler()
        assertEquals(Result.Hold, assembler.onRecognized("Ich möchte wissen wie das geht weil", 1_000L))
        assertTrue(assembler.holding)
        assertEquals(Result.Send("Ich möchte wissen wie das geht weil ich morgen einen Termin habe"),
            assembler.onRecognized("ich morgen einen Termin habe", 5_000L))
        assertFalse(assembler.holding)
        // A complete turn passes through unchanged.
        assertEquals(Result.Send("Wie spät ist es"), assembler.onRecognized("Wie spät ist es", 6_000L))
    }

    @Test fun silenceAfterAFragmentSendsExactlyWhatWasSaid() {
        val assembler = OwnerTurnAssembler()
        assertEquals(Result.Hold, assembler.onRecognized("Ich brauche das für", 0L))
        assertEquals("Ich brauche das für", assembler.onSilence())
        assertNull(assembler.onSilence())
        assertFalse(assembler.holding)
    }

    @Test fun holdingIsBoundedInRoundsAndInTime() {
        val rounds = OwnerTurnAssembler(maxContinuations = 2)
        assertEquals(Result.Hold, rounds.onRecognized("Erstens und", 0L))
        assertEquals(Result.Hold, rounds.onRecognized("zweitens und", 1_000L))
        // The third fragment is sent as it is: nothing is lost, and the assistant is not kept waiting.
        assertEquals(Result.Send("Erstens und zweitens und drittens weil"), rounds.onRecognized("drittens weil", 2_000L))

        val time = OwnerTurnAssembler(maxContinuations = 5, maxHoldMs = 10_000L)
        assertEquals(Result.Hold, time.onRecognized("Ich denke dass", 0L))
        assertEquals(Result.Send("Ich denke dass wir und"), time.onRecognized("wir und", 10_000L))
        // A clock that ran backwards never extends the hold.
        val backwards = OwnerTurnAssembler()
        assertEquals(Result.Hold, backwards.onRecognized("Ich denke dass", 5_000L))
        assertEquals(Result.Send("Ich denke dass wir und"), backwards.onRecognized("wir und", 4_000L))
    }

    @Test fun controlWordsAreNeverHeldOrJoined() {
        val assembler = OwnerTurnAssembler()
        assertEquals(Result.Send("Stopp"), assembler.onRecognized("Stopp", 0L))
        assertEquals(Result.Send(""), assembler.onRecognized("   ", 0L))
    }

    @Test fun theConversationModeKeepsListeningForAContinuationWithoutCountingSilence() {
        val mode = ContinuousConversationMode().apply { silenceWindowMs = 0L }
        assertTrue(mode.start("s-1"))
        // More continuations than the classic three silent attempts: none of them counts as silence.
        repeat(5) {
            val token = mode.listeningTurn("s-1")!!
            assertEquals(Decision.RETRY_LISTEN, mode.ownerContinues(token, "s-1"))
            assertEquals(State.LISTENING, mode.state)
            // The old recognizer round can no longer deliver into the new one.
            assertEquals(Decision.IGNORE, mode.recognized(token, "s-1", "zu spät"))
        }
        repeat(ContinuousConversationMode.MAX_SILENT_RETRIES) {
            assertEquals(Decision.RETRY_LISTEN, mode.listenFailed(mode.listeningTurn("s-1")!!, "s-1", silence = true))
        }
        assertEquals(Decision.PAUSE, mode.listenFailed(mode.listeningTurn("s-1")!!, "s-1", silence = true))
        // Outside listening, or for another session, nothing happens.
        assertEquals(Decision.IGNORE, mode.ownerContinues(0L, "s-1"))
        val other = ContinuousConversationMode().apply { start("s-1") }
        assertEquals(Decision.IGNORE, other.ownerContinues(other.listeningTurn("s-1")!!, "s-2"))
    }
}
