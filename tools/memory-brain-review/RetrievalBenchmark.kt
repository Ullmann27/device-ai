package at.heinz.deviceai.cognitive.review

import at.heinz.deviceai.cognitive.HybridMemoryRetriever
import at.heinz.deviceai.cognitive.MemoryRecord
import at.heinz.deviceai.cognitive.MemoryType
import at.heinz.deviceai.cognitive.PrivacyClass
import at.heinz.deviceai.cognitive.ProvenanceType
import at.heinz.deviceai.cognitive.fixtures.HybridMemoryRetriever as BaselineRetriever
import java.lang.management.ManagementFactory
import java.util.Random

/** Deterministic synthetic host experiment; never measures model or Android latency. */
object RetrievalBenchmark {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isEmpty()) { "This benchmark has a fixed corpus and fixed run count." }
        val allocationBean = (ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean)
            ?.takeIf { it.isThreadAllocatedMemorySupported }
            ?.apply { if (!isThreadAllocatedMemoryEnabled) isThreadAllocatedMemoryEnabled = true }
        val results = workloads().map { workload ->
            val baseline = {
                BaselineRetriever.retrieve(workload.query, workload.records, 8, NOW, workload.privacy)
            }
            val candidate = {
                HybridMemoryRetriever.retrieve(workload.query, workload.records, 8, NOW, workload.privacy)
            }
            // Compare actual ranked records and all rendered double values before timing.
            // Both frozen/current score data classes have the same field order and names.
            val expected = baseline()
            val actual = candidate()
            check(expected.map { it.memory } == actual.map { it.memory }) { "Ranking drift in ${workload.name}" }
            check(expected.map { it.score.toString() } == actual.map { it.score.toString() }) { "Score drift in ${workload.name}" }

            repeat(6) { baseline(); candidate() }
            val beforeSamples = mutableListOf<Sample>()
            val afterSamples = mutableListOf<Sample>()
            repeat(11) { index ->
                // Alternate order to reduce systematic JIT/GC scheduling bias.
                if (index % 2 == 0) {
                    beforeSamples += measure(allocationBean, baseline)
                    afterSamples += measure(allocationBean, candidate)
                } else {
                    afterSamples += measure(allocationBean, candidate)
                    beforeSamples += measure(allocationBean, baseline)
                }
            }
            """{"name":"${workload.name}","records":${workload.records.size},"resultIds":[${actual.joinToString(",") { "\"${it.memory.id}\"" }}],"baseline":${summarize(beforeSamples)},"candidate":${summarize(afterSamples)}}"""
        }
        println("""{"schemaVersion":1,"synthetic":true,"hostOnly":true,"baselineCommit":"578bdfd12f896cc990bf1a043bc1b248ba3adb6d","warmupPairs":6,"measuredPairs":11,"limit":8,"workloads":[${results.joinToString(",") }]}""")
    }

    private data class Sample(val nanos: Long, val bytes: Long?)
    private data class Workload(
        val name: String,
        val query: String,
        val records: List<MemoryRecord>,
        val privacy: Set<PrivacyClass> = PrivacyClass.values().toSet()
    )

    private fun measure(bean: com.sun.management.ThreadMXBean?, block: () -> Any): Sample {
        val thread = Thread.currentThread().id
        val bytes = bean?.getThreadAllocatedBytes(thread)
        val start = System.nanoTime()
        val result = block()
        val elapsed = System.nanoTime() - start
        // Consume results so the computation cannot be treated as dead work.
        consumedHash = consumedHash xor result.hashCode()
        val allocation = bytes?.let { bean.getThreadAllocatedBytes(thread) - it }
        return Sample(elapsed, allocation)
    }

    private fun summarize(samples: List<Sample>): String {
        val nanos = samples.map { it.nanos }.sorted()
        val bytes = samples.mapNotNull { it.bytes }.sorted()
        return """{"medianNanos":${nanos[nanos.size / 2]},"minNanos":${nanos.first()},"maxNanos":${nanos.last()},"medianAllocatedBytes":${bytes.getOrNull(bytes.size / 2)},"samplesNanos":[${samples.joinToString(",") { it.nanos.toString() }}]}"""
    }

    private fun workloads(): List<Workload> {
        val dense = corpus(2_048)
        val sparse = dense.map { it.copy(content = "Neutrale synthetische Nachricht ohne passenden Suchbegriff.", tags = emptyList()) } +
            memory("late-unique", "Quantenkristall dokumentiert.")
        val privateCorpus = dense.mapIndexed { index, memory ->
            memory.copy(privacyClass = if (index % 8 == 0) PrivacyClass.PUBLIC else PrivacyClass.SENSITIVE)
        }
        return listOf(
            Workload("small-64", "Werkstatt Kinder Android Termin", dense.take(64)),
            Workload("dense-2048", "Werkstatt Kinder Android Termin", dense),
            Workload("late-unique-2049", "Quantenkristall", sparse),
            Workload("privacy-2048", "Werkstatt Kinder Android Termin", privateCorpus, setOf(PrivacyClass.PUBLIC))
        )
    }

    private fun corpus(size: Int): List<MemoryRecord> {
        val random = Random(64_913L)
        val terms = listOf("Werkstatt", "Kinder", "Android", "Zahnarzttermin", "Wetter", "Motor", "Schrauben", "Fenster", "Kühlung", "Öffnungszeiten")
        return (0 until size).map { index ->
            memory("synthetic-$index", List(48) { terms[random.nextInt(terms.size)] }.joinToString(" ")).copy(
                tags = listOf(terms[index % terms.size], "synthetisch"),
                updatedAt = NOW - (index % 90) * 86_400_000L
            )
        }
    }

    private fun memory(id: String, content: String) = MemoryRecord(
        id, MemoryType.SEMANTIC, content, ProvenanceType.USER_PROVIDED,
        0.9, 0, NOW, privacyClass = PrivacyClass.PUBLIC
    )

    @Volatile private var consumedHash = 0
    private const val NOW = 1_730_678_400_000L
}
