package at.heinz.deviceai.learning

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuralDemoSandboxTest {
    @Test fun previouslyTrainedNeuralWeightsAndLessonsAreNotTouched() {
        val trained = NeuralDialogue()
        val initialTraining = trained.trainDemo(true)
        assertTrue(initialTraining.training.steps > 0)
        val originalBytes = trained.save()
        val originalFingerprint = trained.lessonFingerprint()
        val originalLessons = trained.lessonCount()
        val report = NeuralDemoSandbox.run()
        assertEquals(16, originalLessons)
        assertTrue(report.training.steps > 0)
        assertEquals(8, report.holdout.total)
        assertEquals(12, report.unfamiliarAfter.total)
        assertArrayEquals(originalBytes, trained.save())
        assertEquals(originalFingerprint, trained.lessonFingerprint())
        assertEquals(originalLessons, trained.lessonCount())
        assertArrayEquals(originalBytes, NeuralDialogue.restore(originalBytes).save())
    }

    @Test fun cancellationDoesNotTouchPriorWeightsOrPublishTemporaryModel() {
        val trained = NeuralDialogue()
        trained.trainDemo(true)
        val originalBytes = trained.save()
        val result = NeuralDemoSandbox.run(cancelled = { true })
        assertTrue(result.training.stoppedEarly)
        assertArrayEquals(originalBytes, trained.save())
    }

    @Test fun sandboxDemoWorksWithNoStoredNeuralLessons() {
        val empty = NeuralDialogue()
        val before = empty.save()
        val report = NeuralDemoSandbox.run()
        assertTrue(report.training.steps > 0)
        assertArrayEquals(before, empty.save())
        assertEquals(0, empty.lessonCount())
    }
}
