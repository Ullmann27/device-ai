package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.ContinuumMemoryEventHub
import at.heinz.deviceai.learning.RecallHit
import at.heinz.deviceai.learning.RecallHits
import java.io.File
import java.util.Collections

/**
 * Observation-only bridge from the actual context compiler to the existing
 * per-application memory hub. Raw query text is used only to compute its salted
 * hash; memory bodies and source text never enter the presentation event.
 */
class ContinuumMemoryRecallPublisher(appDirectory: File) : (String, List<RecallHit>, Long) -> Unit {
    private val hub = ContinuumMemoryEventHub.forApp(appDirectory)

    override fun invoke(query: String, hits: List<RecallHit>, atEpochMs: Long) {
        if (!hub.hasListeners()) return
        require(atEpochMs >= 0L)
        require(hits.size <= 32) { "Context recall exceeds the compiler memory limit" }
        require(hits.map { it.entryId }.distinct().size == hits.size)
        require(hits.withIndex().all { (index, hit) ->
            hit.entryId.isNotBlank() && hit.rank == index + 1 && hit.score.isFinite() && hit.score >= 0f
        })
        // A listener must not be able to alter the next listener's observation.
        val snapshot = Collections.unmodifiableList(ArrayList(hits))
        hub.emit(RecallHits(hub.queryHash(query), snapshot, atEpochMs))
    }
}
