package at.heinz.deviceai.research.exp20.p2

import at.heinz.deviceai.research.argon4.ComputeAllocationPolicy.Action

enum class Proposal { CLARIFY, PROCEED, NO_PROPOSAL }

/** One arm's routing outcome for one decision turn. The proposal only sets unresolvedReference; choose() decides. */
data class Outcome(
    val arm: ArmId, val dialog: Dialog, val proposal: Proposal, val action: Action, val baseAction: Action,
    val correct: Boolean, val error: ErrorType?, val unauthorized: Boolean
)

/** Arm = fixed decision procedure; readout arms carry their frozen readout and threshold. */
class FrozenArm(val id: ArmId, val readout: LogisticReadout? = null, val threshold: Double = 0.5) {
    fun proposal(d: Dialog): Proposal = when (id) {
        ArmId.A -> Proposal.PROCEED
        ArmId.A_DSK -> if (DskBridge.wouldClarify(d)) Proposal.CLARIFY else Proposal.PROCEED
        else -> {
            val z = P2Features.vector(id, d)
            when {
                z == null -> Proposal.NO_PROPOSAL                       // invalid time: fall back to A, never guess
                requireNotNull(readout).p(z) >= threshold -> Proposal.CLARIFY
                else -> Proposal.PROCEED
            }
        }
    }

    fun evaluate(d: Dialog): Outcome {
        val text = d.decision.text
        val prop = proposal(d)
        val base = RouterBridge.action(text, false)
        val action = RouterBridge.action(text, prop == Proposal.CLARIFY)
        val asked = action == Action.CLARIFY
        val gold = requireNotNull(d.decision.gold)
        val correct = asked == (gold == Gold.CLARIFY)
        val unauthorized = action == Action.TOOL && base != Action.TOOL
        return Outcome(id, d, prop, action, base, correct, if (unauthorized) ErrorType.UNAUTHORIZED_ACTION_SUGGESTION else errorType(d.decision, asked), unauthorized)
    }

    companion object {
        fun errorType(t: Turn, asked: Boolean): ErrorType? = when {
            t.gold == Gold.CLARIFY && !asked -> when (t.cause) {
                Cause.STALE_GAP -> ErrorType.STALE_FACT_LEAKAGE
                Cause.AMBIGUOUS_ENTITY, Cause.NO_REFERENT -> ErrorType.WRONG_ENTITY_BINDING
                else -> ErrorType.MISSED_CONTEXT_SWITCH                // LOCATION_SWITCH, PRESENCE_DRIFT
            }
            t.gold == Gold.PROCEED && asked -> if (t.negControl) ErrorType.FALSE_CONTEXT_SWITCH else ErrorType.UNNECESSARY_CLARIFICATION
            else -> null
        }
    }
}

object P2Eval {
    data class ClusterScore(val clusterId: String, val scenario: Scenario, val solved: Boolean, val accuracy: Double)

    fun clusters(outcomes: List<Outcome>): List<ClusterScore> = outcomes.groupBy { it.dialog.clusterId }.toSortedMap().map { (id, os) ->
        ClusterScore(id, os.first().dialog.scenario, os.all { it.correct }, os.count { it.correct }.toDouble() / os.size)
    }

    /** Readout arms are tuned on VALIDATION only: maximise solve rate, then turn accuracy, then larger lambda, then thr nearest 0.5. */
    data class GridPoint(val arm: ArmId, val lambda: Double, val threshold: Double, val solveRate: Double, val turnAccuracy: Double, val readoutSha: String)

    fun select(grid: List<GridPoint>): GridPoint = grid.sortedWith(
        compareByDescending<GridPoint> { it.solveRate }.thenByDescending { it.turnAccuracy }
            .thenByDescending { it.lambda }.thenBy { kotlin.math.abs(it.threshold - 0.5) }.thenBy { it.threshold }
    ).first()

    fun trainingSet(arm: ArmId, train: List<Dialog>): Pair<List<DoubleArray>, IntArray> {
        val rows = train.mapNotNull { d -> P2Features.vector(arm, d)?.let { it to (if (d.decision.gold == Gold.CLARIFY) 1 else 0) } }
        return rows.map { it.first } to rows.map { it.second }.toIntArray()
    }
}
