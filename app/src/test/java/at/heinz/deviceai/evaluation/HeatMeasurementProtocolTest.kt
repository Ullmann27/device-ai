package at.heinz.deviceai.evaluation

import at.heinz.deviceai.localmodel.LocalModelThermalGuard
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The measurement protocol against a scripted phone ([HeatMeasurementFakeDevice]) whose clock moves only when the
 * protocol waits. HOST: order, single-factor configurations, cool-down, flags, stop, restore, NOT_RUN. Every
 * temperature, status and duration in these tests is synthetic.
 */
class HeatMeasurementProtocolTest {
    private fun file(path: String) = File(path).takeIf(File::isFile) ?: File("../$path")
    private val prompts = HeatPromptSet.parse(file("app/src/main/assets/${HeatMeasurement.PROMPT_ASSET}").readText(Charsets.UTF_8))
    private val device = HeatMeasurementFakeDevice()

    private fun plan(size: HeatSize = HeatSize.KURZ, limits: HeatLimits = HeatLimits()) =
        HeatPlan(size, prompts, HeatMeasurement.CONFIGURATIONS, limits)

    private fun measure(plan: HeatPlan = plan()): JSONObject =
        HeatMeasurementProtocol(device, plan).run(1_794_000_000_000L) { 1_794_000_600_000L }

    private fun objects(array: JSONArray) = (0 until array.length()).map(array::getJSONObject)
    private fun configuration(report: JSONObject, id: String) = objects(report.getJSONArray("configurations")).first { it.getString("id") == id }
    private fun runs(report: JSONObject) = objects(report.getJSONArray("runs"))
    private fun turns(run: JSONObject) = objects(run.getJSONArray("turns"))

    @Test fun aShortMeasurementRunsEveryConfigurationWithItsOneFactorAndPutsEverythingBack() {
        val before = device.settings()
        val report = measure()
        assertEquals(HeatMeasurement.SCHEMA, report.getString("schema"))
        assertEquals("KURZ", report.getString("size"))
        val input = report.getJSONObject("input")
        assertEquals(HeatMeasurement.INPUT_NOTE, input.getString("note"))
        assertFalse("The probe never uses the microphone and says so", input.getBoolean("microphone"))
        assertEquals("continuum-heat-v1", input.getString("promptSet"))

        assertEquals(listOf("A", "B", "C", "D", "E"), runs(report).map { it.getString("configuration") })
        val orders = prompts.prompts.first().turns
        assertEquals("Identical orders in every configuration, each differing from A in one setting", listOf(
            "RESTING/false/gemma4-e4b-it", "OFF/false/gemma4-e4b-it", "FULL_EFFECTS/false/gemma4-e4b-it",
            "RESTING/true/gemma4-e4b-it", "RESTING/false/gemma4-e2b-it").flatMap { setting -> orders.map { "$setting|$it" } },
            device.turnsStarted)
        runs(report).forEach { run ->
            assertEquals(listOf("ERSTFRAGE", "FOLGEFRAGE"), turns(run).map { it.getString("role") })
            turns(run).forEach { turn ->
                assertEquals(HeatTurnRecorder.COMPLETE, turn.getString("outcome"))
                assertEquals("Loading a model is never part of an answer time", 4_000L, turn.getLong("totalMs"))
                assertEquals(1_000L, turn.getLong("firstTokenMs"))
            }
        }
        assertEquals("Every run starts in a new chat", 5, device.log.count { it.startsWith("chat:") })
        assertTrue(device.log.indexOf("chat:1") < device.log.indexOfFirst { it.startsWith("turn:") })
        assertEquals("The report so far is handed over after every run", listOf(1, 2, 3, 4, 5), device.checkpoints)

        for (id in listOf("A", "B", "C", "D", "E")) assertEquals(id, HeatMeasurementProtocol.RUN, configuration(report, id).getString("status"))
        val gpu = configuration(report, "F")
        assertEquals(HeatMeasurement.NOT_RUN, gpu.getString("status"))
        assertTrue(gpu.getString("reason").isNotBlank())
        assertEquals("gemma4-e4b-it", configuration(report, "A").getJSONObject("model").getString("id"))
        assertEquals("gemma4-e2b-it", configuration(report, "E").getJSONObject("model").getString("id"))
        val lifecycle = objects(configuration(report, "E").getJSONArray("modelLifecycle"))
        assertEquals("One swap in and one back, each step timed on its own",
            listOf("UNLOAD", "LOAD", "UNLOAD", "LOAD"), lifecycle.map { it.getString("action") })
        assertEquals(listOf(false, false, true, true), lifecycle.map { it.optBoolean("restoresBaseline") })
        assertEquals(listOf("swap:gemma4-e2b-it", "swap:gemma4-e4b-it"), device.log.filter { it.startsWith("swap:") })

        assertTrue("A planned configuration that is NOT_RUN does not make the measurement incomplete", report.getBoolean("complete"))
        assertTrue(report.isNull("stop"))
        assertEquals("Stage, dialogue switch and model are back", before, device.settings())
        assertEquals(0, device.chat)
        assertEquals(1, device.restores)
        assertTrue(report.getBoolean("restoreComplete"))
        assertEquals(1_794_000_600_000L, report.getLong("endedAtEpochMs"))
    }

    @Test fun theFullMeasurementFollowsThePlannedOrder() {
        val report = measure(plan(HeatSize.VOLL))
        assertEquals(plan(HeatSize.VOLL).runs.filter { it.configuration.id != "F" }
            .map { it.configuration.id + ":" + it.prompt.id + ":" + it.repetition },
            runs(report).map { it.getString("configuration") + ":" + it.getString("prompt") + ":" + it.getInt("repetition") })
        assertEquals("The model is swapped once for the whole block", 2, device.log.count { it.startsWith("swap:") })
        assertTrue(report.getBoolean("complete"))
    }

    @Test fun theFirstRunRestsTheMinimumAndARunWhoseStartConditionIsNotReachedIsFlagged() {
        device.script = { _, _ -> HeatMeasurementFakeDevice.Turn(heatsBatteryC = 3.0) }
        device.coolingCPerSecond = 0.01
        val limits = HeatLimits(cooldownMaxMs = 60_000L)
        val report = measure(plan(limits = limits))
        val start = report.getJSONObject("baselineStart")
        assertEquals(30.0, start.getDouble("batteryTempC"), 1e-9)
        assertEquals(0, start.getInt("thermalStatus"))
        val first = runs(report)[0].getJSONObject("startCondition")
        assertTrue(first.getBoolean("reached"))
        assertEquals("Even a cool device rests the minimum", limits.cooldownMinMs, first.getLong("waitedMs"))
        assertTrue(first.isNull("reason"))
        val second = runs(report)[1].getJSONObject("startCondition")
        assertFalse("The battery is still warm after the longest allowed wait: flagged, not hidden", second.getBoolean("reached"))
        assertEquals(limits.cooldownMaxMs, second.getLong("waitedMs"))
        assertTrue(second.getString("reason"), "Akkutemperatur außerhalb des Startbands" in second.getString("reason"))
        assertTrue("The actual start value is written down", second.getDouble("batteryTempC") > 31.0)
        assertEquals("The flagged run still takes place", HeatTurnRecorder.COMPLETE, turns(runs(report)[1])[0].getString("outcome"))
    }

    @Test fun coolDownWaitsUntilTheThermalStatusIsBackAtTheStartStatus() {
        device.script = { _, _ -> HeatMeasurementFakeDevice.Turn(thermalStatus = 2, heatsBatteryC = 0.0) }
        device.thermalRecoversAfterMs = 30_000L
        val report = measure()
        val second = runs(report)[1].getJSONObject("startCondition")
        assertTrue(second.getBoolean("reached"))
        assertEquals(30_000L, second.getLong("waitedMs"))
        assertEquals(0, second.getInt("thermalStatus"))
        val thermal = turns(runs(report)[0])[0].getJSONObject("thermal")
        assertEquals(listOf(0, 2, 2), listOf(thermal.getInt("start"), thermal.getInt("end"), thermal.getInt("max")))
    }

    @Test fun aStartConditionThatCannotBeCheckedIsNotCalledReached() {
        device.reportsThermal = false
        device.reportsBattery = false
        val condition = runs(measure())[0].getJSONObject("startCondition")
        assertFalse(condition.getBoolean("reached"))
        assertTrue(condition.getString("reason"), "nicht prüfbar" in condition.getString("reason"))
        assertTrue(condition.isNull("thermalStatus") && condition.isNull("batteryTempC"))
    }

    @Test fun aBatteryColderThanTheStartBandIsFlaggedWithoutWaitingForTheMaximum() {
        device.batteryC = 34.0
        device.batteryFloor = 30.0
        device.coolingCPerSecond = 0.2
        val condition = runs(measure())[1].getJSONObject("startCondition")
        assertFalse(condition.getBoolean("reached"))
        assertTrue(condition.getString("reason"), "kälter als das Startband" in condition.getString("reason"))
        assertTrue(condition.getLong("waitedMs") < HeatLimits().cooldownMaxMs)
    }

    @Test fun whatCannotRunOnThisPhoneIsRecordedAsNotRunAndNeverReplaced() {
        device.e2bImported = false
        device.stageAvailable = false
        val report = measure()
        assertEquals(listOf("A", "D"), runs(report).map { it.getString("configuration") })
        for (id in listOf("B", "C", "E", "F")) {
            assertEquals(id, HeatMeasurement.NOT_RUN, configuration(report, id).getString("status"))
            assertTrue(id, configuration(report, id).getString("reason").startsWith("Synthetisch"))
            assertTrue(configuration(report, id).isNull("model"))
        }
        assertTrue("No other model stands in for the missing one", device.log.none { it.startsWith("swap:") })
        assertTrue(device.turnsStarted.all { it.startsWith("RESTING/") && "gemma4-e4b-it" in it })
        assertTrue(report.getBoolean("complete"))
    }

    @Test fun stopInTheMiddleOfAnAnswerKeepsWhatWasMeasuredAndRestoresTheOverrides() {
        val before = device.settings()
        device.stopWhen = { phone -> if (phone.turnsStarted.size == 3) HeatStop(HeatStop.OWNER_STOP) else null }
        val report = measure()
        assertEquals("The complete run and the stopped one are both in the report", 2, runs(report).size)
        assertEquals(listOf(HeatTurnRecorder.COMPLETE, HeatTurnRecorder.COMPLETE), turns(runs(report)[0]).map { it.getString("outcome") })
        val stopped = turns(runs(report)[1])
        assertEquals(listOf(HeatTurnRecorder.CANCELLED_BY_OWNER, HeatMeasurement.NOT_RUN), stopped.map { it.getString("outcome") })
        assertTrue("The app's own stop control ended the answer", "abort:stop" in device.log)
        assertEquals(HeatStop.OWNER_STOP, report.getJSONObject("stop").getString("reason"))
        assertFalse(report.getBoolean("complete"))
        assertEquals(HeatMeasurementProtocol.RUN, configuration(report, "B").getString("status"))
        for (id in listOf("C", "D", "E", "F")) {
            assertEquals(HeatMeasurement.NOT_RUN, configuration(report, id).getString("status"))
            assertTrue(configuration(report, id).getString("reason"), "endete, bevor" in configuration(report, id).getString("reason"))
        }
        assertEquals("The stage was off in the stopped configuration and is back", before, device.settings())
        assertEquals(1, device.restores)
        assertTrue(report.getBoolean("restoreComplete"))
        assertEquals(listOf("Chat", "Bühne"), objects(report.getJSONArray("restore")).map { it.getString("what") })
    }

    @Test fun leavingTheAppWhileCoolingDownEndsTheMeasurementBeforeTheNextRun() {
        device.stopWhen = { phone -> if (phone.checkpoints.size == 1) HeatStop(HeatStop.LEFT_FOREGROUND, "synthetic") else null }
        val report = measure()
        assertEquals(1, runs(report).size)
        assertEquals(2, device.turnsStarted.size)
        assertEquals(HeatStop.LEFT_FOREGROUND, report.getJSONObject("stop").getString("reason"))
        assertEquals("RESTING/false/gemma4-e4b-it", device.settings())
    }

    @Test fun stopWhileAnotherModelIsLoadedBringsTheBaselineModelBack() {
        device.stopWhen = { phone -> if (phone.model == "gemma4-e2b-it" && phone.turnsStarted.size == 9) HeatStop(HeatStop.OWNER_STOP) else null }
        val report = measure()
        assertEquals(HeatTurnRecorder.CANCELLED_BY_OWNER, turns(runs(report).last())[0].getString("outcome"))
        assertEquals("gemma4-e4b-it", device.model)
        assertTrue("Geladenes Modell" in objects(report.getJSONArray("restore")).map { it.getString("what") })
    }

    @Test fun anAnswerOverTheTimeLimitIsStoppedWithTheAppsStopControlAndTheMeasurementGoesOn() {
        device.script = { phone, _ -> HeatMeasurementFakeDevice.Turn(
            durationMs = if (phone.stage == HeatStageMode.FULL_EFFECTS) 500_000L else 4_000L) }
        val report = measure(plan(limits = HeatLimits(turnTimeoutMs = 30_000L)))
        val slow = turns(runs(report).first { it.getString("configuration") == "C" })
        assertEquals(listOf(HeatTurnRecorder.TIMEOUT, HeatMeasurement.NOT_RUN), slow.map { it.getString("outcome") })
        assertTrue("abort:timeout" in device.log)
        assertEquals(listOf("A", "B", "C", "D", "E"), runs(report).map { it.getString("configuration") })
        assertTrue(report.isNull("stop"))
    }

    @Test fun aFailedModelSwapMarksTheConfigurationNotRunAndLoadsTheBaselineModelAgain() {
        device.swapFailure = "synthetic load failure"
        val report = measure()
        val small = configuration(report, "E")
        assertEquals(HeatMeasurement.NOT_RUN, small.getString("status"))
        assertTrue(small.getString("reason"), "Modellwechsel fehlgeschlagen: LOAD – synthetic load failure" in small.getString("reason"))
        assertEquals(listOf(true, false, true, true), objects(small.getJSONArray("modelLifecycle")).map { it.getBoolean("ok") })
        assertEquals("gemma4-e4b-it", device.model)
        assertEquals(listOf("A", "B", "C", "D"), runs(report).map { it.getString("configuration") })
        assertTrue(report.isNull("stop"))
    }

    @Test fun aFailureInsideTheMeasurementStillRestoresAndKeepsTheRunsSoFar() {
        val before = device.settings()
        device.script = { phone, _ -> if (phone.stage == HeatStageMode.FULL_EFFECTS) error("synthetic failure in the turn path")
            else HeatMeasurementFakeDevice.Turn() }
        val report = measure()
        assertEquals(HeatStop.ERROR, report.getJSONObject("stop").getString("reason"))
        assertEquals("synthetic failure in the turn path", report.getJSONObject("stop").getString("message"))
        assertEquals(listOf("A", "B"), runs(report).map { it.getString("configuration") })
        assertEquals(before, device.settings())
        assertEquals(1, device.restores)
    }

    @Test fun aRestoreStepThatFailsIsReportedAndDoesNotKeepTheOthersFromBeingRestored() {
        device.failRestoreOf = "Bühne"
        device.stopWhen = { phone -> if (phone.turnsStarted.size == 7) HeatStop(HeatStop.OWNER_STOP) else null }
        val report = measure()
        assertFalse(report.getBoolean("restoreComplete"))
        val steps = objects(report.getJSONArray("restore"))
        assertEquals(listOf(false), steps.filter { it.getString("what") == "Bühne" }.map { it.getBoolean("ok") })
        assertFalse("The dialogue switch was on in the stopped configuration D and is off again", device.dialogueReuse)
        assertEquals(0, device.chat)
    }

    @Test fun criticalHeatEndsTheMeasurementAfterThatAnswer() {
        device.script = { _, _ -> HeatMeasurementFakeDevice.Turn(thermalStatus = 4, completion = "FAILED",
            failure = LocalModelThermalGuard.STOPPED_THERMAL) }
        val report = measure()
        assertEquals(1, runs(report).size)
        assertEquals(listOf(HeatTurnRecorder.STOPPED_BY_THERMAL_GUARD, HeatMeasurement.NOT_RUN),
            turns(runs(report)[0]).map { it.getString("outcome") })
        assertEquals(HeatStop.THERMAL_CRITICAL, report.getJSONObject("stop").getString("reason"))
        assertEquals(1, device.turnsStarted.size)
    }

    @Test fun aBlockedStartMeasuresNothingAndChangesNothing() {
        device.blocked = "Kein geladenes Modell im Zustand BEREIT."
        val report = measure()
        assertEquals(HeatStop.PRECONDITION, report.getJSONObject("stop").getString("reason"))
        assertEquals("Kein geladenes Modell im Zustand BEREIT.", report.getJSONObject("stop").getString("message"))
        assertEquals(0, runs(report).size)
        assertTrue(device.log.none { it.startsWith("turn:") || it.startsWith("stage:") || it.startsWith("chat:") })
        objects(report.getJSONArray("configurations")).forEach { assertEquals(HeatMeasurement.NOT_RUN, it.getString("status")) }
        assertTrue(report.isNull("baselineStart"))
    }

    @Test fun aReplyWithoutTheModelIsNamedAndItsFollowUpIsNotAsked() {
        device.script = { _, _ -> HeatMeasurementFakeDevice.Turn(modelCall = false) }
        val first = turns(runs(measure())[0])
        assertEquals(listOf(HeatTurnRecorder.NO_MODEL_CALL, HeatMeasurement.NOT_RUN), first.map { it.getString("outcome") })
        assertEquals(0, first[0].getInt("modelCalls"))
        assertTrue(first[0].isNull("firstTokenMs"))
    }

    @Test fun anOrderTheAppDoesNotAcceptEndsAsNotAccepted() {
        device.script = { _, _ -> HeatMeasurementFakeDevice.Turn(accepted = false) }
        val first = turns(runs(measure())[0])[0]
        assertEquals(HeatTurnRecorder.NOT_ACCEPTED, first.getString("outcome"))
        assertEquals(HeatTurnRecorder.ACCEPT_TIMEOUT_MS, first.getLong("totalMs"))
    }

    @Test fun samplesStatusChangesFramesAndTheSpokenAnswerAreKeptPerTurn() {
        device.script = { _, _ -> HeatMeasurementFakeDevice.Turn(thermalStatus = 1, speechMs = 3_000L) }
        val turn = turns(runs(measure())[0])[0]
        val samples = objects(turn.getJSONArray("samples"))
        val answer = samples.filter { it.getString("phase") == HeatMeasurementProtocol.PHASE_ANSWER }
        assertEquals("Start, every two seconds, up to the release of the turn", listOf(0L, 2_000L, 4_000L), answer.map { it.getLong("tMs") })
        assertEquals("What was read while the end was not yet certain is kept, marked, and not evaluated", listOf(5_000L),
            samples.filter { it.getString("phase") == HeatMeasurementProtocol.PHASE_AFTER }.map { it.getLong("tMs") })
        assertTrue(answer.all { it.has("thermalStatus") && it.has("headroom") && it.has("batteryTempC") && it.has("batteryPercent") })
        val thermal = turn.getJSONObject("thermal")
        assertEquals(listOf(0, 1, 1), listOf(thermal.getInt("start"), thermal.getInt("end"), thermal.getInt("max")))
        val change = thermal.getJSONArray("changes").getJSONObject(0)
        assertEquals("Every status change with its time after the hand-over", 2_000L, change.getLong("tMs"))
        assertEquals(1, change.getInt("status"))
        assertEquals(30.4, turn.getJSONObject("batteryTempC").getDouble("max"), 1e-9)
        assertEquals(30.0, turn.getJSONObject("batteryTempC").getDouble("start"), 1e-9)
        assertEquals("The spoken answer is awaited and timed apart from the answer", 3_000L, turn.getLong("speechOutputMs"))
        assertTrue(samples.count { it.getString("phase") == HeatMeasurementProtocol.PHASE_SPEECH } >= 2)
        assertEquals(4_000L, turn.getLong("totalMs"))
        assertEquals(HeatFrameAccumulator.MEASURED, turn.getJSONObject("windowFrames").getString("status"))
        assertEquals(4, turn.getJSONObject("windowFrames").getInt("frames"))
        assertEquals(20, turn.getJSONObject("stageFrames").getInt("delivered"))
    }

    @Test fun theLedgerRestoresNewestFirstKeepsTheFirstUndoAndSurvivesAFailingEntry() {
        val ledger = HeatOverrideLedger()
        val order = ArrayList<String>()
        ledger.changed("a") { order += "a" }
        ledger.changed("b") { error("synthetic") }
        ledger.changed("a") { order += "second undo of a must be ignored" }
        ledger.changed("c") { order += "c" }
        assertEquals(listOf("a", "b", "c"), ledger.pending())
        val steps = ledger.restoreAll()
        assertEquals(listOf("c", "a"), order)
        assertEquals(listOf("c" to true, "b" to false, "a" to true), steps.map { it.what to it.ok })
        assertEquals("synthetic", steps[1].detail)
        assertTrue("Restoring twice does nothing the second time", ledger.restoreAll().isEmpty())
        assertNull(steps[0].detail)
    }
}
