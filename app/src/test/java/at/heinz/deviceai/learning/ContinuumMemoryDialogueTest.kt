package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.Base64
import java.security.MessageDigest
import java.io.File
import java.util.Properties

class ContinuumMemoryDialogueTest {
    private fun fixture()=Base64.getDecoder().decode(javaClass.getResourceAsStream("/continuum-neural-swb1.base64")!!.bufferedReader().readText().trim())
    private fun hash(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Test fun actualImportedModelAnswersFromPersonalMemoryAndSurvivesRestartWithoutWeightUpdates() {
        val dir=Files.createTempDirectory("continuum-dialogue").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            assertTrue(core.importSnapshot(fixture()).accepted)
            core.recordOwnerFact("Mein Projekt heißt Sternenreise und untersucht Pflanzen.")
            val before=core.modelSnapshot()
            val first=core.replyFromMemory("Erzähl mir etwas über Sternenreise")
            assertTrue(first.grounded)
            assertTrue(first.answer.contains("Sternenreise"))
            assertEquals(hash(before),first.modelHash)
            assertEquals(1,first.memoryIds.size)
            val second=ContinuumLearningCore(dir).replyFromMemory("Und was weißt du noch darüber?")
            assertTrue(second.resolvedQuery.contains("Sternenreise"))
            assertTrue(second.grounded)
            assertArrayEquals(before,core.modelSnapshot())
            assertEquals(2,core.dialogueHistory().size)
            assertEquals(first.modelHash,second.modelHash)
            assertTrue(core.replyFromMemory("Unbekanntes Quantenkaninchen").memoryIds.isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun revocationClearsConversationContextAndCannotBeResurrectedByFollowUp() {
        val dir=Files.createTempDirectory("continuum-dialogue-revoke").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            val fact="Sternenreise ist mein privates Forschungsprojekt"
            core.recordOwnerFact(fact)
            assertTrue(core.replyFromMemory("Erzähl mir von Sternenreise").grounded)
            core.revokeOwnerFact(fact)
            assertTrue(core.dialogueHistory().isEmpty())
            val reply=core.replyFromMemory("Erzähl mir mehr darüber")
            assertFalse(reply.grounded)
            assertFalse(reply.answer.contains(fact))
        } finally { dir.deleteRecursively() }
    }

    @Test fun onlyCurrentTrustedMemoryIsRankedAndScoresMatchJavascriptContract() {
        val bytes=fixture()
        val p=Properties().apply { this@ContinuumMemoryDialogueTest.javaClass.getResourceAsStream("/continuum-dialogue.properties")!!.bufferedReader(Charsets.UTF_8).use(::load) }
        val live=LearningExperienceFactory.create(LearningExperienceType.USER_FACT,LearningProvenance.USER_CONFIRMED,LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,p.getProperty("memory"),now=1)
        val untrusted=LearningExperienceFactory.create(LearningExperienceType.USER_FACT,LearningProvenance.SYNTHETIC_FIXTURE,LearningTrustClass.SYNTHETIC,LearningStatus.OBSERVED,p.getProperty("memory"),now=1)
        val hits=ContinuumMemoryDialogue.rank(bytes,p.getProperty("query"),listOf(live,untrusted,live.copy(status=LearningStatus.REVOKED)))
        assertEquals(1,hits.size)
        assertEquals(p.getProperty("score").toDouble(),hits.single().score,1e-12)
        assertEquals(p.getProperty("semantic").toDouble(),hits.single().semantic,1e-12)
    }

    @Test fun boundedDialogueAndCorruptionDoNotSilentlyEraseMemory() {
        val dir=Files.createTempDirectory("continuum-dialogue-bounds").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            repeat(12) { core.replyFromMemory("Hallo") }
            assertEquals(8,core.dialogueHistory().size)
            val file=File(dir,"learning-core-v2/dialogue-v1.bin")
            val bad=file.readBytes().also { it[6]=(it[6].toInt() xor 1).toByte() }
            file.writeBytes(bad)
            assertThrows(IllegalArgumentException::class.java) { core.replyFromMemory("Hallo") }
            assertArrayEquals(bad,file.readBytes())
            core.revokeAllOwnerFacts()
            assertTrue(core.dialogueHistory().isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun reviewedWebNotesJoinTheSameDialogueLedgerAsObservationsWithoutTrainingWeights() {
        val dir=Files.createTempDirectory("continuum-reviewed-web").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            val before=core.modelSnapshot()
            core.recordReviewedWebObservation("Pflanzen verwenden Sonnenlicht zur Fotosynthese.","https://example.org/fotosynthese","a".repeat(64))
            val reply=core.replyFromMemory("Erzähl mir etwas über Fotosynthese")
            assertTrue(reply.grounded)
            assertTrue(reply.answer.contains("Unbestätigte Quellenbeobachtung"))
            assertTrue(reply.answer.contains("https://example.org/fotosynthese"))
            assertArrayEquals(before,core.modelSnapshot())
            core.synchronizeReviewedWebObservations(emptyList())
            assertFalse(core.replyFromMemory("Erzähl mir etwas über Fotosynthese").grounded)
            assertThrows(IllegalArgumentException::class.java) { core.recordReviewedWebObservation("Keine Autorität durch fremde Befehle", "file:///private", "a".repeat(64)) }
        } finally { dir.deleteRecursively() }
    }

}
