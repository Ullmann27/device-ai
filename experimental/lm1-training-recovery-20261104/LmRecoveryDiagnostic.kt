package at.heinz.deviceai.learning.lm

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Random
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/** Isolated EXP-04/05 diagnostic. Run from the repository root; never registers or promotes a model. */
object LmRecoveryDiagnostic {
    private const val BASE = "7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd"
    private const val TOK = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
    private const val TRAIN = "27e67455e23dcc410855b2fd0f0292f9a00556d72d62aefb018e9eab4be034c9"
    private const val PLAN = "8e0d6ef53de6c84e48c74ecc4dbd4b70e25632caadc624f31ad0ac4aad681fee"
    private const val SOURCE = "30cd3a89810ed14fa84eca95236d3177e016118d"
    private const val SEED = 20261104L
    private const val STEPS = 16
    private const val BATCH = 4
    private const val PROMPT = "Ein kurzer Bericht beschreibt den Ablauf einer Prüfung. " +
        "Zuerst wird die Aufgabe gelesen, danach wird das Ergebnis geprüft."
    private fun hash(bytes: ByteArray) = LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun read(file: File, limit: Int): ByteArray {
        require(!Files.isSymbolicLink(file.toPath()) && file.isFile && file.length() in 1..limit.toLong())
        return file.inputStream().use { it.readNBytes(limit + 1) }.also { require(it.size in 1..limit) }
    }
    private fun logits(model: LmModel, ids: IntArray): FloatArray {
        val tape = Tape(false)
        return model.logits(tape, model.frozenNodes(tape), ids).value.data.also { check(it.all(Float::isFinite)) }
    }
    private fun delta(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size)
        return a.indices.maxOf { abs(a[it].toDouble() - b[it]) }.also { check(it.isFinite()) }
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 6) { "usage (repository cwd): baseline tokenizer corpus development NEW_OUTPUT variant" }
        val out = File(args[4]).absoluteFile
        require(!Files.exists(out.toPath())) { "output must not exist; no resume or overwrite" }
        Files.createDirectories(out.parentFile.toPath()); Files.createDirectory(out.toPath())
        Files.newBufferedWriter(File(out, "diagnostic.tsv").toPath(), StandardOpenOption.CREATE_NEW).use { log ->
            fun emit(kind: String, fields: List<Pair<String, Any?>>) {
                val line = (listOf(kind) + fields.map { (k, v) -> "$k=${v.toString().replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')}" }).joinToString("\t")
                println(line); log.append(line).append('\n'); log.flush()
            }
            try { run(args, out, ::emit) } catch (failure: Exception) {
                emit("FAILED", listOf("type" to failure.javaClass.name, "message" to failure.message)); throw failure
            }
        }
    }

    private fun run(args: Array<String>, out: File, emit: (String, List<Pair<String, Any?>>) -> Unit) {
        fun report(kind: String, vararg fields: Pair<String, Any?>) = emit(kind, fields.toList())
        val variant = args[5]
        require(variant in setOf("small-standard", "wide-standard", "wide-nf-scaled", "wide-rate-scaled"))
        val inputs = args.take(4).map(::File)
        val limits = listOf(LmRuntime.MAX_CHECKPOINT_BYTES, 100_000, 2_000_000, 2_000_000)
        val bytes = inputs.mapIndexed { i, file -> read(file, limits[i]) }
        val hashes = bytes.map(::hash)
        require(hashes[0] == BASE && hashes[1] == TOK && hashes[2] == TRAIN) { "registered input identity mismatch" }
        val base = LmRuntime.load(bytes[0], bytes[1], BASE)
        val tok = base.tokenizer
        require(base.model.config.arch == LmArch.MINGRU && base.model.config.dim == 64 && base.model.config.layers == 2)
        fun texts(raw: ByteArray): List<String> = raw.toString(Charsets.UTF_8).lines().filter { it.isNotBlank() }.also { rows ->
            require(rows.size in 1..20_000 && rows.all { it.toByteArray(Charsets.UTF_8).size in 2..2048 && tok.encode(it).size + 1 <= base.model.config.context })
        }
        val train = texts(bytes[2]); val development = texts(bytes[3]); require(development.size >= 3)
        val sequences = train.map { text ->
            val full = tok.encode(text); val prefix = intArrayOf(tok.bos) + full.take(63).toIntArray()
            if (full.size <= 63) prefix + tok.eos else prefix
        }
        val schedule = sequences.indices.shuffled(Random(SEED)).take(STEPS * BATCH)
        require(schedule.size == STEPS * BATCH)
        val targetTokens = schedule.sumOf { sequences[it].size - 1 }
        val plan = hash(schedule.joinToString("\n") { sequences[it].joinToString(",") }.toByteArray(Charsets.UTF_8))
        require(plan == PLAN && targetTokens == 3255) { "historical training token plan differs" }
        val sourcePaths = listOf("LmBpeTokenizer.kt", "LmCorpus.kt", "LmCheckpointStore.kt", "LmTensor.kt", "LmModel.kt", "LmRuntime.kt")
            .map { "app/src/main/java/at/heinz/deviceai/learning/lm/$it" } + listOf(
            "experimental/weight-space-20261104/LmWeightSurgery.kt", "experimental/weight-space-20261104/LmCapacityBenchmark.kt",
            "experimental/lm1-training-recovery-20261104/LmRecoveryDiagnostic.kt")
        val sourceHashes = sourcePaths.associateWith { hash(read(File(it), 1_000_000)) }
        val nfScale = if (variant == "wide-nf-scaled") 0.125f else 1f
        val rate = if (variant == "wide-rate-scaled") 0.0000125f else 0.0001f
        val params = if (variant == "small-standard") LmCheckpoint.decode(bytes[0], tok).params
            else LmWeightSurgery.widenMinGru(base.model.params, 8, 0.001f, SEED)
        val model = LmModel(params); check(params.allFinite())
        report("MANIFEST", "scope" to "RESEARCH_SHADOW", "baseSourceCommit" to SOURCE, "variant" to variant,
            "parameters" to params.count, "learningRate" to rate, "nfPostUpdateScale" to nfScale,
            "nfOptimizerMoments" to "UNCHANGED_ORIGINAL_TRAINER", "steps" to STEPS, "batch" to BATCH,
            "seed" to SEED, "weightDecay" to 0.01, "warmup" to 2, "clip" to 1, "workers" to 2,
            "tokenScheduleSha256" to plan, "scheduleSha256" to hash(schedule.joinToString(",").toByteArray(Charsets.UTF_8)),
            "targetTokens" to targetTokens, "eosTargets" to schedule.count { sequences[it].last() == tok.eos }, "eosId" to tok.eos, "bosId" to tok.bos,
            "javaVersion" to System.getProperty("java.version"), "osArch" to System.getProperty("os.arch"))
        inputs.forEachIndexed { i, file -> report("INPUT", "role" to listOf("baseline", "tokenizer", "corpus", "development")[i], "path" to file.absolutePath, "sha256" to hashes[i]) }
        sourceHashes.forEach { (path, sha) -> report("SOURCE", "path" to path, "sha256" to sha) }
        val prompts = listOf(PROMPT) + development.take(3)
        val promptIds = prompts.map { intArrayOf(tok.bos) + tok.encode(it) }
        require(promptIds[0].size == 65) { "capacity prompt must contain exactly 64 content tokens" }
        val probes = promptIds.indices.map { Triple(it, promptIds[it], if (it == 0) "capacity" else "terminal") } + (1..3).map {
            val half = (promptIds[it].size - 1) / 2; require(half > 0) { "development prefix needs at least two content tokens" }
            Triple(it, promptIds[it].copyOfRange(0, half + 1), "prefix")
        }
        val initialBytes = LmCheckpoint.encode(params, tok, base.identity.meta + mapOf("parent" to BASE, "scope" to "RESEARCH_SHADOW",
            "variant" to variant, "adaptSteps" to "0", "parameters" to params.count.toString()))
        val initialRuntime = LmRuntime.load(initialBytes, bytes[1], hash(initialBytes))
        for ((i, ids) in promptIds.withIndex()) {
            val a = logits(base.model, ids); val b = logits(model, ids)
            val aLast = a.copyOfRange(a.size - tok.vocabSize, a.size); val bLast = b.copyOfRange(b.size - tok.vocabSize, b.size)
            val pa = FloatArray(tok.vocabSize); val pb = FloatArray(tok.vocabSize)
            Kernels.softmaxRow(aLast, 0, pa.size, pa); Kernels.softmaxRow(bLast, 0, pb.size, pb)
            val forward = delta(a, b); val probability = delta(pa, pb)
            val recurrent = maxOf(delta(base.stepwiseLogits(ids), initialRuntime.stepwiseLogits(ids)), delta(bLast, initialRuntime.stepwiseLogits(ids)))
            report("INITIAL_PARITY", "probe" to i, "maxLogitDelta" to forward, "maxProbabilityDelta" to probability, "maxStepwiseDelta" to recurrent)
            check(forward <= 0.001 && probability <= 0.00001 && recurrent <= 0.001) { "initial parity rejected; no training" }
        }
        val store = LmCheckpointStore(File(out, "checkpoints"))
        val initialSha = store.put(initialBytes)
        val initialFile = File(out, "checkpoints/$initialSha.lm1")
        report("CHECKPOINT", "step" to 0, "sha256" to initialSha, "path" to initialFile.absolutePath)
        val capacityLog = File(out, "capacity-before.tsv")
        val command = listOf(File(System.getProperty("java.home"), "bin/java").absolutePath, "-Xms64m", "-Xmx1g",
            "-cp", System.getProperty("java.class.path"), "at.heinz.deviceai.learning.lm.LmCapacityBenchmark", initialFile.absolutePath, inputs[1].absolutePath)
        report("CAPACITY_LAUNCH", "timeoutSeconds" to 120, "checkpointSha256" to initialSha, "log" to capacityLog.absolutePath,
            "argv" to command.joinToString(" | "))
        val child = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(capacityLog).start()
        val completed = try { child.waitFor(120, TimeUnit.SECONDS) } finally {
            if (child.isAlive) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS) }
        }
        val exit = if (child.isAlive) null else child.exitValue()
        val capacityBytes = read(capacityLog, 2_000_000)
        val results = capacityBytes.toString(Charsets.UTF_8).lineSequence().filter { it.startsWith("CAPACITY_RESULT\t") }.toList()
        val fields = if (results.size == 1) results.single().split('\t').drop(1).associate {
            val pair = it.split('=', limit = 2); check(pair.size == 2); pair[0] to pair[1]
        } else emptyMap()
        val capacityPass = completed && exit == 0 && results.size == 1 && fields["pass"] == "true" && fields["checkpointSha256"] == initialSha
        report("CAPACITY_GATE", "pass" to capacityPass, "processExit" to exit, "timedOut" to !completed, "logSha256" to hash(capacityBytes))
        check(capacityPass) { "initial capacity rejected; no measurements or training" }
        report("INITIAL_GATES", "pass" to true, "sourceSha256" to BASE, "tokenizerSha256" to TOK, "finite" to true, "capacityPassed" to true)
        val before = params.mats.mapValues { it.value.data.copyOf() }
        fun measure(step: Int) {
            var content = 0.0; var eos = 0.0; var count = 0; var byteCount = 0
            for (text in development) {
                val t = tok.encode(text); val loss = model.nll(intArrayOf(tok.bos) + t, t + tok.eos)
                check(loss.all { it.isFinite() }); content += loss.take(t.size).sum(); eos += loss.last()
                count += t.size; byteCount += text.toByteArray(Charsets.UTF_8).size
            }
            report("FIXED_DEV", "step" to step, "contentNll" to content / count, "eosNll" to eos / development.size,
                "contentBpb" to content / ln(2.0) / byteCount, "contentTargets" to count, "eosTargets" to development.size)
            var refNats = 0.0; var refTargets = 0
            for (index in schedule.take(BATCH)) {
                val seq = sequences[index]; val loss = model.nll(seq.copyOfRange(0, seq.size - 1), seq.copyOfRange(1, seq.size))
                check(loss.all { it.isFinite() }); refNats += loss.sum(); refTargets += loss.size
            }
            report("FIXED_FIRST_BATCH", "step" to step, "nll" to refNats / refTargets, "targets" to refTargets)
            for ((i, probe) in probes.withIndex()) {
                val (sourceIndex, ids, position) = probe
                val all = logits(model, ids); val last = all.copyOfRange(all.size - tok.vocabSize, all.size)
                val p = FloatArray(tok.vocabSize); Kernels.softmaxRow(last, 0, p.size, p)
                val best = last.indices.filter { it != tok.bos && it != tok.eos }.maxBy { last[it] }
                report("EOS_PROBE", "step" to step, "probe" to i, "position" to position, "sourceTextSha256" to hash(prompts[sourceIndex].toByteArray(Charsets.UTF_8)),
                    "inputTokenSha256" to hash(ids.joinToString(",").toByteArray(Charsets.UTF_8)),
                    "promptTokens" to ids.size - 1, "eosProbability" to p[tok.eos], "eosLogit" to last[tok.eos],
                    "bestNonEosId" to best, "bestNonEosLogit" to last[best], "eosMargin" to last[tok.eos] - last[best])
            }
        }
        measure(0)
        var trainingNanos = 0L
        LmTrainer(model, rate, 0.01f, 2, STEPS, threads = 2).use { trainer ->
            repeat(STEPS) { step ->
                val nf = params.mats.getValue("nf").data; val oldNf = nf.copyOf(); val started = System.nanoTime()
                val loss = trainer.step(schedule.subList(step * BATCH, (step + 1) * BATCH).map { sequences[it] })
                check(loss.isFinite()); val proposedNorm = sqrt(nf.indices.sumOf { (nf[it].toDouble() - oldNf[it]).let { d -> d * d } })
                if (nfScale != 1f) for (i in nf.indices) nf[i] = oldNf[i] + (nf[i] - oldNf[i]) * nfScale
                check(params.allFinite()); val duration = System.nanoTime() - started; trainingNanos += duration
                report("TRAIN_STEP", "step" to step + 1, "nll" to loss, "learningRate" to trainer.rate(step), "millis" to duration / 1_000_000.0,
                    "nfProposedUpdateL2" to proposedNorm, "nfAppliedUpdateL2" to sqrt(nf.indices.sumOf { (nf[it].toDouble() - oldNf[it]).let { d -> d * d } }))
                if (step + 1 in listOf(1, 4, 16)) measure(step + 1)
            }
        }
        inputs.forEachIndexed { i, file -> check(hash(read(file, limits[i])) == hashes[i]) { "input changed during run" } }
        sourceHashes.forEach { (path, sha) -> check(hash(read(File(path), 1_000_000)) == sha) { "source changed during run" } }
        val result = LmCheckpoint.encode(params, tok, base.identity.meta + mapOf("parent" to initialSha, "scope" to "RESEARCH_SHADOW",
            "experiment" to "lm1-training-recovery-20261104", "variant" to variant, "adaptSteps" to "$STEPS", "adaptTokens" to targetTokens.toString(),
            "adaptData" to TRAIN, "schedule" to PLAN, "parameters" to params.count.toString()))
        val finalSha = store.put(result); check(store.get(finalSha).contentEquals(result)); LmRuntime.load(result, bytes[1], finalSha)
        val changed = params.mats.entries.sumOf { (name, m) -> m.data.indices.count { m.data[it].toRawBits() != before.getValue(name)[it].toRawBits() } }
        report("CHECKPOINT", "step" to STEPS, "sha256" to finalSha, "path" to File(out, "checkpoints/$finalSha.lm1").absolutePath)
        report("TRAIN_RESULT", "steps" to STEPS, "tokens" to targetTokens, "changedWeights" to changed,
            "trainingMillis" to trainingNanos / 1_000_000.0, "parameters" to params.count, "checkpointSha256" to finalSha,
            "inputsUnchanged" to true, "sourcesUnchanged" to true, "generationCapacity" to "NOT_RUN_SEPARATE_BENCHMARK_REQUIRED", "androidValidation" to "NOT_RUN")
    }
}
