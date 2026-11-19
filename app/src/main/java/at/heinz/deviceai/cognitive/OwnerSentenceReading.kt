package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * An ability a clause asks for that is no step of a sequence today. [cannot]: what the app says about it, in the
 * owner's language, as one whole sentence.
 */
internal enum class OwnerClauseAbility(val cannot: String) {
    /** Searching, reading and summarising mails are steps; answering, deleting, drafting and forwarding are not. */
    MAIL("Diesen Mail-Auftrag kann ich in einer Auftragsfolge noch nicht ausführen – Mails suchen, lesen und zusammenfassen geht."),
    /** A message is a step when it names WhatsApp and exactly one recipient; anything else stays the send lane's own order. */
    SEND("Diese Nachricht kann ich in einer Auftragsfolge noch nicht mitschicken: Dafür brauche ich „auf WhatsApp“ und " +
        "genau einen Empfänger; eine neue E-Mail schreibe ich nur als einzelnen Auftrag."),
    /** Opening an app is a step; working inside another app (a search there) is the computer lane's own order. */
    APP("In einer anderen App suchen oder etwas bedienen kann ich in so einer Auftragsfolge noch nicht – öffnen geht."),
    CALL("Einen Anruf kann ich in so einer Auftragsfolge noch nicht vorbereiten."),
    APPOINTMENTS("Deine Termine kann ich in so einer Auftragsfolge noch nicht nachsehen."),
    WEATHER("Das Wetter kann ich in so einer Auftragsfolge noch nicht abrufen."),
    BRIEFING("Systemcheck und Briefing passen noch nicht in eine Auftragsfolge."),
    STORAGE("Diese Speicheraufgabe braucht deine Auswahl oder eine Rückfrage und passt deshalb noch nicht in eine Auftragsfolge."),
    RESEARCH("Eine Web-Recherche kann ich in so einer Auftragsfolge noch nicht machen."),
    /** „Fass es zusammen“ about something that is no single page of the open PDF. */
    SUMMARY("Zusammenfassen oder erklären kann ich in so einer Auftragsfolge bisher nur eine einzelne Seite eines PDFs.")
}

/** What recognised one clause of the owner's sentence – or that nothing did. [code]: the form of the regression sets. */
internal sealed interface OwnerClauseReading {
    val code: String

    /** A lane carries it out as a step of a sequence today. */
    data class Step(val step: OwnerMissionStep) : OwnerClauseReading {
        override val code get() = step.code
    }

    /**
     * A lane takes this order when it is said alone; as a step of a sequence it is not carried out yet.
     * [transparent]: it produces nothing a later „es“ or „das“ could mean (a message in the owner's words, opening an
     * app, a call) – what follows it still refers to what stood in front of it.
     */
    data class Later(val ability: OwnerClauseAbility, val transparent: Boolean = false) : OwnerClauseReading {
        override val code get() = "LATER:" + ability.name
    }

    /** The app has no way to do this at all yet. */
    data class NotYet(val ability: OwnerClauseAbility) : OwnerClauseReading {
        override val code get() = "NOTYET:" + ability.name
    }

    /** It starts with an order verb, but no lane knows the order. */
    data object Unknown : OwnerClauseReading {
        override val code get() = "UNKNOWN"
    }

    /** No order verb: a remark, a question, or the rest of the clause in front of it. */
    data object NoOrder : OwnerClauseReading {
        override val code get() = "NO_ORDER"
    }
}

/**
 * One clause of the owner's sentence as the planner cut it.
 *
 * [text]: the clause as he said it, without the connector in front. [range]: where it stands in
 * [OwnerSentenceReading.said]. [verb]: the order verb it starts with ([OwnerVerbLexicon]), null = none.
 * [payload]: the part of [text] that is free text and never read as an order – after „dass“, after a colon or inside
 * quotes; null = the whole clause is command. [reading]: what recognised it. [dependsOnOpen]: it works on what an
 * earlier clause would produce, and that clause cannot run – so it is not offered either.
 */
internal data class OwnerClause(
    val text: String,
    val range: IntRange,
    val verb: OwnerVerbLexicon.Verb?,
    val payload: IntRange?,
    val reading: OwnerClauseReading,
    val dependsOnOpen: Boolean = false
) {
    /** The clause without its free text: only here a negation, a condition or an alternative rules the order out. */
    val command: String get() = payload?.let { text.removeRange(it) } ?: text
    val payloadText: String? get() = payload?.let { text.substring(it) }
    val step: OwnerMissionStep? get() = (reading as? OwnerClauseReading.Step)?.step
    /** true = an order that will not be carried out with this sentence. */
    val open: Boolean get() = reading !is OwnerClauseReading.NoOrder && (reading !is OwnerClauseReading.Step || dependsOnOpen)
}

/**
 * The owner's sentence read as a sequence of orders (review finding B1/B7, 19.11.2026): every clause with its verb,
 * its free text and what recognised it. The mission planner, the check of a model plan and the honest partial answer
 * all work on this one reading, so no clause can be dropped by one of them without the others knowing.
 */
internal class OwnerSentenceReading(
    /** The whole sentence as the owner said it. */
    val request: String,
    /** The sentence without the address and the closing punctuation; all ranges refer to it. */
    val said: String,
    val clauses: List<OwnerClause>,
    /** The complete plan: every clause is a step and the steps fit together. */
    val plan: OwnerMissionPlan?,
    /** What can run of a sentence that cannot run completely, in order and bound to each other; empty = nothing. */
    val rest: List<OwnerMissionStep>,
    /** The lane that takes the whole sentence as ONE order today („SEND“, „COMPUTER“, „DESK“, „BRIEFING“ …), or null. */
    val whole: String?,
    /** A negation, a condition or an alternative in a command part, free text outside a send order, or role manipulation. */
    val vetoed: Boolean
) {
    enum class Kind {
        /** A complete plan: the mission starts. */
        PLAN,
        /** Several orders, at least one for an ability of the app, and not all of them can run: the honest partial answer. */
        PARTIAL,
        /** Not a sequence for the planner: the sentence keeps today's route. */
        NONE
    }

    /** The clauses that are orders, in the order they were said. */
    val orders: List<OwnerClause> get() = clauses.filter { it.reading !is OwnerClauseReading.NoOrder }
    /** The orders that will not be carried out with this sentence. */
    val open: List<OwnerClause> get() = clauses.filter { it.open }

    val kind: Kind get() {
        if (plan != null) return Kind.PLAN
        val said = orders
        if (whole != null || vetoed || said.size < 2) return Kind.NONE
        // Only sentences that concern an ability of the app; talk with several verbs stays the model's. A bare „öffne
        // es“ or „fass sie zusammen“ behind an order nobody here knows is no such ability: what „es“ is, nothing here
        // can say – „Lies diese PDF und fass sie zusammen“ keeps its own route.
        if (said.none { clause ->
                when (val reading = clause.reading) {
                    is OwnerClauseReading.Later -> reading.ability != OwnerClauseAbility.SUMMARY
                    is OwnerClauseReading.NotYet -> true
                    is OwnerClauseReading.Step -> !clause.dependsOnOpen
                    else -> false
                }
            }) return Kind.NONE
        // Steps only that do not fit together („Öffne das PDF Argon4 und speichere es als Word“) keep today's route.
        if (said.all { it.reading is OwnerClauseReading.Step }) return Kind.NONE
        return Kind.PARTIAL
    }

    /** One code per order, as the regression sets write it. */
    val codes: List<String> get() = orders.map { it.reading.code }
}

/**
 * The honest partial answer (owner goal 19.11.2026: no „bitte einzeln“, nothing dropped without a word, nothing
 * invented): what cannot be done yet, what is left out because it builds on that, and the offer for the rest – which
 * runs only after his yes. Pure wording; the same clauses always give the same text.
 */
internal object OwnerMissionPartial {
    const val NOTHING_DONE = "Ich habe nichts davon ausgeführt."

    /**
     * [open]: the orders that cannot run, in the order they were said (the dependent ones included).
     * [rest]: what is offered, already bound to each other.
     */
    fun text(open: List<OwnerClause>, rest: List<OwnerMissionStep>): String = buildString {
        append("Verstanden – ganz kann ich das noch nicht erledigen.")
        // In the order the owner said it: what cannot be done, and right behind it what is left out because of it.
        val said = HashSet<String>()
        val hanging = ArrayList<String>()
        fun flush() {
            if (hanging.isNotEmpty()) append(" Was darauf aufbaut, lasse ich deshalb auch: ").append(list(hanging.distinct())).append('.')
            hanging.clear()
        }
        for (clause in open) {
            if (clause.dependsOnOpen) { hanging += label(clause); continue }
            flush()
            val sentence = when (val reading = clause.reading) {
                is OwnerClauseReading.Later -> reading.ability.cannot
                is OwnerClauseReading.NotYet -> reading.ability.cannot
                // An order the app knows, but that a proposed plan left out: said as what it is.
                is OwnerClauseReading.Step -> "Den Auftrag „${quoted(clause.text)}“ deckt der vorgeschlagene Plan nicht ab."
                else -> "Für „${quoted(clause.text)}“ habe ich in einer Auftragsfolge noch keinen festen Ablauf."
            }
            if (said.add(sentence)) append(' ').append(sentence)
        }
        flush()
        when (rest.size) {
            0 -> append(' ').append(NOTHING_DONE)
            1 -> append(" Soll ich den Rest machen: ").append(capital(rest.single().label)).append('?')
            else -> append(" Soll ich den Rest so machen: ").append(rest.mapIndexed { index, step ->
                "${index + 1}. ${capital(step.label)}" + if (index == rest.lastIndex) "?" else "."
            }.joinToString(" "))
        }
    }

    /**
     * The same answer when the sentence is a complete plan but Gmail is not connected: the mail steps cannot run,
     * [hanging] builds on them, [rest] does not and is offered.
     */
    fun withoutMail(hanging: List<String>, rest: List<OwnerMissionStep>): String = buildString {
        append("Verstanden – ganz kann ich das noch nicht erledigen. Gmail ist noch nicht verbunden, deshalb kann ich deine Mails ")
        append("nicht lesen. Sag „Verbinde Gmail“, dann geht auch das.")
        if (hanging.isNotEmpty()) append(" Was darauf aufbaut, lasse ich deshalb auch: ").append(list(hanging.distinct())).append('.')
        when (rest.size) {
            0 -> append(' ').append(NOTHING_DONE)
            1 -> append(" Soll ich den Rest machen: ").append(capital(rest.single().label)).append('?')
            else -> append(" Soll ich den Rest so machen: ").append(rest.mapIndexed { index, step ->
                "${index + 1}. ${capital(step.label)}" + if (index == rest.lastIndex) "?" else "."
            }.joinToString(" "))
        }
    }

    /** What a dependent order would have been, in a few words. */
    private fun label(clause: OwnerClause): String = when (val reading = clause.reading) {
        is OwnerClauseReading.Step -> reading.step.label
        is OwnerClauseReading.Later -> if (reading.ability == OwnerClauseAbility.SUMMARY) "zusammenfassen" else "„${quoted(clause.text)}“"
        else -> "„${quoted(clause.text)}“"
    }

    private fun quoted(text: String): String = text.trim().replace(Regex("[\"„“”«»]"), "").replace(Regex("\\s+"), " ")
        .let { if (it.length <= 90) it else it.take(87).trimEnd() + "…" }

    private fun capital(text: String): String = text.replaceFirstChar { it.uppercase(Locale.GERMAN) }

    private fun list(items: List<String>): String = when (items.size) {
        1 -> items.single()
        else -> items.dropLast(1).joinToString(", ") + " und " + items.last()
    }
}
