package at.heinz.deviceai.intelligence.maxultra

import at.heinz.deviceai.intelligence.maxultra.Lct1.Answer
import at.heinz.deviceai.intelligence.maxultra.Lct1.Fact
import at.heinz.deviceai.intelligence.maxultra.Lct1.Kind
import at.heinz.deviceai.intelligence.maxultra.Lct1.KnowledgeBase
import at.heinz.deviceai.intelligence.maxultra.Lct1.Query
import at.heinz.deviceai.intelligence.maxultra.Lct1.QuestionType
import at.heinz.deviceai.intelligence.maxultra.Lct1.Relation
import at.heinz.deviceai.intelligence.maxultra.Lct1.SourceStatus
import java.time.LocalDate
import java.util.Random

/**
 * Experiment C (WSC1), research only; pre-registered in docs/research/WSC1-CONSOLIDATION-PREREG-20261023.md.
 * A verified answer of LCT1 (L + V) becomes a reusable shortcut ("habit") only through a gate; a habit answers only
 * while everything L and V read for it is unchanged (name group of the mention, every fact group along the
 * derivation, the status of their sources) and the date lies in its validity window. No weights, no owner model.
 */
object Wsc1 {
    fun key(mention: String) = mention.trim().lowercase()

    /** One state of the knowledge base; FULL = L+V on it. */
    class Snapshot(val kb: KnowledgeBase) {
        val verifier = Lct1.Verifier(kb)
        val full = Lct1.VerifiedReasoner(Lct1.Reasoner(kb), verifier)
        val factById: Map<String, Fact> = kb.facts.associateBy { it.id }
        private val groups = kb.facts.groupBy { it.subject to it.relation }
        private val names = kb.entities.groupBy { key(it.name) }
        private val versions = HashMap<Pair<String, Relation>, String>()
        fun group(g: Pair<String, Relation>): List<Fact> = groups[g].orEmpty()
        fun version(g: Pair<String, Relation>): String =
            versions.getOrPut(g) { sha256(group(g).map { it.id + ":" + it.seal }.sorted().joinToString("|")) }
        fun nameVersion(mention: String): String = names[key(mention)].orEmpty().map { it.id }.sorted().joinToString("|")
    }

    /** `until` is exclusive; null = open. */
    data class Window(val from: LocalDate?, val until: LocalDate?) {
        fun contains(day: LocalDate) = (from == null || !day.isBefore(from)) && (until == null || day.isBefore(until))
    }

    data class Habit(
        val pattern: List<Relation>, val mention: String, val value: String, val derivation: List<String>, val last: Fact,
        val nameVersion: String, val groups: Map<Pair<String, Relation>, String>, val sources: Map<String, SourceStatus?>,
        val window: Window
    ) {
        /** SHA-256 over everything the habit claims; anyone holding the habit can recompute it. */
        val receipt: String get() = sha256(listOf(pattern, mention, value, derivation, last.id, last.seal, nameVersion,
            groups.entries.map { "${it.key.first}/${it.key.second}=${it.value}" }.sorted(),
            sources.entries.map { "${it.key}=${it.value}" }.sorted(), window).joinToString("\u001f"))

        fun appliesTo(q: Query, s: Snapshot): Boolean =
            pattern == q.type.pattern && mention == key(q.mention) && window.contains(q.asOf) &&
                s.nameVersion(q.mention) == nameVersion && groups.all { (g, v) -> s.version(g) == v } &&
                sources.all { (source, status) -> s.kb.sources[source] == status }

        /** Check questions re-evaluate the claim; the derived value is the habit's. */
        fun answer(q: Query, s: Snapshot): Answer = Lct1.conclude(q, s.kb, last, derivation)
    }

    /** Dependencies and validity window of a verified answer: everything L and V read to produce it. */
    fun habitOf(q: Query, a: Answer, s: Snapshot): Habit {
        val steps = a.derivation.map(s.factById::getValue)
        val keys = steps.map { it.subject to it.relation }
        val facts = keys.flatMap(s::group)
        val bounds = facts.flatMap { listOfNotNull(it.validFrom, it.validTo) }.distinct().sorted()
        return Habit(q.type.pattern, key(q.mention), a.value!!, a.derivation, steps.last(), s.nameVersion(q.mention),
            keys.associateWith(s::version), facts.map { it.source }.distinct().associateWith { s.kb.sources[it] },
            Window(bounds.lastOrNull { !it.isAfter(q.asOf) }, bounds.firstOrNull { it.isAfter(q.asOf) }))
    }

    fun same(a: Answer, b: Answer) = a.kind == b.kind && a.value == b.value && a.derivation == b.derivation

    interface Responder {
        val name: String
        val hits: Int
        fun answer(q: Query): Answer
        fun switchTo(next: Snapshot)
    }

    class Full(private var s: Snapshot) : Responder {
        override val name = "FULL"
        override val hits = 0
        override fun answer(q: Query) = s.full.answer(q)
        override fun switchTo(next: Snapshot) { s = next }
    }

    /** Plain verified cache: exact question (incl. date and claim) -> confirmed answer; any change empties it. */
    class Cache(private var s: Snapshot) : Responder {
        override val name = "CACHE"
        override var hits = 0
            private set
        private val store = HashMap<Query, Answer>()
        override fun answer(q: Query): Answer {
            val k = q.copy(mention = key(q.mention))
            store[k]?.let { hits++; return it }
            return s.full.answer(q).also { if (it.kind != Kind.ABSTAIN) store[k] = it }
        }
        override fun switchTo(next: Snapshot) { s = next; store.clear() }
    }

    /** WSC1: habits from the gate; a habit answers only while its dependencies and window hold, else FULL. */
    class Consolidated(private var s: Snapshot) : Responder {
        override val name = "WSC1"
        override var hits = 0
            private set
        private val habits = HashMap<Pair<List<Relation>, String>, MutableList<Habit>>()
        val size get() = habits.values.sumOf { it.size }
        fun lookup(q: Query, at: Snapshot = s): Habit? = habits[q.type.pattern to key(q.mention)]?.firstOrNull { it.appliesTo(q, at) }
        fun add(h: Habit) { habits.getOrPut(h.pattern to h.mention) { ArrayList() } += h }
        fun drop(h: Habit) { habits[h.pattern to h.mention]?.remove(h) }
        /** Answer on any snapshot without counting (gate probes). */
        fun answerOn(q: Query, at: Snapshot): Answer = lookup(q, at)?.answer(q, at) ?: at.full.answer(q)
        override fun answer(q: Query): Answer {
            val h = lookup(q) ?: return s.full.answer(q)
            hits++
            return h.answer(q, s)
        }
        override fun switchTo(next: Snapshot) { s = next }
        fun all(): List<Habit> = habits.values.flatten()
    }

    /** Negative control: the first verified answer per (relation sequence, mention), never invalidated. */
    class Naive(private var s: Snapshot) : Responder {
        override val name = "NAIVE"
        override var hits = 0
            private set
        private val habits = HashMap<Pair<List<Relation>, String>, Habit>()
        fun learn(h: Habit) { habits.putIfAbsent(h.pattern to h.mention, h) }
        override fun answer(q: Query): Answer {
            val h = habits[q.type.pattern to key(q.mention)] ?: return s.full.answer(q)
            hits++
            return h.answer(q, s)
        }
        override fun switchTo(next: Snapshot) { s = next }
    }

    /**
     * Sleep-phase gate for WSC1. A candidate becomes a habit only if V confirms it here, it answers like fresh L+V on
     * its own and on probe dates around every validity boundary of the facts it read, it does not apply on validated
     * counterfactual knowledge bases (one changed dependency each, whose fresh answer provably differs), and every
     * earlier accepted probe still passes. Every accepted habit leaves its receipt.
     */
    class Gate(private val target: Consolidated, private val rnd: Random) {
        private data class Probe(val query: Query, val expected: Answer)
        private val regression = ArrayList<Probe>()
        val receipts = ArrayList<String>()
        val verdicts = linkedMapOf<String, Int>()
        var uninformativeCounterfactuals = 0
            private set

        fun offer(q: Query, claim: Answer, s: Snapshot, flaw: ((Habit) -> Habit)? = null): String =
            judge(q, claim, s, flaw).also { verdicts.merge(it, 1, Int::plus) }

        private fun judge(q: Query, claim: Answer, s: Snapshot, flaw: ((Habit) -> Habit)?): String {
            if (claim.kind == Kind.ABSTAIN || !s.verifier.confirms(q, claim)) return "NOT_VERIFIED"
            if (flaw == null && target.lookup(q, s) != null) return "DUPLICATE"
            val correct = habitOf(q, claim, s)
            val habit = flaw?.invoke(correct) ?: correct
            if (!habit.appliesTo(q, s) || !same(habit.answer(q, s), claim)) return "OWN_QUESTION_MISMATCH"
            val probes = arrayListOf(Probe(q, claim))
            // probe dates around every validity boundary of the facts read, enumerated from the snapshot itself
            val read = claim.derivation.map { s.factById.getValue(it) }.flatMap { s.group(it.subject to it.relation) }
            val dates = read.flatMap { listOfNotNull(it.validFrom, it.validTo) }.flatMap { listOf(it.minusDays(1), it) } +
                q.asOf.plusDays(7)
            for (day in dates.distinct().sorted()) {
                val dq = q.copy(asOf = day)
                val fresh = s.full.answer(dq)
                if (habit.appliesTo(dq, s) && !same(habit.answer(dq, s), fresh)) return "DATE_PROBE"
                probes += Probe(dq, fresh)
            }
            // the same mention under another question type must not be served by this habit
            QuestionType.entries.filter { it.pattern != q.type.pattern && it.pattern.first().from == q.type.pattern.first().from }
                .forEach { other -> val oq = Query(other, q.mention, q.asOf); probes += Probe(oq, s.full.answer(oq)) }
            // validated counterfactuals: one dependency changed; fresh L+V must differ, the habit must not apply
            for (stepId in claim.derivation) {
                val cf = counterfactual(s, s.factById.getValue(stepId), q, claim) ?: continue
                if (habit.appliesTo(q, cf.first) && !same(habit.answer(q, cf.first), cf.second)) return "COUNTERFACTUAL"
            }
            // regression: earlier accepted probes and this candidate's probes, with the habit in place
            target.add(habit)
            val failed = (regression + probes).any { !same(target.answerOn(it.query, s), it.expected) }
            if (failed) { target.drop(habit); return "REGRESSION" }
            regression += probes
            receipts += habit.receipt
            return "ACCEPTED"
        }

        /** The knowledge base with `step` restated to another object (up to three tries until fresh L+V differs). */
        private fun counterfactual(s: Snapshot, step: Fact, q: Query, claim: Answer): Pair<Snapshot, Answer>? {
            repeat(3) {
                val changed = if (step.quantity != null) {
                    val others = Lct1Experiment.VOLTAGES.filter { it != step.quantity }
                    Fact.of(step.id, step.source, step.subject, step.relation, quantity = others[rnd.nextInt(others.size)],
                        polarity = step.polarity, validFrom = step.validFrom, validTo = step.validTo)
                } else {
                    val type = s.kb.entityById.getValue(step.objectId!!).type
                    val others = s.kb.entities.filter { it.type == type && it.id != step.objectId }
                    Fact.of(step.id, step.source, step.subject, step.relation, objectId = others[rnd.nextInt(others.size)].id,
                        polarity = step.polarity, validFrom = step.validFrom, validTo = step.validTo)
                }
                val cf = Snapshot(KnowledgeBase(s.kb.entities, s.kb.facts.map { if (it.id == step.id) changed else it }, s.kb.sources))
                val fresh = cf.full.answer(q)
                if (!same(fresh, claim)) return cf to fresh
            }
            uninformativeCounterfactuals++
            return null
        }
    }
}
