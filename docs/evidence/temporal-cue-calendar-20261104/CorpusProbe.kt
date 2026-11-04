import at.heinz.deviceai.learning.CharNgramHashRetriever
import at.heinz.deviceai.learning.ContinuumRetrievalExperimentTest
import at.heinz.deviceai.learning.MemoryDoc
import at.heinz.deviceai.learning.TemporalCue
import java.time.Instant
import java.time.ZoneId

fun main() {
    val fixture = ContinuumRetrievalExperimentTest()
    val corpusMethod = fixture.javaClass.getDeclaredMethod("corpus").apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST")
    val docs = corpusMethod.invoke(fixture) as List<MemoryDoc>
    val query = "Wohin ging ich letzten Monat mit Thomas?"
    val now = Instant.parse("2026-11-04T09:00:00Z").toEpochMilli()
    val zone = ZoneId.of("Europe/Vienna")
    val cue = TemporalCue(zone)
    val window = requireNotNull(cue.window(query, now))
    val retriever = CharNgramHashRetriever(cue)
    retriever.index(docs)
    val expected = docs.single { it.id == "t14" }
    println("query=$query")
    println("now=${Instant.ofEpochMilli(now)} zone=$zone corpusSize=${docs.size}")
    println("windowStart=${Instant.ofEpochMilli(window.first)} windowEndExclusive=${Instant.ofEpochMilli(window.last + 1)}")
    println("expected=${expected.id} at=${Instant.ofEpochMilli(expected.atMillis)} localAt=${Instant.ofEpochMilli(expected.atMillis).atZone(zone)} insideWindow=${expected.atMillis in window}")
    println("expectedText=${expected.text}")
    retriever.scoredParts(query, now, 6).forEachIndexed { index, part ->
        val doc = part.hit.doc
        println("rank=${index + 1} id=${doc.id} at=${Instant.ofEpochMilli(doc.atMillis)} confirmed=${doc.confirmed} score=${part.hit.score} cosine=${part.cosine} timeWeight=${part.timeWeight}")
        println("text=${doc.text}")
    }
}
