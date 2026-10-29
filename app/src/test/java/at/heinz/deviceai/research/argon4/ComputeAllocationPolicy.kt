package at.heinz.deviceai.research.argon4

/**
 * ARGON4 F1 · value-of-computation router (research prototype, not in the APK).
 *
 * Today ChatReasoningRouter starts up to four extra Gemma passes when ContinuumTaskClassifier's complexity, which is
 * mostly the request LENGTH plus keywords, crosses a threshold. This policy instead chooses the action with the
 * highest expected utility from signals the pipeline already produces (EWM2 decision, evidence coverage, claim-check
 * flags, unresolved references, freshness, tool intent, device state):
 *
 *   EU(a | s) = p(a, s) · V  −  h(a, s) · R  −  λ_t · t(a)  −  λ_e · e(a)
 *
 * p = probability the turn ends correct, h = probability of a confident wrong answer, V = value of a correct answer,
 * R = cost of a wrong one (R ≫ V for owner facts), t/e = expected latency and energy. p and h come from Beta posteriors
 * per (action, signal bin) that start at conservative priors and are updated only from verified outcomes (owner
 * feedback, claim check, independent tests). This is metareasoning in the sense of Russell & Wefald (1991); the
 * calibration-table form is our own choice for an on-device, auditable router.
 */
class ComputeAllocationPolicy(
    private val costs: Costs = Costs(),
    private val table: CalibrationTable = CalibrationTable.priors()
) {
    enum class Action { DIRECT_EVIDENCE, MODEL_ONE_PASS, MODEL_VERIFY, MODEL_DEEP, RESEARCH, TOOL, CLARIFY, ABSTAIN }
    enum class EvidenceDecision { QUERY, CLARIFY, ABSTAIN, NONE }

    data class Signals(
        val evidence: EvidenceDecision,
        val coverage: Double,            // share of question attributes covered by cited evidence (0..1)
        val claimFlags: Int,             // details the claim check could not ground in the last draft
        val unresolvedReference: Boolean,
        val freshnessNeeded: Boolean,
        val toolIntent: Boolean,
        val personal: Boolean,           // the answer would state an owner fact
        val modelReady: Boolean,
        val internetAllowed: Boolean,
        val lowBattery: Boolean = false,
        val throttled: Boolean = false,
        val remainingModelCalls: Int = 4
    ) {
        init {
            require(coverage in 0.0..1.0 && coverage.isFinite())
            require(claimFlags in 0..100 && remainingModelCalls in 0..16)
        }
        /** Discrete bin of the evidence state; the calibration table is indexed by it. */
        val bin: String get() = evidence.name + (if (coverage >= 0.999) ":FULL" else if (coverage >= 0.5) ":PART" else ":LOW") +
            (if (claimFlags > 0) ":FLAG" else "") + (if (unresolvedReference) ":REF" else "")
    }

    data class Costs(
        val valueCorrect: Double = 1.0,
        val costWrongPersonal: Double = 6.0,
        val costWrongGeneral: Double = 2.0,
        val perSecond: Double = 0.01,
        val perJoule: Double = 0.002,
        val seconds: Map<Action, Double> = mapOf(
            Action.DIRECT_EVIDENCE to 0.05, Action.MODEL_ONE_PASS to 8.0, Action.MODEL_VERIFY to 14.0, Action.MODEL_DEEP to 35.0,
            Action.RESEARCH to 20.0, Action.TOOL to 6.0, Action.CLARIFY to 0.05, Action.ABSTAIN to 0.05),
        val joules: Map<Action, Double> = mapOf(
            Action.DIRECT_EVIDENCE to 0.1, Action.MODEL_ONE_PASS to 40.0, Action.MODEL_VERIFY to 70.0, Action.MODEL_DEEP to 175.0,
            Action.RESEARCH to 60.0, Action.TOOL to 20.0, Action.CLARIFY to 0.1, Action.ABSTAIN to 0.1),
        val modelCalls: Map<Action, Int> = mapOf(Action.MODEL_ONE_PASS to 1, Action.MODEL_VERIFY to 2, Action.MODEL_DEEP to 5)
    ) {
        init { require(valueCorrect > 0 && costWrongPersonal >= 0 && costWrongGeneral >= 0 && perSecond >= 0 && perJoule >= 0) }
    }

    data class Choice(val action: Action, val expectedUtility: Double, val ranking: List<Pair<Action, Double>>, val reason: String)

    fun choose(s: Signals): Choice {
        val feasible = Action.values().filter { feasible(it, s) }
        check(feasible.isNotEmpty())
        val ranked = feasible.map { it to utility(it, s) }
            .sortedWith(compareByDescending<Pair<Action, Double>> { it.second }.thenBy { costs.seconds.getValue(it.first) })
        val best = ranked.first()
        return Choice(best.first, best.second, ranked, "bin=" + s.bin + ", feasible=" + feasible.size)
    }

    fun utility(a: Action, s: Signals): Double {
        val (p, h) = table.estimate(a, s.bin, s.personal)
        val wrongCost = if (s.personal) costs.costWrongPersonal else costs.costWrongGeneral
        val load = (if (s.throttled) 2.0 else 1.0) * (if (s.lowBattery) 2.0 else 1.0)
        return p * costs.valueCorrect - h * wrongCost - load * (costs.perSecond * costs.seconds.getValue(a) + costs.perJoule * costs.joules.getValue(a))
    }

    /** Hard constraints are not traded against utility: no model without a model, no web without consent, no tool from chat text. */
    fun feasible(a: Action, s: Signals): Boolean = when (a) {
        Action.DIRECT_EVIDENCE -> s.evidence == EvidenceDecision.QUERY && s.claimFlags == 0
        Action.MODEL_ONE_PASS, Action.MODEL_VERIFY, Action.MODEL_DEEP ->
            s.modelReady && costs.modelCalls.getValue(a) <= s.remainingModelCalls && !s.unresolvedReference
        Action.RESEARCH -> s.internetAllowed && !s.personal
        Action.TOOL -> s.toolIntent
        Action.CLARIFY -> s.unresolvedReference || s.evidence == EvidenceDecision.CLARIFY
        Action.ABSTAIN -> true
    }

    /** Owner-verified outcome of an executed action; the only way the table changes. */
    fun observe(a: Action, s: Signals, correct: Boolean, confidentlyWrong: Boolean) = table.update(a, s.bin, s.personal, correct, confidentlyWrong)
}

/**
 * Beta posteriors for p (correct) and h (confidently wrong) per (action, bin, personal). Priors encode the measured
 * EWM2 behaviour where it exists (QUERY:FULL direct answers 39/39 correct on TEST5) and caution elsewhere.
 */
class CalibrationTable private constructor(private val cells: MutableMap<String, DoubleArray>) {
    fun estimate(a: ComputeAllocationPolicy.Action, bin: String, personal: Boolean): Pair<Double, Double> {
        val c = cells[key(a, bin, personal)] ?: cells[key(a, "*", personal)] ?: DEFAULT
        val p = c[0] / (c[0] + c[1])
        val h = c[2] / (c[2] + c[3])
        return p to h
    }

    fun update(a: ComputeAllocationPolicy.Action, bin: String, personal: Boolean, correct: Boolean, confidentlyWrong: Boolean) {
        val k = key(a, bin, personal)
        val c = cells.getOrPut(k) { (cells[key(a, "*", personal)] ?: DEFAULT).copyOf() }
        if (correct) c[0] += 1.0 else c[1] += 1.0
        if (confidentlyWrong) c[2] += 1.0 else c[3] += 1.0
    }

    fun snapshot(): Map<String, List<Double>> = cells.mapValues { it.value.toList() }

    companion object {
        private val DEFAULT = doubleArrayOf(1.0, 1.0, 1.0, 3.0)
        private fun key(a: ComputeAllocationPolicy.Action, bin: String, personal: Boolean) = a.name + "|" + bin + "|" + personal

        /** [correct, incorrect, confidentlyWrong, notConfidentlyWrong] pseudo-counts. */
        fun priors(): CalibrationTable {
            val m = HashMap<String, DoubleArray>()
            fun put(a: ComputeAllocationPolicy.Action, bin: String, personal: Boolean, vararg v: Double) { m[key(a, bin, personal)] = v }
            for (personal in listOf(true, false)) {
                val A = ComputeAllocationPolicy.Action.values()
                for (a in A) put(a, "*", personal, 2.0, 2.0, 1.0, 3.0)
                // evidence-backed direct answers: measured TEST5 K1 behaviour, discounted to 20 pseudo-observations
                put(ComputeAllocationPolicy.Action.DIRECT_EVIDENCE, "QUERY:FULL", personal, 19.0, 1.0, 0.5, 19.5)
                put(ComputeAllocationPolicy.Action.DIRECT_EVIDENCE, "QUERY:PART", personal, 6.0, 4.0, 1.0, 9.0)
                // A free model answer about an owner fact without evidence is the hallucination case; for a general
                // question the local model is usually useful, and abstaining there rarely satisfies the owner.
                for (bin in listOf("NONE:LOW", "ABSTAIN:LOW")) {
                    put(ComputeAllocationPolicy.Action.MODEL_ONE_PASS, bin, personal, if (personal) 1.0 else 7.0, if (personal) 9.0 else 3.0,
                        if (personal) 7.0 else 2.0, if (personal) 3.0 else 8.0)
                    put(ComputeAllocationPolicy.Action.MODEL_VERIFY, bin, personal, if (personal) 1.5 else 7.5, if (personal) 8.5 else 2.5,
                        if (personal) 4.0 else 1.2, if (personal) 6.0 else 8.8)
                    put(ComputeAllocationPolicy.Action.MODEL_DEEP, bin, personal, if (personal) 1.5 else 7.8, if (personal) 8.5 else 2.2,
                        if (personal) 4.0 else 1.0, if (personal) 6.0 else 9.0)
                    put(ComputeAllocationPolicy.Action.ABSTAIN, bin, personal, if (personal) 8.0 else 2.0, if (personal) 2.0 else 8.0, 0.1, 9.9)
                    put(ComputeAllocationPolicy.Action.RESEARCH, bin, personal, 6.0, 4.0, 1.0, 9.0)
                    // with an open reference the right move is a question back, not silence
                    put(ComputeAllocationPolicy.Action.CLARIFY, "$bin:REF", personal, 8.0, 2.0, 0.1, 9.9)
                    put(ComputeAllocationPolicy.Action.ABSTAIN, "$bin:REF", personal, 2.0, 8.0, 0.1, 9.9)
                }
                // "correct" for a question back means the owner then answered it; it is never a confident error
                put(ComputeAllocationPolicy.Action.CLARIFY, "*", personal, 6.0, 4.0, 0.1, 9.9)
                put(ComputeAllocationPolicy.Action.TOOL, "*", personal, 6.0, 4.0, 1.0, 9.0)
                for (bin in listOf("QUERY:FULL:FLAG", "QUERY:PART:FLAG", "QUERY:LOW:FLAG", "NONE:LOW:FLAG")) {
                    put(ComputeAllocationPolicy.Action.MODEL_VERIFY, bin, personal, 6.0, 4.0, 1.0, 9.0)
                    put(ComputeAllocationPolicy.Action.MODEL_ONE_PASS, bin, personal, 4.0, 6.0, 3.0, 7.0)
                }
            }
            return CalibrationTable(m)
        }
    }
}

/** The existing selector, called exactly as ChatReasoningRouter does, as the control group for the benchmark. */
object ExistingRouterControl {
    fun extraModelCalls(task: String): Int {
        val assessment = at.heinz.deviceai.cognitive.ContinuumTaskClassifier.classify(task)
        if (assessment.expectedToolUse || assessment.taskClass == at.heinz.deviceai.cognitive.IntelligenceTaskClass.DEVICE) return 0
        val strategy = at.heinz.deviceai.cognitive.ReasoningStrategySelector.select(assessment.strategyProfile())
        // ChatReasoningRouter.Config.maxExtraCalls = 4 bounds DEEP and MULTI_CANDIDATE
        return if (strategy == at.heinz.deviceai.cognitive.ReasoningStrategy.DEEP ||
            strategy == at.heinz.deviceai.cognitive.ReasoningStrategy.MULTI_CANDIDATE) 4 else 0
    }
}
