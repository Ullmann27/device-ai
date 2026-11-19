package at.heinz.deviceai.codingagents

import at.heinz.deviceai.cognitive.OwnerAppLookupIntent

/** What a cloud coding agent can be asked to do. [label] is the owner-facing name. */
enum class CodingAgentTaskKind(val label: String) {
    CODE("Coding-Auftrag"),
    SECURITY("Sicherheitsprüfung")
}

/** Which repository an order is for. Only [Named] and [OnlyAllowed] can ever lead to a run. */
sealed interface CodingAgentRepoChoice {
    /** The owner named exactly one repository of his allow-list. */
    data class Named(val repository: String) : CodingAgentRepoChoice
    /** None named and exactly one is allowed: proposed, and the confirmation says so. */
    data class OnlyAllowed(val repository: String) : CodingAgentRepoChoice
    /** None named (or several) and more than one is allowed: ask, never guess. */
    data class Ask(val candidates: List<String>) : CodingAgentRepoChoice
    /** A repository was named that is not on the allow-list; [said] is the owner's wording. */
    data class Unknown(val said: String) : CodingAgentRepoChoice
    /** The allow-list is empty. */
    object NoneAllowed : CodingAgentRepoChoice
}

sealed interface CodingAgentCommand {
    /** [task] is the owner's own wording after the address; nothing is added or rephrased. */
    data class Order(val kind: CodingAgentTaskKind, val repository: CodingAgentRepoChoice, val task: String) : CodingAgentCommand
    /** „Öffne die Coding-Agenten“, „Coding-Agenten einrichten“. */
    object Panel : CodingAgentCommand
    /** „Stopp den Coding-Agenten“. */
    object Stop : CodingAgentCommand
    /** „Was macht der Coding-Agent gerade?“ */
    object Status : CodingAgentCommand
}

/**
 * Deterministic recognition of an order for the cloud coding agents. Fail closed: a sentence that is not clearly
 * one of the order forms returns null and keeps today's route (chat, local team, navigation, the other lanes).
 *
 * The word „Coding-Agent“ (or one of its spoken variants) marks the cloud agents. The bare word „Agent“ belongs
 * to the local team of the app; it is only taken in „Lass den Agenten in <Repository> …“ / „Der Agent soll in
 * <Repository> …“ when the repository is on the owner's allow-list.
 */
object CodingAgentOrderParser {
    private const val AGENT = "(?:coding|code|programmier|entwickler|cloud|claude)[- ]?agent(?:en|s)?"
    private const val BARE = "agent(?:en)?"
    private const val END = "[.!]?"

    private fun rx(pattern: String) = Regex(pattern, setOf(RegexOption.IGNORE_CASE))

    private val PANEL = listOf(
        rx("^$AGENT[.:!]?$"),
        rx("^(?:öffne|öffnen|zeig|zeige|starte)\\s+(?:mir\\s+)?(?:bitte\\s+)?(?:(?:den|die|das|meine|meinen|unsere|unseren)\\s+)?" +
            "$AGENT(?:[- ](?:läufe|einstellungen|bereich|übersicht|liste))?(?:\\s+an)?$END$"),
        rx("^$AGENT(?:[- ](?:läufe|einstellungen))?\\s+(?:einrichten|öffnen|anzeigen|zeigen|konfigurieren)$END$"),
        rx("^(?:richte|stell|stelle)\\s+(?:bitte\\s+)?(?:den|die)\\s+$AGENT\\s+ein$END$"))

    private val STOP = listOf(
        rx("^(?:stopp|stoppe|stop|halt|halte|beende|brich|unterbrich)\\s+(?:bitte\\s+)?(?:den|die)\\s+$AGENT(?:\\s+(?:an|ab))?$END$"),
        rx("^$AGENT[,:]?\\s+(?:stopp|stoppen|stop|halt|anhalten|abbrechen|beenden|unterbrechen)$END$"))

    private val STATUS = listOf(
        rx("^$AGENT[- ]?status[?.!]?$"),
        rx("^status\\s+(?:des|vom|der|von)\\s+(?:dem\\s+)?$AGENT[?.!]?$"),
        rx("^(?:wie weit ist|was macht|wie steht es um|was tut)\\s+(?:der|den|mein|unser)\\s+$AGENT(?:\\s+gerade)?[?.!]?$"))

    private val QUESTION_STARTS = setOf("was", "wie", "warum", "wieso", "weshalb", "wann", "wo", "wer", "welche", "welcher",
        "welches", "kann", "kannst", "könnte", "könntest", "darf", "soll", "sollte", "ist", "hat", "gibt", "macht", "wird")

    /** After „Coding-Agent“ without punctuation these make the sentence a statement about the agent, not an order. */
    private val STATEMENT_VERBS = setOf("ist", "sind", "war", "waren", "hat", "hatte", "haben", "hätte", "kann", "könnte",
        "können", "wird", "würde", "wurde", "werden", "darf", "dürfte", "will", "wollte", "möchte", "und", "oder")

    private val ASSIGN = rx("^(?:bitte\\s+)?(?:gib|gebe|erteile|erteil|schick|schicke|sende)\\s+(?:bitte\\s+)?(?:dem|den)\\s+($AGENT)\\s+" +
        "(?:(?:den|einen|folgenden|diesen)\\s+)?auftrag\\s*[:,]?\\s*(.+)$")
    private val ASSIGN_TO = rx("^auftrag\\s+(?:an|für)\\s+(?:den|die)\\s+($AGENT)\\s*[:,]?\\s*(.+)$")
    private val LET = rx("^(?:bitte\\s+)?(?:lass|lasse|lasst)\\s+(?:bitte\\s+)?(?:mal\\s+)?(?:den|die|einen|unseren|meinen)\\s+($AGENT|$BARE)\\s+(.+)$")
    private val SHALL = rx("^(?:(?:der|die|ein|unser|mein)\\s+)?($AGENT|$BARE)\\s+(?:soll|sollte|möge|muss)\\s+(.+)$")
    private val ADDRESSED = rx("^($AGENT)(?:\\s*[:,]\\s*|\\s+[–—-]\\s+)(.+)$")
    private val ADDRESSED_PLAIN = rx("^($AGENT)\\s+(.+)$")

    private val NEGATED = rx("^(?:nicht|nichts|kein|keine|keinen|keinem|keiner|nie|niemals)\\b")
    private val QUOTE_STARTS = setOf('„', '"', '»', '«', '“', '”', '‚', '\'')

    private val SECURITY_TERM = rx("sicherheitsprüfung|sicherheits(?:check|analyse|audit|überprüfung|test|review)|" +
        "security[- ]?(?:review|audit|check|scan)|pentest|penetrationstest")
    private val REVIEW_VERB = rx("(?<![\\p{L}\\p{N}])(?:prüf|überprüf|untersuch|check|durchsuch|such|scann|analysier|find|durchleucht)\\p{L}*")
    private val SECURITY_NOUN = rx("sicherheitslück|schwachstell|sicherheitsproblem|sicherheitsfehler|angreifbar|verwundbar|" +
        "(?<![\\p{L}\\p{N}])sicherheit(?![\\p{L}\\p{N}])")

    private val REPO_MARKER = rx("(?<![\\p{L}\\p{N}])(?:im|in|ins|vom|von|für|des|aus|am|an)\\s+(?:(?:das|dem|den|der)\\s+)?(?:github[- ])?" +
        "(?:repo|repository|repositorium|projekt|projekts)\\s+([A-Za-z0-9][A-Za-z0-9._/-]*)")
    private val REPO_URL = rx("github\\.com/([A-Za-z0-9][A-Za-z0-9-]*/[A-Za-z0-9._-]+)")
    private const val LOCATIVE = "(?:in|im|ins|für|bei|von|vom|auf|am|an)\\s+(?:(?:das|dem|den|der)\\s+)?(?:(?:repo|repository|repositorium|projekt)\\s+)?"

    fun parse(spoken: String, assistantName: String?, allowed: List<String>): CodingAgentCommand? {
        val text = OwnerAppLookupIntent.stripAddress(spoken.trim(), assistantName).trim()
        if (text.isEmpty() || text.length > MAX_ORDER_CHARACTERS || text.first() in QUOTE_STARTS) return null
        if (PANEL.any { it.matches(text) }) return CodingAgentCommand.Panel
        if (STOP.any { it.matches(text) }) return CodingAgentCommand.Stop
        if (STATUS.any { it.matches(text) }) return CodingAgentCommand.Status
        if (text.endsWith("?")) return null
        if (text.substringBefore(' ').lowercase().trimEnd(',', ':') in QUESTION_STARTS) return null

        val (said, rawTask, plain) = ASSIGN.matchEntire(text)?.let { Triple(it.groupValues[1], it.groupValues[2], false) }
            ?: ASSIGN_TO.matchEntire(text)?.let { Triple(it.groupValues[1], it.groupValues[2], false) }
            ?: LET.matchEntire(text)?.let { Triple(it.groupValues[1], it.groupValues[2], false) }
            ?: SHALL.matchEntire(text)?.let { Triple(it.groupValues[1], it.groupValues[2], false) }
            ?: ADDRESSED.matchEntire(text)?.let { Triple(it.groupValues[1], it.groupValues[2], false) }
            ?: ADDRESSED_PLAIN.matchEntire(text)?.let { Triple(it.groupValues[1], it.groupValues[2], true) }
            ?: return null
        val explicit = said.lowercase() !in setOf("agent", "agenten")
        val task = rawTask.trim().replace(Regex("(?i)^bitte\\s+"), "").trimEnd('.', '!', ' ').trim()
        if (task.isEmpty() || NEGATED.containsMatchIn(task)) return null
        val first = task.substringBefore(' ')
        if (plain) {
            // „Coding-Agent hat gestern …“, „Coding-Agent schreibt gerade …“: a statement, not an order.
            if (first.lowercase() in STATEMENT_VERBS) return null
            if (first.first().isLowerCase() && first.endsWith("t")) return null
        }
        if (task.split(Regex("\\s+")).size < 2) return if (explicit) CodingAgentCommand.Panel else null
        val choice = resolve(task, allowed, requireLocative = !explicit)
        // The bare word „Agent“ is the local team unless an allowed repository is named with it.
        if (!explicit && choice !is CodingAgentRepoChoice.Named) return null
        return CodingAgentCommand.Order(kind(task), choice, task)
    }

    fun kind(task: String): CodingAgentTaskKind =
        if (SECURITY_TERM.containsMatchIn(task) || (REVIEW_VERB.containsMatchIn(task) && SECURITY_NOUN.containsMatchIn(task)))
            CodingAgentTaskKind.SECURITY else CodingAgentTaskKind.CODE

    /**
     * The repository of [task]: only ever one of [allowed]. A named repository that is not allowed is reported as
     * [CodingAgentRepoChoice.Unknown] and never replaced by an allowed one.
     */
    fun resolve(task: String, allowed: List<String>, requireLocative: Boolean = false): CodingAgentRepoChoice {
        if (allowed.isEmpty()) return CodingAgentRepoChoice.NoneAllowed
        class Hit(val repository: String, val range: IntRange)
        val hits = ArrayList<Hit>()
        var foreign: String? = null
        allowed.forEach { repository ->
            val owner = repository.substringBefore('/')
            val name = repository.substringAfter('/')
            val namePattern = name.split(Regex("[-_. ]+")).filter { it.isNotEmpty() }.joinToString("[-_. ]?") { Regex.escape(it) }
            val pattern = rx((if (requireLocative) "(?<![\\p{L}\\p{N}])$LOCATIVE" else "(?<![\\p{L}\\p{N}])") +
                "(?:([A-Za-z0-9][A-Za-z0-9-]*)/)?($namePattern)(?![\\p{L}\\p{N}/])")
            pattern.findAll(task).forEach { match ->
                val saidOwner = match.groupValues[1]
                when {
                    saidOwner.isEmpty() || saidOwner.equals(owner, ignoreCase = true) -> hits += Hit(repository, match.range)
                    // Same name under another account: someone else's repository.
                    else -> foreign = saidOwner + "/" + match.groupValues[2]
                }
            }
        }
        // „continuum“ inside „continuum-web“: the longer name is the one that was said.
        val kept = hits.filter { hit -> hits.none { other -> other !== hit && other.repository != hit.repository &&
            other.range.first <= hit.range.first && other.range.last >= hit.range.last &&
            (other.range.last - other.range.first) > (hit.range.last - hit.range.first) } }
        val named = kept.map { it.repository }.distinct()
        foreign?.let { if (named.isEmpty()) return CodingAgentRepoChoice.Unknown(it) }
        if (named.size == 1) return CodingAgentRepoChoice.Named(named.single())
        if (named.size > 1) return CodingAgentRepoChoice.Ask(named)
        val marked = REPO_URL.find(task)?.groupValues?.get(1) ?: REPO_MARKER.find(task)?.groupValues?.get(1)
        marked?.trimEnd('.', ',', ';', ':', '/')?.takeIf { it.isNotEmpty() }?.let { said ->
            return allowed.firstOrNull { it.equals(said, ignoreCase = true) }?.let { CodingAgentRepoChoice.Named(it) }
                ?: CodingAgentRepoChoice.Unknown(said)
        }
        return if (allowed.size == 1) CodingAgentRepoChoice.OnlyAllowed(allowed.single()) else CodingAgentRepoChoice.Ask(allowed)
    }

    /** Stable code of a parse result for the evaluation sets (`regression-coding-agents*.jsonl`). */
    fun code(command: CodingAgentCommand?): String = when (command) {
        null -> "NONE"
        CodingAgentCommand.Panel -> "PANEL"
        CodingAgentCommand.Stop -> "STOP"
        CodingAgentCommand.Status -> "STATUS"
        is CodingAgentCommand.Order -> command.kind.name + ":" + when (val choice = command.repository) {
            is CodingAgentRepoChoice.Named -> choice.repository
            is CodingAgentRepoChoice.OnlyAllowed -> "ONLY:" + choice.repository
            is CodingAgentRepoChoice.Ask -> "ASK"
            is CodingAgentRepoChoice.Unknown -> "UNKNOWN:" + choice.said
            CodingAgentRepoChoice.NoneAllowed -> "NO_REPOS"
        }
    }

    const val MAX_ORDER_CHARACTERS = 2_000
}
