package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class NeuralPracticeTest {
    @Test fun explicitManualRehearsalRequiresNeuralCommand() {
        assertEquals(NeuralCommandKind.PRACTICE,NeuralCommandParser.parse("Neuron üben")!!.kind)
        assertEquals(NeuralCommandKind.PRACTICE,NeuralCommandParser.parse("Neuron wiederholen")!!.kind)
        assertNull(NeuralCommandParser.parse("übe zufällig"))
    }

    @Test fun emptyModelHasNoApprovedTrainingData() {
        val lab=NeuralDialogue()
        assertEquals(0,lab.lessonCount())
        val snapshot=lab.save()
        assertThrows(IllegalArgumentException::class.java) { lab.practice() }
        assertArrayEquals(snapshot,lab.save())
    }
    @Test fun boundedRehearsalReportsResultsWithoutFakingSuccess() {
        val lab=NeuralDialogue()
        lab.teach("Servus lieber Freund",0,true)
        assertEquals(1,lab.lessonCount())
        val before=lab.save()
        val reports=mutableListOf<NeuralLearningProgress>()
        val result=lab.practice(100,onProgress={ reports.add(it) })
        assertEquals(100,result.attemptedSteps)
        assertTrue(result.completedSteps in 1..100)
        assertEquals(result.completedSteps,reports.last().step)
        assertEquals(12,result.unfamiliarAfter.total)
        assertEquals(8,result.heldoutAfter.total)
        assertTrue(result.trainingLossAfter.isFinite())
        assertFalse(result.reason.isBlank())
        if (!result.accepted) assertArrayEquals(before,lab.save())
        else assertFalse(before.contentEquals(lab.save()))
        assertEquals(1,lab.lessonCount())
    }
    @Test fun cancellationCannotPublishUnapprovedWeights() {
        val lab=NeuralDialogue()
        lab.teach("Wie spät ist es jetzt",1,true)
        val before=lab.save()
        val result=lab.practice(150,cancelled={ true })
        assertFalse(result.accepted)
        assertEquals(0,result.completedSteps)
        assertArrayEquals(before,lab.save())
    }
    @Test fun savedApprovedTrainingExamplesSurviveRoundTrips() {
        val lab=NeuralDialogue()
        lab.teach("Willkommen heute",0,true)
        val copy=NeuralDialogue.restore(lab.save())
        assertEquals(1,copy.lessonCount())
        val report=copy.practice(50)
        assertEquals(50,report.attemptedSteps)
        assertEquals(1,NeuralDialogue.restore(copy.save()).lessonCount())
    }
}
