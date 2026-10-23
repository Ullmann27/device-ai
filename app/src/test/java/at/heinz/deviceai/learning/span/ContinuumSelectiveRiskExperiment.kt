package at.heinz.deviceai.learning.span

import at.heinz.deviceai.learning.span.ContinuumCertifiedAbstention.Outcome
import at.heinz.deviceai.learning.span.ContinuumSpanAbstentionBench.Case
import at.heinz.deviceai.learning.span.ContinuumSpanAbstentionBench.Split
import java.util.Random

/**
 * Milestone I2 measurement run (JVM): certified selective risk P(wrong | answered) for the V8 candidate, next to the
 * marginal I1 certificate. Same model, calibration (draws 0..9) and holdout (draws 10..19) as `certify`; draw-level
 * re-splits on draws 20..59; the frozen 264-question TEST is a KNOWN evaluation set (used since cycle 1), evaluated once
 * per policy and never used for any choice. Certificates come from the calibration draws only.
 * Run: java -cp csx1.jar at.heinz.deviceai.learning.span.ContinuumSelectiveRiskExperiment
 */
object ContinuumSelectiveRiskExperiment {
    private const val DELTA = 0.1
    private val SELECTIVE_ALPHAS = listOf(0.10, 0.05, 0.02, 0.01)
    /** (alphaMarginal, alphaSelective) */
    private val JOINT = listOf(0.05 to 0.10, 0.02 to 0.05, 0.02 to 0.02)
    private val GRID_SIZE = ContinuumCertifiedAbstention.GRID.size
    private fun f(x: Double) = "%.4f".format(x)

    private fun draws(range: IntRange) = range.map { ContinuumSpanAbstentionBench.generate(Split.VALIDATION, seed = 90_000_000L + it) }

    @JvmStatic fun main(args: Array<String>) {
        simulation()
        calibrationSize()
        realModel()
    }

    /** Multiple testing on the synthetic model: who keeps P(S(t) > alpha) <= delta, and at what coverage. */
    private fun simulation() {
        val sha = "0".repeat(64)
        for (alpha in listOf(0.05, 0.02)) {
            val oracle = 1 - kotlin.math.sqrt(alpha / 0.1)
            println("SIMULATION alpha=$alpha delta=$DELTA runs=2000 n=3000 oracleThreshold=${f(oracle)} " +
                "oracleCoverage=${f(ContinuumSelectiveRiskSimulation.trueCoverage(oracle))}")
            val rules = linkedMapOf<String, (List<Outcome>) -> Double?>(
                "holm (I2 certificate)" to { o -> ContinuumSelectiveRiskCertification.calibrate(sha, o, alpha, DELTA)?.threshold },
                "bonferroni" to { o -> ContinuumSelectiveRiskSimulation.bonferroni(o, alpha, DELTA) },
                "sgr binary search" to { o -> ContinuumSelectiveRiskSimulation.sgrBinarySearch(o, alpha, DELTA) },
                "uncorrected p<=delta" to { o -> ContinuumSelectiveRiskSimulation.uncorrected(o, alpha, DELTA) },
                "naive empirical<=alpha" to { o -> ContinuumSelectiveRiskSimulation.naive(o, alpha) })
            for ((name, rule) in rules) {
                val r = ContinuumSelectiveRiskSimulation.run(2000, alpha, choose = rule)
                println("  ${name.padEnd(24)} violationRate=${f(r.violationRate)} certifiedRate=${f(r.certifiedRate)} " +
                    "meanThreshold=${f(r.meanThreshold)} meanTrueCoverage=${f(r.meanCoverage)}")
            }
        }
    }

    /** How many answered calibration questions are needed so the FIRST Holm level (delta / 101) can certify alpha. */
    private fun calibrationSize() {
        println("CALIBRATION SIZE (minimal answered n so that P(Bin(n, alpha) <= floor(rate n)) <= delta/$GRID_SIZE)")
        for (alpha in SELECTIVE_ALPHAS) {
            val cells = listOf(0.0, 0.25, 0.5).map { fraction ->
                val n = (1..500_000).first { n ->
                    ContinuumCertifiedAbstention.binomialCdf((fraction * alpha * n).toInt(), n, alpha) <= DELTA / GRID_SIZE
                }
                "observed=${f(fraction * alpha)} n>=$n"
            }
            println("  alpha=$alpha  " + cells.joinToString("  "))
        }
    }

    private class Policy(val name: String, val alphaSelective: Double, val alphaMarginal: Double?,
                         val certificate: ContinuumSelectiveRiskCertification.Certificate?)

    private fun realModel() {
        val train = ContinuumSpanAbstentionBench.generate(Split.TRAIN)
        val validation = ContinuumSpanAbstentionBench.generate(Split.VALIDATION)
        val candidate = ContinuumSpanAbstentionExperiment.VARIANTS.keys.first { it.startsWith("V8 ") }
        val t = ContinuumSpanAbstentionExperiment.train(train, validation, 1L, 30) {
            ContinuumResidualSpanCore(77, ContinuumSpanAbstentionExperiment.VARIANTS.getValue(candidate))
        }
        val model = t.model
        val modelSha = ContinuumCertifiedAbstention.checkpointSha256(model)
        println("MODEL $candidate seed=1 steps=${t.steps} checkpointSha256=$modelSha")
        val calibrationDraws = draws(0..9); val holdoutDraws = draws(10..19); val extraDraws = draws(20..59)
        val calibration = calibrationDraws.flatten(); val holdout = holdoutDraws.flatten()
        val test = ContinuumSpanAbstentionBench.generate(Split.TEST)
        check(ContinuumSpanAbstentionBench.sha256(test) == ContinuumSpanAbstentionBench.TEST_SHA256) { "TEST changed" }
        println("CALIBRATION n=${calibration.size} sha=${ContinuumSpanAbstentionBench.sha256(calibration)}")
        println("HOLDOUT     n=${holdout.size} sha=${ContinuumSpanAbstentionBench.sha256(holdout)}")
        println("RESPLITS    draws 20..59, n=${extraDraws.sumOf { it.size }}, 200 random 20/20 splits of whole draws")
        println("TEST        n=${test.size} sha=${ContinuumSpanAbstentionBench.sha256(test)} (known frozen set, not a new blind test)")

        val cache = HashMap<Case, ContinuumResidualSpanCore.Prediction>()
        fun prediction(c: Case) = cache.getOrPut(c) { model.predict(c.example.evidence, c.example.question) }
        fun outcome(c: Case) = prediction(c).let { Outcome(it.answer != null, it.confidence, it.answer == c.example.answer) }
        val calOutcomes = calibration.map(::outcome)

        // I1 reference: what the marginal certificates say about the quality of the answers they let through
        for (alpha in listOf(0.10, 0.05, 0.02)) {
            val c = ContinuumCertifiedAbstention.calibrate(modelSha, calOutcomes, alpha, DELTA) ?: continue
            println("I1 REFERENCE alpha=$alpha threshold=${"%.2f".format(c.threshold)} answered=${c.calibrationAnswered}/${c.calibrationQuestions} " +
                "errors=${c.calibrationErrors} calibrationSelectiveError=${f(c.calibrationErrors.toDouble() / c.calibrationAnswered)}")
        }

        val policies = SELECTIVE_ALPHAS.map { a ->
            Policy("selective a_s=$a", a, null, ContinuumSelectiveRiskCertification.calibrate(modelSha, calOutcomes, a, DELTA))
        } + JOINT.map { (am, a) ->
            Policy("joint a_m=$am a_s=$a", a, am, ContinuumSelectiveRiskCertification.calibrate(modelSha, calOutcomes, a, DELTA, alphaMarginal = am))
        }
        for (p in policies) {
            val c = p.certificate
            println("CERTIFICATE ${p.name} delta=$DELTA -> " + (c?.let {
                "threshold=${"%.2f".format(it.threshold)} answered=${it.calibrationAnswered}/${it.calibrationQuestions} " +
                    "coverage=${f(it.calibrationCoverage)} errors=${it.calibrationErrors} selectiveError=${f(it.calibrationSelectiveError)} " +
                    "p=${"%.3g".format(it.pValue)} holmLevel=${"%.3g".format(it.holmLevel)} " +
                    "bonferroniUpper=${f(ContinuumSelectiveRiskCertification.clopperPearsonUpper(it.calibrationErrors, it.calibrationAnswered, DELTA / GRID_SIZE))} " +
                    "marginalError=${f(it.calibrationErrors.toDouble() / it.calibrationQuestions)}"
            } ?: "NONE (always abstain)"))
        }
        for (a in SELECTIVE_ALPHAS) {
            println("RULES ON CALIBRATION a_s=$a  bonferroni=${ContinuumSelectiveRiskSimulation.bonferroni(calOutcomes, a, DELTA)} " +
                "sgr=${ContinuumSelectiveRiskSimulation.sgrBinarySearch(calOutcomes, a, DELTA)} " +
                "uncorrected=${ContinuumSelectiveRiskSimulation.uncorrected(calOutcomes, a, DELTA)} " +
                "naive=${ContinuumSelectiveRiskSimulation.naive(calOutcomes, a)}")
        }

        // Draw-level re-splits: does the guarantee survive the real dependence between questions of one draw?
        val blocks = extraDraws.map { d -> d.map(::outcome) }
        val splitter = Random(20261022L)
        val splits = List(200) { blocks.indices.shuffled(splitter) }
        fun holdoutRates(hold: List<Outcome>, threshold: Double): Pair<Double, Double> {
            val shown = hold.filter { it.answered && it.confidence >= threshold }
            val errors = shown.count { !it.correct }
            return (if (shown.isEmpty()) 0.0 else errors.toDouble() / shown.size) to errors.toDouble() / hold.size
        }
        for (p in policies) {
            var violations = 0; var none = 0; var selectiveSum = 0.0; var coverageSum = 0.0; var counted = 0
            for (order in splits) {
                val cal = order.take(20).flatMap { blocks[it] }; val hold = order.drop(20).flatMap { blocks[it] }
                val c = ContinuumSelectiveRiskCertification.calibrate(modelSha, cal, p.alphaSelective, DELTA, alphaMarginal = p.alphaMarginal)
                if (c == null) { none++; continue }
                val (selective, marginal) = holdoutRates(hold, c.threshold)
                counted++; selectiveSum += selective
                coverageSum += hold.count { it.answered && it.confidence >= c.threshold }.toDouble() / hold.size
                if (selective > p.alphaSelective || (p.alphaMarginal != null && marginal > p.alphaMarginal)) violations++
            }
            println("RESPLIT ${p.name.padEnd(24)} violations=$violations/200 (${f(violations / 200.0)}) noCertificate=$none " +
                "meanHoldoutSelectiveError=${f(selectiveSum / maxOf(1, counted))} meanHoldoutCoverage=${f(coverageSum / maxOf(1, counted))}")
        }
        for (a in SELECTIVE_ALPHAS) {
            val rules = linkedMapOf<String, (List<Outcome>) -> Double?>(
                "bonferroni" to { o -> ContinuumSelectiveRiskSimulation.bonferroni(o, a, DELTA) },
                "sgr" to { o -> ContinuumSelectiveRiskSimulation.sgrBinarySearch(o, a, DELTA) },
                "uncorrected" to { o -> ContinuumSelectiveRiskSimulation.uncorrected(o, a, DELTA) },
                "naive" to { o -> ContinuumSelectiveRiskSimulation.naive(o, a) })
            for ((name, rule) in rules) {
                var violations = 0; var none = 0
                for (order in splits) {
                    val threshold = rule(order.take(20).flatMap { blocks[it] })
                    if (threshold == null) { none++; continue }
                    if (holdoutRates(order.drop(20).flatMap { blocks[it] }, threshold).first > a) violations++
                }
                println("RESPLIT RULE a_s=$a ${name.padEnd(12)} violations=$violations/200 (${f(violations / 200.0)}) noCertificate=$none")
            }
        }

        // Dependence diagnostic: Pearson dispersion of per-draw error counts at each certified threshold (40 draws).
        // phi ~ 1: draws behave like binomial samples; phi > 1: errors cluster inside draws (effective n ~ n / phi).
        for (p in policies) {
            val c = p.certificate ?: continue
            val counts = blocks.map { b ->
                val shown = b.filter { it.answered && it.confidence >= c.threshold }
                shown.size to shown.count { !it.correct }
            }
            val answered = counts.sumOf { it.first }; val errors = counts.sumOf { it.second }
            val used = counts.filter { it.first > 0 }
            if (errors == 0 || errors == answered || used.size < 2) {
                println("DEPENDENCE ${p.name.padEnd(24)} extraDraws answered=$answered errors=$errors phi=n/a")
                continue
            }
            val rate = errors.toDouble() / answered
            val chi2 = used.sumOf { (n, k) -> (k - n * rate) * (k - n * rate) / (n * rate * (1 - rate)) }
            val phi = chi2 / (used.size - 1)
            val nEff = (c.calibrationAnswered / maxOf(1.0, phi)).toInt()
            val kEff = (c.calibrationErrors / maxOf(1.0, phi)).toInt()
            val stillCertified = nEff > 0 && ContinuumCertifiedAbstention.binomialCdf(kEff, nEff, p.alphaSelective) <= c.holmLevel
            println("DEPENDENCE ${p.name.padEnd(24)} extraDraws answered=$answered errors=$errors rate=${f(rate)} phi=${f(phi)} " +
                "designEffectAdjusted n=$nEff k=$kEff stillCertified=$stillCertified")
        }

        // Frozen TEST, once per policy (distribution shift: new values, held-out forms). Evaluation only.
        fun line(cases: List<Case>, answer: (Case) -> String?): String {
            var answered = 0; var correct = 0
            for (c in cases) { val a = answer(c); if (a != null) { answered++; if (a == c.example.answer) correct++ } }
            val errors = answered - correct
            val (lo, hi) = if (answered == 0) 0.0 to 1.0 else ContinuumSpanAbstentionExperiment.wilson(errors, answered)
            return "n=${cases.size} answered=$answered coverage=${f(answered.toDouble() / cases.size)} correct=$correct errors=$errors " +
                "selectiveError=${if (answered == 0) "-" else f(errors.toDouble() / answered)} [${f(lo)}, ${f(hi)}] " +
                "marginalError=${f(errors.toDouble() / cases.size)}"
        }
        for (p in policies) {
            val answer: (Case) -> String? = { c -> ContinuumSelectiveRiskCertification.answer(model, c.example.evidence, c.example.question, p.certificate) }
            println("== ${p.name}" + (p.certificate?.let { " threshold=${"%.2f".format(it.threshold)}" } ?: " (no certificate: always abstain)"))
            println("  HOLDOUT       " + line(holdout, answer))
            println("  TEST ALL      " + line(test, answer))
            println("  TEST SEEN     " + line(test.filter { !it.unseenForm }, answer))
            println("  TEST UNSEEN   " + line(test.filter { it.unseenForm }, answer))
            println("  TEST          " + ContinuumSpanAbstentionBench.metrics(test, answer).line())
        }
    }
}
