package at.heinz.deviceai.cognitive

/**
 * Copy-on-write view for one hypothetical reasoning cycle.
 * It has no write reference to durable memory and cannot commit to actual reality.
 */
internal class CounterfactualWorldOverlay(
    baseReality: Map<String, String>,
    val branchId: String
) {
    init {
        require(branchId.matches(Regex("[A-Za-z0-9._:-]{1,96}")))
        require(baseReality.size <= 4_096)
        require(baseReality.keys.none(String::isBlank))
    }

    enum class Scope { COUNTERFACTUAL_SANDBOX }

    val scope: Scope = Scope.COUNTERFACTUAL_SANDBOX

    private val base = LinkedHashMap(baseReality)
    private val assumptions = LinkedHashMap<String, String>()
    private val retractions = LinkedHashSet<String>()
    private val consequences = LinkedHashMap<String, String>()

    fun assume(key: String, value: String) {
        requireKey(key)
        require(value.isNotBlank() && value.length <= 512)
        retractions -= key
        assumptions[key] = value
        consequences.remove(key)
    }

    fun retract(key: String) {
        requireKey(key)
        assumptions.remove(key)
        consequences.remove(key)
        retractions += key
    }

    fun derive(key: String, value: String) {
        requireKey(key)
        require(value.isNotBlank() && value.length <= 512)
        if (key !in assumptions && key !in retractions) consequences[key] = value
    }

    fun read(key: String): String? {
        requireKey(key)
        if (key in retractions) return null
        return assumptions[key] ?: consequences[key] ?: base[key]
    }

    fun delta(): Map<String, String?> {
        val keys = (assumptions.keys + retractions + consequences.keys).toSortedSet()
        return keys.associateWith { read(it) }
    }

    fun snapshot(): Map<String, String> {
        val keys = (base.keys + assumptions.keys + consequences.keys).toSortedSet()
        return keys.mapNotNull { key -> read(key)?.let { key to it } }.toMap()
    }

    /** Deliberately no commit() method. Only a labelled summary may leave this object. */
    fun labelledSummary(maxEntries: Int = 32): String {
        require(maxEntries in 1..128)
        val entries = delta().entries.take(maxEntries)
        return buildString {
            append("[COUNTERFACTUAL_SANDBOX ").append(branchId).append("]\n")
            entries.forEach { (key, value) ->
                append(key).append('=').append(value ?: "<RETRACTED>").append('\n')
            }
        }.trimEnd()
    }

    private fun requireKey(key: String) {
        require(key.matches(Regex("[A-Za-z0-9._:-]{1,160}")))
    }
}
