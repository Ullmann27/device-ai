package at.heinz.deviceai.learning.lm

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Base64
import kotlin.math.PI
import kotlin.math.cos

/** Isolated paired objective study. Production sources and native tensor/optimizer kernels remain unchanged. */
object ObjectiveStudy {
    const val ORIGINAL = "7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd"
    const val TOKENIZER = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
    const val STEPS = 256
    const val BATCH = 4
    val OBJECTIVES = listOf("full_text", "answer_eos")
    val SEEDS = listOf(202611043L, 202611044L)
    data class Row(val id: String, val template: String, val obj: String, val answer: String, val prompt: String)
    data class Sample(val ids: IntArray, val targets: IntArray, val answerIndex: Int) {
        val active: Int get() = targets.count { it >= 0 }
    }
    data class Gradients(val loss: Double, val values: LinkedHashMap<String, Mat>, val active: Int)

    fun sha(bytes: ByteArray) = LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun quote(value: String): String = buildString {
        append('"')
        for (c in value) when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n")
            '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c.code < 32) append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
        }
        append('"')
    }
    fun json(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Number -> value.toString().also { check(it !in setOf("NaN", "Infinity", "-Infinity")) }
        is Boolean -> value.toString()
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { quote(it.key.toString()) + ":" + json(it.value) }
        is Iterable<*> -> value.joinToString(",", "[", "]") { json(it) }
        else -> quote(value.toString())
    }
    fun emit(kind: String, vararg values: Pair<String, Any?>) = println(json(linkedMapOf("kind" to kind, *values)))
    fun rows(file: File): List<Row> {
        check(file.length() in 1..1_000_000)
        return file.readLines().filter { it.isNotBlank() }.map { line ->
            val f = line.split('\t'); check(f.size == 5)
            check(f[0].matches(Regex("[a-f0-9]{20}")) && f[3] in listOf("A", "B", "C", "D"))
            check(f[1] in listOf("owner_late", "owner_early"))
            val prompt = Base64.getDecoder().decode(f[4]).toString(Charsets.UTF_8)
            check(prompt.endsWith("\nBesitzer:") && prompt.length <= 240)
            Row(f[0], f[1], f[2], f[3], prompt)
        }.also { check(it.map { r -> r.id }.distinct().size == it.size) }
    }
    fun load(checkpoint: File, tokenizer: File, expected: String): LmRuntime {
        val bytes = checkpoint.readBytes(); val tok = tokenizer.readBytes()
        check(sha(tok) == TOKENIZER)
        return LmRuntime.load(bytes, tok, expected).also { check(it.model.params.count == 132160) }
    }
    fun sample(runtime: LmRuntime, row: Row, objective: String): Sample {
        check(objective in OBJECTIVES)
        val prefix = runtime.tokenizer.encode(row.prompt)
        val full = runtime.tokenizer.encode(row.prompt + " " + row.answer)
        check(full.size == prefix.size + 1 && full.copyOfRange(0, prefix.size).contentEquals(prefix))
        check(full.none { it == runtime.tokenizer.bos || it == runtime.tokenizer.eos })
        val ids = intArrayOf(runtime.tokenizer.bos) + full
        val targets = full + runtime.tokenizer.eos
        check(ids.size <= runtime.model.config.context && prefix.size + 1 + 12 <= runtime.model.config.context)
        if (objective == "answer_eos") for (i in prefix.indices) targets[i] = -1
        return Sample(ids, targets, prefix.size).also { if (objective == "answer_eos") check(it.active == 2) }
    }

    /** Sum native per-sequence cross entropies using one explicit shared active-target normalizer. */
    fun gradients(model: LmModel, batch: List<Sample>): Gradients {
        check(batch.isNotEmpty())
        val total = batch.sumOf { it.active }; check(total > 0)
        val buffer = model.params.zeros()
        var loss = 0.0
        for (s in batch) {
            val tape = Tape()
            val nodes = model.params.mats.mapValues { (name, p) -> tape.param(p, buffer.getValue(name)) }
            val value = tape.crossEntropy(model.logits(tape, nodes, s.ids), s.targets, total)
            check(value.value.data[0].isFinite())
            loss += value.value.data[0]
            tape.backward(value)
        }
        check(buffer.values.all { m -> m.data.all { it.isFinite() } })
        return Gradients(loss, buffer, total)
    }
    fun weightsSha(model: LmModel): String {
        val md = MessageDigest.getInstance("SHA-256")
        for ((name, p) in model.params.mats) {
            md.update(name.toByteArray()); md.update(0.toByte())
            val bytes = ByteBuffer.allocate(p.data.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (v in p.data) bytes.putInt(v.toRawBits())
            md.update(bytes.array())
        }
        return LmBpeTokenizer.hex(md.digest())
    }
    fun rate(step: Int): Float {
        if (step < 16) return .001f * (step + 1) / 16
        val progress = ((step - 16).toDouble() / (STEPS - 16)).coerceIn(0.0, 1.0)
        return (.001f * (.1 + .9 * .5 * (1 + cos(PI * progress)))).toFloat()
    }
    private fun train(args: Array<String>) {
        check(args.size == 8)
        val input = File(args[1]); val tokenizer = File(args[2]); val train = File(args[3])
        val scheduleFile = File(args[4]); val output = File(args[5]); val objective = args[6]; val seed = args[7].toLong()
        check(objective in OBJECTIVES && seed in SEEDS && !output.exists())
        val runtime = load(input, tokenizer, ORIGINAL)
        val data = rows(train); check(data.size == 128)
        val order = scheduleFile.readLines().map { it.toInt() }
        check(order.size == STEPS * BATCH && order.groupingBy { it }.eachCount() == data.indices.associateWith { 8 })
        val samples = data.map { sample(runtime, it, objective) }
        val sequenceText = order.map { i -> samples[i].ids.joinToString(",") }
        val before = runtime.model.params.mats.mapValues { it.value.data.copyOf() }
        val optimizer = LmAdamW(runtime.model.params, .01f)
        var active = 0; var positions = 0; val losses = ArrayList<Double>(); val norms = ArrayList<Double>()
        val started = System.nanoTime()
        emit("training_start", "objective" to objective, "seed" to seed, "sourceSha256" to ORIGINAL,
            "parameters" to runtime.model.params.count, "trainSha256" to sha(train.readBytes()),
            "scheduleSha256" to sha(scheduleFile.readBytes()), "sequenceScheduleSha256" to sha(sequenceText.joinToString("\n").toByteArray()),
            "sequenceMultisetSha256" to sha(sequenceText.sorted().joinToString("\n").toByteArray()), "plannedSteps" to STEPS,
            "trainingThreads" to 1)
        repeat(STEPS) { step ->
            val batch = order.subList(step * BATCH, (step + 1) * BATCH).map { samples[it] }
            val gradient = gradients(runtime.model, batch)
            val norm = optimizer.step(gradient.values, rate(step), 1f)
            check(runtime.model.params.allFinite() && norm.isFinite())
            active += gradient.active; positions += batch.sumOf { it.ids.size }
            losses += gradient.loss; norms += norm
            if ((step + 1) % 16 == 0) emit("training_step", "objective" to objective, "seed" to seed,
                "step" to step + 1, "lossPerActiveTarget" to gradient.loss, "gradientNormBeforeClip" to norm,
                "activeTargets" to active, "fullSequencePositions" to positions, "learningRate" to rate(step),
                "millis" to (System.nanoTime() - started) / 1_000_000)
        }
        val changed = runtime.model.params.mats.entries.sumOf { (name, p) ->
            p.data.indices.count { p.data[it].toRawBits() != before.getValue(name)[it].toRawBits() }
        }
        val encoded = LmCheckpoint.encode(runtime.model.params, runtime.tokenizer, runtime.identity.meta + mapOf(
            "scope" to "RESEARCH_SHADOW", "experiment" to "lm1-single-binding-objective-v1", "parent" to ORIGINAL,
            "objective" to objective, "scheduleSeed" to seed.toString(), "steps" to STEPS.toString(),
            "activeTargets" to active.toString(), "fullSequencePositions" to positions.toString(), "train" to sha(train.readBytes())))
        Files.write(output.toPath(), encoded, StandardOpenOption.CREATE_NEW)
        val restored = load(output, tokenizer, sha(encoded))
        check(restored.model.params.allFinite() && weightsSha(restored.model) == weightsSha(runtime.model))
        emit("training_result", "objective" to objective, "seed" to seed, "steps" to STEPS,
            "activeTargets" to active, "fullSequencePositions" to positions, "examples" to order.size,
            "eosTargets" to order.size, "answerTargets" to order.size, "changedWeights" to changed,
            "parameters" to restored.model.params.count, "checkpointSha256" to sha(encoded), "checkpointBytes" to encoded.size,
            "scheduleSha256" to sha(scheduleFile.readBytes()), "sequenceScheduleSha256" to sha(sequenceText.joinToString("\n").toByteArray()),
            "sequenceMultisetSha256" to sha(sequenceText.sorted().joinToString("\n").toByteArray()),
            "lossesPerActiveTarget" to losses, "gradientNorms" to norms,
            "millis" to (System.nanoTime() - started) / 1_000_000)
        check(sha(input.readBytes()) == ORIGINAL)
    }
    private fun choice(runtime: LmRuntime, row: Row, answer: String): Pair<Double, Double> {
        val s = sample(runtime, row.copy(answer = answer), "answer_eos")
        val values = runtime.model.nll(s.ids, s.targets)
        val a = values[s.answerIndex]; val eos = values.last()
        check(a.isFinite() && eos.isFinite())
        return a to a + eos
    }
    private fun evaluate(args: Array<String>) {
        check(args.size == 8)
        val runtime = load(File(args[1]), File(args[2]), args[6])
        val data = rows(File(args[3])); val objective = args[4]; val seed = args[5]; val split = args[7]
        check(split in listOf("dev", "test") && data.size == 64)
        val started = System.nanoTime()
        emit("evaluation_start", "objective" to objective, "seed" to seed, "split" to split,
            "checkpointSha256" to runtime.identity.checkpointSha256, "datasetSha256" to sha(File(args[3]).readBytes()), "rows" to data.size)
        for (row in data) {
            val result = runtime.generate(LmRuntime.Request(row.prompt, maxNewTokens = 12, maxMillis = 30_000))
            check(!result.promptTruncated && result.stop !in setOf(LmRuntime.Stop.TIME, LmRuntime.Stop.CANCELLED))
            val scores = linkedMapOf<String, Double>(); val eosScores = linkedMapOf<String, Double>()
            for (answer in listOf("A", "B", "C", "D")) {
                val (a, eos) = choice(runtime, row, answer); scores[answer] = a; eosScores[answer] = eos
            }
            val chosen = scores.minBy { it.value }.key; val chosenEos = eosScores.minBy { it.value }.key
            val logits = runtime.stepwiseLogits(intArrayOf(runtime.tokenizer.bos) + runtime.tokenizer.encode(row.prompt))
            val probabilities = FloatArray(logits.size); Kernels.softmaxRow(logits, 0, logits.size, probabilities)
            val strict = result.text.trim() == row.answer && result.newTokens == 1 && result.stop == LmRuntime.Stop.EOS
            emit("case", "objective" to objective, "seed" to seed, "split" to split, "id" to row.id,
                "template" to row.template, "object" to row.obj, "prompt" to row.prompt, "answer" to row.answer,
                "text" to result.text, "strictCorrect" to strict, "textOnlyCorrect" to (result.text.trim() == row.answer),
                "stop" to result.stop.name, "newTokens" to result.newTokens, "millis" to result.millis,
                "pEos" to probabilities[runtime.tokenizer.eos], "choice" to chosen, "choiceCorrect" to (chosen == row.answer),
                "choiceNll" to scores, "choiceWithEos" to chosenEos, "choiceWithEosCorrect" to (chosenEos == row.answer),
                "choiceNllWithEos" to eosScores, "correctAnswerNll" to scores.getValue(row.answer))
        }
        emit("evaluation_complete", "objective" to objective, "seed" to seed, "split" to split,
            "rows" to data.size, "millis" to (System.nanoTime() - started) / 1_000_000)
    }
    @JvmStatic fun main(args: Array<String>) {
        check(args.isNotEmpty())
        when (args[0]) {
            "preflight" -> {
                check(args.size == 4)
                val checkpoint = File(args[1]); val tokenizer = File(args[2]); val train = File(args[3])
                GradientPreflight.run(load(checkpoint, tokenizer, ORIGINAL), rows(train), train, checkpoint)
            }
            "train" -> train(args)
            "evaluate" -> evaluate(args)
            else -> error("unknown mode")
        }
    }
}
