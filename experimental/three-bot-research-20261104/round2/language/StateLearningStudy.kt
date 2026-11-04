package at.heinz.deviceai.learning.lm

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import kotlin.math.ln

/** Bounded task study. Core model, tokenizer, optimizer and runtime are unchanged. */
object StateLearningStudy {
    private const val ORIGINAL = "7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd"
    private const val TOKENIZER = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
    private const val STEPS = 256
    private const val BATCH = 4
    private data class Row(val id: String, val family: String, val depth: Int, val answer: String, val prompt: String)
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
    private fun json(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Number -> value.toString().also { check(it !in setOf("NaN", "Infinity", "-Infinity")) }
        is Boolean -> value.toString()
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { quote(it.key.toString()) + ":" + json(it.value) }
        is Iterable<*> -> value.joinToString(",", "[", "]") { json(it) }
        else -> quote(value.toString())
    }
    private fun emit(kind: String, vararg values: Pair<String, Any?>) =
        println(json(linkedMapOf("kind" to kind, *values)))
    private fun rows(file: File): List<Row> {
        check(file.length() in 1..2_000_000)
        return file.readLines().filter { it.isNotBlank() }.map { line ->
            val fields = line.split('\t'); check(fields.size == 5)
            check(fields[0].matches(Regex("[a-f0-9]{20}")) && fields[3] in listOf("A", "B", "C", "D"))
            Row(fields[0], fields[1], fields[2].toInt(), fields[3], fields[4])
        }.also { data -> check(data.map { it.id }.distinct().size == data.size) }
    }
    private fun load(checkpoint: File, tokenizer: File, expected: String): LmRuntime {
        val raw = checkpoint.readBytes(); val tokenBytes = tokenizer.readBytes()
        check(sha(tokenBytes) == TOKENIZER)
        return LmRuntime.load(raw, tokenBytes, expected).also { check(it.model.params.count == 132160) }
    }
    private fun sequence(runtime: LmRuntime, row: Row): IntArray {
        val encoded = runtime.tokenizer.encode(row.prompt + " " + row.answer)
        check(encoded.none { it == runtime.tokenizer.bos || it == runtime.tokenizer.eos })
        val sequence = intArrayOf(runtime.tokenizer.bos) + encoded + runtime.tokenizer.eos
        check(sequence.size - 1 <= runtime.model.config.context)
        return sequence
    }
    private fun choice(runtime: LmRuntime, prompt: String, answer: String): Pair<Double, Double> {
        val tok = runtime.tokenizer
        val prefix = tok.encode(prompt)
        val full = tok.encode(prompt + " " + answer)
        check(full.size > prefix.size && full.copyOfRange(0, prefix.size).contentEquals(prefix))
        val ids = intArrayOf(tok.bos) + full
        val targets = full + tok.eos
        for (i in prefix.indices) targets[i] = -1
        val losses = runtime.model.nll(ids, targets)
        val answerLoss = (prefix.size until full.size).sumOf { losses[it] }
        check(answerLoss.isFinite() && losses.last().isFinite())
        return answerLoss to answerLoss + losses.last()
    }
    private fun preflight(checkpoint: File, tokenizer: File, train: File) {
        val runtime = load(checkpoint, tokenizer, ORIGINAL)
        val data = rows(train); check(data.size == 512)
        val lengths = data.map { sequence(runtime, it).size - 1 }
        val answerLengths = linkedMapOf<String, MutableSet<Int>>()
        for (row in data) for (answer in listOf("A", "B", "C", "D")) {
            val prefix = runtime.tokenizer.encode(row.prompt)
            val full = runtime.tokenizer.encode(row.prompt + " " + answer)
            check(full.copyOfRange(0, prefix.size).contentEquals(prefix))
            answerLengths.getOrPut(answer) { linkedSetOf() }.add(full.size - prefix.size)
            check(prefix.size + 1 + 12 <= runtime.model.config.context)
        }
        emit("preflight", "parameters" to runtime.model.params.count, "config" to runtime.model.config.toString(),
            "checkpointSha256" to runtime.identity.checkpointSha256, "tokenizerSha256" to runtime.tokenizer.sha256,
            "rows" to data.size, "minSequenceTargets" to lengths.min(), "maxSequenceTargets" to lengths.max(),
            "targetsPerEpoch" to lengths.sum(), "answerTokenLengths" to answerLengths,
            "trainSha256" to sha(train.readBytes()), "prefixStable" to true, "promptTruncation" to false)
    }
    private fun train(args: Array<String>) {
        check(args.size == 8)
        val checkpoint = File(args[1]); val tokenizer = File(args[2]); val dataFile = File(args[3])
        val scheduleFile = File(args[4]); val output = File(args[5]); val arm = args[6]; val seed = args[7].toLong()
        check(!output.exists() && arm in listOf("mixed", "curriculum") && seed in listOf(202611041L, 202611042L))
        val runtime = load(checkpoint, tokenizer, ORIGINAL)
        val data = rows(dataFile); check(data.size == 512)
        val schedule = scheduleFile.readLines().map { it.toInt() }
        check(schedule.size == STEPS * BATCH && schedule.all { it in data.indices })
        check(schedule.groupingBy { it }.eachCount() == data.indices.associateWith { 2 })
        if (arm == "curriculum") check(schedule.map { data[it].depth }.zipWithNext().all { it.first <= it.second })
        val sequences = data.map { sequence(runtime, it) }
        val actual = schedule.map { sequences[it] }
        val tokenSchedule = actual.joinToString("\n") { it.joinToString(",") }.toByteArray()
        val multiset = actual.map { it.joinToString(",") }.sorted().joinToString("\n").toByteArray()
        val before = runtime.model.params.mats.mapValues { it.value.data.copyOf() }
        val losses = ArrayList<Double>(); var tokens = 0
        val start = System.nanoTime()
        emit("training_start", "arm" to arm, "seed" to seed, "parameters" to runtime.model.params.count,
            "sourceSha256" to ORIGINAL, "trainSha256" to sha(dataFile.readBytes()),
            "scheduleSha256" to sha(scheduleFile.readBytes()), "tokenScheduleSha256" to sha(tokenSchedule),
            "tokenMultisetSha256" to sha(multiset), "plannedSteps" to STEPS)
        LmTrainer(runtime.model, .001f, .01f, 16, STEPS, threads = 2).use { trainer ->
            repeat(STEPS) { step ->
                val batch = actual.subList(step * BATCH, (step + 1) * BATCH)
                val loss = trainer.step(batch); check(loss.isFinite()); losses += loss
                tokens += batch.sumOf { it.size - 1 }
                if ((step + 1) % 16 == 0) emit("training_step", "arm" to arm, "seed" to seed,
                    "step" to step + 1, "loss" to loss, "tokens" to tokens,
                    "millis" to (System.nanoTime() - start) / 1_000_000)
            }
        }
        val changed = runtime.model.params.mats.entries.sumOf { (name, matrix) ->
            matrix.data.indices.count { matrix.data[it].toRawBits() != before.getValue(name)[it].toRawBits() }
        }
        val raw = LmCheckpoint.encode(runtime.model.params, runtime.tokenizer, runtime.identity.meta + mapOf(
            "scope" to "RESEARCH_SHADOW", "experiment" to "lm1-state-binding-v1", "parent" to ORIGINAL,
            "arm" to arm, "scheduleSeed" to seed.toString(), "steps" to STEPS.toString(),
            "tokens" to tokens.toString(), "train" to sha(dataFile.readBytes()), "schedule" to sha(tokenSchedule)))
        Files.write(output.toPath(), raw, StandardOpenOption.CREATE_NEW)
        val restored = load(output, tokenizer, sha(raw))
        check(restored.model.params.allFinite())
        emit("training_result", "arm" to arm, "seed" to seed, "steps" to STEPS, "tokens" to tokens,
            "eosTargets" to actual.size, "changedWeights" to changed, "parameters" to restored.model.params.count,
            "checkpointSha256" to sha(raw), "checkpointBytes" to raw.size,
            "tokenScheduleSha256" to sha(tokenSchedule), "tokenMultisetSha256" to sha(multiset),
            "scheduleSha256" to sha(scheduleFile.readBytes()), "losses" to losses,
            "millis" to (System.nanoTime() - start) / 1_000_000)
        check(sha(checkpoint.readBytes()) == ORIGINAL)
    }
    private fun evaluate(args: Array<String>) {
        check(args.size == 8)
        val runtime = load(File(args[1]), File(args[2]), args[6])
        val data = rows(File(args[3])); val arm = args[4]; val seed = args[5]; val split = args[7]
        check(split in listOf("dev", "test"))
        emit("evaluation_start", "arm" to arm, "seed" to seed, "split" to split,
            "checkpointSha256" to runtime.identity.checkpointSha256,
            "datasetSha256" to sha(File(args[3]).readBytes()), "rows" to data.size)
        val start = System.nanoTime()
        for (row in data) {
            val result = runtime.generate(LmRuntime.Request(row.prompt, maxNewTokens = 12, maxMillis = 30_000))
            check(!result.promptTruncated && result.stop != LmRuntime.Stop.TIME && result.stop != LmRuntime.Stop.CANCELLED)
            val scores = linkedMapOf<String, Double>(); val eosScores = linkedMapOf<String, Double>()
            for (answer in listOf("A", "B", "C", "D")) {
                val values = choice(runtime, row.prompt, answer)
                scores[answer] = values.first; eosScores[answer] = values.second
            }
            val chosen = scores.minBy { it.value }.key
            val chosenEos = eosScores.minBy { it.value }.key
            val promptIds = intArrayOf(runtime.tokenizer.bos) + runtime.tokenizer.encode(row.prompt)
            val logits = runtime.stepwiseLogits(promptIds); val probabilities = FloatArray(logits.size)
            Kernels.softmaxRow(logits, 0, logits.size, probabilities)
            emit("case", "arm" to arm, "seed" to seed, "split" to split, "id" to row.id,
                "family" to row.family, "depth" to row.depth, "prompt" to row.prompt, "answer" to row.answer,
                "text" to result.text, "freeCorrect" to (result.text.trim() == row.answer),
                "stop" to result.stop.name, "newTokens" to result.newTokens, "millis" to result.millis,
                "pEos" to probabilities[runtime.tokenizer.eos], "choice" to chosen,
                "choiceCorrect" to (chosen == row.answer), "choiceNll" to scores,
                "choiceWithEos" to chosenEos, "choiceWithEosCorrect" to (chosenEos == row.answer),
                "choiceNllWithEos" to eosScores, "correctAnswerNll" to scores.getValue(row.answer))
        }
        emit("evaluation_complete", "arm" to arm, "seed" to seed, "split" to split,
            "rows" to data.size, "millis" to (System.nanoTime() - start) / 1_000_000)
    }
    @JvmStatic fun main(args: Array<String>) {
        check(args.isNotEmpty())
        when (args[0]) {
            "preflight" -> { check(args.size == 4); preflight(File(args[1]), File(args[2]), File(args[3])) }
            "train" -> train(args)
            "evaluate" -> evaluate(args)
            else -> error("unknown mode")
        }
    }
}
