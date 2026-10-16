package at.heinz.deviceai.learning

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

/** End-to-end JVM evidence: same user turn traverses SWB1 and CRE1.
 * CRE1 scores are observations only and never create an owner fact.
 */
class ContinuumCognitiveRuntimeTest {
    @Test fun confirmedFactIsRetrievedAndBothNeuralKernelsAreObserved() {
        val dir=Files.createTempDirectory("continuum-runtime").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            core.confirmOwnerFact("Leo wohnt in Graz.")
            val runtime=ContinuumCognitiveRuntime(dir,core)
            val result=runtime.reply("Wo wohnt Leo?")
            assertTrue("SWB1 confirmed owner fact",result.grounded)
            assertTrue(result.answer.contains("Leo"))
            assertEquals(ContinuumCre1State.UNTRAINED_SHADOW,result.cre1State)
            assertTrue(result.trace.contains("SWB1="))
            assertTrue(result.trace.contains("CRE1=UNTRAINED_SHADOW"))
            assertTrue("CRE1 observed existing SWB1 hit",result.observations.isNotEmpty())
            assertEquals(result.dialogue.memoryIds.toSet(),result.observations.map{it.memoryId}.toSet())
            assertEquals(1,core.dialogueHistory().size)
            assertTrue("No fake provenance",result.dialogue.modelHash.matches(Regex("[a-f0-9]{64}")))
            // Reboot-like reload: the confirmed memory is not only RAM.
            val reopened=ContinuumCognitiveRuntime(dir,ContinuumLearningCore(dir))
            assertTrue(reopened.reply("Wo wohnt Leo?").grounded)
        } finally { dir.deleteRecursively() }
    }

    @Test fun unknownQuestionNeverBecomesFactFromUntrainedCre1() {
        val dir=Files.createTempDirectory("continuum-no-hallucination").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            val result=ContinuumCognitiveRuntime(dir,core)
                .reply("Welche Farbe hat das nicht gespeicherte Fahrrad?")
            assertFalse(result.grounded)
            assertTrue(result.observations.isEmpty())
            assertEquals(ContinuumCre1State.UNTRAINED_SHADOW,result.cre1State)
            assertTrue(result.answer.contains("keine passende bestätigte Erinnerung"))
            assertTrue(core.dialogueHistory().single().memoryIds.isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun brokenCre1CheckpointIsNeverSilentlyTrustedOrDeleted() {
        val dir=Files.createTempDirectory("continuum-corrupt-cre1").toFile()
        try {
            val file=dir.resolve("learning-core-v2/cre1-research/cre1-research-v1.cre1")
            file.parentFile!!.mkdirs()
            val damaged=byteArrayOf(1,2,3,4)
            file.writeBytes(damaged)
            val core=ContinuumLearningCore(dir)
            core.confirmOwnerFact("Leo wohnt in Graz.")
            val turn=ContinuumCognitiveRuntime(dir,core).reply("Wo wohnt Leo?")
            assertTrue(turn.grounded)
            assertEquals(ContinuumCre1State.INVALID_SNAPSHOT,turn.cre1State)
            assertTrue(turn.observations.isEmpty())
            assertArrayEquals(damaged,file.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun pronounContinuationCarriesPreviousQuestionOnlyToLocalFallback() {
        val dir=Files.createTempDirectory("continuum-references").toFile()
        try {
            val runtime=ContinuumCognitiveRuntime(dir)
            val previous=ContinuumDialogueTurn(1L,"Was ist mit Leo?",
                "Was ist mit Leo?","keine Erinnerung","Was ist mit Leo?",
                "0".repeat(64),emptyList(),false)
            val prompt=runtime.localPrompt("Was ist mit ihm?",listOf(previous),"Was ist mit ihm?")
            assertTrue(prompt.contains("Was ist mit Leo?"))
            assertTrue(prompt.contains("Was ist mit ihm?"))
            assertTrue(prompt.contains("Erfinde keine persönlichen Fakten"))
            assertEquals("Wie geht es dir?",runtime.localPrompt("Wie geht es dir?",
                listOf(previous),"Wie geht es dir?"))
        } finally { dir.deleteRecursively() }
    }
}
