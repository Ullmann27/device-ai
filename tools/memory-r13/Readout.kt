package at.heinz.deviceai.learning

import java.time.ZoneId

/** Read unchanged experiment fixtures. */
@Suppress("UNCHECKED_CAST")
fun main() {
    val fixture = ContinuumRetrievalExperimentTest()
    val cls = fixture.javaClass
    val docs = cls.getDeclaredMethod("corpus").apply { isAccessible = true }.invoke(fixture) as List<MemoryDoc>
    val queries = cls.getDeclaredField("queries").apply { isAccessible = true }.get(fixture) as List<Pair<String, String>>
    val now = cls.getDeclaredField("now").apply { isAccessible = true }.getLong(fixture)
    check(docs.size == 1000 && queries.size == 44)
    val temporal = TemporalCue(ZoneId.of("Europe/Vienna"))
    val variants = linkedMapOf(
        "BM25" to Bm25GermanRetriever(),
        "BM25_TIME" to Bm25GermanRetriever(temporal),
        "NGRAM" to CharNgramHashRetriever(),
        "NGRAM_TIME" to CharNgramHashRetriever(temporal),
        "DEFAULT" to ContinuumMemoryRetriever.default(),
    )
    for ((name, retriever) in variants) {
        retriever.index(docs)
        var r1 = 0
        var r3 = 0
        var reciprocalSum = 0.0
        for ((index, entry) in queries.withIndex()) {
            val (query, expected) = entry
            val hits = retriever.search(query, now, 10)
            val rank = hits.indexOfFirst { it.doc.id == expected } + 1
            if (rank == 1) r1++
            if (rank in 1..3) r3++
            if (rank > 0) reciprocalSum += 1.0 / rank
            hits.forEach { hit -> check(docs.first { it.id == hit.doc.id } == hit.doc) }
            val serialized = hits.joinToString(";") {
                "${it.doc.id},${it.confirmed},${java.lang.Double.toHexString(it.score)}"
            }
            println("RESULT $name $index expected=$expected rank=$rank $serialized")
        }
        println("METRIC $name corpus=${docs.size} questions=${queries.size} r1=$r1 r3=$r3 mrr=${reciprocalSum / queries.size} bytes=${retriever.approxIndexBytes()}")
    }
}
