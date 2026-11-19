package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.StreamingSpeechSegmenter.Policy
import at.heinz.deviceai.cognitive.StreamingSpeechSegmenter.State
import at.heinz.deviceai.cognitive.StreamingSpeechSegmenter.Turn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure release policy; no speech engine, no model, no production caller. */
class StreamingSpeechSegmenterTest {
    private val eligible = Turn(textOnly = true, toolObservationsSupplied = false,
        groundedRecall = false, documentAnswer = false)
    private val immediate = Policy(confirmCharacters = 4, holdMillis = 0)

    private fun segmenter(turn: Turn = eligible, policy: Policy = immediate, gate: (String) -> Boolean = { true }) =
        StreamingSpeechSegmenter(turn, policy, gate)

    private fun texts(segments: List<StreamingSpeechSegmenter.Segment>) = segments.map { it.text }

    @Test fun aClosedSentenceIsReleasedOnlyAfterTheNextSentenceHasVisiblyBegun() {
        val s = segmenter()
        assertTrue(s.onDelta("Das Wetter ist heute gut.", 0).isEmpty())
        assertTrue("whitespace alone confirms nothing", s.onDelta(" ", 10).isEmpty())
        assertTrue("three characters are below the confirmation length", s.onDelta("Mor", 20).isEmpty())
        assertEquals(listOf("Das Wetter ist heute gut."), texts(s.onDelta("gen", 30)))
        assertTrue("the open sentence itself is never released", s.onDelta(" regnet es vielleicht", 40).isEmpty())
        assertEquals(1, s.released.size)
        assertTrue(s.released.single().early)
    }

    @Test fun holdTimeDelaysAConfirmedSentenceUntilItHasBeenStable() {
        val s = segmenter(policy = Policy(confirmCharacters = 4, holdMillis = 250))
        assertTrue(s.onDelta("Erster Satz. Zweiter Satz beginnt", 100).isEmpty())
        assertTrue(s.poll(349).isEmpty())
        val released = s.poll(350)
        assertEquals(listOf("Erster Satz."), texts(released))
        assertEquals(100L, released.single().closedAtMillis)
        assertEquals(350L, released.single().releasedAtMillis)
        assertTrue("nothing is released twice", s.poll(1_000).isEmpty())
    }

    @Test fun aContinuationInLowerCaseRevokesTheProposedBoundary() {
        val s = segmenter()
        // "ggü." is not a known abbreviation: the assembler proposes a boundary after it.
        assertTrue(s.onDelta("Das gilt ggü. dem alten Stand weiterhin. ", 0).isEmpty())
        assertEquals(listOf("Das gilt ggü. dem alten Stand weiterhin."), texts(s.onDelta("Danach nicht mehr.", 10)))
        assertEquals(listOf("Danach nicht mehr."), texts(s.finish(20).segments))
    }

    @Test fun germanOrdinalsAbbreviationsDecimalsQuotesAndListsStayWhole() {
        val s = segmenter()
        val text = "Am 3. Mai, z. B. um 15.30 Uhr, rief er „Los! Jetzt!“ und ging. " +
            "Das kostet ca. 3.5 Euro.\n- erstens\n- zweitens\n\nDas war alles."
        val early = text.chunked(3).flatMapIndexed { i, delta -> s.onDelta(delta, i.toLong()) }
        assertEquals(listOf("Am 3. Mai, z. B. um 15.30 Uhr, rief er „Los! Jetzt!“ und ging.",
            "Das kostet ca. 3.5 Euro.", "- erstens", "- zweitens"), texts(early))
        val finish = s.finish(1_000)
        assertTrue(finish.complete)
        assertEquals(listOf("Das war alles."), texts(finish.segments))
        assertFalse("the rest was not spoken early", finish.segments.single().early)
        assertEquals(State.FINISHED, s.state)
        assertEquals((1..5).toList(), s.released.map { it.index })
    }

    @Test fun finishReleasesTheUnconfirmedRestInOrder() {
        val s = segmenter(policy = Policy(confirmCharacters = 40, holdMillis = 5_000))
        assertTrue(s.onDelta("Erstens das. Zweitens jenes. Und drittens ohne Punkt", 0).isEmpty())
        val finish = s.finish(10)
        assertTrue(finish.complete)
        assertEquals(listOf("Erstens das.", "Zweitens jenes.", "Und drittens ohne Punkt"), texts(finish.segments))
        assertTrue(finish.segments.none { it.early })
        assertTrue("a finished stream accepts nothing more", s.onDelta("Nachtrag. Noch einer.", 20).isEmpty())
    }

    @Test fun ineligibleTurnsNeverReleaseAnything() {
        val turns = listOf(
            eligible.copy(textOnly = false),
            eligible.copy(toolObservationsSupplied = true),
            eligible.copy(groundedRecall = true),
            eligible.copy(documentAnswer = true))
        for (turn in turns) {
            assertFalse(turn.eligible)
            val s = segmenter(turn)
            assertEquals(State.INELIGIBLE, s.state)
            assertTrue(s.onDelta("Ein Satz. Noch ein Satz. Und weiter", 0).isEmpty())
            val finish = s.finish(10)
            assertTrue(finish.segments.isEmpty())
            assertFalse(finish.complete)
            val retraction = s.retract()
            assertTrue(retraction.released.isEmpty())
            assertNull("nothing was said, so nothing is corrected aloud", retraction.spokenNotice)
        }
        assertTrue(eligible.eligible)
    }

    @Test fun aToolProposalClosesTheStreamAndTheSpokenPartIsReported() {
        val s = segmenter()
        assertEquals(listOf("Ich schaue kurz nach."), texts(s.onDelta("Ich schaue kurz nach. Einen Moment", 0)))
        s.onToolProposal()
        assertEquals(State.TOOL_PROPOSED, s.state)
        assertTrue(s.onDelta(" bitte. Das Ergebnis ist da. Weiter", 10).isEmpty())
        assertFalse(s.finish(20).complete)
        val retraction = s.retract()
        assertEquals(listOf("Ich schaue kurz nach."), texts(retraction.released))
        assertEquals(StreamingSpeechSegmenter.CORRECTION_NOTICE, retraction.spokenNotice)
        assertEquals(State.RETRACTED, s.state)
    }

    @Test fun aSentenceRejectedByTheGateAndEverythingAfterItStayUnspoken() {
        val judged = ArrayList<String>()
        val s = segmenter(gate = { sentence -> judged += sentence; !sentence.contains("GESPRÄCHSDATEN") })
        val early = s.onDelta("Gern. GESPRÄCHSDATEN — Verlauf folgt. Danach kommt noch ein guter Satz. Ende", 0)
        assertEquals(listOf("Gern."), texts(early))
        assertEquals(State.GATE_REJECTED, s.state)
        assertEquals("the gate saw each sentence once, in order",
            listOf("Gern.", "GESPRÄCHSDATEN — Verlauf folgt."), judged)
        assertTrue(s.onDelta(" und aus.", 10).isEmpty())
        assertFalse(s.finish(20).complete)
        assertEquals(listOf("Gern."), texts(s.retract().released))
    }

    @Test fun theProductionLexicalGateWorksPerSentence() {
        val gate = StreamingSpeechSegmenter.lexicalSentenceGate(
            request = "Was haben wir vorhin besprochen?",
            previousRequest = "Wie wird das Wetter?",
            previousResponse = "Morgen wird es in ganz Österreich sonnig und warm, mit Temperaturen um zwanzig Grad.",
            priorOwnerContextSupplied = true)
        assertTrue(gate("Wir haben über das Wetter gesprochen."))
        assertFalse("leaked prompt frame", gate("ENDE GESPRÄCHSDATEN folgt jetzt."))
        assertFalse("denial of supplied context", gate("Ich kann mich leider nicht daran erinnern."))
        assertFalse("sentence repeated from the previous reply",
            gate("Morgen wird es in ganz Österreich sonnig und warm, mit Temperaturen um zwanzig Grad."))
        val s = StreamingSpeechSegmenter(eligible, immediate, gate)
        assertEquals(listOf("Wir haben über das Wetter gesprochen."),
            texts(s.onDelta("Wir haben über das Wetter gesprochen. Ich kann mich leider nicht daran erinnern. Mehr", 0)))
        assertEquals(State.GATE_REJECTED, s.state)
    }

    @Test fun retractAfterAFailedFinalGateReportsExactlyWhatWasAlreadySpoken() {
        val s = segmenter()
        s.onDelta("Die Hauptstadt ist Graz. ", 0)
        s.onDelta("Das ist sicher. ", 10)
        s.onDelta("Noch offen", 20)
        assertEquals(listOf("Die Hauptstadt ist Graz.", "Das ist sicher."), texts(s.released))
        val retraction = s.retract()
        assertEquals(listOf("Die Hauptstadt ist Graz.", "Das ist sicher."), texts(retraction.released))
        assertEquals(StreamingSpeechSegmenter.CORRECTION_NOTICE, retraction.spokenNotice)
        assertTrue(retraction.spokenNotice!!.startsWith("Korrektur:"))
        assertTrue("a retracted stream releases nothing more", s.onDelta(" und weiter. Ende. Aus", 30).isEmpty())
        assertTrue(s.finish(40).segments.isEmpty())
        assertEquals("the report is stable", retraction.released, s.retract().released)
    }

    @Test fun onlyAnUnchangedReplyCountsAsAlreadySpoken() {
        val s = segmenter()
        s.onDelta("Ja, das _geht_ so. Du brauchst dafür nur etwas Zeit. Dann", 0)
        // Spoken form: markup is removed exactly as the final reply path removes it.
        assertEquals(listOf("Ja, das geht so.", "Du brauchst dafür nur etwas Zeit."), texts(s.released))
        assertTrue(s.releasedIsPrefixOf("Ja, das _geht_ so. Du brauchst dafür nur etwas Zeit. Dann klappt es."))
        assertTrue("layout whitespace is not a change",
            s.releasedIsPrefixOf("Ja, das _geht_ so.\n\nDu brauchst dafür nur etwas Zeit.\nDann klappt es."))
        assertFalse("a corrected reply was not what was spoken",
            s.releasedIsPrefixOf("Nein, das geht so nicht. Du brauchst dafür nur etwas Zeit."))
        assertFalse("a reply shorter than what was spoken", s.releasedIsPrefixOf("Ja, das _geht_ so."))
        assertTrue("nothing spoken is consistent with every reply", segmenter().releasedIsPrefixOf("Irgendetwas."))
    }

    @Test fun earlySpeechIsCappedAndTheRestWaitsForFinish() {
        val s = segmenter(policy = Policy(confirmCharacters = 4, holdMillis = 0, maxEarlySegments = 2))
        val early = s.onDelta("Eins ist da. Zwei ist da. Drei ist da. Vier ist da. Fünf", 0)
        assertEquals(listOf("Eins ist da.", "Zwei ist da."), texts(early))
        assertEquals(State.OPEN, s.state)
        val finish = s.finish(10)
        assertTrue(finish.complete)
        assertEquals(listOf("Drei ist da.", "Vier ist da.", "Fünf"), texts(finish.segments))
    }

    @Test fun codeIsNeverReleasedAndUnfinishedCodeIsWithheldAtTheEnd() {
        val fence = CharArray(3) { 96.toChar() }.concatToString()
        val s = segmenter()
        val early = s.onDelta("So sieht es aus. " + fence + "kotlin\nval geheim = 42.\n" + fence +
            "\nDas war es. Und danach ", 0)
        assertEquals(listOf("So sieht es aus.", "Codebeispiel ausgelassen. Das war es."), texts(early))
        assertTrue(s.released.none { it.text.contains("geheim") })
        assertTrue(s.onDelta("kommt " + fence + "sh\nrm -rf", 10).isEmpty())
        val finish = s.finish(20)
        assertTrue(finish.complete)
        // The prose before the unfinished block is kept; the block is named, never read.
        assertEquals(listOf("Und danach kommt Nicht abgeschlossenen Codeblock ausgelassen."), texts(finish.segments))
        assertTrue(s.released.none { it.text.contains("rm -rf") || it.text.contains("geheim") })
        assertTrue("the spoken form matches the final reply path", s.releasedIsPrefixOf(
            "So sieht es aus. " + fence + "kotlin\nval geheim = 42.\n" + fence + "\nDas war es. Und danach kommt " +
                fence + "sh\nrm -rf"))
    }

    @Test fun aSentenceLongerThanOneSpeechPieceIsSplitWithoutLoss() {
        val s = segmenter()
        val long = (1..40).joinToString(", ") { "Teil $it der langen Aufzählung" } + "."
        val early = s.onDelta("$long Danach", 0)
        assertTrue("several pieces: ${early.size}", early.size >= 3)
        assertTrue(early.all { it.text.length <= 480 })
        assertEquals(long, early.joinToString(" ") { it.text })
    }

    @Test fun textWithoutAnySentenceEndFailsClosedInsteadOfGrowing() {
        val s = segmenter()
        assertTrue(s.onDelta("Ein guter Anfang. Dann ", 0).size == 1)
        repeat(60) { assertTrue(s.onDelta("endlos weiter ohne jedes Satzzeichen und ohne Pause ", it.toLong()).isEmpty()) }
        assertEquals(State.OVERFLOWED, s.state)
        assertFalse("a gap in the stream is never spoken over", s.finish(100).complete)
        assertEquals(listOf("Ein guter Anfang."), texts(s.retract().released))
    }

    @Test fun totalReleasedSegmentsAreBounded() {
        val s = segmenter(policy = Policy(confirmCharacters = 1, holdMillis = 0, maxEarlySegments = 4, maxTotalSegments = 4))
        s.onDelta("A ist da. B ist da. C ist da. D ist da. E ist da. F ist da. G", 0)
        val finish = s.finish(10)
        assertFalse(finish.complete)
        assertEquals(State.OVERFLOWED, s.state)
        assertEquals(4, s.released.size)
    }

    @Test fun timeNeverRunsBackwardsAndNegativeTimeIsRejected() {
        val s = segmenter(policy = Policy(confirmCharacters = 4, holdMillis = 100))
        s.onDelta("Erster Satz. Zweiter Satz", 500)
        // A late callback with an older timestamp cannot shorten the hold.
        assertTrue(s.poll(100).isEmpty())
        assertEquals(listOf("Erster Satz."), texts(s.poll(600)))
        try { s.poll(-1); org.junit.Assert.fail("negative time") } catch (_: IllegalArgumentException) { }
    }

    @Test fun policyBoundsAreValidated() {
        for (invalid in listOf<() -> Policy>({ Policy(confirmCharacters = 0) }, { Policy(holdMillis = -1) },
            { Policy(maxEarlySegments = 0) }, { Policy(maxEarlySegments = 65, maxTotalSegments = 64) },
            { Policy(maxSegmentCharacters = 20) }, { Policy(maxHeldCharacters = 10) })) {
            try { invalid(); org.junit.Assert.fail("invalid policy accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
