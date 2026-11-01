package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Need-based (not mode-based) decision whether a public knowledge question requires
 * current world information or is better explained by the local model.
 *
 * Pure lexical policy with a bounded vocabulary. It never grants network access: the
 * result only decides whether the existing owner consent dialog (ASK) is offered or
 * whether an ALWAYS_ON grant should be spent at all. Private/ambiguous questions are
 * already filtered out by [PublicKnowledgeTopicRouter]; a same-turn prohibition
 * ([NetworkTurnConstraint]) always wins.
 */
internal object FreshKnowledgeNeed {
    enum class Need {
        /** Answer depends on the present state of the world (office holders, prices, versions, news, dates). */
        CURRENT_FACT,
        /** Conceptual why/how question; local reasoning is the right first instrument. */
        EXPLANATION,
        /** Static or unclassified knowledge question. */
        STATIC
    }

    private val temporal = Regex(
        """(?iu)\b(?:aktuell(?:e[rsnm]?)?|derzeit(?:ig[ersnm]*)?|zurzeit|momentan|gegenwärtig(?:e[rsnm]?)?|amtierend(?:e[rsnm]?)?|heute|heutig(?:e[rsnm]?)?|gestern|morgen|neu(?:e|er|es|en)?st(?:e[rsnm]?)?|jüngst(?:e[rsnm]?)?|letzte[rnms]?\s+(?:woche|monat|jahr|tage?n?)|diese[rsnm]?\s+(?:woche|monat|jahr)|dieses\s+jahr|jetzt|inzwischen|mittlerweile|seit\s+(?:kurzem|neuestem)|now|current(?:ly)?|latest|recent(?:ly)?|today|this\s+(?:week|month|year))\b"""
    )
    private val yearNearNow = Regex("""\b20(?:2[5-9]|3[0-9])\b""")
    private val volatileTopic = Regex(
        """(?iu)\b(?:preis(?:e|es)?|kurs(?:e|es)?|wechselkurs|aktienkurs|inflation(?:srate)?|zins(?:en|satz)?|wetter|wettervorhersage|temperatur|nachrichten|news|schlagzeilen|wahl(?:en|ergebnis)?|ergebnis(?:se)?|spielstand|tabelle(?:nstand)?|version(?:snummer)?|release|update|veröffentlicht|erschienen|verfügbar|lieferbar|öffnungszeiten|fahrplan|termin(?:e)?|frist(?:en)?|stand\s+der\s+dinge|bundeskanzler(?:in)?|präsident(?:in)?|minister(?:in)?|bürgermeister(?:in)?|ceo|geschäftsführer(?:in)?|vorsitzende[rn]?|chef(?:in)?|trainer(?:in)?|rekord|weltrekord|gewinner(?:in)?|sieger(?:in)?|price|weather|election|stock|exchange\s+rate)\b"""
    )
    private val officeHolderQuestion = Regex(
        """(?iu)^\s*(?:wer|who)\s+(?:ist|sind|is|are|leitet|führt|regiert|hat|has|heads|leads)\b"""
    )
    private val explanation = Regex(
        """(?iu)^\s*(?:warum|wieso|weshalb|why|wozu|erkläre?|erklär|explain|wie\s+funktioniert|how\s+does|how\s+do|was\s+bedeutet|what\s+does\s+\S+\s+mean|was\s+ist\s+der\s+unterschied|what\s+is\s+the\s+difference|wie\s+entsteht|wie\s+kommt\s+es|was\s+passiert\s+wenn|what\s+happens\s+(?:if|when))\b"""
    )

    fun classify(message: String): Need {
        val text = message.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty() || text.length > 512) return Need.STATIC
        val lower = text.lowercase(Locale.ROOT)
        val fresh = temporal.containsMatchIn(lower) || yearNearNow.containsMatchIn(lower) ||
            (officeHolderQuestion.containsMatchIn(lower) && volatileTopic.containsMatchIn(lower))
        if (fresh) return Need.CURRENT_FACT
        if (explanation.containsMatchIn(lower)) return Need.EXPLANATION
        return Need.STATIC
    }

    /** True when current web evidence should be offered/used for a de-identified public topic. */
    fun needsCurrentWeb(message: String): Boolean =
        !NetworkTurnConstraint.forbidsExternalResearch(message) &&
            PublicKnowledgeTopicRouter.topic(message) != null &&
            classify(message) == Need.CURRENT_FACT

    /** True when an ALWAYS_ON grant should NOT be spent because local explanation is the right tool. */
    fun prefersLocalExplanation(message: String): Boolean = classify(message) == Need.EXPLANATION
}
