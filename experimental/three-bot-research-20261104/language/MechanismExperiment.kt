package at.heinz.deviceai.learning.lm

import java.io.File
import java.security.MessageDigest
import java.util.Random
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

/** Isolated research harness. All model/trainer/tokenizer/runtime code is the frozen original. */
object MechanismExperiment {
    private const val STEPS = 16
    private const val BATCH = 4
    private val seeds = listOf(20261104L, 20261105L, 20261106L)
    private val arms = listOf("original_eos", "paragraph_end_eos", "no_eos_target_ablation")
    private data class Segment(val text: String, val paragraphEnd: Boolean)
    private data class Probe(val id: String, val split: String, val kind: String, val text: String)
    private fun sha(bytes: ByteArray) = LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun quote(value: String): String = buildString {
        append('"')
        for (c in value) when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n")
            '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c.code < 32) append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
        }
        append('"')
    }
    private fun json(v: Any?): String = when (v) {
        null -> "null"
        is String -> quote(v)
        is Number -> v.toString().also { check(it !in setOf("NaN", "Infinity", "-Infinity")) }
        is Boolean -> v.toString()
        is Map<*, *> -> v.entries.joinToString(",", "{", "}") { quote(it.key.toString()) + ":" + json(it.value) }
        is Iterable<*> -> v.joinToString(",", "[", "]") { json(it) }
        else -> quote(v.toString())
    }
    private fun emit(kind: String, vararg values: Pair<String, Any?>) =
        println(json(linkedMapOf("kind" to kind, *values)))
    private fun corpus(root: File): List<Segment> {
        val out = ArrayList<Segment>()
        val rows = File(root, "paths.tsv").readLines().filter { it.isNotBlank() }
        check(rows.size == 77)
        for (row in rows) {
            val fields = row.split('\t')
            check(fields.size == 2 && fields[0].matches(Regex("[a-f0-9]{64}\\.md")))
            check(LmCorpus.splitOf(fields[1]) == LmCorpus.Split.TRAIN)
            val cleaned = LmCorpus.clean(File(root, fields[0]).readText())
            val original = LmCorpus.segments(cleaned).map { it.replace(Regex("\\s+"), " ").trim() }
            val reconstructed = ArrayList<Segment>()
            for (paragraph in cleaned.split(Regex("\\n\\s*\\n"))) {
                val parts = LmCorpus.segments(paragraph)
                parts.forEachIndexed { index, text ->
                    reconstructed += Segment(text.replace(Regex("\\s+"), " ").trim(), index == parts.lastIndex)
                }
            }
            check(reconstructed.map { it.text } == original) { "paragraph reconstruction changed the original corpus" }
            out += reconstructed
        }
        return out
    }
    private fun evaluate(runtime: LmRuntime, probes: List<Probe>, arm: String, seed: Long?) {
        val tok = runtime.tokenizer
        for (probe in probes) {
            val tokens = tok.encode(probe.text)
            check(tokens.none { it == tok.eos || it == tok.bos })
            check(tokens.size + 1 + 32 <= runtime.model.config.context)
            val ids = intArrayOf(tok.bos) + tokens
            val logits = runtime.stepwiseLogits(ids)
            val p = FloatArray(logits.size)
            Kernels.softmaxRow(logits, 0, logits.size, p)
            val order = p.indices.filter { it != tok.bos }.sortedByDescending { p[it] }
            val first = order.first()
            val generated = runtime.generate(LmRuntime.Request(probe.text, maxNewTokens = 32, maxMillis = 30_000))
            check(!generated.promptTruncated)
            check((generated.newTokens == 0 && generated.stop == LmRuntime.Stop.EOS) == (first == tok.eos))
            val bpb = LmEval.segmentBits(runtime.model, tok, probe.text)
            emit("probe", "arm" to arm, "seed" to seed, "id" to probe.id, "split" to probe.split,
                "promptKind" to probe.kind, "prompt" to probe.text, "promptTokens" to tokens.size,
                "pEos" to p[tok.eos], "eosRank" to (order.indexOf(tok.eos) + 1), "nextToken" to first,
                "nextPieceHex" to tok.pieceBytes(first).joinToString("") { "%02x".format(it.toInt() and 255) },
                "eosLogitMargin" to (logits[tok.eos] - order.first { it != tok.eos }.let { logits[it] }),
                "newTokens" to generated.newTokens, "stop" to generated.stop.name, "text" to generated.text,
                "millis" to generated.millis, "contentBits" to bpb.first, "utf8Bytes" to bpb.second,
                "endTargetNll" to -ln(p[tok.eos].toDouble().coerceAtLeast(1e-30)))
        }
    }
    private fun parity(baseline: LmRuntime, tokenizerBytes: ByteArray, probes: List<Probe>) {
        val variants = linkedMapOf(
            "widen2_zero_noise" to LmWeightSurgery.widenMinGru(baseline.model.params, 2, 0f, 20261104L),
            "widen2_paired_noise" to LmWeightSurgery.widenMinGru(baseline.model.params, 2, 0.001f, 20261104L),
            "deepen_one" to LmWeightSurgery.deepenMinGru(baseline.model.params, 1, 20261104L)
        )
        for ((name, params) in variants) {
            val bytes = LmCheckpoint.encode(params, baseline.tokenizer, mapOf("scope" to "RESEARCH_SHADOW"))
            val other = LmRuntime.load(bytes, tokenizerBytes, sha(bytes))
            var maxLogit = 0.0; var maxProbability = 0.0; var equalGenerations = 0
            val start = System.nanoTime()
            for (probe in probes) {
                val ids = intArrayOf(baseline.tokenizer.bos) + baseline.tokenizer.encode(probe.text)
                val a = baseline.stepwiseLogits(ids); val b = other.stepwiseLogits(ids)
                val pa = FloatArray(a.size); val pb = FloatArray(b.size)
                Kernels.softmaxRow(a, 0, a.size, pa); Kernels.softmaxRow(b, 0, b.size, pb)
                for (i in a.indices) {
                    maxLogit = max(maxLogit, abs(a[i] - b[i]).toDouble())
                    maxProbability = max(maxProbability, abs(pa[i] - pb[i]).toDouble())
                }
                val ra = baseline.generate(LmRuntime.Request(probe.text, maxNewTokens = 32, maxMillis = 30_000))
                val rb = other.generate(LmRuntime.Request(probe.text, maxNewTokens = 32, maxMillis = 30_000))
                if (ra.text == rb.text && ra.newTokens == rb.newTokens && ra.stop == rb.stop) equalGenerations++
            }
            val passed = maxLogit <= .001 && maxProbability <= .00001 && equalGenerations == probes.size
            emit("growth_parity", "variant" to name, "sourceParameters" to baseline.model.params.count,
                "parameters" to params.count, "prompts" to probes.size, "maxLogitDelta" to maxLogit,
                "maxProbabilityDelta" to maxProbability, "equalGenerations" to equalGenerations,
                "pass" to passed, "millis" to (System.nanoTime() - start) / 1_000_000)
            check(passed) { "growth parity failed; no training of grown candidates" }
        }
    }
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 3) { "inputs-directory probes.tsv output-directory" }
        val input = File(args[0]); val out = File(args[2]); check(out.isDirectory)
        val checkpointBytes = File(input, "lm1-baseline.lm1").readBytes()
        val tokenizerBytes = File(input, "lm1-tokenizer.lmbp").readBytes()
        val originalSha = sha(checkpointBytes)
        check(originalSha == "7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd")
        check(sha(tokenizerBytes) == "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f")
        val baseline = LmRuntime.load(checkpointBytes, tokenizerBytes, originalSha)
        val tok = baseline.tokenizer
        val probes = File(args[1]).readLines().filter { it.isNotBlank() }.map {
            val fields = it.split('\t'); require(fields.size == 4)
            Probe(fields[0], fields[1], fields[2], fields[3])
        }
        check(probes.map { it.id }.distinct().size == probes.size)
        val segments = corpus(File(input, "original-train-docs"))
        for (probe in probes.filter { it.split != "legacy" }) {
            check(segments.none { it.text.contains(probe.text) || probe.text == it.text }) { "diagnostic text overlap: ${probe.id}" }
        }
        val trainBytes = (segments.joinToString("\n") { it.text } + "\n").toByteArray()
        val sequences = segments.map { tok.encode(it.text) }
        File(out, "train-cleaned.txt").writeBytes(trainBytes)
        emit("identity", "checkpointSha256" to originalSha, "tokenizerSha256" to tok.sha256,
            "parameters" to baseline.model.params.count, "dim" to baseline.model.config.dim,
            "layers" to baseline.model.config.layers, "context" to baseline.model.config.context,
            "vocab" to tok.vocabSize, "bos" to tok.bos, "eos" to tok.eos, "meta" to baseline.identity.meta)
        emit("data", "documents" to 77, "segments" to segments.size, "datasetSha256" to sha(trainBytes),
            "bytes" to trainBytes.size, "shortSegments" to sequences.count { it.size <= 63 },
            "paragraphEnds" to segments.count { it.paragraphEnd }, "probes" to probes.size,
            "probeSha256" to sha(File(args[1]).readBytes()), "trainTestWholePromptOverlap" to false)
        evaluate(baseline, probes, "baseline", null)
        parity(baseline, tokenizerBytes, probes.take(6))
        for (seed in seeds) {
            val schedule = sequences.indices.shuffled(Random(seed)).take(STEPS * BATCH)
            check(schedule.size == STEPS * BATCH)
            for (arm in arms) {
                val model = LmRuntime.load(checkpointBytes, tokenizerBytes, originalSha).model
                val actual = schedule.map { index ->
                    val full = sequences[index]
                    val prefix = intArrayOf(tok.bos) + full.take(63).toIntArray()
                    val eos = full.size <= 63 && when (arm) {
                        "original_eos" -> true
                        "paragraph_end_eos" -> segments[index].paragraphEnd
                        "no_eos_target_ablation" -> false
                        else -> error("unknown arm")
                    }
                    if (eos) prefix + tok.eos else prefix
                }
                val contentSchedule = schedule.joinToString("\n") { (intArrayOf(tok.bos) + sequences[it].take(63).toIntArray()).joinToString(",") }
                val actualSchedule = actual.joinToString("\n") { it.joinToString(",") }
                File(out, "schedule-$seed-$arm.txt").writeText(actualSchedule)
                val start = System.nanoTime()
                var tokens = 0
                val losses = ArrayList<Double>()
                LmTrainer(model, .0001f, .01f, 2, STEPS, threads = 2).use { trainer ->
                    repeat(STEPS) { step ->
                        val batch = actual.subList(step * BATCH, (step + 1) * BATCH)
                        val loss = trainer.step(batch); check(loss.isFinite()); losses += loss
                        tokens += batch.sumOf { it.size - 1 }
                    }
                }
                val trainingMs = (System.nanoTime() - start) / 1_000_000
                val changed = model.params.mats.entries.sumOf { (n, m) ->
                    m.data.indices.count { m.data[it].toRawBits() != baseline.model.params.mats.getValue(n).data[it].toRawBits() }
                }
                val raw = LmCheckpoint.encode(model.params, tok, mapOf("parent" to originalSha,
                    "scope" to "RESEARCH_SHADOW", "mechanismArm" to arm, "seed" to seed.toString(),
                    "steps" to STEPS.toString(), "dataset" to sha(trainBytes)))
                val hash = sha(raw)
                File(out, "$seed-$arm.lm1").writeBytes(raw)
                val runtime = LmRuntime.load(raw, tokenizerBytes, hash)
                emit("training", "arm" to arm, "seed" to seed, "steps" to STEPS,
                    "trainingTokens" to tokens, "eosTargets" to actual.count { it.last() == tok.eos },
                    "sourceParameters" to model.params.count, "checkpointSha256" to hash,
                    "changedWeights" to changed, "millis" to trainingMs, "losses" to losses,
                    "indexScheduleSha256" to sha(schedule.joinToString(",").toByteArray()),
                    "contentScheduleSha256" to sha(contentSchedule.toByteArray()),
                    "tokenScheduleSha256" to sha(actualSchedule.toByteArray()),
                    "segmentsWithNonParagraphEosInOriginal" to schedule.count { sequences[it].size <= 63 && !segments[it].paragraphEnd })
                evaluate(runtime, probes, arm, seed)
            }
        }
        check(sha(File(input, "lm1-baseline.lm1").readBytes()) == originalSha)
        emit("complete", "independentArms" to arms.size * seeds.size, "seeds" to seeds,
            "gateChanged" to false, "promotion" to false, "hostOnly" to true)
    }
}
