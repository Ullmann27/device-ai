package at.heinz.deviceai.chief

import at.heinz.deviceai.cognitive.CognitiveWorkState

enum class TurnPhase { IDLE, ACKNOWLEDGED, WORKING, ENDED }
enum class TurnMilestone { APPROVAL_NEEDED, TOOL_STARTED, TOOL_VERIFIED, WORKER_STARTED, WORKER_REPORTED, BLOCKED }

/** RED stub. */
class TurnLifecycle(val turnId: String, private val minMilestoneGapMillis: Long = 1_500, private val clock: () -> Long = System::currentTimeMillis) {
    var phase = TurnPhase.IDLE; private set
    fun acknowledge(): String? = null
    fun milestone(kind: TurnMilestone, detail: String): String? = null
    fun deliver(): Boolean = false
    companion object { fun milestoneOf(state: CognitiveWorkState): TurnMilestone? = null }
}
