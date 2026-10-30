package at.heinz.deviceai.cognitive

import at.heinz.deviceai.research.argon4.JustifiedRelationStore

/**
 * M11 · rewrites "der Bruder von Anna" / "die Schwester von Max" in a memory question into the one person the owner's
 * ACTIVE facts make a sibling of that person, using the existing JustifiedRelationStore rules. Only when exactly one
 * candidate exists; otherwise the question stays as it is (no guessing). Fresh store per call: nothing is retained,
 * nothing becomes a fact. The rewritten question is research input for the frozen EWM2 brain (SHADOW).
 */
object MemoryQuestionResolver {
    /** "Anna ist die Schwester von Max" makes Max a sibling of Anna (Max's gender is not inferred). */
    private val SIBLING_INVERSE = listOf("schwester_von", "bruder_von").map { p ->
        JustifiedRelationStore.Rule("$p ⇒ geschwister (umgekehrt)", listOf(JustifiedRelationStore.Triple("?a", p, "?b")),
            JustifiedRelationStore.Triple("?b", "geschwister_von", "?a"), distinct = listOf("?a" to "?b"))
    }
    private val RELATION = Regex("(?iu)\\b(?:der|den|dem|des) Bruder von (\\p{Lu}\\p{L}+)|\\b(?:die|der) Schwester von (\\p{Lu}\\p{L}+)")

    fun resolve(question: String, facts: List<Pair<String, String>>): String? {
        val m = RELATION.find(question) ?: return null
        val anchor = m.groupValues[1].ifEmpty { m.groupValues[2] }
        val store = JustifiedRelationStore()
        // local rules: kept out of kinshipRules() so the existing evidence shadow and its counts stay unchanged
        (JustifiedRelationStore.kinshipRules() + SIBLING_INVERSE).forEach(store::addRule)
        facts.take(200).forEach { (id, text) -> JustifiedRelationStore.parse(text)?.let { store.assertFact(id, it) } }
        val siblings = store.query(p = "geschwister_von", o = anchor).map { it.triple.s }.distinct().filter { it != anchor }
        return if (siblings.size == 1) question.replaceRange(m.range, siblings.single()) else null
    }
}
