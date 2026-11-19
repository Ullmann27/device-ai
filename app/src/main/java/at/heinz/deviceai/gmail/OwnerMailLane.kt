package at.heinz.deviceai.gmail

import at.heinz.deviceai.OwnerLaneOutcome
import at.heinz.deviceai.OwnerLaneResult
import at.heinz.deviceai.OwnerWorkGeneration
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.connections.ContinuumConnectionKind
import at.heinz.deviceai.connections.ContinuumConnectionUsage
import at.heinz.deviceai.sending.OwnerSendContactSource
import at.heinz.deviceai.sending.OwnerSendRecipientResolver
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.CancellationException

/** The text of one mail as the lane read it – session memory only, never stored or logged. */
internal data class MailText(val ref: MailRef, val body: String, val truncated: Boolean, val attachments: List<GmailAttachmentRef>) {
    override fun toString(): String = "MailText(${ref.id}, ${body.length} Zeichen)"
}

/** What one mail step really produced (typed, for the mission layer). */
internal sealed interface MailLaneResult {
    data class Connected(val account: String?, val rights: Set<GmailCapability>) : MailLaneResult
    data object Disconnected : MailLaneResult
    /** A search or list: [refs] in the order shown (numbered from 1). */
    data class Found(val query: String, val refs: List<MailRef>) : MailLaneResult
    data class Read(val text: MailText) : MailLaneResult
    data class Thread(val texts: List<MailText>) : MailLaneResult
    /** [deliverable] text is what „mach daraus ein PDF“ saves. [cloud]: the owner's cloud brain wrote it. */
    data class Summary(val refs: List<MailRef>, val text: String, val cloud: Boolean, val complete: Boolean) : MailLaneResult
    /** Quoted evidence lines – no interpretation. */
    data class Deadlines(val refs: List<MailRef>, val lines: List<String>) : MailLaneResult
    data class DraftCreated(val id: String, val to: String, val subject: String, val threadId: String?) : MailLaneResult
    data class Sent(val id: String, val to: String, val subject: String) : MailLaneResult
}

/** How one mail step ended – the same four ends as [OwnerLaneOutcome]. */
internal sealed interface MailLaneOutcome {
    data class Done(val result: MailLaneResult) : MailLaneOutcome
    data class NeedsOwner(val need: OwnerLaneOutcome.Need, val question: String) : MailLaneOutcome
    /** [kind]: the Gmail failure, when it was one. */
    data class Failed(val reason: String, val kind: GmailFailureKind? = null) : MailLaneOutcome
    data object Stopped : MailLaneOutcome
}

/** A search as the mission layer may ask for it. [max]: how many are listed (1..10). */
internal data class MailQuery(val from: String? = null, val about: String? = null, val day: MailDay? = null,
                              val unreadOnly: Boolean = false, val max: Int = 8, val label: String? = null) {
    /** Gmail search syntax; days as exact epoch seconds (Gmail reads date words in Pacific time). */
    fun gmail(zone: ZoneId, today: LocalDate): String = buildList {
        add("in:inbox")
        from?.let { add("from:" + quoted(it)) }
        about?.let { add(quoted(it)) }
        if (unreadOnly) add("is:unread")
        day?.let { d ->
            val date = if (d == MailDay.TODAY) today else today.minusDays(1)
            add("after:" + date.atStartOfDay(zone).toEpochSecond())
            add("before:" + date.plusDays(1).atStartOfDay(zone).toEpochSecond())
        }
    }.joinToString(" ")

    /** How the owner hears and the stage shows the search. */
    fun spoken(): String = label ?: listOfNotNull(from?.let { "von $it" }, about?.let { "zu „$it“" },
        day?.let { if (it == MailDay.TODAY) "von heute" else "von gestern" }, "ungelesen".takeIf { unreadOnly }).joinToString(" · ")
        .ifEmpty { "Posteingang" }

    private fun quoted(value: String) = "\"" + value.replace("\"", "").take(80) + "\""
}

/**
 * The mail lane's public, Android-free API for the mission layer (steps `MailSearch`, `MailRead`, `Summarize`):
 * every call is a real lane step with the lane's own gates, wording and log line, and ends in exactly one typed
 * [MailLaneOutcome] through [done] (on the UI thread). Nothing here sends a mail: sending only happens through the
 * owner's own „Schick die Mail ab“ and his confirmation in the next turn.
 */
internal interface OwnerMailLane {
    /** The owner entry: true = [request] was this lane's and is handled. */
    fun handle(request: String): Boolean
    fun search(request: String, query: MailQuery, done: (MailLaneOutcome) -> Unit = {})
    /** Reads [ref] (or its whole conversation when [thread]). */
    fun read(request: String, ref: MailRef, thread: Boolean = false, done: (MailLaneOutcome) -> Unit = {})
    /** Summarises [refs] with the brain the owner's settings allow. */
    fun summarize(request: String, refs: List<MailRef>, done: (MailLaneOutcome) -> Unit = {})
    /** The numbered list shown last in this chat (empty when there is none). */
    val results: List<MailRef>
}

/** One running Gmail job. */
internal interface OwnerMailJob {
    fun step(key: String, label: String, state: StepState)
    /** Blocking text generation: [cloud] false = the on-device model only; true = the owner's cloud brain. */
    fun generate(prompt: String, cloud: Boolean): OwnerWorkGeneration
    fun cancelled(): Boolean
}

/** Everything the mail lane needs from the Activity. Fakes in host tests. */
internal interface OwnerMailHost {
    val sessionId: String
    /** Number of owner turns shown so far in this chat; unchanged means „the very next turn“. */
    val ownerTurn: Long
    val assistantName: String?
    fun acceptOwnerTurn(request: String)
    /**
     * A reply that did not come from the model. [private]: it carries mail data (sender, subject, text, summary): it
     * is shown and spoken, but neither stored in the chat transcript nor put into the model's conversation context.
     */
    fun answer(request: String?, spoken: String, shown: String = spoken, private: Boolean = false)
    fun activity(line: String)
    fun internetMode(): InternetAccessMode
    /** The owner's cloud brain is selected. */
    fun cloudBrain(): Boolean
    /** The on-device model is loaded and may compute now (never while the cloud brain is selected). */
    fun onDeviceModelReady(): Boolean
    fun promptBudgetBytes(): Int
    fun connection(): GmailConnection
    fun usage(): ContinuumConnectionUsage
    fun board(): MailWorkBoard
    fun transport(): GmailHttpTransport
    fun authorizer(): GmailAuthorizer
    /** Google's own consent screen for [capabilities]; [done] on the UI thread. */
    fun authorize(capabilities: Set<GmailCapability>, done: (GmailAuthResult) -> Unit)
    /** Revokes this app's access at Google; [done] with null or why it did not work. */
    fun revoke(account: String?, grant: GmailGrant, done: (GmailFailureKind?) -> Unit)
    /** [work] off the UI thread as the single busy job; [done] on the UI thread. */
    fun <T> runJob(request: String, label: String, work: (OwnerMailJob) -> T, done: (Result<T>) -> Unit)
    fun contactsReadable(): Boolean
    fun requestContacts(explanation: String, done: (Boolean) -> Unit)
    fun contacts(): OwnerSendContactSource
    fun nowMillis(): Long
    fun zone(): ZoneId
    /** The summary is this chat's newest result: „mach daraus ein PDF“ refers to it next. */
    fun deliverable(title: String, text: String, cloud: Boolean) {}
    /**
     * The same with what the saved document states about itself: how many mails it rests on and whether long mails
     * were cut. A host that keeps the summary as a document of its own overrides this one.
     */
    fun summarised(title: String, text: String, mails: Int, cloud: Boolean, complete: Boolean) = deliverable(title, text, cloud)
    /** Typed end of a step for listeners that only know [OwnerLaneOutcome] (drafts and sends map to Delivered). */
    fun outcome(outcome: OwnerLaneOutcome) {}
    fun mailOutcome(outcome: MailLaneOutcome) {}
}

/**
 * The Gmail lane (owner order 19.11.2026: „Gmail wie bei ChatGPT verbinden“). It decides and words; Google, the
 * network, the model and the screen are reached through [OwnerMailHost]. Fixed rules:
 *  - Mail content is third-party data. It is shown and read aloud, summarised with quotation frames, and never handed
 *    to a parser, a tool or another lane – an instruction inside a mail is text, nothing else.
 *  - Mail text goes to the cloud brain only with the owner's separate switch; otherwise the on-device model
 *    summarises, or the owner gets sender, subject and date only.
 *  - Sending: only a draft of this chat, only with the separately granted send right, only after the owner saw
 *    recipient, subject and full text and said yes in the very next turn. One send per confirmation, never repeated.
 *  - Every step ends with a log line and a typed outcome; a failed step is never reported as done.
 *  - Mail bodies, senders and subjects live only in this object's session state and in private replies.
 */
internal class OwnerMailDirector(private val host: OwnerMailHost) : OwnerMailLane {
    private class Session(val id: String) {
        var list: List<GmailMessage> = emptyList()
        var listAt = 0L
        var listTurn = -1L
        var listQuery: MailQuery? = null
        var selected: GmailMessage? = null
        var selectedAt = 0L
        var thread: List<GmailMessage> = emptyList()
        var draft: Draft? = null
        var lastTurn = -1L
    }

    private class Draft(val id: String, val mail: GmailOutgoing, val shownTo: String, val at: Long)

    private sealed interface Pending {
        /** Google is to be asked for [capability]; [then] continues the order. */
        class Grant(val capability: GmailCapability, val then: () -> Unit) : Pending
        class Confirm(val draft: Draft) : Pending
        class Text(val to: String?, val reply: Boolean) : Pending
        class Pick(val then: (GmailMessage) -> Unit) : Pending
        class Recipient(val candidates: List<OwnerSendRecipientResolver.Candidate>, val text: String?) : Pending
    }

    private class Open(val session: String, val turn: Long, val at: Long, val pending: Pending)

    private var session = Session("")
    private var open: Open? = null
    @Volatile private var stopRequested = false
    private var running = false

    private val dates: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMMM, HH:mm", Locale.GERMAN)

    private fun current(): Session {
        if (session.id != host.sessionId) { session = Session(host.sessionId); open = null }
        return session
    }

    override val results: List<MailRef> get() = current().list.map { it.ref }

    /** What is open in this chat, for the parser. */
    fun context(): MailContext {
        val s = current()
        val now = host.nowMillis()
        val pending = open?.takeIf { it.session == s.id && it.turn == host.ownerTurn && now - it.at in 0..PENDING_MILLIS }
        val immediate = s.lastTurn == host.ownerTurn
        return MailContext(
            // Short follow-ups („die zweite“, „Fass sie zusammen“) only right after this lane spoke.
            list = s.list.isNotEmpty() && immediate,
            mail = s.selected != null && immediate,
            draft = s.draft != null && now - s.draft!!.at in 0..FOLLOW_UP_MILLIS,
            confirming = pending?.pending is Pending.Confirm)
    }

    /** The stop word while a Gmail job runs: the job ends at its next check; nothing is reported as done. */
    fun intercept(request: String): Boolean {
        if (!running) return false
        if (OwnerMailCommands.route(request, host.assistantName, MailContext(list = true)) !is OwnerMailCommand.Stop) return false
        stopRequested = true
        host.activity("GMAIL · Stopp angefordert")
        return true
    }

    /**
     * Routing guard: false for „Suche in Gmail nach X“ while no Gmail account is connected – that sentence then stays
     * with the computer lane (types into the Gmail app's search). With a connection this lane answers it through the
     * API. Every other sentence: whether [route] takes it.
     */
    fun handles(request: String): Boolean {
        if (gmailAppSearch.containsMatchIn(request) && !host.connection().connected) return false
        val waiting = open?.takeIf { it.session == current().id && it.turn == host.ownerTurn }
        val context = context().copy(confirming = waiting?.pending is Pending.Confirm)
        return waiting != null || OwnerMailCommands.route(request, host.assistantName, context) != null
    }

    private val gmailAppSearch = Regex("(?iu)(?<![\\p{L}])in\\s+gmail\\s+nach(?![\\p{L}])")

    override fun handle(request: String): Boolean {
        if (gmailAppSearch.containsMatchIn(request) && !host.connection().connected) return false
        val s = current()
        val waiting = open?.takeIf { it.session == s.id && it.turn == host.ownerTurn && host.nowMillis() - it.at in 0..PENDING_MILLIS }
        open = null
        val context = context().copy(confirming = waiting?.pending is Pending.Confirm)
        val command = OwnerMailCommands.route(request, host.assistantName, context)
        if (waiting != null && answerPending(request, waiting.pending, command)) return true
        command ?: return false
        if (command is OwnerMailCommand.Confirm) return false
        host.acceptOwnerTurn(request)
        dispatch(request, command, context)
        return true
    }

    /** [context]: what was open when the owner said it (before his turn was accepted). */
    private fun dispatch(request: String, command: OwnerMailCommand, context: MailContext = MailContext.NONE) {
        val s = current()
        when (command) {
            OwnerMailCommand.Connect -> connect(request)
            OwnerMailCommand.Disconnect -> disconnect(request)
            OwnerMailCommand.Status -> status(request)
            is OwnerMailCommand.New -> search(request, MailQuery(unreadOnly = true, max = 8, label = "neue Mails"))
            OwnerMailCommand.Important -> important(request, summarise = false)
            OwnerMailCommand.ImportantWhy -> say(request, GmailImportance.CRITERIA + (if (host.contactsReadable()) "" else
                " Deine Kontakte darf ich gerade nicht lesen, deshalb zählt „von einem Kontakt“ im Moment für niemanden."))
            is OwnerMailCommand.Search -> search(request, MailQuery(command.from, command.about, command.day))
            is OwnerMailCommand.Latest -> search(request, MailQuery(from = command.from, max = 1, label = "letzte Mail von ${command.from}")) { outcome ->
                if (command.aloud && outcome is MailLaneOutcome.Done) current().list.firstOrNull()?.let { read(request, it.ref) }
            }
            is OwnerMailCommand.Read -> withMail(request, command.index) { message ->
                read(request, message.ref, command.thread)
            }
            OwnerMailCommand.ReadAttachment -> attachment(request)
            is OwnerMailCommand.Pick -> pick(request, command)?.let { read(request, it.ref) }
                ?: say(request, "Diese Mail finde ich in der Liste nicht. Sag mir die Nummer, 1 bis ${s.list.size}.")
            is OwnerMailCommand.Summarize -> when (command.target) {
                OwnerMailCommand.SummaryTarget.IMPORTANT -> important(request, summarise = true)
                OwnerMailCommand.SummaryTarget.NEW -> search(request, MailQuery(unreadOnly = true, max = 5, label = "neue Mails")) { outcome ->
                    if (outcome is MailLaneOutcome.Done && current().list.isNotEmpty()) summarize(request, current().list.map { it.ref })
                }
                OwnerMailCommand.SummaryTarget.THREAD -> withMail(request, null) { message -> summarizeThread(request, message) }
                OwnerMailCommand.SummaryTarget.CONTEXT -> when {
                    command.index != null -> withMail(request, command.index) { summarize(request, listOf(it.ref)) }
                    s.selected != null && (context.mail || !context.list) -> summarize(request, listOf(s.selected!!.ref))
                    s.list.isNotEmpty() -> summarize(request, s.list.take(MAX_SUMMARY_MAILS).map { it.ref })
                    else -> noMail(request)
                }
            }
            is OwnerMailCommand.Deadlines -> if (command.inbox) deadlinesInbox(request) else withMail(request, null) { deadlines(request, it) }
            is OwnerMailCommand.Draft -> draft(request, command.to, command.text)
            is OwnerMailCommand.Reply -> reply(request, command.text)
            OwnerMailCommand.Send -> send(request)
            is OwnerMailCommand.Confirm -> Unit
            OwnerMailCommand.Stop -> say(request, "In Ordnung, ich habe angehalten. Gesendet wurde nichts.").also {
                finish(MailLaneOutcome.Stopped)
            }
        }
    }

    // ── Pending questions ───────────────────────────────────────────────────────────────────────────────────

    private fun answerPending(request: String, pending: Pending, command: OwnerMailCommand?): Boolean {
        when (pending) {
            is Pending.Confirm -> {
                val answer = command as? OwnerMailCommand.Confirm ?: return false.also {
                    host.activity("GMAIL · Bestätigung verfallen · nichts gesendet")
                    host.board().finish(current().id, false, "Nicht gesendet – keine Bestätigung")
                }
                host.acceptOwnerTurn(request)
                if (answer.yes) sendConfirmed(request, pending.draft)
                else {
                    say(request, "In Ordnung, ich sende nichts. Der Entwurf bleibt in Gmail unter Entwürfe.")
                    host.board().finish(current().id, false, "Nicht gesendet – vom Besitzer abgelehnt")
                    finish(MailLaneOutcome.Stopped)
                }
                return true
            }
            is Pending.Grant -> {
                when {
                    isYes(request) -> {
                        host.acceptOwnerTurn(request)
                        requestRight(request, pending.capability, pending.then)
                    }
                    isNo(request) -> {
                        host.acceptOwnerTurn(request)
                        say(request, "In Ordnung, ich frage Google nicht. Es wurde nichts geändert.")
                        finish(MailLaneOutcome.Stopped)
                    }
                    else -> return false
                }
                return true
            }
            is Pending.Text -> {
                if (command != null && command !is OwnerMailCommand.Confirm) return false
                if (isNo(request)) {
                    host.acceptOwnerTurn(request)
                    say(request, "In Ordnung, kein Entwurf.")
                    finish(MailLaneOutcome.Stopped)
                    return true
                }
                val text = request.trim().takeIf { it.isNotEmpty() && it.length <= GmailMime.MAX_BODY_CHARS } ?: return false
                host.acceptOwnerTurn(request)
                if (pending.reply) reply(request, text) else draft(request, pending.to ?: return true, text)
                return true
            }
            is Pending.Pick -> {
                val pick = command as? OwnerMailCommand.Pick
                    ?: (command as? OwnerMailCommand.Read)?.index?.let { OwnerMailCommand.Pick(index = it) } ?: return false
                val message = pick(request, pick) ?: return false
                host.acceptOwnerTurn(request)
                pending.then(message)
                return true
            }
            is Pending.Recipient -> {
                val chosen = OwnerSendRecipientResolver.pick(request, pending.candidates) ?: return false
                host.acceptOwnerTurn(request)
                val text = pending.text
                if (text == null) draft(request, chosen.value, null) else draftTo(request, chosen.value, chosen.displayName, text, reply = null)
                return true
            }
        }
    }

    private fun ask(request: String, question: String, pending: Pending, need: OwnerLaneOutcome.Need, private: Boolean = false) {
        host.answer(request, question, question, private)
        current().lastTurn = host.ownerTurn
        open = Open(current().id, host.ownerTurn, host.nowMillis(), pending)
        host.outcome(OwnerLaneOutcome.NeedsOwner(need, question))
        host.mailOutcome(MailLaneOutcome.NeedsOwner(need, question))
    }

    private fun isYes(text: String) = at.heinz.deviceai.briefing.ContinuumNextSteps.isYes(text, host.assistantName) ||
        at.heinz.deviceai.sending.OwnerSendIntentParser.isYes(text)
    private fun isNo(text: String) = at.heinz.deviceai.briefing.ContinuumNextSteps.isNo(text, host.assistantName) ||
        text.trim().lowercase(Locale.GERMAN).trimEnd('.', '!') in setOf("abbrechen", "stopp", "stop", "nein doch nicht", "doch nicht")

    // ── Saying and settling ─────────────────────────────────────────────────────────────────────────────────

    private fun say(request: String?, text: String, private: Boolean = false, spoken: String = text) {
        host.answer(request, spoken, text, private)
        current().lastTurn = host.ownerTurn
    }

    /** The [done] of the API call that is running; called once with its terminal outcome. */
    private var waiter: ((MailLaneOutcome) -> Unit)? = null

    /** A terminal outcome (Done, Failed, Stopped): logged as typed outcome, handed to the waiting caller. */
    private fun finish(outcome: MailLaneOutcome) {
        host.mailOutcome(outcome)
        val generic: OwnerLaneOutcome? = when (outcome) {
            is MailLaneOutcome.Done -> when (val result = outcome.result) {
                is MailLaneResult.DraftCreated -> OwnerLaneOutcome.Done(OwnerLaneResult.Delivered("Gmail", result.to, null,
                    OwnerLaneResult.Delivered.State.DRAFT))
                is MailLaneResult.Sent -> OwnerLaneOutcome.Done(OwnerLaneResult.Delivered("Gmail", result.to, null,
                    OwnerLaneResult.Delivered.State.SENT))
                // OwnerLaneResult has no mail type yet (mission layer handover); typed listeners use mailOutcome.
                else -> null
            }
            is MailLaneOutcome.NeedsOwner -> OwnerLaneOutcome.NeedsOwner(outcome.need, outcome.question)
            is MailLaneOutcome.Failed -> OwnerLaneOutcome.Failed(outcome.reason)
            MailLaneOutcome.Stopped -> OwnerLaneOutcome.Stopped
        }
        generic?.let(host::outcome)
        val waiting = waiter
        waiter = null
        waiting?.invoke(outcome)
    }

    private fun failure(request: String?, error: Throwable, what: String, private: Boolean = false): MailLaneOutcome {
        if (error is CancellationException) {
            host.activity("GMAIL · $what · gestoppt")
            host.board().finish(current().id, false, "Gestoppt")
            say(request, "Gestoppt. $what ist nicht fertig geworden.")
            return MailLaneOutcome.Stopped.also { finish(it) }
        }
        val gmail = error as? GmailException
        gmail?.let { host.connection().ended(it.kind) }
        val reason = gmail?.message ?: "$what hat nicht geklappt (${error.javaClass.simpleName})."
        host.activity("GMAIL · $what · fehlgeschlagen · " + (gmail?.kind?.name ?: "FEHLER"))
        host.board().finish(current().id, false, "$what fehlgeschlagen")
        val offer = gmail?.takeIf { it.kind == GmailFailureKind.PERMISSION_REQUIRED || it.kind == GmailFailureKind.REVOKED }
            ?.let { " Sag „Verbinde Gmail“, dann fragt Google dich neu." } ?: ""
        say(request, reason + offer, private)
        return MailLaneOutcome.Failed(reason, gmail?.kind).also { finish(it) }
    }

    // ── Gates ───────────────────────────────────────────────────────────────────────────────────────────────

    /** null = Gmail may be used for [capability] now; otherwise the gate already answered the owner. */
    private fun gate(request: String, capability: GmailCapability, then: () -> Unit): Boolean {
        if (host.internetMode() == InternetAccessMode.OFF) {
            refuse(request, GmailFailureKind.INTERNET_OFF)
            return false
        }
        val connection = host.connection()
        if (!connection.connected) {
            refuse(request, GmailFailureKind.NOT_CONNECTED)
            return false
        }
        if (!connection.grant.granted(capability)) {
            ask(request, "Für „${capability.right}“ hast du Google noch keine Freigabe gegeben. Soll ich Google jetzt um genau " +
                "dieses Recht bitten? Google zeigt dir dazu seine eigene Seite." + (if (capability == GmailCapability.SEND)
                " " + GmailText.COMPOSE_NOTE else ""), Pending.Grant(capability, then), OwnerLaneOutcome.Need.GRANT)
            return false
        }
        return true
    }

    private fun refuse(request: String, kind: GmailFailureKind) {
        val text = GmailText.failure(kind, null)
        host.activity("GMAIL · nicht gestartet · " + kind.name)
        say(request, text)
        finish(MailLaneOutcome.Failed(text, kind))
    }

    private fun requestRight(request: String, capability: GmailCapability, then: () -> Unit) {
        host.activity("GMAIL · Google-Freigabe angefragt · ${capability.label}")
        host.authorize(setOf(capability)) { result ->
            when (result) {
                is GmailAuthResult.Granted -> {
                    host.connection().granted(result.grant)
                    if (result.grant.granted(capability)) then() else {
                        val text = "Google hat das Recht „${capability.right}“ nicht erteilt. Es wurde nichts geändert."
                        say(request, text)
                        finish(MailLaneOutcome.Failed(text, GmailFailureKind.PERMISSION_REQUIRED))
                    }
                }
                is GmailAuthResult.NeedsConsent -> failure(request, GmailException(GmailFailureKind.PERMISSION_REQUIRED, capability), "Freigabe")
                is GmailAuthResult.Failed -> failure(request, GmailException(result.kind, capability), "Freigabe")
            }
        }
    }

    // ── Connect, disconnect, status ─────────────────────────────────────────────────────────────────────────

    private fun connect(request: String) {
        host.activity("GMAIL · Verbinden · Googles Freigabeseite")
        connectRight(GmailCapability.READ) { ok, text, result ->
            say(request, text)
            finish(if (ok && result != null) MailLaneOutcome.Done(result) else MailLaneOutcome.Failed(text, host.connection().failure))
        }
    }

    /**
     * Settings → Verbindungen → Google Gmail: „Konto verbinden“ (READ), „Entwürfe erlauben“, „Senden erlauben“. Google's
     * own screen asks for exactly [capability]; [done] gets the sentence for the page. Nothing is said in the chat.
     */
    fun connectFromSettings(capability: GmailCapability, done: (String) -> Unit) {
        host.activity("GMAIL · Einstellungen · Google-Freigabe · ${capability.label}")
        connectRight(capability) { _, text, _ -> done(text) }
    }

    private fun connectRight(capability: GmailCapability, done: (Boolean, String, MailLaneResult?) -> Unit) {
        if (host.internetMode() == InternetAccessMode.OFF) {
            done(false, GmailText.failure(GmailFailureKind.INTERNET_OFF, null), null)
            return
        }
        val board = host.board()
        board.start(current().id, MailWorkBoard.Phase.CONNECT, "Google-Konto")
        host.authorize(setOf(capability)) { result ->
            when (result) {
                is GmailAuthResult.Granted -> {
                    host.connection().granted(result.grant)
                    val readable = result.grant.covers(GmailCapability.READ) || result.grant.covers(GmailCapability.DRAFTS)
                    if (!readable) {
                        board.finish(current().id, true, "Gmail verbunden")
                        done(true, connectedText(host.connection().account),
                            MailLaneResult.Connected(host.connection().account, host.connection().grant.capabilities))
                        return@authorize
                    }
                    val via = if (result.grant.covers(GmailCapability.READ)) GmailCapability.READ else GmailCapability.DRAFTS
                    job("Gmail verbinden", "Gmail-Konto prüfen", { api, _ -> api.profileAddress(via) }) { outcome ->
                        val address = outcome.getOrNull()
                        if (address != null) host.connection().granted(result.grant, address)
                        host.activity("GMAIL · verbunden · " + host.connection().grant.capabilities.joinToString("+") { it.name })
                        board.finish(current().id, true, "Gmail verbunden")
                        done(true, connectedText(address ?: host.connection().account) +
                            (if (address == null) " Die Adresse des Kontos konnte ich gerade nicht abrufen." else ""),
                            MailLaneResult.Connected(address ?: host.connection().account, host.connection().grant.capabilities))
                    }
                }
                is GmailAuthResult.NeedsConsent -> {
                    board.finish(current().id, false, "Nicht verbunden")
                    done(false, GmailText.failure(GmailFailureKind.PERMISSION_REQUIRED, capability), null)
                }
                is GmailAuthResult.Failed -> {
                    host.connection().ended(result.kind)
                    host.activity("GMAIL · Verbinden fehlgeschlagen · " + result.kind.name + (result.statusCode?.let { " · Status $it" } ?: ""))
                    board.finish(current().id, false, "Nicht verbunden")
                    done(false, GmailText.failure(result.kind, capability), null)
                }
            }
        }
    }

    private fun connectedText(address: String?): String {
        val rights = host.connection().grant.capabilities
        return "Gmail ist verbunden" + (address?.let { " mit $it" } ?: "") + ". Freigegeben: " + rights.joinToString(", ") { it.label } +
            "." + (if (GmailCapability.DRAFTS !in rights || GmailCapability.SEND !in rights) " Entwürfe und Senden erlaube ich erst, " +
            "wenn du sie brauchst – Google fragt dich dann einzeln." else "") + " Trennen kannst du jederzeit mit „Trenne Gmail“."
    }

    /** Settings: „Trennen“. [done] gets the sentence for the page. */
    fun disconnectFromSettings(done: (String) -> Unit) = revokeAndClear(done)

    private fun disconnect(request: String) {
        revokeAndClear { text ->
            say(request, text)
            finish(MailLaneOutcome.Done(MailLaneResult.Disconnected))
        }
    }

    private fun revokeAndClear(done: (String) -> Unit) {
        val connection = host.connection()
        val account = connection.account
        val grant = connection.grant
        host.activity("GMAIL · Trennen · Zugriff bei Google widerrufen")
        host.revoke(account, grant) { problem ->
            connection.clear()
            host.usage().forget(ContinuumConnectionKind.GMAIL)
            session = Session(host.sessionId)
            open = null
            host.board().clear()
            host.activity("GMAIL · getrennt · " + if (problem == null) "bei Google widerrufen" else "nur lokal")
            done(if (problem == null) "Gmail ist getrennt. Google hat den Zugriff von Continuum widerrufen, und in der App ist " +
                "nichts mehr davon gespeichert. Nachsehen kannst du das unter myaccount.google.com/permissions."
                else "In der App ist Gmail getrennt und nichts mehr gespeichert. Bei Google konnte ich den Zugriff aber nicht " +
                    "widerrufen (" + GmailText.failure(problem, null).trimEnd('.') + "). Widerrufe ihn bitte selbst unter " +
                    "myaccount.google.com/permissions.")
        }
    }

    private fun status(request: String) {
        val connection = host.connection()
        val view = connection.view()
        val text = if (!view.connected) (view.failureText ?: "Gmail ist nicht verbunden.") + " Verbinden: „Verbinde Gmail“."
        else "Gmail ist verbunden" + (view.account?.let { " mit $it" } ?: "") + ". Freigegeben: " + view.rights.joinToString(", ") +
            ". Mailinhalte an die Cloud: " + (if (connection.cloudContent) "erlaubt" else "nicht erlaubt") + "." +
            (view.failureText?.let { " Zuletzt: $it" } ?: "")
        host.activity("GMAIL · Status · nur gelesen")
        say(request, text)
        finish(MailLaneOutcome.Done(MailLaneResult.Connected(view.account, connection.grant.capabilities)))
    }

    // ── Jobs ────────────────────────────────────────────────────────────────────────────────────────────────

    /** One Gmail job: usage marked IN_USE while it runs, a fresh token source, the stop word honoured. */
    private fun <T> job(request: String, label: String, work: (GmailApi, OwnerMailJob) -> T, done: (Result<T>) -> Unit) {
        stopRequested = false
        running = true
        val connection = host.connection()
        host.runJob(request, label, { job ->
            val tokens = GmailTokenSource(host.authorizer(), { connection.grant.granted(it) }, { connection.granted(it) })
            val api = GmailApi(host.transport(), tokens, cancelled = { stopRequested || job.cancelled() })
            host.usage().use(ContinuumConnectionKind.GMAIL, { error -> (error as? GmailException)?.message }) { work(api, job) }
        }) { result ->
            running = false
            if (result.isSuccess) connection.ended(null)
            done(result)
        }
    }

    private fun fetchMetadata(api: GmailApi, job: OwnerMailJob, ids: List<Pair<String, String>>): List<GmailMessage> {
        val board = host.board()
        val id = current().id
        return ids.mapNotNull { (message, _) ->
            if (job.cancelled() || stopRequested) throw CancellationException("gestoppt")
            GmailPayloadParser.parse(api.messageMetadata(message), withBody = false).also { board.arrived(id, ids.size) }
        }
    }

    private fun fetchFull(api: GmailApi, ref: MailRef, total: Int = 1): GmailMessage {
        val parsed = GmailPayloadParser.parse(api.messageFull(ref.id), withBody = true)
            ?: throw GmailException(GmailFailureKind.INVALID_RESPONSE, GmailCapability.READ)
        host.board().arrived(current().id, total)
        return parsed
    }

    // ── Search, list, important ─────────────────────────────────────────────────────────────────────────────

    override fun search(request: String, query: MailQuery, done: (MailLaneOutcome) -> Unit) {
        waiter = done
        if (!gate(request, GmailCapability.READ) { search(request, query, done) }) return
        val s = current()
        val zone = host.zone()
        val today = Instant.ofEpochMilli(host.nowMillis()).atZone(zone).toLocalDate()
        val board = host.board()
        board.start(s.id, MailWorkBoard.Phase.SEARCH, query.spoken())
        host.activity("GMAIL · Suche · " + query.spoken().take(60) + " · nur lesen")
        job(request, "Gmail durchsuchen", { api, job ->
            job.step(STEP_SEARCH, "Gmail durchsuchen", StepState.ACTIVE)
            val ids = api.listMessages(query.gmail(zone, today), query.max.coerceIn(1, MAX_LIST))
            board.found(s.id, ids.size)
            fetchMetadata(api, job, ids).also { job.step(STEP_SEARCH, "${it.size} Mails gefunden", StepState.DONE) }
        }) { result ->
            result.onSuccess { messages ->
                s.list = messages
                s.listAt = host.nowMillis()
                s.listQuery = query
                s.selected = null
                board.cards(s.id, messages.map { it.ref }, messages.filter { it.unread }.map { it.ref.id }.toSet(), null)
                val line = if (messages.isEmpty()) "Keine Mail gefunden" else "${messages.size} Mails gefunden"
                board.finish(s.id, true, line)
                host.activity("GMAIL · Suche · ${messages.size} Treffer · nur gelesen")
                if (messages.isEmpty()) say(request, "Zu „${query.spoken()}“ habe ich in deinem Posteingang keine Mail gefunden.")
                else say(request, listText(messages, null), private = true, spoken = listSpoken(messages))
                val outcome = MailLaneOutcome.Done(MailLaneResult.Found(query.spoken(), messages.map { it.ref }))
                finish(outcome)
            }.onFailure { failure(request, it, "Die Suche") }
        }
    }

    private fun important(request: String, summarise: Boolean) {
        if (!gate(request, GmailCapability.READ) { important(request, summarise) }) return
        val s = current()
        val board = host.board()
        board.start(s.id, MailWorkBoard.Phase.SEARCH, "wichtige Mails · letzte 7 Tage")
        host.activity("GMAIL · wichtige Mails · feste Punkte, kein Modell")
        val contacts = if (host.contactsReadable()) runCatching { host.contacts().mails().map { it.address.trim().lowercase() }.toSet() }.getOrNull() else null
        job(request, "Wichtige Mails suchen", { api, job ->
            val ids = api.listMessages("in:inbox newer_than:7d", 20)
            board.found(s.id, ids.size)
            fetchMetadata(api, job, ids)
        }) { result ->
            result.onSuccess { messages ->
                val ranked = GmailImportance.rank(messages, contacts, host.connection().account, host.nowMillis()).take(MAX_IMPORTANT)
                s.list = ranked.map { it.message }
                s.listAt = host.nowMillis()
                s.selected = null
                board.cards(s.id, s.list.map { it.ref }, s.list.filter { it.unread }.map { it.ref.id }.toSet(), null)
                board.finish(s.id, true, "${ranked.size} wichtige Mails")
                host.activity("GMAIL · wichtige Mails · ${ranked.size} von ${messages.size}")
                if (ranked.isEmpty()) {
                    say(request, "In den letzten sieben Tagen ist in deinem Posteingang keine Mail.")
                    finish(MailLaneOutcome.Done(MailLaneResult.Found("wichtige Mails", emptyList())))
                    return@onSuccess
                }
                if (summarise) { summarize(request, s.list.map { it.ref }); return@onSuccess }
                say(request, listText(s.list, ranked.associate { it.message.ref.id to it.reasons }) +
                    "\n\nNach welchen Punkten ich ordne, sage ich dir auf „Warum sind die wichtig?“.", private = true,
                    spoken = listSpoken(s.list))
                finish(MailLaneOutcome.Done(MailLaneResult.Found("wichtige Mails", s.list.map { it.ref })))
            }.onFailure { failure(request, it, "Die Suche") }
        }
    }

    private fun dateLabel(millis: Long): String {
        val zone = host.zone()
        val at = Instant.ofEpochMilli(millis).atZone(zone)
        val today = Instant.ofEpochMilli(host.nowMillis()).atZone(zone).toLocalDate()
        return when (at.toLocalDate()) {
            today -> "heute " + String.format(Locale.GERMAN, "%02d:%02d", at.hour, at.minute)
            today.minusDays(1) -> "gestern " + String.format(Locale.GERMAN, "%02d:%02d", at.hour, at.minute)
            else -> dates.format(at)
        }
    }

    private fun listText(messages: List<GmailMessage>, reasons: Map<String, List<String>>?): String = buildString {
        append(if (messages.size == 1) "Ich habe eine Mail gefunden:" else "Ich habe ${messages.size} Mails gefunden:")
        messages.forEachIndexed { index, message ->
            append("\n").append(index + 1).append(". ").append(message.ref.from).append(" – „").append(message.ref.subject).append("“ – ")
                .append(dateLabel(message.ref.dateMillis)).append(if (message.unread) ", ungelesen" else "")
            reasons?.get(message.ref.id)?.takeIf { it.isNotEmpty() }?.let { append(" (").append(it.joinToString(", ")).append(")") }
        }
        append("\n\nSag zum Beispiel „Lies die erste vor“, „die zweite“ oder „Fass sie zusammen“.")
    }

    private fun listSpoken(messages: List<GmailMessage>): String = (if (messages.size == 1) "Eine Mail: " else "${messages.size} Mails. ") +
        messages.take(3).mapIndexed { index, message -> "${index + 1}: ${message.ref.from}, ${message.ref.subject}" }.joinToString(". ") +
        (if (messages.size > 3) ". Und weitere." else ".")

    // ── Picking and reading ─────────────────────────────────────────────────────────────────────────────────

    private fun pick(request: String, command: OwnerMailCommand.Pick): GmailMessage? {
        val list = current().list
        if (list.isEmpty()) return null
        val zone = host.zone()
        val today = Instant.ofEpochMilli(host.nowMillis()).atZone(zone).toLocalDate()
        return when {
            command.index == -1 -> list.last()
            command.index != null -> list.getOrNull(command.index - 1)
            command.day != null -> {
                val day = if (command.day == MailDay.TODAY) today else today.minusDays(1)
                list.filter { Instant.ofEpochMilli(it.ref.dateMillis).atZone(zone).toLocalDate() == day }.singleOrNull()
            }
            command.from != null -> list.filter { it.ref.from.contains(command.from, ignoreCase = true) ||
                it.fromAddress?.contains(command.from, ignoreCase = true) == true }.singleOrNull()
            else -> null
        }
    }

    /** The mail meant by [index] or by the conversation; asks when several are possible. */
    private fun withMail(request: String, index: Int?, then: (GmailMessage) -> Unit) {
        val s = current()
        if (index != null) {
            s.list.getOrNull(index - 1)?.let { then(it); return }
            if (s.list.isEmpty()) { noMail(request); return }
            say(request, "In der Liste stehen ${s.list.size} Mails. Welche meinst du?")
            return
        }
        s.selected?.let { then(it); return }
        when (s.list.size) {
            0 -> noMail(request)
            1 -> then(s.list.single())
            else -> ask(request, "Welche Mail meinst du – 1 bis ${s.list.size}?", Pending.Pick(then), OwnerLaneOutcome.Need.CHOICE)
        }
    }

    private fun noMail(request: String) {
        val text = "In diesem Gespräch habe ich noch keine Mail gezeigt. Sag zum Beispiel „Zeig mir neue Mails“ oder „Suche Mails von Anna“."
        say(request, text)
        finish(MailLaneOutcome.Failed(text))
    }

    override fun read(request: String, ref: MailRef, thread: Boolean, done: (MailLaneOutcome) -> Unit) {
        waiter = done
        if (!gate(request, GmailCapability.READ) { read(request, ref, thread, done) }) return
        val s = current()
        val board = host.board()
        board.start(s.id, MailWorkBoard.Phase.READ)
        board.select(s.id, ref.id, s.list.map { it.ref })
        host.activity("GMAIL · " + (if (thread) "Verlauf" else "Mail") + " lesen · nur lesen")
        job(request, if (thread) "Verlauf lesen" else "Mail lesen", { api, job ->
            job.step(STEP_READ, if (thread) "Verlauf lesen" else "Mail lesen", StepState.ACTIVE)
            val messages = if (thread) GmailApi.threadMessages(api.thread(ref.threadId)).takeLast(MAX_THREAD)
                .mapNotNull { GmailPayloadParser.parse(it, withBody = true, maxBodyChars = THREAD_BODY_CHARS) }
                .also { list -> list.forEach { _ -> board.arrived(s.id, list.size) } }
            else listOf(fetchFull(api, ref))
            job.step(STEP_READ, "Gelesen", StepState.DONE)
            messages
        }) { result ->
            result.onSuccess { messages ->
                if (messages.isEmpty()) { failure(request, GmailException(GmailFailureKind.NOT_FOUND), "Das Lesen"); return@onSuccess }
                val last = messages.last()
                s.selected = messages.firstOrNull { it.ref.id == ref.id } ?: last
                s.selectedAt = host.nowMillis()
                s.thread = if (thread) messages else emptyList()
                board.finish(s.id, true, if (thread) "${messages.size} Mails des Verlaufs gelesen" else "Mail gelesen")
                host.activity("GMAIL · gelesen · ${messages.size} " + (if (messages.size == 1) "Mail" else "Mails") + " · Inhalt nur angezeigt")
                val shown = messages.joinToString("\n\n― ― ―\n\n", transform = ::mailBlock)
                val spoken = messages.joinToString(" ") { "Mail von ${it.ref.from}, Betreff ${it.ref.subject}. " + it.text.take(SPOKEN_BODY_CHARS) }
                say(request, READ_PREFIX + "\n\n" + shown.take(MAX_SHOWN_CHARS), private = true, spoken = spoken.take(MAX_SPOKEN_CHARS))
                val texts = messages.map { MailText(it.ref, it.text, it.truncated, it.attachments) }
                val outcome = MailLaneOutcome.Done(if (thread) MailLaneResult.Thread(texts) else MailLaneResult.Read(texts.last()))
                finish(outcome)
            }.onFailure { failure(request, it, "Das Lesen") }
        }
    }

    private fun mailBlock(message: GmailMessage): String = buildString {
        append("Von: ").append(message.ref.from).append(message.fromAddress?.let { " <$it>" } ?: "").append('\n')
        append("Datum: ").append(dateLabel(message.ref.dateMillis)).append('\n')
        append("Betreff: ").append(message.ref.subject).append("\n\n")
        append(message.text.ifBlank { "(kein lesbarer Text)" })
        if (message.truncated) append("\n\n[gekürzt – die Mail ist länger]")
        if (message.fromHtml) append("\n\n[aus der HTML-Fassung der Mail gelesen]")
        if (message.attachments.isNotEmpty()) append("\n\nAnhänge: ").append(message.attachments.joinToString(", ") {
            it.name + " (" + size(it.size) + ")" }).append(" – geöffnet wird ein Anhang nur auf deinen Auftrag („Lies den Anhang vor“).")
    }

    private fun size(bytes: Int): String = when {
        bytes >= 1_048_576 -> String.format(Locale.GERMAN, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024 -> "${bytes / 1_024} KB"
        else -> "$bytes Bytes"
    }

    private fun attachment(request: String) {
        val message = current().selected ?: return noMail(request)
        val readable = message.attachments.filter { it.mime.startsWith("text/") && it.size in 1..MAX_ATTACHMENT_BYTES }
        if (message.attachments.isEmpty()) { say(request, "Diese Mail hat keinen Anhang."); finish(MailLaneOutcome.Failed("kein Anhang")); return }
        if (readable.isEmpty()) {
            val text = "Anhänge dieser Art (" + message.attachments.joinToString(", ") { it.name } + ") kann ich in dieser Version " +
                "noch nicht lesen – gebaut ist nur reiner Text bis ${MAX_ATTACHMENT_BYTES / 1024} KB. PDF-Anhänge öffnest du am " +
                "besten in Gmail und lässt sie mit „Öffne das PDF …“ lesen, sobald sie gespeichert sind."
            say(request, text)
            finish(MailLaneOutcome.Failed(text))
            return
        }
        if (!gate(request, GmailCapability.READ) { attachment(request) }) return
        val target = readable.first()
        host.activity("GMAIL · Anhang lesen · ${target.mime} · nur lesen")
        job(request, "Anhang lesen", { api, _ -> api.attachment(target.messageId, target.attachmentId, MAX_ATTACHMENT_BYTES) }) { result ->
            result.onSuccess { bytes ->
                val text = GmailPayloadParser.normalise(String(bytes, Charsets.UTF_8)).take(MAX_SHOWN_CHARS)
                host.activity("GMAIL · Anhang gelesen · ${bytes.size} Bytes · nur angezeigt")
                say(request, READ_PREFIX + "\n\nAnhang „${target.name}“:\n\n" + text, private = true, spoken = text.take(MAX_SPOKEN_CHARS))
                finish(MailLaneOutcome.Done(MailLaneResult.Read(MailText(message.ref, text, bytes.size >= MAX_SHOWN_CHARS, emptyList()))))
            }.onFailure { failure(request, it, "Das Lesen des Anhangs") }
        }
    }

    // ── Summaries ───────────────────────────────────────────────────────────────────────────────────────────

    private enum class Brain { LOCAL, CLOUD, NONE }

    /** The brain the owner's settings allow for mail text, and the one sentence that explains a restriction. */
    private fun brain(): Pair<Brain, String?> {
        val cloud = host.cloudBrain()
        val consent = host.connection().cloudContent
        val local = host.onDeviceModelReady()
        return when {
            !cloud -> (if (local) Brain.LOCAL else Brain.NONE) to (if (local) null else
                "Das Sprachmodell auf dem Gerät ist nicht geladen; ich zeige dir Absender, Betreff und Datum.")
            consent -> Brain.CLOUD to null
            local -> Brain.LOCAL to CLOUD_NOT_ALLOWED + " Ich fasse mit dem Modell auf dem Gerät zusammen."
            else -> Brain.NONE to CLOUD_NOT_ALLOWED + " Ein Modell auf dem Gerät ist gerade nicht geladen, deshalb nenne ich dir nur " +
                "Absender, Betreff und Datum. Erlauben kannst du es unter Einstellungen → Verbindungen → Google Gmail → „${GmailText.CLOUD_SWITCH}“."
        }
    }

    override fun summarize(request: String, refs: List<MailRef>, done: (MailLaneOutcome) -> Unit) {
        waiter = done
        if (refs.isEmpty()) { noMail(request); return }
        if (!gate(request, GmailCapability.READ) { summarize(request, refs, done) }) return
        val (brain, note) = brain()
        val s = current()
        val board = host.board()
        if (brain == Brain.NONE) {
            val known = refs.map { ref -> s.list.firstOrNull { it.ref.id == ref.id } ?: s.selected?.takeIf { it.ref.id == ref.id } }
            val lines = refs.mapIndexed { index, ref -> "${index + 1}. ${ref.from} – „${ref.subject}“ – ${dateLabel(ref.dateMillis)}" +
                (if (known[index]?.unread == true) ", ungelesen" else "") }
            host.activity("GMAIL · Zusammenfassen nicht möglich · kein erlaubtes Modell · nur Kopfzeilen")
            board.finish(s.id, false, "Nur Absender und Betreff – kein erlaubtes Modell")
            val text = (note ?: "") + "\n\n" + lines.joinToString("\n")
            say(request, text.trim(), private = true, spoken = note ?: "")
            finish(MailLaneOutcome.Failed(note ?: "Kein Modell"))
            return
        }
        val cloud = brain == Brain.CLOUD
        board.start(s.id, MailWorkBoard.Phase.SUMMARIZE)
        host.activity("GMAIL · Zusammenfassen · " + (if (cloud) "Cloud-Gehirn mit deiner Freigabe" else "Modell auf dem Gerät") +
            " · ${refs.size} Mails")
        job(request, "Mails zusammenfassen", { api, job ->
            job.step(STEP_READ, "Mails lesen", StepState.ACTIVE)
            val messages = refs.take(MAX_SUMMARY_MAILS).map { ref ->
                if (job.cancelled() || stopRequested) throw CancellationException("gestoppt")
                s.selected?.takeIf { it.ref.id == ref.id && it.body != null } ?: fetchFull(api, ref, refs.size)
            }
            job.step(STEP_READ, "${messages.size} Mails gelesen", StepState.DONE)
            val prompt = MailPrompt.summary(messages, host.zone(), host.promptBudgetBytes(), cloud)
                ?: throw IllegalStateException("Die Mails haben keinen Text, den ich zusammenfassen könnte")
            job.step(STEP_SUMMARY, "Zusammenfassen", StepState.ACTIVE)
            val generated = job.generate(prompt.text, cloud)
            if (job.cancelled() || stopRequested) throw CancellationException("gestoppt")
            val accepted = if (generated.failure == null && generated.complete) MailPrompt.accept(generated.text) else null
            job.step(STEP_SUMMARY, if (accepted != null) "Zusammengefasst" else "Zusammenfassen fehlgeschlagen",
                if (accepted != null) StepState.DONE else StepState.FAILED)
            Triple(messages, accepted ?: throw IllegalStateException(generated.failure?.let { "das Modell lieferte keine Zusammenfassung ($it)" }
                ?: "das Modell lieferte keine brauchbare Zusammenfassung"), prompt.complete)
        }) { result ->
            result.onSuccess { (messages, text, complete) ->
                val author = if (cloud) "über das Cloud-Gehirn (mit deiner Freigabe für Mailinhalte)" else "mit dem Modell auf dem Gerät"
                val basis = if (complete) "" else " Grundlage ist ein Auszug; lange Mails sind gekürzt."
                val title = if (messages.size == 1) "Zusammenfassung Mail ${messages.single().ref.from.take(40)}" else "Zusammenfassung ${messages.size} Mails"
                host.summarised(title, text, messages.size, cloud, complete)
                board.finish(s.id, true, "Zusammenfassung von ${messages.size} Mails")
                host.activity("GMAIL · zusammengefasst · ${messages.size} Mails · " + if (cloud) "Cloud" else "Gerät")
                val shown = (note?.let { "$it\n\n" } ?: "") + text + "\n\nZusammengefasst $author aus ${messages.size} " +
                    (if (messages.size == 1) "Mail" else "Mails") + ".$basis Sag „Mach daraus ein PDF“, dann speichere ich sie dir."
                say(request, shown, private = true, spoken = (note?.let { "$it " } ?: "") + text)
                val outcome = MailLaneOutcome.Done(MailLaneResult.Summary(messages.map { it.ref }, text, cloud, complete))
                finish(outcome)
            }.onFailure { failure(request, it, "Das Zusammenfassen") }
        }
    }

    private fun summarizeThread(request: String, message: GmailMessage) {
        val thread = current().thread.takeIf { list -> list.any { it.ref.threadId == message.ref.threadId } }
        if (thread != null) summarize(request, thread.map { it.ref }) else read(request, message.ref, thread = true) { outcome ->
            if (outcome is MailLaneOutcome.Done) summarize(request, current().thread.map { it.ref })
        }
    }

    // ── Deadlines (deterministic, quoted) ───────────────────────────────────────────────────────────────────

    private fun deadlines(request: String, message: GmailMessage) {
        if (message.body != null) { answerDeadlines(request, listOf(message)); return }
        if (!gate(request, GmailCapability.READ) { deadlines(request, message) }) return
        job(request, "Mail lesen", { api, _ -> fetchFull(api, message.ref) }) { result ->
            result.onSuccess { full -> current().selected = full; answerDeadlines(request, listOf(full)) }
                .onFailure { failure(request, it, "Das Lesen") }
        }
    }

    private fun deadlinesInbox(request: String) {
        if (!gate(request, GmailCapability.READ) { deadlinesInbox(request) }) return
        val s = current()
        host.board().start(s.id, MailWorkBoard.Phase.READ, "Fristen · letzte 14 Tage")
        job(request, "Fristen suchen", { api, job ->
            val ids = api.listMessages("in:inbox newer_than:14d", MAX_DEADLINE_MAILS)
            host.board().found(s.id, ids.size)
            ids.map { (id, thread) ->
                if (job.cancelled() || stopRequested) throw CancellationException("gestoppt")
                fetchFull(api, MailRef(id, thread, "", "", 0L), ids.size)
            }
        }) { result ->
            result.onSuccess { messages ->
                s.list = messages
                s.listAt = host.nowMillis()
                answerDeadlines(request, messages)
            }.onFailure { failure(request, it, "Die Fristensuche") }
        }
    }

    private fun answerDeadlines(request: String, messages: List<GmailMessage>) {
        val found = messages.map { it to GmailDeadlines.find(it.text) }.filter { it.second.isNotEmpty() }
        host.activity("GMAIL · Fristen · ${found.sumOf { it.second.size }} Fundstellen in ${messages.size} Mails · ohne Modell")
        host.board().finish(current().id, true, "${found.sumOf { it.second.size }} Fundstellen")
        if (found.isEmpty()) {
            say(request, if (messages.size == 1) "In dieser Mail finde ich kein Datum, keine Uhrzeit und kein Fristwort."
                else "In den ${messages.size} Mails finde ich kein Datum, keine Uhrzeit und kein Fristwort.", private = true)
            finish(MailLaneOutcome.Done(MailLaneResult.Deadlines(messages.map { it.ref }, emptyList())))
            return
        }
        val text = buildString {
            append("Diese Stellen nennen ein Datum, eine Uhrzeit oder eine Frist – wörtlich zitiert, nichts davon ist von mir berechnet:")
            found.forEach { (message, lines) ->
                append("\n\nMail von ").append(message.ref.from).append(" („").append(message.ref.subject).append("“):")
                lines.forEach { append("\n„").append(it.line).append("“") }
            }
        }
        say(request, text, private = true)
        finish(MailLaneOutcome.Done(MailLaneResult.Deadlines(found.map { it.first.ref }, found.flatMap { it.second.map { e -> e.line } })))
    }

    // ── Drafts and replies ──────────────────────────────────────────────────────────────────────────────────

    private fun draft(request: String, to: String, text: String?) {
        if (text == null) {
            ask(request, "Was soll im Entwurf an $to stehen? Sag mir den Text.", Pending.Text(to, reply = false), OwnerLaneOutcome.Need.DETAIL)
            return
        }
        if (!gate(request, GmailCapability.DRAFTS) { draft(request, to, text) }) return
        if (GmailMime.validAddress(to)) { draftTo(request, to.lowercase(), to, text, reply = null); return }
        if (!host.contactsReadable()) {
            host.requestContacts(CONTACTS_EXPLANATION) { granted ->
                if (granted) draft(request, to, text) else {
                    val said = "Ohne Zugriff auf die Kontakte finde ich die Adresse von $to nicht. Es wurde kein Entwurf angelegt. " +
                        "Du kannst mir die Adresse auch direkt sagen."
                    say(request, said)
                    finish(MailLaneOutcome.Failed(said))
                }
            }
            return
        }
        val rows = runCatching { host.contacts().mails() }.getOrElse {
            val said = "Das Adressbuch konnte ich gerade nicht lesen. Es wurde kein Entwurf angelegt."
            say(request, said); finish(MailLaneOutcome.Failed(said)); return
        }
        when (val resolution = OwnerSendRecipientResolver.mail(to, rows)) {
            is OwnerSendRecipientResolver.Resolution.Resolved -> draftTo(request, resolution.candidate.value, resolution.candidate.displayName, text, null)
            is OwnerSendRecipientResolver.Resolution.Ambiguous -> ask(request, "Zu „${resolution.asked}“ gibt es mehrere Adressen: " +
                resolution.candidates.joinToString(", ") { it.label } + ". Welche meinst du?",
                Pending.Recipient(resolution.candidates, text), OwnerLaneOutcome.Need.CHOICE)
            is OwnerSendRecipientResolver.Resolution.NotFound -> {
                val said = "Für „${resolution.asked}“ finde ich im Adressbuch keine E-Mail-Adresse. Es wurde kein Entwurf angelegt."
                say(request, said); finish(MailLaneOutcome.Failed(said))
            }
            is OwnerSendRecipientResolver.Resolution.Unusable -> { say(request, resolution.reason); finish(MailLaneOutcome.Failed(resolution.reason)) }
        }
    }

    private fun reply(request: String, text: String?) {
        val message = current().selected ?: return noMail(request)
        val address = message.fromAddress ?: run {
            val said = "Bei dieser Mail fehlt eine Absenderadresse, auf die ich antworten könnte. Es wurde nichts angelegt."
            say(request, said); finish(MailLaneOutcome.Failed(said)); return
        }
        if (text == null) {
            ask(request, "Was soll ich ${message.ref.from} antworten?", Pending.Text(null, reply = true), OwnerLaneOutcome.Need.DETAIL)
            return
        }
        if (!gate(request, GmailCapability.DRAFTS) { reply(request, text) }) return
        draftTo(request, address, message.ref.from, text, message)
    }

    /** drafts.create; [reply]: the mail answered (same thread, In-Reply-To/References, „Re:“ subject). */
    private fun draftTo(request: String, address: String, shownName: String, text: String, reply: GmailMessage?) {
        val subject = reply?.let { GmailMime.replySubject(it.ref.subject) } ?: subjectOf(text)
        val mail = GmailOutgoing(address, subject, text, reply?.ref?.threadId, reply?.messageIdHeader,
            reply?.let { listOfNotNull(it.references, it.messageIdHeader).joinToString(" ") })
        val raw = runCatching { GmailMime.raw(mail) }.getOrElse { error ->
            val said = "Den Entwurf konnte ich nicht bauen: ${error.message}. Es wurde nichts angelegt."
            say(request, said); finish(MailLaneOutcome.Failed(said)); return
        }
        val s = current()
        val shownTo = if (shownName.equals(address, true)) address else "$shownName <$address>"
        host.board().start(s.id, MailWorkBoard.Phase.DRAFT)
        host.activity("GMAIL · Entwurf anlegen · nichts wird gesendet")
        job(request, "Entwurf anlegen", { api, job ->
            job.step(STEP_DRAFT, "Entwurf anlegen", StepState.ACTIVE)
            api.createDraft(raw, mail.threadId).also { job.step(STEP_DRAFT, "Entwurf angelegt", StepState.DONE) }
        }) { result ->
            result.onSuccess { id ->
                s.draft = Draft(id, mail, shownTo, host.nowMillis())
                host.board().finish(s.id, true, "Entwurf angelegt – nicht gesendet")
                host.activity("GMAIL · Entwurf angelegt · $id · nicht gesendet")
                say(request, "Der Entwurf an $shownTo liegt jetzt in Gmail unter Entwürfe – Betreff „$subject“" +
                    (if (reply != null) ", als Antwort im selben Verlauf" else "") + ". Gesendet habe ich nichts. Sag „Schick die Mail ab“, " +
                    "wenn er so hinausgehen soll – ich zeige dir dann alles noch einmal zur Bestätigung.", private = reply != null)
                finish(MailLaneOutcome.Done(MailLaneResult.DraftCreated(id, address, subject, mail.threadId)))
            }.onFailure { failure(request, it, "Der Entwurf") }
        }
    }

    private fun subjectOf(text: String): String {
        val first = text.trim().lines().first().split(Regex("(?<=[.!?])\\s")).first().trim()
        return if (first.length <= 60) first else first.take(57).trimEnd() + "…"
    }

    // ── Sending: SEND right + confirmation in the very next turn ────────────────────────────────────────────

    private fun send(request: String) {
        val draft = current().draft?.takeIf { host.nowMillis() - it.at in 0..FOLLOW_UP_MILLIS } ?: run {
            val said = "In diesem Gespräch gibt es keinen Entwurf, den ich senden könnte. Sag zuerst zum Beispiel " +
                "„Schreib einen Entwurf an Anna: …“. Gesendet wurde nichts."
            say(request, said); finish(MailLaneOutcome.Failed(said)); return
        }
        if (!gate(request, GmailCapability.SEND) { confirmation(request, draft) }) return
        confirmation(request, draft)
    }

    private fun confirmation(request: String, draft: Draft) {
        if (!host.connection().grant.maySend) {
            val said = "Ohne dein eigenes Senderecht bei Google sende ich nicht. Gesendet wurde nichts."
            say(request, said); finish(MailLaneOutcome.Failed(said, GmailFailureKind.PERMISSION_REQUIRED)); return
        }
        host.board().start(current().id, MailWorkBoard.Phase.SEND)
        host.activity("GMAIL · Senden · wartet auf deine Bestätigung")
        val question = "Soll ich diese Mail jetzt senden?\n\nAn: ${draft.shownTo}\nBetreff: ${draft.mail.subject}\nText:\n${draft.mail.body}" +
            "\n\nSag „Ja, senden“ – oder „Nein“."
        ask(request, question, Pending.Confirm(draft), OwnerLaneOutcome.Need.CONFIRMATION,
            private = draft.mail.threadId != null)
    }

    private fun sendConfirmed(request: String, draft: Draft) {
        if (host.internetMode() == InternetAccessMode.OFF) { refuse(request, GmailFailureKind.INTERNET_OFF); return }
        if (!host.connection().grant.maySend) {
            val said = "Das Senderecht gilt nicht mehr. Gesendet wurde nichts."
            say(request, said); finish(MailLaneOutcome.Failed(said, GmailFailureKind.PERMISSION_REQUIRED)); return
        }
        val raw = GmailMime.raw(draft.mail)
        val s = current()
        // The draft is used up by this confirmation: a second „senden“ needs a new draft and a new confirmation.
        s.draft = null
        host.activity("GMAIL · Senden · vom Besitzer bestätigt · einmal")
        job(request, "Mail senden", { api, job ->
            job.step(STEP_SEND, "Mail senden", StepState.ACTIVE)
            api.send(raw, draft.mail.threadId).also { job.step(STEP_SEND, "Gesendet", StepState.DONE) }
        }) { result ->
            result.onSuccess { id ->
                host.board().finish(s.id, true, "Gesendet")
                host.activity("GMAIL · gesendet · $id")
                say(request, "Gesendet: Gmail hat die Mail an ${draft.shownTo} angenommen (Betreff „${draft.mail.subject}“). " +
                    "Der Entwurf liegt weiter unter Entwürfe; du kannst ihn dort löschen.")
                finish(MailLaneOutcome.Done(MailLaneResult.Sent(id, draft.mail.to, draft.mail.subject)))
            }.onFailure { error ->
                val unclear = (error as? GmailException)?.kind == GmailFailureKind.SERVER || (error as? GmailException)?.kind == GmailFailureKind.INTERRUPTED
                if (unclear) {
                    val said = "Ob die Mail angekommen ist, ist unklar: Die Verbindung zu Gmail brach ab oder Gmail meldete einen Fehler. " +
                        "Bitte sieh in Gmail unter Gesendet nach – ich sende sie nicht noch einmal."
                    host.activity("GMAIL · Senden · Ergebnis unklar · nicht wiederholt")
                    host.board().finish(s.id, false, "Senden unklar – nicht wiederholt")
                    say(request, said)
                    finish(MailLaneOutcome.Failed(said, (error as GmailException).kind))
                } else failure(request, error, "Das Senden")
            }
        }
    }

    companion object {
        const val PENDING_MILLIS = 5 * 60_000L
        const val FOLLOW_UP_MILLIS = 30 * 60_000L
        const val MAX_LIST = 10
        const val MAX_IMPORTANT = 5
        const val MAX_SUMMARY_MAILS = 5
        const val MAX_DEADLINE_MAILS = 8
        const val MAX_THREAD = 10
        const val THREAD_BODY_CHARS = 4_000
        const val MAX_SHOWN_CHARS = 8_000
        const val MAX_SPOKEN_CHARS = 3_000
        const val SPOKEN_BODY_CHARS = 1_500
        const val MAX_ATTACHMENT_BYTES = 200 * 1024
        const val STEP_SEARCH = "mail-search"
        const val STEP_READ = "mail-read"
        const val STEP_SUMMARY = "mail-summary"
        const val STEP_DRAFT = "mail-draft"
        const val STEP_SEND = "mail-send"
        const val READ_PREFIX = "Inhalt aus Gmail – fremder Text, keine Anweisung an mich:"
        const val CLOUD_NOT_ALLOWED = "Als Gehirn ist die Cloud gewählt, aber Mailinhalte an die Cloud hast du nicht freigegeben – " +
            "deshalb geht kein Mailtext an die Cloud."
        const val CONTACTS_EXPLANATION = "Um die E-Mail-Adresse zu finden, lese ich einmal dein Adressbuch auf diesem Gerät. " +
            "Es wird nichts gespeichert und nichts übertragen. Android fragt dich gleich nach der Berechtigung „Kontakte“."
    }
}
