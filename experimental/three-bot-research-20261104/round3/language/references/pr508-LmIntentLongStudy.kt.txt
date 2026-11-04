package at.heinz.deviceai.learning.lm

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Random
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/** Isolated answer-supervised LM1 study. No application integration or action execution. */
object LmIntentLongStudy {
    private const val BASE = "7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd"
    private const val TOKENIZER = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
    private const val STEPS = 512
    private const val BATCH = 4
    private const val SEED = 20261104L
    private const val RATE = 0.0001f
    private val encoder = java.util.Base64.getEncoder()
    data class Example(val id: String, val label: String, val text: String)
    data class Supervised(val ids: IntArray, val targets: IntArray, val answerIndex: Int)
    data class Score(val exact: Int, val total: Int, val answerNll: Double, val eosNll: Double,
                     val prematureEos: Int, val classCorrect: List<Int>, val classTotal: List<Int>,
                     val meanFirstEosProbability: Double, val missingEos: Int, val generatedTokens: Int)
    private fun sha(bytes: ByteArray) = LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun emit(kind: String, vararg values: Pair<String, Any?>) {
        println((listOf(kind) + values.map { (k, v) -> "$k=${v.toString().replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')}" }).joinToString("\t"))
    }
    private fun read(file: File, max: Int): ByteArray {
        require(!Files.isSymbolicLink(file.toPath()) && file.isFile && file.length() in 1..max.toLong())
        return file.inputStream().use { it.readNBytes(max + 1) }.also { require(it.size in 1..max) }
    }
    private fun rows(bytes: ByteArray): List<Example> {
        val lines = bytes.toString(Charsets.UTF_8).lines().filter { it.isNotEmpty() }
        require(lines.first() == "id\tlabel\tprompt") { "expected id/label/prompt TSV" }
        return lines.drop(1).map { line ->
            val f = line.split('\t'); require(f.size == 3 && f[0].isNotBlank() && f[1] in listOf("0", "1", "2", "3") && f[2].isNotBlank())
            Example(f[0], f[1], f[2])
        }.also { data -> require(data.isNotEmpty() && data.size <= 10000 && data.map { it.id }.toSet().size == data.size) }
    }
    private fun prompt(e: Example) = "Auftrag: ${e.text}\nAktion:"
    internal fun exactAnswer(result: LmRuntime.Result, label: String): Boolean =
        result.text == label && result.newTokens == 1 && result.stop == LmRuntime.Stop.EOS
    fun supervised(tok: LmBpeTokenizer, text: String, label: String, context: Int): Supervised {
        val prefix = intArrayOf(tok.bos) + tok.encode(text)
        val answer = tok.encode(label)
        require(answer.size == 1 && answer[0] !in setOf(tok.bos, tok.eos)) { "label must be exactly one ordinary token" }
        require(prefix.size + 4 <= context) { "prompt would be truncated by generation" }
        val ids = prefix + answer
        val targets = IntArray(ids.size) { -1 }
        targets[prefix.lastIndex] = answer[0]
        targets[ids.lastIndex] = tok.eos
        return Supervised(ids, targets, prefix.lastIndex)
    }
    private fun evaluate(model: LmModel, tok: LmBpeTokenizer, data: List<Example>, step: Int,
                         split: String, printCases: Boolean): Score {
        val raw = LmCheckpoint.encode(model.params, tok, mapOf("scope" to "RESEARCH_SHADOW", "step" to "$step"))
        require(raw.size <= LmRuntime.MAX_CHECKPOINT_BYTES)
        val runtime = LmRuntime.load(raw, tok.serialize(), sha(raw))
        var correct = 0; var premature = 0; var answerNll = 0.0; var eosNll = 0.0
        var firstEosProbability = 0.0; var missingEos = 0; var generatedTokens = 0
        val classCorrect = IntArray(4); val classTotal = IntArray(4)
        for (e in data) {
            val s = supervised(tok, prompt(e), e.label, model.config.context)
            val nll = model.nll(s.ids, s.targets)
            val a = nll[s.answerIndex]; val end = nll[s.targets.lastIndex]
            check(a.isFinite() && end.isFinite()) { "invalid answer or EOS NLL" }
            answerNll += a; eosNll += end
            val firstEos = model.nextDistribution(s.ids.copyOfRange(0, s.answerIndex + 1))[tok.eos]
            check(firstEos.isFinite()); firstEosProbability += firstEos
            val result = runtime.generate(LmRuntime.Request(prompt(e), maxNewTokens = 4, maxMillis = 30000))
            check(!result.promptTruncated && result.stop !in setOf(LmRuntime.Stop.TIME, LmRuntime.Stop.CANCELLED, LmRuntime.Stop.CONTEXT))
            val ok = exactAnswer(result, e.label)
            if (result.stop != LmRuntime.Stop.EOS) missingEos++
            generatedTokens += result.newTokens
            val c = e.label.toInt(); classTotal[c]++
            if (ok) { correct++; classCorrect[c]++ }
            if (result.newTokens == 0 && result.stop == LmRuntime.Stop.EOS) premature++
            if (printCases) emit("CASE", "split" to split, "step" to step, "id" to e.id, "expected" to e.label,
                "textBase64" to encoder.encodeToString(result.text.toByteArray(Charsets.UTF_8)), "tokens" to result.newTokens,
                "stop" to result.stop, "correct" to ok, "answerNll" to a, "eosNll" to end,
                "firstEosProbability" to firstEos)
        }
        val score = Score(correct, data.size, answerNll / data.size, eosNll / data.size, premature,
            classCorrect.toList(), classTotal.toList(), firstEosProbability / data.size, missingEos, generatedTokens)
        emit("SCORE", "split" to split, "step" to step, "correct" to score.exact, "total" to score.total,
            "answerNll" to score.answerNll, "eosNll" to score.eosNll, "prematureEos" to score.prematureEos,
            "classCorrect" to score.classCorrect.joinToString(","), "classTotal" to score.classTotal.joinToString(","),
            "meanFirstEosProbability" to score.meanFirstEosProbability, "missingEos" to score.missingEos,
            "generatedTokens" to score.generatedTokens)
        return score
    }
    internal class MaskedTrainer(private val model: LmModel) : AutoCloseable {
        private val pool = Executors.newFixedThreadPool(2)
        private val buffers = List(2) { model.params.zeros() }
        private val optimizer = LmAdamW(model.params, 0.01f)
        fun step(batch: List<Supervised>, step: Int): Pair<Double, Double> {
            val targets = batch.sumOf { s -> s.targets.count { it >= 0 } }
            require(targets == 2 * batch.size)
            for (buffer in buffers) for (m in buffer.values) m.data.fill(0f)
            val losses = pool.invokeAll((0..1).map { worker -> Callable {
                var lossSum = 0.0
                for (i in batch.indices.filter { it % 2 == worker }) {
                    val s = batch[i]; val tape = Tape()
                    val nodes = model.params.mats.mapValues { (name, matrix) -> tape.param(matrix, buffers[worker].getValue(name)) }
                    val loss = tape.crossEntropy(model.logits(tape, nodes, s.ids), s.targets, targets)
                    check(loss.value.data[0].isFinite()); lossSum += loss.value.data[0]
                    tape.backward(loss)
                }
                lossSum
            } }).sumOf { it.get() }
            val grads = buffers[0]
            for ((name, matrix) in buffers[1]) {
                val sum = grads.getValue(name).data
                for (i in sum.indices) sum[i] += matrix.data[i]
            }
            val rate = if (step < 4) RATE * (step + 1) / 4 else
                (RATE * (0.1 + 0.9 * 0.5 * (1 + cos(PI * (step - 4).toDouble() / (STEPS - 4))))).toFloat()
            val norm = optimizer.step(grads, rate, 1f)
            check(model.params.allFinite() && norm.isFinite())
            return losses to norm
        }
        override fun close() { pool.shutdownNow() }
    }
    private fun capacity(checkpoint: File, tokenizer: File, output: File, expected: String) {
        require(!output.exists())
        val command = listOf(File(System.getProperty("java.home"), "bin/java").absolutePath,
            "-Xms64m", "-Xmx1g", "-cp", System.getProperty("java.class.path"),
            "at.heinz.deviceai.learning.lm.LmCapacityBenchmark", checkpoint.absolutePath, tokenizer.absolutePath)
        val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output).start()
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly(); process.waitFor(); error("INITIAL_CAPACITY_GATE_REJECTED: TIMEOUT")
        }
        val result = output.readLines().filter { it.startsWith("CAPACITY_RESULT\t") }
        val fields = result.singleOrNull()?.split('\t')?.drop(1)?.associate { it.substringBefore('=') to it.substringAfter('=') }
        check(process.exitValue() == 0 && fields?.get("pass") == "true" && fields["checkpointSha256"] == expected) {
            "INITIAL_CAPACITY_GATE_REJECTED: inspect ${output.path}"
        }
        emit("INITIAL_CAPACITY_GATE", "pass" to true, "checkpointSha256" to expected, "logSha256" to sha(output.readBytes()))
    }
    private fun train(args: Array<String>) {
        require(args.size == 6) { "train baseline tokenizer DATA_DIR NEW_OUTPUT small|wide" }
        val original = File(args[1]); val tokenizerFile = File(args[2]); val dataDir = File(args[3]); val out = File(args[4])
        val variant = args[5]; require(variant == "small" || variant == "wide")
        require(!out.exists()) { "output must not exist; no implicit resume" }; Files.createDirectories(out.toPath())
        val inputFiles = listOf(original, tokenizerFile, File(dataDir, "train.tsv"), File(dataDir, "dev.tsv"))
        val inputBytes = inputFiles.map { read(it, LmRuntime.MAX_CHECKPOINT_BYTES) }
        val inputHashes = inputBytes.map(::sha)
        require(inputHashes[0] == BASE && inputHashes[1] == TOKENIZER)
        val source = LmRuntime.load(inputBytes[0], inputBytes[1], BASE); val tok = source.tokenizer
        val train = rows(inputBytes[2]); val dev = rows(inputBytes[3])
        require(train.size == 128 && dev.size == 24)
        require(train.map { it.text }.toSet().intersect(dev.map { it.text }.toSet()).isEmpty())
        val params = if (variant == "small") LmCheckpoint.decode(inputBytes[0], tok).params
            else LmWeightSurgery.widenMinGru(source.model.params, 8, 0.001f, SEED)
        val model = LmModel(params); check(params.allFinite())
        require(params.count == if (variant == "small") 132160 else 6562304)
        val examples = train.map { supervised(tok, prompt(it), it.label, model.config.context) }
        dev.forEach { supervised(tok, prompt(it), it.label, model.config.context) }
        val schedule = ArrayList<Int>(); var epoch = 0
        while (schedule.size < STEPS * BATCH) schedule += train.indices.shuffled(Random(SEED + epoch++))
        val scheduleSha = sha(schedule.joinToString(",").toByteArray(Charsets.UTF_8))
        val tokenScheduleSha = sha(schedule.joinToString("\n") { i ->
            examples[i].ids.joinToString(",") + "|" + examples[i].targets.joinToString(",")
        }.toByteArray(Charsets.UTF_8))
        emit("MANIFEST", "variant" to variant, "scope" to "RESEARCH_SHADOW", "parameters" to params.count,
            "steps" to STEPS, "batch" to BATCH, "learningRate" to RATE, "warmup" to 4, "weightDecay" to 0.01,
            "seed" to SEED, "workers" to 2, "scheduleSha256" to scheduleSha, "tokenScheduleSha256" to tokenScheduleSha,
            "objective" to "ANSWER_AND_EOS_ONLY", "testReadDuringTraining" to false)
        inputFiles.forEachIndexed { i, f -> emit("INPUT", "path" to f.absolutePath, "sha256" to inputHashes[i]) }
        for (e in dev.take(3)) {
            val ids = intArrayOf(tok.bos) + tok.encode(prompt(e))
            val a = source.model.nextDistribution(ids); val b = model.nextDistribution(ids)
            val difference = a.indices.maxOf { abs(a[it].toDouble() - b[it]) }
            check(difference <= 0.00001) { "initial probability parity failed; no training" }
            emit("INITIAL_PARITY", "id" to e.id, "probabilityDelta" to difference)
        }
        val store = LmCheckpointStore(File(out, "checkpoints"))
        fun save(step: Int): String {
            val raw = LmCheckpoint.encode(params, tok, source.identity.meta + mapOf("parent" to BASE,
                "scope" to "RESEARCH_SHADOW", "experiment" to "lm1-intent-long-20261104", "variant" to variant,
                "parameters" to "${params.count}", "adaptSteps" to "$step", "schedule" to tokenScheduleSha,
                "adaptData" to inputHashes[2], "objective" to "ANSWER_AND_EOS_ONLY"))
            require(raw.size <= LmRuntime.MAX_CHECKPOINT_BYTES)
            val hash = store.put(raw); LmRuntime.load(store.get(hash), inputBytes[1], hash)
            emit("CHECKPOINT", "step" to step, "sha256" to hash, "path" to File(out, "checkpoints/$hash.lm1").absolutePath)
            return hash
        }
        val initial = save(0)
        capacity(File(out, "checkpoints/$initial.lm1"), tokenizerFile, File(out, "capacity-before.tsv"), initial)
        evaluate(model, tok, dev, 0, "dev", true)
        var bestSha = ""; var bestStep = -1; var best: Score? = null
        val started = System.nanoTime()
        MaskedTrainer(model).use { trainer ->
            repeat(STEPS) { step ->
                check((System.nanoTime() - started) / 1_000_000_000 < 840) { "training budget exceeded" }
                val batch = schedule.subList(step * BATCH, (step + 1) * BATCH).map { examples[it] }
                val (loss, norm) = trainer.step(batch, step)
                emit("TRAIN_STEP", "step" to step + 1, "answerEosNll" to loss, "gradientNormBeforeClip" to norm,
                    "supervisedTargets" to (step + 1) * BATCH * 2, "millis" to (System.nanoTime() - started) / 1_000_000)
                if (step + 1 in listOf(128, 256, 512)) {
                    val hash = save(step + 1); val score = evaluate(model, tok, dev, step + 1, "dev", true)
                    val previous = best
                    if (previous == null || score.exact > previous.exact || score.exact == previous.exact && score.answerNll < previous.answerNll) {
                        best = score; bestSha = hash; bestStep = step + 1
                    }
                }
            }
        }
        inputFiles.forEachIndexed { i, f -> check(sha(read(f, LmRuntime.MAX_CHECKPOINT_BYTES)) == inputHashes[i]) }
        val selected = requireNotNull(best) { "no completed trained checkpoint" }
        val selection = "variant\tcheckpointSha256\tstep\tdevCorrect\tdevTotal\tdevAnswerNll\tinitialSha256\n" +
            "$variant\t$bestSha\t$bestStep\t${selected.exact}\t${selected.total}\t${selected.answerNll}\t$initial\n"
        Files.writeString(File(out, "selection.tsv").toPath(), selection, StandardOpenOption.CREATE_NEW)
        emit("SELECTION", "checkpointSha256" to bestSha, "step" to bestStep, "devCorrect" to selected.exact,
            "devTotal" to selected.total, "devAnswerNll" to selected.answerNll, "testRead" to false, "inputsUnchanged" to true,
            "generalModelPromotion" to "NOT_PERMITTED", "postTrainingCapacity" to "REQUIRES_SEPARATE_BENCHMARK")
    }
    private fun test(args: Array<String>) {
        require(args.size == 4) { "test checkpoint tokenizer TEST_TSV" }
        val checkpoint = File(args[1]); val raw = read(checkpoint, LmRuntime.MAX_CHECKPOINT_BYTES)
        val tokenizer = read(File(args[2]), 100000); require(sha(tokenizer) == TOKENIZER)
        val runtime = LmRuntime.load(raw, tokenizer, sha(raw))
        val bytes = read(File(args[3]), 2000000)
        emit("TEST_INPUT", "checkpointSha256" to sha(raw), "testSha256" to sha(bytes), "parameters" to runtime.model.params.count)
        evaluate(runtime.model, runtime.tokenizer, rows(bytes), runtime.identity.meta["adaptSteps"]?.toInt() ?: 0, "test", true)
        check(sha(read(checkpoint, LmRuntime.MAX_CHECKPOINT_BYTES)) == sha(raw))
    }
    @JvmStatic fun main(args: Array<String>) {
        require(args.isNotEmpty())
        when (args[0]) { "train" -> train(args); "test" -> test(args); else -> error("train or test required") }
    }
}
