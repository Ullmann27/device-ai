package at.heinz.deviceai.learning

import java.time.Instant
import java.util.Locale

enum class JournalStatus { CONFIRMED, UNCONFIRMED, REVOKED }
data class ProfileFact(val id:String, val text:String, val sinceMillis:Long)
data class JournalEntry(val id:String, val text:String, val atMillis:Long, val status:JournalStatus)
data class MemorySearchHit(val entry:JournalEntry, val score:Double) { val confirmed get() = entry.status == JournalStatus.CONFIRMED }
data class MemorySearchResult(val query:String, val hits:List<MemorySearchHit>, val abstained:Boolean)

/** Read-only view handed to specialists/agents: no mutation methods by design (asserted in tests). */
interface SharedOwnerKnowledge {
    fun facts():List<ProfileFact>
    fun answer(question:String, nowMillis:Long):TemporalAnswer?
}

/**
 * Chief pattern point 4 — memory layers as a thin, read-only facade over the EXISTING LEX2 ledger and the
 * temporal memory view (no new store, no format change):
 *  - [profile]: durable confirmed owner facts (= [ContinuumLearningCore.activeOwnerFacts]);
 *  - [journal]: dated history of all owner statements with honest status (confirmed / unconfirmed / revoked);
 *  - [shared]: immutable snapshot for all specialists/agents, answers only from confirmed facts;
 *  - [search]: keyword + time ranking over older entries; abstains when nothing matches.
 * Hypotheticals/plans stay UNCONFIRMED in every layer.
 */
class ContinuumMemoryLayers(private val core:ContinuumLearningCore) {
    fun profile():List<ProfileFact> = core.activeOwnerFacts().map { ProfileFact(it.id, it.content, it.createdAt) }

    /** Owner statements with createdAt in [fromMillis, toMillis), oldest first. */
    fun journal(fromMillis:Long = Long.MIN_VALUE, toMillis:Long = Long.MAX_VALUE):List<JournalEntry> =
        core.ownerStatementHistory().filter { (e, _) -> e.createdAt >= fromMillis && e.createdAt < toMillis }.map { (e, ok) ->
            JournalEntry(e.id, e.content, e.createdAt, when {
                e.status == LearningStatus.REVOKED || e.status == LearningStatus.REJECTED -> JournalStatus.REVOKED
                !ok -> JournalStatus.UNCONFIRMED
                else -> JournalStatus.CONFIRMED
            })
        }

    fun shared():SharedOwnerKnowledge {
        val facts = profile()
        val view = core.temporalMemoryView()
        return object:SharedOwnerKnowledge {
            override fun facts() = facts
            override fun answer(question:String, nowMillis:Long) = view.answer(question, nowMillis)
        }
    }

    fun search(query:String, nowMillis:Long, limit:Int = 5):MemorySearchResult {
        require(limit in 1..50)
        val q = tokens(query)
        if (q.isEmpty()) return MemorySearchResult(query, emptyList(), true)
        val entries = journal()
        val oldest = entries.minOfOrNull { it.atMillis } ?: 0L
        val span = (nowMillis - oldest).coerceAtLeast(1L).toDouble()
        val hits = entries.mapNotNull { e ->
            val words = tokens(e.text)
            val year = Instant.ofEpochMilli(e.atMillis).atZone(ContinuumTemporalMemory.VIENNA).year.toString()
            val matched = q.count { t -> if (YEAR.matches(t)) t == year || t in words else words.any { stemMatch(t, it) } }
            if (matched == 0) null
            else MemorySearchHit(e, matched.toDouble() / q.size + 0.05 * ((e.atMillis - oldest) / span).coerceIn(0.0, 1.0))
        }.sortedWith(compareByDescending<MemorySearchHit> { it.score }.thenByDescending { it.entry.atMillis }).take(limit)
        return MemorySearchResult(query, hits, hits.isEmpty())
    }

    private companion object {
        val YEAR = Regex("(?:19|20)\\d{2}")
        val SPLIT = Regex("[^\\p{L}\\p{N}]+")
        val STOP = setOf("wie", "was", "wo", "wer", "wann", "warum", "mein", "meine", "meinen", "meinem", "meiner", "ich",
            "du", "war", "ist", "bin", "sind", "nochmal", "mit", "heißt", "hieß", "heiße", "heisst", "habe", "hat", "der",
            "die", "das", "den", "dem", "und", "oder", "noch", "mal", "eine", "einen", "ein", "bei", "nach", "von", "seit",
            "für", "nicht", "auch", "dir", "mir", "uns", "sag", "weißt")
        fun tokens(text:String) = text.lowercase(Locale.GERMAN).split(SPLIT)
            .filter { (it.length >= 3 || YEAR.matches(it)) && it !in STOP }.toSet()
        fun stemMatch(a:String, b:String) = a == b || (a.length >= 4 && b.length >= 4 && a.take(4) == b.take(4))
    }
}
