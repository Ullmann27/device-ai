package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class NeuralGradientTest {
    private fun weights(bytes: ByteArray): DoubleArray = LearningSnapshot.decode(bytes) { input ->
        input.readInt(); input.readInt(); input.readLong(); input.readInt()
        DoubleArray(input.readInt()) { input.readDouble() }
    }
    private fun model(w: DoubleArray): TinyNeuralNetwork = TinyNeuralNetwork.restore(LearningSnapshot.encode { out ->
        out.writeInt(0x444e4e31); out.writeInt(2); out.writeLong(42); out.writeInt(0); out.writeInt(w.size)
        w.forEach(out::writeDouble)
    })
    @Test fun everyBackpropagatedGradientAgreesWithFiniteDifferences() {
        val net = TinyNeuralNetwork(2, 42)
        val sample = listOf(NeuralExample(listOf(0.3, -0.6), 1))
        val old = weights(net.save())
        net.train(sample, 1, 0.1)
        val next = weights(net.save())
        for (i in old.indices) {
            val plus = old.copyOf().apply { this[i] += 1e-5 }
            val minus = old.copyOf().apply { this[i] -= 1e-5 }
            val numerical = (model(plus).evaluate(sample).loss - model(minus).evaluate(sample).loss) / 2e-5
            val applied = (old[i] - next[i]) / 0.1
            assertEquals("weight $i", numerical, applied, 1e-7)
        }
    }
    @Test fun entireBatchIsValidatedBeforeMutatingAnyWeights() {
        val net = TinyNeuralNetwork(2, 42)
        val before = net.save()
        assertThrows(IllegalArgumentException::class.java) {
            net.train(listOf(NeuralExample(listOf(0.1, 0.2), 1), NeuralExample(listOf(Double.NaN, 0.2), 0)), 10)
        }
        assertArrayEquals(before, net.save())
    }
}
