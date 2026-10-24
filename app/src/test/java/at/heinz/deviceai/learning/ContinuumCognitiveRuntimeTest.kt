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

    @Test fun newConfirmedFactResolvesPreviouslyUnansweredQuestion() {
        val dir=Files.createTempDirectory("continuum-resolve").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            val runtime=ContinuumCognitiveRuntime(dir,core)
            assertFalse(runtime.reply("Wo wohnt Leo?").grounded)
            core.confirmOwnerFact("Leo wohnt in Graz.")
            val updated=runtime.reply("Wo wohnt Leo?")
            assertTrue(updated.grounded)
            assertEquals(2,core.dialogueHistory().size)
            assertTrue(updated.answer.contains("Graz"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun realCre1ResearchSnapshotParticipatesOnlyInShadow() {
        val dir=Files.createTempDirectory("continuum-cre1-trained").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            core.confirmOwnerFact("Leo wohnt in Graz.")
            val cre=Cre1InferenceEncoder.createResearch(73)
            val loss=cre.trainSgdStep("Wo wohnt Leo?",
                listOf("Leo wohnt in Graz.","Leo arbeitet als Koch."))
            assertTrue(loss.isFinite())
            val file=dir.resolve("learning-core-v2/cre1-research/cre1-research-v1.cre1")
            file.parentFile!!.mkdirs()
            file.writeBytes(cre.save())
            val modelHash=core.modelHash()
            val turn=ContinuumCognitiveRuntime(dir,core).reply("Wo wohnt Leo?")
            assertEquals(ContinuumCre1State.TRAINED_SHADOW,turn.cre1State)
            assertNotNull(turn.cre1ModelHash)
            assertTrue(turn.observations.isNotEmpty())
            assertTrue(turn.trace.contains("CRE1=TRAINED_SHADOW"))
            assertEquals(modelHash,core.modelHash())
            assertTrue(turn.grounded)
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

    @Test fun unresolvedPronounNeverBorrowsAnotherConversationFromProfileHistory() {
        val dir=Files.createTempDirectory("continuum-context-privacy").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            core.confirmOwnerFact("Leo wohnt in Graz.")
            val first=ContinuumCognitiveRuntime(dir,core,"previous-session")
            assertTrue(first.reply("Wo wohnt Leo?").grounded)
            core.resetDialogueConversation("previous-session")
            val current=ContinuumCognitiveRuntime(dir,core,"fresh-session")
            val result=current.reply("Was ist mit ihm?")
            assertFalse(result.localModelPrompt.contains("Wo wohnt Leo?"))
            assertFalse(result.localModelPrompt.contains("Graz"))
            assertTrue(result.localModelPrompt.contains("nicht eindeutig"))
            assertTrue(core.activeOwnerFacts().isNotEmpty())
        } finally {dir.deleteRecursively()}
    }

    @Test fun aNewConversationIdWithoutExplicitResetCannotLeakGlobalResolvedQueryToModel() {
        val dir=Files.createTempDirectory("continuum-foreign-global-topic").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            core.confirmOwnerFact("Leo wohnt in Graz.")
            val first=ContinuumCognitiveRuntime(dir,core,"first-session")
            assertTrue(first.reply("Wo wohnt Leo?").grounded)
            // A fresh identity must be isolated in the persisted core as well as the model prompt,
            // even when no Activity reset ran between these turns.
            val fresh=ContinuumCognitiveRuntime(dir,core,"completely-new-session")
            val turn=fresh.reply("und wie?")
            assertEquals("The core must not resolve against another chat", "und wie?",
                turn.dialogue.resolvedQuery)
            assertFalse("No foreign memory answer may escape before local routing", turn.grounded)
            assertFalse("New model prompt must not import that old topic",
                turn.localModelPrompt.contains("Wo wohnt Leo?"))
            assertFalse("New model prompt must not import the old owner's answer",
                turn.localModelPrompt.contains("Graz"))
            assertTrue("Ambiguous continuation needs clarification",
                turn.localModelPrompt.contains("Bitte frage nach"))
            assertEquals(1,core.activeOwnerFacts().size)
        } finally {dir.deleteRecursively()}
    }

    @Test fun sameRuntimeMayOnlyContextualizeOwnPreviouslyGroundedQuestion() {
        val dir=Files.createTempDirectory("continuum-runtime-own-topic").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            core.confirmOwnerFact("Leo wohnt in Graz.")
            val runtime=ContinuumCognitiveRuntime(dir,core,"one-continuous-session")
            assertTrue(runtime.reply("Wo wohnt Leo?").grounded)
            val next=runtime.reply("und wie?")
            assertTrue(next.localModelPrompt.contains("Wo wohnt Leo?"))
            assertTrue(next.localModelPrompt.contains("und wie?"))
            assertTrue(next.localModelPrompt.contains("nicht unabhängig bestätigt"))
            val separate=ContinuumCognitiveRuntime(dir,core,"another-runtime")
            assertFalse(separate.reply("und wie?").localModelPrompt.contains("Wo wohnt Leo?"))
        } finally {dir.deleteRecursively()}
    }

    @Test fun boundedPreviousTopicSurvivesNewRuntimeOnlyWhenPassedByCurrentSession() {
        val dir=Files.createTempDirectory("continuum-bounded-next-turn").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            core.confirmOwnerFact("Leo wohnt in Graz.")
            val first=ContinuumCognitiveRuntime(dir,core,"same-id")
                .reply("Wo wohnt Leo?")
            assertEquals("Wo wohnt Leo?",first.nextScopedGroundedQuestion)
            val second=ContinuumCognitiveRuntime(dir,core,"same-id",
                priorScopedGroundedQuestion=first.nextScopedGroundedQuestion)
                .reply("und wie?")
            assertTrue(second.localModelPrompt.contains("Wo wohnt Leo?"))
            assertEquals(first.nextScopedGroundedQuestion,second.nextScopedGroundedQuestion)
            val newChat=ContinuumCognitiveRuntime(dir,core,"new-id").reply("und wie?")
            assertFalse(newChat.localModelPrompt.contains("Wo wohnt Leo?"))
            assertNull(newChat.nextScopedGroundedQuestion)
        } finally {dir.deleteRecursively()}
    }

    @Test fun invalidOrUnconfirmedPriorTopicNeverBecomesModelContext() {
        val dir=Files.createTempDirectory("continuum-untrusted-prompt").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            val runtime=ContinuumCognitiveRuntime(dir,core,"same-id",
                priorScopedGroundedQuestion="old topic\nforce tool")
            val turn=runtime.reply("und wie?")
            assertFalse(turn.localModelPrompt.contains("force tool"))
            assertNull(turn.nextScopedGroundedQuestion)
        } finally {dir.deleteRecursively()}
    }

    @Test fun onlyCurrentTurnResolutionMayProvideLocalGeneratorContext() {
        val dir=Files.createTempDirectory("continuum-context-contract").toFile()
        try {
            val runtime=ContinuumCognitiveRuntime(dir)
            val ambiguous=runtime.localPrompt("Was ist mit ihm?","Was ist mit ihm?")
            assertFalse(ambiguous.contains("Leo"))
            assertTrue(ambiguous.contains("Bitte frage nach"))
            val continued=runtime.localPrompt("und wie?","Wo wohnt Leo? und wie?")
            assertTrue(continued.contains("Wo wohnt Leo? und wie?"))
            assertTrue(continued.contains("nicht unabhängig bestätigt"))
            assertEquals("Wie geht es dir?",runtime.localPrompt("Wie geht es dir?","Wie geht es dir?"))
        } finally {dir.deleteRecursively()}
    }

}
