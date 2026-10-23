package at.heinz.deviceai.learning.nf

import at.heinz.deviceai.learning.lm.KneserNeyBytes
import at.heinz.deviceai.learning.lm.LmBpeTokenizer
import at.heinz.deviceai.learning.lm.LmCheckpointStore
import at.heinz.deviceai.learning.lm.LmCorpus.Split
import at.heinz.deviceai.learning.lm.Node
import at.heinz.deviceai.learning.lm.Tape
import java.io.File
import java.lang.management.ManagementFactory
import java.util.Random
import kotlin.math.ln

/**
 * Continuum Neural Frontier Lab, research cycle NFC1 (JVM, test sources only).
 *   corpus                  split statistics and freeze hashes (run and committed before any model run)
 *   lm <families> …         NF-2 / NF-4: language modelling on DOC, VALIDATION BPB per epoch, speed, sparsity
 *   recall <families> …     NF-2 memory capacity: multi-query associative recall with growing numbers of pairs
 *   stream <base ckpts> …   NF-1: online learning on a CODE stream, forgetting on DOC, base checkpoint integrity
 *   blt …                   NF-3: byte-latent model with entropy patches against fixed patches
 *   speed ckpt=…            fair single-thread inference timing of several checkpoints, interleaved
 *   hybrid ckpt=… vocab=258  NF-6b-lite: byte model mixed with Kneser-Ney 7, fixed vs count-anchored, cross-fitted
 * Run: java -cp nf.jar at.heinz.deviceai.learning.nf.NfExperiment <repoRoot> <mode> [args]
 * Families: <G|T|D|L|Dna|Dnb|Gr|Dr>-<S|M|X>; G minGRU, T Transformer, D gated delta, L gated linear attention,
 * Dna delta without decay gate, Dnb delta without write gate, Gr/Dr with ReLU-threshold FFN.
 */
object NfExperiment {
    const val VOCAB = 512
    fun arg(args: Array<String>, key: String) = args.firstOrNull { it.startsWith("$key=") }?.substringAfter('=')

    fun family(name: String, vocab: Int, context: Int = 256): NfConfig {
        val (arch, size) = name.split('-').let { it[0] to it[1] }
        val (dim, heads, mlp) = when (size) { "S" -> Triple(32, 2, 128); "M" -> Triple(64, 4, 256); "X" -> Triple(128, 8, 512); else -> error(size) }
        val mixer = when (arch.first()) { 'G' -> Mixer.MINGRU; 'T' -> Mixer.ATTENTION; 'D' -> Mixer.DELTA; 'L' -> Mixer.LINEAR; else -> error(arch) }
        return NfConfig(vocab, dim, 2, heads, context, mlp, mixer, decayGate = arch != "Dna", writeGate = arch != "Dnb",
            ffn = if (arch.endsWith("r")) Ffn.RELU else Ffn.GELU)
    }

    fun tokenizer(docs: List<NfCorpus.Doc>): LmBpeTokenizer = LmBpeTokenizer.train(
        NfCorpus.segments(docs, NfCorpus.Domain.DOC, Split.TRAIN) + NfCorpus.segments(docs, NfCorpus.Domain.CODE, Split.TRAIN), VOCAB)

    /** (ids, targets) of BOS + tokens + EOS; the EOS target is kept for training. */
    fun pair(tok: LmBpeTokenizer, text: String, context: Int): Pair<IntArray, IntArray> {
        val seq = (intArrayOf(tok.bos) + tok.encode(text) + tok.eos).let { if (it.size > context + 1) it.copyOf(context + 1) else it }
        return seq.copyOfRange(0, seq.size - 1) to seq.copyOfRange(1, seq.size)
    }

    /** Bits of the content tokens of one segment (EOS excluded) and its UTF-8 bytes; [state] carries memory if given. */
    fun segmentBits(model: NfModel, tok: LmBpeTokenizer, text: String, state: NfState? = null, stats: FfnStats? = null): Pair<Double, Int> {
        val tokens = tok.encode(text)
        require(tokens.size + 1 <= model.config.context) { "segment exceeds the context" }
        val ids = intArrayOf(tok.bos) + tokens
        val targets = tokens + tok.eos
        targets[targets.size - 1] = -1
        val nll = model.nll(ids, targets, state, stats)
        var nats = 0.0
        for (t in tokens.indices) nats += nll[t]
        return nats / ln(2.0) to text.toByteArray(Charsets.UTF_8).size
    }

    fun bpb(model: NfModel, tok: LmBpeTokenizer, segments: List<String>, stats: FfnStats? = null): Double {
        var bits = 0.0; var bytes = 0L
        for (s in segments) { val (b, n) = segmentBits(model, tok, s, null, stats); bits += b; bytes += n }
        return bits / bytes
    }

    fun msPerToken(model: NfModel, tok: LmBpeTokenizer, segments: List<String>): Double {
        val sample = segments.take(80)
        repeat(2) { for (s in sample.take(10)) segmentBits(model, tok, s) }            // JIT warm-up
        val tokens = sample.sumOf { tok.encode(it).size }
        val start = System.nanoTime()
        for (s in sample) segmentBits(model, tok, s)
        return (System.nanoTime() - start) / 1e6 / tokens
    }

    private fun peakHeapMb() = ManagementFactory.getMemoryPoolMXBeans().filter { it.type == java.lang.management.MemoryType.HEAP }
        .sumOf { it.peakUsage.used } / 1e6

    @JvmStatic fun main(args: Array<String>) {
        val root = File(args.getOrElse(0) { "." })
        val mode = args.getOrElse(1) { "corpus" }
        // corpus=nfc2: the NF-7 split (NfCorpus2); otherwise the frozen NFC1
        val nfc2 = arg(args, "corpus") == "nfc2"
        val docs = if (nfc2) NfCorpus2.load(root) else NfCorpus.load(root)
        if (nfc2) for ((split, pin) in listOf(Split.VALIDATION to NfCorpus2.VALIDATION_SHA256, Split.TEST to NfCorpus2.TEST_SHA256))
            check(NfCorpus.sha256(docs.filter { it.split == split }) == pin) { "NFC2 $split differs from the pre-registration" }
        // vocab=258: one token per byte (NF-6b-lite byte models); otherwise the frozen NFC1 BPE v512
        val tok = if (arg(args, "vocab") == "258") LmBpeTokenizer.bytes() else tokenizer(docs)
        println("${if (nfc2) "NFC2 commit=" + NfCorpus2.COMMIT else "NFC1 commit=" + NfCorpus.COMMIT} docs=${docs.size} tokenizer=v${tok.vocabSize} sha=${tok.sha256}")
        for (domain in NfCorpus.Domain.values()) for (split in Split.values()) {
            val d = docs.filter { it.domain == domain && it.split == split }
            val segs = d.flatMap { it.segments }
            println("SPLIT $domain $split docs=${d.size} segments=${segs.size} bytes=${NfCorpus.bytes(segs)} sha=${NfCorpus.sha256(d)}")
        }
        when (mode) {
            "corpus" -> {
                val all = docs.flatMap { it.segments }.joinToString("\n")
                println("PRIVACY residue: heinz=${Regex("(?i)heinz").findAll(all).count()} ullmann=${Regex("(?i)ullmann").findAll(all).count()} " +
                    "http=${Regex("https?://").findAll(all).count()} email=${Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").findAll(all).count()}")
                for (split in listOf(Split.TEST, Split.VALIDATION)) for (domain in NfCorpus.Domain.values())
                    println("$split $domain documents: " + docs.filter { it.split == split && it.domain == domain }.joinToString { it.path })
                for (s in NfCorpus.segments(docs, NfCorpus.Domain.CODE, Split.TRAIN).shuffled(Random(1)).take(2)) println("SAMPLE CODE | " + s.replace("\n", " ⏎ ").take(200))
            }
            "lm" -> NfLm.run(docs, tok, args)
            "recall" -> NfRecall.run(args)
            "stream" -> NfStream.run(docs, tok, args)
            "blt" -> NfBltRun.run(docs, args)
            "hybrid" -> NfHybrid.run(docs, tok, args)
            "speed" -> {
                // Fair inference timing: all checkpoints in one process, interleaved over 5 rounds, single thread.
                val validation = NfCorpus.segments(docs, NfCorpus.Domain.DOC, Split.VALIDATION)
                val store = store(args)
                val models = args.filter { it.startsWith("ckpt=") }.map { it.substringAfter('=') }.map { sha ->
                    val r = NfCheckpoint.decode(store.get(sha)); "${r.meta["family"]}/s${r.meta["seed"]}/l1=${r.meta["l1"]}" to NfModel(r.params)
                }
                val times = models.associate { it.first to ArrayList<Double>() }
                repeat(5) { for ((name, model) in models) times.getValue(name) += msPerToken(model, tok, validation) }
                for ((name, model) in models) {
                    val stats = FfnStats(); bpb(model, tok, validation.take(80), stats)
                    val t = times.getValue(name).sorted()
                    println("SPEED-FAIR $name medianMsPerToken=${"%.4f".format(t[2])} minMsPerToken=${"%.4f".format(t[0])} " +
                        (if (stats.total > 0) "ffnDensity=${"%.3f".format(stats.active.toDouble() / stats.total)}" else ""))
                }
            }
            else -> error("unknown mode $mode")
        }
        println("PEAK heapMb=${"%.0f".format(peakHeapMb())}")
    }

    fun store(args: Array<String>) = LmCheckpointStore(File(arg(args, "store") ?: "nf-checkpoints"))
    fun seeds(args: Array<String>) = (arg(args, "seeds") ?: "1,2,3").split(',').map { it.toLong() }
    fun intArg(args: Array<String>, key: String, default: Int) = arg(args, key)?.toInt() ?: default
    fun floatArg(args: Array<String>, key: String, default: Float) = arg(args, key)?.toFloat() ?: default
    fun threads(args: Array<String>) = intArg(args, "threads", 3)
}

/** NF-2 / NF-4: language modelling on DOC with identical tokenizer, data order, schedule and budget for every family. */
object NfLm {
    fun run(docs: List<NfCorpus.Doc>, tok: LmBpeTokenizer, args: Array<String>) {
        val train = NfCorpus.segments(docs, NfCorpus.Domain.DOC, Split.TRAIN)
        val validation = NfCorpus.segments(docs, NfCorpus.Domain.DOC, Split.VALIDATION)
        val epochs = NfExperiment.intArg(args, "epochs", 4)
        val l1 = NfExperiment.floatArg(args, "l1", 0f)
        val store = NfExperiment.store(args)
        val corpus = if (NfExperiment.arg(args, "corpus") == "nfc2") NfCorpus2.COMMIT else NfCorpus.COMMIT
        // residual=λ (NF-7): train on the mixture loss with Kneser-Ney 7 instead of plain cross-entropy
        val residual = NfExperiment.floatArg(args, "residual", 0f)
        require(residual == 0f || tok.vocabSize == 258) { "residual training needs the byte vocabulary (vocab=258)" }
        val kn = if (residual > 0f) KneserNeyBytes(7, train) else null
        val families = args.drop(2).filter { Regex("[A-Za-z]+-[SMX]").matches(it) }
        for (name in families) for (seed in NfExperiment.seeds(args)) {
            val config = NfExperiment.family(name, tok.vocabSize)
            val model = NfModel(NfParams.init(config, seed))
            val sequences = train.map { NfExperiment.pair(tok, it, config.context) }
            val qs = kn?.let { k -> train.mapIndexed { i, s -> NfResidual.targetProbabilities(k, s, sequences[i].second.size) } }
            val label = if (residual > 0f) "$name-res" else name
            val batch = 16
            val stepsPerEpoch = (sequences.size + batch - 1) / batch
            val rnd = Random(seed * 31 + 7)
            val start = System.nanoTime()
            NfTrainer(model, 2e-3f, .1f, 100, stepsPerEpoch * epochs, l1 = if (config.ffn == Ffn.RELU) l1 else 0f,
                threads = NfExperiment.threads(args)).use { trainer ->
                for (epoch in 1..epochs) {
                    val order = sequences.indices.shuffled(rnd)
                    var loss = 0.0
                    for (b in 0 until stepsPerEpoch) {
                        val idx = order.subList(b * batch, minOf(order.size, (b + 1) * batch))
                        loss += trainer.step(idx.map { sequences[it] }, lossFn = qs?.let { q ->
                            { tape: Tape, logits: Node, i: Int, total: Int -> tape.mixtureCrossEntropy(logits, sequences[idx[i]].second, q[idx[i]], residual.toDouble(), total) }
                        })
                    }
                    val stats = FfnStats()
                    val v = NfExperiment.bpb(model, tok, validation, stats)
                    val sha = store.put(NfCheckpoint.encode(model.params, tok.sha256, mapOf("family" to label, "seed" to "$seed",
                        "epoch" to "$epoch", "corpus" to corpus, "l1" to "$l1", "residual" to "$residual")))
                    println("RUN $label seed=$seed epoch=$epoch trainNll=${"%.4f".format(loss / stepsPerEpoch)} valBPB=${"%.4f".format(v)} " +
                        "params=${model.params.count} trainSeconds=${"%.0f".format((System.nanoTime() - start) / 1e9)} " +
                        (if (stats.total > 0) "ffnDensity=${"%.3f".format(stats.active.toDouble() / stats.total)} l1=$l1 " else "") + "ckpt=$sha")
                    System.out.flush()
                }
            }
            println("SPEED $label seed=$seed${if (config.ffn == Ffn.RELU) " l1=$l1" else ""} msPerToken=${"%.3f".format(NfExperiment.msPerToken(model, tok, validation))} " +
                "stateFloats=${NfState.empty(model.config).layers.sumOf { it?.size ?: 0 }} paramBytes=${model.params.count * 4}")
            System.out.flush()
        }
    }
}

/**
 * NF-2 memory capacity: multi-query associative recall (MQAR, Arora et al. 2023). A sequence lists N distinct keys with
 * random values, then asks for every key again in random order; only the values after the queries are scored.
 * Trained on N ∈ [1, 16], evaluated on N = 4…32 (24 and 32 are longer than anything seen in training).
 */
object NfRecall {
    const val KEYS = 64

    fun sample(rnd: Random, n: Int): Pair<IntArray, IntArray> {
        val keys = (0 until KEYS).shuffled(rnd).take(n)
        val values = IntArray(n) { KEYS + rnd.nextInt(KEYS) }
        val seq = ArrayList<Int>(); seq += 2 * KEYS                  // BOS
        val scored = ArrayList<Boolean>(); scored += false
        for (i in 0 until n) { seq += keys[i]; scored += false; seq += values[i]; scored += false }
        for (i in (0 until n).shuffled(rnd)) { seq += keys[i]; scored += false; seq += values[i]; scored += true }
        val ids = seq.subList(0, seq.size - 1).toIntArray()
        val targets = IntArray(ids.size) { t -> if (scored[t + 1]) seq[t + 1] else -1 }
        return ids to targets
    }

    fun run(args: Array<String>) {
        val steps = NfExperiment.intArg(args, "steps", 3000)
        val families = args.drop(2).filter { Regex("[A-Za-z]+-[SMX]").matches(it) }
        for (name in families) for (seed in NfExperiment.seeds(args)) {
            val config = NfExperiment.family(name, 2 * KEYS + 2, context = 160)
            val model = NfModel(NfParams.init(config, seed))
            val rnd = Random(seed * 101 + 13)
            val start = System.nanoTime()
            var loss = 0.0
            NfTrainer(model, 3e-3f, .01f, 200, steps, threads = NfExperiment.threads(args)).use { trainer ->
                for (s in 1..steps) {
                    loss = trainer.step(List(16) { sample(rnd, 1 + rnd.nextInt(16)) })
                    if (s % 500 == 0) { println("TRAIN $name seed=$seed step=$s nll=${"%.4f".format(loss)}"); System.out.flush() }
                }
            }
            val eval = Random(424242L)                                      // identical evaluation set for every model
            val acc = listOf(4, 8, 16, 24, 32).map { n ->
                var right = 0; var total = 0
                repeat(200) {
                    val (ids, targets) = sample(eval, n)
                    val tape = at.heinz.deviceai.learning.lm.Tape(false)
                    val logits = model.logits(tape, model.frozenNodes(tape), ids).value
                    for (t in targets.indices) if (targets[t] >= 0) {
                        var best = 0
                        for (j in 0 until logits.cols) if (logits[t, j] > logits[t, best]) best = j
                        if (best == targets[t]) right++
                        total++
                    }
                }
                n to right.toDouble() / total
            }
            println("RECALL $name seed=$seed params=${model.params.count} stateFloats=${NfState.empty(config).layers.sumOf { it?.size ?: 0 }} " +
                "trainSeconds=${"%.0f".format((System.nanoTime() - start) / 1e9)} finalNll=${"%.4f".format(loss)} " +
                acc.joinToString(" ") { (n, a) -> "acc$n=${"%.3f".format(a)}" })
            System.out.flush()
        }
    }
}
