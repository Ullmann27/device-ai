package at.heinz.deviceai.learning.context

/** Bounded discourse memory for reference resolution only. It stores entity ids, never facts or tool authority. */
internal class Ctx3DiscourseState(private val maxFrames: Int = 8) {
    init { require(maxFrames in 2..16) }

    enum class Cue { PREVIOUS, RETURN_PREVIOUS, ALTERNATIVE }

    data class Binding(val entity: Ctx3Entity, val cue: Cue)

    private data class Frame(val entityIds: List<String>, val turn: Int)

    private val frames = ArrayDeque<Frame>()
    private var turn = 0
    private var alternatives: List<String> = emptyList()
    private var selectedAlternativeId: String? = null

    fun reset() {
        frames.clear()
        alternatives = emptyList()
        selectedAlternativeId = null
        turn = 0
    }

    fun nextTurn() { turn++ }

    fun observeExplicit(entities: List<Ctx3Entity>) {
        val ids = entities.map { it.id }.distinct().take(8)
        if (ids.isEmpty()) return
        when {
            ids.size >= 2 -> {
                alternatives = ids
                selectedAlternativeId = null
            }
            ids.single() in alternatives -> selectedAlternativeId = ids.single()
            else -> {
                alternatives = emptyList()
                selectedAlternativeId = null
            }
        }
        remember(ids)
    }

    fun observeResolved(entity: Ctx3Entity) {
        if (entity.id in alternatives) selectedAlternativeId = entity.id
        remember(listOf(entity.id))
    }

    fun resolve(input: String, currentFocus: List<Ctx3Entity>, entities: List<Ctx3Entity>): Binding? {
        val normalized = input.lowercase()
            .replace('ä', 'a').replace('ö', 'o').replace('ü', 'u').replace('ß', 's')
            .replace(Regex("[^\p{L}\p{N}]+"), " ").trim()

        val alternativeCue = Regex("\b(?:das andere|die andere|den anderen|dem anderen|das zweite|die zweite)\b")
            .containsMatchIn(normalized)
        if (alternativeCue) {
            val selected = selectedAlternativeId ?: currentFocus.singleOrNull()?.id ?: return null
            val otherIds = alternatives.filter { it != selected }
            if (alternatives.size == 2 && otherIds.size == 1) {
                entities.firstOrNull { it.id == otherIds.single() }?.let { return Binding(it, Cue.ALTERNATIVE) }
            }
            return null
        }

        val previousCue = Regex("\b(?:davor|vorhin|zuvor|vorher)\b").containsMatchIn(normalized)
        val returnCue = Regex("\b(?:wieder zuruck|zuruck dazu|zuruck dorthin|nochmal dazu|noch einmal dazu)\b")
            .containsMatchIn(normalized)
        if (!previousCue && !returnCue) return null

        val currentIds = currentFocus.mapTo(hashSetOf()) { it.id }
        val previous = frames.toList().asReversed().firstOrNull { frame ->
            frame.entityIds.isNotEmpty() && frame.entityIds.toSet() != currentIds
        } ?: return null
        if (previous.entityIds.size != 1) return null
        val target = entities.firstOrNull { it.id == previous.entityIds.single() } ?: return null
        return Binding(target, if (returnCue) Cue.RETURN_PREVIOUS else Cue.PREVIOUS)
    }

    fun hasDiscourseCue(input: String): Boolean {
        val normalized = input.lowercase()
            .replace('ä', 'a').replace('ö', 'o').replace('ü', 'u').replace('ß', 's')
            .replace(Regex("[^\p{L}\p{N}]+"), " ").trim()
        return Regex("\b(?:davor|vorhin|zuvor|vorher|das andere|die andere|den anderen|dem anderen|das zweite|die zweite|wieder zuruck|zuruck dazu|zuruck dorthin|nochmal dazu|noch einmal dazu)\b")
            .containsMatchIn(normalized)
    }

    internal fun frameCount(): Int = frames.size

    private fun remember(ids: List<String>) {
        if (frames.lastOrNull()?.entityIds == ids) {
            frames.removeLast()
        }
        frames.addLast(Frame(ids, turn))
        while (frames.size > maxFrames) frames.removeFirst()
    }
}
