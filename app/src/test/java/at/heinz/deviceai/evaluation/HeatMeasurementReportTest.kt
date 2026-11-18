package at.heinz.deviceai.evaluation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Statistics, summary, comparability, cost driver and the written report. HOST. The measurements used here are the
 * synthetic ones of [HeatSyntheticScenarios]; no number in this file was measured on a device.
 */
class HeatMeasurementReportTest {
    private fun objects(array: JSONArray) = (0 until array.length()).map(array::getJSONObject)
    private fun row(report: JSONObject, id: String) =
        objects(report.getJSONObject("summary").getJSONArray("rows")).first { it.getString("configuration") == id }
    private fun comparison(report: JSONObject, id: String) =
        objects(report.getJSONObject("summary").getJSONArray("comparisons")).first { it.getString("configuration") == id }
    private fun reasons(report: JSONObject, id: String) =
        comparison(report, id).getJSONArray("reasons").let { list -> (0 until list.length()).map(list::getString) }

    /** Null when both JSON values are the same (numbers by value); otherwise the first path where they differ. */
    private fun difference(left: Any?, right: Any?, path: String = "$"): String? {
        fun missing(value: Any?) = value == null || value == JSONObject.NULL
        return when {
            missing(left) || missing(right) -> if (missing(left) && missing(right)) null else path
            left is JSONObject && right is JSONObject -> {
                val keys = left.keys().asSequence().toSet() + right.keys().asSequence().toSet()
                keys.sorted().firstNotNullOfOrNull { difference(left.opt(it), right.opt(it), "$path.$it") }
            }
            left is JSONArray && right is JSONArray -> if (left.length() != right.length()) "$path.length"
                else (0 until left.length()).firstNotNullOfOrNull { difference(left.opt(it), right.opt(it), "$path[$it]") }
            left is Number && right is Number -> if (Math.abs(left.toDouble() - right.toDouble()) <= 1e-9) null else path
            else -> if (left == right) null else path
        }
    }

    @Test fun theMedianIsTheMiddleValueOrTheMeanOfTheTwoMiddleValues() {
        assertNull(HeatStatistics.median(emptyList()))
        assertEquals(7.0, HeatStatistics.median(listOf(7.0))!!, 0.0)
        assertEquals(3.0, HeatStatistics.median(listOf(5.0, 1.0, 3.0))!!, 0.0)
        assertEquals(2.5, HeatStatistics.median(listOf(4.0, 1.0, 3.0, 2.0))!!, 0.0)
        assertEquals(15.0, HeatStatistics.medianOfLongs(listOf(10L, 20L))!!, 0.0)
    }

    @Test fun aHistogramPercentileIsTheUpperEdgeOfItsClassAndNeverBetterThanMeasured() {
        val histogram = HeatHistogram()
        assertNull(histogram.percentile(50))
        listOf(8.2, 8.4, 9.0, 16.9).forEach(histogram::add)
        assertEquals(4, histogram.count)
        assertEquals("8.4 ms lies in the class up to 8.5 ms", 8.5, histogram.percentile(50)!!, 0.0)
        assertEquals(17.0, histogram.percentile(90)!!, 0.0)
        assertEquals(17.0, histogram.percentile(99)!!, 0.0)
        histogram.add(412.3)
        assertEquals(1, histogram.overflow)
        assertEquals("Beyond 150 ms the measured maximum is reported", 412.3, histogram.percentile(99)!!, 0.0)
        histogram.add(Double.NaN); histogram.add(-1.0)
        assertEquals("Values that are not durations are not counted", 5, histogram.count)
        val copy = HeatHistogram.parse(JSONObject(histogram.toJson().toString()))!!
        assertEquals(histogram.toJson().toString(), copy.toJson().toString())
        assertEquals(histogram.percentile(50), copy.percentile(50))
        assertNull(HeatHistogram.parse(JSONObject().put("bucketMs", 1.0).put("bucketCount", 300).put("counts", JSONArray())))
        assertNull(HeatHistogram.parse(JSONObject().put("bucketMs", 0.5).put("bucketCount", 300)
            .put("counts", JSONArray().put(JSONArray().put(300).put(1)))))
    }

    @Test fun windowFramesAreCountedWithTheirShareOfSlowFramesAndNoFrameIsAMeasuredZero() {
        val frames = HeatFrameAccumulator(vsyncMs = 16.667)
        listOf(8.0, 8.0, 40.0, 8.0).forEachIndexed { index, value -> frames.frame(1_000L + index * 600L, value, droppedReports = index % 2) }
        val json = frames.toJson(observedMs = 2_000L)
        assertEquals(HeatFrameAccumulator.MEASURED, json.getString("status"))
        assertEquals(4, json.getInt("frames"))
        assertEquals(2.0, json.getDouble("framesPerSecond"), 0.0)
        assertEquals(8.5, json.getDouble("p50Ms"), 0.0)
        assertEquals(40.5, json.getDouble("p99Ms"), 0.0)
        assertEquals("One of four frames took longer than two frame periods", 0.25, json.getDouble("over2VsyncShare"), 0.0)
        assertEquals(2, json.getInt("droppedReports"))
        assertEquals("[2,2]", json.getJSONArray("perSecond").toString())
        val none = HeatFrameAccumulator(16.667).toJson(3_000L)
        assertEquals(0, none.getInt("frames"))
        assertEquals(0.0, none.getDouble("framesPerSecond"), 0.0)
        assertTrue(none.isNull("p50Ms") && none.isNull("over2VsyncShare"))
        val unknownVsync = HeatFrameAccumulator(null).apply { frame(0L, 50.0) }.toJson(1_000L)
        assertTrue("Without a reported frame period no share of slow frames is stated", unknownVsync.isNull("over2VsyncShare"))
        val missing = HeatFrameAccumulator.notAvailable("synthetic reason")
        assertEquals(HeatMeasurement.NOT_AVAILABLE, missing.getString("status"))
        assertEquals("synthetic reason", missing.getString("reason"))
    }

    @Test fun theStageCounterIsTakenAsThePageReportedItOrMarkedNotAvailable() {
        for (raw in listOf(null, "null", "", "kein json", "{}", "{\"schema\":\"other\"}",
            "{\"schema\":\"${HeatStageFrames.PROBE_SCHEMA}\",\"delivered\":3}"))
            assertEquals(raw.toString(), HeatMeasurement.NOT_AVAILABLE, HeatStageFrames.parse(raw, 1_000L).getString("status"))
        val page = JSONObject().put("schema", HeatStageFrames.PROBE_SCHEMA).put("delivered", 10).put("ticks", 120).put("limit", 5)
            .put("observedMs", 1_990).put("visibility", "visible").put("perSecond", JSONArray(listOf(5, 5)))
            .put("limitChanges", JSONArray().put(JSONArray().put(400).put(5)))
            .put("histogram", HeatHistogram().apply { repeat(9) { add(200.0) } }.toJson())
        val stage = HeatStageFrames.parse(page.toString(), 2_000L)
        assertEquals(HeatFrameAccumulator.MEASURED, stage.getString("status"))
        assertEquals(10, stage.getInt("delivered"))
        assertEquals(5.0, stage.getDouble("deliveredPerSecond"), 0.0)
        assertEquals(120, stage.getInt("displayTicks"))
        assertEquals(200.0, stage.getDouble("intervalMaxMs"), 0.0)
        assertEquals("Beyond 150 ms the measured maximum is the percentile", 200.0, stage.getDouble("intervalP50Ms"), 0.0)
        assertEquals(5, stage.getInt("frameLimit"))
        assertEquals("visible", stage.getString("pageVisibility"))
        assertEquals(1_990L, stage.getLong("pageObservedMs"))
    }

    @Test fun theSummaryComparesEveryConfigurationWithTheBaselineAndNamesTheCostDriverByItsRule() {
        val report = HeatSyntheticScenarios.before()
        val baseline = row(report, "A")
        assertEquals(1, baseline.getInt("runs"))
        assertEquals(0, baseline.getInt("startConditionMissed"))
        assertEquals(listOf(2, 2, 0), listOf(baseline.getInt("turns"), baseline.getInt("complete"), baseline.getInt("aborts")))
        assertEquals(1.0, baseline.getDouble("thermalMaxMedian"), 0.0)
        assertEquals("Highest battery temperature of the run minus its start temperature", 1.2, baseline.getDouble("batteryRiseCMedian"), 1e-9)
        assertEquals(12_000.0, baseline.getDouble("runTotalMsMedian"), 0.0)
        val first = baseline.getJSONObject("roles").getJSONObject("ERSTFRAGE")
        assertEquals(1_500.0, first.getDouble("firstTokenMsMedian"), 0.0)
        assertEquals(6_000.0, first.getDouble("firstVisibleTextMsMedian"), 0.0)
        assertEquals(6_000.0, first.getDouble("totalMsMedian"), 0.0)
        assertEquals("120 characters in 4.5 s after the first token", 26.667, first.getDouble("charsPerSecondMedian"), 0.0)
        assertEquals("[\"${HeatTurnRecorder.EVENT_STAGE_MESSAGE}\"]", first.getJSONArray("firstVisibleTextEvents").toString())
        assertEquals(8.5, first.getDouble("windowP50MsMedian"), 0.0)
        assertEquals(20.0 * 1_000 / 6_000, first.getDouble("stageFramesPerSecondMedian"), 1e-3)

        val full = row(report, "C")
        assertEquals(listOf(1, 0, 1), listOf(full.getInt("turns"), full.getInt("complete"), full.getInt("aborts")))
        assertEquals(1, full.getJSONObject("abortKinds").getInt(HeatTurnRecorder.STOPPED_BY_THERMAL_GUARD))
        assertEquals(3.0, full.getDouble("thermalMax"), 0.0)
        assertTrue("A run with an interrupted answer has no total time", full.isNull("runTotalMsMedian"))
        assertTrue(full.getJSONObject("roles").getJSONObject("ERSTFRAGE").isNull("totalMsMedian"))
        assertEquals(0, row(report, "F").getInt("runs"))

        val off = comparison(report, "B")
        assertTrue(off.getBoolean("comparable"))
        assertEquals(HeatMeasurement.FACTOR_STAGE_OFF, off.getString("factor"))
        assertEquals(-1.0, off.getJSONObject("delta").getDouble("thermalMaxMedian"), 0.0)
        assertEquals(-0.4, off.getJSONObject("delta").getDouble("batteryRiseCMedian"), 1e-9)
        assertEquals(-6.667, off.getJSONObject("delta").getDouble("runTotalMsMedianPercent"), 0.0)
        assertEquals(0, off.getJSONObject("delta").getInt("aborts"))
        val kept = comparison(report, "D")
        assertTrue("The follow-up was really continued in the kept conversation", kept.getBoolean("comparable"))
        assertEquals(-50.0, kept.getJSONObject("delta").getJSONObject("FOLGEFRAGE").getDouble("totalMsMedianPercent"), 0.0)
        assertEquals(0.0, kept.getJSONObject("delta").getJSONObject("ERSTFRAGE").getDouble("totalMsMedianPercent"), 0.0)
        assertEquals(1, row(report, "D").getJSONObject("roles").getJSONObject("FOLGEFRAGE").getInt("dialogueReusedTurns"))
        assertEquals(-46.667, comparison(report, "E").getJSONObject("delta").getDouble("runTotalMsMedianPercent"), 0.0)
        assertEquals(listOf("NOT_RUN"), reasons(report, "F"))
        assertFalse(comparison(report, "F").getBoolean("comparable"))

        val driver = report.getJSONObject("summary").getJSONObject("costDriver")
        assertEquals("FOUND", driver.getString("status"))
        assertEquals("C", driver.getString("configuration"))
        assertEquals("The first metric of the rule decides: interrupted answers", "aborts", driver.getString("metric"))
        assertEquals(1.0, driver.getDouble("delta"), 0.0)
        assertTrue("One run per configuration is a single measurement", driver.getBoolean("singleRuns"))
        assertEquals("[\"C\"]", driver.getJSONArray("tied").toString())
        assertEquals(HeatReport.COST_RULE, report.getJSONObject("summary").getString("rule"))
    }

    @Test fun withoutInterruptedAnswersTheNextMetricOfTheRuleDecides() {
        val driver = HeatSyntheticScenarios.after().getJSONObject("summary").getJSONObject("costDriver")
        assertEquals(listOf("FOUND", "C", "thermalMaxMedian"),
            listOf(driver.getString("status"), driver.getString("configuration"), driver.getString("metric")))
        assertEquals(1.0, driver.getDouble("delta"), 0.0)
    }

    @Test fun aRunThatMissedItsStartConditionMakesTheComparisonNotComparable() {
        val report = HeatSyntheticScenarios.before()
        objects(report.getJSONArray("runs")).first { it.getString("configuration") == "B" }.getJSONObject("startCondition")
            .put("reached", false).put("reason", "synthetisch: Start nicht erreicht")
        HeatReport.withSummary(report)
        assertEquals(listOf("START_CONDITION"), reasons(report, "B"))
        assertTrue("A flag on another configuration does not touch this one", comparison(report, "E").getBoolean("comparable"))
        val text = HeatReport.markdown(report)
        assertTrue(text, "nicht vergleichbar" in text)
        assertTrue(text, "Startbedingung in mindestens einem Lauf nicht erreicht" in text)
        assertTrue(text, "## Läufe mit nicht erreichter Startbedingung" in text)
        assertTrue(text, "synthetisch: Start nicht erreicht" in text)

        objects(report.getJSONArray("runs")).first { it.getString("configuration") == "A" }.getJSONObject("startCondition").put("reached", false)
        HeatReport.withSummary(report)
        assertTrue("A flagged baseline makes every comparison not comparable",
            listOf("B", "C", "D", "E").all { "START_CONDITION" in reasons(report, it) })
        assertEquals("NOT_COMPARABLE", report.getJSONObject("summary").getJSONObject("costDriver").getString("status"))
        assertTrue(HeatReport.costDriverText(report), "nicht vergleichbar" in HeatReport.costDriverText(report))
    }

    @Test fun aFactorThatDidNotTakeEffectAndOtherDifferencesAreReasonsAgainstAComparison() {
        val report = HeatSyntheticScenarios.before()
        fun turnsOf(id: String) = objects(report.getJSONArray("runs")).filter { it.getString("configuration") == id }
            .flatMap { objects(it.getJSONArray("turns")) }
        turnsOf("D").forEach { it.put("dialogueReused", false) }
        turnsOf("B").forEach { it.getJSONObject("stageFrames").put("delivered", 12) }
        turnsOf("E")[0].put("promptCharacters", 2_000)
        objects(report.getJSONArray("runs")).first { it.getString("configuration") == "E" }.getJSONObject("startCondition").put("charging", true)
        HeatReport.withSummary(report)
        assertEquals("The runtime never continued the kept conversation", listOf("FACTOR_NOT_EFFECTIVE"), reasons(report, "D"))
        assertEquals("The stage still delivered frames although it was to be off", listOf("FACTOR_NOT_EFFECTIVE"), reasons(report, "B"))
        assertEquals(listOf("CONDITIONS_CHANGED", "PROMPT_SIZE_DIFFERS"), reasons(report, "E"))

        val withoutModel = HeatSyntheticScenarios.before()
        objects(withoutModel.getJSONArray("runs")).first { it.getString("configuration") == "E" }.getJSONArray("turns")
            .getJSONObject(1).put("outcome", HeatTurnRecorder.NO_MODEL_CALL)
        withoutModel.getJSONArray("runs").remove(1)
        HeatReport.withSummary(withoutModel)
        assertEquals(listOf("TURN_WITHOUT_MODEL"), reasons(withoutModel, "E"))
        assertEquals("Configuration B has no run any more", listOf("NOT_RUN"), reasons(withoutModel, "B"))
    }

    @Test fun whenNothingReachesAMinimumChangeNoCostDriverIsNamed() {
        val report = HeatSyntheticScenarios.before()
        val baseline = objects(report.getJSONArray("runs")).first { it.getString("configuration") == "A" }
        val runs = JSONArray()
        for (id in listOf("A", "B", "C", "D", "E")) runs.put(JSONObject(baseline.toString()).put("configuration", id))
        report.put("runs", runs)
        // Make the copied runs satisfy the factor checks without changing a measured value.
        objects(runs).filter { it.getString("configuration") == "B" }.flatMap { objects(it.getJSONArray("turns")) }
            .forEach { it.getJSONObject("stageFrames").put("delivered", 0) }
        objects(runs).filter { it.getString("configuration") == "D" }.flatMap { objects(it.getJSONArray("turns")) }
            .forEach { it.put("dialogueReused", true) }
        HeatReport.withSummary(report)
        val driver = report.getJSONObject("summary").getJSONObject("costDriver")
        assertEquals("NONE", driver.getString("status"))
        assertTrue(HeatReport.costDriverText(report), "kein Kostentreiber" in HeatReport.costDriverText(report))
    }

    @Test fun aWrittenReportReadsBackToTheSameSummaryAndText() {
        val report = HeatSyntheticScenarios.before()
        val read = HeatReport.read(report.toString(1))
        assertNull("Reading back loses nothing", difference(report, read))
        val recomputed = HeatReport.summarize(read)
        assertNull("The summary is a pure function of the stored runs", difference(report.getJSONObject("summary"), recomputed))
        assertEquals(HeatReport.markdown(report), HeatReport.markdown(read))
        assertEquals("continuum-waermemessung-kurz-", HeatReport.fileStem(read).substring(0, 29))
        assertTrue(HeatReport.fileStem(read).matches(Regex("continuum-waermemessung-kurz-\\d{8}-\\d{6}")))
        assertTrue(runCatching { HeatReport.read("{\"schema\":\"continuum-model-eval-report-v1\"}") }.isFailure)
        assertTrue(runCatching { HeatReport.read("{\"schema\":\"${HeatMeasurement.SCHEMA}\"}") }.isFailure)
    }

    @Test fun theReadableReportStatesInputNotRunAbortsRestoreAndWhatEachNumberMeans() {
        val report = HeatSyntheticScenarios.before()
        val text = HeatReport.markdown(report)
        for (expected in listOf(
            "# Continuum · Wärme- und Zeitmessung (kurz)",
            HeatMeasurement.INPUT_NOTE,
            "- Gerät: SYNTHETIC HOST-TEST-FIXTURE",
            "- Sprachausgabe: aus",
            "## Wärme und Abbrüche je Konfiguration",
            "| A · Basis: Bühne ruht beim Rechnen, Gemma 4 E4B, Gespräch nicht behalten | 1 | 0 | 2 von 2 | 0 | 1 | 1 |",
            "| C · Bühne mit voller Bewegung beim Rechnen (wie Code67) | 1 | 0 | 0 von 1 | 1 (1× vom Hitzeschutz angehalten) | 3 | 3 |",
            "| F · GPU-Beschleuniger statt CPU | NOT_RUN | – |",
            "## Zeiten und Bildzeit · Erstfrage",
            "| A | 1 von 1 | 1500 | 6000 | 6000 | 26,67 | 8,5 / 17 / 17 | 0 % |",
            "## Zeiten und Bildzeit · Folgefrage",
            "| C | NOT_RUN | – |",
            "## Vergleich mit der Basis A (jeweils ein Faktor)",
            "| B · Bühne aus (Seite unsichtbar) | ja | ±0 | −1 | ",
            "| F · GPU-Beschleuniger statt CPU | nicht vergleichbar | – | – | – | – | – | – | nicht gelaufen: Synthetisch: GPU wird nicht geschaltet. |",
            "## Größter gemessener Kostentreiber",
            HeatReport.COST_RULE,
            "Ergebnis: C · Bühne mit voller Bewegung beim Rechnen (wie Code67) – Abbrüche +1 gegenüber der Basis.",
            "Einzelmessung je Konfiguration",
            "## Nicht gelaufen (NOT_RUN)",
            "## Nicht vollständig beantwortete Fragen im Einzelnen",
            "- Lauf 3 · C · kurz · Erstfrage: vom Hitzeschutz angehalten nach 3000 ms, 60 Zeichen erzeugt, Thermalstufe max 3",
            "## Modell laden und entladen (nicht in den Antwortzeiten enthalten)",
            "- E · UNLOAD: 200 ms · in Ordnung",
            "## Wiederherstellung",
            "- Geladenes Modell: zurückgestellt",
            "- Bühne: zurückgestellt",
            "## Was die Zahlen bedeuten",
            "In dieser Messung: " + HeatTurnRecorder.EVENT_STAGE_MESSAGE + ".",
            "Es steht nirgends ein Schätzwert."))
            assertTrue("Missing in the readable report: $expected\n\n$text", expected in text)
        assertFalse("null" in text)
    }

    @Test fun theChatMessageNamesTheRealFilesAndSaysWhenNothingWasSaved() {
        val report = HeatSyntheticScenarios.before()
        val saved = HeatReport.chatSummary(report, listOf("continuum-waermemessung-kurz-x.json", "continuum-waermemessung-kurz-x.md"), "Download-Ordner")
        assertTrue(saved, saved.startsWith("WÄRME- UND ZEITMESSUNG (kurz) · abgeschlossen\n" + HeatMeasurement.INPUT_NOTE + "."))
        assertTrue(saved, "\nA: 1 Lauf · Abbrüche 0 · Thermalstufe max 1 · Akku +1,2 °C · Erstfrage gesamt 6000 ms, erster sichtbarer Text 6000 ms" in saved)
        assertTrue(saved, "\nF: NOT_RUN" in saved)
        assertTrue(saved, "Ergebnis: C · " in saved)
        assertTrue(saved, "Bericht gespeichert (Download-Ordner): continuum-waermemessung-kurz-x.json und continuum-waermemessung-kurz-x.md" in saved)
        assertFalse("ACHTUNG" in saved)
        val unsaved = HeatReport.chatSummary(report.put("restoreComplete", false).put("stop", JSONObject().put("reason", HeatStop.OWNER_STOP)),
            emptyList(), null)
        assertTrue(unsaved, "vorzeitig beendet (OWNER_STOP)" in unsaved)
        assertTrue(unsaved, "Der Bericht konnte nicht als Datei gespeichert werden." in unsaved)
        assertTrue(unsaved, "ACHTUNG: Nicht alles ließ sich zurückstellen" in unsaved)
    }

    @Test fun aReportSavedFromAnInterruptedMeasurementSaysThatTheRestoreWasNotRecorded() {
        // What the app keeps after every run: no end, no restore record.
        val partial = HeatSyntheticScenarios.before().apply {
            remove("restore"); remove("restoreComplete"); remove("endedAtEpochMs"); remove("summary")
            put("complete", false)
            put("stop", JSONObject().put("reason", HeatStop.INTERRUPTED).put("message", "synthetisch unterbrochen"))
        }
        HeatReport.withSummary(partial)
        val text = HeatReport.markdown(partial)
        assertTrue(text, "- Status: unvollständig · vorzeitig beendet: INTERRUPTED – synthetisch unterbrochen" in text)
        assertTrue(text, "- Wiederherstellung nach der Messung: nicht aufgezeichnet (Messung unterbrochen)" in text)
        assertTrue(text, "- Nicht aufgezeichnet: Die Messung wurde unterbrochen" in text)
        val chat = HeatReport.chatSummary(partial, listOf("a.json", "a.md"), "Download-Ordner")
        assertTrue(chat, "vorzeitig beendet (INTERRUPTED)" in chat)
        assertFalse("An unknown restore is not reported as a failed one", "ACHTUNG" in chat)
    }

    @Test fun numbersAreWrittenTheWayTheOwnerReadsThem() {
        assertEquals("6000", HeatReport.plain(6_000.0))
        assertEquals("26,67", HeatReport.plain(26.667))
        assertEquals("0,5", HeatReport.plain(0.5))
        assertEquals("-1", HeatReport.plain(-1.0))
        assertEquals("vom Hitzeschutz angehalten", HeatReport.outcomeText(HeatTurnRecorder.STOPPED_BY_THERMAL_GUARD))
        assertEquals("UNBEKANNT", HeatReport.outcomeText("UNBEKANNT"))
    }

    /**
     * The fixtures of the host tool are exactly what the app's own protocol and report code write for the synthetic
     * scenarios. When this fails after a deliberate change, replace the fixture with the file named in the message.
     */
    @Test fun theHostToolFixturesAreWhatTheAppWritesForTheSyntheticScenarios() {
        fun file(path: String) = File(path).takeIf(File::isFile) ?: File("../$path")
        val stale = ArrayList<String>()
        for ((name, report) in listOf("SYNTHETIC-heat-report-before.json" to HeatSyntheticScenarios.before(),
            "SYNTHETIC-heat-report-after.json" to HeatSyntheticScenarios.after())) {
            assertTrue(report.getBoolean("synthetic"))
            assertEquals(HeatSyntheticScenarios.NOTE, report.getString("syntheticNote"))
            val fixture = file("tools/heat-report/fixtures/$name")
            val differs = if (!fixture.isFile) "missing" else difference(HeatReport.read(fixture.readText(Charsets.UTF_8)), report)
            if (differs == null) continue
            // Only a mismatch leaves a file behind: what the app writes now, ready to replace the fixture.
            val generated = File(System.getProperty("java.io.tmpdir"), name).apply { writeText(report.toString(1) + "\n", Charsets.UTF_8) }
            stale += "$fixture differs from what the app writes at $differs – generated: $generated"
        }
        assertTrue(stale.joinToString("\n"), stale.isEmpty())
    }
}
