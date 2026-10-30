package at.heinz.deviceai.research.exp20.m5b

import at.heinz.deviceai.research.exp20.p2.P2Tsv
import java.io.File

/**
 * Construction check on TRAIN only (allowed before VAL exists): generate the TRAIN split, write it, and confirm the frozen
 * normaliser maps every TRAIN S-query to its canonical day and place. This says nothing about unseen VAL wording.
 *   train-check <out-train.tsv>
 */
object M5bTrainCheck {
    @JvmStatic fun main(args: Array<String>) {
        val rows = M5bData.generate(M5bData.Split.TRAIN, M5bData.train)
        val f = File(args[0]); M5bData.write(rows, f)
        var ok = 0; var n = 0
        for ((_, g) in rows.groupBy { it.group }) {
            if (g[0].kind != M5bData.Kind.S) continue
            val q = M5bNormalizer.normalize(g.single { it.role == "QUERY" }.text); val t = g.single { it.role == "TRUE" }.text
            val day = M5bData.days.single { it in t }; val place = M5bData.places.single { it in t }
            n++; if (day in q && place in q) ok++
        }
        println("M5B_TRAIN rows=${rows.size} sha256=${P2Tsv.sha256(f)} normalizer_TRAIN_S_canonical=$ok/$n glossary_keywords=${M5bNormalizer.glossary.size}")
    }
}
