package at.heinz.deviceai.briefing

import at.heinz.deviceai.cognitive.ApprovedWebResearchSession
import at.heinz.deviceai.cognitive.ApprovedWebToolGate
import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.cognitive.InternetConsentPolicy
import at.heinz.deviceai.cognitive.ResearchMode
import at.heinz.deviceai.cognitive.WebToolRequest
import at.heinz.deviceai.sending.OwnerContactMail
import at.heinz.deviceai.sending.OwnerContactPhone
import at.heinz.deviceai.sending.OwnerSendContactSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.CancellationException

/**
 * The Activity as the briefing lane sees it, for host tests: owner turns, answers (shown and spoken), the web
 * consent with its notice, the busy lane, calendar, contacts, dialer and settings. The director, the parsers and
 * all wording under test are the productive ones.
 */
internal class ContinuumBriefingHarness(
    var clock: Instant = moment(LocalDate.of(2026, 11, 19), 7, 0)
) : ContinuumBriefingHost {
    companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Vienna")
        val TODAY: LocalDate = LocalDate.of(2026, 11, 19)
        const val GB = 1_000_000_000L
        fun moment(date: LocalDate, hour: Int, minute: Int = 0): Instant =
            LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(ZONE).toInstant()
        fun event(title: String, hour: Int, minute: Int = 0, lengthMinutes: Int = 60, date: LocalDate = TODAY, location: String? = null) =
            moment(date, hour, minute).toEpochMilli().let { ContinuumCalendarEvent(title, it, it + lengthMinutes * 60_000L, false, location) }
        val LINZ = ContinuumWeatherPlace("Linz", "Linz, Oberösterreich, Österreich", 48.30639, 14.28611)
        val LINZ_REPORT = ContinuumWeatherReport(LINZ, 8.5, 1, listOf(
            ContinuumWeatherDay(TODAY, 80, 11.6, 6.2, 78), ContinuumWeatherDay(TODAY.plusDays(1), 80, 11.9, 8.0, 65)))
        fun fine() = ContinuumSystemFacts(
            brain = ContinuumSystemFacts.Brain.Local(ContinuumSystemFacts.Brain.State.READY, "gemma-3n-e4b-it"),
            speechInputAvailable = true, microphoneGranted = true, speechOutputEnabled = true, speechOutputReady = true,
            internetMode = InternetAccessMode.ASK, networkOnline = true, allFilesAccess = true, accessibilityOn = true,
            shizukuReady = true, batteryPercent = 82, charging = false, thermalStatus = 0,
            storageFreeBytes = 38 * GB, storageTotalBytes = 256 * GB)
    }

    val director = ContinuumBriefingDirector(this, now = { clock }, zone = { ZONE })

    override var sessionId = "chat-1"
    override var ownerTurn = 0L
    override val assistantName: String? = "Jarvis"
    var owner: String? = "Heinz"
    override fun ownerName(): String? = owner

    /** What the owner and the app said, in order: „> …“ is an owner turn. */
    val chat = mutableListOf<String>()
    val spoken = mutableListOf<String>()
    val shown = mutableListOf<String>()
    val activities = mutableListOf<String>()
    val submitted = mutableListOf<String>()
    var inputCleared = 0
    var busy = false
    var speakingNow = false
    private val queue = ArrayDeque<() -> Unit>()

    /** The owner's initiative policy as the Activity wires it to this lane; null = the lane alone decides (the host default). */
    var initiative: at.heinz.deviceai.cognitive.OwnerInitiative? = null
    var briefingsGiven = 0
    override fun allowDailyOffer(): Boolean = initiative?.allows(at.heinz.deviceai.cognitive.OwnerSuggestionKind.BRIEFING) ?: true
    override fun allowOffers(offers: List<ContinuumBriefingBoard.Offer>): List<ContinuumBriefingBoard.Offer> {
        val policy = initiative ?: return offers
        if (offers.isEmpty() || !policy.allows(at.heinz.deviceai.cognitive.OwnerSuggestionKind.NEXT_STEPS)) return emptyList()
        policy.offered(at.heinz.deviceai.cognitive.OwnerSuggestionKind.NEXT_STEPS)
        return offers
    }
    override fun offerAnswered(daily: Boolean, accepted: Boolean) {
        val kind = if (daily) at.heinz.deviceai.cognitive.OwnerSuggestionKind.BRIEFING else at.heinz.deviceai.cognitive.OwnerSuggestionKind.NEXT_STEPS
        initiative?.let { if (accepted) it.accepted(kind) else it.declined(kind) }
    }
    override fun briefingGiven() { briefingsGiven++ }

    override fun acceptOwnerTurn(request: String) { ownerTurn++; chat += "> $request" }
    override fun clearInput() { inputCleared++ }
    override fun answer(request: String?, spoken: String, shown: String) {
        check(shown.startsWith(spoken)) { "the spoken part must be the start of what is shown" }
        chat += shown
        this.spoken += spoken
        this.shown += shown
    }
    override fun activity(line: String) { activities += line }
    override fun idle(): Boolean = !busy
    override fun speaking(): Boolean = speakingNow
    override fun later(action: () -> Unit) { queue += action }
    /** Runs what was posted for „later“ (and what that posts). */
    fun drain() { while (queue.isNotEmpty()) queue.removeFirst()() }

    /** An order dispatched through the owner input path: this lane is asked like every lane; null = it is another lane's. */
    override fun submit(order: String) {
        submitted += order
        director.handle(order)
    }

    var facts: ContinuumSystemFacts = fine()
    var loadReduced = false
    var webJob = false
    override fun systemFacts(): ContinuumSystemFacts = facts.copy(internetMode = mode, openWork = work,
        openMessages = messages.open()?.size)
    override fun loadReducedByHeat(facts: ContinuumSystemFacts): Boolean = loadReduced
    override fun webJobRunning(): Boolean = webJob

    // Calendar
    var calendarGranted = true
    /** The owner's answer to the permission request; null = the request must not happen. */
    var calendarAnswer: Boolean? = null
    val calendarExplanations = mutableListOf<String>()
    var events: List<ContinuumCalendarEvent> = emptyList()
    var calendarFailure: String? = null
    val calendarWindows = mutableListOf<Pair<Long, Long>>()
    override fun calendar(): ContinuumCalendarSource = object : ContinuumCalendarSource {
        override fun granted(): Boolean = calendarGranted
        override fun read(fromMillis: Long, untilMillis: Long): ContinuumCalendarRead {
            if (!calendarGranted) return ContinuumCalendarRead.NotGranted
            calendarWindows += fromMillis to untilMillis
            calendarFailure?.let { return ContinuumCalendarRead.Failed(it) }
            return ContinuumCalendarRead.Events(events.filter { it.startMillis < untilMillis && it.endMillis >= fromMillis })
        }
    }
    override fun requestCalendar(explanation: String, done: (Boolean) -> Unit) {
        calendarExplanations += explanation
        val answer = checkNotNull(calendarAnswer) { "the calendar permission must not be asked for here" }
        calendarGranted = answer
        done(answer)
    }

    // Web consent and weather
    var mode = InternetAccessMode.OFF
    /** The owner's answer to the ASK dialog. */
    var webAnswer = true
    val webExplanations = mutableListOf<String>()
    var weather: (ContinuumWeatherRequest) -> ContinuumWeatherRead = { ContinuumWeatherRead.Report(LINZ_REPORT) }
    /** A weather job that never delivers because it was stopped. */
    var weatherStopped = false
    val weatherRequests = mutableListOf<ContinuumWeatherRequest>()
    override fun internetMode(): InternetAccessMode = mode
    override fun confirmWeb(call: WebToolRequest, explanation: String?, notGranted: String?,
                            completed: (ApprovedWebResearchSession?) -> Unit) {
        check(mode != InternetAccessMode.OFF) { "no web request while the internet setting is OFF" }
        check(call == WebToolRequest.Search(ContinuumWeather.GRANT_LABEL)) { "unexpected web call $call" }
        webExplanations += explanation.orEmpty()
        if (mode == InternetAccessMode.ASK && !webAnswer) {
            // The real gate shows the notice itself.
            chat += notGranted.orEmpty()
            completed(null)
            return
        }
        completed(ApprovedWebToolGate(InternetConsentPolicy(InternetAccessMode.ALWAYS_ON)).authorizeAlwaysOn(call, ResearchMode.STANDARD))
    }
    override fun readWeather(session: ApprovedWebResearchSession, request: String, weather: ContinuumWeatherRequest,
                             done: (Result<ContinuumWeatherRead>) -> Unit) {
        weatherRequests += weather
        done(if (weatherStopped) Result.failure(CancellationException("gestoppt")) else runCatching { this.weather(weather) })
    }
    val jobs = mutableListOf<String>()
    /** A local job that cannot start because something else runs. */
    var jobRefused = false
    override fun <T> runJob(request: String, label: String, work: () -> T, done: (Result<T>) -> Unit) {
        jobs += label
        done(if (jobRefused) Result.failure(IllegalStateException("Ein anderer Auftrag läuft bereits")) else runCatching(work))
    }

    // Open items
    var work: List<String> = emptyList()
    var messages: ContinuumOpenMessages = ContinuumOpenMessages.NONE
    override fun openWork(): List<String> = work
    override fun openMessages(): ContinuumOpenMessages = messages

    // Calls
    var contactsGranted = true
    var contactsAnswer: Boolean? = null
    val contactExplanations = mutableListOf<String>()
    var phones: List<OwnerContactPhone> = emptyList()
    var contactReads = 0
    val dialed = mutableListOf<String>()
    var dialProblem: String? = null
    override fun contactsReadable(): Boolean = contactsGranted
    override fun requestContacts(explanation: String, done: (Boolean) -> Unit) {
        contactExplanations += explanation
        val answer = checkNotNull(contactsAnswer) { "the contacts permission must not be asked for here" }
        contactsGranted = answer
        done(answer)
    }
    override fun contacts(): OwnerSendContactSource = object : OwnerSendContactSource {
        override fun phones(): List<OwnerContactPhone> { check(contactsGranted); contactReads++; return phones }
        override fun mails(): List<OwnerContactMail> = error("the briefing lane never reads mail addresses")
    }
    override fun dial(number: String): String? { dialed += number; return dialProblem }

    val stored = LinkedHashMap<String, String>()
    private val keyValue = object : ContinuumBriefingStore {
        override fun get(key: String): String? = stored[key]
        override fun put(key: String, value: String?) { if (value == null) stored.remove(key) else stored[key] = value }
    }
    override fun store(): ContinuumBriefingStore = keyValue
    val settings = ContinuumBriefingSettings(keyValue)

    /** How the lane said its orders ended (typed), in order. */
    val outcomes = mutableListOf<at.heinz.deviceai.OwnerLaneOutcome>()
    override fun outcome(outcome: at.heinz.deviceai.OwnerLaneOutcome) { outcomes += outcome }

    /** One owner turn through the lane; the answer is the last chat line when the lane took it. */
    fun say(text: String): Boolean = director.handle(text).also { drain() }
    val last: String get() = chat.last()
}
