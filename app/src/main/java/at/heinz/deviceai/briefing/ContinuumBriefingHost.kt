package at.heinz.deviceai.briefing

import at.heinz.deviceai.cognitive.ApprovedWebResearchSession
import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.cognitive.WebToolRequest
import at.heinz.deviceai.sending.OwnerSendContactSource
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** One open incoming message as another part of the app lists it („Offene Nachrichten“). */
internal data class ContinuumOpenMessage(
    /** Who it is from, as shown to the owner. */
    val sender: String,
    /** The app it arrived in („WhatsApp“), or null. */
    val app: String? = null,
    /** An order an existing lane takes to open or read it; null = the app cannot do that, so nothing is offered. */
    val order: String? = null
)

/** The list of open incoming messages. The default knows none – the list is built elsewhere. */
internal fun interface ContinuumOpenMessages {
    /** null = no list is wired (nothing is said about messages); an empty list = wired and nothing open. */
    fun open(): List<ContinuumOpenMessage>?

    companion object { val NONE = ContinuumOpenMessages { null } }
}

/** App-private key/value storage for the owner's briefing settings. Fakes in host tests. */
internal interface ContinuumBriefingStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

/** The owner's briefing settings and the small state that has to survive a restart. */
internal class ContinuumBriefingSettings(private val store: ContinuumBriefingStore) {
    /** The owner's place for the weather, as he said it. Setting it forgets the looked-up coordinates. */
    var place: String?
        get() = store.get(PLACE)?.takeIf { it.isNotBlank() }
        set(value) { store.put(PLACE, value?.trim()?.takeIf { it.isNotEmpty() }); store.put(PLACE_FOUND, null) }

    /** The coordinates the weather service returned for [place]; only valid for the place they were looked up for. */
    var placeFound: ContinuumWeatherPlace?
        get() = runCatching {
            val row = JSONObject(store.get(PLACE_FOUND) ?: return null)
            if (row.getString("for") != place) return null
            ContinuumWeatherPlace(row.getString("name"), row.getString("label"), row.getDouble("lat"), row.getDouble("lon"))
        }.getOrNull()
        set(value) {
            store.put(PLACE_FOUND, value?.let {
                JSONObject().put("for", place).put("name", it.name).put("label", it.label).put("lat", it.latitude)
                    .put("lon", it.longitude).toString()
            })
        }

    var offerEnabled: Boolean
        get() = store.get(OFFER) != "off"
        set(value) = store.put(OFFER, if (value) null else "off")

    /** Hour of day from which the daily offer is made. */
    var offerFromHour: Int
        get() = store.get(OFFER_HOUR)?.toIntOrNull()?.takeIf { it in 0..23 } ?: DEFAULT_OFFER_HOUR
        set(value) = store.put(OFFER_HOUR, value.coerceIn(0, 23).toString())

    var lastOfferDay: LocalDate?
        get() = store.get(LAST_OFFER)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        set(value) = store.put(LAST_OFFER, value?.toString())

    var lastBriefingDay: LocalDate?
        get() = store.get(LAST_BRIEFING)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        set(value) = store.put(LAST_BRIEFING, value?.toString())

    /** The standing news part of the briefing; off unless the owner sets it. */
    var newsPart: Boolean
        get() = store.get(NEWS) == "on"
        set(value) = store.put(NEWS, if (value) "on" else null)

    /** The calendar permission was explained and asked for once. */
    var calendarAsked: Boolean
        get() = store.get(CALENDAR_ASKED) == "yes"
        set(value) = store.put(CALENDAR_ASKED, if (value) "yes" else null)

    /** Files this app saved for the owner and has not handed on since: name to the time of saving. */
    var savedUnsent: List<Pair<String, Long>>
        get() = runCatching {
            val rows = JSONArray(store.get(UNSENT) ?: return emptyList())
            (0 until rows.length()).map { rows.getJSONObject(it).let { row -> row.getString("name") to row.getLong("at") } }
        }.getOrDefault(emptyList())
        set(value) = store.put(UNSENT, value.takeLast(MAX_UNSENT).takeIf { it.isNotEmpty() }?.let { rows ->
            JSONArray().apply { rows.forEach { put(JSONObject().put("name", it.first.take(120)).put("at", it.second)) } }.toString()
        })

    companion object {
        const val DEFAULT_OFFER_HOUR = 5
        const val MAX_UNSENT = 6
        private const val PLACE = "place"
        private const val PLACE_FOUND = "place_found"
        private const val OFFER = "offer"
        private const val OFFER_HOUR = "offer_hour"
        private const val LAST_OFFER = "last_offer_day"
        private const val LAST_BRIEFING = "last_briefing_day"
        private const val NEWS = "news_part"
        private const val CALENDAR_ASKED = "calendar_asked"
        private const val UNSENT = "saved_unsent"
    }
}

/**
 * The small surface the briefing lane needs from the Activity. It keeps [at.heinz.deviceai.AgentActivity] to thin
 * hooks: every method maps to an existing path (owner turn, non-model reply, web consent, single busy lane, contact
 * source of the send lane) or to one read-only Android boundary (calendar, dialer, device state).
 */
internal interface ContinuumBriefingHost {
    val sessionId: String
    /** Number of owner turns shown so far in this chat; unchanged means „the very next turn“. */
    val ownerTurn: Long
    val assistantName: String?
    /** How the owner is addressed („Heinz“), or null. */
    fun ownerName(): String?

    /** Takes the text out of the input field and shows it as the owner's turn. */
    fun acceptOwnerTurn(request: String)
    /** Takes the text out of the input field without showing it (an order is dispatched in its place). */
    fun clearInput()
    /**
     * An answer that did not come from the model: [shown] in the chat, [spoken] aloud ([spoken] is the start of
     * [shown] or equal to it). [request]: the owner turn it answers; null when nothing was asked (offered briefing,
     * warning).
     */
    fun answer(request: String?, spoken: String, shown: String = spoken)
    fun activity(line: String)
    /** true when no job, approval or mission is open. */
    fun idle(): Boolean
    /** true while the app speaks or listens. */
    fun speaking(): Boolean = false
    /** Runs [action] on the UI thread after everything that is being handled right now has finished. */
    fun later(action: () -> Unit)
    /** Runs [action] once the answer just given has been spoken – at once when nothing is spoken, never later than a bounded wait. */
    fun afterSpeech(action: () -> Unit) = later(action)
    /** Dispatches [order] through the owner input path as if the owner had said it. */
    fun submit(order: String)

    /** The app and device state right now. */
    fun systemFacts(): ContinuumSystemFacts
    /** true when the app really runs with reduced load because of the heat reported in [facts]. */
    fun loadReducedByHeat(facts: ContinuumSystemFacts): Boolean = false
    /** true while a job that uses the internet runs. */
    fun webJobRunning(): Boolean = false

    fun calendar(): ContinuumCalendarSource
    /** Explains and asks for READ_CALENDAR; [done] with the grant. */
    fun requestCalendar(explanation: String, done: (Boolean) -> Unit)

    /** The owner's stored internet setting (restored before it is read). */
    fun internetMode(): InternetAccessMode
    /** The existing web consent: ASK dialog, ALWAYS_ON grant; null = not granted (the notice is already shown). */
    fun confirmWeb(call: WebToolRequest, explanation: String?, notGranted: String?,
                   completed: (ApprovedWebResearchSession?) -> Unit)
    /**
     * Reads the weather under [session] off the UI thread as the single busy job. [done] is always called on the UI
     * thread: the read, or a failure when the job could not start, was stopped, or its chat is no longer open.
     */
    fun readWeather(session: ApprovedWebResearchSession, request: String, weather: ContinuumWeatherRequest,
                    done: (Result<ContinuumWeatherRead>) -> Unit)
    /** Runs short local [work] off the UI thread as the single busy job; [done] is always called on the UI thread. */
    fun <T> runJob(request: String, label: String, work: () -> T, done: (Result<T>) -> Unit)

    /** Open next steps of the work journal („Quelle: Schritt“), newest first. */
    fun openWork(): List<String> = emptyList()
    fun openMessages(): ContinuumOpenMessages = ContinuumOpenMessages.NONE

    fun contactsReadable(): Boolean
    /** Explains and asks for READ_CONTACTS (the send lane's path); [done] with the grant. */
    fun requestContacts(explanation: String, done: (Boolean) -> Unit)
    fun contacts(): OwnerSendContactSource
    /** Opens the dialer with [number] filled in; null = started, otherwise the reason. Never places a call. */
    fun dial(number: String): String?

    fun store(): ContinuumBriefingStore
}
