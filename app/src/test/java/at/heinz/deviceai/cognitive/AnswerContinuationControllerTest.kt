package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.AnswerContinuationController.Decision
import at.heinz.deviceai.cognitive.AnswerContinuationController.Reason
import at.heinz.deviceai.cognitive.AnswerContinuationController.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Code64: bounded automatic continuation at the output limit. The lane below repeats, step by step, what the local
 * turn lifecycle of the Activity does with the controller's decisions – with a fake generator instead of the model
 * and the real session context for the „weiter“ anchor. HOST, pure JVM.
 */
class AnswerContinuationControllerTest {
    private sealed class Outcome {
        /** Stopped at the output limit with accepted text. */
        data class Limited(val text: String) : Outcome()
        data class Complete(val text: String) : Outcome()
        /** Incomplete for another reason (thermal stop, stall) with accepted text. */
        data class Partial(val text: String, val blocker: String) : Outcome()
        /** Complete but withheld by the quality gate: no text. */
        object Withheld : Outcome()
        /** The generation threw (thermal pause, runtime error). */
        object Failed : Outcome()
        /** STOP: the result is not delivered at all. */
        object Cancelled : Outcome()
    }

    private class Shown(val text: String, val spoken: Boolean)

    private class Lane(private val controller: AnswerContinuationController, outcomes: List<Outcome>,
                       private val textOnly: Boolean = true) {
        private val generator = outcomes.iterator()
        val session = "chat-1"
        val context = LocalConversationContext()
        val shown = mutableListOf<Shown>()
        val statuses = mutableListOf<String>()
        val requests = mutableListOf<String>()
        var ownerRevision = 1L
        /** Called before each generation; lets a test change the world in the middle. */
        var beforeGeneration: (Int) -> Unit = {}

        fun ownerTurn(request: String) {
            var text = request
            var status: String? = null
            val revisionAtStart = ownerRevision
            while (true) {
                beforeGeneration(requests.size + 1)
                requests += text
                val continuation = status != null
                val outcome = generator.next()
                if (outcome == Outcome.Cancelled) {
                    if (!continuation) context.recordUnanswered(session, text)
                    return free()
                }
                if (outcome == Outcome.Failed) {
                    if (continuation) presentHeld()
                    shown += Shown("Lokaler Modellfehler", spoken = true)
                    if (!continuation) context.recordUnanswered(session, text)
                    return free()
                }
                val response = when (outcome) {
                    is Outcome.Limited -> outcome.text
                    is Outcome.Complete -> outcome.text
                    is Outcome.Partial -> outcome.text
                    else -> ""
                }
                val successful = outcome is Outcome.Complete
                val partial = outcome is Outcome.Limited || outcome is Outcome.Partial
                val blocker = when (outcome) {
                    is Outcome.Limited -> AnswerContinuationController.OUTPUT_LIMIT_BLOCKER
                    is Outcome.Partial -> outcome.blocker
                    Outcome.Withheld -> "Antwort zurückgehalten."
                    else -> null
                }
                val decision = controller.onSegment(session, ownerRevision, text, continuation,
                    eligible = partial && textOnly && ownerRevision == revisionAtStart,
                    segment = Segment(response, successful, partial, blocker))
                val delivered = decision as? Decision.Deliver
                if (decision !is Decision.Continue) shown += Shown(when {
                    delivered != null -> if (delivered.complete) delivered.answer else
                        LocalPartialAnswerPresentation.present(delivered.answer, delivered.blocker,
                            continuationAvailable = partial || context.hasContinuation(session))
                    partial -> LocalPartialAnswerPresentation.present(response, blocker, continuationAvailable = true)
                    successful -> response
                    else -> blocker ?: "Keine vollständige Antwort erhalten."
                }, spoken = true)
                if (delivered != null && !delivered.segmentAccepted) Unit
                else if (successful || partial) context.record(session, text, response, responseComplete = successful)
                else if (!continuation) context.recordUnanswered(session, text)
                if (decision is Decision.Continue) {
                    statuses += decision.status
                    status = decision.status
                    text = AnswerContinuationController.CONTINUE_REQUEST
                    continue
                }
                return free()
            }
        }

        /** setBusy(false): a continuation that still holds text ended without a result. */
        private fun free() = presentHeld()

        private fun presentHeld() {
            val held = controller.abort() ?: return
            shown += Shown(LocalPartialAnswerPresentation.present(held.answer, held.blocker,
                continuationAvailable = context.hasContinuation(held.sessionId)), spoken = false)
        }
    }

    private val a = "Erster Teil der Antwort endet mitten im"
    private val b = "Satz und geht hier weiter bis zum"
    private val c = "Ende der ganzen Antwort."
    private val d = "Ein vierter Teil, der nie erzeugt werden darf."
    private val question = "Erkläre mir das bitte ausführlich."
    private val hint = "\n\n" + LocalPartialAnswerPresentation.CONTINUE_HINT

    private fun lane(vararg outcomes: Outcome, textOnly: Boolean = true,
                     controller: AnswerContinuationController = AnswerContinuationController()) =
        Lane(controller, outcomes.toList(), textOnly)

    @Test fun aCompleteAnswerIsNotTouched() {
        val lane = lane(Outcome.Complete("Fertige Antwort."))
        lane.ownerTurn(question)
        assertEquals(listOf("Fertige Antwort."), lane.shown.map { it.text })
        assertEquals(emptyList<String>(), lane.statuses)
        assertEquals(listOf(question), lane.requests)
    }

    @Test fun oneLimitedSegmentIsContinuedOnceAndDeliveredAsOneAnswer() {
        val lane = lane(Outcome.Limited(a), Outcome.Complete("$b $c"))
        lane.ownerTurn(question)
        assertEquals(listOf("$a $b $c"), lane.shown.map { it.text })
        assertTrue(lane.shown.single().spoken)
        assertEquals(listOf("Antwort wird fortgesetzt (2/3)"), lane.statuses)
        assertEquals(listOf(question, "weiter"), lane.requests)
    }

    @Test fun twoLimitedSegmentsAreContinuedTwice() {
        val lane = lane(Outcome.Limited(a), Outcome.Limited(b), Outcome.Complete(c))
        lane.ownerTurn(question)
        assertEquals(listOf("$a $b $c"), lane.shown.map { it.text })
        assertEquals(listOf("Antwort wird fortgesetzt (2/3)", "Antwort wird fortgesetzt (3/3)"), lane.statuses)
        assertEquals(3, lane.requests.size)
    }

    @Test fun threeLimitedSegmentsEndWithTheAnswerFirstAndTheManualHint() {
        val lane = lane(Outcome.Limited(a), Outcome.Limited(b), Outcome.Limited("Ende der ganzen"), Outcome.Complete(d))
        lane.ownerTurn(question)
        assertEquals(listOf("$a $b Ende der ganzen …$hint"), lane.shown.map { it.text })
        assertTrue(lane.shown.single().spoken)
        assertEquals("the bound is three generations", 3, lane.requests.size)
        assertTrue("„weiter“ resumes the last segment", lane.context.hasContinuation(lane.session))
    }

    @Test fun fourLimitedSegmentsNeverLeadToAFourthGeneration() {
        val lane = lane(Outcome.Limited(a), Outcome.Limited(b), Outcome.Limited(c), Outcome.Limited(d))
        lane.ownerTurn(question)
        assertEquals(3, lane.requests.size)
        assertEquals(1, lane.shown.size)
        assertFalse(lane.shown.single().text.contains("vierter Teil"))
        assertTrue(lane.shown.single().text.endsWith(hint))
    }

    @Test fun aToolOrBotTurnIsPresentedAsBefore() {
        val lane = lane(Outcome.Limited(a), Outcome.Complete(b), textOnly = false)
        lane.ownerTurn(question)
        assertEquals(listOf("$a …$hint"), lane.shown.map { it.text })
        assertEquals(1, lane.requests.size)
        assertEquals(emptyList<String>(), lane.statuses)
    }

    @Test fun stopInTheMiddleShowsWhatWasGeneratedAndSpeaksNothing() {
        val controller = AnswerContinuationController()
        val lane = lane(Outcome.Limited(a), Outcome.Cancelled, controller = controller)
        lane.ownerTurn(question)
        assertEquals(listOf("$a …$hint"), lane.shown.map { it.text })
        assertFalse("the owner stopped it", lane.shown.single().spoken)
        assertFalse(controller.pending)
        assertEquals(2, lane.requests.size)
        // The cancelled segment was no owner turn: the part before it is still the anchor of a manual „weiter“.
        assertTrue(lane.context.hasContinuation(lane.session))
        assertEquals(listOf(question), lane.context.priorUserTurns(lane.session))
    }

    @Test fun aFailingSecondSegmentShowsThePartThenTheFailure() {
        val lane = lane(Outcome.Limited(a), Outcome.Failed)
        lane.ownerTurn(question)
        assertEquals(listOf("$a …$hint", "Lokaler Modellfehler"), lane.shown.map { it.text })
        assertEquals(listOf(false, true), lane.shown.map { it.spoken })
        assertEquals(2, lane.requests.size)
    }

    @Test fun aSecondSegmentThatStopsForAnotherReasonEndsTheContinuation() {
        val thermal = "Antwort unvollständig: " + LocalGenerationFailureCause.THERMAL_STOPPED.ownerMessage
        val lane = lane(Outcome.Limited(a), Outcome.Partial(b, thermal), Outcome.Complete(c))
        lane.ownerTurn(question)
        assertEquals(listOf("$thermal\n\n$a $b"), lane.shown.map { it.text })
        assertEquals(2, lane.requests.size)
    }

    @Test fun aWithheldOrEmptySecondSegmentKeepsTheFirstPartAndItsAnchor() {
        val withheld = lane(Outcome.Limited(a), Outcome.Withheld)
        withheld.ownerTurn(question)
        assertEquals(listOf("$a …$hint"), withheld.shown.map { it.text })
        assertTrue(withheld.context.hasContinuation(withheld.session))
        val repeat = lane(Outcome.Limited("$a Satz und"), Outcome.Limited("Satz und"))
        repeat.ownerTurn(question)
        assertEquals(listOf("$a Satz und …$hint"), repeat.shown.map { it.text })
    }

    @Test fun aSecondSegmentThatStartsAgainIsNeitherJoinedNorRecorded() {
        val first = "Die Photosynthese ist ein Prozess, bei dem Pflanzen aus Licht, Wasser und Kohlendioxid Zucker herstellen. Er besteht aus zwei"
        val restart = "Die Photosynthese ist ein Prozess, bei dem Pflanzen aus Licht Zucker herstellen."
        val lane = lane(Outcome.Limited(first), Outcome.Complete(restart))
        lane.ownerTurn(question)
        assertEquals(listOf("$first …$hint"), lane.shown.map { it.text })
        val anchor = lane.context.render(lane.session, maxBytes = 4_000, continuation = true)
        assertTrue(anchor, anchor.contains("Er besteht aus zwei"))
        assertFalse(anchor, anchor.contains("aus Licht Zucker herstellen"))
    }

    @Test fun aNewOwnerMessageDuringTheContinuationStopsFurtherSegments() {
        val lane = lane(Outcome.Limited(a), Outcome.Limited(b), Outcome.Complete(c))
        lane.beforeGeneration = { generation -> if (generation == 2) lane.ownerRevision++ }
        lane.ownerTurn(question)
        assertEquals(listOf("$a $b …$hint"), lane.shown.map { it.text })
        assertEquals(2, lane.requests.size)
    }

    @Test fun theCharacterBoundStopsTheContinuation() {
        val controller = AnswerContinuationController(maxCharacters = a.length + b.length + 1)
        val lane = lane(Outcome.Limited(a), Outcome.Limited(b), Outcome.Complete(c), controller = controller)
        lane.ownerTurn(question)
        assertEquals(listOf("$a $b …$hint"), lane.shown.map { it.text })
        assertEquals(2, lane.requests.size)
        val tight = lane(Outcome.Limited(a), Outcome.Complete("$b $c"),
            controller = AnswerContinuationController(maxCharacters = a.length + 5))
        tight.ownerTurn(question)
        assertEquals("the too long segment is not joined", listOf("$a …$hint"), tight.shown.map { it.text })
    }

    @Test fun decisionsOfThePureStateMachine() {
        val limit = AnswerContinuationController.OUTPUT_LIMIT_BLOCKER
        val controller = AnswerContinuationController()
        assertEquals("Antwort unvollständig: Das lokale Ausgabelimit wurde erreicht; die Antwort ist unvollständig.", limit)
        // Not eligible, not at the output limit, or a stray continuation: unchanged.
        assertEquals(Decision.Unchanged, controller.onSegment("s", 1, "q", false, false, Segment(a, false, true, limit)))
        assertEquals(Decision.Unchanged, controller.onSegment("s", 1, "q", false, true,
            Segment(a, false, true, "Antwort unvollständig: " + LocalGenerationFailureCause.STALLED.ownerMessage)))
        assertEquals(Decision.Unchanged, controller.onSegment("s", 1, "weiter", true, true, Segment(a, false, true, limit)))
        assertFalse(controller.pending)
        // First limited segment: continue with 2 of 3 and hold the text.
        assertEquals(Decision.Continue(2, 3), controller.onSegment("s", 1, "q", false, true, Segment(a, false, true, limit)))
        assertTrue(controller.pending)
        val held = controller.abort()!!
        assertEquals(a, held.answer); assertEquals(limit, held.blocker); assertEquals(1, held.segments)
        assertEquals("q", held.lastRequest); assertEquals(a, held.lastSegment)
        assertNull(controller.abort())
        // The last allowed segment is delivered with its reason.
        controller.onSegment("s", 1, "q", false, true, Segment(a, false, true, limit))
        controller.onSegment("s", 1, "weiter", true, true, Segment(b, false, true, limit))
        val last = controller.onSegment("s", 1, "weiter", true, true, Segment(c, false, true, limit)) as Decision.Deliver
        assertEquals(Reason.SEGMENT_LIMIT, last.reason); assertEquals(3, last.segments)
        assertFalse(last.complete); assertTrue(last.segmentAccepted); assertEquals("$a $b $c", last.answer)
        // A hold of another session is never joined with this one.
        controller.onSegment("s", 1, "q", false, true, Segment(a, false, true, limit))
        assertEquals(Decision.Unchanged, controller.onSegment("other", 1, "weiter", true, true, Segment(b, true, false, null)))
        assertEquals(a, controller.abort()!!.answer)
        // One generation per turn switches the feature off; more than the hard limit is refused.
        assertEquals(Decision.Unchanged, AnswerContinuationController(maxSegments = 1)
            .onSegment("s", 1, "q", false, true, Segment(a, false, true, limit)))
        assertTrue(runCatching { AnswerContinuationController(maxSegments = 5) }.isFailure)
        assertTrue(LocalConversationFollowUpPolicy.requestsContinuation(AnswerContinuationController.CONTINUE_REQUEST))
    }
}
