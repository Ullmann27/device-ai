package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #758 (Fold7 Code53): multi-step corrections in one conversation. The owner's last valid
 * state must be recalled from the owner's own words – also after the four-turn dialogue window
 * dropped the first statement – without a refusal or an "external verification" detour.
 */
class OwnerObjectStateLedgerTest {
    private val session = "session-758"

    private fun context(vararg ownerTurns: Pair<String, String>) = LocalConversationContext().apply {
        ownerTurns.forEach { (owner, reply) -> record(session, owner, reply) }
    }

    private fun ledger(vararg turns: String) = OwnerObjectStateLedger().apply { turns.forEach(::observeOwnerTurn) }

    private val step1 = "Ich habe eine blaue Werkzeugkiste mit zwölf Schrauben in der Garage. " +
        "Merk dir diese Angaben für unser Gespräch, ich frage dich später danach."
    private val step3 = "Nicht zwölf, sondern siebzehn; im Keller statt Garage; Farbe unverändert."
    private val step4 = "Nicht siebzehn, sondern neunzehn; nein, neunzehn auch nicht, es sind einundzwanzig; " +
        "Farbe und Standort bleiben gleich."
    private val step5 = "Ich brauche keine externe Überprüfung. Beziehe dich ausschließlich auf unser bisheriges " +
        "Gespräch. Welche Farbe hat meine Werkzeugkiste, wie viele Schrauben sind nach allen Korrekturen darin " +
        "und wo steht sie?"

    private fun assertFinal758(answer: String?) {
        assertNotNull("A pure recall of the session state must not fall through to a refusal", answer)
        answer!!
        assertTrue(answer, answer.startsWith("Laut deinen Angaben in diesem Gespräch"))
        assertTrue(answer, answer.contains("Farbe: blau"))
        assertTrue(answer, answer.contains("Anzahl Schrauben: 21"))
        assertTrue(answer, answer.contains("Ort: im Keller"))
        assertTrue(answer, answer.contains("keine externe Prüfung"))
        assertFalse(answer, answer.contains("Zugriff"))
    }

    @Test fun issue758FiveStepFlowSurvivesTheFourTurnWindow() {
        // Step 2 asked colour, count and place in three separate messages – as spoken on the Fold7.
        val dialogue = context(
            step1 to "Notiert: blaue Werkzeugkiste, zwölf Schrauben, Garage.",
            "Welche Farbe hat meine Werkzeugkiste?" to "Blau.",
            "Wie viele Schrauben sind darin?" to "Zwölf.",
            "Wo steht sie?" to "In der Garage.",
            step3 to "Blau, 17 Schrauben, Keller.",
            step4 to "Es sind 21 Schrauben."
        )
        // Root cause evidence: the bounded window no longer holds the first owner statement …
        assertFalse(dialogue.sessionEvidence(session)!!.contains(step1))
        // … but the folded session state does, including spoken numbers and the self-correction.
        assertFinal758(dialogue.objectStateAnswer(session, step5))
        val answer = dialogue.objectStateAnswer(session, step5)!!
        assertTrue(answer, answer.contains("12 → 17 → 19 (in derselben Nachricht verworfen) → 21"))
        assertTrue(answer, answer.contains("in der Garage → im Keller"))
        assertTrue(answer, answer.contains("Farbe seit deiner ersten Angabe unverändert"))
    }

    @Test fun issue758StepByStepAnswersMatchTheOwnersExpectation() {
        val state = ledger(step1)
        assertTrue(state.answer("Welche Farbe hat meine Werkzeugkiste?")!!.contains("Farbe: blau"))
        assertTrue(state.answer("Wie viele Schrauben sind darin?")!!.contains("Anzahl Schrauben: 12"))
        assertTrue(state.answer("Wo steht sie?")!!.contains("Ort: in der Garage"))
        state.observeOwnerTurn(step3)
        val afterStep3 = state.answer("Welche Farbe hat die Kiste, wie viele Schrauben sind darin und wo steht sie?")!!
        assertTrue(afterStep3, afterStep3.contains("Farbe: blau"))
        assertTrue(afterStep3, afterStep3.contains("Anzahl Schrauben: 17"))
        assertTrue(afterStep3, afterStep3.contains("Ort: im Keller"))
        state.observeOwnerTurn(step4)
        assertFinal758(state.answer(step5))
    }

    @Test fun modelPromptCarriesTheFinalStateForACorrectionTurn() {
        val dialogue = context(step1 to "Notiert.", step3 to "Blau, 17, Keller.")
        // The current correction is not recorded yet when the model prompt is built.
        val block = dialogue.objectStateBlock(session, step4)
        assertTrue(block, block.contains("GESPRÄCHSSTAND AUS DEN EIGENEN ANGABEN DES NUTZERS"))
        assertTrue(block, block.contains("Farbe blau"))
        assertTrue(block, block.contains("Anzahl Schrauben 21"))
        assertTrue(block, block.contains("Ort im Keller"))
        assertTrue(block, block.contains("keine externe Wirklichkeitsprüfung"))
        assertEquals("Unrelated requests stay proportionate", "",
            dialogue.objectStateBlock(session, "Erkläre mir kurz die Photosynthese."))
    }

    @Test fun severalCorrectionsInOneSpokenSentenceKeepOnlyTheLastValidValue() {
        val state = ledger("Ich habe eine rote Kiste mit 5 Ringen im Flur.",
            "Nein, nicht fünf, sondern sieben, ach was, sieben auch nicht, es sind acht.")
        val answer = state.answer("Wie viele Ringe sind in der Kiste?")!!
        assertTrue(answer, answer.contains("Anzahl Ringen: 8"))
        assertTrue(answer, answer.contains("5 → 7 (in derselben Nachricht verworfen) → 8"))
    }

    @Test fun negationsWithSonderndOrAPlainReplacementSelectTheNewValue() {
        val count = ledger("Ich habe eine graue Box mit vier Kabeln im Büro.", "Nicht vier, sondern sieben.")
        assertTrue(count.answer("Wie viele Kabel sind in der Box?")!!.contains("Anzahl Kabeln: 7"))
        val place = ledger("Ich habe eine grüne Tasche im Keller.", "Nicht im Keller, in der Garage.")
        val answer = place.answer("Wo ist die Tasche?")!!
        assertTrue(answer, answer.contains("Ort: in der Garage"))
        assertTrue(answer, answer.contains("im Keller → in der Garage"))
        val transfer = ledger("Ich habe eine grüne Tasche im Keller.", "Sie ist nicht mehr im Keller, sondern auf dem Dachboden.")
        assertTrue(transfer.answer("Wo ist die Tasche?")!!.contains("Ort: auf dem Dachboden"))
        val fromTo = ledger("Ich habe eine graue Box mit vier Kabeln im Büro.", "Korrigiere die Anzahl von vier auf neun.")
        assertTrue(fromTo.answer("Wie viele Kabel?")!!.contains("Anzahl Kabeln: 9"))
    }

    @Test fun unchangedAttributesSurviveACorrectionOfAnotherAttribute() {
        val state = ledger(step1, "Es sind jetzt 14 Schrauben.")
        val answer = state.answer("Was habe ich dir über meine Werkzeugkiste gesagt?")!!
        assertTrue(answer, answer.contains("Farbe: blau"))
        assertTrue(answer, answer.contains("Anzahl Schrauben: 14"))
        assertTrue(answer, answer.contains("Ort: in der Garage"))
    }

    @Test fun referencesToThePreviousOrTheOtherValueResolveDeterministically() {
        val previous = ledger(step1, step3, step4, "Nimm doch die Zahl davor.")
        assertTrue(previous.answer("Wie viele Schrauben sind darin?")!!.contains("Anzahl Schrauben: 17"))
        val other = ledger("Ich habe eine grüne Tasche im Keller.", "Nicht im Keller, in der Garage.",
            "Doch der andere Ort.")
        assertTrue(other.answer("Wo ist die Tasche?")!!.contains("Ort: im Keller"))
    }

    @Test fun interruptionsNeitherChangeNorEvictTheState() {
        val dialogue = context(
            step1 to "Notiert.",
            "Wie spät ist es?" to "(Uhrzeit aus der Geräteuhr beantwortet)",
            "Erzähl mir einen kurzen Witz." to "Ein Witz.",
            step3 to "Notiert.",
            "Was ist die Hauptstadt von Frankreich?" to "Paris.",
            "Ich habe heute drei Kaffee getrunken." to "Notiert.",
            step4 to "21."
        )
        assertFinal758(dialogue.objectStateAnswer(session, step5))
    }

    @Test fun temporalOrderOfEarlierStatementsIsAnsweredFromTheTimeline() {
        val state = ledger(step1, step3, step4)
        assertTrue(state.answer("Wie viele Schrauben waren es ursprünglich?")!!
            .contains("Anzahl Schrauben: 12 (deine erste Angabe)"))
        assertTrue(state.answer("Und welche Anzahl war davor?")!!.contains("Anzahl Schrauben: 17"))
        val history = state.answer("In welcher Reihenfolge habe ich die Anzahl korrigiert?")!!
        assertTrue(history, history.contains("12 → 17 → 19 (in derselben Nachricht verworfen) → 21"))
        assertTrue(history, history.contains("Änderungen: 2"))
        val place = state.answer("Wo stand die Werkzeugkiste zuerst?")!!
        assertTrue(place, place.contains("Ort: in der Garage (deine erste Angabe)"))
    }

    @Test fun spokenNumbersWithColouredCountedThingsAreTracked() {
        val state = ledger("Ich habe fünf blaue Schrauben gezählt. Nicht vier, sondern fünf.")
        val answer = state.answer("Wie viele Schrauben habe ich gezählt?")!!
        assertTrue(answer, answer.contains("Anzahl Schrauben: 5"))
        assertTrue(answer, answer.startsWith("Laut deinen Angaben in diesem Gespräch – Schrauben"))
        assertTrue(state.answer("Welche Farbe haben die Schrauben?")!!.contains("Farbe: blau"))
    }

    @Test fun nothingIsGuessedForUnknownObjectsAmbiguityOrMixedTasks() {
        val state = ledger(step1)
        assertNull("Another object is never answered from the toolbox", state.answer("Wo ist mein Handy?"))
        assertNull("A recall plus another task goes to the normal path",
            state.answer("Welche Farbe hat die Werkzeugkiste und erkläre mir warum Blau beruhigt?"))
        assertNull("A message that corrects is not a pure question", state.answer("Es sind 13 Schrauben. Wie viele sind es?"))
        assertNull(state.answer("Öffne WhatsApp."))
        val two = ledger("Ich habe eine rote Kiste mit 5 Ringen im Flur.",
            "Ich habe eine blaue Tasche mit 3 Ringen im Keller.", "Nicht zwei, sondern sechs.")
        val red = two.answer("Wie viele Ringe sind in der Kiste?")!!
        assertTrue("An unbound correction is not applied to either object", red.contains("Anzahl Ringen: 5"))
        assertTrue(red, red.contains("Nicht zugeordnet"))
        assertTrue(two.answer("Wie viele Ringe sind in der Tasche?")!!.contains("Anzahl Ringen: 3"))
    }

    @Test fun aCorrectionThatNegatesOneObjectsValueBindsToThatObject() {
        val two = ledger("Ich habe eine rote Kiste mit 5 Ringen im Flur.",
            "Ich habe eine blaue Tasche mit 3 Ringen im Keller.", "Nicht fünf, sondern sechs.")
        assertTrue(two.answer("Wie viele Ringe sind in der Kiste?")!!.contains("Anzahl Ringen: 6"))
        assertTrue(two.answer("Wie viele Ringe sind in der Tasche?")!!.contains("Anzahl Ringen: 3"))
    }

    @Test fun questionsConditionalsAndHedgesNeverAssertAValue() {
        val state = ledger(step1, "Wären es 30 Schrauben, wäre die Kiste voll.", "Sind es vielleicht 20?")
        assertTrue(state.answer("Wie viele Schrauben sind darin?")!!.contains("Anzahl Schrauben: 12"))
        val hedged = ledger(step1, "Es sind vielleicht 13 Schrauben.")
        assertTrue(hedged.answer("Wie viele Schrauben sind darin?")!!.contains("13 (unsicher: unsicher formuliert)"))
        val retracted = ledger(step1, "Zwölf stimmt nicht.")
        assertTrue(retracted.answer("Wie viele Schrauben sind darin?")!!
            .contains("unklar (zuletzt verneint, ohne neuen Wert)"))
    }

    @Test fun topicResetAndSessionChangeDropTheEphemeralState() {
        val dialogue = context(step1 to "Notiert.")
        assertNotNull(dialogue.objectStateAnswer(session, "Welche Farbe hat die Werkzeugkiste?"))
        assertNull(dialogue.objectStateAnswer("other-session", "Welche Farbe hat die Werkzeugkiste?"))
        dialogue.inspectRequest(session, "Neues Thema")
        assertNull(dialogue.objectStateAnswer(session, "Welche Farbe hat die Werkzeugkiste?"))
        val switched = context(step1 to "Notiert.")
        switched.record("next-session", "Hallo", "Hallo!")
        assertNull(switched.objectStateAnswer(session, "Welche Farbe hat die Werkzeugkiste?"))
    }

    @Test fun anUnreadableOwnerTurnDisablesTheDeterministicAnswer() {
        val state = ledger(step1)
        state.observeOwnerTurn("x".repeat(4_001))
        assertNull(state.answer("Welche Farbe hat die Werkzeugkiste?"))
    }

    @Test fun germanNumberWordsCoverSpokenCounts() {
        mapOf("zwölf" to 12, "siebzehn" to 17, "neunzehn" to 19, "einundzwanzig" to 21, "zweiunddreißig" to 32,
            "hundert" to 100, "hundertdrei" to 103, "dreihundertvierundfünfzig" to 354, "0" to 0, "21" to 21,
            "null" to 0, "eins" to 1).forEach { (word, value) -> assertEquals(word, value, GermanNumberWords.parse(word)) }
        listOf("ein", "eine", "einen", "Werkzeugkiste", "undzwanzig", "").forEach {
            assertNull(it, GermanNumberWords.parse(it))
        }
    }
}
