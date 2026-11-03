package at.heinz.deviceai.chief

import at.heinz.deviceai.cognitive.MemoryRecord

/** RED stub. */
data class ChiefMemoryItem(val id: String, val text: String, val atMillis: Long, val confirmed: Boolean)
interface ChiefMemoryPort {
    fun sharedFacts(): List<ChiefMemoryItem>
    fun search(query: String, nowMillis: Long, limit: Int): List<ChiefMemoryItem>
}
object ChiefMemory { fun recall(port: ChiefMemoryPort, task: String, nowMillis: Long, limit: Int = 5): List<MemoryRecord> = emptyList() }
