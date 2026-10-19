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
            assertThrows(IllegalArgumentException::class.java) { core.dialogueHistory() }
            core.recordOwnerFact("Sternenreise ist mein Forschungsprojekt")
            // only the conversation context is lost: memories still answer, the bytes are kept
            val turn=core.replyFromMemory("Erzähl mir von Sternenreise")
            assertTrue(turn.contextReset)
            assertTrue(turn.grounded)
            val kept=File(dir,"learning-core-v2").listFiles { f -> f.name.startsWith("dialogue-v1.bin.corrupt-") }!!
            assertEquals(1,kept.size)
            assertArrayEquals(bad,kept.single().readBytes())
            assertEquals(1,core.dialogueHistory().size)
            assertFalse(core.replyFromMemory("Und was weißt du noch darüber?").contextReset)
            core.revokeAllOwnerFacts()
            assertTrue(core.dialogueHistory().isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun repeatedCorruptionKeepsOnlyTheNewestQuarantinedFiles() {
        val dir=Files.createTempDirectory("continuum-dialogue-quarantine").toFile()
        try {
            var now=10L
            val core=ContinuumLearningCore(dir) { now++ }
            val file=File(dir,"learning-core-v2/dialogue-v1.bin")
            repeat(5) { round ->
                core.replyFromMemory("Hallo")
                file.writeBytes(ByteArray(40) { (it+round).toByte() })
                assertTrue(core.replyFromMemory("Hallo").contextReset)
            }
            val kept=File(dir,"learning-core-v2").listFiles { f -> f.name.startsWith("dialogue-v1.bin.corrupt-") }!!
            assertEquals(ContinuumMemoryDialogue.MAX_QUARANTINED,kept.size)
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

    @Test fun germanCompoundSuffixRetrievalKeepsKotlinAndNodeEvidenceContract() {
        val sentence = "Sternenreise ist mein Forschungsprojekt"
        val evidence = ContinuumMemoryDialogue.wordEvidence("Wie heißt mein Projekt?", sentence)
        assertTrue("Deutsch-Komposita sollen erkannt werden: $evidence", evidence >= 0.82)
        assertTrue(evidence >= ContinuumMemoryDialogue.EVIDENCE_THRESHOLD)
        assertTrue(
            ContinuumMemoryDialogue.wordEvidence("Wo ist der Schlüssel?",
                "Der Haustürschlüssel liegt in der Schublade.") >= 0.82
        )
        assertTrue(
            ContinuumMemoryDialogue.wordEvidence("Ist mein Mobilgerät erreichbar?",
                "Dein Gerät ist bereit.") >= 0.82
        )
        assertTrue(ContinuumMemoryDialogue.wordEvidence("Code", "Barcode") < 0.82)
        assertTrue(ContinuumMemoryDialogue.wordEvidence("Quantenperiskop", sentence) < 0.6)
        val dir = Files.createTempDirectory("continuum-compound").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact(sentence)
            val reply = core.replyFromMemory("Wie heißt mein Projekt?")
            assertTrue(reply.grounded)
            assertTrue(reply.answer.contains("Sternenreise"))
        } finally {
            dir.deleteRecursively()
        }
    }

}
