package at.heinz.deviceai.learning.lm

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Random
import kotlin.math.abs
import kotlin.math.max

/** Offline research CLI. It uses the existing LM1 codec, kernels, trainer and runtime. */
object LmWeightExperiment {
    private const val STEPS = 16
    private const val BATCH = 4
    private const val SEED = 20261104L
    private const val MAX_DATA_BYTES = 2_000_000L
    private data class Loaded(val file: File, val original: ByteArray, val tokenizer: LmBpeTokenizer,
                              val model: LmModel, val meta: Map<String, String>)
    private fun hash(bytes: ByteArray): String = LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun field(value: Any?): String = value.toString().replace("\t", " ").replace("\r", " ").replace("\n", " ")
    private fun report(kind: String, vararg fields: Pair<String, Any?>) {
        println((listOf(kind) + fields.map { "${it.first}=${field(it.second)}" }).joinToString("\t"))
    }
    private fun boundedFile(path: String, maxBytes: Long): ByteArray {
        val file = File(path)
        require(!Files.isSymbolicLink(file.toPath()) && file.isFile && file.length() in 1..maxBytes)
        return file.readBytes().also { require(it.size.toLong() <= maxBytes) }
    }
    private fun load(checkpoint: String, tokenizer: String): Loaded {
        val raw = boundedFile(checkpoint, LmRuntime.MAX_CHECKPOINT_BYTES.toLong())
        val tokenBytes = boundedFile(tokenizer, 100_000)
        val runtime = LmRuntime.load(raw, tokenBytes, hash(raw))
        return Loaded(File(checkpoint), raw, runtime.tokenizer, runtime.model, runtime.identity.meta)
    }
    private fun data(path: String, tok: LmBpeTokenizer, context: Int): List<String> {
        val bytes = boundedFile(path, MAX_DATA_BYTES)
        val lines = bytes.toString(Charsets.UTF_8).lines().filter { it.isNotBlank() }
        require(lines.isNotEmpty() && lines.size <= 20_000)
        require(lines.all { it.toByteArray(Charsets.UTF_8).size in 2..2048 && tok.encode(it).size + 1 <= context })
        return lines
    }
    private fun save(source: Loaded, model: LmModel, output: String, details: Map<String, String>): String {
        require(model.params.allFinite())
        val bytes = LmCheckpoint.encode(model.params, source.tokenizer,
            source.meta + details + mapOf("parent" to hash(source.original), "experiment" to "weight-space-20261104",
                "scope" to "RESEARCH_SHADOW", "parameters" to model.params.count.toString()))
        require(bytes.size <= LmRuntime.MAX_CHECKPOINT_BYTES)
        val decoded = LmCheckpoint.decode(bytes, source.tokenizer)
        check(decoded.params.count == model.params.count)
        val store = LmCheckpointStore(File(output))
        val sha = store.put(bytes)
        check(store.get(sha).contentEquals(bytes))
        report("CHECKPOINT", "sha256" to sha, "path" to File(output, "$sha.lm1").absolutePath,
            "parameters" to model.params.count, "bytes" to bytes.size)
        return sha
    }
    private fun evaluate(model: LmModel, tok: LmBpeTokenizer, texts: List<String>) {
        var bits = 0.0; var bytes = 0L; var tokens = 0
        val start = System.nanoTime()
        texts.forEachIndexed { i, text ->
            val score = LmEval.segmentBits(model, tok, text)
            check(score.first.isFinite())
            bits += score.first; bytes += score.second; tokens += tok.encode(text).size
            report("ITEM", "index" to i, "bits" to score.first, "bytes" to score.second)
        }
        report("EVALUATION", "parameters" to model.params.count, "segments" to texts.size,
            "bits" to bits, "bytes" to bytes, "tokens" to tokens, "bpb" to bits / bytes,
            "millis" to (System.nanoTime() - start) / 1_000_000)
    }
    private fun corpus(input: String, output: String) {
        val root = File(input).canonicalFile
        val rows = boundedFile(File(root, "paths.tsv").path, MAX_DATA_BYTES).toString(Charsets.UTF_8)
            .lines().filter { it.isNotBlank() }
        val all = ArrayList<String>()
        for (row in rows) {
            val fields = row.split('\t')
            require(fields.size == 2 && Regex("[0-9a-f]{64}\\.md").matches(fields[0]))
            require(LmCorpus.splitOf(fields[1]) == LmCorpus.Split.TRAIN)
            val raw = boundedFile(File(root, fields[0]).path, MAX_DATA_BYTES).toString(Charsets.UTF_8)
            all += LmCorpus.segments(LmCorpus.clean(raw)).map { it.replace(Regex("\\s+"), " ").trim() }
        }
        val bytes = (all.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_DATA_BYTES && all.isNotEmpty())
        val target = File(output)
        require(!target.exists()) { "refusing to overwrite a dataset" }
        target.parentFile?.mkdirs()
        Files.write(target.toPath(), bytes, java.nio.file.StandardOpenOption.CREATE_NEW)
        report("CORPUS", "documents" to rows.size, "segments" to all.size, "bytes" to bytes.size, "sha256" to hash(bytes))
    }
    private fun train(source: Loaded, dataset: String, output: String) {
        val texts = data(dataset, source.tokenizer, source.model.config.context)
        val sequences = texts.map { text ->
            val full = source.tokenizer.encode(text)
            val prefix = intArrayOf(source.tokenizer.bos) + full.take(63).toIntArray()
            if (full.size <= 63) prefix + source.tokenizer.eos else prefix
        }
        val schedule = sequences.indices.shuffled(Random(SEED)).take(STEPS * BATCH)
        require(schedule.size == STEPS * BATCH) { "need at least ${STEPS * BATCH} training segments" }
        val scheduleBytes = schedule.joinToString(",").toByteArray(Charsets.UTF_8)
        val before = source.model.params.mats.mapValues { it.value.data.copyOf() }
        val start = System.nanoTime()
        var trainedTokens = 0
        LmTrainer(source.model, 0.0001f, 0.01f, 2, STEPS, threads = 2).use { trainer ->
            repeat(STEPS) { step ->
                val batch = schedule.subList(step * BATCH, (step + 1) * BATCH).map { sequences[it] }
                val loss = trainer.step(batch)
                check(loss.isFinite())
                trainedTokens += batch.sumOf { it.size - 1 }
                report("TRAIN_STEP", "step" to step + 1, "nll" to loss, "tokens" to trainedTokens,
                    "millis" to (System.nanoTime() - start) / 1_000_000)
            }
        }
        val changed = source.model.params.mats.entries.sumOf { (name, matrix) ->
            matrix.data.indices.count { before.getValue(name)[it].toRawBits() != matrix.data[it].toRawBits() }
        }
        var replicaDelta = 0.0
        val dim = source.model.config.dim
        if (dim > 64 && dim % 64 == 0) {
            val embedding = source.model.params.mats.getValue("tok").data
            for (row in 0 until source.model.config.vocab) for (col in 64 until dim)
                replicaDelta = max(replicaDelta, abs(embedding[row * dim + col] - embedding[row * dim + col % 64]).toDouble())
        }
        save(source, source.model, output, mapOf("adaptSteps" to "$STEPS", "adaptTokens" to "$trainedTokens",
            "adaptData" to hash(File(dataset).readBytes()), "schedule" to hash(scheduleBytes), "adaptSeed" to "$SEED"))
        report("TRAIN_RESULT", "steps" to STEPS, "tokens" to trainedTokens, "changedWeights" to changed,
            "scheduleSha256" to hash(scheduleBytes), "replicaEmbeddingMaxDelta" to replicaDelta,
            "millis" to (System.nanoTime() - start) / 1_000_000)
    }
    private fun parity(source: Loaded, candidateFile: String, tokenizerFile: String) {
        val other = load(candidateFile, tokenizerFile)
        require(source.tokenizer.sha256 == other.tokenizer.sha256)
        var maxLogit = 0.0; var maxProb = 0.0
        val prompts = listOf("Der Kern prüft", "Eine neue Aufgabe beginnt.", "Zwei Kisten stehen im Raum.")
        for (text in prompts) {
            val ids = intArrayOf(source.tokenizer.bos) + source.tokenizer.encode(text)
            val t1 = Tape(false); val t2 = Tape(false)
            val a = source.model.logits(t1, source.model.frozenNodes(t1), ids).value.data
            val b = other.model.logits(t2, other.model.frozenNodes(t2), ids).value.data
            check(a.size == b.size)
            for (i in a.indices) maxLogit = max(maxLogit, abs(a[i] - b[i]).toDouble())
            val pa = source.model.nextDistribution(ids); val pb = other.model.nextDistribution(ids)
            for (i in pa.indices) maxProb = max(maxProb, abs(pa[i] - pb[i]).toDouble())
        }
        report("PARITY", "maxLogitDelta" to maxLogit, "maxProbabilityDelta" to maxProb,
            "originalParameters" to source.model.params.count, "expandedParameters" to other.model.params.count)
        check(maxLogit <= 0.001 && maxProb <= 0.00001) { "expanded model failed forward parity" }
        check(other.original.contentEquals(other.file.readBytes()))
    }
    private fun probes(source: Loaded) {
        val runtime = LmRuntime.load(source.original, source.tokenizer.serialize(), hash(source.original))
        val cases = listOf(
            "Berechne 17 mal 24. Antworte nur mit der Zahl." to "408",
            "Berechne 2 plus 3. Antworte nur mit der Zahl." to "5",
            "Antworte nur mit dem Wort Hallo." to "Hallo",
            "Antworte nur mit dem Wort fertig." to "fertig",
            "Welche Zahl folgt auf 9? Antworte nur mit der Zahl." to "10",
            "Schreibe das Wort Haus in Kleinbuchstaben. Antworte nur mit dem Wort." to "haus")
        var correct = 0
        for ((i, case) in cases.withIndex()) {
            val result = runtime.generate(LmRuntime.Request(case.first, maxNewTokens = 32, maxMillis = 30_000))
            val ok = result.text.trim() == case.second
            if (ok) correct++
            report("PROBE", "index" to i, "prompt" to case.first, "expected" to case.second,
                "text" to result.text, "correct" to ok, "stop" to result.stop, "tokens" to result.newTokens, "millis" to result.millis)
        }
        report("PROBE_RESULT", "correct" to correct, "total" to cases.size, "scoring" to "exact-trimmed-string")
    }
    @JvmStatic fun main(args: Array<String>) {
        require(args.isNotEmpty()) { "mode required: inspect, corpus, evaluate, rescale, widen, train, parity, probes" }
        if (args[0] == "corpus") { require(args.size == 3); corpus(args[1], args[2]); return }
        require(args.size >= 3) { "mode checkpoint tokenizer [arguments]" }
        val source = load(args[1], args[2])
        report("SOURCE", "sha256" to hash(source.original), "tokenizerSha256" to source.tokenizer.sha256,
            "parameters" to source.model.params.count, "arch" to source.model.config.arch,
            "dim" to source.model.config.dim, "layers" to source.model.config.layers)
        when (args[0]) {
            "inspect" -> {
                require(args.size == 3)
                for ((name, matrix) in source.model.params.mats) report("TENSOR", "name" to name,
                    "rows" to matrix.rows, "columns" to matrix.cols, "count" to matrix.data.size,
                    "min" to matrix.data.minOrNull(), "max" to matrix.data.maxOrNull())
            }
            "evaluate" -> { require(args.size == 4); evaluate(source.model, source.tokenizer, data(args[3], source.tokenizer, source.model.config.context)) }
            "rescale" -> {
                require(args.size == 6)
                val result = LmWeightSurgery.rescaleNorm(source.model.params, args[3], args[4].toFloat())
                val changed = result.mats.entries.sumOf { (name, m) -> m.data.indices.count { m.data[it].toRawBits() != source.model.params.mats.getValue(name).data[it].toRawBits() } }
                save(source, LmModel(result), args[5], mapOf("editTensor" to args[3], "editFactor" to args[4], "changedWeights" to "$changed"))
                report("EDIT", "tensor" to args[3], "factor" to args[4], "changedWeights" to changed)
            }
            "widen" -> {
                require(args.size == 7)
                val result = LmWeightSurgery.widenMinGru(source.model.params, args[3].toInt(), args[4].toFloat(), args[5].toLong())
                save(source, LmModel(result), args[6], mapOf("widthFactor" to args[3], "zeroSumNoise" to args[4], "noiseSeed" to args[5]))
            }
            "train" -> { require(args.size == 5); train(source, args[3], args[4]) }
            "parity" -> { require(args.size == 4); parity(source, args[3], args[2]) }
            "probes" -> { require(args.size == 3); probes(source) }
            else -> error("unknown experiment mode")
        }
        check(source.original.contentEquals(source.file.readBytes())) { "source checkpoint changed" }
    }
}
