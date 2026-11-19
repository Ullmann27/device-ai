package at.heinz.deviceai

/**
 * One rule for the owner's next turn: a question an owner lane has just asked gets the answer first, before any
 * lane that reads a whole sentence on its own (the conversation manner, the mission planner, the desk, the send lane,
 * the briefing, the computer and the work tools). Field finding of the review of 19.11.2026: after „Was soll ich Papa
 * schreiben?“ the answer „Guten Morgen“ was taken as a greeting and the send lane's question was lost.
 *
 * A lane is waiting when it says so itself ([Lane.awaiting]) or when it reported [OwnerLaneOutcome.NeedsOwner] in
 * this chat and no owner turn was accepted since. The lane's own entry decides whether the turn is its answer; when it
 * is not, the turn keeps its ordinary route. Free of Android.
 */
internal class OwnerOpenQuestions(private val session: () -> String, private val turn: () -> Long) {
    private class Lane(val name: String, val awaiting: () -> Boolean, val answer: (String) -> Boolean)
    private class Asked(val lane: String, val session: String, val turn: Long)

    private val lanes = ArrayList<Lane>()
    private var asked: Asked? = null

    /** Lanes are asked in the order they are registered. [answer] is the lane's owner entry; true = it took the turn. */
    fun lane(name: String, awaiting: () -> Boolean = { false }, answer: (String) -> Boolean): OwnerOpenQuestions {
        require(lanes.none { it.name == name }) { "lane $name registered twice" }
        lanes += Lane(name, awaiting, answer)
        return this
    }

    /** Every typed outcome of a lane: a question marks it as waiting for exactly the next owner turn. */
    fun noteOutcome(lane: String, outcome: OwnerLaneOutcome) {
        asked = when {
            outcome is OwnerLaneOutcome.NeedsOwner -> Asked(lane, session(), turn())
            asked?.lane == lane -> null
            else -> asked
        }
    }

    /** The lane that waits for the owner's next turn, or null. */
    fun waiting(): String? = waitingLane()?.name

    private fun waitingLane(): Lane? {
        val reported = asked?.takeIf { it.session == session() && it.turn == turn() }?.lane
        return lanes.firstOrNull { it.awaiting() || it.name == reported }
    }

    /** true = the waiting lane took [request] as its answer. Call before the whole-sentence lanes. */
    fun serve(request: String): Boolean {
        val lane = waitingLane() ?: return false
        if (asked?.lane == lane.name) asked = null
        return lane.answer(request)
    }
}
