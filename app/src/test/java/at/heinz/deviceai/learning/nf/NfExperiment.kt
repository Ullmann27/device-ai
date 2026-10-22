package at.heinz.deviceai.learning.nf

import at.heinz.deviceai.learning.lm.LmBpeTokenizer
import at.heinz.deviceai.learning.lm.LmCheckpointStore
import at.heinz.deviceai.learning.lm.LmCorpus.Split
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
 * Run: java -cp nf.jar at.heinz.deviceai.learning.nf.NfExperiment <repoRoot> <mode> [args]
 * Families: <G|T|D|L|Dna|Dnb|Gr|Dr>-<S|M|X>; G minGRU, T Transformer, D gated delta, L gated linear attention,
 * Dna delta without decay gate, Dnb delta without write gate, Gr/Dr with ReLU-threshold FFN.
 */
object NfExperiment {
    const val VOCAB = 512
    private fun arg(args: Array<String>, key: String) = args.firstOrNull { it.startsWith("$key=") }?.substringAfter('=')

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
        val docs = NfCorpus.load(root)
        val tok = tokenizer(docs)
        println("NFC1 commit=${NfCorpus.COMMIT} docs=${docs.size} tokenizer=v${tok.vocabSize} sha=${tok.sha256}")
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
        val families = args.drop(2).filter { Regex("[A-Za-z]+-[SMX]").matches(it) }
        for (name in families) for (seed in NfExperiment.seeds(args)) {
            val config = NfExperiment.family(name, tok.vocabSize)
            val model = NfModel(NfParams.init(config, seed))
            val sequences = train.map { NfExperiment.pair(tok, it, config.context) }
            val batch = 16
            val stepsPerEpoch = (sequences.size + batch - 1) / batch
            val rnd = Random(seed * 31 + 7)
            val start = System.nanoTime()
            NfTrainer(model, 2e-3f, .1f, 100, stepsPerEpoch * epochs, l1 = if (config.ffn == Ffn.RELU) l1 else 0f,
                threads = NfExperiment.threads(args)).use { trainer ->
                for (epoch in 1..epochs) {
                    val order = sequences.indices.shuffled(rnd)
                    var loss = 0.0
                    for (b in 0 until stepsPerEpoch) loss += trainer.step(order.subList(b * batch, minOf(order.size, (b + 1) * batch)).map { sequences[it] })
                    val stats = FfnStats()
                    val v = NfExperiment.bpb(model, tok, validation, stats)
                    val sha = store.put(NfCheckpoint.encode(model.params, tok.sha256, mapOf("family" to name, "seed" to "$seed",
                        "epoch" to "$epoch", "corpus" to NfCorpus.COMMIT, "l1" to "$l1")))
                    println("RUN $name seed=$seed epoch=$epoch trainNll=${"%.4f".format(loss / stepsPerEpoch)} valBPB=${"%.4f".format(v)} " +
                        "params=${model.params.count} trainSeconds=${"%.0f".format((System.nanoTime() - start) / 1e9)} " +
                        (if (stats.total > 0) "ffnDensity=${"%.3f".format(stats.active.toDouble() / stats.total)} " else "") + "ckpt=$sha")
                    System.out.flush()
                }
            }
            println("SPEED $name seed=$seed msPerToken=${"%.3f".format(NfExperiment.msPerToken(model, tok, validation))} " +
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
