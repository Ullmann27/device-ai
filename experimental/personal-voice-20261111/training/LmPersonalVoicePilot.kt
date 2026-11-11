package at.heinz.deviceai.learning.lm

import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Base64
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln

/** Research-only adapter over the original LM1 engine. No app caller or routing change. */
object LmPersonalVoicePilot {
    private const val STEPS = 128
    private const val SEED = 20261111L
    private const val RATE = .00005f
    private const val BASE = "7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd"
    private const val TOK = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
    private data class Row(val id: String, val label: String, val prompt: String)
    private data class Sample(val ids: IntArray, val targets: IntArray)
    private fun sha(bytes: ByteArray) = LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun emit(kind: String, vararg fields: Pair<String, Any>) =
        println((listOf(kind) + fields.map { "${it.first}=${it.second}" }).joinToString("\t"))
    private fun rows(path: Path): List<Row> {
        val lines = Files.readAllLines(path)
        require(lines.firstOrNull() == "id\tlabel\tprompt")
        return lines.drop(1).filter { it.isNotBlank() }.map {
            val fields = it.split('\t')
            require(fields.size == 3 && fields[1] in listOf("0", "1", "2", "3"))
            require(Regex("[A-Za-z0-9_.-]+").matches(fields[0]) && fields[2].isNotBlank())
            Row(fields[0], fields[1], fields[2])
        }.also { require(it.isNotEmpty() && it.map { row -> row.id }.distinct().size == it.size) }
    }
    private fun prefix(tok: LmBpeTokenizer, row: Row) =
        intArrayOf(tok.bos) + tok.encode("Auftrag: ${row.prompt}\n")
    private fun answer(tok: LmBpeTokenizer, row: Row, context: Int): Sample {
        val pre = prefix(tok, row)
        val label = tok.encode(row.label)
        require(label.size == 1 && pre.size + 4 <= context) { "overlength or invalid label: ${row.id}, prefix=${pre.size}" }
        val ids = pre + label
        val targets = IntArray(ids.size) { -1 }
        targets[pre.lastIndex] = label.single()
        targets[targets.lastIndex] = tok.eos
        check(targets.count { it >= 0 } == 2)
        return Sample(ids, targets)
    }
    private fun textSample(tok: LmBpeTokenizer, text: String, context: Int): Sample {
        val content = tok.encode(text)
        require(content.isNotEmpty() && content.size + 1 <= context)
        return Sample(intArrayOf(tok.bos) + content, content + tok.eos)
    }
    private fun argmax(values: FloatArray): Int {
        require(values.isNotEmpty() && values.all { it.isFinite() })
        var best = 0
        for (i in 1 until values.size) if (values[i] > values[best]) best = i
        return best
    }
    private fun tensorDigest(mats: List<Pair<String, Mat>>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for ((name, mat) in mats) {
            digest.update("$name:${mat.rows}:${mat.cols}\n".toByteArray(Charsets.UTF_8))
            val bytes = ByteBuffer.allocate(mat.data.size * 4)
            for (value in mat.data) bytes.putFloat(value)
            digest.update(bytes.array())
        }
        return LmBpeTokenizer.hex(digest.digest())
    }
    private fun teacherProbabilities(model: LmModel, sample: Sample): Mat {
        val tape = Tape(training = false)
        val logits = model.logits(tape, model.frozenNodes(tape), sample.ids).value
        val output = Mat(logits.rows, logits.cols)
        for (r in 0 until logits.rows) Kernels.softmaxRow(logits.data, r * logits.cols, logits.cols, output.data)
        return output
    }
    private fun score(model: LmModel, tok: LmBpeTokenizer, data: List<Row>) {
        var correct = 0; var restricted = 0; var premature = 0; var nll = 0.0
        val choices = (0..3).map { tok.encode(it.toString()).single() }
        for (row in data) {
            val started = System.nanoTime()
            val sample = answer(tok, row, model.config.context)
            nll += model.nll(sample.ids, sample.targets)[sample.ids.size - 2]
            var ids = prefix(tok, row)
            val predictionStarted = System.nanoTime()
            val first = model.nextDistribution(ids)
            val firstMillis = (System.nanoTime() - predictionStarted) / 1e6
            val predicted = choices.indices.maxBy { first[choices[it]] }
            if (predicted.toString() == row.label) restricted++
            val generated = ArrayList<Int>(); var ended = false
            repeat(4) {
                if (!ended) {
                    val next = argmax(if (generated.isEmpty()) first else model.nextDistribution(ids))
                    if (next == tok.eos) ended = true else { generated += next; ids += next }
                }
            }
            val decoded = tok.decode(generated.toIntArray())
            val exact = ended && generated.size == 1 && decoded == row.label
            if (exact) correct++
            if (ended && generated.isEmpty()) premature++
            emit("CASE", "id" to row.id, "expected" to row.label,
                "predictedRestricted" to predicted, "tokens" to generated.joinToString(","),
                "decodedBase64" to Base64.getEncoder().encodeToString(decoded.toByteArray(Charsets.UTF_8)),
                "ended" to ended, "exact" to exact, "firstEos" to first[tok.eos],
                "p0" to first[choices[0]], "p1" to first[choices[1]],
                "p2" to first[choices[2]], "p3" to first[choices[3]],
                "prefixTokens" to prefix(tok, row).size, "firstPredictionMillis" to firstMillis,
                "caseMillis" to (System.nanoTime() - started) / 1e6)
        }
        emit("SCORE", "exact" to correct, "total" to data.size, "restrictedCorrect" to restricted,
            "answerNll" to nll / data.size, "prematureEos" to premature)
    }
    private fun retention(model: LmModel, tok: LmBpeTokenizer, texts: List<String>) {
        var nats = 0.0; var bytes = 0
        for (text in texts) {
            val sample = textSample(tok, text, model.config.context)
            nats += model.nll(sample.ids, sample.targets).dropLast(1).sum()
            bytes += text.toByteArray(Charsets.UTF_8).size
        }
        emit("RETENTION", "bpb" to nats / ln(2.0) / bytes, "bytes" to bytes)
        for ((index, text) in texts.take(4).withIndex()) {
            var ids = intArrayOf(tok.bos) + tok.encode(text.substringBefore('.').take(16))
            val generated = ArrayList<Int>(); var ended = false
            repeat(32) {
                if (!ended && ids.size < model.config.context) {
                    val next = argmax(model.nextDistribution(ids))
                    if (next == tok.eos) ended = true else { ids += next; generated += next }
                }
            }
            emit("CAPACITY", "id" to index, "tokens" to generated.size, "eos" to ended,
                "decodedBase64" to Base64.getEncoder().encodeToString(tok.decode(generated.toIntArray()).toByteArray(Charsets.UTF_8)))
        }
    }
    @JvmStatic fun main(args: Array<String>) {
        require(args.isNotEmpty())
        val mode = args[0]
        require(mode in listOf("inspect", "balanced-replay", "balanced-preserve", "eval"))
        require(args.size == if (mode == "eval") 5 else 7) {
            "inspect|balanced-replay|balanced-preserve baseline tokenizer train.tsv supervised_replay.tsv replay.txt outputDir; eval checkpoint tokenizer cases.tsv retention_text.txt"
        }
        val started = System.nanoTime()
        val baseline = Path.of(args[1]); val tokenizerPath = Path.of(args[2])
        val baselineBytes = Files.readAllBytes(baseline)
        require(mode == "eval" || sha(baselineBytes) == BASE)
        require(sha(Files.readAllBytes(tokenizerPath)) == TOK)
        val tok = LmBpeTokenizer.deserialize(Files.readAllBytes(tokenizerPath))
        val restored = LmCheckpoint.decode(baselineBytes, tok)
        val model = LmModel(restored.params)
        require(model.params.count == 132160 && model.config.arch == LmArch.MINGRU && model.config.context == 256)
        emit("MODEL", "sha256" to sha(baselineBytes), "tokenizerSha256" to TOK,
            "parameters" to model.params.count, "mode" to mode, "sourceScope" to "RESEARCH_SHADOW")
        if (mode == "eval") {
            val cases = Path.of(args[3]); val texts = Path.of(args[4])
            val inputs = listOf(baseline, tokenizerPath, cases, texts).associateWith { sha(Files.readAllBytes(it)) }
            inputs.forEach { (path, hash) -> emit("INPUT", "file" to path.fileName, "sha256" to hash) }
            score(model, tok, rows(cases))
            retention(model, tok, Files.readAllLines(texts).filter { it.isNotBlank() }.also { require(it.size >= 4) })
            check(inputs.all { (path, hash) -> sha(Files.readAllBytes(path)) == hash })
            emit("RESULT", "updates" to 0, "inputUnchanged" to true, "elapsedMillis" to (System.nanoTime() - started) / 1000000)
            return
        }
        val trainPath = Path.of(args[3]); val supervisedPath = Path.of(args[4]); val replayPath = Path.of(args[5])
        val train = rows(trainPath); val supervised = rows(supervisedPath)
        val replayTexts = Files.readAllLines(replayPath).filter { it.isNotBlank() }
        require(train.map { it.id }.toSet().intersect(supervised.map { it.id }.toSet()).isEmpty())
        require(train.map { it.prompt }.toSet().intersect(supervised.map { it.prompt }.toSet()).isEmpty())
        val groups = (0..3).map { label -> train.filter { it.label == "$label" } }
        require(groups.all { it.isNotEmpty() && it.size == groups.first().size })
        require(supervised.all { it.label in listOf("1", "2") })
        val oldGroups = (1..2).map { label -> supervised.filter { it.label == "$label" } }
        require(oldGroups[0].isNotEmpty() && oldGroups[0].size == oldGroups[1].size)
        val samples = (train + supervised).associate { it.id to answer(tok, it, model.config.context) }
        val replay = replayTexts.map { textSample(tok, it, model.config.context) }
        require(replay.size >= 2)
        val inputs = listOf(baseline, tokenizerPath, trainPath, supervisedPath, replayPath).associateWith { sha(Files.readAllBytes(it)) }
        inputs.forEach { (path, hash) -> emit("INPUT", "file" to path.fileName, "sha256" to hash) }
        emit("CONFIG", "mode" to mode, "steps" to STEPS, "seed" to SEED, "rate" to RATE,
            "answerBatch" to 8, "supervisedReplayWithinAnswerBatch" to 2,
            "replayBatch" to 2, "replayWeight" to .25, "teacherWeight" to if (mode == "balanced-preserve") 1 else 0,
            "answerTargetsPerBatch" to 16, "weightDecay" to .01, "clip" to 1, "warmup" to 4)
        if (mode == "inspect") {
            emit("INSPECT", "train" to train.size, "supervisedReplay" to supervised.size,
                "replayTexts" to replay.size, "maximumPrefixTokens" to (train + supervised).maxOf { prefix(tok, it).size }, "updates" to 0)
            return
        }
        val out = Path.of(args[6]); Files.createDirectory(out)
        val teacher = if (mode == "balanced-preserve") LmModel(LmCheckpoint.decode(baselineBytes, tok).params) else null
        if (teacher != null) {
            check(teacher.params.mats.keys == model.params.mats.keys)
            for (name in model.params.mats.keys) check(teacher.params.mats.getValue(name).data !== model.params.mats.getValue(name).data)
        }
        val teacherBefore = teacher?.let { tensorDigest(it.params.mats.toList()) }
        if (teacherBefore != null) check(teacherBefore == tensorDigest(model.params.mats.toList()))
        val cache = teacher?.let { frozen -> replay.map { teacherProbabilities(frozen, it) } }
        val cacheBefore = cache?.let { tensorDigest(it.mapIndexed { i, p -> "$i" to p }) }
        if (teacher != null && cache != null) emit("TEACHER", "phase" to "before", "parameterSha256" to teacherBefore!!,
            "cacheSha256" to cacheBefore!!, "examples" to replay.size, "rows" to cache.sumOf { it.rows }, "originalCheckpointSha256" to BASE)
        val gradients = model.params.zeros(); val optimizer = LmAdamW(model.params, .01f); val rng = Random(SEED)
        for (step in 0 until STEPS) {
            check((System.nanoTime() - started) / 1e9 < 590) { "pilot deadline exceeded" }
            for (g in gradients.values) g.data.fill(0f)
            // Two examples per label; one of each open/search pair is simple supervised replay.
            val selected = (0..3).flatMap { label ->
                val one = groups[label][rng.nextInt(groups[label].size)]
                val source = if (label in 1..2) oldGroups[label - 1] else groups[label]
                listOf(one, source[rng.nextInt(source.size)])
            }
            check((0..3).all { label -> selected.count { it.label == "$label" } == 2 })
            val replayIndices = listOf((step * 2) % replay.size, (step * 2 + 1) % replay.size)
            emit("SCHEDULE", "step" to step + 1, "answerIds" to selected.joinToString(",") { it.id },
                "replayIndices" to replayIndices.joinToString(","))
            fun accumulate(batch: List<Sample>, divisor: Int): Double {
                var total = 0.0
                for (sample in batch) {
                    val tape = Tape()
                    val nodes = model.params.mats.mapValues { (name, matrix) -> tape.param(matrix, gradients.getValue(name)) }
                    val loss = tape.crossEntropy(model.logits(tape, nodes, sample.ids), sample.targets, divisor)
                    check(loss.value.data[0].isFinite()); total += loss.value.data[0]; tape.backward(loss)
                }
                return total
            }
            val answerLoss = accumulate(selected.map { samples.getValue(it.id) }, 16)
            val textBatch = replayIndices.map { replay[it] }
            val replayLoss = accumulate(textBatch, 4 * textBatch.sumOf { it.targets.size })
            var kl = 0.0
            if (cache != null) for (index in replayIndices) {
                val tape = Tape()
                val nodes = model.params.mats.mapValues { (name, matrix) -> tape.param(matrix, gradients.getValue(name)) }
                val loss = tape.softTargetKl(model.logits(tape, nodes, replay[index].ids), cache[index], textBatch.sumOf { it.ids.size })
                check(loss.value.data[0].isFinite()); kl += loss.value.data[0]; tape.backward(loss)
            }
            val rate = if (step < 4) RATE * (step + 1) / 4 else
                (RATE * (.1 + .9 * .5 * (1 + cos(PI * (step - 4) / (STEPS - 4))))).toFloat()
            val norm = optimizer.step(gradients, rate, 1f)
            check(model.params.allFinite())
            emit("UPDATE", "step" to step + 1, "rate" to rate, "answerLoss" to answerLoss,
                "replayWeightedLoss" to replayLoss, "teacherKl" to kl, "gradientNorm" to norm)
        }
        if (teacher != null && cache != null) {
            check(teacherBefore == tensorDigest(teacher.params.mats.toList()))
            check(cacheBefore == tensorDigest(cache.mapIndexed { i, p -> "$i" to p }))
            emit("TEACHER", "phase" to "after", "parameterSha256" to teacherBefore!!, "cacheSha256" to cacheBefore!!, "unchanged" to true)
        }
        val checkpoint = LmCheckpoint.encode(model.params, tok, restored.meta + mapOf(
            "experiment" to "personal-voice-20261111", "scope" to "RESEARCH_SHADOW", "arm" to mode,
            "parent" to BASE, "adaptSteps" to "$STEPS", "seed" to "$SEED",
            "data" to inputs.getValue(trainPath), "supervisedReplay" to inputs.getValue(supervisedPath)))
        Files.write(out.resolve("final.lm1"), checkpoint, StandardOpenOption.CREATE_NEW)
        check(LmCheckpoint.decode(checkpoint, tok).params.count == 132160)
        check(inputs.all { (path, hash) -> sha(Files.readAllBytes(path)) == hash })
        emit("CHECKPOINT", "sha256" to sha(checkpoint), "bytes" to checkpoint.size)
        emit("RESULT", "updates" to optimizer.steps, "inputUnchanged" to true, "elapsedMillis" to (System.nanoTime() - started) / 1000000)
    }
}
