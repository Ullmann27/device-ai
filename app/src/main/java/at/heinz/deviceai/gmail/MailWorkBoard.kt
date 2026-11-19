package at.heinz.deviceai.gmail

import org.json.JSONArray
import org.json.JSONObject

/**
 * The `mailWork` record of the Command03 snapshot (data only; the stage owns the visuals). It is set by the mail lane
 * from real events: a phase starts when its API call starts, counts and progress move only when a message really
 * arrived, and the result line is the lane's own reply sentence. Bound to the chat it was made in; never stored.
 *
 * Schema (version 1):
 * ```
 * mailWork: null | {
 *   "version": 1,
 *   "revision": <long, grows with every change>,
 *   "phase": "CONNECT" | "SEARCH" | "READ" | "SUMMARIZE" | "DRAFT" | "SEND" | "DONE" | "FAILED",
 *   "query": <string ≤ 120: the search as the lane built it, e.g. „von Anna · ungelesen“> | null,
 *   "found": <int ≥ 0: messages Gmail listed> | null,
 *   "read": <int ≥ 0: messages whose headers or text really arrived>,
 *   "progress": { "done": <int>, "total": <int> } | null,
 *   "cards": [ { "index": <1-based>, "sender": <≤ 80>, "subject": <≤ 120>, "dateMillis": <epoch ms>,
 *                "state": "UNREAD" | "READ" | "SELECTED" } ]  (at most MAX_CARDS),
 *   "result": <string ≤ 200: the lane's own result sentence> | null,
 *   "updatedAtMillis": <epoch ms>
 * }
 * ```
 * Never in it: a token, an address, a mail body, a snippet or an attachment.
 */
internal class MailWorkBoard(private val now: () -> Long = System::currentTimeMillis) {
    enum class Phase { CONNECT, SEARCH, READ, SUMMARIZE, DRAFT, SEND, DONE, FAILED }
    enum class CardState { UNREAD, READ, SELECTED }
    data class Card(val index: Int, val sender: String, val subject: String, val dateMillis: Long, val state: CardState)

    private var session: String? = null
    private var phase: Phase? = null
    private var query: String? = null
    private var found: Int? = null
    private var read = 0
    private var progressDone = 0
    private var progressTotal = 0
    private var cards: List<Card> = emptyList()
    private var result: String? = null
    private var revision = 0L
    private var updatedAt = 0L

    @Synchronized fun start(session: String, phase: Phase, query: String? = null) {
        if (this.session != session || phase == Phase.SEARCH || phase == Phase.CONNECT) {
            cards = emptyList(); found = null; read = 0
        }
        this.session = session
        this.phase = phase
        this.query = query?.let { bounded(it, MAX_QUERY) } ?: if (phase == Phase.SEARCH) null else this.query
        progressDone = 0; progressTotal = 0; result = null
        touch()
    }

    /** Gmail listed [count] messages for the running search. */
    @Synchronized fun found(session: String, count: Int) {
        if (session != this.session) return
        found = count.coerceAtLeast(0); progressTotal = found ?: 0; progressDone = 0
        touch()
    }

    /** One message really arrived (headers or text); [total] the number this step fetches. */
    @Synchronized fun arrived(session: String, total: Int) {
        if (session != this.session) return
        read++
        progressTotal = total.coerceAtLeast(0)
        progressDone = (progressDone + 1).coerceAtMost(progressTotal.coerceAtLeast(1))
        touch()
    }

    @Synchronized fun cards(session: String, messages: List<MailRef>, unread: Set<String>, selected: String?) {
        if (session != this.session) return
        cards = messages.take(MAX_CARDS).mapIndexed { index, ref ->
            Card(index + 1, bounded(ref.from, MAX_SENDER), bounded(ref.subject, MAX_SUBJECT), ref.dateMillis, when (ref.id) {
                selected -> CardState.SELECTED
                in unread -> CardState.UNREAD
                else -> CardState.READ
            })
        }
        touch()
    }

    @Synchronized fun select(session: String, id: String?, refs: List<MailRef>) {
        if (session != this.session) return
        cards = cards.map { card ->
            val ref = refs.getOrNull(card.index - 1)
            when {
                ref != null && ref.id == id -> card.copy(state = CardState.SELECTED)
                card.state == CardState.SELECTED -> card.copy(state = CardState.READ)
                else -> card
            }
        }
        touch()
    }

    @Synchronized fun finish(session: String, ok: Boolean, line: String) {
        if (session != this.session) return
        phase = if (ok) Phase.DONE else Phase.FAILED
        result = bounded(line, MAX_RESULT)
        touch()
    }

    @Synchronized fun clear() {
        session = null; phase = null; query = null; found = null; read = 0; progressDone = 0; progressTotal = 0
        cards = emptyList(); result = null
        touch()
    }

    @Synchronized fun snapshot(session: String): JSONObject? {
        val current = phase ?: return null
        if (session != this.session) return null
        val list = JSONArray()
        cards.forEach { card ->
            list.put(JSONObject().put("index", card.index).put("sender", card.sender).put("subject", card.subject)
                .put("dateMillis", card.dateMillis).put("state", card.state.name))
        }
        return JSONObject().put("version", 1).put("revision", revision).put("phase", current.name)
            .put("query", query ?: JSONObject.NULL).put("found", found ?: JSONObject.NULL).put("read", read)
            .put("progress", if (progressTotal > 0) JSONObject().put("done", progressDone).put("total", progressTotal) else JSONObject.NULL)
            .put("cards", list).put("result", result ?: JSONObject.NULL).put("updatedAtMillis", updatedAt)
    }

    @get:Synchronized val currentPhase: Phase? get() = phase

    private fun touch() { revision++; updatedAt = now() }

    private fun bounded(text: String, max: Int): String {
        val clean = text.filter { !it.isISOControl() }.replace(Regex("\\s+"), " ").trim()
        if (clean.length <= max) return clean
        var end = max - 1
        if (end > 0 && clean[end - 1].isHighSurrogate()) end--
        return clean.substring(0, end) + "…"
    }

    companion object {
        const val MAX_CARDS = 6
        const val MAX_QUERY = 120
        const val MAX_SENDER = 80
        const val MAX_SUBJECT = 120
        const val MAX_RESULT = 200
    }
}
