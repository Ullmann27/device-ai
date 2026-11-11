package at.heinz.deviceai.learning.lm

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import kotlin.math.ln

object LmSemanticEvaluator {
    private const val TOKENIZER = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
    private data class Row(val id: String, val label: Int, val prompt: String)
    private fun sha(bytes: ByteArray) = LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun emit(kind: String, vararg fields: Pair<String, Any>) =
        println((listOf(kind) + fields.map { "${it.first}=${it.second}" }).joinToString("\t"))
    private fun encoded(text: String) = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
    private fun rows(path: Path): List<Row> {
        val lines = Files.readAllLines(path)
        require(lines.first() == "id\tlabel\tprompt")
        return lines.drop(1).filter { it.isNotBlank() }.map {
            val fields = it.split('\t')
            require(fields.size == 3 && fields[1] in listOf("0", "1", "2", "3"))
            Row(fields[0], fields[1].toInt(), fields[2])
        }.also { require(it.isNotEmpty() && it.map { row -> row.id }.distinct().size == it.size) }
    }
    private fun prefix(tok: LmBpeTokenizer, row: Row) =
        intArrayOf(tok.bos) + tok.encode("Auftrag: ${row.prompt}\n")
    private fun argmax(values: FloatArray): Int {
        require(values.all { it.isFinite() && it >= 0f })
        var best = 0
        for (i in 1 until values.size) if (values[i] > values[best]) best = i
        return best
    }
    private fun classify(model: LmModel, tok: LmBpeTokenizer, data: List<Row>, split: String) {
        val choices = (0..3).map { tok.encode(it.toString()).single() }
        for (row in data) {
            var ids = prefix(tok, row)
            val first = model.nextDistribution(ids)
            val predicted = choices.indices.maxBy { first[choices[it]] }
            val generated = ArrayList<Int>()
            var ended = false
            repeat(4) {
                if (!ended) {
                    val next = argmax(if (generated.isEmpty()) first else model.nextDistribution(ids))
                    if (next == tok.eos) ended = true
                    else { generated += next; ids += next }
                }
            }
            val exact = ended && generated.size == 1 && generated.single() == choices[row.label]
            emit("CASE", "split" to split, "id" to row.id, "expected" to row.label,
                "predictedRestricted" to predicted, "tokens" to generated.joinToString(","),
                "ended" to ended, "exact" to exact, "firstEos" to first[tok.eos],
                "p0" to first[choices[0]], "p1" to first[choices[1]],
                "p2" to first[choices[2]], "p3" to first[choices[3]],
                "decodedBase64" to encoded(tok.decode(generated.toIntArray())))
        }
    }
    private fun text(model: LmModel, tok: LmBpeTokenizer, texts: List<String>, split: String) {
        for ((index, reference) in texts.withIndex()) {
            val content = tok.encode(reference)
            val input = intArrayOf(tok.bos) + content
            val nll = model.nll(input, content + tok.eos)
            val bytes = reference.toByteArray(Charsets.UTF_8).size
            val nats = nll.dropLast(1).sum()
            emit("TEXT", "split" to split, "id" to index, "nats" to nats,
                "bytes" to bytes, "bpb" to nats / ln(2.0) / bytes,
                "tokens" to content.size)
            val pre = reference.substringBefore('.').take(16)
            var ids = intArrayOf(tok.bos) + tok.encode(pre)
            var ended = false
            val generated = ArrayList<Int>()
            repeat(32) {
                if (!ended && ids.size < model.config.context) {
                    val next = argmax(model.nextDistribution(ids))
                    if (next == tok.eos) ended = true
                    else { generated += next; ids += next }
                }
            }
            emit("CAPACITY", "split" to split, "id" to index, "tokens" to generated.size,
                "eos" to ended, "generated" to generated.joinToString(","),
                "prefixBase64" to encoded(pre),
                "decodedBase64" to encoded(tok.decode(generated.toIntArray())))
        }
    }
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 5) { "inspect|evaluate checkpoint tokenizer holdoutDir legacyDataDir" }
        val mode = args[0]
        require(mode in listOf("inspect", "evaluate"))
        val checkpoint = Path.of(args[1]); val tokenizer = Path.of(args[2])
        val holdout = Path.of(args[3]); val legacy = Path.of(args[4])
        val tokBytes = Files.readAllBytes(tokenizer)
        require(sha(tokBytes) == TOKENIZER)
        val tok = LmBpeTokenizer.deserialize(tokBytes)
        val originalBytes = Files.readAllBytes(checkpoint)
        val restored = LmCheckpoint.decode(originalBytes, tok)
        val model = LmModel(restored.params)
        require(model.params.count == 132160)
        val classification = linkedMapOf("semantic" to holdout.resolve("semantic.tsv"),
            "legacyRetention" to legacy.resolve("retention.tsv"),
            "novelRetention" to holdout.resolve("novel-retention.tsv"))
        val textPaths = linkedMapOf("legacyText" to legacy.resolve("retention_text.txt"),
            "novelText" to holdout.resolve("novel-text.txt"))
        val paths = listOf(checkpoint, tokenizer) + classification.values + textPaths.values
        val hashes = paths.associateWith { sha(Files.readAllBytes(it)) }
        hashes.forEach { (path, hash) -> emit("INPUT", "file" to path.fileName, "sha256" to hash) }
        val sets = classification.mapValues { (_, path) -> rows(path) }
        val texts = textPaths.mapValues { (_, path) -> Files.readAllLines(path).filter { it.isNotBlank() } }
        emit("CONFIG", "mode" to mode, "parameters" to model.params.count,
            "context" to model.config.context, "checkpointSha256" to hashes.getValue(checkpoint),
            "tokenizerSha256" to TOKENIZER, "updates" to 0)
        (0..3).forEach { emit("LABEL", "label" to it, "token" to tok.encode(it.toString()).single()) }
        sets.forEach { (split, data) ->
            data.forEach { row ->
                val length = prefix(tok, row).size
                require(length + 4 <= model.config.context) { "context overflow ${row.id}: $length" }
                emit("LENGTH", "split" to split, "id" to row.id,
                    "prefixTokens" to length, "promptTokens" to tok.encode(row.prompt).size,
                    "bytes" to row.prompt.toByteArray(Charsets.UTF_8).size)
            }
        }
        texts.forEach { (_, data) -> data.forEach { require(tok.encode(it).size + 1 <= model.config.context) } }
        if (mode == "inspect") { emit("RESULT", "updates" to 0, "inputUnchanged" to true); return }
        val started = System.nanoTime()
        sets.forEach { (split, data) -> classify(model, tok, data, split) }
        texts.forEach { (split, data) -> text(model, tok, data, split) }
        check(hashes.all { (path, hash) -> sha(Files.readAllBytes(path)) == hash })
        check(sha(Files.readAllBytes(checkpoint)) == sha(originalBytes))
        emit("RESULT", "updates" to 0, "inputUnchanged" to true,
            "elapsedMillis" to (System.nanoTime() - started) / 1000000)
    }
}
