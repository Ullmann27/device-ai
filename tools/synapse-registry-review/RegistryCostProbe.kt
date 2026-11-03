package at.heinz.deviceai.chief

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/** Storage/API cost only. No model, intelligence, device or energy measurement. */
object RegistryCostProbe {
    private fun id(index: Int) = "metric-${index.toString().padStart(4, '0')}"
    private fun verify(root: File, count: Int) {
        val s = ChiefBotProfileStore(root)
        check(s.get(id(count - 1))?.agentId == id(count - 1))
        var cursor: String? = null
        var found = 0
        do { val p = s.page(cursor, 50); found += p.items.size; cursor = p.nextAfterId } while (cursor != null)
        check(found == count)
    }
    private fun samples(warmups: Int = 5, count: Int = 30, operation: () -> Unit): List<Long> {
        repeat(warmups) { operation() }
        return List(count) { val start = System.nanoTime(); operation(); System.nanoTime() - start }
    }
    private fun percentile(values: List<Long>, q: Double) = values.sorted()[(ceil(values.size * q).toInt() - 1).coerceAtLeast(0)]
    private fun printMeasurement(name: String, count: Int, values: List<Long>) {
        println("{\"operation\":\"$name\",\"profiles\":$count,\"unit\":\"ns\",\"samples\":[${values.joinToString(",")}],\"p50\":${percentile(values, .5)},\"p95\":${percentile(values, .95)}}")
    }
    private fun restart(root: File, count: Int) {
        val child = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path,
            "-Xmx256m", "-cp", System.getProperty("java.class.path"), RegistryCostProbe::class.java.name,
            "restart", root.path, count.toString()).redirectErrorStream(true).start()
        if (!child.waitFor(15, TimeUnit.SECONDS)) {
            child.destroyForcibly(); child.waitFor(3, TimeUnit.SECONDS)
            error("restart exceeded 15 seconds")
        }
        val output = child.inputStream.bufferedReader().readText()
        check(child.exitValue() == 0 && output.trim() == "RESTART_OK") { "restart failed: $output" }
    }
    @JvmStatic fun main(args: Array<String>) {
        if (args.firstOrNull() == "restart") { verify(File(args[1]), args[2].toInt()); println("RESTART_OK"); return }
        for (count in listOf(20, 1_000)) {
            val root = Files.createTempDirectory("synapse-cost-$count-").toFile()
            try {
                val s = ChiefBotProfileStore(root)
                repeat(count) { s.create(id(it), "Synthetic Bot", "Metadata cost probe", "Evidence only") }
                verify(root, count)
                val files = root.walkTopDown().filter { it.isFile }.toList()
                println("{\"operation\":\"storage\",\"profiles\":$count,\"snapshotBytes\":${File(root, "bot-profiles-v1.bin").length()},\"allFileBytes\":${files.sumOf { it.length() }},\"fileCount\":${files.size},\"modelCalls\":0}")
                printMeasurement("new-store-get", count, samples { check(ChiefBotProfileStore(root).get(id(count - 1)) != null) })
                printMeasurement("existing-store-first-page-17", count, samples { check(s.page(null, 17).items.size == 17) })
                printMeasurement("separate-jvm-full-catalogue-restart", count, samples(warmups = 2, count = 10) { restart(root, count) })
            } finally { root.deleteRecursively() }
        }
    }
}
