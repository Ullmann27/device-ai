package at.heinz.deviceai.sending

import at.heinz.deviceai.WhatsAppDraftPolicy
import at.heinz.deviceai.cognitive.OwnerAppLookupIntent
import java.util.Locale

/** The two channels Continuum can send on in the owner's name. One standing owner permission per channel. */
internal enum class SendChannel(val packageName: String, val label: String, val settingLabel: String) {
    WHATSAPP("com.whatsapp", "WhatsApp", "WhatsApp-Nachrichten selbst senden"),
    GMAIL("com.google.android.gm", "Gmail", "E-Mails über Gmail selbst senden")
}

/**
 * Where the words of an outgoing text come from. Fixed rule (a): only [ownerWords] text is sent without a
 * question; everything else is shown once and needs a clear yes (which turns it into [OWNER_CONFIRMED]).
 */
internal enum class TextOrigin(val ownerWords: Boolean) {
    /** Verbatim from the owner's own turn (first letter upper-cased, nothing else). */
    DICTATED(true),
    /** „…, dass ich später komme“ → „Ich komme später“: the owner's own words, only the verb moved, see [IndirectSpeech]. */
    MINIMAL_TRANSFORM(true),
    /** Shown once, the owner said yes. */
    OWNER_CONFIRMED(true),
    /** Written by a model or taken from a generated result. */
    DERIVED(false)
}

/**
 * One WhatsApp order as the owner said it. Exactly one of: [text] (ready), [modelBrief] (he wants Continuum to
 * word it: „schreib ihr was Nettes“) or neither (the wording is asked for). [unsplit] = „an Anna ich komme später“
 * without a separator: the contacts decide where the name ends ([OwnerSendRecipientResolver.split]).
 */
internal data class OwnerSendOrder(
    val recipient: String?,
    val text: String?,
    val origin: TextOrigin,
    val draftOnly: Boolean = false,
    val unsplit: String? = null,
    val modelBrief: String? = null,
    val original: String,
    /**
     * [unsplit] came from a tail that no separator („:“, „,“, „dass“) divides from where a recipient could stand
     * („Schick auf WhatsApp meinem Chef ich komme später“). When neither a relation nor a contact name stands in
     * front, the whole tail is the text – and is confirmed once, because recipient words may be in it.
     */
    val looseText: Boolean = false
)

/**
 * Deterministic and fail closed: an imperative to write/send/say something, the word „WhatsApp“ in the order part,
 * one recipient. Questions („Was hat Anna auf WhatsApp geschrieben?“), reading orders („Nachricht von Anna
 * vorlesen“), the news feature and „Öffne WhatsApp“ never match. Parsing grants nothing.
 */
internal object OwnerSendIntentParser {
    sealed interface Result {
        data class Order(val order: OwnerSendOrder) : Result
        /** Understood as a send order, but one that is never carried out (negated, several recipients, a group). */
        data class Refused(val message: String) : Result
    }

    const val REFUSED_NEGATED = "Der verneinte Sende-Auftrag wird nicht ausgeführt. Es wurde nichts vorbereitet und nichts gesendet."
    const val REFUSED_SEVERAL = "Ich sende pro Auftrag genau eine Nachricht an genau einen Empfänger. " +
        "Gib mir die Aufträge bitte einzeln. Es wurde nichts gesendet."
    const val REFUSED_GROUP = "An Gruppen und Broadcast-Listen sende ich nicht selbst. " +
        "Öffne die Gruppe bitte selbst in WhatsApp. Es wurde nichts gesendet."
    const val REFUSED_TEXT = "Der Text ist zu lang oder enthält unzulässige Steuerzeichen. Es wurde nichts gesendet."

    private val polite = Regex("(?iu)^(?:(?:kannst|könntest|koenntest|magst|würdest|wuerdest|tätest|taetest|tust)\\s+du\\s+)?" +
        "(?:(?:bitte|mal|kurz|gschwind|schnell|geh)\\s+)*")
    private val verb = Regex("(?iu)^(schreib(?:e|st)?|schick(?:e|st)?|send(?:e|est)?|sag(?:e|st)?|gib|geb|richte?|" +
        "bereite|verfasse|entwirf|entwerfe)(?![\\p{L}\\p{N}_])\\s*(?:(?:bitte|mal|doch|kurz|gschwind|schnell|einmal|amal)\\s+)*")
    private val modal = Regex("(?iu)^(?:kannst|könntest|koenntest|magst|würdest|wuerdest|tätest|taetest|tust)\\s+du\\s+")
    private val verbFinal = Regex("(?iu)^([^:,\\n]*?whats\\s?app[^:,\\n]*?)\\s+(?:schreiben|schicken|senden|sagen|ausrichten)" +
        "(?![\\p{L}\\p{N}_])(.*)$", RegexOption.DOT_MATCHES_ALL)
    private val particles = "(?:bitte|mal|kurz|gschwind|schnell|doch|einmal|amal|gleich|noch|eben)"
    private val draftVerbs = setOf("bereite", "verfasse", "entwirf", "entwerfe")
    /** „auf WhatsApp“, „per WhatsApp“, „eine WhatsApp“, „a WhatsApp-Nachricht“, „eine Nachricht auf WhatsApp“ … */
    private val alias = Regex("(?iu)(?<![\\p{L}\\p{N}_])(?:(?:eine?|a|ane)\\s+(?:kurze\\s+)?nachricht\\s+)?" +
        "(?:(?:in|auf|per|via|über|ueber|bei|mit|im|übers|uebers)\\s+)?(?:(?:eine?|a|ane|die|der|dem)\\s+)?(?:kurze\\s+)?" +
        "whats\\s?app(?:[- ]?nachricht)?(?![\\p{L}\\p{N}_])")
    private val negation = Regex("(?iu)(?<![\\p{L}\\p{N}_])(?:nicht|nie|niemals|keinesfalls|kein(?:e|en|em|er|es)?)(?![\\p{L}\\p{N}_])")
    private val several = Regex("(?iu)(?:,|(?<![\\p{L}\\p{N}_])(?:und|sowie|oder|plus)(?![\\p{L}\\p{N}_]))")
    private val group = Regex("(?iu)(?:gruppe|broadcast|verteiler|(?<![\\p{L}\\p{N}_])(?:alle|allen|jeden|jedem)(?![\\p{L}\\p{N}_]))")
    private val article = Regex("(?iu)^(?:an\\s+)?(?:(?:den|dem|der|die|da|dem)\\s+)?")
    private val familyRecipient = Regex("(?iu)^\\s*((?:mein(?:en|em|e|er)?|unser(?:en|em|e|er)?|dem|der|den|die|da)\\s+" +
        "(?:papa|vater|mama|mutter|oma|opa|tante|onkel|bruder|schwester))(?=\\s|[:,]|$)")
    private val additionalRecipient = Regex("(?iu)^\\s*(?:und|sowie|oder|plus)\\b")
    private val channelAnswer = Regex("(?iu)^(?:ja[, ]+)?(?:(?:auf|in|per|über|ueber)\\s+)?whats\\s?app[.!]?$")
    private val recipientAnswerLead = Regex("(?iu)^(?:ich\\s+habe\\s+dir\\s+(?:.{1,60}\\s+)?gesagt[, ]+)?" +
        "(?:der\\s+(?:kontakt\\s*)?name\\s+(?:heißt|heisst|ist)|(?:der\\s+)?kontakt\\s+(?:heißt|heisst|ist)|" +
        "ich\\s+(?:meine|meinte)|gemeint\\s+(?:ist|war))\\s*:?\\s+")
    private val headNoise = Regex("(?iu)^(?:(?:nur\\s+)?(?:als\\s+|einen\\s+|an\\s+)?entwurf\\s+(?:für|fuer|an)\\s+|" +
        "(?:eine?|a|ane)\\s+(?:kurze\\s+)?(?:nachricht|message)\\s+)")
    private val nameLike = Regex("^[\\p{L}][\\p{L}\\p{N}.'’ -]{0,59}$")
    /** Words that are never part of a recipient: a head containing one is not a send order to a person. */
    private val notAName = setOf("mir", "mich", "dir", "dich", "uns", "euch", "was", "wer", "wie", "wo", "wann", "ob",
        "dass", "auf", "zu", "von", "vom", "für", "fuer", "mit", "bei", "ist", "hat", "sich", "es", "ein", "eine",
        "einen", "nachricht", "nachrichten", "text", "etwas", "zusammenfassung", "datei", "pdf", "bild", "foto",
        "screenshot", "standort", "link", "mal", "bitte", "in", "im", "an", "aus", "vor", "noch", "jetzt", "heute")
    private val pronouns = setOf("ihr", "ihm", "ihnen", "sie", "er", "denen", "dem", "der")
    private val modelWish = Regex("(?iu)^(?:(?:irgend)?(?:et)?was|irgendetwas)\\s+(?:\\p{L}+es)$|" +
        "^(?:eine?n?|ein\\s+paar)\\s+(?:\\p{L}+e[nrs]?\\s+)?(?:nachricht|gruß|gruss|grüße|gruesse|glückwunsch|glueckwunsch|" +
        "geburtstagsgruß|geburtstagsgruss|geburtstagswünsche|zeilen|antwort)$")
    private val cancel = Regex("(?iu)^(?:abbrechen|abbruch|vergiss es|nichts|doch nicht|lass es|lass|stopp|stop|nein|nö|" +
        "nein danke|nicht senden|nicht schicken)[.!]?$")
    private val yes = Regex("(?iu)^(?:ja|jo|jawohl|ja bitte|ja genau|genau so|ja passt|passt|passt so|ja gerne|ok|okay|" +
        "(?:ja[, ]+)?(?:bitte )?(?:so )?(?:senden|absenden|abschicken|schicken)|" +
        "(?:ja[, ]+)?(?:schick|send)e?(?: es| sie| das)?(?: so)?(?: ab| raus| weg)?(?: bitte)?)[.!]?$")
    private val forContinuum = Regex("(?iu)^(?:wer|was|wie|wo|wann|warum|wieso|weshalb|welche\\p{L}*|kannst|könntest|" +
        "hast|bist|gibt|öffne|oeffne|starte|such|suche|zeig|zeige|lies|lese|spiel|spiele|mach|schalte|erinnere|" +
        "speichere|speicher|fass|erzähl|erzaehl|ruf|schreib|schreibe|schick|schicke|sende|sag|sage|lösche|loesche)" +
        "(?![\\p{L}\\p{N}_])")

    /** „Nachricht von Anna vorlesen“, „WhatsApp öffnen“: an order to Continuum in the infinitive. */
    private val infinitiveOrder = Regex("(?iu)(?<![\\p{L}\\p{N}_])(?:vorlesen|öffnen|oeffnen|starten|anzeigen|zeigen|suchen|" +
        "abspielen|stoppen|beenden|speichern|löschen|loeschen|zusammenfassen)[.!]?$")

    /**
     * [channelGiven]: the order stands behind „Öffne WhatsApp und …“ in the owner's own sentence, so the channel is
     * said although this clause does not repeat it („schreib meinem Vater guten Morgen“). Only the mission planner
     * passes true, and only for exactly that sentence shape; everything else of the order is read as always.
     */
    fun parse(utterance: String, assistantName: String? = null, channelGiven: Boolean = false): Result? {
        if (utterance.length !in 6..2_400 || utterance.any { it.isISOControl() && it != '\n' }) return null
        val addressed = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName)
        var text = polite.replace(addressed, "")
        // „Kannst du Anna auf WhatsApp schreiben, dass …“: the same order with the verb at the end of its clause.
        if (modal.containsMatchIn(addressed) && verb.find(text) == null) verbFinal.matchEntire(text)?.let { said ->
            text = "schreib " + said.groupValues[1] + said.groupValues[2]
        }
        val command = verb.find(text) ?: return null
        val verbWord = command.groupValues[1].lowercase(Locale.GERMAN)
        val rest = text.substring(command.range.last + 1)
        // „WhatsApp“ must belong to the order, not to the dictated text behind a colon or comma.
        val firstSeparator = rest.indexOfFirst { it == ':' || it == ',' || it == '\n' }
        val named = alias.find(rest)
        if (named != null && firstSeparator in 0 until named.range.first && !channelGiven) return null
        // With the channel given by the clause in front, a „WhatsApp“ behind a separator is part of the dictated text.
        val found = named?.takeUnless { firstSeparator in 0 until it.range.first }
        if (found == null && !channelGiven) return null
        var head = if (found == null) "" else rest.substring(0, found.range.first).trim()
        var tail = if (found == null) " $rest" else rest.substring(found.range.last + 1)
        var draftOnly = verbWord in draftVerbs
        if (Regex("(?iu)(?<![\\p{L}\\p{N}_])entwurf(?![\\p{L}\\p{N}_])").containsMatchIn(head)) draftOnly = true
        head = headNoise.replace(head, "").trim()
        // „… vor: Text“ (bereite … vor), „… Bescheid, dass …“, „… aus, dass …“ carry no text of their own.
        tail = Regex("(?iu)^\\s*(?:vor|bescheid|aus)(?![\\p{L}\\p{N}_])").replace(tail, "")
        tail = Regex("(?iu)^\\s*(?:eine?\\s+)?(?:kurze\\s+)?nachricht(?![\\p{L}\\p{N}_])").replace(tail, "")
        val unseparated = tail.trimStart().firstOrNull()?.let { it != ':' && it != ',' } ?: false
        if (negation.containsMatchIn(head) ||
            (unseparated && negation.find(tail.trimStart())?.range?.first == 0)) return Result.Refused(REFUSED_NEGATED)

        var recipient: String? = null
        var unsplit: String? = null
        var message: String
        if (head.isNotEmpty()) {
            // „Schreib ihr auf WhatsApp …“: a pronoun names nobody; the recipient is asked for, never guessed.
            recipient = if (article.replace(head, "").trim().lowercase(Locale.GERMAN) in pronouns) null
                else cleanRecipient(head) ?: return recipientProblem(head)
            message = tail
        } else {
            val toWhom = Regex("(?iu)^\\s*(?:an|für|fuer)\\s+(?!\\s)").find(tail)
            if (toWhom == null) {
                val family = splitFamily(tail)
                val loose = tail.trim()
                if (family == null && loose.isNotEmpty() && loose.first() !in ":,;" &&
                    !Regex("(?iu)^(?:dass|daß|ob)(?![\\p{L}\\p{N}_])").containsMatchIn(loose)) {
                    // No separator between a possible recipient and the text: the contacts and relations decide.
                    if (group.containsMatchIn(loose.substringBefore(' '))) return Result.Refused(REFUSED_GROUP)
                    if (WhatsAppDraftPolicy.validated(loose) == null) return Result.Refused(REFUSED_TEXT)
                    return Result.Order(OwnerSendOrder(null, null, TextOrigin.DICTATED, draftOnly, loose, null,
                        utterance.trim(), looseText = true))
                }
                if (family == null) message = tail
                else {
                    val remainder = family.second.trimStart()
                    if (startsWithNegation(remainder)) return Result.Refused(REFUSED_NEGATED)
                    if (hasAdditionalRecipient(remainder)) return Result.Refused(REFUSED_SEVERAL)
                    if (remainder.isEmpty() || remainder.startsWith(':') || remainder.startsWith(',')) {
                        recipient = cleanRecipient(family.first)
                        message = remainder
                    } else {
                        unsplit = tail.trim()
                        message = ""
                    }
                }
            } else {
                val named = tail.substring(toWhom.range.last + 1)
                val cut = Regex("(?iu)[:,\\n]|\\s+dass\\s+|\\s+daß\\s+").find(named)
                if (cut != null) {
                    val whom = named.substring(0, cut.range.first)
                    recipient = cleanRecipient(whom) ?: return recipientProblem(whom)
                    message = named.substring(if (cut.value.trim().startsWith("da", true)) cut.range.first else cut.range.last + 1)
                } else {
                    // No separator between name and text: the contacts decide where the name ends.
                    val open = article.replace(named.trim(), "").trim()
                    if (open.isEmpty()) return null
                    if (group.containsMatchIn(open.substringBefore(' '))) return Result.Refused(REFUSED_GROUP)
                    unsplit = open; message = ""
                }
            }
        }
        if (unsplit != null) {
            if (WhatsAppDraftPolicy.validated(unsplit) == null) return Result.Refused(REFUSED_TEXT)
            return Result.Order(OwnerSendOrder(null, null, TextOrigin.DICTATED, draftOnly, unsplit, null, utterance.trim()))
        }
        val content = body(message) ?: return Result.Refused(REFUSED_TEXT)
        return Result.Order(OwnerSendOrder(recipient, content.text, content.origin, draftOnly, null,
            content.modelBrief, utterance.trim()))
    }

    /** What stands behind the recipient: dictated text, a „dass“ clause, a wish for own wording, or nothing yet. */
    data class Body(val text: String?, val origin: TextOrigin, val modelBrief: String? = null)

    /** Null = the text is not sendable (too long, control characters). */
    fun body(raw: String): Body? {
        val body = raw.trim().trimStart(':', ',', ';').trim()
        if (body.isEmpty()) return Body(null, TextOrigin.DICTATED)
        Regex("(?iu)^(?:dass|daß)\\s+(.+)$", RegexOption.DOT_MATCHES_ALL).matchEntire(body)?.let { clause ->
            // Indirect speech: only the documented verb move counts as the owner's wording; otherwise he is asked.
            return Body(IndirectSpeech.toDirect(clause.groupValues[1]), TextOrigin.MINIMAL_TRANSFORM)
        }
        if (Regex("(?iu)^ob\\s+.+", RegexOption.DOT_MATCHES_ALL).matches(body)) return Body(null, TextOrigin.DICTATED)
        val plain = body.trim('"', '„', '“', '”').trim()
        if (modelWish.matches(plain.trimEnd('.', '!'))) return Body(null, TextOrigin.DERIVED, plain.trimEnd('.', '!'))
        return dictated(plain)?.let { Body(it, TextOrigin.DICTATED) }
    }

    /** The owner's own words as message text: validated, first letter upper-cased, nothing else changed. */
    fun dictated(raw: String): String? = WhatsAppDraftPolicy.validated(raw.trim())?.let(::capitalised)

    fun capitalised(text: String): String =
        if (text.firstOrNull()?.isLowerCase() == true) text.substring(0, 1).uppercase(Locale.GERMAN) + text.substring(1) else text

    /** A recipient phrase the owner gave as an answer („Anna Beispiel“, „der Thomas“); null when it is no name. */
    fun recipientAnswer(raw: String): String? {
        val name = raw.trim().trimEnd('.', '!').replace(recipientAnswerLead, "")
        return if (addressesContinuum(name) || isYes(name) || isChannelAnswer(name)) null else cleanRecipient(name)
    }

    private val notThisButThat = Regex("(?iu)^(?:nein[,.!]?\\s+)?(?:ich\\s+meinte\\s+)?nicht\\s+(?:an\\s+)?(.{1,60}?)\\s*[,;]\\s*" +
        "(?:sondern\\s+)?(?:an\\s+)?(.{1,60}?)[.!]?$")
    private val thatNotThis = Regex("(?iu)^(?:nein[,.!]?\\s+)?(?:an\\s+)?(.{1,60}?)\\s*[,;]\\s*nicht\\s+(?:an\\s+)?(.{1,60}?)[.!]?$")

    /**
     * „Nicht Papa, Mama“, „Nein, nicht an Anna, sondern an Thomas“, „Mama, nicht Papa“: the recipient the owner
     * names instead, or null. Only for an open question of the send lane; the message itself stays as it was.
     */
    fun correctedRecipient(raw: String): String? {
        val text = raw.trim()
        val (wanted, rejected) = notThisButThat.matchEntire(text)?.let { it.groupValues[2] to it.groupValues[1] }
            ?: thatNotThis.matchEntire(text)?.let { it.groupValues[1] to it.groupValues[2] } ?: return null
        // Both sides must read like recipients: „Später, nicht jetzt“ is message text, not a correction.
        if (startsWithNegation(wanted) || recipientAnswer(rejected) == null) return null
        return recipientAnswer(wanted)
    }

    fun isCancel(raw: String): Boolean = cancel.matches(raw.trim())
    fun isYes(raw: String): Boolean = yes.matches(raw.trim())
    fun isChannelAnswer(raw: String): Boolean = channelAnswer.matches(raw.trim())
    fun hasAdditionalRecipient(raw: String): Boolean = additionalRecipient.containsMatchIn(raw)
    fun startsWithNegation(raw: String): Boolean = negation.find(raw.trimStart())?.range?.first == 0
    fun splitFamily(raw: String): Pair<String, String>? = familyRecipient.find(raw)?.let {
        it.groupValues[1] to raw.substring(it.range.last + 1)
    }
    /** A follow-up that reads like a question or order to Continuum itself is never taken silently as message text. */
    fun addressesContinuum(raw: String): Boolean = raw.trim().let {
        it.endsWith("?") || forContinuum.containsMatchIn(it) || infinitiveOrder.containsMatchIn(it)
    }

    private fun recipientProblem(raw: String): Result? = when {
        group.containsMatchIn(raw) -> Result.Refused(REFUSED_GROUP)
        several.containsMatchIn(raw) && raw.split(several).all { cleanRecipient(it) != null } -> Result.Refused(REFUSED_SEVERAL)
        else -> null
    }

    private fun cleanRecipient(raw: String): String? {
        val name = article.replace(raw.trim().trim('"', '„', '“', '”'), "")
            .replace(Regex("(?iu)^(?:$particles\\s+)+|(?:\\s+$particles)+$"), "").trim()
        if (!nameLike.matches(name) || group.containsMatchIn(name)) return null
        val words = name.lowercase(Locale.GERMAN).split(Regex("\\s+"))
        if (words.size > 4 || words.any { it in notAName } || several.containsMatchIn(name)) return null
        return name
    }
}

/**
 * „Sag dem Thomas per WhatsApp, dass ich später komme“ → „Ich komme später“.
 *
 * Decision (documented and tested): this one transformation counts as the owner's own wording, because it only moves
 * the finite verb from the end of the „dass“ clause to the second position and upper-cases the first letter – every
 * word is his. It applies only to plain first-person clauses („ich“, „i“, „wir“) without a further clause, without a
 * third-person pronoun (which would need a change of perspective) and without a possibly separable verb at the end
 * („dass ich dich anrufe“). Everything else returns null and the owner is asked for the literal wording – Continuum
 * never paraphrases silently and needs no model here.
 */
internal object IndirectSpeech {
    private val subjects = setOf("ich", "i", "wir")
    private val blockers = setOf("weil", "wenn", "falls", "dass", "daß", "ob", "damit", "bis", "sobald", "obwohl",
        "während", "waehrend", "bevor", "nachdem", "und", "oder", "aber", "denn", "sondern", "er", "sie", "ihm", "ihn",
        "ihr", "ihnen", "ihre", "ihren", "ihrem", "ihrer", "ihres", "sein", "seine", "seinen", "seinem", "seiner",
        "seines", "man")
    private val separable = listOf("zusammen", "zurück", "zurueck", "vorbei", "wieder", "weiter", "durch", "unter",
        "über", "ueber", "nach", "fest", "heim", "auf", "aus", "bei", "ein", "her", "hin", "los", "mit", "vor", "weg",
        "ab", "an", "um", "zu")

    fun toDirect(clause: String): String? {
        val plain = clause.trim().trimEnd('.', '!').trim()
        if (plain.any { it in ",;:?\n" }) return null
        val words = plain.split(Regex("\\s+"))
        if (words.size !in 2..14 || words.first().lowercase(Locale.GERMAN) !in subjects) return null
        if (words.drop(1).any { it.lowercase(Locale.GERMAN) in blockers }) return null
        val verb = words.last()
        val lower = verb.lowercase(Locale.GERMAN)
        if (!verb.all { it.isLetter() } || separable.any { lower.startsWith(it) && lower.length - it.length >= 3 }) return null
        val direct = (listOf(words.first(), verb) + words.subList(1, words.size - 1)).joinToString(" ")
        return OwnerSendIntentParser.dictated(direct)
    }
}
