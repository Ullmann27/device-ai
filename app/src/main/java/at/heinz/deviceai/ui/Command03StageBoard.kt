package at.heinz.deviceai.ui

import org.json.JSONArray
import org.json.JSONObject

/**
 * The two lists of the Command03 snapshot that the stage only shows and never computes:
 *
 *  - "systemCheck": the start-up checks, `[{key, label, state: "OK"|"WARN"|"FAIL"|"OFF", detail}]`. The stage ticks
 *    them in once when it first appears and says "alles bereit" only when every item is OK.
 *  - "briefingCards": the info cards, `[{id, kind: "calendar"|"weather"|"messages"|"tasks"|"news"|"system", title,
 *    lines: [at most 4 strings], state: "info"|"attention"}]`. A card flies in when it enters the list and leaves
 *    when it is gone from it.
 *
 * Both are empty until a provider hands real items over; an empty list renders nothing. This class holds what was
 * handed over, bounded and as plain text, and invents nothing: an item without a usable key/label/title is dropped,
 * a duplicate key keeps its first item, an unknown state or kind is dropped rather than guessed.
 */
class Command03StageBoard {
    enum class CheckState { OK, WARN, FAIL, OFF }
    data class Check(val key: String, val label: String, val state: CheckState, val detail: String = "")

    enum class CardKind(val wire: String) {
        CALENDAR("calendar"), WEATHER("weather"), MESSAGES("messages"), TASKS("tasks"), NEWS("news"), SYSTEM("system")
    }
    data class Card(val id: String, val kind: CardKind, val title: String, val lines: List<String> = emptyList(),
        val attention: Boolean = false)

    private var checks: List<Check> = emptyList()
    private var cards: List<Card> = emptyList()

    /** @return true when the shown checks changed and the page should receive a new snapshot. */
    @Synchronized fun setSystemCheck(items: List<Check>): Boolean {
        val next = items.asSequence()
            .map { Check(it.key.trim(), plain(it.label, MAX_CHECK_LABEL), it.state, plain(it.detail, MAX_CHECK_DETAIL)) }
            .filter { KEY.matches(it.key) && it.label.isNotEmpty() }
            .distinctBy { it.key }.take(MAX_CHECKS).toList()
        if (next == checks) return false
        checks = next
        return true
    }

    /** @return true when the shown cards changed and the page should receive a new snapshot. */
    @Synchronized fun setBriefingCards(items: List<Card>): Boolean {
        val next = items.asSequence()
            .map { card ->
                card.copy(id = card.id.trim(), title = plain(card.title, MAX_CARD_TITLE),
                    lines = card.lines.map { plain(it, MAX_CARD_LINE) }.filter { it.isNotEmpty() }.take(MAX_CARD_LINES))
            }
            .filter { KEY.matches(it.id) && it.title.isNotEmpty() }
            .distinctBy { it.id }.take(MAX_CARDS).toList()
        if (next == cards) return false
        cards = next
        return true
    }

    /** The "systemCheck" field of the snapshot; an empty array until checks were handed over. */
    @Synchronized fun systemCheck(): JSONArray = JSONArray().also { list ->
        checks.forEach { check ->
            list.put(JSONObject().put("key", check.key).put("label", check.label).put("state", check.state.name)
                .put("detail", check.detail))
        }
    }

    /** The "briefingCards" field of the snapshot; an empty array until cards were handed over. */
    @Synchronized fun briefingCards(): JSONArray = JSONArray().also { list ->
        cards.forEach { card ->
            val lines = JSONArray()
            card.lines.forEach(lines::put)
            list.put(JSONObject().put("id", card.id).put("kind", card.kind.wire).put("title", card.title)
                .put("lines", lines).put("state", if (card.attention) "attention" else "info"))
        }
    }

    private fun plain(text: String, max: Int): String {
        val clean = text.filter { !it.isISOControl() }.trim()
        if (clean.length <= max) return clean
        var end = max - 1
        if (end > 0 && clean[end - 1].isHighSurrogate()) end--
        return clean.substring(0, end) + "…"
    }

    companion object {
        const val MAX_CHECKS = 12
        const val MAX_CHECK_LABEL = 40
        const val MAX_CHECK_DETAIL = 120
        const val MAX_CARDS = 6
        const val MAX_CARD_TITLE = 60
        const val MAX_CARD_LINES = 4
        const val MAX_CARD_LINE = 90
        private val KEY = Regex("[A-Za-z0-9._:-]{1,64}")

        /**
         * Reads checks in the snapshot's own shape (`[{key, label, state, detail}]`), for a provider that already
         * produces JSON. Items that are not objects or carry an unknown state are left out.
         */
        fun checksOf(json: JSONArray?): List<Check> = (0 until (json?.length() ?: 0)).mapNotNull { index ->
            val item = json?.optJSONObject(index) ?: return@mapNotNull null
            val state = CheckState.values().firstOrNull { it.name == item.optString("state") } ?: return@mapNotNull null
            Check(item.optString("key"), item.optString("label"), state, item.optString("detail"))
        }

        /** Reads cards in the snapshot's own shape (`[{id, kind, title, lines, state}]`). */
        fun cardsOf(json: JSONArray?): List<Card> = (0 until (json?.length() ?: 0)).mapNotNull { index ->
            val item = json?.optJSONObject(index) ?: return@mapNotNull null
            val kind = CardKind.values().firstOrNull { it.wire == item.optString("kind") } ?: return@mapNotNull null
            val state = item.optString("state")
            if (state != "info" && state != "attention") return@mapNotNull null
            val lines = item.optJSONArray("lines")
            Card(item.optString("id"), kind, item.optString("title"),
                (0 until (lines?.length() ?: 0)).mapNotNull { lines?.opt(it) as? String }, state == "attention")
        }
    }
}
