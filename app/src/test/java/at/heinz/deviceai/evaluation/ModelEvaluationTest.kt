package at.heinz.deviceai.evaluation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Code64: on-device model evaluation – scoring, report arithmetic and the shipped case files. */
class ModelEvaluationTest {
    private fun case(criterion: String, vararg values: String, turns: List<Pair<String, String>> = listOf("user" to "Frage?")) =
        ModelEvalCase.parse(JSONObject().put("id", "T-1").put("category", "math")
            .put("turns", org.json.JSONArray(turns.map { JSONObject().put("role", it.first).put("text", it.second) }))
            .put("expect", JSONObject().put("type", criterion).put("values", org.json.JSONArray(values.toList()))
                .put("tool", JSONObject.NULL).put("forbidden_tools", org.json.JSONArray())).toString())

    private fun verdict(criterion: String, answer: String, vararg values: String) =
        ModelEvalScoring.score(case(criterion, *values), answer)

    @Test fun contentCriteriaAreFormalAndAcceptBothNumberSpellings() {
        assertEquals(ModelEvalVerdict.PASS, verdict("contains_all", "Fünf Böden aus Buche, 80 cm breit.", "fünf", "Buche", "80").verdict)
        assertEquals(ModelEvalVerdict.PASS, verdict("contains_all", "Es sind 5 Böden aus BUCHE.", "fünf", "Buche").verdict)
        assertEquals(ModelEvalVerdict.PASS, verdict("contains_all", "Um vierzehn Gäste.", "14").verdict)
        val missing = verdict("contains_all", "Fünf Böden.", "fünf", "Buche")
        assertEquals(ModelEvalVerdict.FAIL, missing.verdict)
        assertTrue(missing.reason.contains("Buche"))
        assertFalse(missing.heuristic)
        assertEquals(ModelEvalVerdict.PASS, verdict("contains_any", "Das ist erst ab Jänner so.", "noch nicht", "ab Jänner").verdict)
        assertEquals(ModelEvalVerdict.FAIL, verdict("contains_any", "Ja.", "noch nicht", "ab Jänner").verdict)
        assertEquals(ModelEvalVerdict.FAIL, verdict("contains_all", "   ", "x").verdict)
    }

    @Test fun numbersAreReadInGermanAndPlainNotation() {
        assertEquals(ModelEvalVerdict.PASS, verdict("numeric", "17 mal 23 ist 391, minus 41 ergibt 350.", "350").verdict)
        assertEquals(ModelEvalVerdict.PASS, verdict("numeric", "Die Fläche beträgt 90,0 cm².", "90").verdict)
        assertEquals(ModelEvalVerdict.PASS, verdict("numeric", "Das sind 1.250,50 Euro.", "1250.5").verdict)
        assertEquals(ModelEvalVerdict.FAIL, verdict("numeric", "Ungefähr 35.", "350").verdict)
        assertEquals(listOf(1250.5, 3.0), ModelEvalScoring.numbers("1.250,50 und 3"))
        assertEquals(listOf(1250.5), ModelEvalScoring.numbers("1250.5"))
        // A digit inside a word or an identifier is not a number of the answer.
        assertTrue(ModelEvalScoring.numbers("Argon4 und W482").isEmpty())
    }

    @Test fun abstentionAndAskBackAreMarkedAsHeuristic() {
        val abstain = verdict("abstain", "Das weiß ich nicht, du hast mir den Namen nicht genannt.")
        assertEquals(ModelEvalVerdict.PASS, abstain.verdict)
        assertTrue(abstain.heuristic)
        assertEquals(ModelEvalVerdict.FAIL, verdict("abstain", "Dein erster Hund hieß Bello.").verdict)
        val asks = verdict("asks_back", "Welche Datei meinst du genau?")
        assertEquals(ModelEvalVerdict.PASS, asks.verdict)
        assertTrue(asks.heuristic)
        assertEquals(ModelEvalVerdict.FAIL, verdict("asks_back", "Ich lösche die Datei.").verdict)
        // A statement that merely ends with a question mark is not an ask-back.
        assertEquals(ModelEvalVerdict.FAIL, verdict("asks_back", "Erledigt?").verdict)
    }

    @Test fun toolChoiceIsNeverScoredInTheRawModelRun() {
        assertEquals(ModelEvalVerdict.NOT_SCORED, verdict("tool_call", "Ich öffne die Downloads.").verdict)
        assertEquals(ModelEvalVerdict.NOT_SCORED, verdict("no_tool", "Ein Primfaktor ist …").verdict)
    }

    @Test fun thePromptCarriesTheWholeDialogueOrNothing() {
        val dialogue = case("contains_all", "blau", turns = listOf("user" to "Meine Kiste ist rot.", "assistant" to "Notiert.",
            "user" to "Nein, blau.", "assistant" to "Korrigiert.", "user" to "Welche Farbe?"))
        val prompt = ModelEvalScoring.prompt(dialogue)!!
        assertTrue(prompt.indexOf("Nutzer: Meine Kiste ist rot.") < prompt.indexOf("Nutzer: Nein, blau."))
        assertTrue(prompt.endsWith("Nutzer: Welche Farbe?\nAssistent:"))
        assertNull("too long: not truncated, not sent", ModelEvalScoring.prompt(dialogue, maxBytes = 120))
        assertNull(ModelEvalScoring.prompt(case("abstain", turns = listOf("assistant" to "Hallo"))))
    }

    @Test fun theReportContainsOnlyObservedValues() {
        val cases = listOf(case("numeric", "4"), case("abstain"), case("tool_call"), case("numeric", "9"))
        val answers = ArrayDeque(listOf(
            ModelEvalGeneration("Das sind 4.", true, false, null, 300, 1_300, 5, inputTokens = 120, outputTokens = 10),
            ModelEvalGeneration("Das weiß ich nicht.", true, false, null, 500, 2_500, 6),
            ModelEvalGeneration("egal", true, false, null, 100, 400, 2),
            ModelEvalGeneration("Es sind 8 und dann", false, true, null, 700, 9_700, 40)))
        // Warm, never critical: 0, 1, 2, 1, 0, 1, 2, …
        var samples = 0
        val report = ModelEvalRunner(generate = { answers.removeFirst() },
            sample = { ModelEvalDeviceSample(listOf(0, 1, 2, 1)[samples++ % 4], 80, 5_000_000_000L, 1_000L) },
            cancelled = { false }).run("dev", cases, JSONObject().put("model", "test"))
        assertEquals(4, report.getInt("casesRun"))
        assertEquals(1, report.getJSONObject("formal").getInt("pass"))
        assertEquals(1, report.getJSONObject("formal").getInt("fail"))
        assertEquals(1, report.getJSONObject("heuristic").getInt("pass"))
        assertEquals(1, report.getInt("notScored"))
        assertEquals(1, report.getInt("outputLimitReached"))
        assertEquals(1_300L, report.getJSONObject("latencyMs").getLong("totalP50"))
        assertEquals(9_700L, report.getJSONObject("latencyMs").getLong("totalP95"))
        // 10 tokens in the 1 000 ms after the first text; the other generations reported no token counts.
        assertEquals(10.0, report.getDouble("tokensPerSecondMean"), 0.001)
        assertEquals(120, report.getInt("inputTokensMax"))
        assertEquals(2, report.getJSONObject("thermalStatus").getInt("max"))
        assertEquals(0, report.getJSONObject("thermalStatus").getInt("min"))
        assertTrue(report.isNull("stoppedEarly"))
        assertEquals("RAW_MODEL_DIALOGUE_IN_PROMPT_NO_TOOLS_NO_MEMORY", report.getString("scope"))
        assertTrue(ModelEvalRunner.summary(report).contains("Formal bewertet: 1 bestanden, 1 nicht bestanden"))
    }

    @Test fun theReportNamesTheBackendThatReallyRanAndNeverGuessesMissingRuntimeFacts() {
        val one = listOf(case("numeric", "4"))
        fun report(meta: JSONObject) = ModelEvalRunner(
            generate = { ModelEvalGeneration("Das sind 4.", true, false, null, 400, 2_400, 5) },
            sample = { ModelEvalDeviceSample(0, 80, 5_000_000_000L, 1_000L) }, cancelled = { false }).run("dev", one, meta)

        // CPU engine with the cool two-thread budget and the 4K window.
        val cpu = report(ModelEvalRuntimeMeta.put(JSONObject().put("modelId", "gemma4-e4b-it"),
            backend = "CPU", requestedBackend = "CPU", backendNotice = null, contextTokens = 4_096, computeThreads = 2))
        assertEquals("CPU", cpu.getJSONObject("meta").getString("backend"))
        assertEquals(4_096, cpu.getJSONObject("meta").getInt("contextTokens"))
        assertEquals(2, cpu.getJSONObject("meta").getInt("computeThreads"))
        assertTrue(cpu.getJSONObject("meta").isNull("backendNotice"))
        assertEquals("gemma4-e4b-it", cpu.getJSONObject("meta").getString("modelId"))
        assertTrue(ModelEvalRunner.summary(cpu).contains("Laufzeit: Backend CPU · Kontextfenster 4096 Token · CPU-Threads 2"))

        // GPU was requested but the engine fell back: the report says what ran, what was asked for and why.
        val fallback = report(ModelEvalRuntimeMeta.put(JSONObject(), backend = "CPU", requestedBackend = "GPU",
            backendNotice = "GPU-Start fehlgeschlagen. " + "x".repeat(400), contextTokens = 4_096, computeThreads = 2))
        assertEquals("GPU", fallback.getJSONObject("meta").getString("backendRequested"))
        assertEquals(300, fallback.getJSONObject("meta").getString("backendNotice").length)
        assertTrue(ModelEvalRunner.summary(fallback).contains("Backend CPU (angefordert: GPU)"))

        // GPU engine: the CPU thread budget does not apply and is reported as unknown, not as a number.
        val gpu = report(ModelEvalRuntimeMeta.put(JSONObject(), backend = "GPU", requestedBackend = "GPU",
            backendNotice = null, contextTokens = 4_096, computeThreads = null))
        assertTrue(gpu.getJSONObject("meta").isNull("computeThreads"))
        assertTrue(ModelEvalRunner.summary(gpu).contains("Laufzeit: Backend GPU · Kontextfenster 4096 Token · CPU-Threads –"))

        // A runtime that reports nothing (or an older report without these fields) stays "unknown".
        val unknown = report(ModelEvalRuntimeMeta.put(JSONObject(), null, null, null, null, null))
        assertTrue(unknown.getJSONObject("meta").isNull("backend"))
        assertTrue(unknown.getJSONObject("meta").isNull("contextTokens"))
        assertTrue(ModelEvalRunner.summary(unknown).contains("Laufzeit: Backend – · Kontextfenster – Token · CPU-Threads –"))
        assertTrue(ModelEvalRunner.summary(report(JSONObject())).contains("Laufzeit: Backend –"))
        assertEquals("Laufzeit: Backend – · Kontextfenster – Token · CPU-Threads –", ModelEvalRuntimeMeta.summaryLine(null))
    }

    @Test fun theSummaryShowsCharactersPerSecondBecauseTokenCountsMayBeMissing() {
        // 26 characters in the 2 000 ms after the first text; the runtime reported no token counts.
        val report = ModelEvalRunner(
            generate = { ModelEvalGeneration("Das sind zusammen genau 4.", true, false, null, 400, 2_400, 5) },
            sample = { ModelEvalDeviceSample(0, 80, 5_000_000_000L, 1_000L) }, cancelled = { false })
            .run("dev", listOf(case("numeric", "4")), JSONObject())
        assertTrue(report.isNull("tokensPerSecondMean"))
        assertEquals(13.0, report.getDouble("charactersPerSecondMean"), 0.001)
        val summary = ModelEvalRunner.summary(report)
        assertTrue(summary.contains("Tokens pro Sekunde (Mittel): –"))
        assertTrue(summary.contains("Zeichen pro Sekunde nach dem ersten Text (Mittel): 13.0"))
    }

    @Test fun cancellationAndCriticalHeatStopTheRunAndAreReported() {
        val cases = List(5) { case("numeric", "1") }
        var calls = 0
        val cancelled = ModelEvalRunner(generate = { calls++; ModelEvalGeneration("1", true, false, null, 1, 2, 1) },
            sample = { ModelEvalDeviceSample(0, null, null, 1L) }, cancelled = { calls >= 2 })
            .run("dev", cases, JSONObject())
        assertEquals("CANCELLED", cancelled.getString("stoppedEarly"))
        // The case that was running when the stop arrived is not counted.
        assertEquals(1, cancelled.getInt("casesRun"))
        assertEquals(1, cancelled.getJSONObject("formal").getInt("pass"))

        val hot = ModelEvalRunner(generate = { ModelEvalGeneration("1", true, false, null, 1, 2, 1) },
            sample = { ModelEvalDeviceSample(ModelEvalRunner.THERMAL_CRITICAL, null, null, 1L) }, cancelled = { false })
            .run("dev", cases, JSONObject())
        assertEquals("THERMAL_CRITICAL", hot.getString("stoppedEarly"))
        assertEquals(0, hot.getInt("casesRun"))
        assertTrue(hot.getJSONObject("latencyMs").isNull("totalP50"))
    }

    @Test fun aFailedGenerationIsAFailureNotAMissingMeasurement() {
        val report = ModelEvalRunner(generate = { ModelEvalGeneration("", false, false, "LOCAL_RUNTIME", null, 50, 0) },
            sample = { ModelEvalDeviceSample(null, null, null, 1L) }, cancelled = { false })
            .run("dev", listOf(case("numeric", "1")), JSONObject())
        assertEquals(1, report.getJSONObject("formal").getInt("fail"))
        assertTrue(report.getJSONObject("latencyMs").isNull("totalP50"))
        assertTrue(report.getJSONObject("thermalStatus").isNull("max"))
        assertTrue("no token count reported: no rate is invented", report.isNull("tokensPerSecondMean"))
    }

    @Test fun percentilesUseTheNearestRank() {
        assertNull(ModelEvalRunner.percentile(emptyList(), 50))
        assertEquals(3L, ModelEvalRunner.percentile(listOf(5, 1, 3, 2, 4), 50))
        assertEquals(5L, ModelEvalRunner.percentile(listOf(5, 1, 3, 2, 4), 95))
        assertEquals(7L, ModelEvalRunner.percentile(listOf(7), 95))
    }

    @Test fun theShippedCaseFilesAreByteIdenticalToTheEvaluationSetAndParse() {
        fun file(path: String) = File(path).takeIf(File::isFile) ?: File("../$path")
        for (name in listOf("model-dev.jsonl", "model-holdout.jsonl")) {
            val source = file("docs/evaluation/continuum-eval-v1/$name")
            val shipped = file("app/src/main/assets/continuum-eval-v1/$name")
            assertTrue("$name: the app must ship exactly the evaluation set", source.readBytes().contentEquals(shipped.readBytes()))
            val cases = ModelEvalCase.parseAll(shipped.readLines(Charsets.UTF_8).asSequence())
            assertEquals(34, cases.size)
            cases.forEach { assertTrue(it.id, ModelEvalScoring.prompt(it) != null) }
        }
    }
}
