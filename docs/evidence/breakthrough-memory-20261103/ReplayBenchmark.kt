package at.heinz.deviceai.learning

import java.lang.management.ManagementFactory
import java.nio.file.Files
import com.sun.management.ThreadMXBean

/** Host synthetic measurement, not Android latency or model quality. No timing assertion. */
object ReplayBenchmark {
    @JvmStatic fun main(args: Array<String>) {
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        check(bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        val thread = Thread.currentThread().id
        fun allocated() = bean.getThreadAllocatedBytes(thread)
        fun measure(count: Int, name: String, action: () -> Unit) {
            repeat(8) { action() }
            val times = ArrayList<Long>()
            val bytes = ArrayList<Long>()
            repeat(25) {
                val allocatedBefore = allocated()
                val started = System.nanoTime()
                action()
                times += System.nanoTime() - started
                bytes += allocated() - allocatedBefore
            }
            times.sort(); bytes.sort()
            println("REPLAY_METRIC records=$count workload=$name samples=25 p50_ms=${times[12]/1e6} " +
                "p95_ms=${times[23]/1e6} allocated_p50_bytes=${bytes[12]}")
        }
        for (count in listOf(256, 1024, 4096)) {
            val dir = Files.createTempDirectory("replay-benchmark-").toFile()
            try {
                val records = (0 until count).map { i -> LearningExperienceFactory.create(
                    if (i % 3 == 0) LearningExperienceType.INTENT_LABEL else LearningExperienceType.SOURCE_TEXT,
                    LearningProvenance.SYNTHETIC_FIXTURE, LearningTrustClass.OWNER_CONFIRMED,
                    LearningStatus.CONFIRMED, "synthetic day ${i/4} record $i " + "café 東京 ".repeat(if (i % 10 == 0) 180 else 4), now = i.toLong()) }
                FileLearningExperienceStore(dir).appendMissing(records)
                val store = FileLearningExperienceStore(dir)
                fun checkRows(type: LearningExperienceType, rows: List<LearningExperience>) {
                    check(rows == records.filter { it.type == type }.takeLast(128))
                }
                measure(count, "cold_instance") {
                    checkRows(LearningExperienceType.INTENT_LABEL, FileLearningExperienceStore(dir).replay(LearningExperienceType.INTENT_LABEL, 128))
                }
                measure(count, "warm_same_type") {
                    checkRows(LearningExperienceType.INTENT_LABEL, store.replay(LearningExperienceType.INTENT_LABEL, 128))
                }
                measure(count, "type_switch") {
                    checkRows(LearningExperienceType.SOURCE_TEXT, store.replay(LearningExperienceType.SOURCE_TEXT, 128))
                    checkRows(LearningExperienceType.INTENT_LABEL, store.replay(LearningExperienceType.INTENT_LABEL, 128))
                }
                measure(count, "warm_negative_type") { check(store.replay(LearningExperienceType.COMMAND_MAPPING, 128).isEmpty()) }
                println("REPLAY_DISK records=$count bytes=${dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }}")
            } finally { dir.deleteRecursively() }
        }
    }
}
