package at.heinz.deviceai.learning

import java.util.Locale

/**
 * Bounded self-supervised retrieval experiment over the existing SWB1 encoder.
 * No new model format or parallel optimizer: training uses trainSemantic(), while
 * callers decide whether to promote the candidate to the durable model.
 */
data class ContrastiveRetrieval(val reciprocalRank: Double, val top1: Int, val total: Int)
data class ContrastiveTraining(
    val candidate: TrainableSubwordEncoder,
    val steps: Int,
    val interrupted: Boolean,
    val trainingBefore: ContrastiveRetrieval,
    val trainingAfter: ContrastiveRetrieval,
    val validationBefore: ContrastiveRetrieval,
    val validationAfter: ContrastiveRetrieval
)

object ContinuumContrastiveViews {
    // Same unsigned 32-bit LCG and draw order as contrastive-views.mjs.
    private class Rng(seed: Long) {
        private var state = seed and 0xffffffffL
        fun next(): Double {
            state = (state * 1664525L + 1013904223L) and 0xffffffffL
            return state.toDouble() / 4294967296.0
        }
    }

    private fun words(text: String): List<String> {
        require(text.length in 1..2048)
        val clean = text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().take(512)
        require(clean.isNotEmpty())
        return clean.split(' ').filter { it.isNotEmpty() }
    }

    private fun validate(memories: List<String>) {
        require(memories.size in 2..24)
        require(memories.map(::words).map { it.joinToString(" ") }.toSet().size == memories.size) {
            "Duplicate memories cannot be ranked unambiguously"
        }
    }

    private fun view(tokens: List<String>, rng: Rng, drop: Double): String {
        val kept = tokens.filter { rng.next() >= drop }
        return if (kept.isNotEmpty()) kept.joinToString(" ")
            else tokens[(rng.next() * tokens.size).toInt().coerceAtMost(tokens.lastIndex)]
    }

    /** Independent noisy views of each item, scored against every original item. */
    fun evaluate(
        model: TrainableSubwordEncoder, memories: List<String>,
        seed: Long = 17L, drop: Double = 0.65
    ): ContrastiveRetrieval {
        validate(memories)
        require(drop in 0.0..0.95)
        val rng = Rng(seed)
        val tokenized = memories.map(::words)
        var hits = 0
        var reciprocal = 0.0
        for (i in memories.indices) {
            val query = view(tokenized[i], rng, drop)
            val ranked = memories.indices.sortedWith(
                compareByDescending<Int> { model.similarity(query, memories[it]) }.thenBy { it }
            )
            val rank = ranked.indexOf(i) + 1
            reciprocal += 1.0 / rank
            if (rank == 1) hits++
        }
        return ContrastiveRetrieval(reciprocal / memories.size, hits, memories.size)
    }

    /**
     * A positive noisy-to-original pair and an unrelated negative are trained
     * each round. The returned candidate is NOT automatically committed.
     * Keep a separate held-out corpus and use an external acceptance gate.
     */
    fun trainCandidate(
        baseline: TrainableSubwordEncoder,
        memories: List<String>,
        validation: List<String>,
        steps: Int = 240,
        seed: Long = 43L,
        drop: Double = 0.65,
        rate: Double = 0.10,
        cancelled: () -> Boolean = { false }
    ): ContrastiveTraining {
        validate(memories)
        validate(validation)
        require(steps in 1..500 && drop in 0.0..0.95 && rate in 0.0001..0.12)
        val trainKeys = memories.map(::words).map { it.joinToString(" ") }.toSet()
        val validationKeys = validation.map(::words).map { it.joinToString(" ") }.toSet()
        require(trainKeys.intersect(validationKeys).isEmpty()) {
            "Training and validation must not share exact memories"
        }
        val candidate = TrainableSubwordEncoder.restore(baseline.save())
        val before = evaluate(candidate, memories, 17L, drop)
        val holdoutBefore = evaluate(candidate, validation, 29L, drop)
        val tokenized = memories.map(::words)
        val rng = Rng(seed)
        var completed = 0
        while (completed < steps && !cancelled() && !Thread.currentThread().isInterrupted) {
            val i = (rng.next() * memories.size).toInt().coerceAtMost(memories.lastIndex)
            val j = (i + 1 + (rng.next() * (memories.size - 1)).toInt()) % memories.size
            val noisy = view(tokenized[i], rng, drop)
            val pairs = listOf(
                SubwordSemanticExample(noisy, memories[i], true),
                SubwordSemanticExample(noisy, memories[j], false)
            )
            val run = candidate.trainSemantic(
                pairs, steps = 2, learningRate = rate, maxMillis = 4000,
                cancelled = cancelled
            )
            if (run.stoppedEarly) break
            completed++
        }
        return ContrastiveTraining(candidate, completed, completed < steps,
            before, evaluate(candidate, memories, 17L, drop),
            holdoutBefore, evaluate(candidate, validation, 29L, drop))
    }
}
