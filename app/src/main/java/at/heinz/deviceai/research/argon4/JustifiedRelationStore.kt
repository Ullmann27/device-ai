package at.heinz.deviceai.research.argon4

/**
 * ARGON4 F1 · relations between owner facts without turning conclusions into truths (research prototype; integrated SHADOW only).
 *
 * A justification-based truth-maintenance store (Doyle 1979) with why-provenance (Buneman et al. 2001; provenance
 * semirings, Green et al. 2007): every derived relation keeps the set of minimal witness sets of base fact ids that
 * produce it. Revoking a base fact removes every witness containing it; a derivation survives only while one witness
 * remains. Derived relations are always DERIVED_UNCONFIRMED: they may answer "Wer ist Klaras Tante?" with the
 * reasoning chain shown, they never become owner facts, and a contradiction is recorded, not resolved by majority.
 *
 * Targets the EWM1 H2 failure (two-step linking 14/17) without a second memory store: base triples come from the
 * owner facts that ContinuumLearningCore.activeOwnerFacts() already returns.
 */
class JustifiedRelationStore(private val maxDerived: Int = 2_000, private val maxDepth: Int = 3) {
    data class Triple(val s: String, val p: String, val o: String) { override fun toString() = "($s $p $o)" }
    data class Derived(val triple: Triple, val witnesses: Set<Set<String>>, val rule: String, val depth: Int) {
        val status: String get() = "DERIVED_UNCONFIRMED"
    }
    data class Contradiction(val subject: String, val predicate: String, val values: Set<String>, val factIds: Set<String>)
    /** body(?x … ?y) ⇒ head; variables start with '?'. */
    data class Rule(val name: String, val body: List<Triple>, val head: Triple, val distinct: List<Pair<String, String>> = emptyList())

    private val base = LinkedHashMap<String, Triple>()   // fact id -> triple
    private var derived = LinkedHashMap<Triple, Derived>()
    private val rules = ArrayList<Rule>()

    fun addRule(rule: Rule): JustifiedRelationStore {
        require(rule.body.size in 1..3) { "Rules stay small and auditable." }
        val bodyVars = rule.body.flatMap { listOf(it.s, it.p, it.o) }.filter { it.startsWith("?") }.toSet()
        require(listOf(rule.head.s, rule.head.p, rule.head.o).filter { it.startsWith("?") }.all { it in bodyVars }) { "Unsafe rule head." }
        rules += rule; recompute(); return this
    }

    fun assertFact(id: String, t: Triple) { require(id.isNotBlank()); base[id] = t; recompute() }

    /** Owner revoked the fact: all conclusions that needed it disappear in the same step. */
    fun revoke(id: String) { if (base.remove(id) != null) recompute() }

    fun derived(): List<Derived> = derived.values.toList()

    fun query(s: String? = null, p: String? = null, o: String? = null): List<Derived> =
        derived.values.filter { (s == null || it.triple.s == s) && (p == null || it.triple.p == p) && (o == null || it.triple.o == o) }

    /** Functional predicates (one value per subject) whose base facts disagree; reported, never auto-resolved. */
    fun contradictions(functional: Set<String>): List<Contradiction> =
        base.entries.filter { it.value.p in functional }.groupBy { it.value.s to it.value.p }
            .filter { (_, v) -> v.map { it.value.o }.toSet().size > 1 }
            .map { (k, v) -> Contradiction(k.first, k.second, v.map { it.value.o }.toSet(), v.map { it.key }.toSet()) }

    /** Human-readable chain for one derived relation: the shortest witness, by base fact id. */
    fun explain(t: Triple): String? = derived[t]?.let { d ->
        val w = d.witnesses.minByOrNull { it.size }!!
        d.triple.toString() + " weil " + w.sorted().joinToString(" und ") { id -> base.getValue(id).toString() + "[" + id + "]" } +
            " (Regel " + d.rule + ", abgeleitet, nicht bestätigt)"
    }

    // Naive bottom-up fixpoint over witness sets; bounded by depth and size (no runaway on cyclic rules).
    private fun recompute() {
        val facts = HashMap<Triple, MutableSet<Set<String>>>()
        for ((id, t) in base) facts.getOrPut(t) { linkedSetOf() } += setOf(id)
        val result = LinkedHashMap<Triple, Derived>()
        for (depth in 1..maxDepth) {
            var changed = false
            val known = HashMap(facts).apply { result.forEach { (t, d) -> getOrPut(t) { linkedSetOf() }.addAll(d.witnesses) } }
            for (rule in rules) for ((binding, witnesses) in matches(rule.body, known)) {
                if (rule.distinct.any { (x, y) -> binding[x] == binding[y] }) continue
                val head = Triple(sub(rule.head.s, binding), sub(rule.head.p, binding), sub(rule.head.o, binding))
                if (head in facts) continue                      // already an owner fact: nothing to derive
                val old = result[head]
                val merged = minimal((old?.witnesses ?: emptySet()) + witnesses)
                if (old == null || merged != old.witnesses) {
                    check(result.size < maxDerived || old != null) { "Derived relation budget exhausted." }
                    result[head] = Derived(head, merged, rule.name, old?.depth ?: depth)
                    changed = true
                }
            }
            if (!changed) break
        }
        derived = result
    }

    private fun matches(body: List<Triple>, known: Map<Triple, Set<Set<String>>>): List<Pair<Map<String, String>, Set<Set<String>>>> {
        var partial: List<Pair<Map<String, String>, Set<Set<String>>>> = listOf(emptyMap<String, String>() to setOf(emptySet()))
        for (pattern in body) {
            val next = ArrayList<Pair<Map<String, String>, Set<Set<String>>>>()
            for ((b, ws) in partial) for ((t, tw) in known) {
                val nb = unify(pattern, t, b) ?: continue
                next += nb to minimal(ws.flatMap { w -> tw.map { w + it } }.toSet())
            }
            partial = next
            if (partial.isEmpty()) break
        }
        return partial
    }

    private fun unify(p: Triple, t: Triple, b: Map<String, String>): Map<String, String>? {
        val out = HashMap(b)
        for ((pv, tv) in listOf(p.s to t.s, p.p to t.p, p.o to t.o)) {
            if (pv.startsWith("?")) { val cur = out[pv]; if (cur == null) out[pv] = tv else if (cur != tv) return null }
            else if (pv != tv) return null
        }
        return out
    }

    private fun sub(x: String, b: Map<String, String>) = if (x.startsWith("?")) b.getValue(x) else x

    /** Keep only minimal witness sets (a superset adds no new justification). */
    private fun minimal(ws: Set<Set<String>>): Set<Set<String>> =
        ws.filter { w -> ws.none { o -> o != w && w.containsAll(o) } }.toSet()

    companion object {
        /** Small German kinship/place rule base used by the prototype and its tests. */
        fun kinshipRules(): List<Rule> = listOf(
            Rule("geschwister-des-besitzers × kind-des-besitzers ⇒ tante/onkel",
                listOf(Triple("?a", "schwester_von", "BESITZER"), Triple("?k", "kind_von", "BESITZER")), Triple("?a", "tante_von", "?k")),
            Rule("bruder-des-besitzers × kind-des-besitzers ⇒ onkel",
                listOf(Triple("?a", "bruder_von", "BESITZER"), Triple("?k", "kind_von", "BESITZER")), Triple("?a", "onkel_von", "?k")),
            Rule("wohnt-in × liegt-in ⇒ wohnt-in-region",
                listOf(Triple("?x", "wohnt_in", "?c"), Triple("?c", "liegt_in", "?r")), Triple("?x", "wohnt_in", "?r")),
            Rule("kind × kind ⇒ geschwister",
                listOf(Triple("?a", "kind_von", "?p"), Triple("?b", "kind_von", "?p")), Triple("?a", "geschwister_von", "?b"),
                distinct = listOf("?a" to "?b"))
        )

        private val SISTER = Regex("^(\\p{Lu}\\p{L}+) ist meine Schwester\\.?$")
        private val BROTHER = Regex("^(\\p{Lu}\\p{L}+) ist mein Bruder\\.?$")
        private val CHILD = Regex("^Meine (?:Tochter|Sohn) heißt (\\p{Lu}\\p{L}+)\\.?$|^Mein Sohn heißt (\\p{Lu}\\p{L}+)\\.?$")
        private val LIVES = Regex("^(\\p{Lu}\\p{L}+) wohnt in (\\p{Lu}\\p{L}+)\\.?$")
        private val LIES = Regex("^(\\p{Lu}\\p{L}+) liegt in (\\p{Lu}\\p{L}+)\\.?$")
        private val SISTER_OF = Regex("^(\\p{Lu}\\p{L}+) ist die Schwester von (\\p{Lu}\\p{L}+)\\.?$")
        private val BROTHER_OF = Regex("^(\\p{Lu}\\p{L}+) ist der Bruder von (\\p{Lu}\\p{L}+)\\.?$")

        /** Deliberately narrow sentence patterns; anything else is not turned into a triple (no guessing). */
        fun parse(text: String): Triple? {
            val t = text.trim()
            SISTER.matchEntire(t)?.let { return Triple(it.groupValues[1], "schwester_von", "BESITZER") }
            BROTHER.matchEntire(t)?.let { return Triple(it.groupValues[1], "bruder_von", "BESITZER") }
            CHILD.matchEntire(t)?.let { m -> val n = m.groupValues[1].ifEmpty { m.groupValues[2] }; return Triple(n, "kind_von", "BESITZER") }
            LIVES.matchEntire(t)?.let { return Triple(it.groupValues[1], "wohnt_in", it.groupValues[2]) }
            LIES.matchEntire(t)?.let { return Triple(it.groupValues[1], "liegt_in", it.groupValues[2]) }
            SISTER_OF.matchEntire(t)?.let { return Triple(it.groupValues[1], "schwester_von", it.groupValues[2]) }
            BROTHER_OF.matchEntire(t)?.let { return Triple(it.groupValues[1], "bruder_von", it.groupValues[2]) }
            return null
        }
    }
}
