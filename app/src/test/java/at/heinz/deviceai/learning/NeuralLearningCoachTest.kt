package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class NeuralLearningCoachTest {
    @Test fun guidedVoiceOrTextExampleNeedsExplicitLessonApproval() {
        val coach = NeuralLearningCoach()
        assertNull(coach.accept("hallo"))
        assertTrue(coach.begin().message.contains("LERN-GESPRÄCH"))
        assertNull(coach.accept("Servus, mein Freund")?.approvedLesson)
        assertNull(coach.accept("andere Antwort")?.approvedLesson)
        val result = coach.accept("Gruß")!!
        assertEquals(NeuralCommandKind.TEACH, result.approvedLesson!!.kind)
        assertEquals(0, result.approvedLesson!!.label)
        assertEquals("Servus, mein Freund", result.approvedLesson!!.text)
        assertTrue(coach.isActive())
        coach.accept("Kannst du mich hören")
        assertEquals(1, coach.accept("Frage")!!.approvedLesson!!.label)
        assertTrue(coach.accept("Ende")!!.closed)
        assertFalse(coach.isActive())
    }

    @Test fun credentialsNeverBecomeGuidedLessons() {
        val c = NeuralLearningCoach()
        c.begin()
        assertNull(c.accept("Mein API Key = 1234")!!.approvedLesson)
        assertNull(c.accept("Gruß")!!.approvedLesson)
        assertNull(c.accept("Neuron Demo")!!.approvedLesson)
    }

    @Test fun liveProgressIsMeasuredOnActualUpdatedWeights() {
        val samples = listOf(
            NeuralExample(listOf(0.0, 0.0), 0),
            NeuralExample(listOf(1.0, 0.0), 1),
            NeuralExample(listOf(0.0, 1.0), 1),
            NeuralExample(listOf(1.0, 1.0), 0)
        )
        val net = TinyNeuralNetwork(2)
        val progress = mutableListOf<NeuralLearningProgress>()
        val before = net.evaluate(samples)
        val result = net.train(samples, 300, onProgress = { progress += it })
        assertEquals(300, result.steps)
        assertEquals(1, progress.first().step)
        assertEquals(300, progress.last().step)
        assertTrue(progress.zipWithNext().all { (a,b) -> a.step < b.step })
        assertEquals(net.evaluate(samples).loss, progress.last().loss, 1e-12)
        assertTrue(net.evaluate(samples).loss < before.loss)
    }

    @Test fun cancelledLearningNeverPublishesDialogueWeights() {
        val lab = NeuralDialogue()
        val saved = lab.save()
        val steps = mutableListOf<Int>()
        lab.trainDemo(true, cancelled = { steps.size >= 2 },
            onProgress = { steps += it.step })
        assertArrayEquals(saved, lab.save())
        assertTrue(steps.size >= 2)
    }
}
