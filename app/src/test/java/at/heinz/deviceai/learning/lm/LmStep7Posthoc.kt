package at.heinz.deviceai.learning.lm

import java.io.File

/**
 * LM1 step 7, POST HOC (not pre-registered, does not change any verdict of section 7b): why does greedy LM1 miss the
 * next word so often? For the same 2000 positions, the class of the first greedy token (end of segment, line break,
 * punctuation, a new word, the current word going on, digits) and the accuracy when the end-of-segment token is excluded from every step.
 * Run: java -cp lm.jar at.heinz.deviceai.learning.lm.LmStep7Posthoc <repoRoot> store=<dir> ckpt=<sha256>
 */
object LmStep7Posthoc {
    @JvmStatic fun main(args: Array<String>) {
        val root = File(args.getOrElse(0) { "." })
        fun arg(name: String) = args.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')
        val corpus = LmExperiment.corpus(root)
        val train = corpus.filter { it.split == LmCorpus.Split.TRAIN }.sortedBy { it.path }.flatMap { it.segments }
        val segments = LmStep7.filter(LmStep7.newDocuments(root).flatMap { it.segments }, corpus.flatMap { it.segments }).segments
        check(LmStep7.sha256(segments) == LmStep7.NEW_SHA256) { "NEW differs from the pre-registration" }
        val tok = LmBpeTokenizer.train(train, 512)
        val model = LmModel(LmCheckpoint.decode(LmCheckpointStore(File(requireNotNull(arg("store")))).get(requireNotNull(arg("ckpt"))), tok).params)
        val positions = LmStep7.positions(segments)
        val classes = LinkedHashMap<String, Int>()
        var withoutEnd = 0
        for (p in positions) {
            val ids = intArrayOf(tok.bos) + tok.encode(p.prefix)
            val dist = model.nextDistribution(ids); dist[tok.bos] = 0f
            val first = dist.indices.maxByOrNull { dist[it] }!!
            val piece = tok.pieceBytes(first).toString(Charsets.UTF_8)
            val c = when {
                first == tok.eos -> "end-of-segment"
                piece.contains('\n') -> "line-break"
                piece.startsWith(" ") && piece.length > 1 && piece[1].isLetter() -> "space-word"
                piece.isNotEmpty() && !piece[0].isLetterOrDigit() && !piece[0].isWhitespace() -> "punctuation"
                piece.isNotEmpty() && piece[0].isLetter() -> "word-continues"
                piece.isNotEmpty() && piece[0].isDigit() -> "digit"
                piece.startsWith(" ") && piece.length > 1 && piece[1].isDigit() -> "space-digit"
                piece.startsWith(" ") -> "space-other"
                else -> "other"
            }
            classes.merge(c, 1, Int::plus)
            if (predictsWithoutEnd(model, tok, p)) withoutEnd++
        }
        println("POSTHOC first greedy token classes over ${positions.size} positions: $classes")
        println("POSTHOC accuracy with the end-of-segment token excluded at every step: $withoutEnd/${positions.size}")
    }

    private fun predictsWithoutEnd(model: LmModel, tok: LmBpeTokenizer, p: LmStep7.Position): Boolean {
        val target = " ${p.word}".toByteArray(Charsets.UTF_8)
        var ids = intArrayOf(tok.bos) + tok.encode(p.prefix)
        val out = java.io.ByteArrayOutputStream()
        while (out.size() < target.size + 4 && ids.size < model.config.context) {
            val dist = model.nextDistribution(ids); dist[tok.bos] = 0f; dist[tok.eos] = 0f
            val next = dist.indices.maxByOrNull { dist[it] }!!
            out.write(tok.pieceBytes(next)); ids += next
            val b = out.toByteArray()
            for (i in 0 until minOf(b.size, target.size)) if (b[i] != target[i]) return false
        }
        val b = out.toByteArray()
        if (b.size < target.size) return false
        val rest = b.copyOfRange(target.size, b.size).toString(Charsets.UTF_8)
        return rest.isEmpty() || !rest[0].isLetter()
    }
}
