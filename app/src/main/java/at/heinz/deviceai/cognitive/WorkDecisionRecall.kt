package at.heinz.deviceai.cognitive

import java.util.TimeZone

/**
 * „Was haben wir gestern bei unserem Projekt entschieden?“ – „Nein, ich meinte die Entscheidung davor.“ – „Was davon
 * haben wir schon umgesetzt?“ – „Dann mach bitte beim letzten offenen Punkt weiter.“ (owner brief 06.11.2026, Priorität 1).
 *
 * A read-only view over the two records that exist – nothing is written and no third store is created:
 *  - the [CockpitWorkJournal]: what was really saved, stopped, corrected or left unfinished, with the journal's times;
 *  - the owner's own confirmed statements of the learning ledger ([OwnerStatement], supplied by the caller from
 *    [at.heinz.deviceai.learning.ContinuumLearningCore.ownerStatementHistory]) that speak of a decision, with their
 *    assertion time and – when revoked – the revocation time. Plain owner facts („Ich wohne in …“) are not decisions.
 *
 * A journal episode never becomes an owner fact here, and an owner statement is never reported as carried out: the device
 * cannot check that. Follow-ups („davor“, „davon“) resolve only against the items shown in this conversation ([Focus]);
 * after a restart nothing was shown, so they ask instead of guessing. Every answer names its origin.
 */
internal object WorkDecisionRecall {
    enum class Origin { JOURNAL, OWNER_STATEMENT }

    /** A confirmed owner statement of the ledger, with its assertion time and – if revoked – the revocation time. */
    data class OwnerStatement(val content: String, val assertedAt: Long, val revokedAt: Long? = null)

    data class Item(
        val at: Long,
        val text: String,
        val origin: Origin,
        val episodeId: String? = null,
        /** true = really carried out (recorded in the journal), false = open, null = not checkable (owner statement). */
        val done: Boolean?,
        val next: String? = null,
        val revokedAt: Long? = null,
        /** A change: correction, owner edit, STOP, revocation – for „Was hat sich geändert?“. */
        val change: Boolean = false
    )

    sealed class Ask {
        data class Decisions(val period: WorkRecall.Period, val changes: Boolean) : Ask()
        object Previous : Ask()
        object Implemented : Ask()
        object ContinueOpen : Ask()
    }

    /** What this conversation has shown: all items newest first, the ones shown last, and the one the owner refers to. */
    data class Focus(val items: List<Item>, val shown: List<Item>, val index: Int)

    /** [hits] = items really found (0 = no light in the cockpit); [understood] = a follow-up resolved to a record. */
    data class Reply(val text: String, val focus: Focus?, val hits: Int, val understood: Boolean = false)

    data class Resume(val item: Item, val episode: CockpitWorkJournal.Episode, val step: String)

    private const val S = "(?<![\\p{L}\\p{N}_])"
    private const val E = "(?![\\p{L}\\p{N}_])"
    private val we = Regex("(?iu)$S(wir|uns|unser|unserem|unseren|unsere)$E")
    private val question = Regex("(?iu)$S(was|welche|welchen|welcher|wie)$E")
    private val decided = Regex("(?iu)$S(entschieden|beschlossen|festgelegt|ausgemacht|vereinbart|entscheidungen?|beschl(u|ü)sse?)$E")
    private val changes = Regex("(?iu)${S}was\\s+hat\\s+sich(\\s+(seitdem|seither|inzwischen|seit\\s+gestern|seit\\s+heute|daran|dabei))?\\s+(ge|ver)ändert$E")
    private val previous = Regex("(?iu)^\\s*(nein[,!.]?\\s+|nicht\\s+(die|den|das)[,!.]?\\s+)?(ich\\s+meinte|ich\\s+meine|gemeint\\s+war|gemeint\\s+ist)?\\s*" +
        "(die|den|das|der)?\\s*(entscheidung|beschluss|punkt)\\s+(davor|vorher|zuvor|vorige|vorherige)\\b")
    private val implemented = Regex("(?iu)^\\s*(und\\s+)?was\\s+(davon\\s+)?(haben|hatten|ist|sind)\\s+(wir\\s+)?(davon\\s+)?" +
        "(schon|bereits|noch|davon)?\\s*(schon|bereits|noch)?\\s*(umgesetzt|erledigt|gemacht|fertig|offen)")
    private val continueOpen = Regex("(?iu)$S(mach|machen|weiter|fahr|fahre|setz|setze|arbeite|arbeiten)$E.*$S(letzten?|letzter)\\s+offenen?\\s+(punkt|aufgabe|schritt)$E|" +
        "$S(letzten?|letzter)\\s+offenen?\\s+(punkt|aufgabe|schritt)$E.*${S}weiter$E")
    private val yesterday = Regex("(?iu)${S}gestern$E")
    private val today = Regex("(?iu)$S(heute|vorhin|gerade|eben)$E")
    /** Owner statements that speak of a decision (never plain facts about persons or places). */
    private val statementIsDecision = Regex("(?iu)$S(entschieden|entscheidung|beschlossen|beschluss|festgelegt|vereinbart|ausgemacht|" +
        "ab\\s+jetzt|ab\\s+sofort|künftig|zukünftig|in\\s+zukunft|nicht\\s+mehr|stattdessen|wir\\s+(nehmen|machen|verwenden|bleiben|wechseln|" +
        "verschieben|streichen|lassen|kürzen|starten|beginnen|behalten|verzichten))$E")

    fun parse(utterance: String): Ask? {
        if (utterance.length !in 8..300 || utterance.any { it.isISOControl() }) return null
        if (previous.containsMatchIn(utterance)) return Ask.Previous
        if (implemented.containsMatchIn(utterance) && utterance.contains(Regex("(?iu)${S}davon$E"))) return Ask.Implemented
        if (continueOpen.containsMatchIn(utterance)) return Ask.ContinueOpen
        if (changes.containsMatchIn(utterance)) return Ask.Decisions(period(utterance), changes = true)
        if (question.containsMatchIn(utterance) && we.containsMatchIn(utterance) && decided.containsMatchIn(utterance) &&
            utterance.contains('?')) return Ask.Decisions(period(utterance), changes = false)
        return null
    }

    private fun period(utterance: String) = when {
        yesterday.containsMatchIn(utterance) -> WorkRecall.Period.YESTERDAY
        today.containsMatchIn(utterance) -> WorkRecall.Period.TODAY
        else -> WorkRecall.Period.RECENT
    }

    /** All decision items of both records, newest first (ties: journal before statements, then by text). */
    fun items(episodes: List<CockpitWorkJournal.Episode>, statements: List<OwnerStatement>, now: Long): List<Item> {
        val fromJournal = episodes.flatMap { e -> episodeItems(e, now) }
        val fromOwner = statements.filter { statementIsDecision.containsMatchIn(it.content) }.map {
            Item(it.assertedAt, "Von dir bestätigt: „${it.content.trim()}“", Origin.OWNER_STATEMENT, done = null,
                revokedAt = it.revokedAt, change = it.revokedAt != null)
        }
        return (fromJournal + fromOwner).sortedWith(compareByDescending<Item> { it.at }.thenBy { it.origin }.thenBy { it.text })
    }

    /**
     * One item per episode – the owner's latest act on it: the correction when there is one (it carries the open step),
     * otherwise the outcome the journal recorded. Reading alone decides nothing.
     */
    private fun episodeItems(e: CockpitWorkJournal.Episode, now: Long): List<Item> {
        val name = e.source ?: "Auftrag"
        val next = CockpitWorkJournal.nextStep(e, now)
        e.correction?.takeIf { it.isNotBlank() }?.let {
            return listOf(Item(e.correctedAt ?: e.updatedAt, "Deine Korrektur zu „${e.request}“: „$it“", Origin.JOURNAL, e.id,
                done = next == null, next = next, change = true))
        }
        val unfinished = "Auftrag „${e.request}“ zu „$name“: ${CockpitWorkJournal.stateText(e, now)}"
        val outcome = when (e.state) {
            CockpitWorkJournal.State.READ -> null
            CockpitWorkJournal.State.SAVED -> Item(e.updatedAt, "Entwurf zu „$name“ gespeichert", Origin.JOURNAL, e.id, done = true)
            CockpitWorkJournal.State.DRAFTED -> Item(e.updatedAt, "Entwurf zu „$name“ erstellt" +
                (if (e.ownerEdits > 0) " und von dir bearbeitet" else "") + ", noch nicht gespeichert", Origin.JOURNAL, e.id,
                done = false, next = next, change = e.ownerEdits > 0)
            CockpitWorkJournal.State.ABORTED -> Item(e.updatedAt, "Arbeit an „$name“ mit STOP beendet", Origin.JOURNAL, e.id,
                done = false, next = next, change = true)
            CockpitWorkJournal.State.DRAFTING -> if (next == null) Item(e.updatedAt, "Entwurf zu „$name“ in Arbeit", Origin.JOURNAL,
                e.id, done = false) else Item(e.updatedAt, unfinished, Origin.JOURNAL, e.id, done = false, next = next)
            CockpitWorkJournal.State.NO_MODEL, CockpitWorkJournal.State.FAILED, CockpitWorkJournal.State.INTERRUPTED ->
                Item(e.updatedAt, unfinished, Origin.JOURNAL, e.id, done = false, next = next)
        }
        return listOfNotNull(outcome)
    }

    fun answer(ask: Ask, focus: Focus?, episodes: List<CockpitWorkJournal.Episode>, statements: List<OwnerStatement>,
               now: Long, zone: TimeZone, limit: Int = 6): Reply = when (ask) {
        is Ask.Decisions -> decisions(ask, episodes, statements, now, zone, limit)
        Ask.Previous -> previous(focus, now, zone)
        Ask.Implemented -> implemented(focus, now, zone)
        Ask.ContinueOpen -> {
            val resume = resolveContinue(focus, episodes, now)
            if (resume == null) Reply(if (focus == null) "Im Arbeitsverlauf ist kein Punkt offen."
                else "Von den genannten Punkten ist keiner mehr offen – im Arbeitsverlauf ist kein Punkt offen.", focus, 0)
            else Reply("Weiter beim letzten offenen Punkt: ${line(resume.item, now, zone)}", focus, 1, understood = true)
        }
    }

    private fun decisions(ask: Ask.Decisions, episodes: List<CockpitWorkJournal.Episode>, statements: List<OwnerStatement>,
                          now: Long, zone: TimeZone, limit: Int): Reply {
        val all = items(episodes, statements, now)
        val (from, to) = WorkRecall.window(ask.period, now, zone)
        val shown = all.filter { it.at in from until to }.filter { !ask.changes || it.change }.take(limit)
        if (shown.isEmpty()) {
            val what = if (ask.changes) "keine Änderung" else "keine Entscheidung"
            val text = when (ask.period) {
                WorkRecall.Period.YESTERDAY -> "Für gestern finde ich weder im Arbeitsverlauf noch in deinen bestätigten Aussagen $what. " +
                    "Meinst du einen anderen Tag – oder soll ich die letzten Entscheidungen zeigen?"
                WorkRecall.Period.TODAY -> "Für heute finde ich weder im Arbeitsverlauf noch in deinen bestätigten Aussagen $what."
                WorkRecall.Period.RECENT -> "Im Arbeitsverlauf und in deinen bestätigten Aussagen finde ich $what."
            }
            return Reply(text, null, 0)
        }
        val head = when (ask.period) {
            WorkRecall.Period.YESTERDAY -> "Gestern"
            WorkRecall.Period.TODAY -> "Heute"
            WorkRecall.Period.RECENT -> "Zuletzt"
        } + if (ask.changes) " geändert:" else " entschieden oder festgehalten:"
        val text = head + "\n" + shown.joinToString("\n") { "• " + line(it, now, zone) } + "\n" + ORIGIN
        return Reply(text, Focus(all, shown, all.indexOf(shown.first())), shown.size)
    }

    private fun previous(focus: Focus?, now: Long, zone: TimeZone): Reply {
        if (focus == null) return Reply("Davor – von welcher aus? Ich habe in diesem Gespräch noch keine Entscheidung genannt. " +
            "Frag mich zum Beispiel: „Was haben wir gestern entschieden?“", null, 0)
        val index = focus.index + 1
        val item = focus.items.getOrNull(index)
            ?: return Reply("Davor finde ich keine weitere Entscheidung – weder im Arbeitsverlauf noch in deinen bestätigten Aussagen.",
                focus, 0)
        return Reply("Verstanden – die Entscheidung davor:\n• ${line(item, now, zone)}\n$ORIGIN",
            Focus(focus.items, listOf(item), index), 1, understood = true)
    }

    private fun implemented(focus: Focus?, now: Long, zone: TimeZone): Reply {
        if (focus == null) return Reply("Wovon? Ich habe in diesem Gespräch noch keine Entscheidungen genannt. " +
            "Frag mich zum Beispiel: „Was haben wir gestern entschieden?“", null, 0)
        val revoked = focus.shown.filter { it.revokedAt != null }
        val done = focus.shown.filter { it.done == true && it.revokedAt == null }
        val open = focus.shown.filter { it.done == false && it.revokedAt == null }
        val unknown = focus.shown.filter { it.done == null && it.revokedAt == null }
        fun section(title: String, items: List<Item>) =
            if (items.isEmpty()) null else "$title\n" + items.joinToString("\n") { "• " + line(it, now, zone) }
        val text = listOfNotNull(
            section("Umgesetzt:", done),
            section("Noch offen:", open),
            section("Nicht prüfbar (deine Aussage, keine Aktion dieses Geräts):", unknown),
            section("Widerrufen:", revoked)
        ).joinToString("\n") + "\n" + ORIGIN
        return Reply(text, focus, focus.shown.size, understood = true)
    }

    /**
     * The newest open journal point – of the items shown, or of the whole record when nothing was shown. Owner statements
     * cannot be continued by the device; done tasks are done. Null when nothing is open.
     */
    fun resolveContinue(focus: Focus?, episodes: List<CockpitWorkJournal.Episode>, now: Long): Resume? {
        val candidates = focus?.shown ?: items(episodes, emptyList(), now)
        return candidates.filter { it.done == false && it.episodeId != null && it.next != null }
            .sortedByDescending { it.at }.firstNotNullOfOrNull { item ->
                episodes.firstOrNull { it.id == item.episodeId }?.let { e -> Resume(item, e, item.next!!) }
            }
    }

    fun line(item: Item, now: Long, zone: TimeZone): String =
        "${WorkRecall.whenText(item.at, now, zone)}: ${item.text}" +
            (item.revokedAt?.let { " – widerrufen ${WorkRecall.whenText(it, now, zone)}" } ?: "") +
            (if (item.done == true) " – umgesetzt" else "") +
            (item.next?.let { " – offen: $it" } ?: "")

    const val ORIGIN = "(Aus dem Arbeitsverlauf und deinen bestätigten Aussagen dieses Geräts – Erinnerung, kein Modelllernen.)"
}
