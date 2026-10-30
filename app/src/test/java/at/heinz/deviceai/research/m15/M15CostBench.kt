package at.heinz.deviceai.research.m15

import at.heinz.deviceai.cognitive.Argon4ConversationShadow
import at.heinz.deviceai.cognitive.Argon4ShadowMode
import at.heinz.deviceai.cognitive.LocalConversationContext
import at.heinz.deviceai.cognitive.LocalGroundedRecall
import at.heinz.deviceai.cognitive.LocalModelClaimAudit
import at.heinz.deviceai.cognitive.MemoryQuestionResolver
import at.heinz.deviceai.cognitive.MemoryTimeline
import at.heinz.deviceai.intelligence.ewm.Ewm1Brain
import at.heinz.deviceai.intelligence.ewm.Ewm1SourceIndex
import java.io.File

/**
 * M15 · per-turn host cost of every SHADOW component after M11/M12, against the OFF baseline. Host JVM, NOT Fold7.
 * Run: java -cp <harness classpath> at.heinz.deviceai.research.m15.M15CostBench [out.json]
 */
object M15CostBench {
    private val USER = listOf("Mein Bruder Max hat heute angerufen.", "Wie spät ist es?", "Erzähl mir einen Witz.",
        "Was wollte er eigentlich?", "Gestern war ich mit Anna in Linz.", "Was haben wir an dem Tag gegessen?",
        "Nein, ich meinte meinen Bruder Paul.", "Wie alt ist er?", "Gib mir einen Tipp.", "Anderes Thema: Wie wird das Wetter?")
    private fun facts(n: Int) = (0 until n).map { i -> "f$i" to when (i % 4) {
        0 -> "Person$i wohnte bis 2023 in Ort$i."; 1 -> "Person${i - 1} wohnt seit 2024 in Stadt$i."
        2 -> "Person$i ist die Schwester von Bruder$i."; else -> "Bruder${i - 1} arbeitet in Werk$i." } }
    private fun median(xs: List<Double>) = xs.sorted()[xs.size / 2]
    private fun p95(xs: List<Double>) = xs.sorted()[(xs.size * 95) / 100]
    private fun timeMicros(reps: Int, block: () -> Unit): List<Double> {
        repeat(reps / 2) { block() }
        return (0 until reps).map { val t = System.nanoTime(); block(); (System.nanoTime() - t) / 1000.0 }
    }

    @JvmStatic fun main(args: Array<String>) {
        fun dialogue(mode: Argon4ShadowMode) {
            val ctx = LocalConversationContext(Argon4ConversationShadow(mode))
            for (q in USER) { ctx.inspectRequest("s", q); ctx.record("s", q, "Verstanden."); ctx.render("s") }
        }
        val off = timeMicros(400) { dialogue(Argon4ShadowMode.OFF) }.map { it / USER.size }
        val shadow = timeMicros(400) { dialogue(Argon4ShadowMode.SHADOW) }.map { it / USER.size }
        val recall = LocalGroundedRecall(("Bis 2023 wohnte Anna in Linz, seit 2024 wohnt sie in Graz. Markus hat 12 Euro bezahlt. " +
            "Der Termin mit Dr. Berger ist am 12. März. ").repeat(10).take(3000), "Bestätigte Besitzererinnerung")
        val answer = ("Anna wohnt seit 2024 in Graz und der Termin mit Dr. Berger ist am 12. März. ").repeat(12).take(1200)
        val audit = timeMicros(400) { LocalModelClaimAudit.stage(answer, recall) }
        val out = StringBuilder("{\"schema\":1,\"device\":\"NOT_FOLD7\",\"host\":\"${System.getProperty("os.arch")} JVM ${System.getProperty("java.version")}\",")
        out.append("\"contextTurnMicros\":{\"off\":%.1f,\"shadow\":%.1f,\"shadowP95\":%.1f},".format(median(off), median(shadow), p95(shadow)))
        out.append("\"claimAuditV2Micros\":{\"median\":%.1f,\"p95\":%.1f,\"answerChars\":${answer.length},\"evidenceChars\":${recall.answer.length}},".format(median(audit), p95(audit)))
        out.append("\"memoryPerQuestionMicros\":{")
        listOf(50, 200).forEachIndexed { idx, n ->
            val fs = facts(n)
            val brain = timeMicros(60) { Ewm1Brain(fs, Ewm1SourceIndex(emptyList())) { _, s -> s.toList() }.ask("Wo wohnt Person4?") }
            val timeline = timeMicros(60) { MemoryTimeline.check(listOf("f4"), fs) }
            val resolver = timeMicros(60) { MemoryQuestionResolver.resolve("Wo wohnt der Bruder von Person2?", fs) }
            if (idx > 0) out.append(',')
            out.append("\"facts$n\":{\"ewm2Ask\":%.1f,\"timelineCheck\":%.1f,\"siblingResolver\":%.1f}".format(median(brain), median(timeline), median(resolver)))
        }
        out.append("}}")
        println(out)
        args.firstOrNull()?.let { File(it).writeText(out.toString() + "\n") }
    }
}
