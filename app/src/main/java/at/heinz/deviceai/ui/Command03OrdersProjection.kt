package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.CockpitAnimationRenderer
import at.heinz.deviceai.cockpit.MissionOrderState
import at.heinz.deviceai.cockpit.MissionOrdersEvent
import at.heinz.deviceai.cockpit.StepState
import org.json.JSONArray
import org.json.JSONObject

/**
 * Projects the real current work into the "orders" record of the Command03 snapshot: the order rail of the stage.
 *
 * Two sources exist and only one is shown at a time:
 *  - MISSION: the step overview an owner mission publishes about itself ([MissionOrdersEvent]). While a mission is
 *    open, the timelines of the lanes it hands its steps to are NOT orders of the rail – they stay the detailed
 *    activity of the work window ([Command03ActivityProjection]).
 *  - TURN: the steps of the owner's running turn, as its timeline reports them, under the command the turn started
 *    with. The acknowledgement itself is not a step of work and is left out.
 *
 * Until Code70 both emitters wrote into the one activity record, so the mission overview and a lane's own timeline
 * replaced each other ("latest emitter wins"). Now each has its own place.
 *
 * Nothing is synthesised: no event means no orders, an order appears when its step was really started, and its
 * state is the one the emitter reported. Turn ids never leave the app; the page sees an opaque [key] per source.
 */
class Command03OrdersProjection(private val session: () -> String) : CockpitAnimationRenderer {
    private class Item(val key: String, val number: Int, val label: String, val state: String)

    private var revision = 0L
    private var serial = 0L
    private var source: String? = null
    private var id: String? = null
    private var key = ""
    private var owner = ""
    private var title = ""
    private var total = 0
    private var active = false
    private var outcome: String? = null
    private var items: List<Item> = emptyList()
    private var signature = ""
    private val previews = LinkedHashMap<String, String>()
    private val seenTurns = LinkedHashSet<String>()
    private val laneTurns = LinkedHashSet<String>()
    /** Step key → position among the steps the running turn reported so far (1-based). */
    private val ordinals = LinkedHashMap<String, Int>()

    @Synchronized override fun render(event: CockpitAnimationEvent) {
        when (event) {
            is MissionOrdersEvent -> {
                if (source != MISSION || id != event.turnId) begin(MISSION, event.turnId, "")
                val all = event.orders.filter { it.index >= 0 && it.index < event.total }.map { order ->
                    Item("m" + order.index, order.index + 1, bounded(order.label), when (order.state) {
                        MissionOrderState.RUNNING -> "ACTIVE"
                        else -> order.state.name
                    })
                }
                // A mission that publishes its overview is open (again): a correction reopens a completed one.
                publish(all.takeLast(MAX_ITEMS), event.total.coerceAtLeast(all.size), open = true, result = null)
            }
            is CockpitAnimationEvent.CommandFlyIn -> {
                previews[event.turnId] = bounded(event.commandPreview)
                trim(previews.keys, MAX_PREVIEWS)
            }
            is CockpitAnimationEvent.ProcessTimeline -> turn(event)
            is CockpitAnimationEvent.TurnEnded -> if (event.turnId == id && outcome == null) {
                publish(items, total, open = false, result = when {
                    event.delivered -> "COMPLETED"
                    items.any { it.state == "FAILED" } -> "FAILED"
                    items.any { it.state == "STOPPED" } -> "STOPPED"
                    else -> "ENDED"
                })
            }
            else -> Unit
        }
    }

    private fun turn(event: CockpitAnimationEvent.ProcessTimeline) {
        // The mission's own overview arrives as MissionOrdersEvent; a background worker is not an owner order.
        if (MissionOrdersEvent.isOverview(event) || event.steps.firstOrNull()?.key == WORKER_KEY) return
        val first = seenTurns.add(event.turnId)
        trim(seenTurns, MAX_TURNS)
        if (source == MISSION && active) {
            // A lane working on a step of the open mission: its timeline belongs to the work window.
            laneTurns += event.turnId
            trim(laneTurns, MAX_TURNS)
            return
        }
        if (event.turnId in laneTurns) return
        if (source != TURN || id != event.turnId) {
            // Only a turn that starts now takes the rail; a late report of an earlier turn does not return to it.
            if (!first) return
            begin(TURN, event.turnId, previews[event.turnId].orEmpty())
        }
        // The timeline keeps only its newest steps; an order keeps the position it had when it was first reported.
        val steps = event.steps.filter { it.key != ACK_KEY }
        for (step in steps) if (step.key !in ordinals) ordinals[step.key] = ordinals.size + 1
        val all = steps.map { step -> Item("t" + step.key, ordinals.getValue(step.key), bounded(step.label), step.state.name) }
        publish(all.takeLast(MAX_ITEMS), ordinals.size,
            open = steps.any { it.state == StepState.ACTIVE || it.state == StepState.WAITING }, result = null)
    }

    /** Null until a real order of [sessionId] exists. */
    @Synchronized fun snapshot(sessionId: String): JSONObject? {
        val current = source ?: return null
        if (owner != sessionId) return null
        val list = JSONArray()
        items.forEach { item ->
            list.put(JSONObject().put("key", item.key).put("number", item.number).put("label", item.label)
                .put("state", item.state))
        }
        return JSONObject().put("revision", revision).put("source", current).put("key", key)
            .put("title", title).put("total", total).put("active", active)
            .put("outcome", outcome ?: JSONObject.NULL).put("items", list)
    }

    @Synchronized fun clear() {
        if (source == null) return
        source = null; id = null; key = ""; owner = ""; title = ""; total = 0; active = false; outcome = null
        items = emptyList(); signature = ""; ordinals.clear(); revision++
    }

    private fun begin(newSource: String, turnId: String, newTitle: String) {
        source = newSource
        id = turnId
        key = (if (newSource == MISSION) "m" else "t") + (++serial)
        owner = session()
        title = newTitle
        items = emptyList(); total = 0; active = false; outcome = null; signature = ""
        ordinals.clear()
    }

    /** One revision per real change; a repeated report of the same state changes nothing. */
    private fun publish(next: List<Item>, count: Int, open: Boolean, result: String?) {
        val now = key + "|" + count + "|" + open + "|" + result + "|" +
            next.joinToString("\u0001") { it.key + "\u0002" + it.number + "\u0002" + it.label + "\u0002" + it.state }
        items = next; total = count; active = open; outcome = result
        if (now == signature) return
        signature = now
        revision++
    }

    private fun trim(keys: MutableSet<String>, max: Int) {
        val iterator = keys.iterator()
        while (keys.size > max && iterator.hasNext()) { iterator.next(); iterator.remove() }
    }

    private fun bounded(text: String): String {
        val clean = text.filter { !it.isISOControl() }.trim()
        if (clean.length <= MAX_LABEL) return clean
        var end = MAX_LABEL - 1
        if (end > 0 && clean[end - 1].isHighSurrogate()) end--
        return clean.substring(0, end) + "…"
    }

    companion object {
        const val MISSION = "MISSION"
        const val TURN = "TURN"
        const val MAX_ITEMS = 8
        const val MAX_LABEL = 80
        private const val ACK_KEY = "ack"
        private const val WORKER_KEY = "worker"
        private const val MAX_PREVIEWS = 4
        private const val MAX_TURNS = 64
    }
}

/**
 * The readouts of the stage's status strip. Every value is one the app already holds: the selected brain, the
 * owner's internet mode, PowerManager's thermal status and the battery reading of the dashboard sample. A value
 * the platform did not report is null and the page then shows no figure – never a default.
 */
object Command03TacticalStrip {
    private val internetModes = setOf("OFF", "ASK", "ALWAYS_ON")

    fun snapshot(claudeBrain: Boolean, internetMode: String?, thermalStatus: Int?, batteryPercent: Int?,
                 charging: Boolean?): JSONObject = JSONObject()
        .put("brain", if (claudeBrain) "CLAUDE" else "LOCAL")
        .put("internet", internetMode?.takeIf { it in internetModes } ?: JSONObject.NULL)
        .put("thermal", thermalStatus?.takeIf { it in 0..6 } ?: JSONObject.NULL)
        .put("battery", batteryPercent?.takeIf { it in 0..100 } ?: JSONObject.NULL)
        .put("charging", charging ?: JSONObject.NULL)
}
