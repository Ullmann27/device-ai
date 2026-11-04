package at.heinz.deviceai.learning.lm

import java.util.Random

/**
 * Pure, offline operations for the registered LM1 weight-space experiment.
 *
 * Every result owns fresh arrays. This object has no persistence, runtime registration or promotion API.
 * A caller must keep the source stable while an operation is running, just as for LmCheckpoint.encode.
 */
object LmWeightSurgery {
    private val allowedNorms = setOf("l0.n1", "l1.n1", "nf")
    private const val MAX_NOISE = 0.01f

    /**
     * Clone a minGRU checkpoint and multiply exactly one registered gain tensor by [factor].
     * Factors are restricted to [0.25, 4]; this is a calibration experiment, not model promotion.
     * Finite inputs that would overflow during multiplication are rejected without changing the source.
     */
    fun rescaleNorm(params: LmParams, name: String, factor: Float): LmParams {
        validateSource(params)
        require(name in allowedNorms && name in params.mats) { "unregistered or absent normalization gain: $name" }
        require(factor.isFinite() && factor in 0.25f..4f) { "gain factor must be finite and in [0.25, 4]" }
        val result = LinkedHashMap<String, Mat>()
        for ((key, source) in params.mats) {
            val candidate = source.copy()
            if (key == name) {
                for (i in candidate.data.indices) {
                    val value = candidate.data[i] * factor
                    require(value.isFinite()) { "non-finite normalization gain after rescaling" }
                    candidate.data[i] = value
                }
            }
            result[key] = candidate
        }
        return LmParams(params.config, result)
    }

    /**
     * Replicate minGRU channels by a factor of 2, 4 or 8, within the existing LM1 shape limits.
     *
     * An old channel j maps to j + replica * oldWidth. Embeddings, biases and internal RMS gains
     * are tiled. Each dense matrix is tiled in both dimensions and divided by the number of input
     * replicas. Consequently the recurrent state, gates, MLP and residuals reproduce the original
     * activations in every replica. RMSNorm's mean square is unchanged by replication. The final
     * gain is divided by [factor] to compensate for the tied embedding/output projection's sum.
     *
     * With nonzero [noiseStd], each pair of input replicas receives opposite perturbations for
     * each original input channel and each new output coordinate. Draws use Java Random's Gaussian
     * generator, scaled by [noiseStd] and clipped to +/-0.01. The perturbations sum to zero for
     * identical replicas and can give different backward gradients after training. The clipped
     * distribution's realized standard deviation need not equal [noiseStd]. [seed] fixes the draws.
     *
     * Preservation is exact over real numbers, not bitwise floating-point arithmetic. The caller
     * must verify logits on its actual checkpoint before using a widened candidate. Expansion
     * creates capacity; it does not, by itself, add learned knowledge or improve predictions.
     */
    fun widenMinGru(params: LmParams, factor: Int, noiseStd: Float = 0f, seed: Long = 0L): LmParams {
        validateSource(params)
        require(factor == 2 || factor == 4 || factor == 8) { "replication factor must be 2, 4 or 8" }
        require(noiseStd.isFinite() && noiseStd in 0f..MAX_NOISE) { "noise scale must be finite and in [0, 0.01]" }
        val dim = params.config.dim.toLong() * factor
        val mlp = params.config.mlp.toLong() * factor
        require(dim <= 512L && mlp <= 4096L) { "expanded shape exceeds LM1 limits" }
        val config = params.config.copy(dim = dim.toInt(), mlp = mlp.toInt())
        val layout = config.layout()
        val count = layout.sumOf { (_, rows, cols) -> rows.toLong() * cols }
        require(count <= Int.MAX_VALUE.toLong()) { "expanded parameter count overflows LM1" }
        val random = Random(seed)
        val result = LinkedHashMap<String, Mat>()
        for ((name, rows, cols) in layout) {
            val source = params.mats.getValue(name)
            val candidate = Mat(rows, cols)
            val leaf = name.substringAfterLast('.')
            when {
                name == "tok" -> {
                    for (row in 0 until rows) for (col in 0 until cols) {
                        candidate[row, col] = source[row, col % source.cols]
                    }
                }
                name == "nf" -> {
                    for (col in 0 until cols) candidate[0, col] = source[0, col % source.cols] / factor
                }
                leaf == "n1" || leaf == "n2" || leaf == "bz" || leaf == "b1" || leaf == "b2" -> {
                    for (col in 0 until cols) candidate[0, col] = source[0, col % source.cols]
                }
                leaf == "wz" || leaf == "wc" || leaf == "wg" || leaf == "wo" || leaf == "w1" || leaf == "w2" -> {
                    for (row in 0 until source.rows) for (col in 0 until cols) {
                        val divided = source[row, col % source.cols] / factor
                        for (pair in 0 until factor / 2) {
                            val delta = if (noiseStd == 0f) 0f else
                                (random.nextGaussian() * noiseStd).coerceIn(-MAX_NOISE.toDouble(), MAX_NOISE.toDouble()).toFloat()
                            candidate[row + (2 * pair) * source.rows, col] = divided + delta
                            candidate[row + (2 * pair + 1) * source.rows, col] = divided - delta
                        }
                    }
                }
                else -> throw IllegalArgumentException("unregistered minGRU parameter: $name")
            }
            require(candidate.data.all { it.isFinite() }) { "non-finite expanded parameter: $name" }
            result[name] = candidate
        }
        return LmParams(config, result)
    }

    /**
     * Append trainable minGRU residual layers without changing the learned initial function.
     *
     * The existing initializer supplies all new parameters deterministically from [seed]. Every
     * original tensor, including the tied embedding and final normalization, is then deep-copied
     * unchanged. Each added layer's wo, w2 and b2 are zero: both residual branches initially add
     * zero, while the nonzero upstream matrices allow wo and w2 to learn on the first update.
     * Subsequent updates can also train the upstream matrices after an output path becomes nonzero.
     *
     * At most twelve total layers are permitted by LM1. This operation does not bypass checkpoint
     * size, inference-time or runtime-promotion gates; the caller must check those separately.
     */
    fun deepenMinGru(params: LmParams, additionalLayers: Int, seed: Long = 0L): LmParams {
        validateSource(params)
        require(additionalLayers in 1..(12 - params.config.layers)) { "additional layers must keep total depth in 1..12" }
        val config = params.config.copy(layers = params.config.layers + additionalLayers)
        val count = config.layout().sumOf { (_, rows, cols) -> rows.toLong() * cols }
        require(count <= Int.MAX_VALUE.toLong()) { "deepened parameter count overflows LM1" }
        val result = LmParams.init(config, seed)
        for ((name, source) in params.mats) result.mats[name] = source.copy()
        for (layer in params.config.layers until config.layers) {
            for (leaf in listOf("wo", "w2", "b2")) result.mats.getValue("l$layer.$leaf").data.fill(0f)
        }
        require(result.allFinite()) { "non-finite deepened parameters" }
        return result
    }

    private fun validateSource(params: LmParams) {
        require(params.config.arch == LmArch.MINGRU) { "this experiment supports minGRU checkpoints only" }
        val layout = params.config.layout()
        require(params.mats.keys.toList() == layout.map { it.first }) { "source parameter names or order do not match LM1" }
        for ((name, rows, cols) in layout) {
            val matrix = params.mats.getValue(name)
            require(matrix.rows == rows && matrix.cols == cols && matrix.data.size.toLong() == rows.toLong() * cols) {
                "source shape does not match LM1 at $name"
            }
            require(matrix.data.all { it.isFinite() }) { "source contains non-finite parameters at $name" }
        }
    }
}
