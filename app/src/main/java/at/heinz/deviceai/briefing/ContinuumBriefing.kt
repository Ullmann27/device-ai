package at.heinz.deviceai.briefing

import at.heinz.deviceai.OwnerLaneOutcome
import at.heinz.deviceai.OwnerLaneResult
import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.cognitive.WebToolRequest
import at.heinz.deviceai.sending.OwnerSendRecipientResolver
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CancellationException

/** Why a briefing has no weather – or the weather itself. */
internal sealed interface ContinuumWeatherOutcome {
    data class Report(val report: ContinuumWeatherReport) : ContinuumWeatherOutcome
    /** The owner has not set a place. */
    data object NoPlace : ContinuumWeatherOutcome
    /** The owner's internet setting is OFF: nothing was sent. */
    data object InternetOff : ContinuumWeatherOutcome
    /** The owner did not grant this request; the notice was already shown. */
    data object NotGranted : ContinuumWeatherOutcome
    data class PlaceUnknown(val asked: String) : ContinuumWeatherOutcome
    data class Failed(val reason: String) : ContinuumWeatherOutcome
}

/** Everything one briefing is assembled from – only what was really read. */
internal data class ContinuumBriefingParts(
    val today: LocalDate,
    val ownerName: String?,
    val calendar: ContinuumCalendarRead,
    /** Today's and tomorrow's view; null unless [calendar] was read. */
    val agendaToday: ContinuumAgenda.Day?,
    val agendaTomorrow: ContinuumAgenda.Day?,
    val weather: ContinuumWeatherOutcome,
    val openWork: List<String>,
    /** null = no message list is wired. */
    val openMessages: List<ContinuumOpenMessage>?,
    val savedUnsent: List<String>,
    val checks: List<ContinuumSystemCheck.Check>,
    /** The news overview follows as its own turn of the news lane. */
    val newsFollows: Boolean
)

/** Wording and cards of the daily briefing. Pure: the same parts always give the same text. */
internal object ContinuumBriefingText {
    const val CALENDAR_DENIED = "Deine Termine kann ich nicht nennen: Der Kalenderzugriff ist nicht erlaubt. " +
        "Du kannst ihn in den Android-Einstellungen unter Apps → Continuum → Berechtigungen einschalten."
    const val CALENDAR_FAILED = "Den Kalender konnte ich gerade nicht lesen."
    const val NO_PLACE = "Für das Wetter fehlt mir dein Ort – sag zum Beispiel: Mein Ort ist Linz."
    const val WEATHER_OFF = "Wetter gibt es keines: Der Internetzugang ist ausgeschaltet."
    const val WEATHER_NOT_GRANTED = "Wetter nicht abgerufen: Der Internetzugriff wurde für diese Anfrage nicht freigegeben."

    fun unknownPlace(asked: String) = "Den Ort „$asked“ kennt der Wetterdienst nicht."
    fun weatherFailed(reason: String) = "Das Wetter konnte ich nicht abrufen: $reason."

    private fun calendarSentence(parts: ContinuumBriefingParts): String = when (parts.calendar) {
        ContinuumCalendarRead.NotGranted -> "Termine kann ich dir nicht nennen, der Kalenderzugriff ist nicht erlaubt."
        is ContinuumCalendarRead.Failed -> CALENDAR_FAILED
        is ContinuumCalendarRead.Events -> listOfNotNull(
            parts.agendaToday?.let { ContinuumAgenda.spoken(it, "Heute") },
            parts.agendaTomorrow?.let(ContinuumAgenda::spokenMorning)).joinToString(" ")
    }

    private fun weatherSentence(parts: ContinuumBriefingParts): String? = when (val weather = parts.weather) {
        is ContinuumWeatherOutcome.Report -> ContinuumWeather.spoken(weather.report, BriefingDay.TODAY, parts.today)
            ?: weatherFailed("die Antwort enthielt keine Werte für heute")
        ContinuumWeatherOutcome.NoPlace -> NO_PLACE
        ContinuumWeatherOutcome.InternetOff -> WEATHER_OFF
        // The web gate has already said that the request was not granted.
        ContinuumWeatherOutcome.NotGranted -> null
        is ContinuumWeatherOutcome.PlaceUnknown -> unknownPlace(weather.asked)
        is ContinuumWeatherOutcome.Failed -> weatherFailed(weather.reason)
    }

    private fun openSentence(parts: ContinuumBriefingParts): String {
        val work = parts.openWork
        val messages = parts.openMessages.orEmpty()
        val sentences = buildList {
            if (work.isNotEmpty()) add("Offen " + (if (work.size == 1) "ist noch eine Arbeit" else
                "sind noch " + ContinuumSystemCheck.count(work.size, "eine Arbeit", "Arbeiten")) + ": " +
                work.first().trim().trimEnd('.').take(140) + (if (work.size > 1) " – und mehr" else "") + ".")
            if (messages.isNotEmpty()) {
                val senders = messages.map { it.sender.trim() }.filter { it.isNotEmpty() }.distinct()
                add((if (messages.size == 1) "Eine Nachricht ist offen" else
                    ContinuumSystemCheck.count(messages.size, "Eine Nachricht", "Nachrichten").replaceFirstChar { it.uppercase() } + " sind offen") +
                    (if (senders.isNotEmpty()) ", von " + ContinuumSystemCheck.list(senders.take(3)) +
                        (if (senders.size > 3) " und weiteren" else "") else "") + ".")
            }
            if (parts.savedUnsent.isNotEmpty()) add("Gespeichert, aber von mir noch nicht verschickt: " +
                ContinuumSystemCheck.list(parts.savedUnsent.take(2)) + ".")
        }
        return if (sentences.isEmpty()) "Offen ist nichts." else sentences.joinToString(" ")
    }

    /** The briefing as it is spoken: date, appointments, weather, open items, a serious device problem if there is one. */
    fun spoken(parts: ContinuumBriefingParts): String = buildList {
        add((parts.ownerName?.trim()?.takeIf { it.isNotEmpty() }?.let { "$it, heute" } ?: "Heute") + " ist " +
            ContinuumAgenda.spokenDate(parts.today) + ".")
        add(calendarSentence(parts))
        weatherSentence(parts)?.let(::add)
        add(openSentence(parts))
        parts.checks.filter { it.state == ContinuumSystemCheck.State.FAIL }.take(2).takeIf { it.isNotEmpty() }?.let { failing ->
            add("Noch etwas: " + ContinuumSystemCheck.list(failing.map { it.spoken }) + ".")
        }
        if (parts.newsFollows) add("Die Nachrichten folgen gleich.")
    }.filter { it.isNotBlank() }.joinToString(" ")

    fun cards(parts: ContinuumBriefingParts): List<ContinuumBriefingBoard.Card> = buildList {
        add(calendarCard(parts.calendar, parts.agendaToday, "Termine heute"))
        add(weatherCard(parts.weather, parts.today))
        parts.openMessages?.let { messages ->
            add(ContinuumBriefingBoard.Card("messages", ContinuumBriefingBoard.Kind.MESSAGES, "Offene Nachrichten",
                if (messages.isEmpty()) listOf("keine") else capped(messages.map { it.sender + (it.app?.let { app -> " · $app" } ?: "") }),
                if (messages.isEmpty()) ContinuumBriefingBoard.State.INFO else ContinuumBriefingBoard.State.ATTENTION))
        }
        val tasks = parts.openWork.map { it.take(90) } + parts.savedUnsent.map { "gespeichert, nicht verschickt: $it" }
        add(ContinuumBriefingBoard.Card("tasks", ContinuumBriefingBoard.Kind.TASKS, "Offene Arbeit",
            if (tasks.isEmpty()) listOf("nichts offen") else capped(tasks),
            if (tasks.isEmpty()) ContinuumBriefingBoard.State.INFO else ContinuumBriefingBoard.State.ATTENTION))
        if (parts.newsFollows) add(ContinuumBriefingBoard.Card("news", ContinuumBriefingBoard.Kind.NEWS, "Nachrichten",
            listOf("Überblick folgt als eigene Antwort"), ContinuumBriefingBoard.State.INFO))
        systemCard(parts.checks)?.takeIf { it.state == ContinuumBriefingBoard.State.ATTENTION }?.let(::add)
    }

    private fun capped(lines: List<String>): List<String> = if (lines.size <= ContinuumBriefingBoard.MAX_LINES) lines
        else lines.take(ContinuumBriefingBoard.MAX_LINES - 1) + "+ ${lines.size - (ContinuumBriefingBoard.MAX_LINES - 1)} weitere"

    fun calendarCard(read: ContinuumCalendarRead, day: ContinuumAgenda.Day?, title: String): ContinuumBriefingBoard.Card = when {
        read is ContinuumCalendarRead.Events && day != null -> ContinuumBriefingBoard.Card("calendar",
            ContinuumBriefingBoard.Kind.CALENDAR, title, if (day.empty) listOf("nichts im Kalender") else ContinuumAgenda.lines(day),
            if (ContinuumAgenda.overlap(day) != null) ContinuumBriefingBoard.State.ATTENTION else ContinuumBriefingBoard.State.INFO)
        read is ContinuumCalendarRead.NotGranted -> ContinuumBriefingBoard.Card("calendar", ContinuumBriefingBoard.Kind.CALENDAR,
            title, listOf("Kalenderzugriff nicht erlaubt"), ContinuumBriefingBoard.State.ATTENTION)
        else -> ContinuumBriefingBoard.Card("calendar", ContinuumBriefingBoard.Kind.CALENDAR, title,
            listOf("Kalender nicht lesbar"), ContinuumBriefingBoard.State.ATTENTION)
    }

    fun weatherCard(weather: ContinuumWeatherOutcome, today: LocalDate): ContinuumBriefingBoard.Card {
        fun none(line: String) = ContinuumBriefingBoard.Card("weather", ContinuumBriefingBoard.Kind.WEATHER, "Wetter",
            listOf(line), ContinuumBriefingBoard.State.INFO)
        return when (weather) {
            is ContinuumWeatherOutcome.Report -> ContinuumBriefingBoard.Card("weather", ContinuumBriefingBoard.Kind.WEATHER,
                "Wetter · " + weather.report.place.label, ContinuumWeather.lines(weather.report, today), ContinuumBriefingBoard.State.INFO)
            ContinuumWeatherOutcome.NoPlace -> none("kein Ort festgelegt")
            ContinuumWeatherOutcome.InternetOff -> none("nicht abgerufen · Internetzugang aus")
            ContinuumWeatherOutcome.NotGranted -> none("nicht abgerufen · nicht freigegeben")
            is ContinuumWeatherOutcome.PlaceUnknown -> none("Ort „${weather.asked}“ nicht gefunden")
            is ContinuumWeatherOutcome.Failed -> none("nicht abgerufen · ${weather.reason}")
        }
    }

    /** The system card: the problems, or that everything checked is fine. Null without checks. */
    fun systemCard(checks: List<ContinuumSystemCheck.Check>): ContinuumBriefingBoard.Card? {
        if (checks.isEmpty()) return null
        val problems = ContinuumSystemCheck.problems(checks)
        val off = checks.filter { it.state == ContinuumSystemCheck.State.OFF }
        val lines = when {
            problems.isNotEmpty() -> capped(problems.map { it.label + ": " + it.detail })
            off.isNotEmpty() -> capped(listOf("Eingeschaltetes läuft") + off.map { it.label + ": " + it.detail })
            else -> listOf("Alle Prüfungen in Ordnung")
        }
        return ContinuumBriefingBoard.Card("system", ContinuumBriefingBoard.Kind.SYSTEM, "System", lines,
            if (problems.isNotEmpty()) ContinuumBriefingBoard.State.ATTENTION else ContinuumBriefingBoard.State.INFO)
    }
}

/**
 * The briefing lane (owner goal 19.11.2026): the truthful system start report, the daily briefing, „Entscheide du die
 * Aktion“, „Ruf Anna an“ and truthful warnings. This class decides and words; reading, asking and showing go through
 * [ContinuumBriefingHost]. It is free of Android, so every flow runs in host tests with the real parsers and wording.
 *
 * Fixed rules: nothing is said that was not read at that moment; nothing is sent, changed or called – the calendar is
 * only read, the dialer only opened; the network is used for the weather alone and only through the owner's web
 * consent; a next step is carried out only after the owner chose it.
 */
internal class ContinuumBriefingDirector(
    private val host: ContinuumBriefingHost,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) {
    /** A question of this lane that waits for the owner's very next turn in the same chat, for a few minutes. */
    private open inner class Pending {
        val session: String = host.sessionId
        val turn: Long = host.ownerTurn
        private val askedAt: Long = now().toEpochMilli()
        val open: Boolean get() = session == host.sessionId && turn == host.ownerTurn &&
            now().toEpochMilli() - askedAt in 0..PENDING_MILLIS
    }
    private inner class PendingOffers(val offers: List<ContinuumBriefingBoard.Offer>) : Pending()
    private inner class PendingYes : Pending()
    private inner class PendingCall(val asked: String, val candidates: List<OwnerSendRecipientResolver.Candidate>) : Pending()
    private inner class PendingWeather(val day: BriefingDay) : Pending()

    private val settings by lazy { ContinuumBriefingSettings(host.store()) }
    private val watch = ContinuumWatch()
    private var pendingOffers: PendingOffers? = null
    private var pendingDaily: PendingYes? = null
    private var pendingCall: PendingCall? = null
    private var pendingWeather: PendingWeather? = null

    private fun today(): LocalDate = now().atZone(zone()).toLocalDate()

    // ── Hooks for the greeting chain ────────────────────────────────────────────────────────────────────────

    /**
     * The once-per-day offer at [at], or null: switched off, earlier than the owner's hour, already offered today or
     * a briefing was already given today. Reads only; [briefingOffer] is what consumes the day's offer.
     */
    fun dueOffer(at: Instant): String? {
        if (!settings.offerEnabled) return null
        val local = at.atZone(zone())
        if (local.hour < settings.offerFromHour) return null
        val day = local.toLocalDate()
        if (settings.lastOfferDay == day || settings.lastBriefingDay == day) return null
        return OFFER_TEXT
    }

    /**
     * The offer text for the greeting to say – at most once per day. Saying it is the caller's part; from now until
     * the owner's next turn a plain „ja“ starts the briefing and a plain „nein“ is taken as his answer.
     */
    fun briefingOffer(): String? {
        val text = dueOffer(now()) ?: return null
        settings.lastOfferDay = today()
        pendingDaily = PendingYes()
        return text
    }

    /** Starts the briefing without an owner turn (the owner accepted the offer by other means). */
    fun runBriefing() {
        clearPending()
        startBriefing(null, withNews = false)
    }

    /** The owner tapped the offer with [offerId]; false when that offer is no longer open. */
    fun choose(offerId: String): Boolean {
        val offer = ContinuumBriefingBoard.offers().firstOrNull { it.id == offerId } ?: return false
        if (!host.idle()) return false
        clearPending()
        host.later { host.submit(offer.order) }
        return true
    }

    // ── Owner turns ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * true = [request] was for this lane and is completely handled (an answer was or will be given). Call on the UI
     * thread while no other job runs. false = not for this lane; nothing happened.
     */
    fun handle(request: String): Boolean {
        val name = host.assistantName
        if (answerPending(request, name)) return true
        // Whatever was open is over with any other owner turn.
        clearPending()
        val command = ContinuumBriefingCommands.parse(request, name) ?: return false
        host.acceptOwnerTurn(request)
        when (command) {
            ContinuumBriefingCommand.SystemCheck -> systemCheck(request)
            is ContinuumBriefingCommand.Briefing -> startBriefing(request, command.withNews)
            is ContinuumBriefingCommand.Appointments -> appointments(request, command.day)
            is ContinuumBriefingCommand.Agenda -> agenda(request, command.span, command.ask)
            is ContinuumBriefingCommand.Weather -> weather(request, command.day, command.place)
            is ContinuumBriefingCommand.SetPlace -> setPlace(request, command.place)
            is ContinuumBriefingCommand.Call -> call(request, command.target)
            is ContinuumBriefingCommand.Offer -> setOffer(request, command)
            is ContinuumBriefingCommand.NewsPart -> {
                settings.newsPart = command.enabled
                host.answer(request, if (command.enabled) "Gut, die Nachrichten gehören ab jetzt zu deinem Briefing. " +
                    "Sie brauchen den Internetzugang und kommen als eigener Überblick danach."
                    else "Gut, dein Briefing kommt ab jetzt ohne Nachrichten.")
            }
        }
        return true
    }

    private fun clearPending() {
        pendingOffers = null
        pendingDaily = null
        pendingCall = null
        pendingWeather = null
        ContinuumBriefingBoard.publishOffers(emptyList())
    }

    /** true while one of this lane's questions still waits for the owner's answer. */
    private fun questionOpen(): Boolean =
        listOf(pendingOffers, pendingDaily, pendingCall, pendingWeather).any { it?.open == true }

    /** The owner's answer to this lane's own open question; false = [request] is not such an answer. */
    private fun answerPending(request: String, name: String?): Boolean {
        pendingCall?.takeIf { it.open }?.let { pending ->
            if (ContinuumNextSteps.isNo(request, name) || request.trim().lowercase() in setOf("abbrechen", "keinen", "niemanden")) {
                pendingCall = null
                host.acceptOwnerTurn(request)
                host.answer(request, "In Ordnung, ich habe niemanden angerufen.")
                return true
            }
            OwnerSendRecipientResolver.pick(request, pending.candidates)?.let { candidate ->
                pendingCall = null
                host.acceptOwnerTurn(request)
                openDialer(request, "mit der Nummer von " + candidate.displayName, candidate.value)
                return true
            }
        }
        pendingDaily?.takeIf { it.open }?.let {
            if (ContinuumNextSteps.isYes(request, name)) {
                clearPending()
                host.acceptOwnerTurn(request)
                startBriefing(request, withNews = false)
                return true
            }
            if (ContinuumNextSteps.isNo(request, name)) {
                clearPending()
                host.acceptOwnerTurn(request)
                host.answer(request, "In Ordnung. Sag einfach „Gib mir mein Briefing“, wenn du es doch möchtest.")
                return true
            }
        }
        pendingOffers?.takeIf { it.open }?.let { pending ->
            when (val choice = ContinuumNextSteps.choose(request, pending.offers.map { it.kind }, name)) {
                null -> Unit
                is ContinuumNextSteps.Choice.Pick -> {
                    val offer = pending.offers[choice.index]
                    clearPending()
                    // The chosen order takes the owner's turn: it is shown and handled as if he had said it.
                    host.clearInput()
                    host.activity("NÄCHSTER SCHRITT · vom Besitzer gewählt · „${offer.order}“")
                    host.later { host.submit(offer.order) }
                    return true
                }
                ContinuumNextSteps.Choice.Ask -> {
                    host.acceptOwnerTurn(request)
                    host.answer(request, ContinuumNextSteps.askWhich(pending.offers))
                    pendingOffers = PendingOffers(pending.offers)
                    return true
                }
                ContinuumNextSteps.Choice.Decline -> {
                    clearPending()
                    host.acceptOwnerTurn(request)
                    host.answer(request, "In Ordnung.")
                    return true
                }
            }
        }
        pendingWeather?.takeIf { it.open }?.let { pending ->
            (ContinuumBriefingCommands.parse(request, name) as? ContinuumBriefingCommand.SetPlace)?.let { place ->
                clearPending()
                host.acceptOwnerTurn(request)
                settings.place = place.place
                host.activity("WETTER · Ort gespeichert · noch keine Anfrage gesendet")
                readWeather(request, place.place, null, ownPlace = true) { outcome -> answerWeather(request, pending.day, outcome) }
                return true
            }
        }
        return false
    }

    /** Offers [offers] with [question] already part of the answer just given; valid for the owner's next turn. */
    private fun offer(offers: List<ContinuumBriefingBoard.Offer>) {
        ContinuumBriefingBoard.publishOffers(offers)
        pendingOffers = offers.takeIf { it.isNotEmpty() }?.let { PendingOffers(it.take(ContinuumBriefingBoard.MAX_OFFERS)) }
    }

    // ── System check ────────────────────────────────────────────────────────────────────────────────────────

    private fun diagnosisOffer() = ContinuumBriefingBoard.Offer("diagnosis", ContinuumBriefingBoard.OfferKind.DIAGNOSIS,
        "eine Systemdiagnose machen", ORDER_DIAGNOSIS)

    /**
     * The system check of this moment as the spoken report – for the greeting to say at app start. It publishes the
     * checks ([ContinuumSystemCheck.snapshot]) and the system card, and says nothing itself.
     */
    fun systemReport(): String {
        val checks = ContinuumSystemCheck.run(host.systemFacts())
        ContinuumBriefingText.systemCard(checks)?.let(ContinuumBriefingBoard::put)
        return ContinuumSystemCheck.report(checks, host.ownerName())
    }

    private fun systemCheck(request: String) {
        val report = systemReport()
        val checks = ContinuumSystemCheck.snapshot()
        val storage = checks.firstOrNull { it.key == ContinuumSystemCheck.STORAGE }?.state
        val offers = if (storage == ContinuumSystemCheck.State.WARN || storage == ContinuumSystemCheck.State.FAIL)
            listOf(diagnosisOffer()) else emptyList()
        val spoken = listOfNotNull(report, ContinuumNextSteps.question(offers)).joinToString(" ")
        host.activity("SYSTEMCHECK · " + checks.size + " Prüfungen · " + ContinuumSystemCheck.problems(checks).size +
            " auffällig · nur gelesen, kein Modell- oder Netzaufruf")
        host.answer(request, spoken, spoken + "\n\n" + ContinuumSystemCheck.lines(checks))
        offer(offers)
    }

    /** One observation of the watcher; says at most one warning. Call on the UI thread, e.g. once a minute. */
    fun watchTick() {
        val facts = runCatching { host.systemFacts() }.getOrNull() ?: return
        val quiet = !host.idle() || host.speaking() || questionOpen()
        val warning = watch.observe(facts, host.ownerName(), now().toEpochMilli(), quiet,
            loadReduced = host.loadReducedByHeat(facts), webJobRunning = host.webJobRunning()) ?: return
        val offers = if (warning.offer == ContinuumBriefingBoard.OfferKind.DIAGNOSIS) listOf(diagnosisOffer()) else emptyList()
        ContinuumBriefingText.systemCard(ContinuumSystemCheck.run(facts))?.let(ContinuumBriefingBoard::put)
        host.activity("HINWEIS · " + warning.condition.name + " · aus dem Gerätezustand, einmal je Anlass")
        host.answer(null, listOfNotNull(warning.text, ContinuumNextSteps.question(offers)).joinToString(" "))
        offer(offers)
    }

    // ── Calendar ────────────────────────────────────────────────────────────────────────────────────────────

    private fun readCalendar(request: String, mayAsk: Boolean, window: Pair<Long, Long>, done: (ContinuumCalendarRead) -> Unit) {
        val source = host.calendar()
        fun read() = host.runJob(request, "Kalender lesen", { source.read(window.first, window.second) }) { result ->
            result.fold({ done(it) }, { error -> if (error !is CancellationException) done(ContinuumCalendarRead.Failed(error.message.orEmpty())) })
        }
        if (source.granted()) { read(); return }
        if (!mayAsk) { done(ContinuumCalendarRead.NotGranted); return }
        settings.calendarAsked = true
        host.requestCalendar(CALENDAR_EXPLANATION) { granted -> if (granted) read() else done(ContinuumCalendarRead.NotGranted) }
    }

    private fun appointments(request: String, day: BriefingDay) {
        val date = today().plusDays(if (day == BriefingDay.TOMORROW) 1 else 0)
        val label = if (day == BriefingDay.TOMORROW) "Morgen" else "Heute"
        readCalendar(request, mayAsk = true, ContinuumAgenda.dayWindow(date, zone())) { read ->
            val view = (read as? ContinuumCalendarRead.Events)?.let { ContinuumAgenda.day(it.events, date, zone(), now()) }
            ContinuumBriefingBoard.put(ContinuumBriefingText.calendarCard(read, view, "Termine " + label.lowercase()))
            host.activity("KALENDER · nur gelesen · " + when (read) {
                is ContinuumCalendarRead.Events -> "${read.events.size} Einträge im Zeitraum"
                ContinuumCalendarRead.NotGranted -> "Zugriff nicht erlaubt"
                is ContinuumCalendarRead.Failed -> "nicht lesbar"
            })
            host.answer(request, when (read) {
                is ContinuumCalendarRead.Events -> ContinuumAgenda.spoken(view!!, label, detailed = true)
                ContinuumCalendarRead.NotGranted -> ContinuumBriefingText.CALENDAR_DENIED
                is ContinuumCalendarRead.Failed -> ContinuumBriefingText.CALENDAR_FAILED
            })
        }
    }

    /**
     * The calendar over [span]: the list, the check for what stands out, or the free time (owner sentence 19.11.2026:
     * „Schau, welche Termine ich diese Woche habe, und sag mir, ob ich etwas vergessen könnte“). One read through the
     * same READ_CALENDAR path as the briefing; the answer is built here from the entries and the fixed rules of
     * [AgendaCheck] – no model sees a calendar entry and nothing goes to the network.
     */
    private fun agenda(request: String, span: AgendaSpan, ask: AgendaAsk) {
        val today = today()
        val dates = ContinuumAgenda.dates(span, today)
        val label = span.label.replaceFirstChar { it.titlecase(java.util.Locale.GERMAN) }
        readCalendar(request, mayAsk = true, ContinuumAgenda.window(dates, zone())) { read ->
            host.activity("KALENDER · nur gelesen · " + when (read) {
                is ContinuumCalendarRead.Events -> "${read.events.size} Einträge im Zeitraum · ${span.label} · geprüft nach festen Regeln, ohne Modell"
                ContinuumCalendarRead.NotGranted -> "Zugriff nicht erlaubt"
                is ContinuumCalendarRead.Failed -> "nicht lesbar"
            })
            when (read) {
                ContinuumCalendarRead.NotGranted -> {
                    ContinuumBriefingBoard.put(ContinuumBriefingText.calendarCard(read, null, "Termine " + span.label))
                    host.answer(request, ContinuumBriefingText.CALENDAR_DENIED)
                    host.outcome(at.heinz.deviceai.OwnerLaneOutcome.NeedsOwner(at.heinz.deviceai.OwnerLaneOutcome.Need.GRANT,
                        ContinuumBriefingText.CALENDAR_DENIED))
                }
                is ContinuumCalendarRead.Failed -> {
                    ContinuumBriefingBoard.put(ContinuumBriefingText.calendarCard(read, null, "Termine " + span.label))
                    host.answer(request, ContinuumBriefingText.CALENDAR_FAILED)
                    host.outcome(at.heinz.deviceai.OwnerLaneOutcome.Failed(ContinuumBriefingText.CALENDAR_FAILED))
                }
                is ContinuumCalendarRead.Events -> {
                    val days = ContinuumAgenda.days(read.events, dates, zone(), now())
                    val report = AgendaCheck.check(days, now().atZone(zone()).toLocalDateTime())
                    val listed = if (days.size == 1) ContinuumAgenda.spoken(days.single(), label, detailed = true)
                        else ContinuumAgenda.spokenWeek(days, label, today)
                    val spoken = when (ask) {
                        AgendaAsk.LIST -> listed
                        AgendaAsk.CHECK -> listed + " " + AgendaCheck.spoken(report, span.label, today)
                        AgendaAsk.FREE -> AgendaCheck.spokenFree(report, span.label, today)
                    }
                    val cardLines = if (ask == AgendaAsk.CHECK) AgendaCheck.lines(report) else
                        ContinuumAgenda.weekLines(days, ContinuumBriefingBoard.MAX_LINES).ifEmpty { listOf("nichts im Kalender") }
                    val attention = report.findings.any { it.kind == AgendaCheck.Kind.OVERLAP || it.kind == AgendaCheck.Kind.TIGHT_CHANGE }
                    ContinuumBriefingBoard.put(ContinuumBriefingBoard.Card("calendar", ContinuumBriefingBoard.Kind.CALENDAR,
                        (if (ask == AgendaAsk.CHECK) "Terminprüfung " else "Termine ") + span.label, cardLines,
                        if (attention) ContinuumBriefingBoard.State.ATTENTION else ContinuumBriefingBoard.State.INFO))
                    host.answer(request, spoken)
                    host.outcome(at.heinz.deviceai.OwnerLaneOutcome.Done(OwnerLaneResult.Agenda(span.label, report.events + report.allDay,
                        report.findings.size, report.free.size)))
                }
            }
        }
    }

    // ── Weather ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * The weather for [placeName] through the owner's web consent. [ownPlace]: it is the owner's stored place, so the
     * coordinates found for it are kept for the next time. [done] is not called when the job was stopped.
     */
    private fun readWeather(request: String, placeName: String?, known: ContinuumWeatherPlace?, ownPlace: Boolean,
                            done: (ContinuumWeatherOutcome) -> Unit) {
        if (placeName == null) { done(ContinuumWeatherOutcome.NoPlace); return }
        if (host.internetMode() == InternetAccessMode.OFF) {
            host.activity("WETTER · Internetzugang AUS · keine Anfrage gesendet")
            done(ContinuumWeatherOutcome.InternetOff)
            return
        }
        host.activity("WETTER · " + (if (host.internetMode() == InternetAccessMode.ALWAYS_ON) "dauerhafte Freigabe" else
            "einmalige Freigabe nötig") + " · Open-Meteo · übertragen wird nur der Ort")
        host.confirmWeb(WebToolRequest.Search(ContinuumWeather.GRANT_LABEL),
            explanation = "Für das Wetter frage ich den freien Wetterdienst Open-Meteo. Übertragen wird nur dein Ort „" +
                placeName + "“ beziehungsweise seine Koordinaten – kein Konto, kein Schlüssel und nichts aus deinem Auftrag. ",
            notGranted = ContinuumBriefingText.WEATHER_NOT_GRANTED) { session ->
            if (session == null) {
                host.activity("WETTER · nicht freigegeben · keine Anfrage gesendet")
                done(ContinuumWeatherOutcome.NotGranted)
            } else host.readWeather(session, request, ContinuumWeatherRequest(placeName, known)) { result ->
                result.fold({ read ->
                    when (read) {
                        is ContinuumWeatherRead.Report -> {
                            if (ownPlace && known == null && settings.place == placeName) settings.placeFound = read.report.place
                            host.activity("WETTER · gelesen · " + read.report.place.label + " · Quelle Open-Meteo")
                            done(ContinuumWeatherOutcome.Report(read.report))
                        }
                        is ContinuumWeatherRead.PlaceUnknown -> done(ContinuumWeatherOutcome.PlaceUnknown(read.asked))
                        is ContinuumWeatherRead.Failed -> done(ContinuumWeatherOutcome.Failed(read.reason))
                    }
                }, { error ->
                    if (error !is CancellationException) done(ContinuumWeatherOutcome.Failed(ContinuumWeather.failureClause(error)))
                })
            }
        }
    }

    private fun weather(request: String, day: BriefingDay, named: String?) {
        val place = named ?: settings.place
        if (place == null) {
            host.answer(request, ContinuumBriefingText.NO_PLACE)
            pendingWeather = PendingWeather(day)
            return
        }
        readWeather(request, place, if (named == null) settings.placeFound else null, ownPlace = named == null) { outcome ->
            answerWeather(request, day, outcome)
        }
    }

    private fun answerWeather(request: String, day: BriefingDay, outcome: ContinuumWeatherOutcome) {
        ContinuumBriefingBoard.put(ContinuumBriefingText.weatherCard(outcome, today()))
        val text = when (outcome) {
            is ContinuumWeatherOutcome.Report -> (ContinuumWeather.spoken(outcome.report, day, today())
                ?: ContinuumBriefingText.weatherFailed("die Antwort enthielt keine Werte für diesen Tag")) to
                "Quelle: ${ContinuumWeather.SOURCE} · ${outcome.report.place.label}"
            ContinuumWeatherOutcome.NoPlace -> ContinuumBriefingText.NO_PLACE to null
            ContinuumWeatherOutcome.InternetOff -> WEATHER_OFF_REPLY to null
            // The web gate has already said so.
            ContinuumWeatherOutcome.NotGranted -> return
            is ContinuumWeatherOutcome.PlaceUnknown -> (ContinuumBriefingText.unknownPlace(outcome.asked) +
                " Ich habe kein Wetter abgerufen.") to null
            is ContinuumWeatherOutcome.Failed -> ContinuumBriefingText.weatherFailed(outcome.reason) to null
        }
        host.answer(request, text.first, text.first + (text.second?.let { "\n\n$it" } ?: ""))
    }

    private fun setPlace(request: String, place: String) {
        settings.place = place
        host.activity("WETTER · Ort gespeichert · noch keine Anfrage gesendet")
        host.answer(request, "Gut, $place ist jetzt dein Ort für das Wetter. Nachgeschlagen wird er erst, wenn du nach dem Wetter fragst.")
    }

    private fun setOffer(request: String, command: ContinuumBriefingCommand.Offer) {
        settings.offerEnabled = command.enabled
        command.fromHour?.let { settings.offerFromHour = it }
        host.answer(request, when {
            !command.enabled -> "In Ordnung, ich biete dir das Briefing nicht mehr von selbst an. Auf Wunsch bekommst du es jederzeit."
            else -> "Gut, ich biete dir das Briefing einmal am Tag an – ab ${settings.offerFromHour} Uhr, sobald du dich das erste Mal " +
                "meldest. Von selbst wecken oder starten kann ich mich nicht: Android lässt eine App im Hintergrund nicht ungefragt sprechen."
        })
    }

    // ── Briefing ────────────────────────────────────────────────────────────────────────────────────────────

    private fun startBriefing(request: String?, withNews: Boolean) {
        val label = request ?: BRIEFING_LABEL
        val date = today()
        settings.lastBriefingDay = date
        readCalendar(label, mayAsk = !settings.calendarAsked, ContinuumAgenda.briefingWindow(date, zone())) { calendar ->
            readWeather(label, settings.place, settings.placeFound, ownPlace = true) { weather ->
                publishBriefing(request, date, calendar, weather, withNews || settings.newsPart)
            }
        }
    }

    private fun publishBriefing(request: String?, date: LocalDate, calendar: ContinuumCalendarRead,
                                weather: ContinuumWeatherOutcome, wantsNews: Boolean) {
        val events = (calendar as? ContinuumCalendarRead.Events)?.events
        val newsFollows = wantsNews && host.internetMode() != InternetAccessMode.OFF
        val nowMillis = now().toEpochMilli()
        val unsent = settings.savedUnsent.filter { nowMillis - it.second in 0..UNSENT_MILLIS }.map { it.first }
        val parts = ContinuumBriefingParts(date, host.ownerName(), calendar,
            events?.let { ContinuumAgenda.day(it, date, zone(), now()) },
            events?.let { ContinuumAgenda.day(it, date.plusDays(1), zone(), now()) },
            weather, host.openWork(), host.openMessages().open(), unsent,
            ContinuumSystemCheck.run(host.systemFacts()), newsFollows)
        val cards = ContinuumBriefingText.cards(parts)
        ContinuumBriefingBoard.publish(cards)
        val offers = if (newsFollows) emptyList() else nextSteps(parts)
        val spoken = listOfNotNull(ContinuumBriefingText.spoken(parts) +
            (if (wantsNews && !newsFollows) " Nachrichten gibt es keine: Der Internetzugang ist ausgeschaltet." else ""),
            ContinuumNextSteps.question(offers)).joinToString(" ")
        host.activity("BRIEFING · Kalender, Wetter und Offenes · nur Gelesenes · " + cards.size + " Karten")
        host.answer(request, spoken, spoken + "\n\n" + ContinuumBriefingBoard.chat(cards))
        offer(offers)
        if (newsFollows) {
            val session = host.sessionId
            val turn = host.ownerTurn
            // The news lane takes over once the briefing has been said – unless the owner went on in the meantime.
            host.afterSpeech { if (host.sessionId == session && host.ownerTurn == turn && host.idle()) host.submit(ORDER_NEWS) }
        }
    }

    /** At most three next steps the app can really carry out, from what the briefing just showed. */
    internal fun nextSteps(parts: ContinuumBriefingParts): List<ContinuumBriefingBoard.Offer> = buildList {
        val ahead = parts.agendaToday?.ahead.orEmpty()
        if (ahead.any { !it.event.location.isNullOrBlank() } || ahead.size > ContinuumAgenda.MAX_SPOKEN)
            add(ContinuumBriefingBoard.Offer("appointments", ContinuumBriefingBoard.OfferKind.APPOINTMENTS,
                if (ahead.size == 1) "den Termin um ${ContinuumAgenda.spokenTime(ahead[0].start.toLocalTime())} mit Ort vorlesen"
                else "die Termine einzeln mit Ort vorlesen", ORDER_APPOINTMENTS))
        parts.openMessages?.firstOrNull { !it.order.isNullOrBlank() && it.sender.isNotBlank() }?.let { message ->
            add(ContinuumBriefingBoard.Offer("message", ContinuumBriefingBoard.OfferKind.MESSAGE,
                "die Nachricht von ${message.sender.trim()} öffnen", message.order!!.trim()))
        }
        if (parts.openWork.isNotEmpty()) add(ContinuumBriefingBoard.Offer("work", ContinuumBriefingBoard.OfferKind.WORK,
            "die offene Arbeit zeigen", ORDER_OPEN_WORK))
        if (host.internetMode() != InternetAccessMode.OFF) add(ContinuumBriefingBoard.Offer("news",
            ContinuumBriefingBoard.OfferKind.NEWS, "die Nachrichten von heute zusammenfassen", ORDER_NEWS))
    }.take(ContinuumBriefingBoard.MAX_OFFERS)

    // ── Calling ─────────────────────────────────────────────────────────────────────────────────────────────

    private fun call(request: String, target: String) {
        if (ContinuumBriefingCommands.isNumber(target)) { openDialer(request, "mit der Nummer $target", target); return }
        // An emergency is never looked up in the address book: the dialer opens with the Euro emergency number.
        if (ContinuumBriefingCommands.isEmergency(target)) { openDialer(request, "mit dem Euro-Notruf $EMERGENCY_NUMBER", EMERGENCY_NUMBER); return }
        if (host.contactsReadable()) { resolveCall(request, target); return }
        host.requestContacts(CONTACTS_EXPLANATION) { granted ->
            if (granted) resolveCall(request, target) else host.answer(request, CONTACTS_DENIED)
        }
    }

    private fun resolveCall(request: String, target: String) {
        host.runJob(request, "Kontakt suchen", { host.contacts().phones() }) { result ->
            val rows = result.getOrElse { error ->
                if (error !is CancellationException) host.answer(request, "Das Adressbuch konnte ich gerade nicht lesen. Angerufen habe ich niemanden.")
                return@runJob
            }
            host.activity("ANRUF · Adressbuch nur gelesen · nichts gespeichert")
            when (val resolution = OwnerSendRecipientResolver.dial(target, rows)) {
                is OwnerSendRecipientResolver.Resolution.Resolved ->
                    openDialer(request, "mit der Nummer von " + resolution.candidate.displayName, resolution.candidate.value)
                is OwnerSendRecipientResolver.Resolution.Ambiguous -> {
                    host.answer(request, "Zu „${resolution.asked}“ gibt es mehrere passende Nummern: " +
                        resolution.candidates.joinToString(", ") { it.label } +
                        (if (resolution.total > resolution.candidates.size) " und weitere" else "") + ". Welche meinst du?")
                    pendingCall = PendingCall(resolution.asked, resolution.candidates)
                }
                is OwnerSendRecipientResolver.Resolution.NotFound ->
                    host.answer(request, "Im Adressbuch finde ich „${resolution.asked}“ nicht. Angerufen habe ich niemanden.")
                is OwnerSendRecipientResolver.Resolution.Unusable -> host.answer(request, resolution.reason)
            }
        }
    }

    /** [what]: what the dialer was opened with, as a clause („mit der Nummer von Anna Beispiel“). */
    private fun openDialer(request: String, what: String, number: String) {
        val problem = host.dial(number)
        if (problem != null) {
            host.activity("ANRUF · Wähltastatur nicht geöffnet")
            host.answer(request, "Die Wähltastatur ließ sich nicht öffnen: $problem. Angerufen habe ich niemanden.")
            return
        }
        host.activity("ANRUF · Wähltastatur geöffnet · kein Anruf gestartet")
        host.answer(request, "Die Wähltastatur ist offen, $what. Auf Anrufen tippst du selbst – ich wähle nicht.")
    }

    // ── Saved, not handed on ────────────────────────────────────────────────────────────────────────────────

    /** A lane settled an order: a saved file is remembered until it was handed on (mail, send). Nobody has to call this. */
    fun laneOutcome(outcome: OwnerLaneOutcome) {
        val result = (outcome as? OwnerLaneOutcome.Done)?.result ?: return
        when (result) {
            is OwnerLaneResult.FileSaved -> settings.savedUnsent = settings.savedUnsent.filter { it.first != result.file.displayName } +
                (result.file.displayName to now().toEpochMilli())
            is OwnerLaneResult.Delivered -> result.attachment?.let { sent ->
                settings.savedUnsent = settings.savedUnsent.filter { it.first != sent.displayName }
            }
            else -> Unit
        }
    }

    companion object {
        const val OFFER_TEXT = "Soll ich dir dein Briefing für heute geben?"
        const val BRIEFING_LABEL = "Briefing"
        const val ORDER_APPOINTMENTS = "Welche Termine habe ich heute?"
        const val ORDER_OPEN_WORK = "Was ist noch offen?"
        const val ORDER_NEWS = "Fass mir die Nachrichten von heute zusammen"
        const val ORDER_DIAGNOSIS = "Systemdiagnose"
        const val UNSENT_MILLIS = 24 * 3_600_000L
        const val PENDING_MILLIS = 5 * 60_000L
        /** The emergency number that works in the whole EU and on every mobile network. */
        const val EMERGENCY_NUMBER = "112"
        /** How often the watcher looks while the app is in front. */
        const val WATCH_MILLIS = 60_000L
        /** Longest wait for the spoken briefing before the news lane takes over (polls of 300 ms). */
        const val SPEECH_POLLS = 150
        /** The owner's form of address until the greeting lane provides its own source. */
        const val DEFAULT_OWNER_NAME = "Heinz"
        const val WEATHER_OFF_REPLY = "Der Internetzugang ist AUS, deshalb habe ich kein Wetter abgerufen. Stell ihn unter " +
            "„Datenschutz & Freigaben“ auf „Nachfragen“ oder „Immer“, dann hole ich es dir."
        const val CALENDAR_EXPLANATION = "Für deine Termine lese ich den Kalender dieses Geräts – nur lesen, nichts wird " +
            "geändert oder eingetragen. Die Termine bleiben auf dem Gerät. Android fragt dich gleich einmal nach der " +
            "Berechtigung „Kalender“."
        const val CONTACTS_EXPLANATION = "Um die Nummer zu finden, lese ich einmal dein Adressbuch auf diesem Gerät. " +
            "Es wird nichts gespeichert und nichts übertragen. Android fragt dich gleich nach der Berechtigung „Kontakte“."
        const val CONTACTS_DENIED = "Ohne Zugriff auf die Kontakte finde ich die Nummer nicht. Angerufen habe ich niemanden. " +
            "Du kannst mir auch die Nummer sagen: „Ruf 0664 … an“."
    }
}
