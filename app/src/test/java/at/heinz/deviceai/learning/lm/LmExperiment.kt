package at.heinz.deviceai.learning.lm

import java.io.File
import java.util.Random
import kotlin.math.sqrt

/**
 * LM1 study (JVM). Corpus: the owner's Markdown documentation at a pinned commit, privacy-filtered by [LmCorpus].
 * `corpus` prints split statistics and hashes; `select` trains and scores on VALIDATION only; `final` scores the
 * pre-registered checkpoints and baselines once on the frozen TEST; `sample` prints continuations.
 * Run: java -cp lm.jar at.heinz.deviceai.learning.lm.LmExperiment <repoRoot> corpus|select|final|sample [...]
 */
object LmExperiment {
    /** Source of the corpus: PR #57 head, the base of this branch. */
    const val COMMIT = "1d92db03b87558c0b330dc584fd1f4c025a93c82"
    /** Frozen before any model was scored on TEST (see docs/CONTINUUM-LM1-OPUS-20261022.md). */
    const val TEST_SHA256 = "0d11bf88f23df23c001c3e77616fe87567b3fa2a03e44f597bee9ef48b787188"
    private val EXCLUDED = listOf("docs/evidence/", "THIRD_PARTY_NOTICES.md")

    data class Family(val arch: LmArch, val size: String, val vocab: Int) {
        val name get() = "LM1-${if (arch == LmArch.TRANSFORMER) "T" else "G"}$size-v$vocab"
        fun config(tokenizerVocab: Int) = when (size) {
            "S" -> LmConfig(arch, tokenizerVocab, 32, 2, 2, 256, 128)
            "M" -> LmConfig(arch, tokenizerVocab, 64, 2, 4, 256, 256)
            else -> error(size)
        }
    }

    fun git(root: File, vararg args: String): String {
        val p = ProcessBuilder(listOf("git") + args).directory(root).redirectErrorStream(false).start()
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
        check(p.waitFor() == 0) { "git ${args.joinToString(" ")} failed" }
        return out
    }

    fun corpus(root: File): List<LmCorpus.Document> {
        val files = git(root, "ls-tree", "-r", "--name-only", COMMIT).lines()
            .filter { it.endsWith(".md") && EXCLUDED.none { e -> it.startsWith(e) } }
        return files.map { LmCorpus.document(it, git(root, "show", "$COMMIT:$it")) }.filter { it.segments.isNotEmpty() }
    }

    private fun bpb(scorer: SegmentScorer, segments: List<String>): Double {
        var bits = 0.0; var bytes = 0L
        for (s in segments) { bits += scorer.bits(s); bytes += s.toByteArray(Charsets.UTF_8).size }
        return bits / bytes
    }

    private fun lmScorer(model: LmModel, tok: LmBpeTokenizer) = SegmentScorer { s -> LmEval.segmentBits(model, tok, s).first }

    /** ms per scored token, single thread (inference tape). */
    private fun msPerToken(model: LmModel, tok: LmBpeTokenizer, segments: List<String>): Double {
        val sample = segments.take(60)
        val tokens = sample.sumOf { tok.encode(it).size }
        val start = System.nanoTime()
        for (s in sample) LmEval.segmentBits(model, tok, s)
        return (System.nanoTime() - start) / 1e6 / tokens
    }

    fun tokenizerFor(vocab: Int, train: List<String>) = if (vocab == 258) LmBpeTokenizer.bytes() else LmBpeTokenizer.train(train, vocab)

    /** Trains one family/seed on TRAIN, reporting VALIDATION BPB and the checkpoint SHA after every epoch. */
    fun runLm(family: Family, seed: Long, tok: LmBpeTokenizer, train: List<String>, validation: List<String>, epochs: Int,
              store: LmCheckpointStore, corpusSha: String, log: (String) -> Unit) {
        val model = LmModel(LmParams.init(family.config(tok.vocabSize), seed))
        val sequences = train.map { intArrayOf(tok.bos) + tok.encode(it) + tok.eos }
        val batch = 16
        val stepsPerEpoch = (sequences.size + batch - 1) / batch
        val rnd = Random(seed * 31 + 7)
        val start = System.nanoTime()
        LmTrainer(model, 2e-3f, .1f, 100, stepsPerEpoch * epochs).use { trainer ->
            for (epoch in 1..epochs) {
                val order = sequences.indices.shuffled(rnd)
                var loss = 0.0
                for (b in 0 until stepsPerEpoch) loss += trainer.step(order.subList(b * batch, minOf(order.size, (b + 1) * batch)).map { sequences[it] })
                val v = bpb(lmScorer(model, tok), validation)
                val sha = store.put(LmCheckpoint.encode(model.params, tok, mapOf("family" to family.name, "seed" to "$seed",
                    "epoch" to "$epoch", "corpus" to corpusSha, "commit" to COMMIT)))
                log("RUN ${family.name} seed=$seed epoch=$epoch trainNll=${"%.4f".format(loss / stepsPerEpoch)} valBPB=${"%.4f".format(v)} " +
                    "params=${model.params.count} trainSeconds=${"%.0f".format((System.nanoTime() - start) / 1e9)} ckpt=$sha")
            }
        }
        log("SPEED ${family.name} seed=$seed msPerToken=${"%.3f".format(msPerToken(model, tok, validation))} tokensPerByte=" +
            "%.3f".format(validation.sumOf { tok.encode(it).size }.toDouble() / validation.sumOf { it.toByteArray(Charsets.UTF_8).size }))
    }

    @JvmStatic fun main(args: Array<String>) {
        val root = File(args.getOrElse(0) { "." })
        val mode = args.getOrElse(1) { "corpus" }
        val docs = corpus(root)
        fun segs(split: LmCorpus.Split) = docs.filter { it.split == split }.sortedBy { it.path }.flatMap { it.segments }
        val train = segs(LmCorpus.Split.TRAIN); val validation = segs(LmCorpus.Split.VALIDATION)
        val trainSha = LmCorpus.sha256(docs.filter { it.split == LmCorpus.Split.TRAIN })
        println("CORPUS commit=$COMMIT docs=${docs.size} trainSha=$trainSha validationSha=${LmCorpus.sha256(docs.filter { it.split == LmCorpus.Split.VALIDATION })}")
        for (split in LmCorpus.Split.values()) {
            val d = docs.filter { it.split == split }
            println("SPLIT $split docs=${d.size} segments=${d.sumOf { it.segments.size }} bytes=${d.sumOf { it.segments.sumOf { s -> s.toByteArray(Charsets.UTF_8).size } }}")
        }
        when (mode) {
            "corpus" -> {
                val test = docs.filter { it.split == LmCorpus.Split.TEST }
                println("TEST sha=${LmCorpus.sha256(test)} pinned=$TEST_SHA256")
                println("TEST documents: " + test.joinToString { it.path })
                println("VALIDATION documents: " + docs.filter { it.split == LmCorpus.Split.VALIDATION }.joinToString { it.path })
                val all = docs.flatMap { it.segments }.joinToString("\n")
                println("PRIVACY residue: heinz=${Regex("(?i)heinz").findAll(all).count()} ullmann=${Regex("(?i)ullmann").findAll(all).count()} " +
                    "at-sign=${all.count { it == '@' }} http=${Regex("https?://").findAll(all).count()}")
                for (s in train.shuffled(Random(1)).take(4)) println("SAMPLE TRAIN | " + s.replace("\n", " ⏎ ").take(200))
            }
            "select" -> {
                val quick = args.contains("quick")
                val store = LmCheckpointStore(File(args.firstOrNull { it.startsWith("store=") }?.substringAfter('=') ?: "lm-checkpoints"))
                val only = args.filter { it.startsWith("LM1-") || it in setOf("baselines", "cdx1") }
                if (only.isEmpty() || "baselines" in only) {
                    println("BASE unigram valBPB=${"%.4f".format(bpb(UnigramBytes(train), validation))}")
                    for (n in 2..7) println("BASE kn$n valBPB=${"%.4f".format(bpb(KneserNeyBytes(n, train), validation))}")
                }
                if (only.isEmpty() || "cdx1" in only) for (seed in if (quick) listOf(1L) else listOf(1L, 2L, 3L)) {
                    val cdx = Cdx1Baseline(seed)
                    val start = System.nanoTime()
                    for (epoch in 1..6) {
                        cdx.trainEpoch(train)
                        println("CDX1 seed=$seed epoch=$epoch valBPB=${"%.4f".format(bpb(cdx, validation))} params=${cdx.model.parameterCount} " +
                            "trainSeconds=${"%.0f".format((System.nanoTime() - start) / 1e9)}")
                    }
                }
                val families = listOf(
                    Family(LmArch.TRANSFORMER, "S", 512), Family(LmArch.MINGRU, "S", 512),
                    Family(LmArch.TRANSFORMER, "M", 512), Family(LmArch.MINGRU, "M", 512),
                    Family(LmArch.TRANSFORMER, "M", 258), Family(LmArch.MINGRU, "M", 258),
                    Family(LmArch.TRANSFORMER, "M", 1024), Family(LmArch.MINGRU, "M", 1024))
                val tokenizers = HashMap<Int, LmBpeTokenizer>()
                for (f in families.filter { f -> only.any { it == f.name } || only.isEmpty() }) {
                    val tok = tokenizers.getOrPut(f.vocab) { tokenizerFor(f.vocab, train) }
                    println("TOKENIZER v${f.vocab} sha=${tok.sha256}")
                    val seeds = if (quick || f.vocab != 512) listOf(1L) else listOf(1L, 2L, 3L)
                    for (seed in seeds) runLm(f, seed, tok, train, validation, if (quick) 2 else 8, store, trainSha) { println(it); System.out.flush() }
                }
            }
            "final" -> {
                val test = docs.filter { it.split == LmCorpus.Split.TEST }
                val sha = LmCorpus.sha256(test)
                check(sha == TEST_SHA256) { "TEST changed or not frozen: $sha" }
                val testSegs = test.sortedBy { it.path }.flatMap { it.segments }
                val store = LmCheckpointStore(File(args.firstOrNull { it.startsWith("store=") }?.substringAfter('=') ?: "lm-checkpoints"))
                val kn = args.firstOrNull { it.startsWith("kn=") }?.substringAfter('=')?.toInt() ?: 5
                val cdxEpochs = args.firstOrNull { it.startsWith("cdx1epochs=") }?.substringAfter('=')?.toInt() ?: 6
                println("FINAL test=$sha segments=${testSegs.size}")
                println("BASE unigram testBPB=${"%.4f".format(bpb(UnigramBytes(train), testSegs))}")
                println("BASE kn$kn testBPB=${"%.4f".format(bpb(KneserNeyBytes(kn, train), testSegs))}")
                for (seed in listOf(1L, 2L, 3L)) {
                    val cdx = Cdx1Baseline(seed); repeat(cdxEpochs) { cdx.trainEpoch(train) }
                    println("CDX1 seed=$seed epochs=$cdxEpochs testBPB=${"%.4f".format(bpb(cdx, testSegs))}")
                }
                val tokenizers = HashMap<Int, LmBpeTokenizer>()
                for (ck in args.filter { it.startsWith("ckpt=") }.map { it.substringAfter('=') }) {
                    val (vocab, hash) = ck.split(':').let { it[0].toInt() to it[1] }
                    val tok = tokenizers.getOrPut(vocab) { tokenizerFor(vocab, train) }
                    val restored = LmCheckpoint.decode(store.get(hash), tok)
                    val model = LmModel(restored.params)
                    println("LM ${restored.meta["family"]} seed=${restored.meta["seed"]} epoch=${restored.meta["epoch"]} ckpt=$hash " +
                        "testBPB=${"%.4f".format(bpb(lmScorer(model, tok), testSegs))} valBPB=${"%.4f".format(bpb(lmScorer(model, tok), validation))}")
                }
            }
            "sample" -> {
                val store = LmCheckpointStore(File(args.firstOrNull { it.startsWith("store=") }?.substringAfter('=') ?: "lm-checkpoints"))
                val (vocab, hash) = args.first { it.startsWith("ckpt=") }.substringAfter('=').split(':').let { it[0].toInt() to it[1] }
                val tok = tokenizerFor(vocab, train)
                val model = LmModel(LmCheckpoint.decode(store.get(hash), tok).params)
                val rnd = Random(5)
                for (prompt in listOf("Der Coding-Agent", "Continuum lernt", "Die Quittung", "Wenn der Test")) {
                    println("PROMPT $prompt | greedy: " + LmEval.generate(model, tok, prompt, 40, 0.0, 1, rnd).replace("\n", " ⏎ "))
                    println("PROMPT $prompt | t=0.8: " + LmEval.generate(model, tok, prompt, 40, .8, 40, rnd).replace("\n", " ⏎ "))
                }
            }
        }
    }

    fun meanStd(values: List<Double>): String {
        val m = values.average()
        val s = sqrt(values.sumOf { (it - m) * (it - m) } / (values.size - 1).coerceAtLeast(1))
        return "%.4f ± %.4f".format(m, s)
    }
}
