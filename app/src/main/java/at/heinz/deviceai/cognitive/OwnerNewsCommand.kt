package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Owner wish (18.11.2026): „Fass mir die Nachrichten von heute zusammen“, „Was gibt's Neues in der Welt?“,
 * „Schlagzeilen“, „Nachrichten zu Wirtschaft“ start the news briefing straight from chat or speech. The field
 * failure was a meta answer of the local model („müsstest du eine Funktion entwickeln …“) without any web tool.
 *
 * This object only recognises the command and the requested topics. It grants nothing: the feeds are read through
 * the same web grant as research ([ApprovedWebToolGate]). The topic words are a LOCAL filter over the fixed feed
 * list of [NewsFeedCatalog]; they are never sent to a server.
 *
 * Fail closed: every word of the utterance must be known vocabulary of a news request. One unknown word, a
 * negation, a possessive („meine Nachrichten“), the singular „Nachricht“, a messenger/mail word or a messaging
 * verb means: not a news command – the message, notification and app lanes keep their turn.
 */
internal object OwnerNewsCommand {
    /** [section]: a known section (adds its feed). [words]: the owner's own words as local keyword filter. */
    data class TopicFilter(val label: String, val section: NewsTopic?, val words: String?)

    data class Request(val topics: List<TopicFilter>, val original: String) {
        val unspecific: Boolean get() = topics.isEmpty()
        val topicLabel: String? get() = topics.takeIf { it.isNotEmpty() }?.joinToString(" und ") { it.label }
        val sections: List<NewsTopic> get() = topics.mapNotNull { it.section }.distinct()
    }

    sealed interface FollowUp {
        data class Topics(val request: Request) : FollowUp
        /** „Mehr zur zweiten Meldung“ – [number] is 1-based; -1 means the last one. */
        data class Story(val number: Int) : FollowUp
        data object AskWhich : FollowUp
        data object Decline : FollowUp
    }

    /** Fixed public label of the web grant and the consent dialog; it never contains owner wording. */
    const val GRANT_LABEL = "Nachrichten von heute (öffentliche Nachrichten-Feeds)"

    fun offlineReply(): String = "Internetzugang ist AUS, deshalb habe ich keine Nachrichten abgerufen. Stell den " +
        "Internetzugang unter „Datenschutz & Freigaben“ auf „Nachfragen“ oder „Immer“, dann hole ich sie dir."

    private const val NEWS = "§news"
    private val newsNoun = Regex(
        "^(?:([\\p{L}]{2,24}?)-?)?(nachrichten|news|schlagzeilen|meldungen|weltgeschehen|tagesgeschehen|" +
            "nachrichten-?(?:überblick|ueberblick|übersicht|uebersicht|update|lage|zusammenfassung|briefing))$")
    /** Compound heads that only say „general news“ („Weltnachrichten“, „Top-News“). */
    private val generalPrefixes = setOf("welt", "tages", "top", "haupt", "spitzen", "abend", "morgen", "früh", "mittags")
    private val plainNouns = setOf("nachrichten", "news", "meldungen")

    private val fillers = setOf("mir", "mal", "bitte", "kurz", "doch", "jetzt", "noch", "schnell", "eben", "einmal",
        "uns", "ja", "dann", "also", "gern", "gerne", "einfach", "nochmal", "auch", "so", "denn", "eigentlich", "ok",
        "okay", "gut", "hey", "hallo", "servus", "und", "aber", "danke", "zuerst", "vorerst", "erstmal", "gerade",
        "grad", "halt", "eh", "na", "du", "sonst", "alles", "nur", "vor", "allem", "heut")
    private val frames = setOf("kannst", "könntest", "koenntest", "würdest", "wuerdest", "magst", "ich", "will",
        "möchte", "moechte", "hätte", "haette", "würde", "wuerde", "brauche", "brauch", "was", "welche", "wie", "sind",
        "ist", "gibt", "gibts", "es", "lauten", "wären", "waeren", "an", "für", "fuer", "mich", "steht", "stehen", "kommt",
        "läuft", "laeuft", "drin")
    private val articles = setOf("die", "der", "den", "das", "des", "dem", "alle", "allen", "ein", "eine", "einen",
        "einer", "paar", "einige")
    private val leadVerbs = setOf("fass", "fasse", "gib", "sag", "sage", "erzähl", "erzähle", "erzaehl", "erzaehle",
        "zeig", "zeige", "bring", "bringe", "nenn", "nenne", "erklär", "erkläre", "erklaer", "erklaere", "mach",
        "mache", "hol", "hole", "berichte", "informier", "informiere")
    private val tails = setOf("zusammen", "zusammenfassen", "geben", "sagen", "erzählen", "erzaehlen", "zeigen",
        "bringen", "nennen", "erklären", "erklaeren", "hören", "hoeren", "sehen", "haben", "wissen", "machen",
        "berichten", "informieren", "durchgehen", "durch")
    /** Reading aloud is also what the notification announcement does: needs a clear news marker. */
    private val weakVerbs = setOf("lies", "lese", "lesen", "vorlesen")
    private val adjectives = setOf("wichtigsten", "wichtigste", "wichtigen", "aktuellen", "aktuelle", "aktuell",
        "aktuellsten", "aktuellste", "neuesten", "neueste", "heutigen", "heutige", "globalen", "globale", "weltweiten",
        "weltweite", "internationalen", "internationale", "großen", "grossen", "größten", "groessten", "top",
        "wesentlichen", "relevanten", "letzten", "täglichen", "taeglichen", "breaking")
    private val neutralAdjectives = setOf("kurzen", "kurze", "kompakt", "kompakte", "kompakten", "ganzen", "gesamten",
        "sämtliche", "sämtlichen", "knapp", "knappe", "knappen", "schnelle", "schnellen")
    /** „Neue Nachrichten“ usually means messages: only a news request together with a clear news marker. */
    private val ambiguousAdjectives = setOf("neue", "neuen", "neues")
    private val summaryNouns = setOf("zusammenfassung", "überblick", "ueberblick", "übersicht", "uebersicht",
        "kurzfassung", "briefing", "update", "kurzmeldung", "kurzmeldungen", "kurzmitteilung", "lage", "themen")
    private val topicIntros = setOf("zu", "zum", "zur", "über", "ueber", "bezüglich", "bezueglich", "betreffend",
        "aus", "in", "im", "speziell")
    private val conjunctions = setOf("und", "sowie", "oder")

    private enum class Kind { DAY, WORLD, NEUTRAL, TOPIC }
    private val phrases: List<Pair<List<String>, Kind>> = listOf(
        listOf("vom", "heutigen", "tag") to Kind.DAY, listOf("von", "heute") to Kind.DAY,
        listOf("des", "tages") to Kind.DAY, listOf("vom", "tag") to Kind.DAY, listOf("für", "heute") to Kind.DAY,
        listOf("heute") to Kind.DAY, listOf("heutzutage") to Kind.DAY,
        listOf("aus", "der", "ganzen", "welt") to Kind.WORLD, listOf("rund", "um", "die", "welt") to Kind.WORLD,
        listOf("in", "der", "ganzen", "welt") to Kind.WORLD, listOf("aus", "aller", "welt") to Kind.WORLD,
        listOf("aus", "der", "welt") to Kind.WORLD, listOf("in", "der", "welt") to Kind.WORLD,
        listOf("auf", "der", "welt") to Kind.WORLD, listOf("um", "die", "welt") to Kind.WORLD,
        listOf("der", "welt") to Kind.WORLD, listOf("weltweit") to Kind.WORLD, listOf("global") to Kind.WORLD,
        listOf("international") to Kind.WORLD,
        listOf("aus", "dem", "internet") to Kind.NEUTRAL, listOf("aus", "dem", "netz") to Kind.NEUTRAL,
        listOf("im", "internet") to Kind.NEUTRAL, listOf("im", "netz") to Kind.NEUTRAL,
        listOf("im", "web") to Kind.NEUTRAL, listOf("online") to Kind.NEUTRAL, listOf("für", "mich") to Kind.NEUTRAL,
        listOf("fuer", "mich") to Kind.NEUTRAL, listOf("auf", "den", "punkt") to Kind.NEUTRAL,
        listOf("zu", "den", "themen") to Kind.TOPIC, listOf("zu", "dem", "thema") to Kind.TOPIC,
        listOf("zum", "themenbereich") to Kind.TOPIC, listOf("zum", "thema") to Kind.TOPIC,
        listOf("aus", "dem", "bereich") to Kind.TOPIC, listOf("aus", "den", "bereichen") to Kind.TOPIC,
        listOf("im", "bereich") to Kind.TOPIC, listOf("in", "sachen") to Kind.TOPIC,
        listOf("rund", "um") to Kind.TOPIC, listOf("was", "ist", "mit") to Kind.TOPIC,
        listOf("das", "mit") to Kind.TOPIC
    )

    /** Never part of a news request – and never accepted inside a topic. */
    private val foreign = setOf(
        "nicht", "kein", "keine", "keinen", "keiner", "keinerlei", "niemals", "nie", "ohne", "offline",
        "nachricht", "nachrichtenentwurf", "kurznachricht", "textnachricht", "textnachrichten", "sprachnachricht",
        "sprachnachrichten", "mitteilung", "mitteilungen", "benachrichtigung", "benachrichtigungen", "anruf",
        "anrufe", "whatsapp", "sms", "signal", "telegram", "messenger", "mail", "mails", "email", "emails", "e-mail",
        "e-mails", "gmail", "outlook", "posteingang", "chat", "chats", "app", "apps",
        "schreib", "schreibe", "schreiben", "schick", "schicke", "schicken", "send", "sende", "senden", "versende",
        "antworte", "antworten", "beantworte", "weiterleiten", "leite", "lösch", "lösche", "löschen", "loesche",
        "öffne", "oeffne", "öffnen", "starte", "installiere", "kaufe", "bezahle", "speicher", "speichere",
        "speichern", "abspeichern", "exportiere", "pdf", "word", "docx", "datei", "dokument", "download",
        "downloads", "download-ordner", "ordner",
        "mein", "meine", "meinen", "meiner", "meinem", "meines", "dein", "deine", "deinen", "deiner", "unsere",
        "unseren", "unserer", "ungelesene", "ungelesenen", "eingegangene", "eingegangenen", "gestern", "gestrigen",
        "morgen", "passwort", "konto", "iban")
    private val topicStop = fillers + tails + weakVerbs + leadVerbs + frames + summaryNouns
    /** Pronouns and question words are never a news topic („Was ist mit dir los?“). */
    private val nonTopics = setOf("dir", "dich", "mir", "mich", "uns", "euch", "ihm", "ihr", "ihnen", "sich", "sie",
        "er", "es", "das", "dem", "den", "was", "wie", "wo", "wer", "wann", "warum", "etwas", "nichts", "alles", "hier",
        "da", "dort", "jetzt", "los", "so", "mal", "neues", "neu")
    private val greeting = Regex("^(?:(?:guten (?:morgen|tag|abend)|hallo|hi|hey|servus|moin|grüß dich|gruess dich)\\s+)+")
    private val linkOrPath = Regex("(?i)(https?://|www\\.|[/\\\\]|@)")
    private val topicWord = Regex("^[\\p{L}\\p{N}][\\p{L}\\p{N}-]{0,29}$")

    private class Scan {
        var day = false
        var world = false
        var marker = false
        var weak = false
        val topics = mutableListOf<TopicFilter>()
    }

    fun parse(utterance: String, assistantName: String? = null): Request? {
        val words = words(utterance, assistantName) ?: return null
        val scan = Scan()
        val joined = words.joinToString(" ")
        // Question forms without a news noun: „Was gibt's Neues in der Welt?“, „Was ist heute passiert?“.
        newsQuestions.firstNotNullOfOrNull { (pattern, needsWorld) -> pattern.matchEntire(joined)?.let { it to needsWorld } }
            ?.let { (match, needsWorld) ->
                val rest = match.groupValues.drop(1).filter { it.isNotBlank() }.flatMap { it.trim().split(' ') }
                if (!scan(rest, utterance, scan)) return null
                val named = scan.world || scan.topics.isNotEmpty()
                return if (named || (!needsWorld && scan.day)) Request(scan.topics.toList(), utterance.trim()) else null
            }
        val index = words.indexOfFirst { newsNoun.matches(it) }
        if (index < 0 || words.drop(index + 1).any { newsNoun.matches(it) }) return null
        val noun = newsNoun.matchEntire(words[index])!!
        val head = noun.groupValues[2]
        val prefix = noun.groupValues[1]
        if (prefix.isNotEmpty()) {
            val stem = prefix.removeSuffix("s")
            when {
                prefix in generalPrefixes || stem in generalPrefixes -> scan.marker = true
                else -> scan.topics += knownTopic(prefix) ?: knownTopic(stem) ?: return null
            }
        }
        if (head !in plainNouns) scan.marker = true
        if (head == "meldungen") scan.weak = true
        if (!scan(words.toMutableList().also { it[index] = NEWS }, utterance, scan)) return null
        if (scan.weak && !(scan.day || scan.world || scan.marker || scan.topics.isNotEmpty())) return null
        return Request(scan.topics.distinctBy { it.label }.take(MAX_TOPICS), utterance.trim())
    }

    /**
     * The owner's reply to the closing question of a briefing. [immediate]: it is the very next owner turn.
     * [briefedWords]: lower-case words of the briefed headlines – a bare word counts as a topic only when it is a
     * section name or one of these.
     */
    fun parseFollowUp(utterance: String, assistantName: String? = null, immediate: Boolean,
                      briefedWords: Set<String> = emptySet()): FollowUp? {
        val all = words(utterance, assistantName, minLength = 2) ?: return null
        val joined = all.joinToString(" ")
        if (immediate && decline.matches(joined)) return FollowUp.Decline
        if (immediate && accept.matches(joined)) return FollowUp.AskWhich
        storyNumber.find(joined)?.let { match ->
            val explicit = storyLead.containsMatchIn(joined)
            val rest = joined.removeRange(match.range).split(' ').filter { it.isNotEmpty() }
            val known = rest.all { it in fillers || it in followLead || it in articles || it in topicIntros || it in storyNouns }
            val number = ordinal(match.groupValues.drop(1).first { it.isNotEmpty() })
            if (known && number != null && (immediate || explicit)) return FollowUp.Story(number)
        }
        val words = all.dropWhile { it in fillers }
        if (words.isEmpty()) return null
        // „Mehr zu Wirtschaft“, „erzähl mir mehr über die Ukraine“, „genauer bei Österreich“, „was ist mit Sport“.
        val intro = words.indexOfFirst { it in topicIntros || it == "mit" || it == "bei" || it == "beim" }
        if (intro >= 0) {
            val lead = words.take(intro)
            // Later in the conversation only an unmistakable „mehr zu …“ counts; „was ist mit …“ needs the open question.
            val explicit = lead.any { it in followMarkers }
            if (lead.all { it in followLead || it in fillers || it in leadVerbs || it in frames || it in articles } &&
                (immediate || explicit)) {
                val scan = Scan()
                val end = topicsAt(words, intro + 1, utterance, scan)
                if (end != null && scan.topics.isNotEmpty() && words.drop(end).all { it in fillers || it in tails || it in followLead })
                    return FollowUp.Topics(Request(scan.topics.take(MAX_TOPICS), utterance.trim()))
            }
            return null
        }
        if (!immediate) return null
        // A bare reply: „Wirtschaft“, „ja, Sport bitte“, „Österreich und Technik“.
        val scan = Scan()
        val end = topicsAt(words, 0, utterance, scan) ?: return null
        if (scan.topics.isEmpty() || !words.drop(end).all { it in fillers }) return null
        val familiar = scan.topics.all { topic ->
            topic.section != null || topic.words.orEmpty().split(' ').all { word -> briefedWords.any { it.startsWith(word.take(6)) } }
        }
        return if (familiar) FollowUp.Topics(Request(scan.topics.take(MAX_TOPICS), utterance.trim())) else null
    }

    private const val MAX_TOPICS = 3
    private const val F = "(?:(?:ja|ok|okay|gut|also|dann|und|sag|sage|erzähl|erzähle|mir|mal|bitte|kurz|hey|hallo|servus|na|du)\\s)*"
    private const val MID = "((?:[\\p{L}\\p{N}-]+\\s){0,8}?)"
    private const val REST = "(?:\\s(.+))?"
    /** Pattern to „a world or topic marker is required“ (a bare „Was gibt's Neues?“ is small talk). */
    private val newsQuestions: List<Pair<Regex, Boolean>> = listOf(
        Regex("^${F}was (?:gibts|gibt es) (?:denn |so |sonst |heute |alles )*neues$REST") to true,
        Regex("^$F(?:gibts|gibt es) (?:denn |heute )*(?:was|etwas|irgendwas|irgendetwas) neues$REST") to true,
        Regex("^${F}was (?:ist|war) ${MID}(?:passiert|geschehen)$REST") to false,
        Regex("^${F}was (?:ist|war) ${MID}los$REST") to true,
        Regex("^${F}was tut sich$REST") to true,
        Regex("^$F(?:was|etwas|irgendwas) neues$REST") to true
    )
    private val accept = Regex("^(?:ja|jo|joa|gern|gerne|bitte|ok|okay|klar|unbedingt|mach|ja bitte|ja gern|ja gerne|ja klar|ja mach|sehr gern|sehr gerne)$")
    private val decline = Regex(
        "^(?:(?:nein|nö|noe|nee|na)(?: danke)?(?: (?:das )?(?:reicht|passt)(?: mir)?(?: so)?)?|" +
            "(?:danke )?(?:das )?(?:reicht|passt)(?: mir)?(?: so| schon)?(?: danke)?|alles klar|genug|danke|passt schon|" +
            "das wars|das war es|nichts mehr)$")
    private val followMarkers = setOf("mehr", "genauer", "genaueres", "details", "detail", "tiefer", "ausführlicher",
        "ausfuehrlicher", "näheres", "naeheres", "vertiefe", "vertiefen", "interessiert", "interessieren")
    private val followLead = followMarkers + setOf("geh", "gehe", "gehen", "rein", "ein", "hinschauen", "schau",
        "hin", "thema", "bereich", "nachlesen", "lies", "nach", "da", "davon", "dazu", "wissen", "hören", "würde",
        "mich", "ich", "möchte", "will", "noch", "was", "ist", "das", "nimm", "nehmen", "nehme", "lieber", "dann")
    private val storyNouns = setOf("meldung", "nachricht", "geschichte", "story", "sache", "punkt", "schlagzeile",
        "thema", "nummer", "nr")
    private val storyLead = Regex("(?:^| )(?:mehr|genauer|genaueres|details|tiefer|ausführlicher|näheres|nachlesen)(?: |$)")
    private val storyNumber = Regex(
        "(?:^| )(?:(erste|zweite|dritte|vierte|fünfte|fuenfte|sechste|letzte)[nrsm]?|(?:nummer|nr|meldung|punkt) (eins|zwei|drei|vier|fünf|fuenf|sechs|[1-6]))(?= |$)")

    private fun ordinal(word: String): Int? = when (word) {
        "erste", "eins", "1" -> 1; "zweite", "zwei", "2" -> 2; "dritte", "drei", "3" -> 3
        "vierte", "vier", "4" -> 4; "fünfte", "fuenfte", "fünf", "fuenf", "5" -> 5; "sechste", "sechs", "6" -> 6
        "letzte" -> -1
        else -> null
    }

    /** Lower-case words without punctuation; null when the utterance cannot be a news request at all. */
    private fun words(utterance: String, assistantName: String?, minLength: Int = 4): List<String>? {
        if (utterance.length !in minLength..240 || utterance.any { it.isISOControl() }) return null
        val text = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName)
        if (linkOrPath.containsMatchIn(text) || NetworkTurnConstraint.forbidsExternalResearch(utterance)) return null
        val words = text.lowercase(Locale.GERMAN).replace(Regex("[’'`´]"), "")
            .replace(Regex("[^\\p{L}\\p{N}-]+"), " ").trim().replace(greeting, "").split(' ')
            .map { it.trim('-') }.filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 32 || words.any { it in foreign }) return null
        return words
    }

    /** Consumes every word; false as soon as one word is not vocabulary of a news request. */
    private fun scan(words: List<String>, original: String, scan: Scan): Boolean {
        var i = 0
        while (i < words.size) {
            val phrase = phrases.firstOrNull { (parts, _) -> startsWith(words, i, parts) }
            if (phrase != null) {
                i += phrase.first.size
                when (phrase.second) {
                    Kind.DAY -> scan.day = true
                    Kind.WORLD -> scan.world = true
                    Kind.NEUTRAL -> Unit
                    Kind.TOPIC -> i = topicsAt(words, i, original, scan) ?: return false
                }
                continue
            }
            val word = words[i]
            when {
                word == NEWS || word in fillers || word in frames || word in articles || word in leadVerbs ||
                    word in tails || word in neutralAdjectives || word in summaryNouns -> i++
                word in adjectives -> { scan.marker = true; i++ }
                word in weakVerbs || word in ambiguousAdjectives -> { scan.weak = true; i++ }
                // „eine Zusammenfassung von den Nachrichten“ – never „Nachrichten von Anna“.
                word == "von" || word == "vom" ->
                    if (words.getOrNull(i + 1).let { it == NEWS || it in articles }) i++ else return false
                word in topicIntros -> i = topicsAt(words, i + 1, original, scan) ?: return false
                else -> return false
            }
        }
        return true
    }

    /**
     * Topic phrases from [start] („die Ukraine“, „Wirtschaft und Sport“); returns the index after them. A structural
     * preposition without a topic („einen Überblick über die Nachrichten“) adds nothing and is fine.
     */
    private fun topicsAt(words: List<String>, start: Int, original: String, scan: Scan): Int? {
        var i = start
        while (true) {
            while (i < words.size && (words[i] in articles || words[i] in adjectives || words[i] in neutralAdjectives)) i++
            val begin = i
            while (i < words.size && i - begin < 4 && words[i] != NEWS && words[i] !in topicStop &&
                words[i] !in conjunctions && topicWord.matches(words[i]) &&
                // „… über die Ukraine von heute“: a day or world phrase ends the topic.
                phrases.none { (parts, kind) -> kind != Kind.TOPIC && startsWith(words, i, parts) }) i++
            if (i == begin) return if (i >= words.size || words[i] == NEWS || words[i] in topicStop ||
                phrases.any { (parts, kind) -> kind != Kind.TOPIC && startsWith(words, i, parts) }) i else null
            val phrase = words.subList(begin, i).joinToString(" ")
            if (phrase.length !in 2..60 || words[begin] in nonTopics) return null
            when (phrase) {
                "welt", "ganzen welt", "aller welt" -> scan.world = true
                "heute" -> scan.day = true
                else -> scan.topics += knownTopic(phrase) ?: TopicFilter(originalCase(original, phrase), null, phrase)
            }
            if (i + 1 < words.size && words[i] in conjunctions && words[i + 1] != NEWS && words[i + 1] !in topicStop) i++
            else return i
        }
    }

    private fun knownTopic(phrase: String): TopicFilter? {
        NewsTopic.ofAlias(phrase)?.let { return TopicFilter(it.label, it, null) }
        NewsTopic.ofRelated(phrase)?.let { return TopicFilter(phrase.replaceFirstChar { c -> c.titlecase(Locale.GERMAN) }, it, phrase) }
        return null
    }

    private fun startsWith(words: List<String>, at: Int, parts: List<String>): Boolean =
        at + parts.size <= words.size && parts.indices.all { words[at + it] == parts[it] }

    /** The owner's own spelling of [phrase] for display („Ukraine“, not „ukraine“). */
    private fun originalCase(original: String, phrase: String): String {
        val at = original.indexOf(phrase, ignoreCase = true)
        return (if (at >= 0) original.substring(at, at + phrase.length) else phrase)
            .replaceFirstChar { it.titlecase(Locale.GERMAN) }
    }
}
