package at.heinz.deviceai.research.exp20.p3

import at.heinz.deviceai.learning.Cre1InferenceEncoder
import at.heinz.deviceai.learning.TrainableSubwordEncoder
import at.heinz.deviceai.research.exp20.p2.P2Encoder
import at.heinz.deviceai.research.exp20.p2.P2Stats
import at.heinz.deviceai.research.exp20.p2.P2Tsv
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * EXP-20 P3 · encoder precondition + DG-SDR pilot (preregistered in docs/research/exp20/EXP-20-P3-PREREGISTRATION-20261030.md).
 * New dataset, disjoint from P2. Each group: one query, its TRUE memory, three near misses (other person / place / day)
 * and four unrelated memories. Type L repeats the slot words; type S names place and day only by synonyms.
 *   generate <file.tsv>        run <file.tsv> <cre1Checkpoint> <outDir>
 */
object P3Probe {
    const val SEED = 2026103003L
    const val GROUPS_PER_TYPE = 60
    const val SDR_BITS = 2048
    const val SDR_K = 41                    // 2.0 % active
    const val SDR_FAN_IN = 4
    const val SDR_SEED = 2048041L

    private val surnames = listOf("Lindner", "Kofler", "Rainer", "Wallner", "Haider", "Egger", "Holzer", "Mair", "Lehner", "Schuster", "Brandl", "Ortner")
    private val tasks = listOf("repariert die Heizung", "holt das Paket ab", "streicht den Zaun", "prüft die Bremsen", "gießt die Pflanzen", "räumt die Regale auf")
    private val places = listOf("im Keller" to "unten im Haus", "in der Garage" to "beim Stellplatz fürs Auto", "auf dem Dachboden" to "ganz oben unterm Dach",
        "im Garten" to "draußen im Grünen", "im Büro" to "am Arbeitsplatz")
    private val days = listOf("am Montag" to "zu Wochenbeginn", "am Mittwoch" to "in der Wochenmitte", "am Freitag" to "zum Wochenausklang",
        "am Samstag" to "am ersten Tag des Wochenendes", "am Sonntag" to "am letzten Tag des Wochenendes")

    data class Row(val group: Int, val type: Char, val role: String, val text: String)

    fun generate(): List<Row> {
        val out = ArrayList<Row>()
        for (g in 0 until 2 * GROUPS_PER_TYPE) {
            val r = Random(SEED * 7919 + g); val type = if (g < GROUPS_PER_TYPE) 'L' else 'S'
            val title = if (r.nextBoolean()) "Frau" else "Herr"
            val names = surnames.shuffled(r); val a = names[0]; val b = names[1]
            val task = tasks.indices.random(r)
            val pl = places.indices.shuffled(r); val dy = days.indices.shuffled(r)
            fun mem(name: String, d: Int, p: Int) = "$title $name ${tasks[task]} ${days[d].first} ${places[p].first}."
            val q = if (type == 'L') "Was macht $title $a ${days[dy[0]].first} ${places[pl[0]].first}?"
                else "Was macht $title $a ${days[dy[0]].second} ${places[pl[0]].second}?"
            out += Row(g, type, "QUERY", q)
            out += Row(g, type, "TRUE", mem(a, dy[0], pl[0]))
            out += Row(g, type, "NEAR_PERSON", mem(b, dy[0], pl[0]))
            out += Row(g, type, "NEAR_PLACE", mem(a, dy[0], pl[1]))
            out += Row(g, type, "NEAR_DAY", mem(a, dy[1], pl[0]))
            repeat(4) {
                val t2 = (tasks.indices - task).random(r); val n2 = names[2 + it]
                out += Row(g, type, "UNRELATED", "${if (r.nextBoolean()) "Frau" else "Herr"} $n2 ${tasks[t2]} ${days.random(r).first} ${places.random(r).first}.")
            }
        }
        return out
    }

    fun write(rows: List<Row>, f: File) { f.parentFile?.mkdirs(); f.writeText("group\ttype\trole\ttext\n" + rows.joinToString("\n") { "${it.group}\t${it.type}\t${it.role}\t${it.text}" } + "\n") }
    fun read(f: File) = f.readLines().drop(1).filter { it.isNotEmpty() }.map { val c = it.split('\t', limit = 4); Row(c[0].toInt(), c[1][0], c[2], c[3]) }

    private val word = Regex("[\\p{L}\\p{N}]+")
    fun tokens(t: String) = word.findAll(t.lowercase()).map { it.value }.toSet()
    fun jaccard(a: String, b: String): Double { val x = tokens(a); val y = tokens(b); return (x intersect y).size.toDouble() / (x union y).size }
    fun cosine(a: DoubleArray, b: DoubleArray): Double {
        var d = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { d += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return if (na == 0.0 || nb == 0.0) 0.0 else d / sqrt(na * nb)
    }

    /** Fixed sparse random projection (fan-in 4, signs +-1) followed by k-winner-take-all; no learning. */
    class Sdr(dim: Int) {
        private val idx = IntArray(SDR_BITS * SDR_FAN_IN); private val sign = DoubleArray(SDR_BITS * SDR_FAN_IN)
        init { val r = Random(SDR_SEED + dim); for (i in idx.indices) { idx[i] = r.nextInt(dim); sign[i] = if (r.nextBoolean()) 1.0 else -1.0 } }
        fun code(v: DoubleArray): IntArray {
            val act = DoubleArray(SDR_BITS) { o -> var s = 0.0; for (f in 0 until SDR_FAN_IN) s += sign[o * SDR_FAN_IN + f] * v[idx[o * SDR_FAN_IN + f]]; s }
            return (0 until SDR_BITS).sortedWith(compareByDescending<Int> { act[it] }.thenBy { it }).take(SDR_K).sorted().toIntArray()
        }
        fun overlap(a: IntArray, b: IntArray): Double { var i = 0; var j = 0; var c = 0
            while (i < a.size && j < b.size) { when { a[i] == b[j] -> { c++; i++; j++ }; a[i] < b[j] -> i++; else -> j++ } }
            return c.toDouble() / SDR_K }
    }

    class Method(val name: String, val score: (String, String) -> Double)

    fun methods(cre1Bytes: ByteArray): List<Method> {
        val sub = TrainableSubwordEncoder(73L)                       // what ContinuumLearningCore creates without a model file
        val cre = Cre1InferenceEncoder.restore(cre1Bytes)            // parity-trained CRE1 checkpoint (not the device checkpoint)
        val creRaw = Cre1InferenceEncoder.createResearch(73L)        // untrained research init
        val sdr16 = Sdr(TrainableSubwordEncoder.DIMENSIONS); val sdr32 = Sdr(Cre1InferenceEncoder.DIMENSION)
        return listOf(
            Method("A0_P2_FEATURES") { q, m -> cosine(P2Encoder.encode(q), P2Encoder.encode(m)) },
            Method("JACCARD") { q, m -> jaccard(q, m) },
            Method("SUBWORD") { q, m -> cosine(sub.representation(q), sub.representation(m)) },
            Method("SUBWORD_SDR") { q, m -> sdr16.overlap(sdr16.code(sub.representation(q)), sdr16.code(sub.representation(m))) },
            Method("CRE1") { q, m -> cre.score(q, m) },
            Method("CRE1_SDR") { q, m -> sdr32.overlap(sdr32.code(cre.embed(q, "q")), sdr32.code(cre.embed(m, "m"))) },
            Method("CRE1_UNTRAINED") { q, m -> creRaw.score(q, m) })
    }

    data class GroupResult(val group: Int, val type: Char, val top1: Boolean, val nearConfusion: Boolean, val unrelatedConfusion: Boolean, val tie: Boolean, val rr: Double)

    /** Strict: correct only if TRUE scores strictly above every other candidate; ties count against the method. */
    fun evaluate(m: Method, rows: List<Row>): List<GroupResult> = rows.groupBy { it.group }.toSortedMap().map { (g, rs) ->
        val q = rs.single { it.role == "QUERY" }.text
        val s = rs.filter { it.role != "QUERY" }.associate { it.role + "|" + it.text to m.score(q, it.text) }
        val t = s.entries.single { it.key.startsWith("TRUE|") }.value
        val others = s.filter { !it.key.startsWith("TRUE|") }
        GroupResult(g, rs[0].type, others.values.all { it < t }, others.any { it.key.startsWith("NEAR") && it.value >= t },
            others.any { it.key.startsWith("UNRELATED") && it.value >= t }, others.values.any { it == t }, 1.0 / (1 + others.values.count { it >= t }))
    }

    @JvmStatic fun main(args: Array<String>) {
        when (args[0]) {
            "generate" -> { val f = File(args[1]); write(generate(), f); println("${P2Tsv.sha256(f)}  ${f.name}") }
            "run" -> run(File(args[1]), File(args[2]), File(args[3]))
        }
    }

    private fun run(data: File, cre1: File, out: File) {
        out.mkdirs()
        val rows = read(data); val bytes = cre1.readBytes()
        val sb = StringBuilder("EXP-20 P3 encoder precondition + DG-SDR  data=${P2Tsv.sha256(data)} cre1=${P2Tsv.sha256(bytes)} jvm=${System.getProperty("java.version")} ${System.getProperty("os.arch")}\n")
        val ms = methods(bytes)
        val res = ms.associate { it.name to evaluate(it, rows) }
        val raw = StringBuilder("method\tgroup\ttype\ttop1\tnear_confusion\tunrelated_confusion\ttie\trr\n")
        for ((name, rs) in res) {
            for (r in rs) raw.append("$name\t${r.group}\t${r.type}\t${if (r.top1) 1 else 0}\t${if (r.nearConfusion) 1 else 0}\t${if (r.unrelatedConfusion) 1 else 0}\t${if (r.tie) 1 else 0}\t${r.rr}\n")
            fun line(t: Char?) = rs.filter { t == null || it.type == t }.let { x ->
                "top1=%d/%d near_conf=%d unrel_conf=%d ties=%d MRR=%.3f".format(x.count { it.top1 }, x.size, x.count { it.nearConfusion }, x.count { it.unrelatedConfusion }, x.count { it.tie }, x.sumOf { it.rr } / x.size) }
            sb.append("%-15s ALL %s | L %s | S %s\n".format(name, line(null), line('L'), line('S')))
        }
        File(out, "p3-groups.tsv").writeText(raw.toString())
        sb.append("\nPRECONDITION (registered): top1>=0.80 on L and on S, near confusion <=0.10 on S\n")
        for ((name, rs) in res) {
            val l = rs.filter { it.type == 'L' }; val s = rs.filter { it.type == 'S' }
            val pass = l.count { it.top1 } >= 0.8 * l.size && s.count { it.top1 } >= 0.8 * s.size && s.count { it.nearConfusion } <= 0.1 * s.size
            sb.append("  %-15s %s\n".format(name, if (pass) "PASS" else "FAIL"))
        }
        val plan = listOf("SUBWORD_SDR" to "SUBWORD", "CRE1_SDR" to "CRE1", "CRE1" to "JACCARD", "SUBWORD" to "JACCARD")
        val raws = plan.map { (x, y) -> val a = res.getValue(x); val b = res.getValue(y)
            Triple(x to y, a.indices.count { a[it].top1 && !b[it].top1 }, a.indices.count { !a[it].top1 && b[it].top1 }) }
        val ps = raws.map { P2Stats.mcnemarExact(it.second, it.third) }.toDoubleArray(); val holm = P2Stats.holm(ps)
        sb.append("\nPLANNED COMPARISONS on all ${res.getValue("JACCARD").size} groups (exact McNemar, Holm over 4, MDE +5 pp)\n")
        raws.forEachIndexed { i, (pair, b, c) ->
            val a = res.getValue(pair.first); val y = res.getValue(pair.second)
            val ci = P2Stats.bootstrapDiff(DoubleArray(a.size) { if (a[it].top1) 1.0 else 0.0 }, DoubleArray(y.size) { if (y[it].top1) 1.0 else 0.0 })
            sb.append("  %s vs %s: b=%d c=%d p=%.3g Holm=%.3g diff=%+.4f 95%%CI[%+.4f,%+.4f] -> %s\n".format(pair.first, pair.second, b, c, ps[i], holm[i], ci.estimate, ci.lo95, ci.hi95, P2Stats.verdict(holm[i], ci)))
        }
        // full uncached ranking cost per query (encode query + 8 candidates + score), single JVM, host only
        val groups = rows.groupBy { it.group }.values.toList()
        val mx = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean; val tid = Thread.currentThread().id
        sb.append("\nCOST per query, uncached (host JVM, block means over 200 x 50 queries, after 5000 warm-up queries)\n")
        var sink = 0.0
        for (m in ms) {
            fun one(k: Int) { val g = groups[k % groups.size]; val q = g[0].text; for (i in 1 until g.size) sink += m.score(q, g[i].text) }
            repeat(5000) { one(it) }
            val blocks = DoubleArray(200); var k = 0; val a0 = mx.getThreadAllocatedBytes(tid)
            for (b in 0 until 200) { val t0 = System.nanoTime(); repeat(50) { one(k++) }; blocks[b] = (System.nanoTime() - t0) / 50.0 / 1000.0 }
            val alloc = (mx.getThreadAllocatedBytes(tid) - a0) / 10_000.0; blocks.sort()
            sb.append("  %-15s p50=%.1f us p95=%.1f us p99=%.1f us alloc/query=%.0f B\n".format(m.name, blocks[100], blocks[190], blocks[198], alloc))
        }
        sb.append("\nMEMORY (primitive payload; analytical)\n")
        sb.append("  SUBWORD params=${TrainableSubwordEncoder(73L).parameterCount} doubles (${8 * TrainableSubwordEncoder(73L).parameterCount} B); stored vector 16 doubles = 128 B\n")
        sb.append("  CRE1 params=${Cre1InferenceEncoder.VOCABULARY * Cre1InferenceEncoder.DIMENSION + 2 * 32 * 32} doubles (${Cre1InferenceEncoder.SNAPSHOT_LENGTH} B snapshot); stored vector 32 doubles = 256 B\n")
        sb.append("  SDR projection ${SDR_BITS * SDR_FAN_IN} (int index + double sign) = ${SDR_BITS * SDR_FAN_IN * 12} B per input width; stored code $SDR_K indices = ${SDR_K * 2} B as 16-bit, or ${SDR_BITS / 8} B bitset\n")
        sb.append("  JACCARD: no parameters; stored token set\n")
        sb.append("sink=$sink\n")
        File(out, "p3-summary.txt").writeText(sb.toString()); print(sb)
    }
}
