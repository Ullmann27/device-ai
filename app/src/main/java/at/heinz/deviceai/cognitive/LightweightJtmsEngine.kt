package at.heinz.deviceai.cognitive

/**
 * Bounded justification-based truth maintenance for already-identified claim IDs.
 * This engine never invents premises. It only propagates active/inactive state over explicit rules.
 */
internal class LightweightJtmsEngine(
    private val maxRules: Int = 512,
    private val maxFacts: Int = 2_048
) {
    enum class TruthState {
        VALID_FACT,
        VALID_DERIVED,
        INVALIDATED_DEPENDENCY,
        BLOCKED_BY_EXCEPTION
    }

    data class Rule(
        val conclusionId: String,
        val requiredAllActive: Set<String>,
        val blockedIfAnyActive: Set<String> = emptySet()
    ) {
        init {
            require(ID.matches(conclusionId))
            require(requiredAllActive.isNotEmpty() && requiredAllActive.size <= 32)
            require(blockedIfAnyActive.size <= 32)
            require((requiredAllActive + blockedIfAnyActive).all(ID::matches))
            require(conclusionId !in requiredAllActive && conclusionId !in blockedIfAnyActive)
        }

        companion object {
            private val ID = Regex("[A-Za-z0-9._:-]{1,128}")
        }
    }

    private val facts = LinkedHashSet<String>()
    private val rules = LinkedHashMap<String, Rule>()
    private var states: Map<String, TruthState> = emptyMap()

    fun setFact(id: String, active: Boolean) {
        require(ID.matches(id))
        if (active) {
            require(facts.size < maxFacts || id in facts) { "JTMS fact capacity reached" }
            facts += id
        } else facts -= id
        recompute()
    }

    fun addRule(rule: Rule) {
        require(rules.size < maxRules || rule.conclusionId in rules) { "JTMS rule capacity reached" }
        val existing = rules[rule.conclusionId]
        require(existing == null || existing == rule) { "JTMS conclusion already has a different rule" }
        rules[rule.conclusionId] = rule
        recompute()
    }

    fun removeRule(conclusionId: String): Boolean {
        val removed = rules.remove(conclusionId) != null
        if (removed) recompute()
        return removed
    }

    fun state(id: String): TruthState =
        states[id] ?: TruthState.INVALIDATED_DEPENDENCY

    fun snapshot(): Map<String, TruthState> = LinkedHashMap(states)

    private fun recompute() {
        val current = LinkedHashMap<String, TruthState>()
        facts.forEach { current[it] = TruthState.VALID_FACT }
        rules.keys.forEach { if (it !in current) current[it] = TruthState.INVALIDATED_DEPENDENCY }

        // Positive dependencies are monotone within one recompute. Exception claims may become
        // active in later rounds, so a conclusion can move VALID_DERIVED -> BLOCKED_BY_EXCEPTION.
        val maxRounds = rules.size * 2 + 4
        var stable = false
        repeat(maxRounds) {
            val next = LinkedHashMap(current)
            for ((id, rule) in rules) {
                if (id in facts) continue
                val required = rule.requiredAllActive.all { dep ->
                    current[dep] in ACTIVE_STATES
                }
                val blocked = rule.blockedIfAnyActive.any { dep ->
                    current[dep] in ACTIVE_STATES
                }
                next[id] = when {
                    blocked -> TruthState.BLOCKED_BY_EXCEPTION
                    required -> TruthState.VALID_DERIVED
                    else -> TruthState.INVALIDATED_DEPENDENCY
                }
            }
            if (next == current) {
                stable = true
                states = next
                return
            }
            current.clear()
            current.putAll(next)
        }

        // A negative-dependency cycle can oscillate. Fail closed instead of picking an arbitrary round.
        if (!stable) {
            val safe = LinkedHashMap<String, TruthState>()
            facts.forEach { safe[it] = TruthState.VALID_FACT }
            rules.keys.forEach { if (it !in safe) safe[it] = TruthState.INVALIDATED_DEPENDENCY }
            states = safe
        }
    }

    companion object {
        private val ID = Regex("[A-Za-z0-9._:-]{1,128}")
        private val ACTIVE_STATES = setOf(TruthState.VALID_FACT, TruthState.VALID_DERIVED)
    }
}
