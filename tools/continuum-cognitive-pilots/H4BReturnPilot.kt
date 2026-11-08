package at.heinz.deviceai.cognitive

import java.io.File
import java.lang.management.ManagementFactory

/** Replays prospective stories through the production tracker API only; labels stay withheld. */
object H4BReturnPilot {
    private data class Story(val id: String, val turns: List<String>, val returnText: String)

    private fun read(path: String): List<Story> = File(path).readLines().drop(1).map { line ->
        val fields = line.split('\t')
        require(fields.size == 4) { "Malformed story row: ${fields.firstOrNull()}" }
        Story(fields[0], fields[2].split(" ||| "), fields[3])
    }

    private fun run(story: Story, arm: String): String {
        val tracker = H4BArms.create(arm)
        val bean = ManagementFactory.getThreadMXBean()
        val cpuEnabled = bean.isCurrentThreadCpuTimeSupported && bean.isThreadCpuTimeEnabled
        val cpuStart = if (cpuEnabled) bean.currentThreadCpuTime else 0
        val started = System.nanoTime()
        val turnThreads = story.turns.map { text ->
            tracker.observeOwnerTurn(text)
            tracker.snapshot().currentThreadId ?: "-"
        }
        val before = tracker.snapshot().threads
        val hint = tracker.resolveReturn(story.returnText)
        val wall = System.nanoTime() - started
        val cpu = if (cpuEnabled) bean.currentThreadCpuTime - cpuStart else -1
        val kind: String
        val target: String
        when (hint) {
            is LocalConversationSituationTracker.Hint.Resolved -> { kind = "RESOLVED"; target = hint.threadId }
            is LocalConversationSituationTracker.Hint.Ambiguous -> {
                kind = "AMBIGUOUS"
                target = hint.labels.joinToString("|") { label ->
                    before.filter { it.label == label }.joinToString("/") { it.id }.ifEmpty { "?" }
                }
            }
            LocalConversationSituationTracker.Hint.NoMatch -> { kind = "NOMATCH"; target = "" }
        }
        return listOf(story.id, arm, kind, target, turnThreads.joinToString(","), wall, cpu).joinToString("\t")
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2) { "Pass stories.tsv and arm" }
        val stories = read(args[0])
        repeat(4) { stories.take(4).forEach { run(it, args[1]) } }
        println("id\tarm\tkind\ttarget\tturn_threads\twall_ns\tcpu_ns")
        stories.forEach { println(run(it, args[1])) }
    }
}
