package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class NeuralTeachingAuditTest {
    @Test fun approvedLessonProducesMeasurableOutcomeAndSurvivesSnapshot() {
        val lab = NeuralDialogue()
        val baseline = lab.save()
        val audit = lab.teachWithAudit("Servus miteinander",0,true)
        assertEquals(800,audit.training.steps)
        assertTrue(audit.accepted)
        assertEquals(0,audit.retainedTotal)
        assertTrue(audit.newExampleScoreAfter < 0.45)
        assertEquals(1,lab.lessonCount())
        assertFalse(baseline.contentEquals(lab.save()))
        assertEquals(1,NeuralDialogue.restore(lab.save()).lessonCount())
    }

    @Test fun interruptedRetrainingNeverChangesApprovedLessonsOrWeights() {
        val lab=NeuralDialogue()
        lab.teach("Hallo ihr Lieben",0,true)
        val original=lab.save()
        val rejected=lab.teachWithAudit("Wie funktioniert das Gerät",1,true,
            cancelled={true})
        assertFalse(rejected.accepted)
        assertEquals(0,rejected.training.steps)
        assertTrue(rejected.reason.contains("Abgebrochen"))
        assertArrayEquals(original,lab.save())
        assertEquals(1,lab.lessonCount())
    }

    @Test fun oldLabelIsExcludedWhenUserCorrectsExactlyOnePhrase() {
        val lab=NeuralDialogue()
        lab.teach("Kannst du Hallo sagen",0,true)
        val original=lab.save()
        val correction=lab.teachWithAudit("Kannst du Hallo sagen",1,true)
        assertEquals(0,correction.retainedTotal)
        assertEquals(0,correction.retainedBefore)
        assertTrue(correction.newExampleScoreBefore.isFinite())
        assertTrue(correction.newExampleScoreAfter.isFinite())
        if (!correction.accepted) assertArrayEquals(original,lab.save())
        if (correction.accepted) assertEquals(1,lab.lessonCount())
    }

    @Test fun unapprovedExamplesAreRejectedBeforeAnyUpdates() {
        val lab=NeuralDialogue()
        val saved=lab.save()
        assertThrows(IllegalArgumentException::class.java) {
            lab.teachWithAudit("Bitte löschen",1,false)
        }
        assertArrayEquals(saved,lab.save())
    }

    @Test fun onlyAcceptedTrainingMayChangePriorMeasurements() {
        val lab=NeuralDialogue()
        lab.trainDemo(true)
        val before=lab.save()
        val audit=lab.teachWithAudit("Hast du meine Frage verstanden",1,true)
        assertEquals(16,audit.retainedTotal)
        assertTrue(audit.retainedBefore in 0..16)
        assertTrue(audit.retainedAfter in 0..16)
        if (audit.accepted) {
            assertTrue(audit.retainedAfter>=audit.retainedBefore)
            assertTrue(audit.afterLoss!! <= audit.priorLoss!! + 0.075)
        } else {
            assertArrayEquals(before,lab.save())
        }
    }
}
