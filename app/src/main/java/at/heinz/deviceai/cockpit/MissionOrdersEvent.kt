package at.heinz.deviceai.cockpit

/**
 * The owner mission's own step overview with the exact state of every step that was really handed over.
 *
 * The mission also publishes the same fact as a [CockpitAnimationEvent.ProcessTimeline], whose [StepState] has no
 * word for "stopped" or "skipped". The order rail of the Command03 stage names these outcomes, so it needs them as
 * they are. Unknown [CockpitAnimationEvent.Extension] kinds are ignored by every other renderer by design.
 *
 * [total] is the number of steps of the plan; [orders] holds only steps that have started (oldest first).
 */
class MissionOrdersEvent(
    override val turnId: String,
    override val atMillis: Long,
    val total: Int,
    val orders: List<MissionOrder>,
) : CockpitAnimationEvent.Extension(KIND) {
    companion object {
        const val KIND = "mission-orders-v1"
        const val STEP_KEY_PREFIX = "mission:"

        /** true for the timeline a mission publishes about itself (never for a lane's own timeline). */
        fun isOverview(event: CockpitAnimationEvent.ProcessTimeline): Boolean =
            event.steps.isNotEmpty() && event.steps.all { it.key.startsWith(STEP_KEY_PREFIX) }
    }
}

/** One step of a mission: its position in the plan (0-based), its plain label and how it stands. */
data class MissionOrder(val index: Int, val label: String, val state: MissionOrderState)

enum class MissionOrderState { RUNNING, WAITING, DONE, FAILED, STOPPED, SKIPPED }
