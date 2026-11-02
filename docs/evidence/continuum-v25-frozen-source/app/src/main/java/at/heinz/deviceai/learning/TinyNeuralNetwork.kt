package at.heinz.deviceai.learning

import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tanh

data class NeuralExample(val inputs: List<Double>, val label: Int)
data class NeuralEvaluation(val loss: Double, val correct: Int, val total: Int)
data class NeuralTraining(val steps: Int, val elapsedNanos: Long, val stoppedEarly: Boolean)

/** Transient training telemetry. No weights or private examples leave this device. */
data class NeuralLearningProgress(
    val step: Int,
    val total: Int,
    val loss: Double,
    val correct: Int,
    val examples: Int
)

/** Original tiny dense MLP: bounded inputs -> 8 tanh units -> one binary logit.
 * Analytic backpropagation + SGD; no pretrained weights, autograd graph, network, or tools.
 * Caller owns data approval and scheduling. Never run training on Android's UI thread.
 */
class TinyNeuralNetwork(val inputSize: Int, private val seed: Long = 42) {
    init { require(inputSize in 1..32) }
    private var weights = initialWeights()
    var updates: Int = 0
        private set
    private fun initialWeights(): DoubleArray {
        val random = Random(seed)
        return DoubleArray(8 * (inputSize + 1) + 9) { i ->
            if (i < 8 * (inputSize + 1) && i % (inputSize + 1) == inputSize || i == 8 * (inputSize + 1) + 8) 0.0
            else (random.nextDouble() * 2 - 1) / sqrt(inputSize.toDouble())
        }
    }
    private fun validated(input: List<Double>): DoubleArray {
        require(input.size == inputSize)
        return input.toDoubleArray().also { require(it.all { x -> x.isFinite() && abs(x) <= 1.0 }) }
    }
    private fun forward(x: DoubleArray): Pair<DoubleArray, Double> {
        val hidden = DoubleArray(8) { h ->
            val offset = h * (inputSize + 1)
            var z = weights[offset + inputSize]
            for (i in x.indices) z += x[i] * weights[offset + i]
            tanh(z)
        }
        val offset = 8 * (inputSize + 1)
        var logit = weights[offset + 8]
        for (h in hidden.indices) logit += hidden[h] * weights[offset + h]
        return hidden to logit
    }
    private fun sigmoid(z: Double): Double = if (z >= 0) 1 / (1 + exp(-z)) else exp(z) / (1 + exp(z))
    @Synchronized fun predict(input: List<Double>): Double = sigmoid(forward(validated(input)).second)
    @Synchronized fun evaluate(examples: List<NeuralExample>): NeuralEvaluation {
        require(examples.size in 1..128)
        var loss = 0.0
        var correct = 0
        examples.forEach { example ->
            require(example.label in 0..1)
            val z = forward(validated(example.inputs)).second
            loss += max(z, 0.0) - z * example.label + ln1p(exp(-abs(z)))
            if ((if (z >= 0) 1 else 0) == example.label) correct++
        }
        return NeuralEvaluation(loss / examples.size, correct, examples.size)
    }
    @Synchronized fun train(
        examples: List<NeuralExample>, steps: Int, learningRate: Double = 0.1,
        maxMillis: Long = 2000, cancelled: () -> Boolean = { false },
        nowNanos: () -> Long = System::nanoTime,
        onProgress: (NeuralLearningProgress) -> Unit = {}
    ): NeuralTraining {
        require(examples.size in 1..128 && steps in 1..5000)
        require(learningRate.isFinite() && learningRate in 0.0001..0.2 && maxMillis in 1..5000)
        require(updates <= 100000 - steps) { "Reset experiment before further training" }
        // Snapshot and validate the entire batch before the first weight mutation.
        val batch = examples.map { require(it.label in 0..1); validated(it.inputs) to it.label }
        val start = nowNanos()
        var completed = 0
        while (completed < steps) {
            if (cancelled() || Thread.currentThread().isInterrupted || nowNanos() - start >= maxMillis * 1000000) break
            val (x, label) = batch[updates % batch.size]
            val (hidden, z) = forward(x)
            val error = sigmoid(z) - label
            val outputOffset = 8 * (inputSize + 1)
            // Compute hidden gradients using the OLD output weights.
            val delta = DoubleArray(8) { h -> error * weights[outputOffset + h] * (1 - hidden[h] * hidden[h]) }
            val next = weights.copyOf()
            for (h in 0 until 8) {
                val offset = h * (inputSize + 1)
                for (i in x.indices) next[offset + i] -= learningRate * delta[h] * x[i]
                next[offset + inputSize] -= learningRate * delta[h]
                next[outputOffset + h] -= learningRate * error * hidden[h]
            }
            next[outputOffset + 8] -= learningRate * error
            check(next.all { it.isFinite() }) { "Non-finite weight update rejected" }
            weights = DoubleArray(next.size) { next[it].coerceIn(-16.0, 16.0) }
            updates++
            completed++
            if (completed == 1 || completed % 100 == 0 || completed == steps) {
                val check = evaluate(examples)
                onProgress(NeuralLearningProgress(completed, steps, check.loss, check.correct, check.total))
            }
        }
        return NeuralTraining(completed, (nowNanos() - start).coerceAtLeast(0), completed < steps)
    }
    @Synchronized fun reset() { weights = initialWeights(); updates = 0 }
    @Synchronized fun save(): ByteArray = LearningSnapshot.encode { out ->
        out.writeInt(0x444e4e31)
        out.writeInt(inputSize)
        out.writeLong(seed)
        out.writeInt(updates)
        out.writeInt(weights.size)
        weights.forEach(out::writeDouble)
    }
    companion object {
        fun restore(bytes: ByteArray): TinyNeuralNetwork = LearningSnapshot.decode(bytes) { input ->
            require(input.readInt() == 0x444e4e31)
            val model = TinyNeuralNetwork(input.readInt(), input.readLong())
            val updates = input.readInt()
            require(updates in 0..100000 && input.readInt() == model.weights.size)
            val weights = DoubleArray(model.weights.size) { input.readDouble() }
            require(weights.all { it.isFinite() && abs(it) <= 16.0 })
            model.weights = weights
            model.updates = updates
            model
        }
    }
}
