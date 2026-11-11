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

object LmSemanticPilot {
    private const val STEPS = 128
    private const val SEED = 20261111L
    private const val BASE = "7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd"
    private const val TOK = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
    private val spec = LmTrainingSpec(.0001f, .01f, 4, STEPS, 1f, 1)
    private data class Row(val id: String, val label: Int, val prompt: String)
    private data class Rollout(val id: String, val prefix: String, val tokens: IntArray)
    private data class Answer(val ids: IntArray, val label: IntArray, val eos: IntArray)
    private data class Cache(val ids: IntArray, val start: Int, val probabilities: Mat)
    private fun sha(bytes: ByteArray) = LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun sha(path: Path) = sha(Files.readAllBytes(path))
    private fun emit(kind: String, vararg fields: Pair<String, Any>) =
        println((listOf(kind) + fields.map { "${it.first}=${it.second}" }).joinToString("\t"))
    private fun b64(text: String) = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
    private fun unb64(text: String) = String(Base64.getDecoder().decode(text), Charsets.UTF_8)
    private fun rows(path: Path): List<Row> {
        val lines = Files.readAllLines(path)
        require(lines.first() == "id\tlabel\tprompt")
        return lines.drop(1).filter { it.isNotBlank() }.map {
            val p = it.split('\t')
            require(p.size == 3 && p[1] in listOf("0", "1", "2", "3"))
            Row(p[0], p[1].toInt(), p[2])
        }.also { require(it.isNotEmpty() && it.map { r -> r.id }.distinct().size == it.size) }
    }
    private fun prefix(tok: LmBpeTokenizer, row: Row) =
        intArrayOf(tok.bos) + tok.encode("Auftrag: ${row.prompt}\n")
    private fun answer(tok: LmBpeTokenizer, row: Row, context: Int): Answer {
        val pre = prefix(tok, row)
        val label = tok.encode(row.label.toString())
        require(label.size == 1 && pre.size + 4 <= context)
        val ids = pre + label
        val labels = IntArray(ids.size) { -1 }
        val eos = IntArray(ids.size) { -1 }
        labels[pre.lastIndex] = label.single()
        eos[ids.lastIndex] = tok.eos
        check(labels.count { it >= 0 } == 1 && eos.count { it >= 0 } == 1)
        return Answer(ids, labels, eos)
    }
    private fun argmax(p: FloatArray): Int {
        require(p.all { it.isFinite() })
        var best = 0
        for (i in 1 until p.size) if (p[i] > p[best]) best = i
        return best
    }
    private fun restricted(model: LmModel, tok: LmBpeTokenizer, row: Row): Int {
        val p = model.nextDistribution(prefix(tok, row))
        return (0..3).maxBy { p[tok.encode(it.toString()).single()] }
    }
    private fun generate(model: LmModel, tok: LmBpeTokenizer, prefix: String): IntArray {
        var ids = intArrayOf(tok.bos) + tok.encode(prefix)
        val made = ArrayList<Int>()
        while (made.size < 32 && ids.size < model.config.context) {
            val next = argmax(model.nextDistribution(ids))
            if (next == tok.eos) break
            made += next
            ids += next
        }
        return made.toIntArray()
    }
    private fun digest(mats: List<Pair<String, Mat>>): String {
        val d = MessageDigest.getInstance("SHA-256")
        for ((name, mat) in mats) {
            d.update("$name:${mat.rows}:${mat.cols}\n".toByteArray(Charsets.UTF_8))
            val bytes = ByteBuffer.allocate(mat.data.size * 4)
            mat.data.forEach(bytes::putFloat)
            d.update(bytes.array())
        }
        return LmBpeTokenizer.hex(d.digest())
    }
    private fun cache(model: LmModel, tok: LmBpeTokenizer, rollout: Rollout): Cache {
        val pre = intArrayOf(tok.bos) + tok.encode(rollout.prefix)
        val ids = pre + rollout.tokens.dropLast(1).toIntArray()
        require(rollout.tokens.size in 24..32 && ids.size <= model.config.context)
        val tape = Tape(training = false)
        val all = model.logits(tape, model.frozenNodes(tape), ids)
        val selected = tape.rows(all, pre.lastIndex, rollout.tokens.size).value
        val p = Mat(selected.rows, selected.cols)
        for (r in 0 until p.rows) Kernels.softmaxRow(selected.data, r * p.cols, p.cols, p.data)
        return Cache(ids, pre.lastIndex, p)
    }
    private fun prepare(model: LmModel, tok: LmBpeTokenizer, train: List<Row>, fixtures: Path, out: Path) {
        Files.createDirectory(out)
        val candidates = rows(fixtures.resolve("retention-candidates.tsv"))
        require(candidates.all { it.label in 1..2 })
        require(train.map { it.prompt }.toSet().intersect(candidates.map { it.prompt }.toSet()).isEmpty())
        val correct = candidates.filter { row ->
            answer(tok, row, model.config.context)
            val prediction = restricted(model, tok, row)
            emit("ANCHOR", "id" to row.id, "gold" to row.label, "restricted" to prediction,
                "correct" to (prediction == row.label), "promptBase64" to b64(row.prompt))
            prediction == row.label
        }
        val quota = minOf(8, correct.count { it.label == 1 }, correct.count { it.label == 2 })
        val chosen = (1..2).flatMap { label -> correct.filter { it.label == label }.take(quota) }
        Files.writeString(out.resolve("anchors.tsv"), "id\tlabel\tprompt\n" +
            chosen.joinToString("") { "${it.id}\t${it.label}\t${it.prompt}\n" }, StandardOpenOption.CREATE_NEW)
        val lines = Files.readAllLines(fixtures.resolve("continuation-prefixes.tsv"))
        require(lines.first() == "id\tprefix")
        val rollouts = lines.drop(1).filter { it.isNotBlank() }.map {
            val p = it.split('\t'); require(p.size == 2)
            val made = generate(model, tok, p[1])
            emit("ORIGINAL_ROLLOUT", "id" to p[0], "tokens" to made.joinToString(","),
                "length" to made.size, "textBase64" to b64(tok.decode(made)))
            Rollout(p[0], p[1], made)
        }
        val selected = rollouts.filter { it.tokens.size >= 24 }.take(8)
        Files.writeString(out.resolve("rollouts.tsv"), "id\tprefixBase64\ttokens\n" + selected.joinToString("") {
            "${it.id}\t${b64(it.prefix)}\t${it.tokens.joinToString(",")}\n"
        }, StandardOpenOption.CREATE_NEW)
        Files.writeString(out.resolve("native-train-tokens.tsv"), "id\tprefixTokens\n" + train.joinToString("") {
            "${it.id}\t${prefix(tok, it).joinToString(",")}\n"
        }, StandardOpenOption.CREATE_NEW)
        val ready = quota >= 2 && selected.size >= 2
        emit("PREFLIGHT", "status" to if (ready) "PASS" else "INSUFFICIENT_PRESERVATION_ANCHORS",
            "originalCorrectOpen" to correct.count { it.label == 1 },
            "originalCorrectSearch" to correct.count { it.label == 2 }, "selectedPerLabel" to quota,
            "originalLongRollouts" to rollouts.count { it.tokens.size >= 24 }, "selectedRollouts" to selected.size,
            "trainRows" to train.size, "maxPrefixTokens" to train.maxOf { prefix(tok, it).size }, "updates" to 0)
    }
    private fun loadRollouts(path: Path): List<Rollout> {
        val lines = Files.readAllLines(path)
        require(lines.first() == "id\tprefixBase64\ttokens")
        return lines.drop(1).filter { it.isNotBlank() }.map {
            val p = it.split('\t'); require(p.size == 3)
            Rollout(p[0], unb64(p[1]), p[2].split(',').map(String::toInt).toIntArray())
        }.also { require(it.size in 2..8) }
    }
    private fun export(model: LmModel, optimizer: LmAdamW, tok: LmBpeTokenizer,
                       identity: LmTrainingIdentity, out: Path, arm: String, parentMeta: Map<String, String>): Path {
        val state = spec.trainer(model.params).use { container ->
            container.restoreOptimizerState(optimizer.snapshot())
            LmTrainingState.save(out.resolve("states"), container, tok, identity)
        }
        val weights = LmCheckpoint.encode(model.params, tok, parentMeta + mapOf(
            "experiment" to "lm1-semantic-20261111", "scope" to "RESEARCH_SHADOW", "arm" to arm,
            "parent" to BASE, "adaptSteps" to optimizer.steps.toString(),
            "data" to identity.dataSha256, "source" to identity.sourceSha256))
        val checkpoint = out.resolve("step-${optimizer.steps}.lm1")
        Files.write(checkpoint, weights, StandardOpenOption.CREATE_NEW)
        emit("CHECKPOINT", "step" to optimizer.steps, "file" to checkpoint.fileName,
            "sha256" to sha(weights), "stateFile" to state.fileName, "stateSha256" to sha(state))
        return state
    }
    private fun monitor(model: LmModel, tok: LmBpeTokenizer, rows: List<Row>, rollouts: List<Rollout>, step: Int): Boolean {
        var eos = 0
        var right = 0
        for (row in rows) {
            val p = model.nextDistribution(prefix(tok, row))
            if (argmax(p) == tok.eos) eos++
            if (restricted(model, tok, row) == row.label) right++
        }
        val lengths = rollouts.take(4).map { generate(model, tok, it.prefix).size }
        val collapsed = eos >= 7 || lengths.all { it < 4 }
        emit("MONITOR", "step" to step, "scope" to "TRAIN_ONLY", "firstEos" to eos,
            "rows" to rows.size, "restrictedCorrect" to right, "rolloutLengths" to lengths.joinToString(","),
            "collapse" to collapsed)
        return collapsed
    }
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 7) { "prepare|A|B baseline tokenizer train.tsv fixtures|prepared output sourceSha" }
        val mode = args[0]; require(mode in listOf("prepare", "A", "B"))
        val baseline = Path.of(args[1]); val tokenizerPath = Path.of(args[2]); val trainPath = Path.of(args[3])
        val support = Path.of(args[4]); val out = Path.of(args[5]); val sourceSha = args[6]
        require(Regex("[0-9a-f]{64}").matches(sourceSha))
        val baselineBytes = Files.readAllBytes(baseline)
        require(sha(baselineBytes) == BASE && sha(tokenizerPath) == TOK)
        val tok = LmBpeTokenizer.deserialize(Files.readAllBytes(tokenizerPath))
        val restored = LmCheckpoint.decode(baselineBytes, tok)
        var model = LmModel(restored.params)
        require(model.params.count == 132160 && model.config.arch == LmArch.MINGRU)
        val train = rows(trainPath)
        val groups = (0..3).map { label -> train.filter { it.label == label } }
        require(groups.all { it.isNotEmpty() } && groups.map { it.size }.distinct().size == 1)
        train.forEach { answer(tok, it, model.config.context) }
        emit("CONFIG", "mode" to mode, "parameters" to model.params.count, "stepsMaximum" to STEPS,
            "seed" to SEED, "trainSha256" to sha(trainPath), "labelWeight" to 1.0, "eosWeight" to .25,
            "retentionWeight" to if (mode == "B") .5 else 0.0, "rolloutKlWeight" to if (mode == "B") 1.0 else 0.0,
            "sourceSha256" to sourceSha, "learningRate" to .0001, "weightDecay" to .01,
            "warmup" to 4, "clip" to 1.0, "workers" to 1)
        if (mode == "prepare") {
            prepare(model, tok, train, support, out)
            check(sha(baseline) == BASE && sha(tokenizerPath) == TOK)
            return
        }
        val anchors = rows(support.resolve("anchors.tsv"))
        require(anchors.all { it.label in 1..2 })
        val anchorGroups = (1..2).map { label -> anchors.filter { it.label == label } }
        require(anchorGroups.all { it.size in 2..8 } && anchorGroups[0].size == anchorGroups[1].size)
        require(anchors.all { restricted(model, tok, it) == it.label })
        val rollouts = loadRollouts(support.resolve("rollouts.tsv"))
        require(rollouts.all { generate(model, tok, it.prefix).contentEquals(it.tokens) })
        require(train.map { it.prompt }.toSet().intersect(anchors.map { it.prompt }.toSet()).isEmpty())
        val inputPaths = listOf(baseline, tokenizerPath, trainPath, support.resolve("anchors.tsv"), support.resolve("rollouts.tsv"))
        val hashes = inputPaths.associateWith(::sha)
        hashes.forEach { (p, h) -> emit("INPUT", "file" to p.fileName, "sha256" to h) }
        val dataSha = sha(("LM1_SEMANTIC_V1\n$mode\n" + inputPaths.joinToString("\n") { hashes.getValue(it) } + "\n").toByteArray())
        val identity = LmTrainingIdentity(sourceSha, dataSha, SEED)
        Files.createDirectory(out)
        Files.createDirectory(out.resolve("states"))
        Files.createDirectory(out.resolve("roundtrip"))
        val started = System.nanoTime()
        val teacher = LmModel(LmCheckpoint.decode(baselineBytes, tok).params)
        require(teacher.params.mats.all { (name, mat) -> mat.data !== model.params.mats.getValue(name).data })
        val teacherSha = digest(teacher.params.mats.toList())
        val caches = if (mode == "B") rollouts.map { cache(teacher, tok, it) } else emptyList()
        val cacheSha = digest(caches.mapIndexed { i, c -> i.toString() to c.probabilities })
        emit("TEACHER", "phase" to "before", "weightsSha256" to teacherSha, "cacheSha256" to cacheSha,
            "rolloutCount" to caches.size, "rows" to caches.sumOf { it.probabilities.rows })
        val rng = Random(SEED)
        val schedule = List(STEPS) { groups.map { group -> group[rng.nextInt(group.size)] } }
        val monitors = groups.flatMap { it.take(2) }
        var optimizer = LmAdamW(model.params, .01f)
        var gradients = model.params.zeros()
        var stopped = false
        monitor(model, tok, monitors, rollouts, 0)
        for (step in 0 until STEPS) {
            check((System.nanoTime() - started) / 1e9 < 600) { "native deadline exceeded" }
            for (g in gradients.values) g.data.fill(0f)
            var labelLoss = 0.0; var eosLoss = 0.0; var retentionLoss = 0.0; var kl = 0.0
            for (row in schedule[step]) {
                val sample = answer(tok, row, model.config.context)
                val tape = Tape()
                val nodes = model.params.mats.mapValues { (n, m) -> tape.param(m, gradients.getValue(n)) }
                val logits = model.logits(tape, nodes, sample.ids)
                val label = tape.crossEntropy(logits, sample.label, 4)
                val eos = tape.crossEntropy(logits, sample.eos, 16)
                val loss = tape.add(label, eos)
                require(loss.value.data.single().isFinite())
                labelLoss += label.value.data.single(); eosLoss += eos.value.data.single()
                tape.backward(loss)
            }
            val selectedAnchors = anchorGroups.map { it[step % it.size] }
            if (mode == "B") {
                for (row in selectedAnchors) {
                    val sample = answer(tok, row, model.config.context)
                    val tape = Tape()
                    val nodes = model.params.mats.mapValues { (n, m) -> tape.param(m, gradients.getValue(n)) }
                    val loss = tape.crossEntropy(model.logits(tape, nodes, sample.ids), sample.label, 4)
                    require(loss.value.data.single().isFinite())
                    retentionLoss += loss.value.data.single()
                    tape.backward(loss)
                }
                val cached = caches[step % caches.size]
                val tape = Tape()
                val nodes = model.params.mats.mapValues { (n, m) -> tape.param(m, gradients.getValue(n)) }
                val logits = model.logits(tape, nodes, cached.ids)
                val selected = tape.rows(logits, cached.start, cached.probabilities.rows)
                val loss = tape.softTargetKl(selected, cached.probabilities, cached.probabilities.rows)
                require(loss.value.data.single().isFinite())
                kl = loss.value.data.single().toDouble()
                tape.backward(loss)
            }
            val rate = if (step < 4) .0001f * (step + 1) / 4 else
                (.0001 * (.1 + .9 * .5 * (1 + cos(PI * (step - 4) / (STEPS - 4))))).toFloat()
            val norm = optimizer.step(gradients, rate, 1f)
            require(model.params.allFinite())
            emit("UPDATE", "step" to optimizer.steps, "answerIds" to schedule[step].joinToString(",") { it.id },
                "retentionIds" to if (mode == "B") selectedAnchors.joinToString(",") { it.id } else "none",
                "rolloutId" to if (mode == "B") rollouts[step % rollouts.size].id else "none",
                "labelLoss" to labelLoss, "eosWeightedLoss" to eosLoss,
                "retentionWeightedLoss" to retentionLoss, "rolloutKl" to kl, "rate" to rate, "gradientNorm" to norm)
            if (optimizer.steps % 32 == 0) {
                if (monitor(model, tok, monitors, rollouts, optimizer.steps)) {
                    stopped = true
                    export(model, optimizer, tok, identity, out, mode, restored.meta)
                    break
                }
            }
            if (optimizer.steps == 64) {
                val saved = export(model, optimizer, tok, identity, out, mode, restored.meta)
                LmTrainingState.load(saved, sha(saved), tok, identity, spec).use { resumed ->
                    val again = LmTrainingState.save(out.resolve("roundtrip"), resumed, tok, identity)
                    check(sha(saved) == sha(again))
                    model = resumed.model
                    optimizer = LmAdamW(model.params, .01f).also { it.restore(resumed.optimizerState()) }
                    gradients = model.params.zeros()
                    emit("RESUME", "step" to optimizer.steps, "stateSha256" to sha(saved), "byteIdentical" to true,
                        "additionalUpdates" to 0)
                }
            }
        }
        if (!stopped) export(model, optimizer, tok, identity, out, mode, restored.meta)
        val moments = optimizer.snapshot()
        require(moments.first.values.any { a -> a.any { it != 0f } } && moments.second.values.any { a -> a.any { it != 0f } })
        require(hashes.all { (p, h) -> sha(p) == h })
        check(teacherSha == digest(teacher.params.mats.toList()))
        check(cacheSha == digest(caches.mapIndexed { i, c -> i.toString() to c.probabilities }))
        emit("TEACHER", "phase" to "after", "weightsSha256" to teacherSha, "cacheSha256" to cacheSha,
            "unchanged" to true)
        emit("RESULT", "status" to if (stopped) "EARLY_STOP_REJECTED" else "COMPLETED_NOT_PROMOTED",
            "updates" to optimizer.steps, "inputUnchanged" to true,
            "firstNonzero" to moments.first.values.sumOf { a -> a.count { it != 0f } },
            "secondNonzero" to moments.second.values.sumOf { a -> a.count { it != 0f } },
            "elapsedMillis" to (System.nanoTime() - started) / 1000000)
    }
}
