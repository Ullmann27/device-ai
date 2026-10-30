package at.heinz.deviceai.research.exp20.p2

import at.heinz.deviceai.research.argon4.ComputeAllocationPolicy
import at.heinz.deviceai.research.argon4.ComputeAllocationPolicy.Action
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Infrastructure only: no arm is evaluated on any P2 dataset here (that happens after the preregistration commit). */
class P2InfraTest {
    @Test fun mcnemarAndHolmMatchHandComputedValues() {
        assertEquals(0.021484375, P2Stats.mcnemarExact(1, 9), 1e-15)
        assertEquals(0.03125, P2Stats.mcnemarExact(0, 6), 1e-15)
        assertEquals(1.0, P2Stats.mcnemarExact(5, 5), 0.0)
        assertEquals(1.0, P2Stats.mcnemarExact(0, 0), 0.0)
        assertArrayEquals(doubleArrayOf(0.03, 0.06, 0.06), P2Stats.holm(doubleArrayOf(0.01, 0.04, 0.03)), 1e-15)
    }

    @Test fun verdictRulesAreTheRegisteredOnes() {
        fun iv(e: Double, lo95: Double, hi95: Double, lo90: Double, hi90: Double) = P2Stats.Interval(e, lo95, hi95, lo90, hi90)
        assertEquals(P2Stats.Verdict.SUPERIOR, P2Stats.verdict(0.01, iv(0.08, 0.03, 0.13, 0.04, 0.12)))
        assertEquals(P2Stats.Verdict.INCONCLUSIVE, P2Stats.verdict(0.01, iv(0.03, 0.01, 0.05, 0.015, 0.06)))  // significant, below MDE, CI not inside margin
        assertEquals(P2Stats.Verdict.EQUIVALENT, P2Stats.verdict(0.6, iv(0.0, -0.05, 0.05, -0.04, 0.04)))
        assertEquals(P2Stats.Verdict.INCONCLUSIVE, P2Stats.verdict(0.3, iv(0.02, -0.04, 0.09, -0.03, 0.08)))
        assertEquals(P2Stats.Verdict.INFERIOR, P2Stats.verdict(0.001, iv(-0.1, -0.15, -0.05, -0.14, -0.06)))
        val a = P2Stats.bootstrapDiff(doubleArrayOf(1.0, 0.0, 1.0, 1.0), doubleArrayOf(0.0, 0.0, 1.0, 0.0))
        assertEquals(a, P2Stats.bootstrapDiff(doubleArrayOf(1.0, 0.0, 1.0, 1.0), doubleArrayOf(0.0, 0.0, 1.0, 0.0)))
        assertEquals(0.5, a.estimate, 1e-15)
    }

    @Test fun encoderSeesTheRegisteredCuesAndNothingElse() {
        fun f(t: String, name: String) = P2Encoder.encode(t)[P2Encoder.NAMES.indexOf(name)]
        assertEquals(1.0, f("Ich sitz jetzt im Auto.", "SWITCH"), 0.0)
        assertEquals(1.0, f("Nein, ich meinte nicht Mittwoch, sondern Donnerstag.", "CORRECTION"), 0.0)
        assertEquals(1.0, f("Ich war mit Herrn Huber und Herrn Gruber beim Training.", "PERSON_INTRO"), 0.0)
        assertEquals(0.5, f("Die Ärztin hat angerufen.", "PERSON_INTRO"), 0.0)            // Unicode word boundary before Ä
        assertEquals(1.0, f("Was hat er gesagt?", "PERSON_PRONOUN"), 0.0)
        assertEquals(0.0, f("Können Sie mir helfen?", "PERSON_PRONOUN"), 0.0)            // polite Sie mid-sentence
        assertEquals(1.0, f("Da klingelt jemand an der Tür.", "PRESENCE_UNCERTAIN"), 0.0)
        assertEquals(1.0, f("Da klingelt jemand an der Tür.", "DEVICE_OR_PLACE"), 0.0)
        assertTrue(P2Encoder.encode("Wie wird das Wetter morgen?").all { it == 0.0 })
        assertArrayEquals(P2Encoder.encode("Und wie war das zweite davon?"), P2Encoder.encode("Und wie war das zweite davon?"), 0.0)
    }

    private fun kernels() = listOf(LeakyKernel(), CtrnnKernel())

    @Test fun invalidTimesFailClosedWithoutAnyPartialStateTransition() {
        for (k in kernels()) {
            val track = TemporalTrack(k)
            val u = P2Config.channels(doubleArrayOf(1.0, 0.0, 1.0, 0.5, 0.0, 1.0, 1.0, 0.0))
            assertEquals(TimeStatus.OK, track.observe(100.0, u))
            val before = track.state()
            for (t in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY))
                assertEquals(TimeStatus.REJECTED_NON_FINITE, track.observe(t, u))
            assertEquals(TimeStatus.REJECTED_NON_MONOTONIC, track.observe(99.0, u))            // clock went backwards
            assertEquals(TimeStatus.REJECTED_INPUT, track.observe(200.0, DoubleArray(3)))
            assertEquals(TimeStatus.REJECTED_INPUT, track.observe(200.0, u.copyOf().also { it[2] = Double.NaN }))
            assertEquals(TimeStatus.DUPLICATE_IGNORED, track.observe(100.0, u))                 // redelivered event
            assertArrayEquals(before, track.state(), 0.0)
            assertEquals(TimeStatus.OK, track.observe(100.0, DoubleArray(u.size)))              // dt = 0, new input: no decay
            track.reset()
            assertArrayEquals(DoubleArray(u.size), track.state(), 0.0)
            assertEquals(TimeStatus.OK, track.observe(-5.0e9, u))                               // a negative epoch is a valid origin
        }
    }

    @Test fun replayIsBitIdenticalAndOnlyDtSeparatesFiveSecondsFromFourHours() {
        for (make in listOf({ LeakyKernel() as TemporalKernel }, { CtrnnKernel() as TemporalKernel })) {
            fun run(gap: Double): DoubleArray {
                val tr = TemporalTrack(make())
                tr.observe(0.0, P2Config.channels(P2Encoder.encode("Im Keller brennt noch das Licht.")))
                tr.observe(gap, P2Config.channels(P2Encoder.encode("Ist das dort noch an?")))
                return tr.state()
            }
            assertArrayEquals(run(5.0), run(5.0), 0.0)
            assertFalse(run(5.0).contentEquals(run(14_400.0)))
        }
        // leaky: after 4 h the 60 s channel of the referent is gone, the 1 h channel keeps exp(-4) of it
        val tr = TemporalTrack(LeakyKernel())
        tr.observe(0.0, P2Config.channels(P2Encoder.encode("Im Keller brennt noch das Licht.")))
        val injected = tr.state()[2 * 6 + 1]
        tr.observe(14_400.0, DoubleArray(P2Config.CHANNELS))
        assertTrue(tr.state()[2 * 6] < 1e-50)
        assertEquals(1.0, tr.state()[2 * 6 + 1] / (injected * kotlin.math.exp(-14_400.0 / 3600.0)), 1e-2)
    }

    /** UNAUTHORIZED_ACTION_SUGGESTION is structurally impossible if setting unresolvedReference never creates TOOL. */
    @Test fun clarifyFlagNeverCreatesAToolActionInTheRealPolicy() {
        var checked = 0
        for (ev in ComputeAllocationPolicy.EvidenceDecision.values()) for (cov in listOf(0.0, 0.6, 1.0)) for (flags in listOf(0, 1))
        for (tool in listOf(false, true)) for (personal in listOf(false, true)) for (ready in listOf(false, true))
        for (net in listOf(false, true)) for (low in listOf(false, true)) for (hot in listOf(false, true)) for (calls in listOf(0, 1, 2, 4, 5)) {
            fun s(ref: Boolean) = ComputeAllocationPolicy.Signals(ev, cov, flags, ref, false, tool, personal, ready, net, low, hot, calls)
            val with = ComputeAllocationPolicy().choose(s(true)).action
            val without = ComputeAllocationPolicy().choose(s(false)).action
            assertFalse("ref=true created TOOL for ${s(true)}", with == Action.TOOL && without != Action.TOOL)
            checked++
        }
        assertEquals(4 * 3 * 2 * 2 * 2 * 2 * 2 * 2 * 2 * 5, checked)
    }

    @Test fun goldRulesFollowTheRegisteredSemantics() {
        val scale = P2Generator.devBank().scale
        fun b() = DialogBuilder(Random(1), scale)
        b().apply { shortRef("Im Keller brennt noch das Licht."); wait(2000.0); decide("Ist das dort noch an?", RefType.SHORT) }
            .turns.last().let { assertEquals(Gold.CLARIFY, it.gold); assertEquals(Cause.STALE_GAP, it.cause) }
        b().apply { shortRef("x"); wait(5.0); switch("Ich sitz jetzt im Auto."); wait(5.0); decide("q", RefType.SHORT) }
            .turns.last().let { assertEquals(Cause.LOCATION_SWITCH, it.cause) }
        b().apply { switch("s"); wait(5.0); shortRef("x"); wait(5.0); decide("q", RefType.SHORT) }
            .turns.last().let { assertEquals(Gold.PROCEED, it.gold); assertTrue(it.negControl) }
        b().apply { person("p", 'm', "A"); wait(8000.0); person("p", 'm', "B"); wait(5.0); decide("q", RefType.PERSON_M) }
            .turns.last().let { assertEquals(Gold.PROCEED, it.gold) }
        b().apply { person("p", 'f', "A", "B"); wait(5.0); decide("q", RefType.PERSON_F) }
            .turns.last().let { assertEquals(Cause.AMBIGUOUS_ENTITY, it.cause) }
        b().apply { person("p", 'f', "A", "B"); wait(5.0); personCorrection("c", 'f', "A", "B"); wait(5.0); decide("q", RefType.PERSON_F) }
            .turns.last().let { assertEquals(Gold.PROCEED, it.gold) }
        b().apply { deviceRef("d"); wait(5.0); presence("p"); wait(5.0); decide("q", RefType.DEVICE_CMD) }
            .turns.last().let { assertEquals(Cause.PRESENCE_DRIFT, it.cause) }
        b().apply { longRef("l"); wait(9000.0); decide("q", RefType.LONG) }.turns.last().let { assertEquals(Gold.PROCEED, it.gold) }
    }

    @Test fun devGenerationIsDeterministicAndRoundTripsThroughTsv() {
        val splits = listOf(Split.TRAIN to 3, Split.VALIDATION to 2)
        val a = P2Generator.generate(P2Generator.devBank(), P2Config.DEV_SEED, splits)
        val b = P2Generator.generate(P2Generator.devBank(), P2Config.DEV_SEED, splits)
        assertEquals(a, b)
        assertEquals(Scenario.values().size * 5 * P2Config.VARIANTS, a.size)
        val f = File.createTempFile("exp20p2", ".tsv").apply { deleteOnExit() }
        P2Tsv.write(a, f)
        assertEquals(a, P2Tsv.read(f))
        // S5: the four variants of a stem carry identical text; only the pause differs
        val s5 = a.filter { it.scenario == Scenario.S5_SAME_SIGNALS_DT && it.clusterId == a.first { d -> d.scenario == Scenario.S5_SAME_SIGNALS_DT }.clusterId }
        assertEquals(1, s5.map { d -> d.turns.map { it.text } }.distinct().size)
        assertEquals(setOf(Gold.PROCEED, Gold.CLARIFY), s5.map { it.decision.gold }.toSet())
    }

    @Test fun readoutFitsADeterministicToyProblem() {
        val r = Random(3)
        val zs = List(200) { doubleArrayOf(r.nextDouble(-1.0, 1.0), r.nextDouble(-1.0, 1.0)) }
        val y = IntArray(200) { if (zs[it][0] + 0.2 * zs[it][1] > 0.1) 1 else 0 }
        val m = LogisticReadout.fit(zs, y, 1e-2)
        assertEquals(m.sha256(), LogisticReadout.fit(zs, y, 1e-2).sha256())
        val acc = zs.indices.count { (m.p(zs[it]) >= 0.5) == (y[it] == 1) } / 200.0
        assertTrue("toy accuracy $acc", acc > 0.95)
    }

    /** Construction check on the DEV bank only: each sentence class carries its intended cue, fillers carry none. */
    @Test fun devBankSentencesCarryTheirIntendedCues() {
        val bank = P2Generator.devBank()
        fun has(t: String, name: String) = P2Encoder.encode(t)[P2Encoder.NAMES.indexOf(name)] > 0.0
        fun all(ts: List<String>, name: String) = ts.forEach { assertTrue("$name missing in: $it", has(it.replace("{ROOM}", "Keller"), name)) }
        all(bank.shortReferents, "DEVICE_OR_PLACE"); all(bank.shortDecisions, "SHORT_REF")
        all(bank.longPairs.flatMap { listOf(it.first, it.second) }, "LONG_REF")
        all(bank.longCorrectionTriples.map { it.second }, "CORRECTION"); all(bank.corrections, "CORRECTION")
        all(bank.switches, "SWITCH"); all(bank.presence, "PRESENCE_UNCERTAIN")
        all(bank.devicePairs.map { it.first }, "DEVICE_OR_PLACE"); all(bank.devicePairs.map { it.second }, "SHORT_REF")
        all(bank.maleIntro + bank.femaleIntro + bank.maleTwo + bank.femaleTwo + bank.mechanicMale, "PERSON_INTRO")
        all(bank.maleDecisions + bank.femaleDecisions, "PERSON_PRONOUN")
        for (t in bank.fillers + bank.selfContained) assertTrue("filler carries a cue: $t", P2Encoder.encode(t).all { it == 0.0 })
    }
}
