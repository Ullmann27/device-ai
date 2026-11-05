package at.heinz.deviceai.cognitive

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import java.security.MessageDigest

/** Host-only R2 comparison. Cold mode is invoked in a separate fresh JVM. */
object RedactionReuseBenchmark {
    private const val ROUNDS = 12
    private val clock = { 100_000L }
    @Volatile private var sink: CompiledAiContext? = null

    private data class Workload(
        val name: String,
        val input: AiContextInput,
        val policy: AiContextPolicy,
        val warmupPairs: Int,
        val batch: Int
    )

    private data class Sample(val wallNs: Double, val cpuNs: Double, val allocatedBytes: Double)

    @JvmStatic
    fun main(args: Array<String>) {
        when (args.firstOrNull()) {
            "functional" -> functional()
            "hot" -> hot()
            "cold" -> cold(args.getOrNull(1) ?: error("Specify baseline or candidate"))
            else -> error("Expected functional, hot or cold")
        }
    }

    private fun minimal(): Workload = Workload("no-secrets", ContextIntegrityFixture.input("Wo ist Maren?"),
        AiContextPolicy(includeToolCatalog = false), warmupPairs = 10_000, batch = 2_000)

    private fun workloads(): List<Workload> {
        val keys = listOf("password", "PaSsWd", "token", "secret", "credential", "authorization",
            "apiKey", "api_key", "api-key")
        val request = "Maren Android local model\n" + keys.joinToString("\n") {
            "$it=synthetic_${it}_value" + "q".repeat(40)
        } + "\nNicht senden. Å🧠\uD800"
        val memories = List(4) { index ->
            ContextIntegrityFixture.memory("pattern-memory-$index",
                "Maren Android local model $index token=synthetic_memory_$index " + "detail ".repeat(30) + "Nicht Graz.")
        }
        val original = ContextIntegrityFixture.dossier("Maren api_key=synthetic_source Nicht Graz.")
        val dossier = original.copy(
            sources = List(2) { index -> original.sources.single().copy(
                canonicalUrl = "https://example.org/evidence/$index", title = "Maren token=synthetic_title_$index") },
            synthesis = ResearchSynthesisOutput("Maren api-key=synthetic_synthesis Nicht Graz.")
        )
        val input = ContextIntegrityFixture.input(request, memories,
            listOf("Maren nicht in Graz. secret=synthetic_contradiction", "Widerruf: keine Freigabe."), dossier)
            .copy(activeTaskSummary = "Maren authorization : synthetic_task\nBearer Abcdefgh1234\nNicht senden.",
                availableSkillNames = listOf("memory-review", "token=synthetic_skill", "follow-up"))
        return listOf(minimal(), Workload("secret-rich-sections", input,
            AiContextPolicy(maxBytes = 16_000), warmupPairs = 1_000, batch = 200))
    }

    private fun operation(workload: Workload, baseline: Boolean, fresh: Boolean): () -> CompiledAiContext {
        if (fresh) return if (baseline) {
            { BaselineAiContextCompiler(workload.policy, clock).compile(workload.input) }
        } else {
            { AiContextCompiler(workload.policy, clock).compile(workload.input) }
        }
        if (baseline) {
            val compiler = BaselineAiContextCompiler(workload.policy, clock)
            return { compiler.compile(workload.input) }
        }
        val compiler = AiContextCompiler(workload.policy, clock)
        return { compiler.compile(workload.input) }
    }

    private fun same(old: CompiledAiContext, new: CompiledAiContext) {
        check(old == new) { "Complete context parity failed" }
        check(old.text.toByteArray(Charsets.UTF_8).contentEquals(new.text.toByteArray(Charsets.UTF_8)))
    }

    private fun digest(value: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
        value.forEach { char -> hash.update((char.code ushr 8).toByte()); hash.update(char.code.toByte()) }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    private fun functional() {
        var checks = 0
        for (workload in workloads()) for (fresh in listOf(false, true)) {
            val old = operation(workload, true, fresh)
            val new = operation(workload, false, fresh)
            repeat(32) { same(old(), new()); checks++ }
        }
        println("""{"status":"PASS","instanceLifecycleComparisons":$checks,"workloads":2,"instanceModes":["reused","fresh-per-call"]}""")
    }

    private fun counters(): ThreadMXBean {
        val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean ?: error("Host counters unavailable")
        check(bean.isThreadAllocatedMemorySupported && bean.isCurrentThreadCpuTimeSupported)
        bean.isThreadAllocatedMemoryEnabled = true
        bean.isThreadCpuTimeEnabled = true
        return bean
    }

    private fun measure(bean: ThreadMXBean, count: Int, operation: () -> CompiledAiContext): Sample {
        val thread = Thread.currentThread().threadId()
        val allocatedBefore = bean.getThreadAllocatedBytes(thread)
        val cpuBefore = bean.currentThreadCpuTime
        val started = System.nanoTime()
        repeat(count) { sink = operation() }
        val wall = System.nanoTime() - started
        val cpu = bean.currentThreadCpuTime - cpuBefore
        val bytes = bean.getThreadAllocatedBytes(thread) - allocatedBefore
        return Sample(wall.toDouble() / count, cpu.toDouble() / count, bytes.toDouble() / count)
    }

    private fun hot() {
        val bean = counters()
        val reports = mutableListOf<String>()
        for (workload in workloads()) for (fresh in listOf(false, true)) {
            val old = operation(workload, true, fresh)
            val new = operation(workload, false, fresh)
            val expected = old()
            same(expected, new())
            repeat(workload.warmupPairs) { iteration ->
                if (iteration % 2 == 0) { sink = old(); sink = new() }
                else { sink = new(); sink = old() }
            }
            val samples = mutableListOf<String>()
            repeat(ROUNDS) { round ->
                val a: Sample
                val b: Sample
                if (round % 2 == 0) { a = measure(bean, workload.batch, old); b = measure(bean, workload.batch, new) }
                else { b = measure(bean, workload.batch, new); a = measure(bean, workload.batch, old) }
                same(expected, checkNotNull(sink))
                samples += """{"round":$round,"baselineFirst":${round % 2 == 0},"baselineWallNs":${a.wallNs},"candidateWallNs":${b.wallNs},"baselineCpuNs":${a.cpuNs},"candidateCpuNs":${b.cpuNs},"baselineAllocatedBytes":${a.allocatedBytes},"candidateAllocatedBytes":${b.allocatedBytes}}"""
            }
            same(expected, old()); same(expected, new())
            reports += """{"workload":"${workload.name}","instanceMode":"${if (fresh) "fresh-per-call" else "reused"}","warmupPairs":${workload.warmupPairs},"rounds":$ROUNDS,"batch":${workload.batch},"outputBytes":${expected.text.toByteArray(Charsets.UTF_8).size},"includedMemoryCount":${expected.includedMemoryIds.size},"includedSourceCount":${expected.includedResearchSourceUrls.size},"truncated":${expected.truncated},"inputUtf16Sha256":"${digest(workload.input.toString() + workload.policy)}","resultUtf16Sha256":"${digest(expected.toString())}","samples":[${samples.joinToString(",")}]}"""
        }
        println("""{"status":"PASS","comparison":"R1 versus R2","workloads":[${reports.joinToString(",")}]}""")
    }

    private fun cold(variant: String) {
        require(variant == "baseline" || variant == "candidate")
        val workload = minimal()
        val bean = counters()
        val thread = Thread.currentThread().threadId()
        // No compiler has been constructed or called in this fresh JVM.
        val allocatedBefore = bean.getThreadAllocatedBytes(thread)
        val cpuBefore = bean.currentThreadCpuTime
        val started = System.nanoTime()
        val result = if (variant == "baseline") {
            BaselineAiContextCompiler(workload.policy, clock).compile(workload.input)
        } else {
            AiContextCompiler(workload.policy, clock).compile(workload.input)
        }
        sink = result
        val wall = System.nanoTime() - started
        val cpu = bean.currentThreadCpuTime - cpuBefore
        val bytes = bean.getThreadAllocatedBytes(thread) - allocatedBefore
        println("""{"variant":"$variant","scope":"first constructor plus compile in fresh JVM after fixture/counter setup","wallNs":$wall,"cpuNs":$cpu,"allocatedBytes":$bytes,"outputBytes":${result.text.toByteArray(Charsets.UTF_8).size},"resultUtf16Sha256":"${digest(result.toString())}"}""")
    }
}
