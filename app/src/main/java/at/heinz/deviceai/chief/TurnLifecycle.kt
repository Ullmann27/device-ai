package at.heinz.deviceai.chief

import at.heinz.deviceai.cognitive.CognitiveWorkState

enum class TurnPhase { IDLE, ACKNOWLEDGED, WORKING, ENDED }

/** Only these count as "real milestones" worth telling the owner about. */
enum class TurnMilestone(val label: String, val alwaysShown: Boolean = false) {
    APPROVAL_NEEDED("Freigabe nötig", alwaysShown = true),
    TOOL_STARTED("Starte"),
    TOOL_VERIFIED("Geprüft"),
    WORKER_STARTED("Hintergrundauftrag gestartet"),
    WORKER_REPORTED("Hintergrundauftrag gemeldet", alwaysShown = true),
    BLOCKED("Blockiert", alwaysShown = true)
}

/**
 * Chief pattern point 1: one turn = quick acknowledgement → work → exactly one result → end.
 * Pure state machine (no Android), fed by the existing orchestrator eventObserver.
 * Progress lines are deduplicated and rate limited; after [deliver] the turn is closed and
 * every late event is dropped.
 */
class TurnLifecycle(
    val turnId: String,
    private val minMilestoneGapMillis: Long = 1_500,
    private val clock: () -> Long = System::currentTimeMillis
) {
    var phase = TurnPhase.IDLE; private set
    private val shown = HashSet<String>()
    private var lastShownAt = Long.MIN_VALUE

    @Synchronized
    fun acknowledge(): String? {
        if (phase != TurnPhase.IDLE) return null
        phase = TurnPhase.ACKNOWLEDGED
        return ACK_TEXT
    }

    @Synchronized
    fun milestone(kind: TurnMilestone, detail: String): String? {
        if (phase == TurnPhase.ENDED) return null
        if (phase == TurnPhase.IDLE) phase = TurnPhase.ACKNOWLEDGED
        phase = TurnPhase.WORKING
        val clean = detail.filterNot { it.isISOControl() }.trim().take(80)
        val key = "${kind.name}|$clean"
        if (key in shown) return null
        val now = clock()
        if (!kind.alwaysShown && lastShownAt != Long.MIN_VALUE && now - lastShownAt < minMilestoneGapMillis) return null
        shown += key
        lastShownAt = now
        return if (clean.isEmpty()) kind.label else "${kind.label}: $clean"
    }

    /** Returns true exactly once; the caller shows the final result only when true. */
    @Synchronized
    fun deliver(): Boolean {
        if (phase == TurnPhase.ENDED) return false
        phase = TurnPhase.ENDED
        return true
    }

    companion object {
        const val ACK_TEXT = "Verstanden – ich kümmere mich darum."
        fun milestoneOf(state: CognitiveWorkState): TurnMilestone? = when (state) {
            CognitiveWorkState.WAITING_FOR_APPROVAL -> TurnMilestone.APPROVAL_NEEDED
            CognitiveWorkState.EXECUTING -> TurnMilestone.TOOL_STARTED
            CognitiveWorkState.BLOCKED -> TurnMilestone.BLOCKED
            else -> null
        }
    }
}
