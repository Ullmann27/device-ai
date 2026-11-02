package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ContinuumExtendedCurriculumTest {
    @Test fun trainingAndFrozenAuditsNeverOverlap() {
        val x=ContinuumExtendedCurriculum
        x.validate()
        assertEquals(12,x.STAGES)
        assertEquals(1152,x.intentTrain.size)
        assertEquals(64,x.intentAudit.size)
        assertEquals(480,x.semanticTrain.size)
        assertEquals(8,x.semanticAudit.size)
        val seenIntent=mutableSetOf<String>()
        val seenSemantic=mutableSetOf<String>()
        for(i in 0 until x.STAGES) {
            val intent=x.intentBatch(i)
            val semantic=x.semanticBatch(i)
            assertEquals(96,intent.size)
            assertEquals(40,semantic.size)
            for(item in intent)assertTrue(seenIntent.add(item.text))
            for(item in semantic)
                assertTrue(seenSemantic.add(item.left+"|"+item.right))
        }
        assertEquals(x.intentTrain.size,seenIntent.size)
        assertEquals(x.semanticTrain.size,seenSemantic.size)
        assertTrue(x.intentAudit.none { it.text in seenIntent })
        assertTrue(x.semanticAudit.none { it.left+"|"+it.right in seenSemantic })
    }

    @Test fun interruptedStageDoesNotChangeWeightsOrConsumeStage() {
        val root=Files.createTempDirectory("continuum-extended-interrupt").toFile()
        try {
            val core=ContinuumLearningCore(root)
            val before=core.modelSnapshot()
            val result=core.trainExtendedCurriculum { true }!!
            assertFalse(result.completed)
            assertFalse(result.accepted)
            assertEquals(1,result.stage)
            assertArrayEquals(before,core.modelSnapshot())
            val retry=core.trainExtendedCurriculum { true }!!
            assertEquals(1,retry.stage)
            assertEquals(0,core.status().experiences.audits)
        } finally { root.deleteRecursively() }
    }

    @Test fun finishedStageIsAuditedAndRespectsProtectedData() {
        val root=Files.createTempDirectory("continuum-extended-protected").toFile()
        try {
            val core=ContinuumLearningCore(root)
            assertTrue(core.recordOwnerFact("Antwortsprache ist Deutsch."))
            val original=core.modelSnapshot()
            val first=core.trainExtendedCurriculum()!!
            assertEquals(1,first.stage)
            assertEquals(12,first.stagesTotal)
            assertEquals(1,core.status().experiences.userFacts)
            assertEquals("Antwortsprache ist Deutsch.",core.activeOwnerFacts().single().content)
            if(!first.accepted)assertArrayEquals(original,core.modelSnapshot())
            if(first.completed) {
                assertEquals(1,core.status().experiences.audits)
                assertEquals(2,core.trainExtendedCurriculum { true }!!.stage)
            }
        } finally { root.deleteRecursively() }
    }
}
