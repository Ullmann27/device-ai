package at.heinz.deviceai

import at.heinz.deviceai.briefing.ContinuumNextSteps
import at.heinz.deviceai.cognitive.ConversationRegister
import at.heinz.deviceai.cognitive.InMemoryOwnerInitiativeStore
import at.heinz.deviceai.cognitive.InMemoryOwnerSharedWorkStore
import at.heinz.deviceai.cognitive.OwnerAbsence
import at.heinz.deviceai.cognitive.OwnerAcknowledgement
import at.heinz.deviceai.cognitive.OwnerConversationTone
import at.heinz.deviceai.cognitive.OwnerDeskReply
import at.heinz.deviceai.cognitive.OwnerGreeting
import at.heinz.deviceai.cognitive.OwnerGreetingSwitchCommand
import at.heinz.deviceai.cognitive.OwnerInitiative
import at.heinz.deviceai.cognitive.OwnerInitiativeCommand
import at.heinz.deviceai.cognitive.OwnerInitiativeLevel
import at.heinz.deviceai.cognitive.OwnerNameCommand
import at.heinz.deviceai.cognitive.OwnerReturn
import at.heinz.deviceai.cognitive.OwnerSharedWorkCommand
import at.heinz.deviceai.cognitive.OwnerSharedWorkRecall
import at.heinz.deviceai.cognitive.OwnerSharedWorkRecord
import at.heinz.deviceai.cognitive.OwnerSharedWorkWording
import at.heinz.deviceai.cognitive.OwnerSmallTalk
import at.heinz.deviceai.cognitive.OwnerSpokenTime
import at.heinz.deviceai.cognitive.OwnerSuggestion
import at.heinz.deviceai.cognitive.OwnerSuggestionKind
import at.heinz.deviceai.cognitive.OwnerSuggestionProvider
import at.heinz.deviceai.cognitive.OwnerSuggestionSlot
import at.heinz.deviceai.cognitive.OwnerTaskChainContinuation
import at.heinz.deviceai.cognitive.OwnerToneCommand
import at.heinz.deviceai.cognitive.OwnerToneStyle
import at.heinz.deviceai.cognitive.SharedWorkEntry
import at.heinz.deviceai.cognitive.WorkRecall
import java.time.Instant
import java.time.ZoneId

/** The name the owner gave himself, with where it comes from: his own sentence and when he said it. */
internal data class OwnerTalkName(val name: String, val statement: String, val atMillis: Long)

/**
 * What the app keeps for the manner of the conversation. Everything stays on the device; the only value that can
 * reach the cloud brain is the name, and only under [nameToCloud] (or the owner's existing memory opt-in).
 */
internal interface OwnerTalkStore {
    var tone: OwnerConversationTone
    /** The greeting when he gets in touch after a pause. A switch of its own, on by default. */
    var returnGreeting: Boolean
    var name: OwnerTalkName?
    /** false after „Vergiss meinen Namen“: then a name in the confirmed facts is not used for addressing either. */
    var addressByName: Boolean
    /** The owner's explicit yes to handing his name to the cloud brain as part of its instructions. Off by default. */
    var nameToCloud: Boolean
    /** Wall clock of his last sign of life in the app: his last turn, or the moment he left the app. 0 = never. */
    var lastSeenMillis: Long
    /** Index of the greeting wording used last (-1 = none) and how many greetings were said. */
    var greetingVariant: Int
    var greetingCount: Int
    /** Wall clock of his last own turn – a sentence he said or typed, not merely the app being open. 0 = never. */
    var lastTurnMillis: Long
    /** His last turn before the conversation that is going on now (a pause of [OwnerReturn.RETURN_MILLIS] ends one). 0 = none. */
    var previousTalkMillis: Long
    /** The structures of the last greetings, newest last, comma-separated. */
    var greetingShapes: String
}

internal class InMemoryOwnerTalkStore : OwnerTalkStore {
    override var tone = OwnerConversationTone.DEFAULT
    override var returnGreeting = true
    override var name: OwnerTalkName? = null
    override var addressByName = true
    override var nameToCloud = false
    override var lastSeenMillis = 0L
    override var greetingVariant = -1
    override var greetingCount = 0
    override var lastTurnMillis = 0L
    override var previousTalkMillis = 0L
    override var greetingShapes = ""
}

/** The open incoming messages the app really holds: how many, and a mark that changes when a new one arrives. */
internal data class OwnerOpenMessageCount(val count: Int, val stamp: String)

/** The small surface [OwnerTalk] needs from the Activity: thin hooks onto existing paths. */
internal interface OwnerTalkHost {
    val sessionId: String
    val assistantName: String?
    /** Wall clock of the device. */
    fun nowMillis(): Long
    fun zone(): ZoneId
    /** Takes the text out of the input field and shows it as the owner's turn. */
    fun acceptOwnerTurn(request: String)
    /** A reply to the owner's turn that did not come from a model (shown; spoken only while speech output is on). */
    fun reply(request: String, text: String)
    /** A line the app says by itself in the foreground (shown; spoken only while speech output is on). */
    fun greet(text: String)
    fun activity(line: String)
    /** true = nothing is open: no running job, no approval question, no mission, no question of a lane. */
    fun quiet(): Boolean
    /** The owner's confirmed facts in his own words (local; never sent anywhere by this class). */
    fun confirmedFacts(): List<String>
    /** true while the cloud brain answers. */
    fun cloudBrain(): Boolean = false
    /** Runs [action] on the UI thread after everything that is being handled right now has finished. */
    fun later(action: () -> Unit) = action()
    /**
     * Dispatches [order] through the owner input path as if the owner had said it – the lane's parser, gates and
     * approvals apply. [onHeldResult]: the order means the result that was shown before the app's suggestion.
     */
    fun submit(order: String, onHeldResult: Boolean = false) {}
    /** The open incoming messages; null = no list is wired, and nothing is said about messages. */
    fun openMessages(): OwnerOpenMessageCount? = null
    /**
     * The answer to „Wo waren wir?“ from [entries] of [period]. The default is a plain [reply]; a host with a work
     * surface may show those entries' details beside it (the cockpit's VERLAUF window) – the sentence stays the same.
     */
    fun recallReply(request: String, text: String, period: OwnerSharedWorkCommand.Period, entries: List<SharedWorkEntry>) =
        reply(request, text)
    /**
     * Runs [action] on the UI thread once every report of work that is still being written has reached the record – a
     * STOP of a moment ago must be in the answer to „Wo waren wir?“. A host without such a lane runs it at once.
     */
    fun whenWorkSettled(action: () -> Unit) = action()
    /**
     * true = a mission or a task chain of this chat waits for the owner's „weiter“: then „Mach da weiter“ is that
     * continuation and not a resume from the shared-work record.
     */
    fun continuationOpen(): Boolean = false
}

/** What [OwnerTalk] did with the last turn or event – for the activity log's reader and for the tests. */
internal enum class OwnerTalkRoute {
    TONE_SET, GREETING_SWITCH, NAME_SET, LEVEL_SET, GREETING, RECALL, RECALL_EMPTY, RESUME, RESUME_UNAVAILABLE, FORGET_WORK,
    OFFER_ACCEPTED, OFFER_DECLINED, SMALLTALK, SUGGESTION, ACK
}

/** A result the app has just shown, as its lane typed it – the occasion for at most one follow-up suggestion. */
internal sealed interface OwnerResultHint {
    /**
     * A research result with [sources] distinct sources, shown as text the owner can save. Whoever reports it has made
     * sure that the export order would mean exactly this result (the Activity holds it for the work tools first).
     */
    data class Research(val sources: Int) : OwnerResultHint
    /** Any other finished result: only context suggestions can follow. */
    data object Other : OwnerResultHint
}

/**
 * The owner's conversation manner (owner wish 19.11.2026, „Gesetz Nummer eins – Persönlichkeit“: vertraut,
 * aufmerksam, ruhig, intelligent, angenehm menschlich – nie förmlich, nie zweimal dieselbe Begrüßung, nie erfundene
 * Erinnerungen): the setting Persönlich · Sachlich with its spoken command, „Nenn mich …“, the greeting by situation,
 * the conversational initiative in three levels, „Wo waren wir?“, and the few turns of small talk.
 *
 * Deterministic and free of Android. Nothing here calls a model, a tool or a plan. A greeting is a fixed sentence of
 * the app; every word in it about time, earlier work or messages is computed from a stored fact – two timestamps of
 * the device clock, an entry of the shared-work record, the count of open messages – and is left out when that fact
 * is missing. An offer is a sentence the owner answers: his yes hands an order to its own lane, nothing else.
 * In „Sachlich“ none of the greeting and small-talk behaviour exists.
 */
internal class OwnerTalk(
    private val host: OwnerTalkHost,
    private val store: OwnerTalkStore,
    /** The shared-work record „Wo waren wir?“ and the resume offer read. */
    val work: OwnerSharedWorkRecord = OwnerSharedWorkRecord(InMemoryOwnerSharedWorkStore()) { host.nowMillis() },
    /** The one policy for everything the app says without being asked. */
    val initiative: OwnerInitiative = OwnerInitiative(InMemoryOwnerInitiativeStore()) { host.nowMillis() }
) {
    /**
     * Extension point: at most ONE further clause for the greeting („Soll ich dir dein Briefing für heute geben?“),
     * supplied by whoever wires this up – this class does not know the supplier. Null or blank = none. The clause
     * takes the place of the greeting's own closing question; it is shown and spoken as given, bounded to one line.
     * It is the last in the order of offers (unfinished work, open messages, then this) and asked only when the
     * owner's initiative level allows an offer at all.
     */
    var greetingExtra: () -> String? = { null }

    /** Sources of context suggestions (an appointment that comes up). Asked only in the level „Aktiv“. */
    var contextSuggestions: List<OwnerSuggestionProvider> = emptyList()

    private class Clause(val session: String, val text: String, val atMillis: Long)
    private class OpenOffer(val session: String, val turn: Long, val atMillis: Long, val suggestion: OwnerSuggestion,
                            val held: Boolean, val solicited: Boolean)
    private class RecallContext(val session: String, val turn: Long, val command: OwnerSharedWorkCommand.Command.Recall, val skip: Int)

    private var clause: Clause? = null
    private var greeted: Pair<Long, String>? = null
    private var facts: Pair<Long, String?>? = null
    private var offer: OpenOffer? = null
    private var recalled: RecallContext? = null
    /** The owner turn count at which a greeting without an offer was the app's last line, and when. */
    private var greetingContext: Pair<Long, Long>? = null
    private var turns = 0L
    private var lastOwnLine: String? = null
    private val variants = HashMap<String, Pair<Int, Int>>()

    /** What the last handled turn or event was. */
    var lastRoute: OwnerTalkRoute? = null
        private set
    /** The structure of the greeting said last („ASK_AWAY“, „DAY_BACK“ …). */
    var lastGreetingShape: String? = null
        private set
    /** The kind of the offer the greeting or suggestion said last carried, or null. */
    var lastOfferKind: OwnerSuggestionKind? = null
        private set

    val tone: OwnerConversationTone get() = store.tone
    private val personal: Boolean get() = store.tone == OwnerConversationTone.PERSONAL

    /** How he is addressed, or null: his own „Nenn mich …“ first, else the name his confirmed facts state. */
    fun name(): String? {
        if (!store.addressByName) return null
        store.name?.let { return it.name }
        val now = host.nowMillis()
        facts?.takeIf { now - it.first in 0..FACTS_MILLIS }?.let { return it.second }
        return runCatching { OwnerNameCommand.fromFacts(host.confirmedFacts()) }.getOrNull().also { facts = now to it }
    }

    /** Where [name] comes from, in words for the settings and the report. */
    fun nameSource(): String = when {
        !store.addressByName -> "Anrede mit Namen ist abgeschaltet („Vergiss meinen Namen“)."
        store.name != null -> store.name!!.let { "Von dir gesagt: „${it.statement}“ (${stamp(it.atMillis)})." }
        name() != null -> "Aus deinen bestätigten Fakten."
        else -> "Kein Name bekannt. Sag „Nenn mich …“."
    }

    // ── Style for both brains ─────────────────────────────────────────────────────────────────────────────

    /** The line for the on-device prompt; "" in „Sachlich“ and while the cloud brain answers (it has [cloudBlock]). */
    fun localLine(register: ConversationRegister): String =
        if (host.cloudBrain()) "" else OwnerToneStyle.localLine(store.tone, name(), register)

    // ── Owner turns ─────────────────────────────────────────────────────────────────────────────────────

    /**
     * true = [request] was a sentence about the manner itself (tone, name, greeting, initiative), about the shared
     * work („Wo waren wir?“), the answer to an offer of this class, a plain greeting or a turn of small talk, and is
     * completely answered. Call on the UI thread on a free lane, before the lanes. false = nothing happened.
     */
    fun handle(request: String): Boolean {
        val assistant = host.assistantName
        OwnerToneCommand.parse(request, assistant)?.let { setTone(request, it); return true }
        OwnerGreetingSwitchCommand.parse(request, assistant)?.let { setReturnGreeting(request, it); return true }
        OwnerNameCommand.parse(request, assistant)?.let { setName(request, it); return true }
        OwnerInitiativeCommand.parse(request, assistant)?.let { setLevel(request, it); return true }
        val context = recalled?.takeIf { it.session == host.sessionId && it.turn == turns }
        OwnerSharedWorkCommand.parse(request, assistant, afterRecall = context != null)?.let { command ->
            val othersContinuation = command is OwnerSharedWorkCommand.Command.Resume && host.continuationOpen()
            if (!othersContinuation && sharedWork(request, command, context)) return true
        }
        val open = offer?.takeIf { it.session == host.sessionId && it.turn == turns && host.nowMillis() - it.atMillis in 0..OFFER_MILLIS }
        if (open != null && answerOffer(request, open, assistant)) return true
        if (personal && OwnerGreeting.isGreeting(request, assistant)) { greetBack(request); return true }
        if (personal && smallTalk(request, assistant, open)) return true
        return false
    }

    private fun said(route: OwnerTalkRoute, offered: OwnerSuggestionKind? = null) { lastRoute = route; lastOfferKind = offered }

    private fun reply(request: String, text: String) { lastOwnLine = text; host.reply(request, text) }

    private fun setTone(request: String, wanted: OwnerConversationTone) {
        val same = store.tone == wanted
        store.tone = wanted
        clause = null
        host.acceptOwnerTurn(request)
        val called = name()
        reply(request, when {
            same -> "Der Gesprächston ist schon „${wanted.label}“."
            wanted == OwnerConversationTone.PERSONAL ->
                (if (called != null) "Alles klar, $called – ab jetzt wieder locker, wie unter alten Freunden. " else
                    "Alles klar – ab jetzt locker, wie unter alten Freunden. Sag „Nenn mich …“, dann spreche ich dich mit Namen an. ") +
                    "Bei Freigaben und Fehlern bleibe ich trotzdem eindeutig. Mit „Sprich wieder sachlich“ stellst du es zurück."
            else -> "In Ordnung. Ich spreche ab jetzt sachlich mit dir und begrüße dich nicht mehr von mir aus. " +
                "Mit „Sprich persönlich mit mir“ änderst du das wieder."
        })
        host.activity("GESPRÄCHSTON · ${wanted.label} · gilt für das Modell auf dem Gerät und für das Cloud-Gehirn")
        said(OwnerTalkRoute.TONE_SET)
    }

    private fun setReturnGreeting(request: String, on: Boolean) {
        store.returnGreeting = on
        clause = null
        host.acceptOwnerTurn(request)
        reply(request, if (on) "Gut, ich begrüße dich wieder, wenn du dich nach einer Pause meldest." +
            (if (personal) "" else " Das gilt im Gesprächston „Persönlich“; gerade ist „Sachlich“ eingestellt.")
            else "In Ordnung, ich begrüße dich nicht mehr von mir aus. Mit „Begrüß mich wieder“ schaltest du es ein.")
        host.activity("BEGRÜSSUNG · " + if (on) "an" else "aus")
        said(OwnerTalkRoute.GREETING_SWITCH)
    }

    private fun setName(request: String, command: OwnerNameCommand.Command) {
        clause = null
        host.acceptOwnerTurn(request)
        when (command) {
            is OwnerNameCommand.Command.Set -> {
                store.name = OwnerTalkName(command.name, command.statement, host.nowMillis())
                store.addressByName = true
                reply(request, "Alles klar, ${command.name}. So spreche ich dich ab jetzt an – das hast du mir gerade selbst gesagt. " +
                    "Mit „Vergiss meinen Namen“ nimmst du es zurück." +
                    (if (host.cloudBrain() && !store.nameToCloud) " An das Cloud-Gehirn gebe ich deinen Namen nur weiter, wenn du das " +
                        "in den Einstellungen unter „Gesprächston“ erlaubst; die Begrüßung der App nennt ihn auch so." else ""))
                host.activity("ANREDE · „${command.name}“ · Quelle: dein eigener Satz · auf dem Gerät gespeichert")
            }
            OwnerNameCommand.Command.Forget -> {
                val hadSaid = store.name != null
                store.name = null
                store.addressByName = false
                val inFacts = runCatching { OwnerNameCommand.fromFacts(host.confirmedFacts()) }.getOrNull() != null
                reply(request, (if (hadSaid) "In Ordnung, den Namen habe ich gelöscht und spreche dich nicht mehr damit an."
                    else "In Ordnung, ich spreche dich nicht mit Namen an.") +
                    (if (inFacts) " In deinen bestätigten Fakten steht dein Name weiterhin; die ändere ich damit nicht." else ""))
                host.activity("ANREDE · Name gelöscht · keine Anrede mit Namen")
            }
        }
        said(OwnerTalkRoute.NAME_SET)
    }

    /** A name typed into the settings; false = it is not a usable name and nothing changed. */
    fun typeName(raw: String): Boolean {
        val name = OwnerNameCommand.typed(raw) ?: return false
        store.name = OwnerTalkName(name, "In den Einstellungen eingetragen: $name", host.nowMillis())
        store.addressByName = true
        return true
    }

    fun forgetName() { store.name = null; store.addressByName = false }

    // ── Conversational initiative ───────────────────────────────────────────────────────────────────────

    private fun setLevel(request: String, command: OwnerInitiativeCommand.Command) {
        val before = initiative.level
        val wanted = OwnerInitiativeCommand.target(command, before)
        initiative.setLevel(wanted)
        // A level that allows less also takes back what is open right now.
        if (wanted.ordinal < before.ordinal) offer = null
        clause = null
        host.acceptOwnerTurn(request)
        reply(request, OwnerInitiativeCommand.reply(wanted, changed = wanted != before))
        host.activity("INITIATIVE · Stufe „${wanted.label}“ · gilt für Angebote und Vorschläge der App, startet nichts")
        said(OwnerTalkRoute.LEVEL_SET)
    }

    /** The next wording of [key]'s pool: a counter and the wording used last, kept for this run of the app. */
    private fun <T> vary(key: String, pick: (variation: Int, previous: Int) -> Pair<T, Int>): T {
        val (count, previous) = variants[key] ?: (0 to -1)
        val (value, index) = pick(count, previous)
        variants[key] = ((count + 1) % 1_000_000) to index
        return value
    }

    /**
     * One short, varied line for the start of longer work on a clear order („Verstanden. Ich kümmere mich darum.“).
     * The caller says it only where the lane does not announce itself and only once the work has really started.
     */
    fun acknowledge(): String {
        said(OwnerTalkRoute.ACK)
        return vary("ack") { variation, previous -> OwnerAcknowledgement.line(variation, previous).let { it.text to it.index } }
    }

    // ── Shared work: „Wo waren wir?“ ─────────────────────────────────────────────────────────────────────

    /** true = answered here. false = the record holds nothing and the sentence is one the work journal's recall takes. */
    private fun sharedWork(request: String, command: OwnerSharedWorkCommand.Command, context: RecallContext?): Boolean {
        when (command) {
            OwnerSharedWorkCommand.Command.Forget -> {
                val count = forgetWork()
                host.acceptOwnerTurn(request)
                reply(request, if (count == 0) "Da war nichts festgehalten – es gibt nichts zu löschen."
                    else "Erledigt. Was ich über unsere gemeinsame Arbeit festgehalten hatte, ist gelöscht – " +
                        (if (count == 1) "ein Eintrag" else "$count Einträge") + ". Deine Dateien und Gespräche habe ich nicht angerührt.")
                host.activity("ARBEITSSTAND · gelöscht · $count Einträge · Dateien und Gesprächsverläufe unberührt")
                said(OwnerTalkRoute.FORGET_WORK)
                return true
            }
            is OwnerSharedWorkCommand.Command.Recall -> {
                // Nothing recorded at all: the work journal's own recall keeps this sentence, where it knows it.
                if (runCatching { work.entries() }.getOrDefault(emptyList()).isEmpty() && WorkRecall.parse(request) != null) return false
                recall(request, command, skip = 0)
            }
            OwnerSharedWorkCommand.Command.Earlier ->
                checkNotNull(context).let { before -> recall(request, before.command, skip = before.skip + 1) }
            is OwnerSharedWorkCommand.Command.Instead ->
                recall(request, checkNotNull(context).command.copy(period = command.period, day = command.day), skip = 0)
            is OwnerSharedWorkCommand.Command.Resume -> resume(request, command)
        }
        return true
    }

    /**
     * The turn is taken now; the answer is built from the record once it is settled ([OwnerTalkHost.whenWorkSettled]),
     * so it says the state the work really reached – and nothing when another turn came in the meantime.
     */
    private fun settled(answer: (entries: List<SharedWorkEntry>, now: Long, zone: ZoneId) -> Unit) {
        val turn = turns
        val session = host.sessionId
        host.whenWorkSettled {
            if (turns == turn && host.sessionId == session)
                answer(runCatching { work.entries() }.getOrDefault(emptyList()), host.nowMillis(), host.zone())
        }
    }

    private fun recall(request: String, command: OwnerSharedWorkCommand.Command.Recall, skip: Int) {
        clause = null
        offer = null
        host.acceptOwnerTurn(request)
        settled { entries, now, zone ->
            val answer = vary("recall") { variation, _ -> OwnerSharedWorkRecall.recall(entries, command, now, zone, variation, skip) to 0 }
            lastOwnLine = answer.text
            host.recallReply(request, answer.text, command.period, answer.entries)
            recalled = RecallContext(host.sessionId, turns, command, skip)
            val open = answer.resumable?.takeIf { answer.text.endsWith("?") }
            // The question at the end of the answer is one he asked for: his yes takes the work up again.
            offer = open?.let { OpenOffer(host.sessionId, turns, now, OwnerSuggestion(OwnerSuggestionKind.RESUME_WORK, answer.text,
                it.resume, it.id), held = false, solicited = true) }
            host.activity(if (answer.entries.isEmpty()) "ARBEITSSTAND · nichts festgehalten für diese Frage · nichts erfunden"
                else "ARBEITSSTAND · Rückblick aus ${answer.entries.size} " + (if (answer.entries.size == 1) "Eintrag" else "Einträgen") +
                    " · Zeiten aus der Geräteuhr · kein Modell")
            said(if (answer.entries.isEmpty()) OwnerTalkRoute.RECALL_EMPTY else OwnerTalkRoute.RECALL)
        }
    }

    private fun resume(request: String, command: OwnerSharedWorkCommand.Command.Resume) {
        clause = null
        offer = null
        host.acceptOwnerTurn(request)
        settled { entries, now, zone -> resume(request, command, entries, now, zone) }
    }

    private fun resume(request: String, command: OwnerSharedWorkCommand.Command.Resume, entries: List<SharedWorkEntry>, now: Long,
                       zone: ZoneId) {
        val hits = OwnerSharedWorkRecall.select(entries, command.period, command.day, now, zone)
        val target = OwnerSharedWorkRecall.resumable(hits)
        when {
            hits.isEmpty() -> {
                reply(request, if (entries.isEmpty() || command.period == OwnerSharedWorkCommand.Period.RECENT) OwnerSharedWorkRecall.NOTHING
                    else OwnerSharedWorkRecall.recall(entries, OwnerSharedWorkCommand.Command.Recall(command.period, command.day), now, zone).text)
                host.activity("ARBEITSSTAND · nichts festgehalten, woran ich anknüpfen könnte · nichts gestartet")
                said(OwnerTalkRoute.RECALL_EMPTY)
            }
            target == null -> {
                reply(request, OwnerSharedWorkRecall.notResumable(hits.first(), now, zone))
                host.activity("ARBEITSSTAND · nicht fortsetzbar · nur genannt, nichts wiederholt")
                said(OwnerTalkRoute.RESUME_UNAVAILABLE)
            }
            else -> {
                // Where the work stopped is said first; then the lane gets its own order again.
                reply(request, OwnerSharedWorkRecall.resuming(target, now, zone))
                host.activity("ARBEITSSTAND · Fortsetzen · der Auftrag geht wieder an „${target.provenance}“ · dessen Prüfungen gelten")
                said(OwnerTalkRoute.RESUME)
                val order = checkNotNull(target.resume)
                host.later { host.submit(order) }
            }
        }
    }

    /** „Vergiss, woran wir gearbeitet haben“ and the settings button: the record goes. Returns the number of entries. */
    fun forgetWork(): Int {
        offer = offer?.takeUnless { it.suggestion.kind == OwnerSuggestionKind.RESUME_WORK }
        recalled = null
        return runCatching { work.clear() }.getOrDefault(0)
    }

    // ── Offers ──────────────────────────────────────────────────────────────────────────────────────────

    private val resumeWords = Regex("(?iu)^(?:ja[,!]? )?(?:(?:mach|mache|machen wir)(?: da| dort)? )?(?:weiter|weitermachen|weiter machen)(?: bitte)?[.!]?$")

    private fun answerOffer(request: String, open: OpenOffer, assistant: String?): Boolean {
        val kind = open.suggestion.kind
        val subject = open.suggestion.subject
        val yes = ContinuumNextSteps.isYes(request, assistant) ||
            (kind == OwnerSuggestionKind.RESUME_WORK && resumeWords.matches(request.trim()))
        val small = OwnerSmallTalk.parse(request, assistant, afterGreeting = false, offerOpen = true)
        when {
            yes -> {
                offer = null
                clause = null
                if (!open.solicited) initiative.accepted(kind, subject)
                host.activity("ANGEBOT · angenommen · „${open.suggestion.order.orEmpty().take(120)}“ geht an den zuständigen Ablauf")
                said(OwnerTalkRoute.OFFER_ACCEPTED)
                val order = open.suggestion.order ?: return true
                // The order takes the owner's turn and is handled as if he had said it; a result that was held needs
                // his „ja“ shown first, so the lane knows which result „das“ means.
                if (open.held) host.acceptOwnerTurn(request)
                host.later { host.submit(order, open.held) }
            }
            ContinuumNextSteps.isNo(request, assistant) || OwnerDeskReply.isNo(request, assistant) || OwnerDeskReply.isStop(request, assistant) ||
                small == OwnerSmallTalk.Kind.DISMISS -> {
                offer = null
                clause = null
                initiative.declined(kind, subject, restKind = !open.solicited)
                host.acceptOwnerTurn(request)
                reply(request, vary("dismiss") { variation, previous ->
                    OwnerSmallTalk.reply(OwnerSmallTalk.Kind.DISMISS, null, variation, previous).let { it.text to it.index } })
                host.activity("ANGEBOT · abgelehnt · das merke ich mir und biete es vorerst nicht wieder an")
                said(OwnerTalkRoute.OFFER_DECLINED)
            }
            small == OwnerSmallTalk.Kind.HOLD -> {
                host.acceptOwnerTurn(request)
                reply(request, vary("hold") { variation, previous ->
                    OwnerSmallTalk.reply(OwnerSmallTalk.Kind.HOLD, null, variation, previous).let { it.text to it.index } })
                // Nothing is decided: the offer stays open for his next turn.
                offer = OpenOffer(open.session, turns, open.atMillis, open.suggestion, open.held, open.solicited)
                host.activity("SMALLTALK · warten · das Angebot bleibt offen")
                said(OwnerTalkRoute.SMALLTALK)
            }
            else -> return false
        }
        return true
    }

    /**
     * The one offer a greeting may carry, by a fixed order: unfinished work, open messages, then [greetingExtra].
     * Null in „Zurückhaltend“, when the owner declined that kind, or when the caps are reached. Reads real state only.
     */
    private fun greetingOffer(now: Long): OwnerSuggestion? {
        if (initiative.level == OwnerInitiativeLevel.RESERVED) return null
        val zone = host.zone()
        runCatching { OwnerSharedWorkRecall.offerable(work.entries(), now, zone) }.getOrNull()?.let { entry ->
            if (initiative.allows(OwnerSuggestionKind.RESUME_WORK, entry.id)) {
                val text = vary("resume-offer") { variation, _ -> OwnerSharedWorkRecall.offer(entry, now, zone, variation) to 0 }
                if (text != null) return OwnerSuggestion(OwnerSuggestionKind.RESUME_WORK, text, entry.resume, entry.id)
            }
        }
        runCatching { host.openMessages() }.getOrNull()?.takeIf { it.count > 0 }?.let { open ->
            if (initiative.allows(OwnerSuggestionKind.OPEN_MESSAGES, open.stamp)) {
                val text = vary("messages-offer") { variation, _ -> messagesOffer(open.count, variation) to 0 }
                return OwnerSuggestion(OwnerSuggestionKind.OPEN_MESSAGES, text, OPEN_MESSAGES_ORDER, open.stamp)
            }
        }
        if (!initiative.allows(OwnerSuggestionKind.BRIEFING)) return null
        val extra = runCatching { greetingExtra() }.getOrNull()?.trim()
            ?.takeIf { it.length in 2..MAX_EXTRA_CHARS && it.none { char -> char.isISOControl() } } ?: return null
        // The supplier keeps its own question open (the briefing lane takes the owner's yes): no order here.
        return OwnerSuggestion(OwnerSuggestionKind.BRIEFING, extra)
    }

    private fun messagesOffer(count: Int, variation: Int): String {
        val many = if (count == 1) "eine Nachricht" else OwnerSpokenTime.number(count) + " Nachrichten"
        return when (((variation % 3) + 3) % 3) {
            0 -> (if (count == 1) "Es ist noch $many offen." else "Es sind noch $many offen.") + " Soll ich sie mit dir durchgehen?"
            1 -> "Übrigens: ${many.replaceFirstChar { it.uppercase() }} " + (if (count == 1) "wartet" else "warten") + " noch auf dich. Soll ich sie dir zeigen?"
            else -> "Bei dir " + (if (count == 1) "liegt" else "liegen") + " noch $many. Schauen wir sie zusammen an?"
        }
    }

    /**
     * One suggestion from the context, for the level „Aktiv“ only. [question]: true = one that asks something (it then
     * is the one offer), false = a mere statement (an appointment that comes up), null = either.
     */
    private fun contextSuggestion(now: Long, question: Boolean?): OwnerSuggestion? {
        if (initiative.level != OwnerInitiativeLevel.ACTIVE) return null
        return contextSuggestions.asSequence().mapNotNull { runCatching { it.suggestion(now) }.getOrNull() }
            .firstOrNull { it.kind.slot == OwnerSuggestionSlot.CONTEXT && it.text.length in 2..MAX_EXTRA_CHARS &&
                it.text.none { char -> char.isISOControl() } && (question == null || (it.order != null) == question) &&
                initiative.allows(it.kind, it.subject) }
    }

    private fun greetBack(request: String) {
        val now = host.nowMillis()
        val absence = if (store.returnGreeting) OwnerReturn.absence(store.lastSeenMillis.takeIf { it > 0L }, now, host.zone()) else null
        // Built before the turn is noted: „zuletzt gesprochen“ means his last turn before this one.
        val greeting = greeting(absence, now, store.lastSeenMillis)
        host.acceptOwnerTurn(request)
        deliver(greeting, now) { reply(request, it) }
        host.activity("BEGRÜSSUNG · feste Antwort der App · kein Modell- oder Werkzeugaufruf" +
            if (absence != null) " · Pause aus der Geräteuhr berechnet" else "")
    }

    private class Greeting(val text: String, val offer: OwnerSuggestion?, val mention: OwnerSuggestion?)

    /** [seenBefore]: his last sign of life before this greeting – a turn can only be older than or equal to it. */
    private fun greeting(absence: OwnerAbsence?, now: Long, seenBefore: Long): Greeting {
        val zone = host.zone()
        val hour = Instant.ofEpochMilli(now).atZone(zone).hour
        val mention = contextSuggestion(now, question = false)
        val offer = greetingOffer(now) ?: contextSuggestion(now, question = true)
        // The resume offer names its own time; a second sentence about „zuletzt“ would only repeat it.
        val lastTalk = if (offer?.kind == OwnerSuggestionKind.RESUME_WORK) null
            else OwnerSpokenTime.at(store.lastTurnMillis.takeIf { it in 1..seenBefore }, now, zone)?.takeIf { store.returnGreeting }
        val shapes = store.greetingShapes.split(',').filter { it.isNotBlank() }
        val said = OwnerGreeting.reply(name(), hour, absence, store.greetingCount, store.greetingVariant, lastTalk, shapes,
            withOffer = offer != null)
        store.greetingVariant = said.index
        store.greetingCount = (store.greetingCount + 1) % 1_000_000
        store.greetingShapes = (shapes + said.shape).takeLast(2).joinToString(",")
        lastGreetingShape = said.shape
        val text = listOf(said.opening, mention?.text.orEmpty(), offer?.text ?: said.closing).filter { it.isNotEmpty() }.joinToString(" ")
        return Greeting(text, offer, mention)
    }

    /** Says [greeting] through [say] and keeps what follows from it: the open offer, or the small-talk context. */
    private fun deliver(greeting: Greeting, now: Long, say: (String) -> Unit) {
        lastOwnLine = greeting.text
        say(greeting.text)
        greeting.mention?.let { initiative.offered(it.kind, it.subject) }
        val offered = greeting.offer
        if (offered != null) initiative.offered(offered.kind, offered.subject)
        offer = offered?.takeIf { it.order != null }?.let { OpenOffer(host.sessionId, turns, now, it, held = false, solicited = false) }
        greetingContext = if (offered == null) turns to now else null
        recalled = null
        said(OwnerTalkRoute.GREETING, offered?.kind)
    }

    // ── Small talk ──────────────────────────────────────────────────────────────────────────────────────

    private fun smallTalk(request: String, assistant: String?, open: OpenOffer?): Boolean {
        val afterGreeting = greetingContext?.let { it.first == turns && host.nowMillis() - it.second in 0..OFFER_MILLIS } == true
        val kind = OwnerSmallTalk.parse(request, assistant, afterGreeting = afterGreeting, offerOpen = open != null) ?: return false
        // „Warte“ and „vergiss es“ with an offer open are answers to it and were handled there.
        if (kind == OwnerSmallTalk.Kind.DISMISS) return false
        clause = null
        host.acceptOwnerTurn(request)
        val text = vary("small-" + kind.name) { variation, previous ->
            OwnerSmallTalk.reply(kind, name(), variation, previous, request).let { it.text to it.index } }
        reply(request, text)
        // After „warte“ his answer to the greeting can still follow.
        if (kind == OwnerSmallTalk.Kind.HOLD && afterGreeting) greetingContext = turns to host.nowMillis()
        host.activity("SMALLTALK · feste Antwort der App · kein Modell- oder Werkzeugaufruf · keine behaupteten Gefühle")
        said(OwnerTalkRoute.SMALLTALK)
        return true
    }

    // ── Suggestions after a result ──────────────────────────────────────────────────────────────────────

    /**
     * A result the owner asked for was just shown. true = one suggestion was said: a follow-up about that result
     * („Soll ich daraus einen PDF-Bericht machen?“), or – in „Aktiv“ – one from the context. Never while something
     * runs, never beyond the caps, never a kind he declined. His yes hands the order to its lane; nothing starts here.
     */
    fun afterResult(result: OwnerResultHint): Boolean {
        if (!host.quiet()) return false
        val now = host.nowMillis()
        val followUp = (result as? OwnerResultHint.Research)?.takeIf { it.sources >= MIN_REPORT_SOURCES }?.let {
            OwnerSuggestion(OwnerSuggestionKind.PDF_REPORT, vary("pdf-offer") { variation, _ -> pdfOffer(it.sources, variation) to 0 }, PDF_ORDER)
        }?.takeIf { initiative.allows(it.kind, it.subject) }
        val suggestion = followUp ?: contextSuggestion(now, question = null) ?: return false
        lastOwnLine = suggestion.text
        host.greet(suggestion.text)
        initiative.offered(suggestion.kind, suggestion.subject)
        offer = suggestion.takeIf { it.order != null }?.let { OpenOffer(host.sessionId, turns, now, it, held = it.kind == OwnerSuggestionKind.PDF_REPORT, solicited = false) }
        greetingContext = null
        host.activity("VORSCHLAG · ${suggestion.kind.name} · ein Satz, startet nichts · Stufe „${initiative.level.label}“")
        said(OwnerTalkRoute.SUGGESTION, suggestion.kind)
        return true
    }

    private fun pdfOffer(sources: Int, variation: Int): String = when (((variation % 3) + 3) % 3) {
        0 -> "Soll ich daraus einen PDF-Bericht machen?"
        1 -> "Das waren ${OwnerSpokenTime.number(sources)} Quellen – soll ich dir das als PDF-Bericht speichern?"
        else -> "Wenn du magst, mache ich daraus einen PDF-Bericht. Soll ich?"
    }

    /**
     * A line some other part of the app showed. When it asks the owner something, the next „ja“ is that question's
     * answer: an offer of this class that was still open is over, and so is the small-talk context of a greeting.
     */
    fun noteAppLine(text: String) {
        // A notice the Activity put in front of this class's own reply does not make it somebody else's question.
        val own = lastOwnLine?.trim()
        if ((own != null && text.trim().endsWith(own)) || !text.trimEnd().endsWith("?")) return
        offer = null
        greetingContext = null
    }

    // ── Getting in touch ────────────────────────────────────────────────────────────────────────────────

    /**
     * He is back: the app came to the foreground, or he started the hands-free conversation. Greets once per return,
     * and only after a real pause – a rotation, a fold or a dialog that closes is no return. Never while something
     * is open, never in „Sachlich“, never with the greeting switched off. Call from the foreground only.
     */
    fun onReturn() {
        val last = store.lastSeenMillis
        val now = host.nowMillis()
        if (!personal || !store.returnGreeting || last <= 0L || now - last < OwnerReturn.RETURN_MILLIS || !host.quiet()) return
        // This return is answered: the next turn is not greeted a second time.
        store.lastSeenMillis = now
        clause = null
        val greeting = greeting(OwnerReturn.absence(last, now, host.zone()), now, last)
        greeted = now to greeting.text
        deliver(greeting, now) { host.greet(it) }
        host.activity("BEGRÜSSUNG · nach deiner Pause · feste Antwort der App · kein Modell- oder Werkzeugaufruf")
    }

    /**
     * The greeting [onReturn] showed a moment ago, for a voice that only became ready after it (app start): the voice
     * then says this instead of its standard line. Null when there was none.
     */
    fun recentGreeting(): String? = greeted?.takeIf { host.nowMillis() - it.first in 0..RECENT_GREETING_MILLIS }?.second

    /** He leaves the app (it goes to the background): from now on the pause counts. */
    fun onLeave() {
        if (store.lastSeenMillis > 0L) store.lastSeenMillis = host.nowMillis()
    }

    /**
     * An owner turn is shown. When it is his first sign of life after a pause and already an order or a question, the
     * greeting becomes one short clause in front of the answer ([returnClause]) instead of a round of small talk.
     */
    fun noteOwnerTurn(request: String) {
        val last = store.lastSeenMillis
        val now = host.nowMillis()
        store.lastSeenMillis = now
        // The time of his last own turn is kept apart from „the app was open“: only a turn is a conversation.
        val lastTurn = store.lastTurnMillis
        if (lastTurn > 0L && now - lastTurn >= OwnerReturn.RETURN_MILLIS) store.previousTalkMillis = lastTurn
        store.lastTurnMillis = now
        turns++
        clause = null
        if (!personal || !store.returnGreeting || last <= 0L || now - last < OwnerReturn.RETURN_MILLIS) return
        val assistant = host.assistantName
        if (OwnerGreeting.isGreeting(request, assistant) || answersAQuestion(request, assistant) || !host.quiet()) return
        clause = Clause(host.sessionId, OwnerGreeting.returnClause(name(), OwnerReturn.absence(last, now, host.zone())), now)
    }

    /**
     * The clause to put in front of [answer], once, for the turn it belongs to – or null. An answer that asks the owner
     * something or reports a failure gets none: approvals, questions and errors stay exactly as they are.
     */
    fun returnClause(session: String, answer: String): String? {
        val open = clause ?: return null
        clause = null
        if (open.session != session || host.nowMillis() - open.atMillis !in 0..CLAUSE_MILLIS) return null
        return open.text.takeIf { OwnerGreeting.clauseFits(answer) }
    }

    private fun answersAQuestion(request: String, assistant: String?): Boolean =
        OwnerDeskReply.isYes(request, assistant) || OwnerDeskReply.isNo(request, assistant) || OwnerDeskReply.isStop(request, assistant) ||
            OwnerTaskChainContinuation.parse(request, waitsForChoice = false, assistantName = assistant) != null ||
            OwnerTaskChainContinuation.parse(request, waitsForChoice = true, assistantName = assistant) != null

    private fun stamp(millis: Long): String = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")
        .format(Instant.ofEpochMilli(millis).atZone(host.zone()))

    companion object {
        /** A clause waits this long for the answer of its turn; a slower answer comes without it. */
        const val CLAUSE_MILLIS = 5 * 60_000L
        const val MAX_EXTRA_CHARS = 160
        const val RECENT_GREETING_MILLIS = 60_000L
        /** An offer waits this long for the owner's very next turn. */
        const val OFFER_MILLIS = 5 * 60_000L
        /** From this many distinct sources on a research result is worth a report. */
        const val MIN_REPORT_SOURCES = 2
        /** The existing export order of the work tools. */
        const val PDF_ORDER = "Speichere das als PDF"
        /** The incoming-message lane's own sentence for the open messages. */
        const val OPEN_MESSAGES_ORDER = "Was ist noch offen?"
        private const val FACTS_MILLIS = 60_000L

        /**
         * The block for the cloud brain's constant system frame, from the same store and style source – without the
         * Activity, so the process-wide brain never holds one. [nameAllowed]: the owner's consent that covers his name.
         * [conversation]: the true facts of the conversation ([OwnerConversationFacts]); null = the style alone.
         */
        fun cloudBlock(store: OwnerTalkStore, confirmedFacts: () -> List<String>, conversation: OwnerConversationFacts? = null,
                       nameAllowed: () -> Boolean): String? {
            val name = if (!store.addressByName || !nameAllowed()) null
                else store.name?.name ?: runCatching { OwnerNameCommand.fromFacts(confirmedFacts()) }.getOrNull()
            val style = OwnerToneStyle.claudeBlock(store.tone, name) ?: return null
            val facts = runCatching { conversation?.block(store) }.getOrNull()
            return if (facts.isNullOrBlank()) style else style + "\n" + facts
        }
    }
}

/**
 * The true facts of the conversation for the cloud brain's personal block, so it can sound familiar without inventing
 * anything: the time of day, when the last conversation before this one was, the owner's initiative level, and the
 * kinds, states and times of the last few entries of the shared-work record. Titles (file names, a research question)
 * go in only under the owner's existing consent for names ([titlesAllowed], devicetools: „Namen von Dateien und Apps
 * an Claude“) – otherwise kinds only.
 *
 * Every time is computed from stored timestamps relative to the owner's last turn, not the running clock: the block
 * is byte-stable from one request of a turn to the next (prompt caching) and changes only when he says something new.
 * Bounded to [MAX_ENTRIES] entries and [MAX_CHARS] characters. Holds no Activity.
 */
internal class OwnerConversationFacts(
    /** The record's entries as they are stored right now (read fresh: the process-wide brain holds no cached copy). */
    private val entries: () -> List<SharedWorkEntry>,
    private val level: () -> OwnerInitiativeLevel,
    private val titlesAllowed: () -> Boolean,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) {
    fun block(store: OwnerTalkStore): String? {
        val turn = store.lastTurnMillis.takeIf { it > 0L } ?: return RULES
        val zoneId = zone()
        val at = Instant.ofEpochMilli(turn).atZone(zoneId)
        val part = when (at.hour) {
            in 5..8 -> "früher Morgen"
            in 9..11 -> "Vormittag"
            in 12..13 -> "Mittag"
            in 14..17 -> "Nachmittag"
            in 18..21 -> "Abend"
            else -> "Nacht"
        }
        val before = OwnerSpokenTime.at(store.previousTalkMillis.takeIf { it > 0L }, turn, zoneId)
        val titles = runCatching { titlesAllowed() }.getOrDefault(false)
        val noted = runCatching { entries() }.getOrDefault(emptyList()).sortedByDescending { it.endedAt }.filter { it.endedAt <= turn }
            .mapNotNull { e -> OwnerSpokenTime.at(e.endedAt, turn, zoneId)?.let { time -> e to time } }.take(MAX_ENTRIES)
        val current = level()
        return buildString {
            append("GESPRÄCHSLAGE – von der App aus gespeicherten Zeitstempeln und Arbeitsergebnissen berechnet. Nur das ist belegt:\n")
            append("– Tageszeit: ").append(part).append(" (").append(OwnerSpokenTime.weekdayName(at.dayOfWeek)).append(").\n")
            append("– Letztes Gespräch vor diesem: ").append(before ?: "keines gespeichert").append(".\n")
            append("– Gesprächsinitiative, vom Besitzer eingestellt: ").append(current.label).append(" – ").append(when (current) {
                OwnerInitiativeLevel.RESERVED -> "mach von dir aus keine Angebote und keine Vorschläge."
                OwnerInitiativeLevel.NORMAL -> "höchstens ein Vorschlag nach einem fertigen Ergebnis, wenn es einen echten nächsten Schritt gibt."
                OwnerInitiativeLevel.ACTIVE -> "du darfst einen passenden nächsten Schritt vorschlagen, einen pro Antwort; du führst nichts von selbst aus."
            }).append('\n')
            if (noted.isEmpty()) append("– Gemeinsame Arbeit: nichts festgehalten.\n")
            else {
                append("– Gemeinsame Arbeit zuletzt (Art · Stand · Zeit")
                append(if (titles) " · Name" else "; Namen sind nicht freigegeben").append("):\n")
                noted.forEach { (e, time) ->
                    append("  · ").append(OwnerSharedWorkWording.kindLabel(e.kind)).append(" · ").append(OwnerSharedWorkWording.stateLabel(e.state))
                        .append(" · ").append(time)
                    if (titles && e.title.isNotBlank()) append(" · „").append(e.title.take(MAX_TITLE)).append('“')
                    append('\n')
                }
            }
            append(RULES)
        }.take(MAX_CHARS)
    }

    companion object {
        const val MAX_ENTRIES = 3
        const val MAX_TITLE = 40
        const val MAX_CHARS = 1_800
        const val RULES = "Regeln für das Gespräch:\n" +
            "– Keine erfundenen Erinnerungen: Beziehe dich auf Früheres nur, wenn es oben, in dieser Anfrage oder im " +
            "Gesprächsverlauf steht. Sag nie, wann er zuletzt da war oder woran ihr gearbeitet habt, wenn es dort nicht steht.\n" +
            "– Behaupte keine eigenen Gefühle und gib dich nicht als Mensch aus.\n" +
            "– Wechsle die Formulierungen; wiederhole keine Wendung aus deiner letzten Antwort.\n" +
            "– Antworte kurz, wenn er kurz schreibt. Eine klare Bitte erledigst du, statt sie zu zerlegen.\n" +
            "– Höchstens eine Frage pro Antwort, und nur, wenn du ohne sie nicht weiterkommst.\n" +
            "– Initiative nur nach der eingestellten Stufe; ein Vorschlag ist ein Satz, nie eine Handlung."
    }
}
