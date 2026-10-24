package at.heinz.deviceai.learning

import java.nio.file.Files
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ContinuumDialogueConversationBindingTest {
    @Test fun anotherExplicitConversationCannotBorrowThePreviousTopic() {
        val dir = Files.createTempDirectory("continuum-bound-topic").toFile()
        try {
            val first = ContinuumLearningCore(dir)
            first.confirmOwnerFact("Max wohnt in Graz.")
            assertTrue(first.replyFromMemory("Wo wohnt Max?", "chat-a").grounded)
            val facts = first.activeOwnerFacts()
            val weights = first.modelSnapshot()

            val second = ContinuumLearningCore(dir).replyFromMemory("und wie?", "chat-b")
            assertEquals("und wie?", second.resolvedQuery)
            assertFalse(second.grounded)
            assertEquals(facts, first.activeOwnerFacts())
            assertArrayEquals(weights, first.modelSnapshot())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun anotherRuntimeCannotPutAForeignQuestionIntoItsLocalModelPrompt() {
        val dir = Files.createTempDirectory("continuum-bound-prompt").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Max wohnt in Graz.")
            ContinuumCognitiveRuntime(dir, core, "chat-a").reply("Wo wohnt Max?")
            val second = ContinuumCognitiveRuntime(dir, ContinuumLearningCore(dir), "chat-b")
                .reply("Was ist mit ihm?")
            assertFalse(second.grounded)
            assertFalse(second.localModelPrompt.contains("Max"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun theSameExplicitConversationKeepsItsTopicAcrossCoreRecreation() {
        val dir = Files.createTempDirectory("continuum-bound-reopen").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Max wohnt in Graz.")
            core.replyFromMemory("Wo wohnt Max?", "same-chat")
            val reopened = ContinuumLearningCore(dir).replyFromMemory("und wie?", "same-chat")
            assertEquals("Wo wohnt Max? und wie?", reopened.resolvedQuery)
            assertTrue(reopened.grounded)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun anExplicitConversationNeverAdoptsUnboundLegacyHistory() {
        val dir = Files.createTempDirectory("continuum-bound-legacy").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Max wohnt in Graz.")
            core.replyFromMemory("Wo wohnt Max?")
            val explicit = core.replyFromMemory("und wie?", "new-explicit-chat")
            assertEquals("und wie?", explicit.resolvedQuery)
            assertFalse(explicit.grounded)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun theSameRuntimeIdentityKeepsItsGroundedDialogueContextAcrossRecreation() {
        val dir = Files.createTempDirectory("continuum-bound-own-prompt").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Max wohnt in Graz.")
            ContinuumCognitiveRuntime(dir, core, "same-chat").reply("Wo wohnt Max?")
            val reopened = ContinuumCognitiveRuntime(dir, ContinuumLearningCore(dir), "same-chat")
                .reply("und wie?")
            assertTrue(reopened.grounded)
            assertEquals("Wo wohnt Max? und wie?", reopened.dialogue.resolvedQuery)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun switchingBackAfterAnotherConversationAsksFreshInsteadOfBorrowingItsTopic() {
        val dir = Files.createTempDirectory("continuum-bound-switchback").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Max wohnt in Graz.")
            core.confirmOwnerFact("Sternenreise ist mein Forschungsprojekt.")
            core.replyFromMemory("Wo wohnt Max?", "chat-a")
            core.replyFromMemory("Was ist Sternenreise?", "chat-b")
            val returned = core.replyFromMemory("und wie?", "chat-a")
            assertEquals("und wie?", returned.resolvedQuery)
            assertFalse(returned.grounded)
            assertEquals(1, core.dialogueHistory().size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun aLegacyCallCannotBorrowAnExplicitlyBoundHistory() {
        val dir = Files.createTempDirectory("continuum-bound-to-legacy").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Max wohnt in Graz.")
            core.replyFromMemory("Wo wohnt Max?", "chat-a")
            val legacy = ContinuumLearningCore(dir).replyFromMemory("und wie?")
            assertEquals("und wie?", legacy.resolvedQuery)
            assertFalse(legacy.grounded)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun invalidIdentitiesCannotModifyTheCurrentDialogueOrOwnerState() {
        val dir = Files.createTempDirectory("continuum-bound-invalid-id").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Max wohnt in Graz.")
            core.replyFromMemory("Wo wohnt Max?", "chat-a")
            val file = File(dir, "learning-core-v2/dialogue-v1.bin")
            val snapshot = file.readBytes()
            val facts = core.activeOwnerFacts()
            val weights = core.modelSnapshot()
            for (id in listOf("", "../other", "two chats", "x".repeat(81))) {
                assertThrows(IllegalArgumentException::class.java) {
                    core.replyFromMemory("und wie?", id)
                }
                assertThrows(IllegalArgumentException::class.java) {
                    core.replyWithEvidenceInConversation("und wie?", id)
                }
                assertArrayEquals(snapshot, file.readBytes())
            }
            assertEquals(facts, core.activeOwnerFacts())
            assertArrayEquals(weights, core.modelSnapshot())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun anOldResetCannotDiscardANewerBoundConversation() {
        val dir = Files.createTempDirectory("continuum-bound-stale-reset").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Max wohnt in Graz.")
            core.replyFromMemory("Wo wohnt Max?", "chat-a")
            core.replyFromMemory("Wo wohnt Max?", "chat-b")
            val current = core.dialogueHistory()
            assertFalse(core.resetDialogueConversation("chat-a"))
            assertEquals(current, core.dialogueHistory())
            assertEquals("Wo wohnt Max? und wie?", core.replyFromMemory("und wie?", "chat-b").resolvedQuery)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun withdrawalCannotLeaveARevokedQuestionInTheLocalModelPrompt() {
        val dir = Files.createTempDirectory("continuum-bound-withdrawal").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            val fact = "Max wohnt in Graz."
            core.confirmOwnerFact(fact)
            ContinuumCognitiveRuntime(dir, core, "chat-a").reply("Wo wohnt Max?")
            core.revokeOwnerFact(fact)
            val next = ContinuumCognitiveRuntime(dir, core, "chat-a").reply("Was ist mit ihm?")
            assertTrue(next.localModelPrompt.contains("Bitte frage nach"))
            assertFalse(next.grounded)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun anUnansweredNewTopicCannotResurrectAnOlderQuestionInTheLocalPrompt() {
        val dir = Files.createTempDirectory("continuum-bound-unanswered").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Max wohnt in Graz.")
            val runtime = ContinuumCognitiveRuntime(dir, core, "same-chat")
            assertTrue(runtime.reply("Wo wohnt Max?").grounded)
            assertFalse(runtime.reply("Unbekanntes Quantenkaninchen").grounded)
            val next = runtime.reply("Was ist mit ihm?")
            assertTrue(next.localModelPrompt.contains("Bitte frage nach"))
            assertFalse(next.localModelPrompt.contains("Max"))
            assertFalse(next.grounded)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun aCorruptBoundSnapshotIsQuarantinedWhileOwnerFactsRemainAvailable() {
        val dir = Files.createTempDirectory("continuum-bound-corrupt").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Max wohnt in Graz.")
            core.replyFromMemory("Wo wohnt Max?", "chat-a")
            val file = File(dir, "learning-core-v2/dialogue-v1.bin")
            val damaged = file.readBytes().also { it[8] = (it[8].toInt() xor 1).toByte() }
            file.writeBytes(damaged)
            val reply = core.replyFromMemory("Wo wohnt Max?", "chat-a")
            assertTrue(reply.contextReset)
            assertTrue(reply.grounded)
            assertEquals(1, core.activeOwnerFacts().size)
            val kept = file.parentFile!!.listFiles { f -> f.name.startsWith("dialogue-v1.bin.corrupt-") }!!
            assertEquals(1, kept.size)
            assertArrayEquals(damaged, kept.single().readBytes())
        } finally {
            dir.deleteRecursively()
        }
    }
}
