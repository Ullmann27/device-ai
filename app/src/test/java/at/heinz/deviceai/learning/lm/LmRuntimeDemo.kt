package at.heinz.deviceai.learning.lm

import java.io.File
import java.util.Random

/**
 * Runs [LmRuntime] on a real LM1 checkpoint: writes the portable tokenizer file the checkpoint is bound to (so the
 * runtime never needs the corpus), loads the model through the runtime's checks, compares step-wise generation with
 * the research generator (same text required) and measures both on one thread, interleaved over rounds.
 * Run: java -cp lm.jar at.heinz.deviceai.learning.lm.LmRuntimeDemo <repoRoot> store=<dir> ckpt=<sha256> out=<dir>
 */
object LmRuntimeDemo {
    private val PROMPTS = listOf("Der Coding-Agent", "Continuum lernt", "Die Quittung", "Wenn der Test", "Der Besitzer")

    @JvmStatic fun main(args: Array<String>) {
        val root = File(args.getOrElse(0) { "." })
        fun arg(name: String) = args.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')
        val train = LmExperiment.corpus(root).filter { it.split == LmCorpus.Split.TRAIN }.sortedBy { it.path }.flatMap { it.segments }
        val tok = LmBpeTokenizer.train(train, 512)
        val out = File(requireNotNull(arg("out")) { "out=<dir> missing" }).apply { mkdirs() }
        val tokenizerFile = File(out, "tokenizer-v512-${tok.sha256}.lmbp").apply { writeBytes(tok.serialize()) }
        println("TOKENIZER file=${tokenizerFile.name} bytes=${tokenizerFile.length()} sha=${tok.sha256}")
        val rt = LmRuntime.load(LmCheckpointStore(File(requireNotNull(arg("store")))), requireNotNull(arg("ckpt")), tokenizerFile.readBytes())
        with(rt.identity) {
            println("MODEL ckpt=$checkpointSha256 arch=$arch parameters=$parameters parameterBytes=$parameterBytes context=$context " +
                "family=${meta["family"]} seed=${meta["seed"]} epoch=${meta["epoch"]}")
        }
        val rounds = arg("rounds")?.toInt() ?: 3
        val newTokens = 64
        val stepMs = ArrayList<Double>(); val fullMs = ArrayList<Double>()
        for (round in 1..rounds) for (prompt in PROMPTS) {
            val t0 = System.nanoTime()
            val r = rt.generate(LmRuntime.Request(prompt, maxNewTokens = newTokens, maxMillis = 60_000))
            val t1 = System.nanoTime()
            val reference = LmEval.generate(rt.model, rt.tokenizer, prompt, newTokens, 0.0, 1, Random(0))
            val t2 = System.nanoTime()
            check(LmRuntime.completeUtf8(reference.toByteArray(Charsets.UTF_8)) == r.text || reference == r.text) {
                "step-wise and research generator differ for '$prompt'"
            }
            if (r.newTokens > 0) {
                stepMs += (t1 - t0) / 1e6 / r.newTokens
                fullMs += (t2 - t1) / 1e6 / r.newTokens
            }
            if (round == rounds) println("GREEDY $prompt | ${r.text.replace("\n", " ⏎ ")} | stop=${r.stop} tokens=${r.newTokens}")
        }
        fun median(v: List<Double>) = v.sorted().let { if (it.isEmpty()) Double.NaN else it[it.size / 2] }
        println("SPEED rounds=$rounds prompts=${PROMPTS.size} newTokens<=$newTokens msPerToken stepwise=${"%.3f".format(median(stepMs))} " +
            "fullRecompute=${"%.3f".format(median(fullMs))} (medians, one thread, JVM x86_64, not ARM64)")
        for ((i, prompt) in PROMPTS.withIndex()) {
            val r = rt.generate(LmRuntime.Request(prompt, maxNewTokens = newTokens, temperature = .8, topK = 40, seed = 100L + i))
            println("SAMPLED t=0.8 seed=${100 + i} $prompt | ${r.text.replace("\n", " ⏎ ")} | stop=${r.stop}")
        }
        val rtm = Runtime.getRuntime()
        println("MEMORY heapUsedMb=${(rtm.totalMemory() - rtm.freeMemory()) shr 20} parameterMb=${"%.2f".format(rt.identity.parameterBytes / 1048576.0)}")
    }
}
