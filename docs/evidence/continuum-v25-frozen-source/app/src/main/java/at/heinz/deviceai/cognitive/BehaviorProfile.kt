package at.heinz.deviceai.cognitive

enum class BehaviorTrait {
    PRECISION, CURIOSITY, SKEPTICISM, EXPLANATION_DEPTH, VERIFICATION_PREFERENCE,
    UNCERTAINTY_AWARENESS, CONSENSUS_PREFERENCE, EXPLORATION_RATE, CREATIVITY,
    RISK_AVERSION, COMMUNICATION_DIRECTNESS
}

/** Immutable typed behavior traits, each normalized to 0..1. Starts neutral (0.5). */
class BehaviorProfile private constructor(private val values: Map<BehaviorTrait, Double>) {
    operator fun get(trait: BehaviorTrait): Double = values.getValue(trait)

    fun with(trait: BehaviorTrait, value: Double): BehaviorProfile {
        require(value.isUnit()) { "Trait $trait must be finite within 0..1, was $value" }
        return BehaviorProfile(values + (trait to value))
    }

    fun asMap(): Map<BehaviorTrait, Double> = values

    fun canonical(): String = BehaviorTrait.values().joinToString(",") { "${it.name}=${canon(values.getValue(it))}" }

    override fun equals(other: Any?) = other is BehaviorProfile && other.values == values
    override fun hashCode() = values.hashCode()
    override fun toString() = "BehaviorProfile(${canonical()})"

    companion object {
        const val NEUTRAL = 0.5
        fun neutral() = BehaviorProfile(BehaviorTrait.values().associateWith { NEUTRAL })
        fun of(values: Map<BehaviorTrait, Double>): BehaviorProfile {
            var p = neutral()
            values.forEach { (t, v) -> p = p.with(t, v) }
            return p
        }
    }
}
