package at.heinz.deviceai.evaluation

import at.heinz.deviceai.localmodel.LocalInferenceCallback
import at.heinz.deviceai.localmodel.LocalInferenceEvent
import at.heinz.deviceai.localmodel.LocalInferenceMetrics
import at.heinz.deviceai.localmodel.LocalModelThermalGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which events of a turn are timestamped and how its end is classified. HOST: a clock the test moves by hand. */
class HeatTurnRecorderTest {
    private var now = 1_000L
    private val recorder = HeatTurnRecorder { now }
    private val stage = HeatTurnRecorder.EVENT_STAGE_MESSAGE

    /** Hand-over, the owner's bubble, busy, one model call that starts; returns the call's probe and callback. */
    private fun started(promptCharacters: Int = 900): Pair<HeatTurnRecorder.GenerationProbe, LocalInferenceCallback> {
        recorder.begin()
        now += 20; recorder.bubble(true, 40, stage, true)
        now += 10; recorder.busy(true)
        now += 70
        val probe = recorder.probe(promptCharacters)!!
        return probe to probe.observe(LocalInferenceCallback { })
    }

    private fun completed(characters: Int, first: Long?, total: Long) = LocalInferenceEvent.Completed(
        LocalInferenceMetrics(900, characters, 3, 210, 55, first, total))

    @Test fun aCompleteTurnIsTimedFromTheHandOver() {
        val (probe, callback) = started()
        now = 1_000 + 1_500; callback.onEvent(LocalInferenceEvent.TextDelta(""))
        assertNull("An empty piece is not a first token", recorder.result().firstTokenMs)
        now = 1_000 + 2_000; callback.onEvent(LocalInferenceEvent.TextDelta("Der Himmel"))
        now = 1_000 + 2_300; callback.onEvent(LocalInferenceEvent.TextDelta(" ist blau."))
        now = 1_000 + 6_000; callback.onEvent(completed(200, 1_900, 5_900))
        probe.finished("COMPLETE", null, 200)
        now = 1_000 + 6_100; recorder.bubble(false, 190, stage, true)
        now = 1_000 + 6_150; recorder.busy(false)
        assertEquals("The app may start another busy phase of the same turn", HeatTurnRecorder.State.RUNNING, recorder.poll(appBusy = false))
        now += HeatTurnRecorder.QUIET_END_MS
        assertEquals(HeatTurnRecorder.State.ENDED, recorder.poll(appBusy = false))
        val result = recorder.result()
        assertEquals(HeatTurnRecorder.COMPLETE, result.outcome)
        assertEquals(20L, result.acceptedMs)
        assertEquals(2_000L, result.firstTokenMs)
        assertEquals("In the stage the first answer text the owner can see is the finished answer", 6_100L, result.firstVisibleTextMs)
        assertEquals(stage, result.firstVisibleTextEvent)
        assertEquals(true, result.firstVisibleTextShown)
        assertEquals(6_100L, result.answerHandoverMs)
        assertEquals(6_150L, result.totalMs)
        assertEquals(1, result.modelCalls)
        assertEquals(listOf("COMPLETE"), result.completions)
        assertEquals(900, result.promptCharacters)
        assertEquals(200, result.generatedCharacters)
        assertEquals(190, result.answerCharacters)
        assertEquals("Characters per second after the first token", 200 * 1_000.0 / 4_000, result.charsPerSecond!!, 1e-9)
        assertEquals(210, result.inputTokens)
        assertEquals(55, result.outputTokens)
        assertEquals(1_900L, result.runtimeFirstTokenMs)
    }

    @Test fun memoryStageAndModelStageAreOneTurnAndTheTotalIsItsLastRelease() {
        recorder.begin()
        now += 20; recorder.bubble(true, 40, stage, true)
        // The app looks into memory and context first: busy, free, and at once busy again for the model.
        now += 5; recorder.busy(true)
        now += 400; recorder.busy(false); recorder.busy(true)
        assertEquals(HeatTurnRecorder.State.RUNNING, recorder.poll(appBusy = true))
        val probe = recorder.probe(900)!!
        now += 3_000; probe.observe(LocalInferenceCallback { }).onEvent(LocalInferenceEvent.TextDelta("Antwort"))
        now += HeatTurnRecorder.QUIET_END_MS * 3
        assertEquals("A long answer is not a quiet app", HeatTurnRecorder.State.RUNNING, recorder.poll(appBusy = true))
        probe.finished("COMPLETE", null, 70)
        now += 10; recorder.bubble(false, 70, stage, true)
        val released = now - 1_000; recorder.busy(false)
        now += HeatTurnRecorder.QUIET_END_MS - 1
        assertEquals(HeatTurnRecorder.State.RUNNING, recorder.poll(appBusy = false))
        now += 1
        assertEquals(HeatTurnRecorder.State.ENDED, recorder.poll(appBusy = false))
        val result = recorder.result()
        assertEquals(HeatTurnRecorder.COMPLETE, result.outcome)
        assertEquals("The wait for a possible next phase is not part of the answer time", released, result.totalMs)
        assertEquals(1, result.modelCalls)
        assertEquals(3_425L, result.firstTokenMs)
    }

    @Test fun theNativeDraftCountsAsFirstVisibleTextOnlyWhenItsViewIsShown() {
        val (probe, callback) = started()
        now = 1_000 + 2_000; callback.onEvent(LocalInferenceEvent.TextDelta("Der"))
        now = 1_000 + 2_050; recorder.previewTextSet(shown = false)
        now = 1_000 + 3_000; recorder.previewTextSet(shown = true)
        probe.finished("COMPLETE", null, 80)
        now = 1_000 + 5_000; recorder.bubble(false, 80, stage, true); recorder.busy(false)
        val hidden = recorder.result()
        assertEquals("A draft in a view that is not shown is recorded, but it is not what the owner saw", 2_050L, hidden.nativePreviewMs)
        assertEquals(false, hidden.nativePreviewShown)
        assertEquals(5_000L, hidden.firstVisibleTextMs)
        assertEquals(stage, hidden.firstVisibleTextEvent)

        val (again, _) = started()
        val begun = now - 100
        now = begun + 1_800; recorder.previewTextSet(shown = true)
        again.finished("COMPLETE", null, 80)
        now = begun + 4_000; recorder.bubble(false, 80, HeatTurnRecorder.EVENT_NATIVE_BUBBLE, true); recorder.busy(false)
        val shown = recorder.result()
        assertEquals(1_800L, shown.firstVisibleTextMs)
        assertEquals(HeatTurnRecorder.EVENT_NATIVE_PREVIEW, shown.firstVisibleTextEvent)
        assertEquals(true, shown.firstVisibleTextShown)
    }

    @Test fun anAnswerHandedToAHiddenStageSaysSo() {
        val (probe, _) = started()
        probe.finished("COMPLETE", null, 50)
        now += 3_000; recorder.bubble(false, 50, stage, shown = false); recorder.busy(false)
        val result = recorder.result()
        assertEquals(HeatTurnRecorder.COMPLETE, result.outcome)
        assertEquals(false, result.firstVisibleTextShown)
    }

    @Test fun theThermalGuardIsNamedWhetherItStoppedOrDidNotStartTheAnswer() {
        val (stopped, callback) = started()
        now += 2_000; callback.onEvent(LocalInferenceEvent.TextDelta("Halbe Antw"))
        callback.onEvent(LocalInferenceEvent.Failed(LocalModelThermalGuard.STOPPED_THERMAL))
        stopped.finished("FAILED", LocalModelThermalGuard.STOPPED_THERMAL, 10)
        now += 100; recorder.bubble(false, 120, stage, true); recorder.busy(false)
        assertEquals(HeatTurnRecorder.STOPPED_BY_THERMAL_GUARD, recorder.result().outcome)
        assertEquals("What the model wrote before the stop is counted", 10, recorder.result().generatedCharacters)

        val (paused, _) = started()
        paused.finished("FAILED", LocalModelThermalGuard.PAUSED_THERMAL, 0)
        now += 50; recorder.bubble(false, 150, stage, true); recorder.busy(false)
        val result = recorder.result()
        assertEquals(HeatTurnRecorder.PAUSED_BY_THERMAL_GUARD, result.outcome)
        assertNull(result.firstTokenMs)
        assertNull(result.charsPerSecond)
    }

    @Test fun stallOutputLimitFailureAndAppCancellationAreDifferentOutcomes() {
        for ((completion, expected) in listOf("STALLED" to HeatTurnRecorder.STALLED, "OUTPUT_LIMIT" to HeatTurnRecorder.OUTPUT_LIMIT,
            "FAILED" to HeatTurnRecorder.FAILED, "CANCELLED" to HeatTurnRecorder.CANCELLED_BY_APP)) {
            val (probe, _) = started()
            probe.finished(completion, "diagnostic $completion", 30)
            now += 10; recorder.busy(false)
            val result = recorder.result()
            assertEquals(completion, expected, result.outcome)
            assertEquals("diagnostic $completion", result.message)
            assertTrue(expected + " is an interruption", expected in HeatTurnRecorder.ABORTS)
        }
    }

    @Test fun anAutomaticContinuationIsOneTurnWithSeveralModelCalls() {
        val (first, callback) = started()
        now += 1_000; callback.onEvent(LocalInferenceEvent.TextDelta("Teil eins"))
        now += 3_000; first.finished("OUTPUT_LIMIT", "OUTPUT_LIMIT", 400)
        val second = recorder.probe(1_200)!!
        val next = second.observe(LocalInferenceCallback { })
        now += 500; next.onEvent(LocalInferenceEvent.TextDelta("Teil zwei"))
        now += 1_500; second.finished("COMPLETE", null, 150)
        now += 20; recorder.bubble(false, 540, stage, true); recorder.busy(false)
        val result = recorder.result()
        assertEquals(HeatTurnRecorder.COMPLETE, result.outcome)
        assertEquals(2, result.modelCalls)
        assertEquals(listOf("OUTPUT_LIMIT", "COMPLETE"), result.completions)
        assertEquals(550, result.generatedCharacters)
        assertEquals("The first token belongs to the first call", 1_100L, result.firstTokenMs)
        assertEquals("Writing time is summed per call", 550 * 1_000.0 / (3_000 + 1_500), result.charsPerSecond!!, 1e-9)
        assertEquals("The prompt size of the first call is the turn's", 900, result.promptCharacters)
    }

    @Test fun ownerStopAndTimeoutWinOverWhatTheCancelledCallReports() {
        val (probe, _) = started()
        recorder.ownerStop()
        probe.finished("CANCELLED", "Local generation cancelled.", 12)
        recorder.busy(false)
        assertEquals(HeatTurnRecorder.CANCELLED_BY_OWNER, recorder.result().outcome)
        assertFalse("The owner's stop is not an interruption by the app", HeatTurnRecorder.CANCELLED_BY_OWNER in HeatTurnRecorder.ABORTS)

        val (late, _) = started()
        recorder.timeout()
        late.finished("CANCELLED", null, 0)
        recorder.busy(false)
        assertEquals(HeatTurnRecorder.TIMEOUT, recorder.result().outcome)
    }

    @Test fun aReplyWithoutTheModelAndAnOrderThatWasNeverAcceptedAreNotMeasurementsOfTheModel() {
        recorder.begin()
        now += 30; recorder.bubble(true, 20, stage, true)
        now += 5; recorder.bubble(false, 60, stage, true)
        assertEquals("The app may still start work", HeatTurnRecorder.State.RUNNING, recorder.poll(appBusy = false))
        now += HeatTurnRecorder.QUIET_END_MS
        assertEquals(HeatTurnRecorder.State.ENDED, recorder.poll(appBusy = false))
        val reply = recorder.result()
        assertEquals(HeatTurnRecorder.NO_MODEL_CALL, reply.outcome)
        assertEquals(0, reply.modelCalls)
        assertFalse(HeatTurnRecorder.NO_MODEL_CALL in HeatTurnRecorder.ABORTS)

        recorder.begin()
        now += HeatTurnRecorder.ACCEPT_TIMEOUT_MS - 1
        assertEquals(HeatTurnRecorder.State.RUNNING, recorder.poll(appBusy = false))
        now += 1
        assertEquals(HeatTurnRecorder.State.ENDED, recorder.poll(appBusy = false))
        assertEquals(HeatTurnRecorder.NOT_ACCEPTED, recorder.result().outcome)
        assertNull(recorder.result().acceptedMs)
    }

    @Test fun aBusyPhaseBeforeTheOrderWasAcceptedDoesNotEndTheTurn() {
        recorder.begin()
        recorder.busy(true); recorder.busy(false)
        assertEquals(HeatTurnRecorder.State.RUNNING, recorder.poll(appBusy = false))
    }

    @Test fun whatWasWithheldFromTheOwnerIsNotACompleteAnswer() {
        val (probe, _) = started()
        probe.finished("COMPLETE", null, 90)
        now += 10; recorder.busy(false)
        val result = recorder.result()
        assertEquals(HeatTurnRecorder.FAILED, result.outcome)
        assertEquals("Die Antwort des Modells wurde nicht angezeigt.", result.message)
        assertNull(result.firstVisibleTextMs)
    }

    @Test fun theDialogueReuseReceiptIsKeptWithTheTurn() {
        val (probe, _) = started()
        assertNull(recorder.result().toolFreeRoute)
        recorder.route(withoutTools = true)
        recorder.route(withoutTools = false)
        recorder.dialogueReuse(true, null)
        probe.finished("COMPLETE", null, 10)
        recorder.bubble(false, 10, stage, true); recorder.busy(false)
        assertEquals(true, recorder.result().dialogueReused)
        assertEquals("The route of the turn's first model call", true, recorder.result().toolFreeRoute)

        val (other, _) = started()
        assertNull("A new turn starts without the previous receipt", recorder.result().dialogueReused)
        recorder.dialogueReuse(false, "RESOLVED_BY_APP")
        other.finished("COMPLETE", null, 10)
        assertEquals(false, recorder.result().dialogueReused)
        assertEquals("RESOLVED_BY_APP", recorder.result().dialogueReuseReason)
    }

    @Test fun outsideAMeasuredTurnNothingIsObservedAndTheProbeForwardsEveryEventUnchanged() {
        assertNull("No turn of a measurement: the app's generation gets no probe", recorder.probe(100))
        recorder.busy(true); recorder.bubble(false, 10, stage, true); recorder.previewTextSet(true)
        assertEquals(HeatTurnRecorder.State.IDLE, recorder.poll(appBusy = true))

        recorder.begin()
        recorder.bubble(true, 5, stage, true)
        val seen = ArrayList<LocalInferenceEvent>()
        val probe = recorder.probe(100)!!
        val callback = probe.observe(LocalInferenceCallback { seen += it })
        val events = listOf(LocalInferenceEvent.TextDelta("a"), LocalInferenceEvent.ToolProposalRejected("tool", "no"),
            completed(1, 5, 9), LocalInferenceEvent.Cancelled, LocalInferenceEvent.Failed("x"))
        events.forEach(callback::onEvent)
        assertEquals(events.size, seen.size)
        events.forEachIndexed { index, event -> assertSame(event, seen[index]) }
        // A late call of an ended turn must not reach the next turn.
        recorder.forceEnd()
        recorder.begin()
        probe.finished("COMPLETE", null, 999)
        callback.onEvent(LocalInferenceEvent.TextDelta("late"))
        assertEquals(0, recorder.result().modelCalls)
    }
}
