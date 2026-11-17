package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class TinyNeuralNetworkTest {
    @Test fun learnsNonlinearHoldoutAndActuallyChangesWeights() {
        val model = TinyNeuralNetwork(2, 42)
        val training = listOf(-0.9, -0.5, 0.5, 0.9).flatMap { a ->
            listOf(-0.8, -0.4, 0.4, 0.8).map { b -> NeuralExample(listOf(a, b), if (a*b < 0) 1 else 0) }
        }
        val holdout = listOf(-0.7, -0.3, 0.3, 0.7).flatMap { a ->
            listOf(-0.6, -0.2, 0.2, 0.6).map { b -> NeuralExample(listOf(a, b), if (a*b < 0) 1 else 0) }
        }
        assertTrue(training.map { it.inputs }.toSet().intersect(holdout.map { it.inputs }.toSet()).isEmpty())
        val before = model.evaluate(training)
        val weights = model.save()
        val report = model.train(training, 4000, 0.1)
        val after = model.evaluate(training)
        val test = model.evaluate(holdout)
        assertEquals(4000, report.steps)
        assertFalse(weights.contentEquals(model.save()))
        assertTrue("$before -> $after", after.loss < before.loss * 0.5)
        assertTrue(test.toString(), test.correct >= 14)
        val restored = TinyNeuralNetwork.restore(model.save())
        holdout.forEach { assertEquals(model.predict(it.inputs), restored.predict(it.inputs), 0.0) }
        assertArrayEquals(TinyNeuralNetwork(2, 42).save(), weights)
        println("NEURAL_XOR training=$before -> $after holdout=$test bytes=${model.save().size} steps=${report.steps}")
    }
    @Test fun invalidNumbersDimensionsAndCorruptedSnapshotsAreRejected() {
        val m = TinyNeuralNetwork(2, 7)
        listOf(listOf(Double.NaN, 0.0), listOf(Double.POSITIVE_INFINITY, 0.0), listOf(2.0, 0.0), listOf(0.0))
            .forEach { input -> assertThrows(IllegalArgumentException::class.java) { m.predict(input) } }
        assertThrows(IllegalArgumentException::class.java) { m.train(emptyList(), 1) }
        assertThrows(IllegalArgumentException::class.java) { m.train(listOf(NeuralExample(listOf(0.0, 0.0), 0)), 5001) }
        val corrupt = m.save().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) { TinyNeuralNetwork.restore(corrupt) }
        assertThrows(IllegalArgumentException::class.java) { TinyNeuralNetwork.restore(m.save() + 0.toByte()) }
    }
    @Test fun zeroBudgetAndCancellationDoNotTrain() {
        val m = TinyNeuralNetwork(2, 5)
        val before = m.save()
        val data = listOf(NeuralExample(listOf(0.1, 0.1), 1))
        assertEquals(0, m.train(data, 100, cancelled = { true }).steps)
        var clock = 0L
        assertEquals(0, m.train(data, 100, maxMillis = 1, nowNanos = { clock.also { clock += 2_000_000 } }).steps)
        assertArrayEquals(before, m.save())
    }
}
