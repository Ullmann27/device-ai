package at.heinz.deviceai.ui

import at.heinz.deviceai.OwnerLaneOutcome
import at.heinz.deviceai.OwnerLaneResult
import org.json.JSONObject

/**
 * Code73 · the one place that decides which work scene the Command03 stage shows (`scene` of the snapshot), so that
 * the page never has two scenes fighting for the avatar's gaze. It reads only records the snapshot already carries –
 * `computerWork`, `deepClean`, `mailWork` (once the mail lane delivers it), `sendWork`, `activity`, `files`, `busy` –
 * and invents nothing: a scene exists only while its lane's record does.
 *
 * Rules:
 *  · a lane that is working (or waiting for the owner) wins over one that has ended; among working lanes the one
 *    already shown stays (no flip-flop), otherwise [LIVE_ORDER] decides – the approved computer task first, because
 *    it controls the screen;
 *  · an ended lane stays shown for [retainMillis] after it ended, then the stage is free again;
 *  · an ended record that was never shown (a run that finished between two snapshots) is shown once; one that was
 *    shown and dropped does not come back.
 *
 * `scene` is `{"kind": "computer"|"deepclean"|"mail"|"send"|"web"|"files", "since": elapsedRealtime ms}` or null.
 * "computer" and "files" name the existing work windows: the page draws no scene for them.
 */
internal class VisualWorkstationDirector(private val retainMillis: Long = RETAIN_MILLIS) {
    /** One lane's state in a snapshot: [live] = working or waiting for the owner; [signature] identifies the record. */
    internal data class Lane(val kind: String, val live: Boolean, val signature: String)

    private var kind: String? = null
    private var since = 0L
    private var signature = ""
    private var settledAt: Long? = null
    private val dropped = HashMap<String, String>()

    /** The scene for [snapshot] at [nowMillis] (elapsedRealtime), or null. */
    @Synchronized fun decide(snapshot: JSONObject, nowMillis: Long): JSONObject? {
        val lanes = lanes(snapshot)
        val live = LIVE_ORDER.mapNotNull { name -> lanes.firstOrNull { it.kind == name && it.live } }
        val current = lanes.firstOrNull { it.kind == kind }
        val chosen: Lane? = when {
            current != null && current.live -> current
            live.isNotEmpty() -> live.first()
            current != null && current.signature == signature -> {
                val ended = settledAt ?: nowMillis.also { settledAt = it }
                if (nowMillis - ended <= retainMillis) current else null
            }
            else -> LIVE_ORDER.mapNotNull { name -> lanes.firstOrNull { it.kind == name } }
                .firstOrNull { dropped[it.kind] != it.signature && it.kind in SHOWN_ENDED }
        }
        if (chosen == null) {
            kind?.let { dropped[it] = signature }
            kind = null; signature = ""; settledAt = null
            return null
        }
        if (chosen.kind != kind) { kind = chosen.kind; since = nowMillis }
        if (chosen.live) settledAt = null
        else if (chosen.signature != signature) settledAt = nowMillis
        signature = chosen.signature
        return JSONObject().put("kind", chosen.kind).put("since", since)
    }

    companion object {
        const val RETAIN_MILLIS = 10 * 60_000L
        /** Working lanes by precedence. */
        val LIVE_ORDER = listOf("computer", "deepclean", "mail", "send", "web", "files")
        /** Lanes whose ended record is worth a scene of its own (a result with figures or a delivery state). */
        private val SHOWN_ENDED = setOf("deepclean", "mail", "send")
        private val CLEAN_LIVE = setOf("SCAN", "CLASSIFY", "CLEAN", "CHOICE")
        private val CLEAN_ENDED = setOf("DONE", "STOPPED", "FAILED")
        private val MAIL_LIVE = setOf("CONNECT", "SEARCH", "READ", "SUMMARIZE", "DRAFT", "SEND")
        private val MAIL_ENDED = setOf("DONE", "FAILED")
        private val COMPUTER_ENDED = setOf("STOPPED", "ERROR", "COMPLETED")

        /** The lanes the snapshot reports, each only when its record is well-formed. */
        internal fun lanes(snapshot: JSONObject): List<Lane> {
            val lanes = ArrayList<Lane>()
            snapshot.optJSONObject("computerWork")?.let { work ->
                val consent = work.optBoolean("consentPending", false)
                val stage = work.optString("stage", "")
                val task = work.optString("taskId", "")
                if (consent || (task.isNotEmpty() && stage !in COMPUTER_ENDED)) lanes += Lane("computer", true, task)
            }
            snapshot.optJSONObject("deepClean")?.let { clean ->
                val phase = clean.optString("phase", "")
                val key = clean.optString("runId", "") + "|" + clean.optLong("updatedAtMs", 0)
                if (phase in CLEAN_LIVE) lanes += Lane("deepclean", true, key)
                else if (phase in CLEAN_ENDED) lanes += Lane("deepclean", false, key + "|" + phase)
            }
            snapshot.optJSONObject("mailWork")?.let { mail ->
                val phase = mail.optString("phase", "")
                val key = mail.optLong("revision", 0).toString()
                if (phase in MAIL_LIVE) lanes += Lane("mail", true, key)
                else if (phase in MAIL_ENDED) lanes += Lane("mail", false, key)
            }
            snapshot.optJSONObject("sendWork")?.let { send ->
                val phase = send.optString("phase", "")
                val key = send.optLong("at", 0).toString()
                if (phase in Command03SendWork.LIVE) lanes += Lane("send", true, key)
                else if (phase in Command03SendWork.ENDED) lanes += Lane("send", false, key + "|" + phase)
            }
            snapshot.optJSONObject("activity")?.let { activity ->
                if (activity.optString("kind") == "RESEARCH")
                    lanes += Lane("web", activity.optBoolean("active", false), activity.optLong("revision", 0).toString())
            }
            if (snapshot.optBoolean("busy", false))
                snapshot.optJSONObject("files")?.let { files -> lanes += Lane("files", true, files.optLong("revision", 0).toString()) }
            return lanes
        }
    }
}

/**
 * Code73 · the send lane's order as the stage shows it (`sendWork`): accepted, waiting for the owner, and how it
 * ended – exactly what the lane reported through its host ([OwnerLaneOutcome]); never a message text or a number.
 * `{"phase": "RUNNING"|"WAITING"|"SENT"|"DRAFT"|"HANDED_OVER"|"FAILED"|"STOPPED", "channel": string|null,
 *   "to": string|null, "detail": string|null, "at": ms}`.
 */
internal class Command03SendWork {
    private var session: String? = null
    private var phase: String? = null
    private var channel: String? = null
    private var to: String? = null
    private var detail: String? = null
    private var at = 0L

    /** The lane accepted an owner turn of [session]: its order runs. */
    @Synchronized fun started(session: String, nowMillis: Long) {
        if (this.session != session) { channel = null; to = null }
        this.session = session; phase = "RUNNING"; detail = null; at = nowMillis
    }

    /** The lane settled its order. Outcomes of other lanes' kinds are ignored. */
    @Synchronized fun settled(session: String, outcome: OwnerLaneOutcome, nowMillis: Long) {
        if (this.session != session) return
        when (outcome) {
            is OwnerLaneOutcome.NeedsOwner -> { phase = "WAITING"; detail = null }
            is OwnerLaneOutcome.Failed -> { phase = "FAILED"; detail = outcome.reason.take(160) }
            OwnerLaneOutcome.Stopped -> { phase = "STOPPED"; detail = null }
            is OwnerLaneOutcome.Done -> {
                val delivered = outcome.result as? OwnerLaneResult.Delivered ?: return
                phase = when (delivered.state) {
                    OwnerLaneResult.Delivered.State.SENT -> "SENT"
                    OwnerLaneResult.Delivered.State.DRAFT -> "DRAFT"
                    OwnerLaneResult.Delivered.State.HANDED_OVER -> "HANDED_OVER"
                }
                channel = delivered.channel.take(24); to = delivered.to.take(60); detail = null
            }
        }
        at = nowMillis
    }

    @Synchronized fun snapshot(session: String): JSONObject? {
        val current = phase ?: return null
        if (this.session != session) return null
        return JSONObject().put("phase", current).put("channel", channel ?: JSONObject.NULL).put("to", to ?: JSONObject.NULL)
            .put("detail", detail ?: JSONObject.NULL).put("at", at)
    }

    companion object {
        val LIVE = setOf("RUNNING", "WAITING")
        val ENDED = setOf("SENT", "DRAFT", "HANDED_OVER", "FAILED", "STOPPED")
    }
}
