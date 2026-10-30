package at.heinz.deviceai.research.exp20.m5

import at.heinz.deviceai.learning.Cre1InferenceEncoder
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Preregistered M5 query-blind, memory-only CRE1 medoid validity gate on DEV/VAL.
 * The scoring routine cannot receive a query, the gold role, or a candidate identifier.
 * A SEALED test is intentionally unsupported. The model is loaded read-only.
 */
object M5Cre1BlindGate {
    const val MAX_STRICT_VAL_TOP1 = 0.175
    private const val TIE_EPS = 1e-12

    data class BlindRank(val scores: DoubleArray, val strictWinner: Int?, val tiedMaximum: Boolean)
    data class Summary(
        val split: M5Data.Split,
        val groups: Int,
        val strictHits: Int,
        val forcedHits: Int,
        val maxTies: Int
    ) {
        val strictRate: Double get() = strictHits.toDouble() / groups
        val forcedRate: Double get() = forcedHits.toDouble() / groups
    }

    /** Score_j = sum_i <memory_i, memory_j>; query and gold are not parameters. */
    fun rank(memories: List<DoubleArray>): BlindRank {
        require(memories.size == 8) { "exactly 8 memories required" }
        val d = memories[0].size
        require(d > 0 && memories.all { it.size == d && it.all(Double::isFinite) })
        val aggregate = DoubleArray(d)
        for (v in memories) for (j in 0 until d) aggregate[j] += v[j]
        val scores = DoubleArray(memories.size) { i ->
            var dot = 0.0
            for (j in 0 until d) dot += aggregate[j] * memories[i][j]
            require(dot.isFinite())
            dot
        }
        val max = scores.max()
        val winners = scores.indices.filter { abs(scores[it] - max) <= TIE_EPS }
        // Ties do not count as correct even when a deterministic argmax picks the gold index.
        return BlindRank(scores, if (winners.size == 1) winners.single() else null, winners.size != 1)
    }

    /**
     * The embedding callback receives ONLY the memory text. Gold and query are used
     * exclusively by the independent scoring summary, never to choose the predicted index.
     */
    fun evaluate(rows: List<M5Data.Row>, split: M5Data.Split, embedMemory: (String) -> DoubleArray): Summary {
        M5Data.validate(rows)
        val groups = rows.groupBy { it.group }.values.filter { it[0].split == split }
        require(groups.size == 128) { "expected 128 $split groups, got ${groups.size}" }
        var strict = 0
        var forced = 0
        var ties = 0
        for (group in groups) {
            val records = group.filter { it.role != "QUERY" }
            val ranked = rank(records.map { embedMemory(it.text) })
            val goldIndex = records.indexOfFirst { it.role == "TRUE" }
            if (ranked.strictWinner == goldIndex) strict++
            if (ranked.tiedMaximum) ties++
            // Extra diagnostic, not a preregistered validity gate: ties broken by slot order.
            val forcedWinner = ranked.scores.indices.maxBy { ranked.scores[it] }
            if (forcedWinner == goldIndex) forced++
        }
        return Summary(split, groups.size, strict, forced, ties)
    }

    fun gatePassed(s: Summary): Boolean =
        s.split == M5Data.Split.VAL && s.groups == 128 && s.strictRate <= MAX_STRICT_VAL_TOP1

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 5) { "usage: M5Cre1BlindGate <DEV_VAL.tsv> <frozen-CRE1-checkpoint.cre1> <expected-data-sha256> <expected-checkpoint-sha256> <audit-output.txt>" }
        val data = File(args[0])
        val checkpoint = File(args[1])
        val dataExpected = args[2].lowercase()
        val checkpointExpected = args[3].lowercase()
        require(Regex("[0-9a-f]{64}").matches(dataExpected) && Regex("[0-9a-f]{64}").matches(checkpointExpected)) { "Expected frozen SHA-256 digests" }
        val output = File(args[4])
        require(data.isFile && checkpoint.isFile && !output.canonicalFile.equals(data.canonicalFile))
        require(!output.canonicalFile.equals(checkpoint.canonicalFile))
        val dataActual = M5Data.sha256(data)
        require(dataActual == dataExpected) { "M5 DEV/VAL dataset changed since freeze" }
        val snapshot = checkpoint.readBytes()
        val checkpointActual = sha256(snapshot)
        require(checkpointActual == checkpointExpected) { "CRE1 checkpoint changed since freeze" }
        val rows = M5Data.read(data)
        val cre = Cre1InferenceEncoder.restore(snapshot)
        val embed: (String) -> DoubleArray = { memory -> cre.embed(memory, "m") }
        val dev = evaluate(rows, M5Data.Split.DEV, embed)
        val validation = evaluate(rows, M5Data.Split.VAL, embed)
        val passed = gatePassed(validation)
        val report = buildString {
            appendLine("EXP-20 M5: CRE1 QUERY-BLIND MEDOID VALIDITY AUDIT; no SEALED opened")
            appendLine("DATA_SHA256=$dataActual")
            appendLine("CHECKPOINT_SHA256=$checkpointActual CHECKPOINT_STEPS=${cre.steps}")
            for (s in listOf(dev, validation)) {
                appendLine("${s.split} strict_top1=${s.strictHits}/${s.groups} " +
                    "forced_top1=${s.forcedHits}/${s.groups} max_ties=${s.maxTies}")
            }
            appendLine("PREREG_VAL_GATE=${if (passed) "PASS" else "INVALID"}")
            appendLine("FORCED_TOP1_IS_DIAGNOSTIC_ONLY; SEALED=UNOPENED; FOLD7=DEVICE_NOT_RUN")
        }
        output.parentFile?.mkdirs()
        output.writeText(report, StandardCharsets.UTF_8)
        print(report)
        check(passed) { "M5 dataset invalid under preregistered CRE1 query-blind medoid gate" }
    }
}
