package at.heinz.deviceai.learning.nf

import at.heinz.deviceai.learning.lm.LmBpeTokenizer
import at.heinz.deviceai.learning.lm.LmCorpus.Split
import at.heinz.deviceai.learning.lm.Mat
import java.util.Random

/**
 * NF-1 multi-speed memory. A base checkpoint trained on DOC only reads a stream of CODE TRAIN documents once, segment
 * by segment and prequentially: every segment is scored before the system may learn from it. Arms:
 *  BASE      nothing learns; memory reset per segment (LM1 protocol)
 *  CARRY     recurrent state carried across the segments of a document (fixed recurrent memory, no weight change)
 *  FT        one AdamW step on every segment, all weights
 *  REPLAY    like FT, every step also contains one random DOC TRAIN segment
 *  ADAPT     base frozen; a rank-4 adapter learns on every segment (slow level outside the base checkpoint)
 *  SURPRISE  like ADAPT, but only segments whose BPB exceeds the running median of the stream are consolidated
 *  TWO       CARRY (fast level within a document) + ADAPT (slow level)
 *  TWOS      CARRY + SURPRISE
 * After the stream: CODE VALIDATION (what was learned generalises?), DOC VALIDATION (forgetting); adapter arms also
 * report DOC VALIDATION with the adapter switched off and whether the base parameters are still bit-identical.
 */
object NfStream {
    val ARMS = listOf("BASE", "CARRY", "FT", "REPLAY", "ADAPT", "SURPRISE", "TWO", "TWOS")
    const val RANK = 4

    fun withAdapter(base: NfParams, rank: Int, seed: Long): NfParams {
        val config = base.config.copy(adapterRank = rank)
        val fresh = NfParams.init(config, seed)
        val mats = LinkedHashMap<String, Mat>()
        for ((name, _, _) in config.layout()) mats[name] = base.mats[name]?.copy() ?: fresh.mats.getValue(name)
        return NfParams(config, mats)
    }

    fun withoutAdapter(params: NfParams): NfParams {
        val config = params.config.copy(adapterRank = 0)
        return NfParams(config, LinkedHashMap(config.layout().associate { (n, _, _) -> n to params.mats.getValue(n) }))
    }

    fun run(docs: List<NfCorpus.Doc>, tok: LmBpeTokenizer, args: Array<String>) {
        val store = NfExperiment.store(args)
        val streamBytes = NfExperiment.intArg(args, "streamBytes", 120_000)
        val lr = NfExperiment.floatArg(args, "lr", 1e-3f)
        val adapterLr = NfExperiment.floatArg(args, "adapterLr", 3e-3f)
        val arms = args.firstOrNull { it.startsWith("arms=") }?.substringAfter('=')?.split(',') ?: ARMS
        val stream = ArrayList<NfCorpus.Doc>(); var bytes = 0
        for (d in docs.filter { it.domain == NfCorpus.Domain.CODE && it.split == Split.TRAIN }
            .sortedBy { LmBpeTokenizer.hex(java.security.MessageDigest.getInstance("SHA-256").digest(("stream\u0000" + it.path).toByteArray())) }) {
            if (bytes >= streamBytes) break
            stream += d; bytes += NfCorpus.bytes(d.segments)
        }
        val codeVal = NfCorpus.segments(docs, NfCorpus.Domain.CODE, Split.VALIDATION)
        val docVal = NfCorpus.segments(docs, NfCorpus.Domain.DOC, Split.VALIDATION)
        val docTrain = NfCorpus.segments(docs, NfCorpus.Domain.DOC, Split.TRAIN)
        println("STREAM documents=${stream.size} segments=${stream.sumOf { it.segments.size }} bytes=$bytes codeVal=${codeVal.size} docVal=${docVal.size}")
        for (sha in args.filter { it.startsWith("ckpt=") }.map { it.substringAfter('=') }) {
            val restored = NfCheckpoint.decode(store.get(sha))
            check(restored.tokenizerSha == tok.sha256) { "checkpoint $sha belongs to another tokenizer" }
            val family = restored.meta["family"]; val seed = restored.meta["seed"]
            val baseHash = restored.params.sha256()
            for (arm in arms) {
                val adapter = arm in setOf("ADAPT", "SURPRISE", "TWO", "TWOS")
                val carry = arm in setOf("CARRY", "TWO", "TWOS")
                val surprise = arm == "SURPRISE" || arm == "TWOS"
                val learnsAll = arm == "FT" || arm == "REPLAY"
                val params = if (adapter) withAdapter(restored.params, RANK, 99) else restored.params.copy()
                val model = NfModel(params)
                val adapters = params.config.adapterNames()
                val trainer = when {
                    learnsAll -> NfTrainer(model, lr, 0f, 0, 1, threads = 1)
                    adapter -> NfTrainer(model, adapterLr, 0f, 0, 1, trainable = adapters, threads = 1)
                    else -> null
                }
                val rnd = Random(17)
                val seen = ArrayList<Double>()
                var bits = 0.0; var total = 0L; var updates = 0; var updateNanos = 0L
                for (doc in stream) {
                    val state = if (carry && model.config.recurrent) NfState.empty(model.config) else null
                    for (segment in doc.segments) {
                        val before = state?.copy()
                        val (b, n) = NfExperiment.segmentBits(model, tok, segment, state)
                        bits += b; total += n
                        val segBpb = b / n
                        val consolidate = trainer != null && (!surprise || seen.isEmpty() || segBpb > median(seen))
                        seen += segBpb
                        if (consolidate) {
                            val t0 = System.nanoTime()
                            val batch = ArrayList<Pair<IntArray, IntArray>>()
                            batch += NfExperiment.pair(tok, segment, model.config.context)
                            val states = ArrayList<NfState?>(); states += before?.copy()
                            if (arm == "REPLAY") { batch += NfExperiment.pair(tok, docTrain[rnd.nextInt(docTrain.size)], model.config.context); states += null }
                            trainer!!.step(batch, states, if (adapter) adapterLr else lr)
                            updates++; updateNanos += System.nanoTime() - t0
                        }
                    }
                }
                trainer?.close()
                val codeBpb = NfExperiment.bpb(model, tok, codeVal)
                val docBpb = NfExperiment.bpb(model, tok, docVal)
                val extra = if (adapter) {
                    val off = NfModel(withoutAdapter(params))
                    " docValAdapterOff=${"%.4f".format(NfExperiment.bpb(off, tok, docVal))} baseUnchanged=${off.params.sha256() == baseHash}"
                } else ""
                println("STREAM $arm base=$family seed=$seed streamBPB=${"%.4f".format(bits / total)} codeValBPB=${"%.4f".format(codeBpb)} " +
                    "docValBPB=${"%.4f".format(docBpb)} updates=$updates updateSeconds=${"%.1f".format(updateNanos / 1e9)}$extra")
                System.out.flush()
            }
        }
    }

    fun median(xs: List<Double>): Double = xs.sorted().let { s -> if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2 }
}
