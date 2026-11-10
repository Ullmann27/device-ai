package at.heinz.deviceai.learning.lm

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln

object LmContrastPilot {
    private const val STEPS = 128
    private const val BASE = "7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd"
    private const val TOK = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
    private data class Row(val id: String, val label: String, val prompt: String)
    private data class Sample(val ids: IntArray, val targets: IntArray)
    private fun sha(bytes: ByteArray) = LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun emit(kind: String, vararg fields: Pair<String, Any>) =
        println((listOf(kind) + fields.map { "${it.first}=${it.second}" }).joinToString("\t"))
    private fun rows(path: Path): List<Row> {
        val lines = Files.readAllLines(path)
        require(lines.first() == "id\tlabel\tprompt")
        return lines.drop(1).filter { it.isNotBlank() }.map {
            val fields = it.split('\t')
            require(fields.size == 3 && fields[1] in listOf("0", "1", "2", "3"))
            Row(fields[0], fields[1], fields[2])
        }.also { require(it.isNotEmpty() && it.map { row -> row.id }.distinct().size == it.size) }
    }
    private fun prefix(tok: LmBpeTokenizer, row: Row) =
        intArrayOf(tok.bos) + tok.encode("Auftrag: ${row.prompt}\n")
    private fun answer(tok: LmBpeTokenizer, row: Row, context: Int): Sample {
        val pre = prefix(tok, row)
        val label = tok.encode(row.label)
        require(label.size == 1 && pre.size + 4 <= context)
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
        require(values.all { it.isFinite() })
        var best = 0
        for (i in 1 until values.size) if (values[i] > values[best]) best = i
        return best
    }
    private fun score(model: LmModel, tok: LmBpeTokenizer, data: List<Row>, split: String, step: Int) {
        var correct = 0
        var classification = 0
        var premature = 0
        var nll = 0.0
        for (row in data) {
            val sample = answer(tok, row, model.config.context)
            nll += model.nll(sample.ids, sample.targets)[sample.ids.size - 2]
            var ids = prefix(tok, row)
            val first = model.nextDistribution(ids)
            val choices = (0..3).map { tok.encode(it.toString()).single() }
            val predicted = choices.indices.maxBy { first[choices[it]] }
            if (predicted.toString() == row.label) classification++
            val generated = ArrayList<Int>()
            var ended = false
            repeat(4) {
                if (!ended) {
                    val next = argmax(if (generated.isEmpty()) first else model.nextDistribution(ids))
                    if (next == tok.eos) ended = true
                    else { generated += next; ids += next }
                }
            }
            val decoded = tok.decode(generated.toIntArray())
            val ok = ended && generated.size == 1 && decoded == row.label
            if (ok) correct++
            if (ended && generated.isEmpty()) premature++
            emit("CASE", "split" to split, "step" to step, "id" to row.id, "expected" to row.label,
                "predictedRestricted" to predicted, "tokens" to generated.joinToString(","),
                "ended" to ended, "exact" to ok, "firstEos" to first[tok.eos])
        }
        emit("SCORE", "split" to split, "step" to step, "exact" to correct, "total" to data.size,
            "restrictedCorrect" to classification, "answerNll" to nll / data.size, "prematureEos" to premature)
    }
    private fun retention(model: LmModel, tok: LmBpeTokenizer, texts: List<String>, step: Int) {
        var nats = 0.0
        var bytes = 0
        for (text in texts) {
            val sample = textSample(tok, text, model.config.context)
            val values = model.nll(sample.ids, sample.targets)
            nats += values.dropLast(1).sum()
            bytes += text.toByteArray(Charsets.UTF_8).size
        }
        emit("RETENTION", "step" to step, "bpb" to nats / ln(2.0) / bytes, "bytes" to bytes)
        for ((index, text) in texts.take(4).withIndex()) {
            var ids = intArrayOf(tok.bos) + tok.encode(text.substringBefore('.').take(16))
            var count = 0
            var ended = false
            repeat(32) {
                if (!ended && ids.size < model.config.context) {
                    val next = argmax(model.nextDistribution(ids))
                    if (next == tok.eos) ended = true else { ids += next; count++ }
                }
            }
            emit("CAPACITY", "step" to step, "id" to index, "tokens" to count, "eos" to ended)
        }
    }
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 5) { "inspect|answer|replay baseline tokenizer dataDir outputDir" }
        val mode = args[0]
        require(mode in listOf("inspect", "answer", "replay"))
        val baseline = Path.of(args[1]); val tokenizerPath = Path.of(args[2]); val data = Path.of(args[3])
        val baselineBytes = Files.readAllBytes(baseline)
        require(sha(baselineBytes) == BASE && sha(Files.readAllBytes(tokenizerPath)) == TOK)
        val tok = LmBpeTokenizer.deserialize(Files.readAllBytes(tokenizerPath))
        val restored = LmCheckpoint.decode(baselineBytes, tok)
        val model = LmModel(restored.params)
        require(model.params.count == 132160)
        val train = rows(data.resolve("train.tsv"))
        val dev = rows(data.resolve("dev.tsv"))
        val retain = rows(data.resolve("retention.tsv"))
        val replayTexts = Files.readAllLines(data.resolve("replay.txt")).filter { it.isNotBlank() }
        val retainTexts = Files.readAllLines(data.resolve("retention_text.txt")).filter { it.isNotBlank() }
        require(replayTexts.isNotEmpty() && retainTexts.size >= 4)
        require(replayTexts.toSet().intersect(retainTexts.toSet()).isEmpty())
        require(train.map { it.prompt }.toSet().intersect((dev + retain).map { it.prompt }.toSet()).isEmpty())
        require(train.all { it.label in listOf("0", "3") } && retain.all { it.label in listOf("1", "2") })
        val groups = listOf("0", "3").map { label -> train.filter { it.label == label } }
        require(groups[0].size == groups[1].size && groups[0].isNotEmpty())
        val examples = groups.map { group -> group.map { answer(tok, it, model.config.context) } }
        (dev + retain).forEach { answer(tok, it, model.config.context) }
        val replay = replayTexts.map { textSample(tok, it, model.config.context) }
        retainTexts.forEach { textSample(tok, it, model.config.context) }
        val paths = listOf(baseline, tokenizerPath) + listOf("train.tsv", "dev.tsv", "retention.tsv", "replay.txt", "retention_text.txt").map(data::resolve)
        val hashes = paths.associateWith { sha(Files.readAllBytes(it)) }
        hashes.forEach { (p, hash) -> emit("INPUT", "file" to p.fileName, "sha256" to hash) }
        emit("CONFIG", "mode" to mode, "steps" to STEPS, "parameters" to model.params.count,
            "seed" to 20261110, "rate" to .0001, "answerBatch" to 4, "replayBatch" to 2, "replayWeight" to .1)
        if (mode == "inspect") {
            emit("INSPECT", "train" to train.size, "dev" to dev.size, "retention" to retain.size, "updates" to 0)
            return
        }
        val out = Path.of(args[4]); Files.createDirectory(out)
        val started = System.nanoTime()
        score(model, tok, dev, "dev", 0); score(model, tok, retain, "retention", 0)
        retention(model, tok, retainTexts, 0)
        val gradients = model.params.zeros()
        val optimizer = LmAdamW(model.params, .01f)
        val rng = Random(20261110L)
        for (step in 0 until STEPS) {
            check((System.nanoTime() - started) / 1e9 < 600) { "pilot deadline exceeded" }
            for (g in gradients.values) g.data.fill(0f)
            val selected = (0..1).flatMap { label -> List(2) { examples[label][rng.nextInt(examples[label].size)] } }
            fun accumulate(samples: List<Sample>, divisor: Int): Double {
                var total = 0.0
                for (sample in samples) {
                    val tape = Tape()
                    val nodes = model.params.mats.mapValues { (name, matrix) -> tape.param(matrix, gradients.getValue(name)) }
                    val loss = tape.crossEntropy(model.logits(tape, nodes, sample.ids), sample.targets, divisor)
                    check(loss.value.data[0].isFinite())
                    total += loss.value.data[0]
                    tape.backward(loss)
                }
                return total
            }
            val answerLoss = accumulate(selected, 8)
            val textBatch = listOf(replay[(step * 2) % replay.size], replay[(step * 2 + 1) % replay.size])
            val replayLoss = if (mode == "replay") accumulate(textBatch, 10 * textBatch.sumOf { it.targets.size }) else 0.0
            val rate = if (step < 4) .0001f * (step + 1) / 4 else
                (.0001 * (.1 + .9 * .5 * (1 + cos(PI * (step - 4) / (STEPS - 4))))).toFloat()
            val norm = optimizer.step(gradients, rate, 1f)
            check(model.params.allFinite())
            emit("UPDATE", "step" to step + 1, "answerLoss" to answerLoss, "replayWeightedLoss" to replayLoss, "gradientNorm" to norm)
        }
        val checkpoint = LmCheckpoint.encode(model.params, tok, restored.meta + mapOf(
            "experiment" to "contrast-dev-20261110", "scope" to "RESEARCH_SHADOW", "arm" to mode,
            "parent" to BASE, "adaptSteps" to "$STEPS", "data" to hashes.getValue(data.resolve("train.tsv"))))
        Files.write(out.resolve("final.lm1"), checkpoint, StandardOpenOption.CREATE_NEW)
        check(LmCheckpoint.decode(checkpoint, tok).params.count == 132160)
        emit("CHECKPOINT", "sha256" to sha(checkpoint), "bytes" to checkpoint.size)
        score(model, tok, dev, "dev", STEPS); score(model, tok, retain, "retention", STEPS)
        retention(model, tok, retainTexts, STEPS)
        check(hashes.all { (p, hash) -> sha(Files.readAllBytes(p)) == hash })
        emit("RESULT", "updates" to optimizer.steps, "inputUnchanged" to true, "elapsedMillis" to (System.nanoTime() - started) / 1000000)
    }
}
