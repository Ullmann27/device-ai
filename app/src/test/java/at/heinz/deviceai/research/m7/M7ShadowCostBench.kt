package at.heinz.deviceai.research.m7

import at.heinz.deviceai.cognitive.Argon4ConversationShadow
import at.heinz.deviceai.cognitive.Argon4ShadowMode
import at.heinz.deviceai.cognitive.LocalConversationContext
import at.heinz.deviceai.cognitive.LocalGroundedRecall
import at.heinz.deviceai.cognitive.LocalModelClaimAudit
import java.io.File

/**
 * M7 · what do the SHADOW paths cost per chat turn, against the unchanged baseline (OFF)?
 * JVM on the build host, NOT the Fold7: absolute numbers only bound the order of magnitude; the OFF/SHADOW
 * difference and the claim-audit cost are the measured quantities. Not part of the unit suite (no *Test name).
 * Run: java -cp <harness classpath> at.heinz.deviceai.research.m7.M7ShadowCostBench [out.json]
 */
object M7ShadowCostBench {
    private val USER = listOf(
        "Wir hatten gestern ein Problem mit Gemma 4.", "Das Modell hat nach einiger Zeit nicht mehr geantwortet.",
        "Haben wir das inzwischen behoben?", "Anna hat mir am Montag geschrieben.", "Sie fragt, ob der Termin bleibt.",
        "Nein, ich meinte den Termin mit Dr. Berger.", "Wann war das?", "Und was sagt Markus dazu?",
        "Er meint, wir sollten es nächste Woche testen.", "Anderes Thema: Wie spät ist es in Graz?")
    private const val ANSWER = "Laut deinem Verlauf trat das Problem gestern auf. Ein Test, der die Behebung belegt, liegt mir nicht vor."

    private fun dialogueNanos(mode: Argon4ShadowMode, turns: Int): Long {
        val ctx = LocalConversationContext(Argon4ConversationShadow(mode))
        val start = System.nanoTime()
        for (i in 0 until turns) {
            val q = USER[i % USER.size]
            ctx.inspectRequest("bench-session", q)
            ctx.record("bench-session", q, ANSWER)
            ctx.render("bench-session")
        }
        return System.nanoTime() - start
    }

    private fun stats(samples: List<Double>): Pair<Double, Double> {
        val s = samples.sorted(); return s[s.size / 2] to s[(s.size * 95) / 100]
    }

    @JvmStatic fun main(args: Array<String>) {
        val turns = 40; val runs = 60
        repeat(30) { dialogueNanos(Argon4ShadowMode.OFF, turns); dialogueNanos(Argon4ShadowMode.SHADOW, turns) }   // JIT warm-up
        val off = ArrayList<Double>(); val shadow = ArrayList<Double>()
        repeat(runs) {   // interleaved, so host noise hits both arms alike
            off += dialogueNanos(Argon4ShadowMode.OFF, turns) / 1000.0 / turns
            shadow += dialogueNanos(Argon4ShadowMode.SHADOW, turns) / 1000.0 / turns
        }
        val recall = LocalGroundedRecall(("Anna wohnt seit 2024 in Graz. Der Termin mit Dr. Berger ist am 12. März. " +
            "Das Fahrrad ist nicht blau. Markus testet Gemma 4 E4B nächste Woche. ").repeat(12).take(4000), "Bestätigte Besitzererinnerung")
        val answer = ("Anna wohnt seit 2024 in Graz, und der Termin mit Dr. Berger ist am 12. März. " +
            "Markus testet Gemma 4 E4B nächste Woche, das Fahrrad ist nicht blau. ").repeat(8).take(1200)
        repeat(2000) { LocalModelClaimAudit.stage(answer, recall) }
        val audit = (0 until 400).map {
            val t = System.nanoTime(); LocalModelClaimAudit.stage(answer, recall); (System.nanoTime() - t) / 1000.0
        }
        val (offMed, offP95) = stats(off); val (shMed, shP95) = stats(shadow); val (auMed, auP95) = stats(audit)
        val json = """{"schema":1,"host":"${System.getProperty("os.name")} ${System.getProperty("os.arch")} JVM ${System.getProperty("java.version")}",""" +
            """"device":"NOT_FOLD7","turnsPerDialogue":$turns,"runs":$runs,""" +
            """"contextTurnMicros":{"off":{"median":%.1f,"p95":%.1f},"shadow":{"median":%.1f,"p95":%.1f}},""".format(offMed, offP95, shMed, shP95) +
            """"shadowExtraMicrosPerTurnMedian":%.1f,""".format(shMed - offMed) +
            """"claimAuditMicros":{"answerChars":${answer.length},"evidenceChars":${recall.answer.length},"median":%.1f,"p95":%.1f}}""".format(auMed, auP95)
        println(json)
        args.firstOrNull()?.let { File(it).writeText(json + "\n") }
    }
}
