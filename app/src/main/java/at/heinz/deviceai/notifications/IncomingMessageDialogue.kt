package at.heinz.deviceai.notifications

import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.cognitive.OwnerAppLookupIntent
import java.util.Locale

/**
 * The owner's name and the tone for the personal address („Hey Heinz, darf ich dich kurz stören?“).
 *
 * This feature keeps no name of its own. The app wires this to its real source of the owner's name and of the
 * personal/„Sachlich“ setting; the default knows no name and speaks personally.
 */
interface IncomingOwnerProfile {
    /** How the owner wants to be addressed, or null when the app does not know his name. */
    val ownerName: String? get() = null
    /** false = „Sachlich“: plain wording without „Hey“ and without asking for permission to interrupt. */
    val personalTone: Boolean get() = true

    companion object {
        val DEFAULT: IncomingOwnerProfile = object : IncomingOwnerProfile {}
    }
}

/** The two channels the existing send lane can answer on. */
internal enum class IncomingReplyChannel { WHATSAPP, GMAIL }

/** Everything the conversation about incoming messages needs from the Activity. Fakes in host tests. */
internal interface IncomingMessageHost {
    /** Changes with every owner turn any lane accepted; an open question only counts until the next one. */
    val ownerTurn: Long
    val assistantName: String?
    val profile: IncomingOwnerProfile
    fun nowMillis(): Long
    fun minuteOfDay(): Int
    fun settings(): IncomingMessageSettings
    fun updateSettings(change: (IncomingMessageSettings) -> IncomingMessageSettings)
    /** No job, no open approval or lane question, no mission step, nothing typed, nobody speaking. */
    fun quiet(): Boolean
    /**
     * Says something the owner did not ask for: shown, and spoken with the offline voice when speech is on.
     * False = not possible right now (he is speaking, or a reply is being spoken); it is tried again.
     */
    fun announce(title: String, detail: String, spoken: String): Boolean
    /** Takes the text out of the input field and shows it as the owner's turn. */
    fun acceptOwnerTurn(request: String)
    /** The answer to the owner's turn: shown for this session only and spoken with the offline voice. */
    fun reply(text: String)
    /** One line for the activity log. Names the app and what happened, never sender or content. */
    fun activity(line: String)
    fun stage(event: CockpitAnimationEvent) {}
    fun later(delayMs: Long, action: () -> Unit)
    /**
     * Hands the answer the owner dictated and confirmed to the send lane, which applies its own rules (standing
     * grant, contact resolution, screen check, daily limit) and reports itself. False = the lane did not take it.
     */
    fun sendReply(request: String, channel: IncomingReplyChannel, recipient: String, subject: String, text: String): Boolean
    /** Continuum's own card with „Vorlesen“ / „Später“ for an answer by tap. */
    fun card(item: IncomingItem) {}
    fun clearCard(item: IncomingItem) {}
}

/** All sentences of the conversation. Third-party text only ever stands behind a fixed German frame. */
internal object IncomingMessageWording {
    private val COUNT = listOf("eine", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn", "elf", "zwölf")
    private val ORDINAL = listOf("erstens", "zweitens", "drittens", "viertens", "fünftens")
    private val SECRET_WORD = Regex("passwor[dt]|kennwort|passphrase|zugangsdaten|(?<![\\p{L}\\p{N}])(?:pin|puk|tans?)(?![\\p{L}\\p{N}])" +
        "|sicherheitscode|bestätigungscode|verifizierungscode|einmalcode|einmalpasswort")

    const val NOT_KEPT = "Diese Nachricht habe ich nicht mehr vorgemerkt. Öffne bitte die App, in der sie eingegangen ist."
    const val NOTHING_OPEN = "Es ist keine Nachricht offen."
    const val NOT_READ = "In Ordnung, ich lese sie nicht vor."
    const val KEPT = "In Ordnung, ich merke sie mir. Frag mich einfach: Was ist noch offen?"
    const val ASK_REPLY = "Möchtest du antworten?"
    const val NO_REPLY = "In Ordnung."
    const val REPLY_KEPT = "In Ordnung, die Antwort bleibt offen. Gesendet wurde nichts."
    const val NOTHING_SENT = "In Ordnung, es wurde nichts gesendet."
    const val TEXT_REFUSED = "Diesen Text kann ich nicht übernehmen. Bitte diktiere die Antwort noch einmal."
    const val NOT_HANDED = "Die Antwort konnte ich nicht an den Sendeweg übergeben. Es wurde nichts gesendet."
    const val GMAIL_GRANT = "Für Antworten per E-Mail ist „E-Mails über Gmail selbst senden“ nicht eingeschaltet " +
        "(Einstellungen → Selbst senden). Es wurde nichts gesendet."
    const val DND_ON = "In Ordnung, ich störe dich nicht. Nachrichten merke ich mir solange. Sag „Du darfst wieder“, " +
        "wenn ich mich wieder melden soll."
    const val DND_OFF = "Gut, ich melde mich wieder, wenn eine Nachricht kommt."
    const val CLEARED = "Die offenen Nachrichten sind gelöscht."
    const val NO_DECISION = "Dazu habe ich nichts vorgemerkt."
    const val SWITCHED_OFF = "„Eingehende Nachrichten melden“ ist ausgeschaltet. Du findest den Schalter unter " +
        "Einstellungen → Eingehende Nachrichten melden."

    fun countWord(count: Int): String = COUNT.getOrNull(count - 1) ?: count.toString()

    private fun name(profile: IncomingOwnerProfile): String? = profile.ownerName
        ?.let { UntrustedNotificationText.clean(it).filter { c -> c.isLetter() || c == ' ' || c == '-' }.trim().take(30) }
        ?.takeIf { it.isNotEmpty() }

    private fun noun(item: IncomingItem, plural: Boolean): String =
        if (item.kind == IncomingKind.MAIL) (if (plural) "E-Mails" else "E-Mail") else (if (plural) "Nachrichten" else "Nachricht")

    /** „eine Nachricht von Anna auf WhatsApp“ – what arrived, without any content. */
    fun what(item: IncomingItem): String {
        val count = item.count.coerceAtLeast(1)
        val amount = if (count == 1) "eine ${noun(item, false)}" else "${countWord(count)} ${noun(item, true)}"
        val from = when {
            item.isGroup && count == 1 && item.sender.isNotEmpty() -> "von ${item.sender} in der Gruppe ${item.conversation}"
            item.isGroup -> "in der Gruppe ${item.conversation}"
            item.unknownNumber -> "von einer unbekannten Nummer"
            item.sender.isEmpty() -> ""
            else -> "von ${item.sender}"
        }
        val where = (if (item.kind == IncomingKind.MAIL) "in " else "auf ") + item.appLabel
        return listOf(amount, from, where).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** One line for the card and the stage: who and where, no content. */
    fun cardLine(item: IncomingItem): String = when {
        item.hidden -> "In ${item.appLabel} ist etwas eingegangen – Absender und Inhalt nicht sichtbar"
        else -> what(item).replaceFirstChar { it.titlecase(Locale.GERMAN) } + " – vorlesen?"
    }

    /** The clause in front of a delayed offer, with its comma where German needs one. */
    private fun lead(hold: IncomingHold): String = when (hold) {
        IncomingHold.BUSY -> "Während du beschäftigt warst,"
        IncomingHold.AWAY -> "Während du weg warst,"
        IncomingHold.QUIET_HOURS -> "Während der Ruhezeit"
        IncomingHold.DO_NOT_DISTURB -> "Während ich dich nicht stören sollte,"
    }

    private fun more(others: Int): String = when {
        others <= 0 -> ""
        others == 1 -> " Außerdem wartet noch eine weitere Nachricht."
        else -> " Außerdem warten noch ${countWord(others)} weitere Nachrichten."
    }

    /** The unprompted question. Personal by default, plain in „Sachlich“; the name only when the app knows it. */
    fun offer(item: IncomingItem, profile: IncomingOwnerProfile, others: Int): String {
        val plural = item.count > 1
        val personal = profile.personalTone
        val name = name(profile)
        val question = if (personal) "Soll ich sie dir vorlesen?" else "Soll ich sie vorlesen?"
        val unclear = if (!item.unclear) "" else
            " Ich kann nicht einordnen, ob sie wichtig " + (if (plural) "sind." else "ist.")
        val hold = item.held
        if (hold == null) {
            val start = when {
                !personal -> ""
                name != null -> "Hey $name, darf ich dich kurz stören? "
                else -> "Hey, darf ich dich kurz stören? "
            }
            return start + "Du hast ${what(item)} bekommen.$unclear $question" + more(others)
        }
        val start = if (personal && name != null) "$name, " + lead(hold).replaceFirstChar { it.lowercase(Locale.GERMAN) } else lead(hold)
        val came = if (plural) "kamen" else "kam"
        return if (unclear.isEmpty() && personal) "$start $came ${what(item)} – ${question.replaceFirstChar { it.lowercase(Locale.GERMAN) }}" + more(others)
        else "$start $came ${what(item)}.$unclear $question" + more(others)
    }

    /** Android showed no sender and no text (locked device, or only the app's summary): an honest hint, no question. */
    fun hidden(item: IncomingItem, profile: IncomingOwnerProfile): String {
        val name = name(profile)
        val where = (if (item.kind == IncomingKind.MAIL) "in " else "auf ") + item.appLabel
        val several = item.count > 1
        val what = if (several) "${countWord(item.count)} Benachrichtigungen" else "etwas für dich"
        val verb = if (several) "sind" else "ist"
        val hold = item.held
        val start = when {
            // „Während du weg warst, ist auf WhatsApp etwas für dich eingegangen.“
            hold != null -> (if (profile.personalTone && name != null) "$name, " + lead(hold).replaceFirstChar { it.lowercase(Locale.GERMAN) }
                else lead(hold)) + " $verb $where $what eingegangen"
            profile.personalTone -> (if (name != null) "Hey $name, " else "Hey, ") + "$where $verb $what eingegangen"
            else -> where.replaceFirstChar { it.titlecase(Locale.GERMAN) } + " $verb $what eingegangen"
        }
        return "$start. Absender und Inhalt hat mir Android dazu nicht gezeigt; bei gesperrtem Gerät ist das immer so. " +
            "Sag „Öffne ${item.appLabel}“, wenn du nachsehen möchtest."
    }

    /** The one sentence the service may speak while the app is not in front. No question: no microphone is open. */
    fun background(item: IncomingItem, profile: IncomingOwnerProfile): String {
        val name = name(profile)
        val start = if (!profile.personalTone) "" else if (name != null) "Hey $name, " else "Hey, "
        val where = (if (item.kind == IncomingKind.MAIL) "in " else "auf ") + item.appLabel
        if (item.hidden) return (start + "$where ist etwas für dich eingegangen.").replaceFirstChar { it.titlecase(Locale.GERMAN) }
        return (start + "du hast ${what(item)} bekommen.").replaceFirstChar { it.titlecase(Locale.GERMAN) } +
            " Öffne Continuum, wenn ich sie vorlesen soll."
    }

    private fun secret(part: IncomingPart): Boolean =
        UntrustedNotificationText.containsSecurityCode(part.subject, part.text) ||
            SECRET_WORD.containsMatchIn((part.subject + "\n" + part.text).lowercase(Locale.ROOT))

    private fun sentence(text: String): String = if (text.isNotEmpty() && text.last() in ".!?…") text else "$text."

    /**
     * Sender and text as the notification showed them. Codes and passwords are named as present and not read,
     * links and addresses are replaced by a word; a mail is said to be only its beginning.
     */
    fun read(item: IncomingItem): String {
        val parts = item.parts
        if (parts.isEmpty()) return "Den Inhalt habe ich nicht mehr."
        var withheld = false
        var links = false
        fun spoken(raw: String): String = UntrustedNotificationText.replaceLinks(raw).also { if (it != raw) links = true }
        val text = StringBuilder()
        if (item.count > parts.size) text.append("Es sind ${countWord(item.count)} ${noun(item, true)}; ich habe die letzten " +
            "${countWord(parts.size)}. ")
        if (item.kind == IncomingKind.MAIL) {
            text.append(if (parts.size == 1) "E-Mail von ${item.sender}." else
                "${countWord(parts.size).replaceFirstChar { it.titlecase(Locale.GERMAN) }} E-Mails von ${item.sender}.")
            parts.forEach { part ->
                if (secret(part)) { withheld = true; text.append(" Eine E-Mail mit einem Code oder Passwort."); return@forEach }
                text.append(if (part.subject.isEmpty()) " Ohne Betreff." else " Betreff: " + sentence(spoken(part.subject)))
                text.append(if (part.text.isEmpty()) " Mehr zeigt die Benachrichtigung nicht." else " Der Anfang lautet: „${spoken(part.text)}“")
            }
            text.append(" Die ganze E-Mail steht in ${item.appLabel}.")
        } else {
            if (item.isGroup) text.append("In der Gruppe ${item.conversation}: ")
            val single = if (item.unknownNumber) "Die unbekannte Nummer" else item.sender.ifEmpty { "Der Absender" }
            text.append(parts.withIndex().joinToString(" ") { (index, part) ->
                val who = if (item.isGroup) part.sender.ifEmpty { "Jemand" } else single
                when {
                    secret(part) -> { withheld = true; (if (index == 0 || item.isGroup) "$who schickt" else "Dann") + " eine Nachricht mit einem Code oder Passwort." }
                    index == 0 || item.isGroup -> "$who schreibt: „${spoken(part.text)}“"
                    else -> "Dann: „${spoken(part.text)}“"
                }
            })
        }
        if (withheld) text.append(" Codes und Passwörter lese ich nicht vor; du findest sie in ${item.appLabel}.")
        if (links) text.append(" Links und Adressen lese ich nicht vor.")
        return text.toString()
    }

    fun askDictation(item: IncomingItem, profile: IncomingOwnerProfile): String =
        if (profile.personalTone) "Gern. Was soll ich ${item.sender} antworten?" else "Bitte diktiere die Antwort an ${item.sender}."

    fun confirm(item: IncomingItem, channel: IncomingReplyChannel, subject: String, text: String): String = when (channel) {
        IncomingReplyChannel.WHATSAPP -> "Ich antworte ${item.sender} auf WhatsApp: „$text“. Soll ich das so senden?"
        IncomingReplyChannel.GMAIL -> "Ich schreibe ${item.sender} über Gmail eine neue E-Mail mit dem Betreff „$subject“: " +
            "„$text“. Soll ich das so senden?"
    }

    /** Why no answer is offered for [item]; said once after reading. */
    fun noReply(item: IncomingItem): String = when {
        item.isGroup -> "In Gruppen antworte ich nicht selbst."
        item.kind == IncomingKind.MAIL -> "Wenn du antworten möchtest, öffne bitte ${item.appLabel}."
        item.unknownNumber -> "Der Absender steht nicht mit Namen in deinen Kontakten; antworte bitte direkt in ${item.appLabel}."
        else -> "Antworten kann ich bisher nur in WhatsApp und Gmail; öffne dafür bitte ${item.appLabel}."
    }

    fun list(open: List<IncomingItem>): String {
        if (open.isEmpty()) return NOTHING_OPEN
        fun entry(item: IncomingItem): String = (if (item.hidden) "etwas ${if (item.kind == IncomingKind.MAIL) "in" else "auf"} ${item.appLabel}, ohne sichtbaren Absender"
            else what(item)) + if (item.read) " (gelesen, die Antwort ist offen)" else ""
        if (open.size == 1) return "Offen ist ${entry(open.first())}."
        val shown = open.take(ORDINAL.size)
        return "Offen sind ${countWord(open.size)} Einträge: " +
            shown.withIndex().joinToString("; ") { (index, item) -> "${ORDINAL[index]} ${entry(item)}" } + "." +
            if (open.size > shown.size) " Und ${countWord(open.size - shown.size)} weitere." else ""
    }

    fun why(record: IncomingDecisionRecord): String {
        val about = listOf(record.who.takeIf { it.isNotEmpty() }?.let { "von $it" }, "(${record.appLabel})").filterNotNull().joinToString(" ")
        return if (record.reported) "Die Nachricht $about habe ich gemeldet: ${record.explanation}"
        else "Die Nachricht $about habe ich nicht gemeldet: ${record.explanation}"
    }
}

/** The owner's sentences this lane understands. Deterministic and narrow: everything else keeps its present route. */
internal object IncomingMessageCommands {
    sealed interface Command {
        data object OpenList : Command
        /** „Lies die Nachricht von Anna vor“; [who] is null for „Lies die Nachricht vor“. */
        data class Read(val who: String?) : Command
        data object DoNotDisturb : Command
        data object MayDisturb : Command
        data class Why(val reported: Boolean, val who: String?) : Command
        data class Always(val who: String) : Command
        data class Never(val who: String) : Command
        /** „So etwas nie mehr melden“: the sender of the message that was reported last. */
        data object NeverLast : Command
        data object Clear : Command
    }

    private const val END = "[.!?]?$"
    private val openList = Regex("(?iu)^(?:(?:was|welche (?:nachrichten|mails|e-mails)) (?:ist|sind) (?:denn |jetzt |gerade )?(?:noch )?offen" +
        "|(?:(?:zeig|zeige|nenn|nenne|sag|sage) (?:mir )?)?(?:die |meine )?offenen? nachrichten(?: zeigen| anzeigen| auflisten| vorlesen)?" +
        "|(?:gibt es|habe ich|hab ich) (?:noch )?offene nachrichten)$END")
    private val read = Regex("(?iu)^(?:bitte )?(?:lies|lese|les) (?:mir )?(?:bitte )?(?:die|meine|diese) (?:offenen? |neuen? |letzten? )?" +
        "(?:nachricht(?:en)?|mail|e-mail|whatsapp)(?: von (.+?))? (?:mir )?(?:bitte )?vor$END")
    private val dnd = Regex("(?iu)^(?:bitte )?(?:stör(?:e)? mich (?:jetzt |gerade |bitte |heute )*nicht(?: mehr)?" +
        "|(?:jetzt |bitte )*nicht stören|lass mich (?:jetzt |bitte )*in ruhe)$END")
    private val mayDisturb = Regex("(?iu)^(?:du (?:darfst|kannst) (?:mich )?(?:jetzt )?wieder(?: stören| melden)?" +
        "|(?:nicht stören|ruhe) (?:aus|beenden|ende|aufheben))$END")
    private val why = Regex("(?iu)^(?:warum|wieso|weshalb) hast du (?:mir )?(?:das|es|(?:die|diese|die letzte) (?:nachricht|mail|e-mail))" +
        "(?: von (.+?))? (nicht )?(?:gemeldet|angesagt)$END")
    private val always = Regex("(?iu)^(?:bitte )?melde (?:mir )?(?:nachrichten |mails |e-mails )?(?:von )?(.+?) (?:bitte )?immer$END")
    private val never = Regex("(?iu)^(?:bitte )?melde (?:mir )?(?:nachrichten |mails |e-mails )?(?:von )?(.+?) (?:bitte )?(?:nie|niemals|nie mehr|nicht mehr|nie wieder)$END")
    private val neverLast = Regex("(?iu)^(?:so etwas|sowas|das|den absender|diesen absender|die) (?:bitte )?(?:nie|nicht) (?:mehr |wieder )?melden$END")
    private val clear = Regex("(?iu)^(?:(?:lösche|lösch|leere) (?:bitte )?(?:die |alle )?offenen? nachrichten|offene nachrichten (?:löschen|leeren))$END")
    private val pronoun = setOf("das", "es", "sie", "die", "so etwas", "sowas", "nichts", "alles", "mir", "dich", "mich")

    private fun who(raw: String): String? = raw.trim().trim('"', '„', '“', '”').trim()
        .takeIf { it.length in 2..IncomingMessageSettingsCodec.MAX_ENTRY && it.lowercase(Locale.GERMAN) !in pronoun }

    fun parse(said: String): Command? {
        val text = said.trim()
        if (text.length !in 3..160 || text.any { it.isISOControl() }) return null
        if (openList.matches(text)) return Command.OpenList
        read.matchEntire(text)?.let { return Command.Read(it.groupValues[1].takeIf(String::isNotEmpty)?.let(::who)) }
        if (dnd.matches(text)) return Command.DoNotDisturb
        if (mayDisturb.matches(text)) return Command.MayDisturb
        why.matchEntire(text)?.let {
            return Command.Why(reported = it.groupValues[2].isEmpty(), who = it.groupValues[1].takeIf(String::isNotEmpty)?.let(::who))
        }
        if (neverLast.matches(text)) return Command.NeverLast
        if (clear.matches(text)) return Command.Clear
        never.matchEntire(text)?.let { match -> who(match.groupValues[1])?.let { return Command.Never(it) } }
        always.matchEntire(text)?.let { match -> who(match.groupValues[1])?.let { return Command.Always(it) } }
        return null
    }

    private val yes = Regex("(?iu)^(?:ja|jo|jawohl|ja bitte|ja gerne|ja gern|gerne|gern|bitte|ok|okay|klar|ja klar|mach|mach das|ja mach|" +
        "unbedingt|passt|ja passt|genau|ja genau|(?:ja[, ]+)?(?:bitte )?(?:vorlesen|lies vor|lies sie vor|lies sie mir vor|lies es vor|lies mal vor)(?: bitte)?)[.!]?$")
    private val no = Regex("(?iu)^(?:nein|nö|nein danke|danke nein|nicht nötig|lass|lass es|lass nur|lass gut sein|brauchst du nicht|" +
        "nicht vorlesen|nichts|nicht antworten|keine antwort|nein nicht antworten|abbrechen|abbruch|stopp|stop|doch nicht|vergiss es|" +
        "nicht senden|nicht schicken|nein nicht senden)[.!]?$")
    private val later = Regex("(?iu)^(?:(?:ja[, ]+|nein[, ]+)?(?:später|spaeter)(?: bitte| vielleicht)?|nicht jetzt|jetzt nicht|nachher|danach|" +
        "vielleicht später|erinner(?:e)? mich später)[.!]?$")
    private val send = Regex("(?iu)^(?:ja|jo|jawohl|ja bitte|ja genau|genau so|ja passt|passt|passt so|ja gerne|ok|okay|" +
        "(?:ja[, ]+)?(?:bitte )?(?:so )?(?:senden|absenden|abschicken|schicken)|" +
        "(?:ja[, ]+)?(?:schick|send)e?(?: es| sie| das)?(?: so)?(?: ab| raus| weg)?(?: bitte)?)[.!]?$")
    private val redo = Regex("(?iu)^(?:(?:nein[, ]+)?(?:neu|anders|nochmal|noch einmal|ändern|korrigieren|neu diktieren|anderer text))[.!]?$")
    private val yesWithText = Regex("(?iu)^ja[, ]+(?:bitte )?(?:schreib(?:e)?|antworte|sag(?:e)?)(?: (?:ihr|ihm|ihnen|zurück))*[,:]?\\s+(.{2,})$", RegexOption.DOT_MATCHES_ALL)
    private val dictationLead = Regex("(?iu)^(?:bitte )?(?:schreib(?:e)?|antworte|sag(?:e)?)(?: (?:ihr|ihm|ihnen|zurück|bitte))*[,:]\\s+")
    private val ordinal = Regex("(?iu)^(?:(?:ja[, ]+)?(?:lies )?(?:die|den|das) )?(erste|zweite|dritte|vierte|fünfte)n?(?: nachricht| mail| e-mail)?(?: bitte)?(?: vor| vorlesen)?[.!]?$")
    private val ordinals = listOf("erste", "zweite", "dritte", "vierte", "fünfte")

    fun isYes(said: String): Boolean = yes.matches(said.trim())
    fun isNo(said: String): Boolean = no.matches(said.trim())
    fun isLater(said: String): Boolean = later.matches(said.trim())
    fun isSend(said: String): Boolean = send.matches(said.trim())
    fun isRedo(said: String): Boolean = redo.matches(said.trim())
    /** „Ja, schreib ihr, ich komme um fünf“ → the text behind it. */
    fun yesWithText(said: String): String? = yesWithText.matchEntire(said.trim())?.groupValues?.get(1)
    /** Index of „die zweite“ in a list that was just read out, or null. */
    fun ordinal(said: String): Int? = ordinal.matchEntire(said.trim())?.let { ordinals.indexOf(it.groupValues[1].lowercase(Locale.GERMAN)) }?.takeIf { it >= 0 }

    /**
     * The owner's dictated answer as message text: his own words, a leading „Antworte:“ removed, the first letter
     * upper-cased, nothing else changed. Null = not usable (empty, too long, control characters).
     */
    fun dictation(said: String): String? {
        val text = dictationLead.replace(said.trim(), "").trim().trim('"', '„', '“', '”').trim()
        if (text.isEmpty() || text.length > MAX_REPLY || text.any { it.isISOControl() && it != '\n' }) return null
        return if (text.first().isLowerCase()) text.substring(0, 1).uppercase(Locale.GERMAN) + text.substring(1) else text
    }

    const val MAX_REPLY = 600
}

/**
 * The conversation about incoming messages (owner orders 18. and 19.11.2026): „Hey Heinz, darf ich dich kurz stören?
 * Du hast eine Nachricht von Anna auf WhatsApp bekommen. Soll ich sie dir vorlesen?“ → reading → „Möchtest du
 * antworten?“ → his dictated answer, repeated → the existing send lane.
 *
 * Free of Android and of any model. Fixed rules:
 *  - It speaks unprompted only in a quiet moment ([IncomingMessageHost.quiet]), outside the quiet hours and outside
 *    „Stör mich jetzt nicht“; a message that had to wait is offered later and says so.
 *  - One question at a time. A question counts for the owner's very next turn and a bounded time; silence or another
 *    order ends it – an unread message then stays in „Offene Nachrichten“, and nothing is ever sent.
 *  - Reading happens only after his yes (or his tap on „Vorlesen“). Codes, passwords and links are not read.
 *  - An answer is his dictated wording, repeated once and sent only after a clear yes, and only by the send lane.
 *  - Notification content is output only: it is never interpreted as a command and never handed to a model.
 */
internal class IncomingMessageDialogue(
    private val host: IncomingMessageHost,
    private val inbox: IncomingMessageInbox,
) {
    private enum class Kind { READ, REPLY, DICTATE, CONFIRM }
    private class Asked(val kind: Kind, val item: IncomingItem, val at: Long, val turn: Long, val text: String = "",
                        val listed: List<IncomingItem> = emptyList())

    private var asked: Asked? = null
    private var attached = false
    private var lastOfferAt = -OFFER_GAP_MS
    private var wanted: IncomingItem? = null
    private var sending: IncomingItem? = null
    private var sendingSince = 0L
    private var pollAt = Long.MAX_VALUE
    private var lastSender: String? = null

    /** true while a question of this lane waits for the owner's next turn. */
    val awaitingAnswer: Boolean get() = asked != null
    /**
     * When the question that still counts for the owner's next turn was asked ([IncomingMessageHost.nowMillis]); null
     * when none does. For a host on which another source asks a question of its own (the notification listener's
     * „Darf ich sie vorlesen?“): the owner's „Lies vor“ belongs to the question that was asked last.
     */
    val questionAt: Long? get() = asked?.takeIf { host.ownerTurn == it.turn && host.nowMillis() - it.at <= window(it.kind) }?.at
    val doNotDisturb: Boolean get() = host.nowMillis() < inbox.doNotDisturbUntil

    /** The Activity is in front: messages that became ready meanwhile are offered as „Während du weg warst …“. */
    fun attach() {
        attached = true
        if (asked == null && sending == null) inbox.reclaim()
        inbox.waiting().forEach { if (it.held == null) it.held = IncomingHold.AWAY }
        schedule(ATTACH_DELAY_MS)
    }

    fun detach() {
        attached = false
    }

    /** The inbox closed a burst. */
    fun ready(item: IncomingItem) {
        if (attached) poll()
    }

    fun gone(item: IncomingItem) {
        if (asked?.item === item) asked = null
        if (wanted === item) wanted = null
        host.clearCard(item)
    }

    /**
     * Offers the oldest waiting message when this is a quiet moment. True = something was said or shown now.
     * Called when a burst closes, after a conversation about one message ended, when the recogniser pauses, and on
     * its own bounded timer while messages wait.
     */
    fun poll(): Boolean {
        if (!attached || !host.settings().enabled) return false
        settle()
        if (asked != null) return false
        wanted?.let { item ->
            if (item.state == IncomingState.DONE) wanted = null
            else if (host.quiet()) { wanted = null; read(item); return true }
            else { schedule(RETRY_MS); return false }
        }
        val waiting = inbox.waiting()
        if (waiting.isEmpty()) return false
        val now = host.nowMillis()
        val hold = when {
            now < inbox.doNotDisturbUntil -> IncomingHold.DO_NOT_DISTURB
            host.settings().quietAt(host.minuteOfDay()) -> IncomingHold.QUIET_HOURS
            !host.quiet() -> IncomingHold.BUSY
            else -> null
        }
        if (hold != null) {
            waiting.forEach { if (it.held == null || it.held == IncomingHold.BUSY) it.held = hold }
            schedule(if (hold == IncomingHold.BUSY) RETRY_MS else LONG_RETRY_MS)
            return false
        }
        if (now - lastOfferAt < OFFER_GAP_MS) { schedule(RETRY_MS); return false }
        return offer(waiting.first(), waiting.size - 1, now)
    }

    /** The owner's own turn. true = it belonged to this lane and is completely handled. */
    fun handle(raw: String): Boolean {
        if (!host.settings().enabled) { asked = null; return false }
        val said = OwnerAppLookupIntent.stripAddress(raw.trim(), host.assistantName).trim()
        if (said.isEmpty() || said.length > MAX_TURN) return false
        settle()
        asked?.let { open -> if (answer(open, raw, said)) return true }
        return command(raw, said)
    }

    /** A tap on Continuum's own card: „Vorlesen“ ([read]) or „Später“. */
    fun action(itemId: Long, read: Boolean) {
        val item = inbox.item(itemId)
        if (item == null) { if (read) host.reply(IncomingMessageWording.NOT_KEPT); return }
        if (asked?.item === item) asked = null
        if (!read) {
            if (item.state != IncomingState.ACTIVE) inbox.mark(item, IncomingState.OPEN)
            host.clearCard(item)
            return
        }
        settle()
        asked?.let { open -> asked = null; if (open.kind == Kind.READ && open.item.state == IncomingState.OFFERED) inbox.mark(open.item, IncomingState.OPEN) }
        wanted = item
        if (attached) poll()
    }

    /** How the send lane ended the handed-over answer. [waitsForOwner]: it asked him something itself. */
    fun replySettled(sent: Boolean, waitsForOwner: Boolean) {
        val item = sending ?: return
        if (host.nowMillis() - sendingSince > SEND_WINDOW_MS) { sending = null; return }
        if (waitsForOwner) { stage(item, Phase.SEND_QUESTION); return }
        sending = null
        stage(item, if (sent) Phase.SENT else Phase.NOT_SENT)
        host.activity("Nachricht · ${item.appLabel} · " + if (sent) "Antwort gesendet" else "Antwort nicht gesendet")
        finish(item)
        next()
    }

    // ── answers ───────────────────────────────────────────────────────────────────────────────────────────────

    private fun answer(open: Asked, raw: String, said: String): Boolean {
        val item = open.item
        when (open.kind) {
            Kind.READ -> {
                val picked = IncomingMessageCommands.ordinal(said)?.let { open.listed.getOrNull(it) }
                when {
                    picked != null -> {
                        host.acceptOwnerTurn(raw)
                        if (picked !== item && item.state == IncomingState.OFFERED) inbox.mark(item, IncomingState.OPEN)
                        read(picked)
                    }
                    IncomingMessageCommands.isYes(said) -> { host.acceptOwnerTurn(raw); read(item) }
                    IncomingMessageCommands.isNo(said) -> {
                        host.acceptOwnerTurn(raw); asked = null
                        host.reply(IncomingMessageWording.NOT_READ); finish(item); next()
                    }
                    IncomingMessageCommands.isLater(said) -> {
                        host.acceptOwnerTurn(raw); asked = null
                        inbox.mark(item, IncomingState.OPEN); host.clearCard(item)
                        host.reply(IncomingMessageWording.KEPT); next()
                    }
                    else -> return false
                }
            }
            Kind.REPLY -> {
                val direct = IncomingMessageCommands.yesWithText(said)
                when {
                    direct != null -> { host.acceptOwnerTurn(raw); dictated(item, direct) }
                    IncomingMessageCommands.isYes(said) -> {
                        host.acceptOwnerTurn(raw)
                        host.reply(IncomingMessageWording.askDictation(item, host.profile))
                        ask(Kind.DICTATE, item)
                    }
                    IncomingMessageCommands.isNo(said) -> {
                        host.acceptOwnerTurn(raw); asked = null
                        host.reply(IncomingMessageWording.NO_REPLY); finish(item); next()
                    }
                    IncomingMessageCommands.isLater(said) -> { host.acceptOwnerTurn(raw); keepForReply(item) }
                    else -> return false
                }
            }
            Kind.DICTATE -> {
                host.acceptOwnerTurn(raw)
                when {
                    IncomingMessageCommands.isNo(said) -> { asked = null; host.reply(IncomingMessageWording.NOTHING_SENT); finish(item); next() }
                    IncomingMessageCommands.isLater(said) -> keepForReply(item)
                    else -> dictated(item, said)
                }
            }
            Kind.CONFIRM -> when {
                IncomingMessageCommands.isSend(said) -> send(item, raw, open.text)
                IncomingMessageCommands.isRedo(said) -> {
                    host.acceptOwnerTurn(raw)
                    host.reply("In Ordnung. Wie soll die Antwort lauten?")
                    ask(Kind.DICTATE, item)
                }
                IncomingMessageCommands.isNo(said) -> {
                    host.acceptOwnerTurn(raw); asked = null
                    host.reply(IncomingMessageWording.NOTHING_SENT); finish(item); next()
                }
                IncomingMessageCommands.isLater(said) -> { host.acceptOwnerTurn(raw); keepForReply(item) }
                else -> return false
            }
        }
        return true
    }

    private fun keepForReply(item: IncomingItem) {
        asked = null
        item.read = true
        inbox.mark(item, IncomingState.OPEN)
        host.reply(IncomingMessageWording.REPLY_KEPT)
        next()
    }

    private fun dictated(item: IncomingItem, said: String) {
        val channel = replyChannel(item)
        val text = IncomingMessageCommands.dictation(said)
        if (channel == null) { asked = null; host.reply(IncomingMessageWording.noReply(item)); finish(item); next(); return }
        if (text == null) { host.reply(IncomingMessageWording.TEXT_REFUSED); ask(Kind.DICTATE, item); return }
        host.reply(IncomingMessageWording.confirm(item, channel, subject(item), text))
        ask(Kind.CONFIRM, item, text)
    }

    private fun send(item: IncomingItem, raw: String, text: String) {
        val channel = replyChannel(item)
        asked = null
        if (channel == null) { host.acceptOwnerTurn(raw); host.reply(IncomingMessageWording.noReply(item)); finish(item); next(); return }
        sending = item
        sendingSince = host.nowMillis()
        stage(item, Phase.SENDING)
        // The send lane shows the owner's yes as his turn itself and reports in its own words.
        if (!host.sendReply(raw, channel, item.sender, subject(item), text)) {
            sending = null
            host.acceptOwnerTurn(raw)
            host.reply(if (channel == IncomingReplyChannel.GMAIL) IncomingMessageWording.GMAIL_GRANT else IncomingMessageWording.NOT_HANDED)
            stage(item, Phase.NOT_SENT)
            finish(item); next()
        }
    }

    private fun subject(item: IncomingItem): String {
        val subject = item.parts.lastOrNull()?.subject.orEmpty()
        return when {
            item.kind != IncomingKind.MAIL -> ""
            subject.isEmpty() -> "Antwort"
            subject.startsWith("Re:", ignoreCase = true) || subject.startsWith("AW:", ignoreCase = true) -> subject
            else -> "Re: $subject"
        }
    }

    /**
     * WhatsApp: a direct chat whose sender has a name (the send lane resolves it in the contacts). Gmail: a mail from
     * a person, as a new mail to the address the contacts hold for that name. Groups, other apps, unknown numbers
     * and mails of institutes are read only.
     */
    private fun replyChannel(item: IncomingItem): IncomingReplyChannel? = when {
        item.hidden || item.isGroup || item.sender.isEmpty() -> null
        item.packageName == "com.whatsapp" -> IncomingReplyChannel.WHATSAPP.takeUnless { item.unknownNumber }
        item.packageName == "com.google.android.gm" -> IncomingReplyChannel.GMAIL.takeIf {
            item.decision.reason == ImportanceReason.CONTACT || item.decision.reason == ImportanceReason.PERSONAL_NAME
        }
        else -> null
    }

    // ── commands ──────────────────────────────────────────────────────────────────────────────────────────────

    private fun command(raw: String, said: String): Boolean {
        when (val command = IncomingMessageCommands.parse(said) ?: return false) {
            IncomingMessageCommands.Command.OpenList -> { host.acceptOwnerTurn(raw); list() }
            is IncomingMessageCommands.Command.Read -> {
                val open = inbox.open().filterNot { it.hidden }
                val wantedSender = command.who?.lowercase(Locale.GERMAN)
                // Without an open message this sentence is not for this lane (documents and news are read elsewhere).
                val item = (if (wantedSender == null) open.firstOrNull()
                    else open.firstOrNull { wantedSender in it.sender.lowercase(Locale.GERMAN) || wantedSender in it.conversation.lowercase(Locale.GERMAN) })
                    ?: return false
                host.acceptOwnerTurn(raw)
                dropQuestion()
                read(item)
            }
            IncomingMessageCommands.Command.DoNotDisturb -> {
                host.acceptOwnerTurn(raw)
                dropQuestion()
                inbox.doNotDisturbUntil = host.nowMillis() + DO_NOT_DISTURB_MAX_MS
                host.reply(IncomingMessageWording.DND_ON)
            }
            IncomingMessageCommands.Command.MayDisturb -> {
                host.acceptOwnerTurn(raw)
                inbox.doNotDisturbUntil = 0L
                host.reply(IncomingMessageWording.DND_OFF)
                next()
            }
            is IncomingMessageCommands.Command.Why -> {
                host.acceptOwnerTurn(raw)
                host.reply(inbox.lastDecision(command.reported, command.who)?.let(IncomingMessageWording::why)
                    ?: IncomingMessageWording.NO_DECISION)
            }
            is IncomingMessageCommands.Command.Always -> { host.acceptOwnerTurn(raw); rule(command.who, always = true) }
            is IncomingMessageCommands.Command.Never -> { host.acceptOwnerTurn(raw); rule(command.who, always = false) }
            IncomingMessageCommands.Command.NeverLast -> {
                val sender = lastSender ?: return false
                host.acceptOwnerTurn(raw)
                rule(sender, always = false)
            }
            IncomingMessageCommands.Command.Clear -> {
                host.acceptOwnerTurn(raw)
                asked = null; wanted = null
                inbox.clear()
                host.reply(IncomingMessageWording.CLEARED)
            }
        }
        return true
    }

    private fun rule(who: String, always: Boolean) {
        host.updateSettings { current ->
            fun without(list: List<String>) = list.filterNot { it.equals(who, ignoreCase = true) }
            if (always) current.copy(always = without(current.always) + who, never = without(current.never))
            else current.copy(never = without(current.never) + who, always = without(current.always))
        }
        host.reply(if (always) "Gut, Nachrichten von „$who“ melde ich ab jetzt immer."
            else "Gut, Nachrichten von „$who“ melde ich ab jetzt nicht mehr.")
    }

    private fun list() {
        dropQuestion()
        val open = inbox.open()
        val first = open.firstOrNull { !it.hidden }
        val question = when {
            first == null -> ""
            first.read && replyChannel(first) != null -> " Möchtest du ${first.sender} jetzt antworten?"
            first.read -> ""
            open.size == 1 -> " Soll ich sie vorlesen?"
            else -> " Soll ich die erste vorlesen? Du kannst auch „die zweite“ sagen."
        }
        host.reply(IncomingMessageWording.list(open) + question)
        // A hint without content has been told now; there is nothing more to do with it.
        open.filter { it.hidden }.forEach { finish(it) }
        if (first == null || question.isEmpty()) return
        if (first.read) { inbox.mark(first, IncomingState.ACTIVE); ask(Kind.REPLY, first) }
        else { inbox.mark(first, IncomingState.OFFERED); ask(Kind.READ, first, listed = open.filterNot { it.hidden }) }
    }

    // ── offering and reading ──────────────────────────────────────────────────────────────────────────────────

    private fun offer(item: IncomingItem, others: Int, now: Long): Boolean {
        if (item.held == IncomingHold.BUSY && now - item.readyAtMs < HELD_AFTER_MS) item.held = null
        val spoken = if (item.hidden) IncomingMessageWording.hidden(item, host.profile)
            else IncomingMessageWording.offer(item, host.profile, others)
        if (!host.announce(item.appLabel, IncomingMessageWording.cardLine(item), spoken)) { schedule(RETRY_MS); return false }
        lastOfferAt = now
        host.activity("Nachricht · ${item.appLabel} · gemeldet")
        if (item.hidden) { finish(item); next(); return true }
        lastSender = item.sender.takeIf { it.isNotEmpty() && !item.unknownNumber }
        inbox.mark(item, IncomingState.OFFERED)
        stage(item, Phase.WAITING)
        host.card(item)
        ask(Kind.READ, item)
        return true
    }

    private fun read(item: IncomingItem) {
        asked = null
        inbox.mark(item, IncomingState.ACTIVE)
        host.clearCard(item)
        lastSender = item.sender.takeIf { it.isNotEmpty() && !item.unknownNumber }
        val channel = replyChannel(item)
        stage(item, Phase.READING)
        host.reply(IncomingMessageWording.read(item) + " " +
            if (channel != null) IncomingMessageWording.ASK_REPLY else IncomingMessageWording.noReply(item))
        item.read = true
        host.activity("Nachricht · ${item.appLabel} · vorgelesen")
        if (channel != null) { stage(item, Phase.REPLY_OPEN); ask(Kind.REPLY, item) } else { finish(item); next() }
    }

    private fun ask(kind: Kind, item: IncomingItem, text: String = "", listed: List<IncomingItem> = emptyList()) {
        asked = Asked(kind, item, host.nowMillis(), host.ownerTurn, text, listed)
        host.later(window(kind) + 250L) { settle(); poll() }
    }

    private fun window(kind: Kind): Long = if (kind == Kind.READ) OFFER_WINDOW_MS else ANSWER_WINDOW_MS

    /** Ends a question the owner did not answer: silence, or he said something else that another lane took. */
    private fun settle() {
        val open = asked ?: return
        if (host.ownerTurn == open.turn && host.nowMillis() - open.at <= window(open.kind)) return
        dropQuestion()
    }

    private fun dropQuestion() {
        val open = asked ?: return
        asked = null
        if (open.kind == Kind.READ) {
            if (open.item.state == IncomingState.OFFERED) inbox.mark(open.item, IncomingState.OPEN)
        } else {
            host.activity("Nachricht · ${open.item.appLabel} · keine Antwort gesendet")
            finish(open.item)
        }
    }

    private fun finish(item: IncomingItem) {
        inbox.close(item)
        host.clearCard(item)
        host.stage(CockpitAnimationEvent.TurnEnded(turn(item), host.nowMillis(), true))
    }

    private fun next() = schedule(NEXT_GAP_MS)

    /** One bounded timer: the earliest wanted wake-up wins, and nothing is scheduled while nothing waits. */
    private fun schedule(delayMs: Long) {
        val due = host.nowMillis() + delayMs
        if (due >= pollAt) return
        pollAt = due
        host.later(delayMs) {
            if (pollAt == due) { pollAt = Long.MAX_VALUE; poll() }
        }
    }

    private enum class Phase { WAITING, READING, REPLY_OPEN, SENDING, SEND_QUESTION, SENT, NOT_SENT }

    private fun turn(item: IncomingItem) = "incoming-${item.id}"

    /** Real steps for the existing work window: message waiting → reading → answer pending → sent. They name the app only. */
    private fun stage(item: IncomingItem, phase: Phase) {
        val who = item.appLabel
        val steps = ArrayList<ProcessStep>()
        steps += ProcessStep("incoming-wait", if (phase == Phase.WAITING) "Nachricht wartet · $who" else "Nachricht gemeldet · $who",
            if (phase == Phase.WAITING) StepState.WAITING else StepState.DONE)
        if (phase != Phase.WAITING) steps += ProcessStep("incoming-read", if (phase == Phase.READING) "Nachricht vorlesen" else "Nachricht vorgelesen",
            if (phase == Phase.READING) StepState.ACTIVE else StepState.DONE)
        when (phase) {
            Phase.REPLY_OPEN -> steps += ProcessStep("incoming-reply", "Antwort offen", StepState.WAITING)
            Phase.SENDING -> steps += ProcessStep("incoming-reply", "Antwort an den Sendeweg übergeben", StepState.ACTIVE)
            Phase.SEND_QUESTION -> steps += ProcessStep("incoming-reply", "Rückfrage des Sendewegs", StepState.WAITING)
            Phase.SENT -> steps += ProcessStep("incoming-reply", "Antwort gesendet", StepState.DONE)
            Phase.NOT_SENT -> steps += ProcessStep("incoming-reply", "Antwort nicht gesendet", StepState.BLOCKED)
            else -> Unit
        }
        host.stage(CockpitAnimationEvent.ProcessTimeline(turn(item), host.nowMillis(), steps))
    }

    companion object {
        /** How long „Soll ich sie dir vorlesen?“ waits for an answer. */
        const val OFFER_WINDOW_MS = 90_000L
        const val ANSWER_WINDOW_MS = 120_000L
        const val SEND_WINDOW_MS = 5 * 60_000L
        /** Unprompted offers are at least this far apart. */
        const val OFFER_GAP_MS = 8_000L
        const val NEXT_GAP_MS = 2_500L
        const val RETRY_MS = 2_500L
        const val LONG_RETRY_MS = 60_000L
        const val ATTACH_DELAY_MS = 1_500L
        /** A message that waited at least this long for a quiet moment is offered as „Während du beschäftigt warst …“. */
        const val HELD_AFTER_MS = 20_000L
        /** „Stör mich jetzt nicht“ ends by itself after this time (the open list expires then as well). */
        const val DO_NOT_DISTURB_MAX_MS = IncomingMessageInbox.RETENTION_MS
        const val MAX_TURN = 1_200
    }
}
