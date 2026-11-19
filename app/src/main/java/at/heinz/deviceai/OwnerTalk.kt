package at.heinz.deviceai

import at.heinz.deviceai.cognitive.ConversationRegister
import at.heinz.deviceai.cognitive.OwnerConversationTone
import at.heinz.deviceai.cognitive.OwnerDeskReply
import at.heinz.deviceai.cognitive.OwnerGreeting
import at.heinz.deviceai.cognitive.OwnerGreetingSwitchCommand
import at.heinz.deviceai.cognitive.OwnerNameCommand
import at.heinz.deviceai.cognitive.OwnerReturn
import at.heinz.deviceai.cognitive.OwnerTaskChainContinuation
import at.heinz.deviceai.cognitive.OwnerToneCommand
import at.heinz.deviceai.cognitive.OwnerToneStyle
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
}

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
}

/**
 * The owner's conversation manner (owner wish 19.11.2026: „… wie, wenn wir beste Freunde werden … nicht so förmlich“,
 * „hallo Heinz, wie geht's dir? Oder misst die Zeit, ich habe mich schon 2 Stunden nicht mehr gerührt“): the setting
 * Persönlich · Sachlich with its spoken command, „Nenn mich …“, and the greeting when he gets in touch.
 *
 * Deterministic and free of Android. Nothing here calls a model, a tool or a plan: a greeting is a fixed sentence of
 * the app, the pause in it is computed from two timestamps of the device clock, and the name is only ever what the
 * owner said himself (or what his confirmed facts state). In „Sachlich“ none of the greeting behaviour exists.
 */
internal class OwnerTalk(private val host: OwnerTalkHost, private val store: OwnerTalkStore) {
    /**
     * Extension point: at most ONE further clause for the greeting („Soll ich dir den Tag kurz zusammenfassen?“),
     * supplied by whoever wires this up – this class does not know the supplier. Null or blank = none. The clause
     * takes the place of the greeting's own closing question; it is shown and spoken as given, bounded to one line.
     */
    var greetingExtra: () -> String? = { null }

    private class Clause(val session: String, val text: String, val atMillis: Long)
    private var clause: Clause? = null
    private var greeted: Pair<Long, String>? = null
    private var facts: Pair<Long, String?>? = null

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
     * true = [request] was a sentence about the manner itself (tone, name, greeting on/off) or a plain greeting, and
     * is completely answered. Call on the UI thread on a free lane, before the lanes. false = nothing happened.
     */
    fun handle(request: String): Boolean {
        val assistant = host.assistantName
        OwnerToneCommand.parse(request, assistant)?.let { setTone(request, it); return true }
        OwnerGreetingSwitchCommand.parse(request, assistant)?.let { setReturnGreeting(request, it); return true }
        OwnerNameCommand.parse(request, assistant)?.let { setName(request, it); return true }
        if (personal && OwnerGreeting.isGreeting(request, assistant)) { greetBack(request); return true }
        return false
    }

    private fun setTone(request: String, wanted: OwnerConversationTone) {
        val same = store.tone == wanted
        store.tone = wanted
        clause = null
        host.acceptOwnerTurn(request)
        val called = name()
        host.reply(request, when {
            same -> "Der Gesprächston ist schon „${wanted.label}“."
            wanted == OwnerConversationTone.PERSONAL ->
                (if (called != null) "Alles klar, $called – ab jetzt wieder locker, wie unter alten Freunden. " else
                    "Alles klar – ab jetzt locker, wie unter alten Freunden. Sag „Nenn mich …“, dann spreche ich dich mit Namen an. ") +
                    "Bei Freigaben und Fehlern bleibe ich trotzdem eindeutig. Mit „Sprich wieder sachlich“ stellst du es zurück."
            else -> "In Ordnung. Ich spreche ab jetzt sachlich mit dir und begrüße dich nicht mehr von mir aus. " +
                "Mit „Sprich persönlich mit mir“ änderst du das wieder."
        })
        host.activity("GESPRÄCHSTON · ${wanted.label} · gilt für das Modell auf dem Gerät und für das Cloud-Gehirn")
    }

    private fun setReturnGreeting(request: String, on: Boolean) {
        store.returnGreeting = on
        clause = null
        host.acceptOwnerTurn(request)
        host.reply(request, if (on) "Gut, ich begrüße dich wieder, wenn du dich nach einer Pause meldest." +
            (if (personal) "" else " Das gilt im Gesprächston „Persönlich“; gerade ist „Sachlich“ eingestellt.")
            else "In Ordnung, ich begrüße dich nicht mehr von mir aus. Mit „Begrüß mich wieder“ schaltest du es ein.")
        host.activity("BEGRÜSSUNG · " + if (on) "an" else "aus")
    }

    private fun setName(request: String, command: OwnerNameCommand.Command) {
        clause = null
        host.acceptOwnerTurn(request)
        when (command) {
            is OwnerNameCommand.Command.Set -> {
                store.name = OwnerTalkName(command.name, command.statement, host.nowMillis())
                store.addressByName = true
                host.reply(request, "Alles klar, ${command.name}. So spreche ich dich ab jetzt an – das hast du mir gerade selbst gesagt. " +
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
                host.reply(request, (if (hadSaid) "In Ordnung, den Namen habe ich gelöscht und spreche dich nicht mehr damit an."
                    else "In Ordnung, ich spreche dich nicht mit Namen an.") +
                    (if (inFacts) " In deinen bestätigten Fakten steht dein Name weiterhin; die ändere ich damit nicht." else ""))
                host.activity("ANREDE · Name gelöscht · keine Anrede mit Namen")
            }
        }
    }

    /** A name typed into the settings; false = it is not a usable name and nothing changed. */
    fun typeName(raw: String): Boolean {
        val name = OwnerNameCommand.typed(raw) ?: return false
        store.name = OwnerTalkName(name, "In den Einstellungen eingetragen: $name", host.nowMillis())
        store.addressByName = true
        return true
    }

    fun forgetName() { store.name = null; store.addressByName = false }

    private fun greetBack(request: String) {
        val now = host.nowMillis()
        val absence = if (store.returnGreeting) OwnerReturn.absence(store.lastSeenMillis.takeIf { it > 0L }, now, host.zone()) else null
        host.acceptOwnerTurn(request)
        host.reply(request, greeting(absence, now))
        host.activity("BEGRÜSSUNG · feste Antwort der App · kein Modell- oder Werkzeugaufruf" +
            if (absence != null) " · Pause aus der Geräteuhr berechnet" else "")
    }

    private fun greeting(absence: at.heinz.deviceai.cognitive.OwnerAbsence?, now: Long): String {
        val hour = Instant.ofEpochMilli(now).atZone(host.zone()).hour
        val said = OwnerGreeting.reply(name(), hour, absence, store.greetingCount, store.greetingVariant)
        store.greetingVariant = said.index
        store.greetingCount = (store.greetingCount + 1) % 1_000_000
        val extra = runCatching { greetingExtra() }.getOrNull()?.trim()
            ?.takeIf { it.length in 2..MAX_EXTRA_CHARS && it.none { char -> char.isISOControl() } }
        return listOf(said.opening, extra ?: said.closing).filter { it.isNotEmpty() }.joinToString(" ")
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
        val text = greeting(OwnerReturn.absence(last, now, host.zone()), now)
        greeted = now to text
        host.greet(text)
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
        private const val FACTS_MILLIS = 60_000L

        /**
         * The block for the cloud brain's constant system frame, from the same store and style source – without the
         * Activity, so the process-wide brain never holds one. [nameAllowed]: the owner's consent that covers his name.
         */
        fun cloudBlock(store: OwnerTalkStore, confirmedFacts: () -> List<String>, nameAllowed: () -> Boolean): String? {
            val name = if (!store.addressByName || !nameAllowed()) null
                else store.name?.name ?: runCatching { OwnerNameCommand.fromFacts(confirmedFacts()) }.getOrNull()
            return OwnerToneStyle.claudeBlock(store.tone, name)
        }
    }
}
