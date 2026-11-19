package at.heinz.deviceai.gmail

import at.heinz.deviceai.cognitive.OwnerAppLookupIntent
import at.heinz.deviceai.sending.IndirectSpeech
import at.heinz.deviceai.sending.OwnerSendIntentParser
import java.util.Locale

/** A day the owner named for mails („von gestern“). */
internal enum class MailDay { TODAY, YESTERDAY }

/**
 * What the mail lane has open in this chat, which decides whether a short follow-up („die zweite“, „Fass sie
 * zusammen“) belongs to it. A sentence that names mails itself („Lies die Mail vor“) needs no context.
 */
internal data class MailContext(val list: Boolean = false, val mail: Boolean = false, val draft: Boolean = false,
                                val confirming: Boolean = false) {
    val any: Boolean get() = list || mail || draft || confirming

    companion object {
        val NONE = MailContext()
        /** The regression sets' names for a context. */
        fun of(name: String): MailContext = when (name) {
            "LIST" -> MailContext(list = true)
            "MAIL" -> MailContext(mail = true)
            "DRAFT" -> MailContext(draft = true)
            "CONFIRM" -> MailContext(confirming = true)
            else -> NONE
        }
    }
}

/** One owner order of the mail lane. [code] is the route as the regression sets write it. */
internal sealed interface OwnerMailCommand {
    val code: String

    data object Connect : OwnerMailCommand { override val code = "CONNECT" }
    data object Disconnect : OwnerMailCommand { override val code = "DISCONNECT" }
    data object Status : OwnerMailCommand { override val code = "STATUS" }
    /** Unread mails in the inbox; [aloud]: „Lies mir meine neuen Mails vor“. */
    data class New(val aloud: Boolean = false) : OwnerMailCommand { override val code = "NEW" }
    data object Important : OwnerMailCommand { override val code = "IMPORTANT" }
    data object ImportantWhy : OwnerMailCommand { override val code = "IMPORTANT_WHY" }
    data class Search(val from: String?, val about: String?, val day: MailDay?) : OwnerMailCommand {
        override val code: String get() = when {
            day != null -> "SEARCH_DAY:" + day.name
            from != null && about != null -> "SEARCH_FROM:$from|ABOUT:$about"
            from != null -> "SEARCH_FROM:$from"
            else -> "SEARCH_ABOUT:$about"
        }
    }
    data class Latest(val from: String, val aloud: Boolean) : OwnerMailCommand {
        override val code: String get() = (if (aloud) "READ_LATEST_FROM:" else "LATEST_FROM:") + from
    }
    /** [index]: 1-based in the shown list; null = the mail we talk about. [thread]: the whole conversation. */
    data class Read(val index: Int? = null, val thread: Boolean = false) : OwnerMailCommand {
        override val code: String get() = if (thread) "READ_THREAD" else "READ" + (index?.let { ":$it" } ?: "")
    }
    data object ReadAttachment : OwnerMailCommand { override val code = "READ_ATTACHMENT" }
    /** A follow-up choice from the list: [index] (−1 = the last), or the one of [day], or the one from [from]. */
    data class Pick(val index: Int? = null, val day: MailDay? = null, val from: String? = null) : OwnerMailCommand {
        override val code: String get() = when {
            day != null -> "PICK_DAY:" + day.name
            from != null -> "PICK_FROM:$from"
            index == -1 -> "PICK:LAST"
            else -> "PICK:$index"
        }
    }
    enum class SummaryTarget { CONTEXT, IMPORTANT, NEW, THREAD }
    data class Summarize(val target: SummaryTarget, val index: Int? = null) : OwnerMailCommand {
        override val code: String get() = when (target) {
            SummaryTarget.CONTEXT -> "SUMMARIZE" + (index?.let { ":$it" } ?: "")
            SummaryTarget.IMPORTANT -> "SUMMARIZE:IMPORTANT"
            SummaryTarget.NEW -> "SUMMARIZE:NEW"
            SummaryTarget.THREAD -> "SUMMARIZE_THREAD"
        }
    }
    data class Deadlines(val inbox: Boolean) : OwnerMailCommand {
        override val code: String get() = if (inbox) "DEADLINES:INBOX" else "DEADLINES"
    }
    /** [text]: null = the lane asks for it. */
    data class Draft(val to: String, val text: String?) : OwnerMailCommand {
        override val code: String get() = "DRAFT:$to|" + (text ?: "?")
    }
    data class Reply(val text: String?) : OwnerMailCommand {
        override val code: String get() = "REPLY:" + (text ?: "?")
    }
    data object Send : OwnerMailCommand { override val code = "SEND" }
    data class Confirm(val yes: Boolean) : OwnerMailCommand { override val code: String get() = if (yes) "CONFIRM:YES" else "CONFIRM:NO" }
    data object Stop : OwnerMailCommand { override val code = "STOP" }
}

/**
 * The mail lane's parser. Deterministic, whole-utterance and fail closed: a question about mail in general, a negation,
 * a quotation, reported speech, a condition, WhatsApp, or „schick das per Mail an …“ (the existing hand-over of a
 * result) are never taken. A short follow-up is taken only with the [MailContext] it refers to.
 */
internal object OwnerMailCommands {
    private const val MAIL = "(?:e-?mails?|mails?)"
    private const val ADDRESS = "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
    private val ordinals = mapOf(
        "erste" to 1, "ersten" to 1, "erster" to 1, "eins" to 1, "1" to 1, "zweite" to 2, "zweiten" to 2, "zwei" to 2, "2" to 2,
        "dritte" to 3, "dritten" to 3, "drei" to 3, "3" to 3, "vierte" to 4, "vierten" to 4, "vier" to 4, "4" to 4,
        "fünfte" to 5, "fünften" to 5, "fünf" to 5, "5" to 5, "sechste" to 6, "sechsten" to 6, "sechs" to 6, "6" to 6,
        "siebte" to 7, "sieben" to 7, "7" to 7, "achte" to 8, "acht" to 8, "8" to 8, "neunte" to 9, "neun" to 9, "9" to 9,
        "zehnte" to 10, "zehn" to 10, "10" to 10, "letzte" to -1, "letzten" to -1)
    private const val ORD = "(erste|ersten|erster|zweite|zweiten|dritte|dritten|vierte|vierten|fünfte|fünften|sechste|sechsten|" +
        "siebte|achte|neunte|zehnte|letzte|letzten|\\d{1,2})"
    private val notAName = setOf("mir", "mich", "dir", "dich", "uns", "euch", "ihm", "ihr", "ihnen", "sie", "es", "das", "dem",
        "den", "der", "die", "wem", "wer", "was", "allen", "alle", "jedem", "niemand", "niemandem", "gestern", "heute", "morgen")
    private val negation = Regex("(?iu)(?<![\\p{L}])(nicht|nie|niemals|kein|keine|keinen|keinem|keiner)(?![\\p{L}])")

    /**
     * The command for [utterance] in [context], or null when it is not this lane's. [assistantName]: the name the
     * owner addresses the app with („Jarvis, …“).
     */
    fun route(utterance: String, assistantName: String?, context: MailContext = MailContext.NONE): OwnerMailCommand? {
        if (utterance.length !in 2..2_400 || utterance.any { it.isISOControl() && it != '\n' }) return null
        val stripped = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).trim()
        if (context.confirming) confirm(stripped)?.let { return it }
        // Quotation marks and reported speech: someone else's words, not an order (dictated text after a colon may quote).
        val head = stripped.substringBefore(':')
        if (Regex("[„“”\"«»]").containsMatchIn(head)) return null
        if (Regex("(?iu)\\bwhats\\s?app\\b").containsMatchIn(stripped)) return null
        val text = normal(stripped)
        if (text.isEmpty()) return null
        if (context.any && STOP.matches(text)) return OwnerMailCommand.Stop
        val command = draft(stripped) ?: reply(stripped, context) ?: parse(text, stripped, context) ?: return null
        return command
    }

    /** Only what is taken without any open mail context – for audits of the other lanes' utterances. */
    fun standalone(utterance: String, assistantName: String?): OwnerMailCommand? = route(utterance, assistantName, MailContext.NONE)

    private val STOP = Regex("^(?:stopp|stop|abbrechen|abbruch|halt|hör auf|hoer auf)$")

    /** The owner's answer to the send confirmation. */
    private fun confirm(text: String): OwnerMailCommand.Confirm? {
        val plain = text.lowercase(Locale.GERMAN).replace(Regex("[,.!]"), " ").replace(Regex("\\s+"), " ").trim()
        if (plain in setOf("nein", "nein danke", "nein doch nicht", "doch nicht", "nicht senden", "nicht abschicken", "abbrechen",
                "stopp", "stop", "lass es", "lieber nicht", "nö", "nee")) return OwnerMailCommand.Confirm(false)
        if (negation.containsMatchIn(plain)) return null
        if (OwnerSendIntentParser.isYes(text) || plain in setOf("ja senden", "ja abschicken", "ja bitte senden", "ja schick sie ab",
                "ja send sie", "senden", "abschicken", "ja", "jawohl")) return OwnerMailCommand.Confirm(true)
        return null
    }

    /** Lower case, without „bitte“/„mal“/„doch“, the polite frame, closing punctuation and runs of blanks. */
    private fun normal(text: String): String = text.lowercase(Locale.GERMAN)
        .replace("gibt's", "gibt es").replace("gibts", "gibt es").replace("’", "'")
        .replace(Regex("[!?.]+$"), "").replace(Regex("(?<![\\p{L}])(bitte|mal|doch|kurz|jetzt gleich)(?![\\p{L}])"), " ")
        .replace(Regex("^(?:kannst|könntest|würdest|magst) du\\s+"), "")
        .replace(Regex("\\s+"), " ").trim().trimEnd(',').trim()

    private fun parse(text: String, original: String, context: MailContext): OwnerMailCommand? {
        if (negation.containsMatchIn(text)) return null
        if (text.startsWith("wenn ") || text.startsWith("falls ")) return null
        exact(text)?.let { return it }
        search(text, original)?.let { return it }
        contextual(text, original, context)?.let { return it }
        return null
    }

    private val connect = listOf(
        Regex("^verbinde(?:n)? (?:(?:mein|meine|meinen) )?(?:gmail(?:[- ]?konto)?|google-?konto für gmail|e-?mail-?konto von gmail)" +
            "(?: (?:erneut|neu|wieder|mit continuum))?$"),
        Regex("^(?:(?:mein|das) )?gmail(?:[- ]?konto)? (?:neu |erneut |wieder )?verbinden$"))
    private val disconnect = listOf(
        Regex("^trenne? (?:(?:mein|meine|meinen|die|das) )?(?:gmail(?:[- ]?(?:konto|verbindung))?|verbindung zu gmail)$"),
        Regex("^(?:(?:mein|die|das) )?gmail(?:[- ]?(?:konto|verbindung))? (?:trennen|abmelden)$"),
        Regex("^melde (?:mein )?gmail ab$"))
    private val status = listOf(
        Regex("^(?:ist|bin ich mit) gmail verbunden$"), Regex("^gmail-?status$"), Regex("^mit welchem konto ist gmail verbunden$"),
        Regex("^welches gmail-?konto ist verbunden$"), Regex("^zeig(?:e)? (?:mir )?(?:die |meine )?gmail-?verbindung$"))
    private val new = listOf(
        Regex("^zeig(?:e)? (?:mir )?(?:meine |die )?(?:neuen?|ungelesenen?) $MAIL$"),
        Regex("^was ist neu (?:in meinem|im) postfach$"),
        Regex("^(?:habe|hab) ich (?:neue|ungelesene) $MAIL(?: in gmail)?$"),
        Regex("^gibt es neue $MAIL$"), Regex("^neue $MAIL$"), Regex("^was gibt es neues in gmail$"),
        Regex("^sind neue $MAIL da$"))
    private val newAloud = Regex("^lies (?:mir )?(?:meine |die )?neuen $MAIL vor$")
    private val important = listOf(
        Regex("^(?:was sind|zeig(?:e)?(?: mir)?) (?:meine|die) (?:wichtigsten|wichtigen) $MAIL$"),
        Regex("^welche $MAIL sind wichtig$"))
    private val summaryWide = Regex("^fass (?:mir )?(?:(?:meine|die) (wichtigsten|wichtigen|neuen|ungelesenen) $MAIL|mein postfach) zusammen$")
    private val whyStandalone = Regex("^wie (?:bestimmst|entscheidest|erkennst) du,? welche $MAIL wichtig sind$")
    private val deadlinesStandalone = listOf(
        Regex("^steht in der $MAIL eine frist$"), Regex("^gibt es in der $MAIL (?:eine )?(?:frist|fristen|termine)$"))
    private val deadlinesInbox = Regex("^welche (?:fristen|termine) stehen in meinen $MAIL$")
    private val readMail = Regex("^lies (?:mir )?(?:die|diese) $MAIL vor$")
    private val summarizeMail = listOf(Regex("^fass (?:die|diese) $MAIL zusammen$"), Regex("^mach (?:mir )?eine zusammenfassung der $MAIL$"))
    private val send = Regex("^(?:schick|schicke|sende|send) (?:die $MAIL|den entwurf)(?: jetzt)?(?: ab)?$")

    private fun exact(text: String): OwnerMailCommand? = when {
        connect.any { it.matches(text) } -> OwnerMailCommand.Connect
        disconnect.any { it.matches(text) } -> OwnerMailCommand.Disconnect
        status.any { it.matches(text) } -> OwnerMailCommand.Status
        new.any { it.matches(text) } -> OwnerMailCommand.New()
        newAloud.matches(text) -> OwnerMailCommand.New(aloud = true)
        important.any { it.matches(text) } -> OwnerMailCommand.Important
        whyStandalone.matches(text) -> OwnerMailCommand.ImportantWhy
        deadlinesInbox.matches(text) -> OwnerMailCommand.Deadlines(inbox = true)
        deadlinesStandalone.any { it.matches(text) } -> OwnerMailCommand.Deadlines(inbox = false)
        readMail.matches(text) -> OwnerMailCommand.Read()
        summarizeMail.any { it.matches(text) } -> OwnerMailCommand.Summarize(OwnerMailCommand.SummaryTarget.CONTEXT)
        send.matches(text) -> OwnerMailCommand.Send
        else -> summaryWide.matchEntire(text)?.let { match ->
            OwnerMailCommand.Summarize(if (match.groupValues[1].startsWith("wichtig")) OwnerMailCommand.SummaryTarget.IMPORTANT
                else OwnerMailCommand.SummaryTarget.NEW)
        }
    }

    private const val FIND = "(?:such(?:e)?|finde?|zeig(?:e)?(?: mir)?)"
    private val searchFrom = Regex("^$FIND (?:(?:alle|die|meine) )?$MAIL von (.+?)(?: (?:zu|zum thema|über) (.+))?$")
    private val searchAbout = Regex("^$FIND (?:(?:alle|die) )?$MAIL (?:zu|zum thema|über|mit dem stichwort) (.+)$")
    private val searchIn = Regex("^$FIND in (?:gmail|meinen $MAIL|meinem postfach) nach (.+)$")
    private val latest = Regex("^(?:$FIND|lies(?: mir)?) die (?:letzte|neueste|jüngste) $MAIL von (.+?)( vor)?$")
    private val latestQuestion = Regex("^was war die (?:letzte|neueste) $MAIL von (.+)$")

    private fun search(text: String, original: String): OwnerMailCommand? {
        latest.matchEntire(text)?.let { match ->
            val aloud = match.groupValues[2].isNotEmpty()
            if (text.startsWith("lies") != aloud) return null
            return name(match.groupValues[1], original)?.let { OwnerMailCommand.Latest(it, aloud) }
        }
        latestQuestion.matchEntire(text)?.let { match -> return name(match.groupValues[1], original)?.let { OwnerMailCommand.Latest(it, false) } }
        searchFrom.matchEntire(text)?.let { match ->
            day(match.groupValues[1])?.let { day -> if (match.groupValues[2].isEmpty()) return OwnerMailCommand.Search(null, null, day) }
            val from = name(match.groupValues[1], original) ?: return null
            val about = match.groupValues[2].takeIf { it.isNotEmpty() }?.let { term(it, original) ?: return null }
            return OwnerMailCommand.Search(from, about, null)
        }
        (searchAbout.matchEntire(text) ?: searchIn.matchEntire(text))?.let { match ->
            return term(match.groupValues[1], original)?.let { OwnerMailCommand.Search(null, it, null) }
        }
        return null
    }

    private fun day(text: String): MailDay? = when (text.trim()) {
        "heute" -> MailDay.TODAY
        "gestern" -> MailDay.YESTERDAY
        else -> null
    }

    private val articles = Regex("^(?:der|die|das|dem|den|meiner|meinem|meinen|meine|einer|einem)\\s+")

    /** A sender as the owner said it, in his own spelling: an address or a name of at most four words. */
    private fun name(lower: String, original: String): String? {
        val said = recover(lower.replace(articles, "").trim(), original) ?: return null
        if (Regex("^$ADDRESS$").matches(said)) return said.lowercase(Locale.ROOT)
        val words = said.split(' ')
        if (words.size !in 1..4 || words.any { it.lowercase(Locale.GERMAN) in notAName }) return null
        if (!Regex("^[\\p{L}][\\p{L}\\p{N}.'’ -]{0,79}$").matches(said)) return null
        return said
    }

    /** A search word as said: at most six words, letters and digits. */
    private fun term(lower: String, original: String): String? {
        val said = recover(lower.replace(Regex("^(?:dem thema|thema|stichwort|dem stichwort)\\s+"), "").replace(articles, "").trim(), original)
            ?: return null
        val words = said.split(' ')
        if (words.size !in 1..6 || said.length > 80) return null
        if (!Regex("^[\\p{L}\\p{N}][\\p{L}\\p{N}.,'’ &/-]{0,79}$").matches(said)) return null
        return said
    }

    /** The owner's own spelling of [lower], found in [original]. */
    private fun recover(lower: String, original: String): String? {
        if (lower.isEmpty()) return null
        val at = original.lowercase(Locale.GERMAN).lastIndexOf(lower)
        return if (at >= 0) original.substring(at, at + lower.length) else lower
    }

    private val readPronoun = Regex("^lies (?:mir )?(?:sie|es) vor$")
    private val readThread = Regex("^lies (?:mir )?den (?:ganzen )?verlauf vor$")
    private val readIndex = Regex("^(?:lies (?:mir )?die $ORD $MAIL vor|öffne die $ORD $MAIL)$")
    private val readWhat = Regex("^was steht (?:in der $MAIL|da drin|darin)$")
    private val readAttachment = Regex("^lies (?:mir )?(?:den|die) anh(?:ang|änge) vor$")
    private val pickIndex = Regex("^(?:die |nummer |die nummer )?$ORD(?: $MAIL)?$")
    private val pickNumber = Regex("^(?:die )?nummer (eins|zwei|drei|vier|fünf|sechs|sieben|acht|neun|zehn)$")
    private val pickDay = Regex("^die von (heute|gestern)$")
    private val pickFrom = Regex("^die von (.+)$")
    private val summarizePronoun = listOf(Regex("^fass (?:sie|es) zusammen$"), Regex("^mach (?:mir )?eine zusammenfassung davon$"))
    private val summarizeIndex = Regex("^fass die $ORD $MAIL zusammen$")
    private val summarizeThread = Regex("^fass den (?:ganzen )?verlauf zusammen$")
    private val deadlines = listOf(
        Regex("^was steht da (?:wegen|zu|über) (?:den |einer |der )?(?:frist|fristen|termin|terminen)$"),
        Regex("^gibt es (?:darin|da) (?:eine |irgendwelche )?(?:frist|fristen|termine)$"),
        Regex("^stehen da (?:fristen|termine) drin$"))
    private val why = listOf(Regex("^warum sind (?:die|diese|sie) (?:so )?wichtig$"), Regex("^nach welchen kriterien$"),
        Regex("^warum (?:die|diese)$"))

    private fun contextual(text: String, original: String, context: MailContext): OwnerMailCommand? {
        val talking = context.mail || context.list
        if (context.list) {
            readIndex.matchEntire(text)?.let { match -> return ordinal(match.groupValues[1].ifEmpty { match.groupValues[2] })?.let { n -> OwnerMailCommand.Read(index = n) } }
            summarizeIndex.matchEntire(text)?.let { return ordinal(it.groupValues[1])?.let { n -> OwnerMailCommand.Summarize(OwnerMailCommand.SummaryTarget.CONTEXT, n) } }
            pickIndex.matchEntire(text)?.let { return ordinal(it.groupValues[1])?.let { n -> OwnerMailCommand.Pick(index = n) } }
            pickNumber.matchEntire(text)?.let { return ordinal(it.groupValues[1])?.let { n -> OwnerMailCommand.Pick(index = n) } }
            pickDay.matchEntire(text)?.let { return OwnerMailCommand.Pick(day = day(it.groupValues[1])) }
            pickFrom.matchEntire(text)?.let { match -> return name(match.groupValues[1], original)?.let { OwnerMailCommand.Pick(from = it) } }
            if (why.any { it.matches(text) }) return OwnerMailCommand.ImportantWhy
        }
        if (talking) {
            if (readPronoun.matches(text) || readWhat.matches(text)) return OwnerMailCommand.Read()
            if (readThread.matches(text)) return OwnerMailCommand.Read(thread = true)
            if (summarizePronoun.any { it.matches(text) }) return OwnerMailCommand.Summarize(OwnerMailCommand.SummaryTarget.CONTEXT)
            if (summarizeThread.matches(text)) return OwnerMailCommand.Summarize(OwnerMailCommand.SummaryTarget.THREAD)
            if (deadlines.any { it.matches(text) }) return OwnerMailCommand.Deadlines(inbox = false)
        }
        if (context.mail && readAttachment.matches(text)) return OwnerMailCommand.ReadAttachment
        return null
    }

    private fun ordinal(word: String): Int? = ordinals[word]

    // ── Drafts and replies: the owner's own words after a colon, or a „dass“ clause turned into direct speech ──

    private val draftHead = Regex("(?iu)^(?:schreib|schreibe|erstelle|entwirf|entwerfe|verfasse|mach)\\s+" +
        "(?:(?:einen|den|eine|ein)\\s+)?(?:mail-?\\s?entwurf|mailentwurf|entwurf|e-?mail|mail)\\s+an\\s+(.+?)$")
    private val draftDative = Regex("(?iu)^(?:schreib|schreibe)\\s+(.+?)\\s+(?:eine|ein)\\s+(?:e-?mail|mail)$")

    private fun draft(original: String): OwnerMailCommand? {
        val (head, text) = split(original) ?: return null
        val plainHead = head.replace(Regex("(?iu)(?<![\\p{L}])bitte(?![\\p{L}])"), " ").replace(Regex("\\s+"), " ").trim()
        if (negation.containsMatchIn(plainHead)) return null
        val match = draftHead.matchEntire(plainHead) ?: draftDative.matchEntire(plainHead) ?: return null
        val to = name(match.groupValues[1].lowercase(Locale.GERMAN), plainHead) ?: return null
        return OwnerMailCommand.Draft(to, text)
    }

    private val replyHead = Regex("(?iu)^antworte\\s+(?:ihm|ihr|ihnen|darauf|auf die mail)$")

    private fun reply(original: String, context: MailContext): OwnerMailCommand? {
        if (!context.mail) return null
        val (head, text) = split(original) ?: return null
        if (!replyHead.matches(head.replace(Regex("(?iu)(?<![\\p{L}])bitte(?![\\p{L}])"), " ").replace(Regex("\\s+"), " ").trim())) return null
        return OwnerMailCommand.Reply(text)
    }

    /** „Kopf: Text“ or „Kopf, dass …“ → (head, text); text null when nothing follows. */
    private fun split(original: String): Pair<String, String?>? {
        val trimmed = original.trim()
        val colon = trimmed.indexOf(':')
        if (colon > 0) {
            val text = trimmed.substring(colon + 1).trim().trim('„', '“', '"').trim()
            return trimmed.substring(0, colon).trim() to text.takeIf { it.isNotEmpty() && it.length <= GmailMime.MAX_BODY_CHARS }
        }
        Regex("(?iu)^(.+?),?\\s+dass\\s+(.+)$").matchEntire(trimmed)?.let { match ->
            val clause = match.groupValues[2].trim().trimEnd('.', '!')
            val direct = IndirectSpeech.toDirect(clause) ?: clause.replaceFirstChar { it.titlecase(Locale.GERMAN) }
            return match.groupValues[1].trim() to direct
        }
        return trimmed.trimEnd('.', '!') to null
    }
}
