package at.heinz.deviceai.intelligence.maxultra

import java.security.MessageDigest
import java.time.LocalDate
import java.util.Random
import kotlin.math.sqrt

/**
 * Experiment B (LCT1), research only: test source set, pre-registered in
 * docs/research/LCT1-REASONING-PREREG-20261023.md. A typed, source-bound, bounded multi-hop reasoner (L), an
 * independent verifier (V), the algorithm of EvidenceBoundedThoughtSearch on the same rules (B0) and two latent
 * fast-weight variants (Z-rel, Z-beam). Nothing here is wired to the chat, CCC1, memory or any model weights.
 */
object Lct1 {
    const val MAX_DEPTH = 3

    enum class Type { DEVICE, BUILDING, CITY, REGION, COMPANY, PERSON }
    data class Entity(val id: String, val type: Type, val name: String)

    /** All relations are functional (one object per subject at a time); `to == null` means a quantity in `unit`. */
    enum class Relation(val from: Type, val to: Type?, val unit: String? = null) {
        IN_BUILDING(Type.DEVICE, Type.BUILDING), IN_CITY(Type.BUILDING, Type.CITY), IN_REGION(Type.CITY, Type.REGION),
        GRID_VOLTAGE(Type.REGION, null, "V"), MAYOR(Type.CITY, Type.PERSON), OPERATED_BY(Type.BUILDING, Type.COMPANY),
        HQ_CITY(Type.COMPANY, Type.CITY), CEO(Type.COMPANY, Type.PERSON), CAPITAL(Type.REGION, Type.CITY),
        LARGEST_CITY(Type.REGION, Type.CITY)
    }
    enum class Polarity { ASSERTED, NEGATED }
    enum class SourceStatus { TRUSTED, REVOKED }

    /** `validTo` is exclusive. The seal covers exactly these fields: it proves identity of the data, not its truth. */
    data class Fact(
        val id: String, val source: String, val subject: String, val relation: Relation, val objectId: String?,
        val quantity: Long?, val polarity: Polarity, val validFrom: LocalDate?, val validTo: LocalDate?, val seal: String
    ) {
        /** Entity id, or the quantity with its unit. */
        val value: String get() = objectId ?: "$quantity ${relation.unit}"
        fun validAt(day: LocalDate) = (validFrom == null || !day.isBefore(validFrom)) && (validTo == null || day.isBefore(validTo))
        fun sealValid() = seal == sealOf(id, source, subject, relation, objectId, quantity, polarity, validFrom, validTo)

        companion object {
            fun sealOf(id: String, source: String, subject: String, relation: Relation, objectId: String?, quantity: Long?,
                       polarity: Polarity, validFrom: LocalDate?, validTo: LocalDate?): String =
                sha256(listOf(id, source, subject, relation.name, objectId ?: "", quantity?.toString() ?: "",
                    polarity.name, validFrom?.toString() ?: "", validTo?.toString() ?: "").joinToString("\u001f"))

            fun of(id: String, source: String, subject: String, relation: Relation, objectId: String? = null,
                   quantity: Long? = null, polarity: Polarity = Polarity.ASSERTED,
                   validFrom: LocalDate? = null, validTo: LocalDate? = null): Fact {
                require((objectId == null) != (quantity == null) && (relation.to == null) == (quantity != null))
                return Fact(id, source, subject, relation, objectId, quantity, polarity, validFrom, validTo,
                    sealOf(id, source, subject, relation, objectId, quantity, polarity, validFrom, validTo))
            }
        }
    }

    class KnowledgeBase(val entities: List<Entity>, val facts: List<Fact>, val sources: Map<String, SourceStatus>) {
        val entityById: Map<String, Entity> = entities.associateBy { it.id }
        init { require(entityById.size == entities.size && facts.map { it.id }.toSet().size == facts.size) }
        fun display(value: String?): String? = value?.let { entityById[it]?.name ?: it }
    }

    enum class QuestionType(val pattern: List<Relation>, val check: Boolean = false) {
        CITY_OF_DEVICE(listOf(Relation.IN_BUILDING, Relation.IN_CITY)),
        MAYOR_AT_BUILDING(listOf(Relation.IN_CITY, Relation.MAYOR)),
        REGION_OF_DEVICE(listOf(Relation.IN_BUILDING, Relation.IN_CITY, Relation.IN_REGION)),
        VOLTAGE_AT_BUILDING(listOf(Relation.IN_CITY, Relation.IN_REGION, Relation.GRID_VOLTAGE)),
        CEO_FOR_DEVICE(listOf(Relation.IN_BUILDING, Relation.OPERATED_BY, Relation.CEO)),
        CAPITAL_FOR_BUILDING(listOf(Relation.IN_CITY, Relation.IN_REGION, Relation.CAPITAL)),
        CHECK_VOLTAGE_AT_BUILDING(listOf(Relation.IN_CITY, Relation.IN_REGION, Relation.GRID_VOLTAGE), check = true),
        CHECK_CITY_OF_DEVICE(listOf(Relation.IN_BUILDING, Relation.IN_CITY), check = true)
    }

    /** A typed question: the asked thing as written, the reference date and, for checks, the claim (maybe negated). */
    data class Query(val type: QuestionType, val mention: String, val asOf: LocalDate, val claimedName: String? = null,
                     val claimedQuantity: Long? = null, val negated: Boolean = false)

    enum class Kind { VALUE, YES, NO, ABSTAIN }

    /** VALUE/YES/NO carry the derived value (entity id or "230 V") and the derivation (fact ids in hop order). */
    data class Answer(val kind: Kind, val value: String?, val derivation: List<String>, val reason: String? = null) {
        companion object { fun abstain(reason: String) = Answer(Kind.ABSTAIN, null, emptyList(), reason) }
    }

    interface Solver { val name: String; fun answer(q: Query): Answer }

    /** Admission of a fact for L and the latent variants (the verifier has its own code for this). */
    fun admissible(kb: KnowledgeBase, f: Fact, day: LocalDate) =
        f.sealValid() && kb.sources[f.source] == SourceStatus.TRUSTED && f.validAt(day)

    /** Final value and check verdict; the last relation must yield the type (or unit) the question asks for. */
    fun conclude(q: Query, kb: KnowledgeBase, last: Fact, derivation: List<String>): Answer {
        val target = q.type.pattern.last()
        if (last.relation.to != target.to || last.relation.unit != target.unit) return Answer.abstain("TYPE_MISMATCH")
        if (!q.type.check) return Answer(Kind.VALUE, last.value, derivation)
        val holds = when {
            q.claimedQuantity != null -> last.quantity == q.claimedQuantity
            q.claimedName != null -> kb.entityById[last.objectId]?.name.equals(q.claimedName.trim(), ignoreCase = true)
            else -> return Answer.abstain("NO_CLAIM")
        }
        return Answer(if (holds != q.negated) Kind.YES else Kind.NO, last.value, derivation)
    }

    /** L: ids, admissible facts only, every hop unique (rival objects or an admissible negation = contradiction). */
    class Reasoner(private val kb: KnowledgeBase) : Solver {
        override val name = "L"
        private val byName = kb.entities.groupBy { it.name.trim().lowercase() }
        private val bySubject = kb.facts.groupBy { it.subject to it.relation }

        fun start(q: Query): Pair<String?, String?> {
            val found = byName[q.mention.trim().lowercase()].orEmpty().filter { it.type == q.type.pattern.first().from }
            return when (found.size) { 0 -> null to "UNKNOWN_ENTITY"; 1 -> found.single().id to null; else -> null to "AMBIGUOUS_ENTITY" }
        }

        /** Relations with at least one admissible asserted fact at `node` (used by Z-rel). */
        fun relationsAt(node: String, day: LocalDate): List<Relation> = Relation.entries.filter { relation ->
            bySubject[node to relation].orEmpty().any { it.polarity == Polarity.ASSERTED && admissible(kb, it, day) }
        }

        /** One hop. `prune` (Z-beam) may cut the admissible asserted candidates before the uniqueness check. */
        fun step(node: String, relation: Relation, day: LocalDate, prune: ((List<Fact>) -> List<Fact>)? = null): Pair<Fact?, String?> {
            val all = bySubject[node to relation].orEmpty()
            val admitted = all.filter { admissible(kb, it, day) }
            var asserted = admitted.filter { it.polarity == Polarity.ASSERTED }
            if (asserted.isEmpty()) return null to (if (all.isEmpty()) "MISSING_LINK" else "NO_ADMISSIBLE_FACT")
            if (prune != null) asserted = prune(asserted)
            if (asserted.map { it.value }.distinct().size > 1) return null to "CONTRADICTION"
            val fact = asserted.minBy { it.id }
            if (admitted.any { it.polarity == Polarity.NEGATED && it.value == fact.value }) return null to "NEGATED_BY_SOURCE"
            if (relation.to != null && kb.entityById[fact.objectId]?.type != relation.to) return null to "TYPE_MISMATCH"
            return fact to null
        }

        override fun answer(q: Query): Answer = follow(q, null)

        /** The question's fixed relation sequence; `prune` receives the candidates and the previous relation. */
        fun follow(q: Query, prune: ((List<Fact>, Relation?) -> List<Fact>)?): Answer {
            val relations = q.type.pattern
            require(relations.size in 1..MAX_DEPTH)
            val (startId, why) = start(q)
            if (startId == null) return Answer.abstain(why!!)
            var node: String = startId
            val derivation = mutableListOf<String>()
            var last: Fact? = null
            var previous: Relation? = null
            for ((i, relation) in relations.withIndex()) {
                val (fact, reason) = step(node, relation, q.asOf, prune?.let { p -> { c: List<Fact> -> p(c, previous) } })
                if (fact == null) return Answer.abstain(reason!!)
                derivation += fact.id
                last = fact
                previous = relation
                if (i < relations.lastIndex) node = fact.objectId ?: return Answer.abstain("TYPE_MISMATCH")
            }
            return conclude(q, kb, last!!, derivation)
        }
    }

    /**
     * Independent check of a claimed answer. Deliberately shares no index or helper with the reasoners: it scans the
     * raw entity and fact lists, recomputes seals with its own code and re-derives every hop of the claim.
     */
    class Verifier(private val kb: KnowledgeBase) {
        fun confirms(q: Query, a: Answer): Boolean {
            if (a.kind == Kind.ABSTAIN) return true
            val pattern = q.type.pattern
            if (a.derivation.size != pattern.size) return false
            val starts = kb.entities.filter { it.type == pattern[0].from && it.name.trim().equals(q.mention.trim(), ignoreCase = true) }
            if (starts.size != 1) return false
            var node = starts[0].id
            var last: Fact? = null
            for ((i, id) in a.derivation.withIndex()) {
                val matches = kb.facts.filter { it.id == id }
                if (matches.size != 1) return false
                val f = matches[0]
                if (f.relation != pattern[i] || f.subject != node || f.polarity != Polarity.ASSERTED || !admitted(f, q.asOf)) return false
                for (rival in kb.facts) {
                    if (rival.subject != node || rival.relation != f.relation || !admitted(rival, q.asOf)) continue
                    if (rival.polarity == Polarity.ASSERTED && valueOf(rival) != valueOf(f)) return false
                    if (rival.polarity == Polarity.NEGATED && valueOf(rival) == valueOf(f)) return false
                }
                val wanted = pattern[i].to
                if (wanted != null && kb.entities.none { it.id == f.objectId && it.type == wanted }) return false
                if (i < pattern.lastIndex) node = f.objectId ?: return false else last = f
            }
            val fin = last ?: return false
            if (a.value != valueOf(fin)) return false
            if (!q.type.check) return a.kind == Kind.VALUE
            val holds = if (q.claimedQuantity != null) fin.quantity == q.claimedQuantity
                else q.claimedName != null &&
                    kb.entities.any { it.id == fin.objectId && it.name.trim().equals(q.claimedName.trim(), ignoreCase = true) }
            return a.kind == (if (holds xor q.negated) Kind.YES else Kind.NO)
        }

        private fun valueOf(f: Fact) = if (f.objectId != null) f.objectId else f.quantity.toString() + " " + f.relation.unit
        private fun admitted(f: Fact, day: LocalDate): Boolean {
            val text = listOf(f.id, f.source, f.subject, f.relation.name, f.objectId ?: "", f.quantity?.toString() ?: "",
                f.polarity.name, f.validFrom?.toString() ?: "", f.validTo?.toString() ?: "").joinToString("\u001f")
            val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            val seal = StringBuilder(64).apply { for (b in digest) append(String.format("%02x", b.toInt() and 0xff)) }.toString()
            if (seal != f.seal || kb.sources[f.source] != SourceStatus.TRUSTED) return false
            val from = f.validFrom
            val to = f.validTo
            return (from == null || from <= day) && (to == null || day < to)
        }
    }

    /** L + V: an answer counts only if the independent verifier confirms it. */
    class VerifiedReasoner(private val reasoner: Reasoner, private val verifier: Verifier) : Solver {
        override val name = "L+V"
        override fun answer(q: Query): Answer {
            val a = reasoner.answer(q)
            return if (verifier.confirms(q, a)) a else Answer.abstain("VERIFIER_REJECTED")
        }
    }

    /**
     * B0: the walk of EvidenceBoundedThoughtSearch.solve on the same composition rules. Its data model has names as
     * nodes and no time, source status or negation: facts with an invalid seal and negated facts cannot be imported;
     * validity dates and the source register are not part of it. Values are names. Indexed by subject name (the
     * original filters a list of at most 64 edges), which changes speed, not results.
     */
    class EbtsAlgorithm(private val kb: KnowledgeBase) : Solver {
        override val name = "B0"
        private data class Edge(val id: String, val subject: String, val relation: Relation, val target: String, val quantity: Long?)
        private val bySubject = kb.facts.filter { it.sealValid() && it.polarity == Polarity.ASSERTED }
            .map { Edge(it.id, kb.entityById.getValue(it.subject).name, it.relation,
                it.objectId?.let { o -> kb.entityById.getValue(o).name } ?: it.value, it.quantity) }
            .groupBy { it.subject.lowercase() to it.relation }

        override fun answer(q: Query): Answer {
            val sequence = q.type.pattern
            val matches = mutableListOf<Pair<Edge, List<String>>>()
            var ambiguousPrefix = false
            fun walk(subject: String, at: Int, ids: List<String>, visited: Set<String>, via: Edge?) {
                if (at == sequence.size) { matches.add(via!! to ids); return }
                val outgoing = bySubject[subject.lowercase() to sequence[at]].orEmpty()
                if (outgoing.map { it.target.lowercase() }.distinct().size > 1) ambiguousPrefix = true
                for (edge in outgoing) if (edge.target.lowercase() !in visited)
                    walk(edge.target, at + 1, ids + edge.id, visited + edge.target.lowercase(), edge)
            }
            walk(q.mention.trim(), 0, emptyList(), setOf(q.mention.trim().lowercase()), null)
            if (ambiguousPrefix || matches.isEmpty() || matches.map { it.first.target.lowercase() }.distinct().size != 1)
                return Answer.abstain("NO_UNIQUE_PATH")
            val (edge, ids) = matches.sortedWith(compareBy<Pair<Edge, List<String>>> { it.second.size }
                .thenBy { it.second.joinToString("|") }).first()
            if (!q.type.check) return Answer(Kind.VALUE, edge.target, ids)
            val holds = when {
                q.claimedQuantity != null -> edge.quantity == q.claimedQuantity
                q.claimedName != null -> edge.target.equals(q.claimedName.trim(), ignoreCase = true)
                else -> return Answer.abstain("NO_CLAIM")
            }
            return Answer(if (holds != q.negated) Kind.YES else Kind.NO, edge.target, ids)
        }
    }

    /** 16×16 fast weights with the update rule of EphemeralFastWeightCore; embeddings are seeded unit vectors. */
    class FastWeightGuide(private val seed: Long) {
        private val rnd = Random(seed)
        private fun unit(r: Random = rnd): DoubleArray = normalize(DoubleArray(DIM) { r.nextGaussian() })
        val questionVector: Map<QuestionType, DoubleArray> = QuestionType.entries.associateWith { unit() }
        val relationVector: Map<Relation, DoubleArray> = Relation.entries.associateWith { unit() }
        val startVector = unit()
        val stopVector = unit()
        private val weights = DoubleArray(DIM * DIM)
        var updates = 0
            private set

        fun state(q: QuestionType, previous: Relation?): DoubleArray =
            normalize(add(questionVector.getValue(q), previous?.let(relationVector::getValue) ?: startVector))

        fun predict(x: DoubleArray): DoubleArray = DoubleArray(DIM) { r -> (0 until DIM).sumOf { c -> weights[r * DIM + c] * x[c] } }

        /** L = ½‖W·x − y‖² + 0.001‖W‖², rate 0.08, error clamp ±2, Frobenius norm ≤ 2.5 (EphemeralFastWeightCore). */
        fun learn(x: DoubleArray, y: DoubleArray) {
            val next = weights.copyOf()
            for (r in 0 until DIM) {
                var prediction = 0.0
                for (c in 0 until DIM) prediction += weights[r * DIM + c] * x[c]
                val error = (prediction - y[r]).coerceIn(-2.0, 2.0)
                for (c in 0 until DIM) {
                    val i = r * DIM + c
                    next[i] = weights[i] - 0.08 * (error * x[c] + 0.002 * weights[i])
                }
            }
            val norm = sqrt(next.sumOf { it * it })
            check(norm.isFinite())
            if (norm > MAX_NORM) for (i in next.indices) next[i] *= MAX_NORM / norm
            next.copyInto(weights)
            updates++
        }

        fun weightNorm(): Double = sqrt(weights.sumOf { it * it })

        /** Gold relation sequences as transitions (question, previous relation) -> next relation, ending in STOP. */
        fun train(questions: List<QuestionType>, epochs: Int = 50) {
            val transitions = questions.flatMap { q ->
                val p = q.pattern
                p.indices.map { i -> state(q, if (i == 0) null else p[i - 1]) to relationVector.getValue(p[i]) } +
                    (state(q, p.last()) to stopVector)
            }
            repeat(epochs) { transitions.forEach { (x, y) -> learn(x, y) } }
        }

        /** Diagnostic: share of the gold transitions whose most similar target (all relations and STOP) is right. */
        fun transitionAccuracy(questions: List<QuestionType>): Double {
            val targets = relationVector.values + listOf(stopVector)
            val pairs = questions.distinct().flatMap { q ->
                val p = q.pattern
                p.indices.map { i -> state(q, if (i == 0) null else p[i - 1]) to relationVector.getValue(p[i]) } +
                    (state(q, p.last()) to stopVector)
            }
            return pairs.count { (x, y) -> val pr = predict(x); targets.maxBy { cosine(pr, it) } === y }.toDouble() / pairs.size
        }

        /** Seeded object embedding for Z-beam scores (fixed per value and seed). */
        fun objectVector(value: String): DoubleArray = unit(Random(seed * 1_000_003L + value.hashCode()))

        companion object {
            const val DIM = 16
            const val MAX_NORM = 2.5
            fun normalize(v: DoubleArray): DoubleArray { val n = sqrt(v.sumOf { it * it }); return DoubleArray(v.size) { v[it] / n } }
            fun add(a: DoubleArray, b: DoubleArray) = DoubleArray(a.size) { a[it] + b[it] }
            fun cosine(a: DoubleArray, b: DoubleArray): Double {
                val dot = a.indices.sumOf { a[it] * b[it] }
                val n = sqrt(a.sumOf { it * it }) * sqrt(b.sumOf { it * it })
                return if (n == 0.0) 0.0 else dot / n
            }
        }
    }

    /** Z-rel: the latent state picks the next relation among those available (STOP ends); all other checks as L. */
    class LatentRelationReasoner(private val kb: KnowledgeBase, private val guide: FastWeightGuide, private val base: Reasoner) : Solver {
        override val name = "Z-rel"
        override fun answer(q: Query): Answer {
            val (startId, why) = base.start(q)
            if (startId == null) return Answer.abstain(why!!)
            var node: String? = startId
            var previous: Relation? = null
            var last: Fact? = null
            val derivation = mutableListOf<String>()
            for (hop in 0 until MAX_DEPTH) {
                val at = node ?: break
                val prediction = guide.predict(guide.state(q.type, previous))
                val best = base.relationsAt(at, q.asOf)
                    .maxByOrNull { FastWeightGuide.cosine(prediction, guide.relationVector.getValue(it)) } ?: break
                if (FastWeightGuide.cosine(prediction, guide.stopVector) >=
                    FastWeightGuide.cosine(prediction, guide.relationVector.getValue(best))) break
                val (fact, reason) = base.step(at, best, q.asOf)
                if (fact == null) return Answer.abstain(reason!!)
                derivation += fact.id
                last = fact
                previous = best
                node = fact.objectId
            }
            return last?.let { conclude(q, kb, it, derivation) } ?: Answer.abstain("NO_PATH")
        }
    }

    /** Z-beam: fixed composition as L, but each hop keeps only the best latent-scored candidate (k = 1). */
    class LatentBeamReasoner(private val guide: FastWeightGuide, private val base: Reasoner) : Solver {
        override val name = "Z-beam"
        override fun answer(q: Query): Answer = base.follow(q) { candidates, previous ->
            val prediction = guide.predict(guide.state(q.type, previous))
            listOf(candidates.sortedBy { it.id }.maxBy { f ->
                FastWeightGuide.cosine(prediction,
                    FastWeightGuide.normalize(FastWeightGuide.add(guide.relationVector.getValue(f.relation), guide.objectVector(f.value))))
            })
        }
    }

}
