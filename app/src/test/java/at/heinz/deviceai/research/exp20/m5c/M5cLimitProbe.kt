package at.heinz.deviceai.research.exp20.m5c

import at.heinz.deviceai.learning.Cre1InferenceEncoder
import at.heinz.deviceai.research.exp20.m5b.M5bValFamilies
import java.io.File

/**
 * EXP-20 M5c limit analysis of the real CRE1 encoder (no training, no new data split):
 *  L1 role binding: CRE1 features are per token (word + in-word char n-grams), so two sentences with the same tokens in
 *     another order have bit-identical embeddings. Measured on generated role-swap pairs.
 *  L3 lexical transfer: share of CRE1 feature weight of each M5b VAL S-query that no TRAIN text contains, split by the
 *     near/far families. Post-hoc diagnostic on the already opened M5b VAL; it is no new performance evidence.
 *   main <parity.cre1> <trained.cre1> <m5b-train.tsv> <m5b-val.tsv> <outFile>
 */
object M5cLimitProbe {
    @JvmStatic fun main(args: Array<String>) {
        val sb = StringBuilder("EXP-20 M5c CRE1 limit analysis\n")
        val encoders = listOf("parity" to Cre1InferenceEncoder.restore(File(args[0]).readBytes()),
            "m5b-trained" to Cre1InferenceEncoder.restore(File(args[1]).readBytes()))
        val names = listOf("Anna", "Ben", "Carla", "David", "Eva")
        val frames = listOf<(String, String) -> Pair<String, String>>(
            { a, b -> "$a wohnt bei $b." to "Bei wem wohnt $a?" }, { a, b -> "$a arbeitet für $b." to "Für wen arbeitet $a?" },
            { a, b -> "$a ruft $b an." to "Wen ruft $a an?" }, { a, b -> "$a besucht $b." to "Wen besucht $a?" })
        for ((label, enc) in encoders) {
            var pairs = 0; var identical = 0; var ties = 0; var maxDiff = 0.0
            for (a in names) for (b in names) if (a != b) for (f in frames) {
                val (fact, query) = f(a, b); val swapped = f(b, a).first
                pairs++
                val d = enc.embed(fact, "m").zip(enc.embed(swapped, "m")).maxOf { (x, y) -> kotlin.math.abs(x - y) }
                maxDiff = maxOf(maxDiff, d); if (d == 0.0) identical++
                if (enc.score(query, fact) == enc.score(query, swapped)) ties++
            }
            sb.append("L1 $label: role-swap pairs=$pairs bit-identical memory embeddings=$identical exact score ties=$ties max|diff|=$maxDiff\n")
        }
        fun texts(f: String, split: Boolean) = File(f).readLines().drop(1).map { it.split('\t') }.filter { split || it[2] == "S" && it[3] == "QUERY" }
        val trainFeatures = texts(args[2], true).flatMap { Cre1InferenceEncoder.features(it[5]).map { f -> f.id } }.toSet()
        val dayNear = M5bValFamilies.families.days.map { it[0] }; val dayFar = M5bValFamilies.families.days.map { it[1] }
        val placeNear = M5bValFamilies.families.places.map { it[0] }; val placeFar = M5bValFamilies.families.places.map { it[1] }
        fun unseen(text: String) = Cre1InferenceEncoder.features(text).filter { it.id !in trainFeatures }.sumOf { it.weight }
        val cells = java.util.TreeMap<String, MutableList<Double>>()
        for (q in texts(args[3], false)) {
            val t = q[5]
            val d = when { dayNear.any { it in t } -> "near"; dayFar.any { it in t } -> "far"; else -> "?" }
            val p = when { placeNear.any { it in t } -> "near"; placeFar.any { it in t } -> "far"; else -> "?" }
            val dayPhrase = (dayNear + dayFar).first { it in t }; val placePhrase = (placeNear + placeFar).first { it in t }
            cells.getOrPut("day $d: unseen weight of the day phrase") { ArrayList() } += unseen(dayPhrase)
            cells.getOrPut("place $p: unseen weight of the place phrase") { ArrayList() } += unseen(placePhrase)
            cells.getOrPut("query day $d / place $p: unseen weight of the whole query") { ArrayList() } += unseen(t)
        }
        sb.append("L3 TRAIN distinct CRE1 features=${trainFeatures.size}; share of CRE1 feature weight never seen in TRAIN (M5b VAL S, post-hoc):\n")
        for ((k, v) in cells) sb.append("  %-58s n=%-3d mean=%.3f min=%.3f max=%.3f\n".format(k, v.size, v.average(), v.min(), v.max()))
        File(args[4]).apply { parentFile?.mkdirs() }.writeText(sb.toString()); print(sb)
    }
}
