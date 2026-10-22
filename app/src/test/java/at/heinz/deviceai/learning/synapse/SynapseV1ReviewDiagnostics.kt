package at.heinz.deviceai.learning.synapse

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Architecture review of the PR #71 code (v1), executed as a measurement, not as an opinion.
 * Prints evidence lines; nothing here changes a model outside its own fresh in-memory instance.
 * Run: java -cp synapse.jar at.heinz.deviceai.learning.synapse.SynapseV1ReviewDiagnostics
 */
object SynapseV1ReviewDiagnostics {
    private fun table(model: SynapseContrastiveRoleProbe, name: String): DoubleArray =
        SynapseContrastiveRoleProbe::class.java.getDeclaredField(name).apply { isAccessible = true }.get(model) as DoubleArray

    private fun buckets(model: SynapseContrastiveRoleProbe, text: String): IntArray =
        SynapseContrastiveRoleProbe::class.java.getDeclaredMethod("buckets", String::class.java)
            .apply { isAccessible = true }.invoke(model, text) as IntArray

    private fun embed(model: SynapseContrastiveRoleProbe, table: DoubleArray, ids: IntArray): DoubleArray =
        SynapseContrastiveRoleProbe::class.java.getDeclaredMethod("embed", DoubleArray::class.java, IntArray::class.java)
            .apply { isAccessible = true }.invoke(model, table, ids) as DoubleArray

    private fun pairLoss(m: SynapseContrastiveRoleProbe, q: String, p: String, n: String): Double {
        val delta = m.rankScore(q, p) - m.rankScore(q, n)
        return if (delta > 0) ln(1 + exp(-delta)) else -delta + ln(1 + exp(delta))
    }

    /** R1: with role = e_1 the "holographic" unbinding is a fixed cyclic shift: score = sum_k q[k] * c[(k+1) mod 16]. */
    fun roleIsCyclicShift(): Double {
        val m = SynapseContrastiveRoleProbe(47)
        var worst = 0.0
        for ((q, c) in listOf("Welches Kühlmittel benötigt Roboter Z77?" to "Modul M4 benötigt Kühlmittel Helium.",
                "Wo steht Einheit R10?" to "Einheit R10 steht in Wien.")) {
            val qv = embed(m, table(m, "query"), buckets(m, q)); val cv = embed(m, table(m, "clause"), buckets(m, c))
            val shifted = (0 until 16).sumOf { k -> qv[k] * cv[(k + 1) % 16] }
            worst = maxOf(worst, abs(shifted - m.rankScore(q, c)))
        }
        return worst
    }

    /**
     * R2: the update applied by trainPair equals -lr * dLoss/dTheta (checked by central finite differences on the
     * pre-update parameters), as long as no parameter hits the +-2 clamp. Returns the max abs gradient error.
     */
    fun gradientCheck(): Pair<Double, Int> {
        val q = "Welches Kühlmittel benötigt Roboter Z77?"; val p = "Modul M4 benötigt Kühlmittel Helium."
        val n = "Modul M4 benötigt Schutzgas Argon."
        val m = SynapseContrastiveRoleProbe(47)
        repeat(30) { m.trainPair(q, p, n, 0.12) } // non-trivial weights
        val lr = 0.01
        val before = listOf("query", "clause").associateWith { table(m, it).copyOf() }
        val probe = SynapseContrastiveRoleProbe(47); repeat(30) { probe.trainPair(q, p, n, 0.12) }
        probe.trainPair(q, p, n, lr)
        var worst = 0.0; var checked = 0
        for (name in listOf("query", "clause")) {
            val t = table(m, name); val after = table(probe, name)
            for (i in t.indices) {
                val analytic = -(after[i] - before.getValue(name)[i]) / lr
                if (abs(analytic) < 1e-9) continue
                val keep = t[i]
                t[i] = keep + 1e-6; val hi = pairLoss(m, q, p, n)
                t[i] = keep - 1e-6; val lo = pairLoss(m, q, p, n)
                t[i] = keep
                worst = maxOf(worst, abs((hi - lo) / 2e-6 - analytic)); checked++
            }
        }
        return worst to checked
    }

    /** R3: char 2..4-grams hashed into 128 buckets; occupancy and positive/negative overlap of real corpus sentences. */
    fun bucketOccupancy(): List<String> {
        val m = SynapseContrastiveRoleProbe(47)
        val pairs = listOf(
            "Modul M4 benötigt Kühlmittel Helium." to "Modul M4 benötigt Schutzgas Argon.",
            "Einheit R10 steht in Wien." to "Einheit R10 nutzt Modul M4.",
            "Für Modul M9 ist Schutzgas Argon vorgeschrieben." to "Für Modul M9 ist Kühlmittel Neon vorgeschrieben.")
        return pairs.map { (a, b) ->
            val x = buckets(m, a).toSet(); val y = buckets(m, b).toSet()
            "occupancy ${x.size}/128 and ${y.size}/128, shared ${(x intersect y).size}, jaccard ${"%.2f".format((x intersect y).size.toDouble() / (x union y).size)}"
        }
    }

    /** R4: every identifier becomes `subj_slot`, so the asked entity cannot be told apart from any other entity. */
    fun identifierErasure(): Double {
        val m = SynapseContrastiveRoleProbe(47)
        repeat(50) { m.trainPair("Wo steht Einheit R10?", "Einheit R10 steht in Wien.", "Einheit R10 nutzt Modul M4.", 0.12) }
        val right = m.rankScore("Wo steht Einheit R10?", "Einheit R10 steht in Wien.")
        val wrong = m.rankScore("Wo steht Einheit R10?", "Einheit R99 steht in Wien.")
        return abs(right - wrong) // 0.0 means: the model cannot prefer the clause about the asked entity
    }

    /** R5: the +-2 clamp makes some updates differ from the gradient step (counts clamped parameters). */
    fun clampCount(): Int {
        val m = SynapseContrastiveRoleProbe(47)
        repeat(4000) { m.trainPair("Welches Kühlmittel benötigt Roboter Z77?", "Modul M4 benötigt Kühlmittel Helium.",
            "Modul M4 benötigt Schutzgas Argon.", 0.5) }
        return listOf("query", "clause").sumOf { name -> table(m, name).count { abs(it) >= 2.0 } }
    }

    /** R6: Sinkhorn forces a one-to-one assignment; for shared values (two robots in the same hall) it is wrong. */
    fun sinkhornSharedValue(): String {
        val probe = SynapseHolographicProbe(16)
        // rows: questions about R1, R2; columns: candidate values (Nord, Süd). Truth: both are in Nord.
        val evidenceScores = arrayOf(doubleArrayOf(9.0, 1.0), doubleArrayOf(9.0, 1.0))
        val s = probe.sinkhorn(evidenceScores, 50)
        val tie = probe.sinkhorn(arrayOf(doubleArrayOf(1.0, 1.0), doubleArrayOf(1.0, 1.0)), 50)
        return "shared value: row R2 -> Nord ${"%.3f".format(s[1][0])} vs Süd ${"%.3f".format(s[1][1])} (raw prefers Nord 0.900); " +
            "tie stays ${"%.2f".format(tie[0][0])}/${"%.2f".format(tie[0][1])} (no decision)"
    }

    /** R7: adversarial inputs against the v1 proof core (expected answer null unless stated). */
    fun proofCoreAdversarial(): List<String> {
        val core = ContinuumSynapseZeroShadow
        val cases = listOf(
            Triple("wrong bridge", "Roboter Z77 nutzt Modul M4. Für Modul M9 ist Kühlmittel Helium vorgeschrieben.", "Welches Kühlmittel benötigt Z77?") to null,
            Triple("negation 'nie' (not in veto list)", "Roboter Z77 nutzt Modul M4 nie. Für Modul M4 ist Kühlmittel Helium vorgeschrieben.", "Welches Kühlmittel benötigt Z77?") to null,
            Triple("negation 'niemals'", "Roboter Z77 nutzt niemals Modul M4. Für Modul M4 ist Kühlmittel Helium vorgeschrieben.", "Welches Kühlmittel benötigt Z77?") to null,
            Triple("prefix 'Falsch:'", "Falsch: Roboter Z77 nutzt Modul M4. Für Modul M4 ist Kühlmittel Helium vorgeschrieben.", "Welches Kühlmittel benötigt Z77?") to null,
            Triple("negated question", "Roboter Z77 nutzt Modul M4. Für Modul M4 ist Kühlmittel Helium vorgeschrieben.", "Welches Kühlmittel benötigt Z77 nicht?") to null,
            Triple("module location asked, robot location given", "Roboter Z77 steht in Wien. Roboter Z77 nutzt Modul M4.", "Wo steht das Modul von Z77?") to null,
            Triple("unrelated negation vetoes a valid answer", "Einheit Z10 steht nicht in Graz. Einheit Z77 steht in Wien.", "Wo steht Z77?") to "Wien",
            Triple("same-clause chain", "Roboter Z77 nutzt Modul M4 und für Modul M4 ist Kühlmittel Helium vorgeschrieben.", "Welches Kühlmittel benötigt Z77?") to "Helium",
            Triple("passive voice", "Modul M4 wird von Roboter Z77 genutzt. Für Modul M4 ist Kühlmittel Helium vorgeschrieben.", "Welches Kühlmittel benötigt Z77?") to "Helium",
            Triple("two subjects, one clause", "Roboter Z77 nutzt Modul M4, Roboter Z88 nutzt Modul M5. Für Modul M5 ist Kühlmittel Neon vorgeschrieben.", "Welches Kühlmittel benötigt Z88?") to "Neon",
            Triple("temporal: früher", "Früher nutzte Roboter Z77 Modul M4. Heute nutzt Roboter Z77 Modul M5. Für Modul M4 ist Kühlmittel Helium vorgeschrieben. Für Modul M5 ist Kühlmittel Neon vorgeschrieben.", "Welches Kühlmittel benötigt Z77?") to "Neon",
            Triple("homoglyph module id (Cyrillic М)", "Roboter Z77 nutzt Modul M4. Für Modul М4 ist Kühlmittel Helium vorgeschrieben.", "Welches Kühlmittel benötigt Z77?") to null,
            Triple("prefix id M4 vs M40", "Roboter Z77 nutzt Modul M4. Für Modul M40 ist Kühlmittel Helium vorgeschrieben.", "Welches Kühlmittel benötigt Z77?") to null
        )
        return cases.map { (case, expected) ->
            val r = core.deliberate(case.second, case.third)
            val verdict = when {
                r.answer == expected -> "OK"
                expected == null -> "UNSUPPORTED_ANSWER"
                r.answer == null -> "UNJUSTIFIED_ABSTENTION"
                else -> "WRONG"
            }
            "${case.first.padEnd(48)} expected=${expected ?: "ABSTAIN"} got=${r.answer ?: "ABSTAIN"} (${r.decision}) -> $verdict"
        }
    }

    @JvmStatic fun main(args: Array<String>) {
        println("R1 role=e_1 unbinding equals cyclic shift: max |difference| = ${roleIsCyclicShift()}")
        val (gradError, checked) = gradientCheck()
        println("R2 trainPair update vs finite differences: max error ${"%.3e".format(gradError)} over $checked parameters")
        bucketOccupancy().forEach { println("R3 $it") }
        println("R4 identifier erasure: |score(asked R10 clause) - score(other R99 clause)| = ${identifierErasure()}")
        println("R5 parameters clamped at +-2 after 4000 updates with lr 0.5: ${clampCount()}")
        println("R6 Sinkhorn: ${sinkhornSharedValue()}")
        proofCoreAdversarial().forEach { println("R7 $it") }
    }
}
