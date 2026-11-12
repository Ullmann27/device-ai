package at.heinz.deviceai.cognitive

import java.util.Locale

/** Classifies intent, never grants permission. */
internal object OwnerDeviceRequestSafety {
    enum class Category { READ_ONLY, PROPOSAL, DEFERRED_CHANGE, NEGATED_CHANGE, CHANGE, ROLE_MANIPULATION, CONVERSATION }
    data class Assessment(val categories: Set<Category>, val readOnlyText: String) {
        val blocked: Boolean get() = Category.ROLE_MANIPULATION in categories || Category.CHANGE in categories
        val roleManipulation: Boolean get() = Category.ROLE_MANIPULATION in categories
    }

    private fun words(pattern: String) = Regex("(?iu)(?<![\\p{L}\\p{N}_])(?:$pattern)(?![\\p{L}\\p{N}_])")
    private const val verbs = "lösch(?:e|en|st)?|loesch(?:e|en|st)?|entfern(?:e|en|st)?|" +
        "deinstallier(?:e|en|st)?|installier(?:e|en|st)?|verschieb(?:e|en|st)?|" +
        "start(?:e|en|est)?|stopp(?:e|en|st)?|überweis(?:e|en|t)?|ueberweis(?:e|en|t)?|" +
        "bezahl(?:e|en|st)?|änder(?:e|n|st)?|aender(?:e|n|st)?|bereinig(?:e|en|st)?|" +
        "säuber(?:e|n|st)?|saeuber(?:e|n|st)?|optimier(?:e|en|st)?|verbesser(?:e|n|st)?|" +
        "beend(?:e|en|est)?|schließ(?:e|en|t)?|schliess(?:e|en|t)?"
    private val mutation = words(verbs)
    private val roleHeader = Regex("(?iu)(?:^\\s*|[.!?;,\\n]\\s*)" +
        "(?:(?:hey|hallo)\\s+)?(?:(?:jarvis|continuum)[,:]?\\s+)?(?:system|developer|assistant|tool)\\s*:")
    private val ruleOverride = words("(?:ignorier\\p{L}*|vergiss|umgeh\\p{L}*|ignore|forget|bypass)" +
        "\\s+(?:(?:alle|die|deine|vorherigen|bisherigen|all|the|your|previous|safety)\\s+){0,5}" +
        "(?:anweisungen|regeln|sicherheitsregeln|sicherheitsprüfungen|instructions|rules|safeguards)")
    private val roleTakeover = words("(?:du\\s+bist|you\\s+are)\\s+(?:jetzt|ab\\s+sofort|now)" +
        "\\s+(?:ein\\s+|an?\\s+)?(?:admin|administrator|root)|(?:jailbreak)[ -]?modus")
    private val clauseBoundary = Regex(
        "(?iu)[.;!?\\n]+|,(?!\\s*(?:dass|was|warum|welche\\p{L}*|ob|wie|die\\s+ich)(?![\\p{L}]))|" +
            "\\s+(?:und|aber|jedoch)\\s+(?=(?:bitte\\s+)?(?:$verbs|prüfe?|pruefe?|zeige?|" +
            "analysiere?|untersuche?|schlage|sag(?:e)?|mach(?:e)?)(?![\\p{L}]))|" +
            "\\s+(?=ohne\\s+(?:etwas\\s+)?zu\\s+(?:$verbs)(?![\\p{L}]))"
    )
    private val negative = words("nicht|nichts|nie|niemals|kein\\p{L}*|unterlasse")
    private val exception = Regex("(?iu)(?:nichts|kein\\p{L}*|nicht)(?![\\p{L}])[^.;!?]*?" +
        "(?:(?<![\\p{L}])(?:außer|ausser|sondern|stattdessen|aber|jedoch)(?![\\p{L}])|,\\s*nur(?![\\p{L}]))")
    private val readAlternative = Regex("(?iu)^\\s+(?:prüfe?|pruefe?|zeige?|" +
        "analysiere?|untersuche?)\\s+")
    private val withoutChanging = Regex("(?iu)^ohne\\s+(?:etwas\\s+)?zu\\s+(?:$verbs)(?![\\p{L}])")
    private val refusesAction = Regex("(?iu)^(?:ich\\s+(?:will|möchte|moechte)\\s+)?" +
        "nicht\\s*,?\\s+dass\\s+du(?![\\p{L}])")
    private val beforeAction = Regex("(?iu)^(?:bitte\\s+)?(?:nicht|nie|niemals)$")
    private val noObject = Regex("(?iu)^(?:bitte\\s+)?(?:(?:noch|vorerst|erstmal|erst\\s+mal)\\s+)?" +
        "(?:nichts|kein\\p{L}*)(?![\\p{L}])")
    private val directRefusal = Regex("(?iu)^(?:bitte\\s+)?(?:nicht|nie|niemals)(?:$|\\s+" +
        "(?:die|den|das|diese\\p{L}*|mein\\p{L}*|dein\\p{L}*|etwas|alles)(?![\\p{L}]))")
    private val finalRefusal = Regex("(?iu)(?<![\\p{L}])(?:nicht|nie|niemals)$")
    private val notOnly = words("nicht\\s+nur|vergiss\\s+nicht")
    private val proposalSubject = words("ich|man|wir")
    private val proposalModal = words("kann|könnte|koennte|sollte|könnten|koennten")
    private val proposalQuestion = words("was|welche\\p{L}*|wie")
    private val deferred = words("(?:erst|nur)\\s+nach\\s+meiner\\s+(?:freigabe|bestätigung|bestaetigung|entscheidung)|" +
        "(?:danach|später|spaeter|anschließend)\\s+entscheide\\s+ich|" +
        "die\\s+ich\\s+dir\\s+(?:ausdrücklich\\s+)?freigebe")
    private val ownerDecision = Regex("(?iu)^(?:ich\\s+entscheide\\s+(?:später|spaeter)|" +
        "(?:danach|später|spaeter|anschließend)\\s+entscheide\\s+ich)(?![\\p{L}]).*")
    private val improvements = words("mach(?:e)?\\s+(?:dann\\s+|danach\\s+)?(?:alle\\s+|die\\s+)?verbesserungen")
    private val readOnlyStart = Regex("(?iu)^(?:bitte\\s+)?starte\\s+(?:bitte\\s+)?" +
        "(?:(?:eine|die)\\s+)?(?:systemdiagnose|handyanalyse|geräteanalyse|geraeteanalyse|" +
        "analyse\\s+(?:meines|des)\\s+(?:handys|geräts|geraets|smartphones))$")
    private const val collectiveTime = "(?:heute|morgen|jetzt|bald|später|gleich)"
    private const val collectiveObject = "(?:das|die|den|ein|eine|einen|unser(?:e|en|es)?)" +
        "\\s+[\\p{L}\\p{N}][\\p{L}\\p{N}-]{0,63}"
    private const val collectiveDetail = "(?:\\s+$collectiveTime)?(?:\\s+$collectiveObject)?(?:\\s+$collectiveTime)?"
    private val collectiveStart = Regex("(?iu)^(?:wir\\s+starten$collectiveDetail|" +
        "(?:lass(?:t)?\\s+uns|wir\\s+(?:wollen|möchten|werden|können)|" +
        "ihr\\s+(?:wollt|möchtet|werdet|könnt)|" +
        "$collectiveTime\\s+(?:(?:wollen|möchten|werden|können)\\s+wir|" +
        "(?:wollt|möchtet|werdet|könnt)\\s+ihr))$collectiveDetail\\s+starten)$")
    private val directedStart = words("du|dich|dir|dein\\p{L}*|sie|ihnen|dass|damit")

    /**
     * Proposal word order only: question word, then subject, then the single mutation verb ("was ich … löschen
     * könnte", "was könnten wir bereinigen"). An imperative opening or a declarative "ich/wir/man lösche(n) jetzt …"
     * before the question word stays a change, whatever sub-clause follows.
     */
    private fun isProposalOrder(clause: String, verbAt: Int): Boolean {
        if (verbAt <= 0) return false
        val question = proposalQuestion.findAll(clause).lastOrNull { it.range.first < verbAt } ?: return false
        return proposalSubject.findAll(clause).any { it.range.first > question.range.last && it.range.first < verbAt }
    }

    fun inspect(text: String): Assessment {
        if (roleHeader.containsMatchIn(text) || ruleOverride.containsMatchIn(text) || roleTakeover.containsMatchIn(text))
            return Assessment(setOf(Category.ROLE_MANIPULATION), "")
        val normalized = text.trim().lowercase(Locale.ROOT)
        val completeClause = normalized.trimEnd('.', '!', '?').trim()
        val categories = linkedSetOf<Category>()
        val readable = ArrayList<String>()
        normalized.split(Regex("[.;!?\\n]+" )).forEach { sentence ->
            val excluded = exception.find(sentence)
            val redirectsToRead = excluded != null &&
                listOf("sondern", "stattdessen", "aber", "jedoch").any(excluded.value::endsWith) &&
                readAlternative.containsMatchIn(sentence.substring(excluded.range.last + 1))
            if (mutation.containsMatchIn(sentence) && excluded != null && !redirectsToRead)
                categories += Category.CHANGE
        }
        for (part in normalized.split(clauseBoundary)) {
            val clause = part.trim().replace(Regex("^(?:aber|und|jedoch)\\s+"), "")
            if (clause.isBlank()) continue
            val changes = mutation.findAll(clause).toList()
            val category = when {
                readOnlyStart.matches(clause) -> Category.READ_ONLY
                changes.isEmpty() && !improvements.containsMatchIn(clause) ->
                    if (ownerDecision.matches(clause)) Category.DEFERRED_CHANGE else Category.READ_ONLY
                changes.size > 1 -> Category.CHANGE
                notOnly.containsMatchIn(clause) -> Category.CHANGE
                deferred.containsMatchIn(clause) -> Category.DEFERRED_CHANGE
                // "was wir/ich/man … bereinigen könnten" is a proposal; "Lösch alles, was wir … könnten" or "Entferne
                // alles, was ich … kann" is an imperative: question word, then subject, then the single mutation verb
                // (review #788, Code54 7b81e46e8 and 9713854bf).
                proposalModal.containsMatchIn(clause) && changes.singleOrNull()?.let { verb ->
                        isProposalOrder(clause, verb.range.first)
                    } == true -> Category.PROPOSAL
                changes.singleOrNull()?.let { negatesAction(clause, it) } == true -> Category.NEGATED_CHANGE
                changes.singleOrNull()?.value == "starten" && !improvements.containsMatchIn(clause) &&
                    clause == completeClause && collectiveStart.matches(clause) &&
                    !directedStart.containsMatchIn(clause) -> Category.CONVERSATION
                else -> Category.CHANGE
            }
            categories += category
            if (category == Category.READ_ONLY) readable += clause
        }
        return Assessment(categories, readable.joinToString(". "))
    }

    private fun negatesAction(clause: String, change: MatchResult): Boolean {
        if (withoutChanging.containsMatchIn(clause)) return true
        if (negative.findAll(clause).count() != 1) return false
        val before = clause.substring(0, change.range.first).trim()
        val after = clause.substring(change.range.last + 1).trim()
        return refusesAction.containsMatchIn(before) || beforeAction.matches(before) ||
            noObject.containsMatchIn(after) || directRefusal.containsMatchIn(after) ||
            finalRefusal.containsMatchIn(after)
    }
}
