package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class NeuralTransferExperimentTest {
    @Test fun structuralFeaturesAreDeterministicAndCanShareSignalsAcrossUnknownWords() {
        val a=NeuralTransferExperiment.features("Was passiert morgen?")
        val b=NeuralTransferExperiment.features("Warum leuchten Sterne?")
        assertEquals(32,a.size)
        assertTrue(a.all { it==0.0 || it==1.0 })
        assertEquals(a,NeuralTransferExperiment.features("Was passiert morgen?"))
        // Both new first words carry an initial-'w' signal without a hard-coded
        // label. Distinct lexical word-hash features may still vary.
        assertTrue((0 until 24).any { a[it]==1.0 && b[it]==1.0 })
        assertThrows(IllegalArgumentException::class.java) {
            NeuralTransferExperiment.features("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NeuralTransferExperiment.features("a".repeat(321))
        }
    }

    @Test fun learningExperimentMeasuresTwoDifferentHoldoutsWithoutSavingWeights() {
        val memory = NeuralDialogue()
        memory.teach("Servus am heutigen Tag",0,true)
        val original = memory.save()
        val first = NeuralTransferExperiment.run()
        assertFalse(first.training.stoppedEarly)
        assertEquals(4000, first.training.steps)
        assertEquals(8,first.known.total)
        assertEquals(12,first.unseenAfter.total)
        assertEquals(10,first.challengeAfter.total)
        assertTrue(first.trained.loss.isFinite())
        assertTrue(first.unseenAfter.loss.isFinite())
        assertTrue(first.challengeAfter.loss.isFinite())
        // The experiment cannot replace the owner's separately saved network.
        assertArrayEquals(original,memory.save())
        val second=NeuralTransferExperiment.run()
        assertEquals(first.unseenAfter, second.unseenAfter)
        assertEquals(first.challengeAfter, second.challengeAfter)
    }

    @Test fun cancellationOfExperimentalWeightsDoesNotWriteAnyModel() {
        val steps = mutableListOf<Int>()
        val result=NeuralTransferExperiment.run(cancelled={steps.size>=2},
            progress={steps += it.step})
        assertTrue(result.training.stoppedEarly)
        assertTrue(result.training.steps<4000)
    }
}
