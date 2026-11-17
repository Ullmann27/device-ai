package at.heinz.deviceai.chief

import at.heinz.deviceai.cognitive.MemoryRecord
import at.heinz.deviceai.cognitive.MemoryType
import at.heinz.deviceai.cognitive.ProvenanceType
import at.heinz.deviceai.cognitive.ValidationState

/** One memory entry as seen by the chief; `confirmed=false` means hypothesis, never fact. Revoked entries are not passed. */
data class ChiefMemoryItem(val id: String, val text: String, val atMillis: Long, val confirmed: Boolean)

/**
 * Small, stable seam to the Gedächtnis layers (#371 `ContinuumMemoryLayers`):
 * `sharedFacts()` ← `layers.shared().facts()`, `search()` ← `layers.search(query, now, limit)`.
 * Read-only by design; the chief never writes through this port.
 */
interface ChiefMemoryPort {
    fun sharedFacts(): List<ChiefMemoryItem>
    fun search(query: String, nowMillis: Long, limit: Int): List<ChiefMemoryItem>
}

object ChiefMemory {
    /** Maps shared facts + task-relevant search hits to orchestrator memories. Fails closed (empty) on any error. */
    fun recall(port: ChiefMemoryPort, task: String, nowMillis: Long, limit: Int = 5): List<MemoryRecord> {
        val items = runCatching {
            port.sharedFacts().take(20) + port.search(task.take(512), nowMillis, limit.coerceIn(1, 20))
        }.getOrElse { return emptyList() }
        return items.filter { it.id.isNotBlank() && it.text.isNotBlank() }
            .distinctBy { it.id }
            .take(25)
            .mapNotNull { item -> runCatching { toRecord(item, nowMillis) }.getOrNull() }
    }

    private fun toRecord(item: ChiefMemoryItem, nowMillis: Long): MemoryRecord {
        val at = item.atMillis.coerceIn(0, maxOf(0, nowMillis))
        return MemoryRecord(
            id = item.id.take(100),
            type = if (item.confirmed) MemoryType.SEMANTIC else MemoryType.EPISODIC,
            content = item.text.take(1_000),
            provenance = if (item.confirmed) ProvenanceType.USER_PROVIDED else ProvenanceType.UNVERIFIED,
            confidence = if (item.confirmed) 0.9 else 0.3,
            createdAt = at, updatedAt = maxOf(at, nowMillis.coerceAtLeast(0)),
            tags = if (item.confirmed) listOf("gedaechtnis", "bestaetigt") else listOf("gedaechtnis", "hypothese"),
            validationState = if (item.confirmed) ValidationState.CORROBORATED else ValidationState.UNVERIFIED
        )
    }
}
