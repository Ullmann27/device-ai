package at.heinz.deviceai.cognitive

import java.io.File
import java.lang.management.ManagementFactory
import kotlin.system.measureNanoTime

/** Source-bound routing measurement, not an answer-quality or model benchmark. */
object ContextPilot {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size in 1..2 && (args.size == 1 || args[1] == "--assert")) {
            "Pass context-cases.tsv [--assert]"
        }
        val cases = File(args[0]).readLines().drop(1).map { line ->
            val fields = line.split('\t', limit = 4)
            require(fields.size == 4 && fields[2] in setOf("true", "false"))
            fields
        }
        val bean = ManagementFactory.getThreadMXBean()
        val cpuAvailable = bean.isCurrentThreadCpuTimeSupported && bean.isThreadCpuTimeEnabled
        repeat(200) { cases.forEach { LocalConversationFollowUpPolicy.requiresPreviousOwnerTurn(it[3]) } }
        println("id\tsplit\texpected\tactual\tbenefits\tpass")
        var failures = 0
        for ((id, split, expected, request) in cases) {
            val actual = LocalConversationFollowUpPolicy.requiresPreviousOwnerTurn(request)
            val benefits = LocalConversationFollowUpPolicy.benefitsFromPreviousOwnerTurn(request)
            val passed = actual == expected.toBoolean() && benefits == actual
            if (!passed) failures++
            println("$id\t$split\t$expected\t$actual\t$benefits\t$passed")
        }
        val samples = List(7) {
            val cpuStart = if (cpuAvailable) bean.currentThreadCpuTime else 0L
            var accepted = 0
            val wall = measureNanoTime {
                repeat(100) {
                    cases.forEach { if (LocalConversationFollowUpPolicy.requiresPreviousOwnerTurn(it[3])) accepted++ }
                }
            }
            val cpu = if (cpuAvailable) bean.currentThreadCpuTime - cpuStart else -1L
            "$wall,$cpu,$accepted"
        }
        println("METRICS wall_ns,cpu_ns,accepted; each sample = ${cases.size * 100} routing calls")
        samples.forEach(::println)
        if (args.size == 2) check(failures == 0) { "$failures context cases failed" }
    }
}
