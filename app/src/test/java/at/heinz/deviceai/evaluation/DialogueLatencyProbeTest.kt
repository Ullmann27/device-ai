package at.heinz.deviceai.evaluation

import at.heinz.deviceai.localmodel.LocalDialogueReuse
import at.heinz.deviceai.localmodel.LocalDialogueRequest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The dialogue latency probe with stand-in generations: what it sends, what it reports, when it stops. HOST. */
class DialogueLatencyProbeTest {
    private val anchor = "Referenzzeit laut Geräteuhr: 2026-11-18 (Mittwoch) 10:00.\n"

    private class Fixture(var thermal: Int? = 0) {
        val freshPrompts = mutableListOf<String>()
        val keptRequests = mutableListOf<LocalDialogueRequest>()
        var released = 0
        var cancelAfter = Int.MAX_VALUE
        var failAt = -1
        var blankAt = -1
        var keeps = true
        private var calls = 0
        private fun answer(index: Int) = "Antwort Nummer $index zum Probedialog."

        fun fresh(prompt: String): DialogueLatencyProbe.Generation {
            freshPrompts += prompt
            return generation(1_000)
        }

        fun kept(request: LocalDialogueRequest): DialogueLatencyProbe.Generation {
            keptRequests += request
            val reuse = keeps && request.followUp != null && keptRequests.size > 1
            request.receipt.dispatched(reuse, if (reuse || request.followUp == null) null
                else LocalDialogueReuse.RUNTIME_NO_KEPT_CONVERSATION, if (reuse) 900 + keptRequests.size else null)
            if (keeps) request.receipt.retained(1_000 + keptRequests.size)
            return generation(if (reuse) 200 else 1_000)
        }

        private fun generation(first: Long): DialogueLatencyProbe.Generation {
            calls++
            if (calls == blankAt) return DialogueLatencyProbe.Generation("", true, null, null, 900)
            return if (calls == failAt) DialogueLatencyProbe.Generation("", false, "native failure", null, 5)
                else DialogueLatencyProbe.Generation(answer(calls), true, null, first, first + 300)
        }

        fun runner(maxPromptBytes: Int = 8_448) = DialogueLatencyProbe.Runner(
            fresh = ::fresh, kept = ::kept, release = { released++ },
            sample = { ModelEvalDeviceSample(thermal, 80, 4_000_000_000L, 1_000_000L) },
            cancelled = { calls >= cancelAfter }, clockAnchor = { "Referenzzeit laut Geräteuhr: 2026-11-18 (Mittwoch) 10:00.\n" },
            maxPromptBytes = maxPromptBytes, newKey = { "probe-key" })
    }

    @Test fun bothPassesRunTheSameSixTurnsAndReportWhatWasReallySent() {
        val fixture = Fixture()
        val report = fixture.runner().run(JSONObject().put("modelId", "gemma4-e4b-it"))
        assertEquals(DialogueLatencyProbe.SCHEMA, report.getString("schema"))
        assertEquals("gemma4-e4b-it", report.getJSONObject("meta").getString("modelId"))
        assertTrue(report.getBoolean("complete"))
        assertTrue(report.getBoolean("syntheticDialogue"))
        assertEquals(1, fixture.released)
        val passes = report.getJSONArray("passes")
        assertEquals(DialogueLatencyProbe.MODE_FRESH, passes.getJSONObject(0).getString("mode"))
        assertEquals(DialogueLatencyProbe.MODE_KEPT, passes.getJSONObject(1).getString("mode"))

        // Without keeping: six complete prompts, each with the whole dialogue so far.
        assertEquals(6, fixture.freshPrompts.size)
        fixture.freshPrompts.forEachIndexed { index, prompt ->
            assertTrue(prompt.startsWith(DialogueLatencyProbe.INSTRUCTIONS))
            assertTrue(prompt.contains("Aktueller Auftrag:\n" + DialogueLatencyProbe.TURNS[index] + "\n"))
            (0 until index).forEach { assertTrue(prompt.contains("Nutzer: " + DialogueLatencyProbe.TURNS[it] + "\n")) }
        }
        val fresh = passes.getJSONObject(0)
        assertEquals(0, fresh.getInt("reusedTurns"))
        assertEquals(6 * 1_300L, fresh.getLong("totalMsSum"))
        assertTrue(fresh.getJSONArray("turns").getJSONObject(3).isNull("nativeTokensBefore"))

        // With keeping: one key, turn 1 without follow-up, then only the new question; the full prompt is the fallback.
        assertEquals(6, fixture.keptRequests.size)
        assertTrue(fixture.keptRequests.all { it.conversationKey == "probe-key" })
        assertNull(fixture.keptRequests[0].followUp)
        fixture.keptRequests.drop(1).forEachIndexed { offset, request ->
            val index = offset + 1
            val followUp = request.followUp!!
            assertTrue(followUp.contains(DialogueLatencyProbe.TURNS[index]))
            (0 until index).forEach { assertFalse(followUp.contains(DialogueLatencyProbe.TURNS[it])) }
            assertFalse(followUp.contains("Antwort Nummer"))
            assertTrue(request.freshPrompt.contains("Nutzer: " + DialogueLatencyProbe.TURNS[index - 1] + "\n"))
        }
        val kept = passes.getJSONObject(1)
        assertEquals(5, kept.getInt("reusedTurns"))
        val turns = kept.getJSONArray("turns")
        assertFalse(turns.getJSONObject(0).getBoolean("reused"))
        assertEquals(fixture.keptRequests[0].freshPrompt.toByteArray(Charsets.UTF_8).size, turns.getJSONObject(0).getInt("sentBytes"))
        val third = turns.getJSONObject(2)
        assertTrue(third.getBoolean("reused"))
        assertEquals(fixture.keptRequests[2].followUp!!.toByteArray(Charsets.UTF_8).size, third.getInt("sentBytes"))
        assertTrue(third.getInt("sentBytes") < third.getInt("fullPromptBytes"))
        assertEquals(903, third.getInt("nativeTokensBefore"))
        assertEquals(1_003, third.getInt("nativeTokensAfter"))
        assertEquals(200L, third.getLong("firstTextMs"))
        assertEquals(500L, third.getLong("totalMs"))
        assertEquals(0, third.getInt("thermalBefore"))
        assertEquals(1_000 + 5 * 200L, kept.getLong("firstTextMsSum"))

        val summary = DialogueLatencyProbe.summary(report)
        assertTrue(summary, summary.contains("Ohne (neues Gespräch je Zug, bisheriges Verhalten) · 6 Züge"))
        assertTrue(summary, summary.contains("Mit behaltenem Gespräch · 6 Züge · davon weiterverwendet: 5"))
        assertTrue(summary, summary.contains("Erster Text je Zug (ms): 1000 / 200 / 200 / 200 / 200 / 200"))
        assertFalse("no conclusion the run did not measure", summary.contains("schneller"))
    }

    @Test fun aRuntimeThatKeepsNothingIsReportedTruthfully() {
        val fixture = Fixture().apply { keeps = false }
        val kept = fixture.runner().run(JSONObject()).getJSONArray("passes").getJSONObject(1)
        assertEquals(0, kept.getInt("reusedTurns"))
        val second = kept.getJSONArray("turns").getJSONObject(1)
        assertFalse(second.getBoolean("reused"))
        assertEquals(LocalDialogueReuse.RUNTIME_NO_KEPT_CONVERSATION, second.getString("reuseReason"))
        assertEquals(second.getInt("fullPromptBytes"), second.getInt("sentBytes"))
    }

    @Test fun heatStopAndFailureEndTheProbeWithoutRetry() {
        val hot = Fixture(thermal = 3)
        val heat = hot.runner().run(JSONObject())
        assertFalse(heat.getBoolean("complete"))
        assertEquals("THERMAL", heat.getString("stopReason"))
        assertEquals(1, heat.getJSONArray("passes").length())
        assertTrue(hot.freshPrompts.isEmpty())
        assertTrue(DialogueLatencyProbe.summary(heat).contains("Die Probe ist unvollständig (THERMAL)"))

        val stopped = Fixture().apply { cancelAfter = 2 }
        val cancel = stopped.runner().run(JSONObject())
        assertEquals("CANCELLED", cancel.getString("stopReason"))
        assertEquals(2, cancel.getJSONArray("passes").getJSONObject(0).getInt("turnsRun"))
        assertTrue(stopped.keptRequests.isEmpty())

        val broken = Fixture().apply { failAt = 8 }
        val failure = broken.runner().run(JSONObject())
        assertEquals("TURN_FAILED", failure.getString("stopReason"))
        val kept = failure.getJSONArray("passes").getJSONObject(1)
        assertEquals(2, kept.getInt("turnsRun"))
        assertEquals("native failure", kept.getJSONArray("turns").getJSONObject(1).getString("failure"))
        assertTrue(kept.isNull("firstTextMsSum"))
        assertEquals("a kept conversation is released even after a failed pass", 1, broken.released)
    }

    @Test fun aCompletedButEmptyAnswerIsReportedAndTheDialogueGoesOn() {
        val fixture = Fixture().apply { blankAt = 3 }
        val report = fixture.runner().run(JSONObject())
        assertTrue(report.getBoolean("complete"))
        val fresh = report.getJSONArray("passes").getJSONObject(0)
        assertEquals(6, fresh.getInt("turnsRun"))
        assertEquals(1, fresh.getInt("emptyAnswers"))
        assertEquals(0, fresh.getJSONArray("turns").getJSONObject(2).getInt("responseCharacters"))
        assertTrue("without a first text the sum is not invented", fresh.isNull("firstTextMsSum"))
        assertTrue(fixture.freshPrompts[3].contains("Modellantwort (unbestätigt): (leere Antwort)\n"))
        assertEquals(0, report.getJSONArray("passes").getJSONObject(1).getInt("emptyAnswers"))
        assertTrue(DialogueLatencyProbe.summary(report).contains("Leere Antworten des Modells: 1"))
    }

    @Test fun aPromptThatWouldExceedTheBudgetDropsOldAnswersBeforeOwnerStatements() {
        val history = listOf("Erste Frage?" to "A".repeat(3_000), "Zweite Frage?" to "B".repeat(3_000))
        val all = DialogueLatencyProbe.freshPrompt(history, "Dritte Frage?", anchor, 16_000)!!
        assertTrue(all.contains("A".repeat(3_000)) && all.contains("B".repeat(3_000)))
        val tight = DialogueLatencyProbe.freshPrompt(history, "Dritte Frage?", anchor, 6_000)!!
        assertTrue(tight.toByteArray(Charsets.UTF_8).size <= 6_000)
        assertFalse(tight.contains("A".repeat(3_000)))
        assertTrue(tight.contains("B".repeat(3_000)))
        assertTrue(tight.contains("Nutzer: Erste Frage?\nModell: (Antwort nicht im Kontext)\n"))
        assertNull(DialogueLatencyProbe.freshPrompt(history, "Dritte Frage?", anchor, 500))
        val size = DialogueLatencyProbe.INSTRUCTIONS.toByteArray(Charsets.UTF_8).size
        assertTrue("stand-in instructions are about the size of the app's compiled context: $size", size in 1_600..2_400)
    }

    @Test fun theEvaluationReportNamesTheSwitchAndThatItsCasesAreIndependent() {
        val meta = ModelEvalRuntimeMeta.putDialogueReuse(JSONObject(), switchOn = true)
        assertTrue(meta.getBoolean("dialogueReuseSwitch"))
        assertTrue(meta.getBoolean("casesIndependent"))
        assertFalse(meta.getBoolean("dialogueReuseAppliedToCases"))
        assertFalse(ModelEvalRuntimeMeta.putDialogueReuse(JSONObject(), switchOn = false).getBoolean("dialogueReuseSwitch"))
    }
}
