package at.heinz.deviceai.evaluation

import at.heinz.deviceai.ContinuumWorkerChatRouter
import at.heinz.deviceai.GroqInvocationRouter
import at.heinz.deviceai.cognitive.CertifiedLocationReflex
import at.heinz.deviceai.cognitive.FreshKnowledgeNeed
import at.heinz.deviceai.cognitive.LocalTextOnlyInferenceRoute
import at.heinz.deviceai.cognitive.OwnerAppLookupIntent
import at.heinz.deviceai.cognitive.OwnerChatIntentRouter
import at.heinz.deviceai.cognitive.OwnerComputerIntentParser
import at.heinz.deviceai.cognitive.OwnerCorrectionIntent
import at.heinz.deviceai.cognitive.OwnerDeviceClockReply
import at.heinz.deviceai.cognitive.OwnerDocumentIntent
import at.heinz.deviceai.cognitive.OwnerInternalNavigationIntent
import at.heinz.deviceai.cognitive.OwnerResearchCommand
import at.heinz.deviceai.cognitive.PublicKnowledgeTopicRouter
import at.heinz.deviceai.cognitive.WorkDecisionRecall
import at.heinz.deviceai.cognitive.WorkRecall
import at.heinz.deviceai.learning.ContinuumBrainCommand
import at.heinz.deviceai.learning.MentorAssociativeRecall
import at.heinz.deviceai.learning.MentorRecall
import at.heinz.deviceai.learning.MentorSemanticRecall
import at.heinz.deviceai.learning.NeuralDialogue
import at.heinz.deviceai.routines.OwnerRoutineCommands
import at.heinz.deviceai.sending.OwnerSendIntentParser
import at.heinz.deviceai.systemintelligence.SystemIntelligenceCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** What the heat measurement runs and in which order, its fixed orders and its chat command. HOST, no device value. */
class HeatMeasurementPlanTest {
    private fun file(path: String) = File(path).takeIf(File::isFile) ?: File("../$path")
    private val prompts = HeatPromptSet.parse(file("app/src/main/assets/${HeatMeasurement.PROMPT_ASSET}").readText(Charsets.UTF_8))

    @Test fun everyConfigurationDiffersFromTheBaselineInExactlyOneFactor() {
        val baseline = HeatMeasurement.BASELINE
        assertEquals("A", baseline.id)
        assertNull(baseline.factor)
        assertEquals(HeatStageMode.RESTING, baseline.stage)
        assertFalse("The baseline is the default: the dialogue is not kept in the model", baseline.dialogueReuse)
        assertEquals(HeatModelChoice.BASELINE, baseline.model)
        val others = HeatMeasurement.CONFIGURATIONS.drop(1)
        assertEquals(listOf("B", "C", "D", "E", "F"), others.map { it.id })
        others.forEach { assertEquals(it.id + " changes one thing", 1, it.differences(baseline)) }
        assertEquals(listOf(HeatMeasurement.FACTOR_STAGE_OFF, HeatMeasurement.FACTOR_STAGE_FULL_EFFECTS,
            HeatMeasurement.FACTOR_DIALOGUE_REUSE, HeatMeasurement.FACTOR_MODEL_E2B, HeatMeasurement.FACTOR_BACKEND_GPU),
            others.map { it.factor })
        assertEquals(HeatStageMode.OFF, others[0].stage)
        assertEquals(HeatStageMode.FULL_EFFECTS, others[1].stage)
        assertTrue(others[2].dialogueReuse)
        assertEquals(HeatModelChoice.GEMMA_E2B, others[3].model)
        assertEquals(HeatBackendChoice.GPU, others[4].backend)
    }

    @Test fun aPlanRefusesAConfigurationThatChangesTwoThings() {
        val two = HeatMeasurement.CONFIGURATIONS[1].copy(id = "X", dialogueReuse = true)
        val failure = runCatching { HeatPlan(HeatSize.KURZ, prompts, listOf(HeatMeasurement.BASELINE, two), HeatLimits()) }
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun theShortSizeRunsTheFirstOrderOncePerConfiguration() {
        val plan = HeatMeasurement.plan(HeatSize.KURZ, prompts)
        assertEquals(listOf("A", "B", "C", "D", "E", "F"), plan.runs.map { it.configuration.id })
        assertTrue(plan.runs.all { it.prompt.id == "kurz" && it.repetition == 1 })
        assertEquals(12, plan.turnCount)
    }

    @Test fun theFullSizeAlternatesTheConfigurationsAndKeepsAModelSwapInOneBlock() {
        val plan = HeatMeasurement.plan(HeatSize.VOLL, prompts)
        assertEquals(6 * 3 * 2, plan.runs.size)
        val rounds = plan.runs.take(4 * 3 * 2)
        assertEquals("A B C D alternate, order by order and repetition by repetition",
            List(6) { listOf("A", "B", "C", "D") }.flatten(), rounds.map { it.configuration.id })
        assertEquals(listOf("kurz", "mittel", "lang", "kurz", "mittel", "lang"), rounds.chunked(4).map { it.first().prompt.id })
        assertEquals(listOf(1, 1, 1, 2, 2, 2), rounds.chunked(4).map { it.first().repetition })
        val blocks = plan.runs.drop(rounds.size)
        assertEquals(List(6) { "E" } + List(6) { "F" }, blocks.map { it.configuration.id })
        assertTrue("A model swap happens once in and once out", blocks.take(6).all { it.configuration.swapsModel })
    }

    @Test fun theExpectedDurationUsesOnlyAnswerTimesOfThisDeviceAndSaysWhenThereAreNone() {
        val plan = HeatMeasurement.plan(HeatSize.KURZ, prompts)
        assertNull("No answer time measured in this session: no expectation", plan.expectedMs(emptyList()))
        val unknown = plan.announcement(emptyList())
        assertTrue(unknown, "Erwartete Dauer: unbekannt" in unknown)
        assertTrue(unknown, "Obergrenze durch die Zeitlimits" in unknown)
        // Synthetic session values, only to check the arithmetic.
        val recent = listOf(10_000L, 30_000L, 20_000L)
        assertEquals(plan.fixedWaitMs + 20_000L * plan.turnCount, plan.expectedMs(recent))
        val known = plan.announcement(recent)
        assertTrue(known, "gerechnet mit dem Median deiner letzten 3 Antwortzeiten" in known)
        assertTrue(known, "6 Konfigurationen, 6 Läufe, 12 Modellantworten" in known)
        assertTrue("The owner is told that he can stop", "STOPP beendet die Messung" in known)
        assertTrue(plan.upperBoundMs > plan.fixedWaitMs)
    }

    @Test fun theShippedOrdersAreTheVersionedSetAndEveryOrderIsAFirstQuestionWithAFollowUp() {
        val source = file("docs/evaluation/continuum-heat-v1/prompts.json")
        val shipped = file("app/src/main/assets/${HeatMeasurement.PROMPT_ASSET}")
        assertTrue("The app ships exactly the versioned orders", source.readBytes().contentEquals(shipped.readBytes()))
        assertEquals("continuum-heat-v1", prompts.id)
        assertEquals(1, prompts.version)
        assertEquals(listOf("kurz", "mittel", "lang"), prompts.prompts.map { it.id })
        prompts.prompts.forEach { assertEquals(it.id, 2, it.turns.size) }
    }

    /**
     * The fixed orders must reach the model. Every deterministic route of the send path that can be asked on the
     * host is asked here; routes that depend on the phone's state are caught at run time (outcome NO_MODEL_CALL).
     */
    @Test fun theFixedOrdersAreNotTakenByTheDeterministicRoutesThatCanBeCheckedOnTheHost() {
        for (text in prompts.prompts.flatMap { it.turns }) {
            assertNull(text, HeatMeasurementCommand.parse(text))
            assertTrue("$text must go to the model as a plain text question without tool schemas",
                LocalTextOnlyInferenceRoute.useToolFree(text, false, false, false, false))
            assertNull(text, OwnerChatIntentRouter.route(text, false))
            assertNull(text, OwnerCorrectionIntent.parse(text))
            assertFalse(text, OwnerCorrectionIntent.interruptsWork(text))
            assertNull(text, OwnerRoutineCommands.parse(text))
            assertFalse(text, SystemIntelligenceCommands.isDiagnosisRequest(text))
            assertFalse(text, SystemIntelligenceCommands.isCachePreviewRequest(text))
            assertNull(text, OwnerDeviceClockReply.answer(text, Instant.parse("2026-11-18T10:00:00Z"), ZoneId.of("Europe/Vienna")))
            assertNull(text, WorkDecisionRecall.parse(text))
            assertNull(text, WorkRecall.parse(text))
            assertNull(text, OwnerDocumentIntent.parse(text, null))
            assertNull(text, ContinuumBrainCommand.parse(text))
            assertNull(text, GroqInvocationRouter.extractQuestion(text))
            assertNull(text, ContinuumWorkerChatRouter.parse(text))
            assertNull(text, OwnerInternalNavigationIntent.resolve(text, null))
            assertNull(text, OwnerResearchCommand.parse(text, null))
            assertNull(text, OwnerAppLookupIntent.inspect(text, null))
            assertNull(text, OwnerSendIntentParser.parse(text, null))
            assertEquals(text, OwnerComputerIntentParser.Result.NotComputerCommand, OwnerComputerIntentParser.parse(text))
            assertFalse(text, NeuralDialogue.accepts(text))
            assertNull(text, MentorSemanticRecall.topic(text))
            assertNull(text, MentorAssociativeRecall.topic(text))
            assertNull(text, MentorRecall.topic(text))
            assertNull(text, CertifiedLocationReflex.routeExplicit(text))
            assertFalse("$text must not need current web knowledge", FreshKnowledgeNeed.needsCurrentWeb(text))
            assertTrue("$text must stay a local answer even with internet always on",
                PublicKnowledgeTopicRouter.topic(text) == null || FreshKnowledgeNeed.prefersLocalExplanation(text))
        }
    }

    @Test fun theChatCommandIsTheWholeSentenceOrNothing() {
        assertEquals(HeatCommand.Start(null), HeatMeasurementCommand.parse("Continuum Wärmemessung"))
        assertEquals(HeatCommand.Start(HeatSize.KURZ), HeatMeasurementCommand.parse("Continuum Wärmemessung kurz"))
        assertEquals(HeatCommand.Start(HeatSize.KURZ), HeatMeasurementCommand.parse("  continuum wärmemessung KURZ. "))
        assertEquals(HeatCommand.Start(HeatSize.VOLL), HeatMeasurementCommand.parse("Continuum Wärmemessung voll"))
        assertEquals(HeatCommand.Start(HeatSize.VOLL), HeatMeasurementCommand.parse("Wärme- und Zeitmessung vollständig"))
        assertEquals(HeatCommand.Start(null), HeatMeasurementCommand.parse("Starte die Wärmemessung"))
        assertEquals("As the speech recogniser may write it", HeatCommand.Start(HeatSize.KURZ),
            HeatMeasurementCommand.parse("Continuum Wärme Messung kurz"))
        assertEquals(HeatCommand.Start(null), HeatMeasurementCommand.parse("Continuum Waermemessung"))
        assertEquals(HeatCommand.Stop, HeatMeasurementCommand.parse("Wärmemessung stoppen"))
        assertEquals(HeatCommand.Stop, HeatMeasurementCommand.parse("Continuum Wärmemessung abbrechen"))
        assertEquals(HeatCommand.SavePartial, HeatMeasurementCommand.parse("Continuum Wärmemessung Teilbericht"))
        assertEquals(HeatCommand.SavePartial, HeatMeasurementCommand.parse("Wärmemessung Teilbericht sichern."))
        for (other in listOf("Wie funktioniert eine Wärmemessung?", "Continuum Wärmemessung kurz und dann das Wetter",
            "Was ist Wärme?", "Continuum Modelltest", "Stopp", "")) assertNull(other, HeatMeasurementCommand.parse(other))
    }

    @Test fun aBareStopEndsOnlyARunningMeasurement() {
        for (stop in listOf("Stopp", "stop!", "Abbrechen", "Wärmemessung stoppen", "Messung abbrechen"))
            assertTrue(stop, HeatMeasurementCommand.stopsRunningMeasurement(stop))
        for (other in listOf("Stopp die Musik", "Erklär mir den Stoppschalter", "weiter"))
            assertFalse(other, HeatMeasurementCommand.stopsRunningMeasurement(other))
        assertNotNull(HeatMeasurement.INPUT_NOTE)
        assertEquals("Eingabe: identischer Text an der Sprachübergabe, kein Mikrofon", HeatMeasurement.INPUT_NOTE)
    }
}
