package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ContinuumIntelligenceAuditTest {
    private val checkpoint = "a".repeat(64)
    private val probes = ContinuumIntelligenceAudit.frozenProbes

    @Test fun independentFixedAuditIsNotOldCurriculum() {
        assertEquals(25, probes.size)
        assertEquals(20, probes.count { it.expected != null })
        assertEquals(5, probes.count { it.expected == null })
        assertEquals(25, probes.map { it.id }.toSet().size)
        val training = ContinuumCdxLargeCurriculum.train.map { it.conditionerText() }.toSet()
        val olderAudit = ContinuumCdxLargeCurriculum.protectedAudit().pairs
            .map { it.conditionerText() }.toSet()
        assertTrue(probes.none { (it.evidence + "\n" + it.question) in training || 
            (it.evidence + "\n" + it.question) in olderAudit })
        probes.forEach { decoderEvidencePrefix(it.evidence, it.question) }
        assertEquals(64, ContinuumIntelligenceAudit.fixtureSha256().length)
    }

    @Test fun perfectAnswersAndHonestUnknownsMeetResearchTarget() {
        val predictions = probes.map { it.expected ?: "unbekannt" }
        val scored = ContinuumIntelligenceAudit.score(predictions, checkpoint, 600)
        assertEquals("MEASURED", scored.status)
        assertEquals(20, scored.correctAnswers)
        assertEquals(0, scored.unsupportedClaims)
        assertEquals(5, scored.validAbstentions)
        assertTrue(scored.targetMet)
        assertTrue(scored.summary().contains("20/20"))
        assertEquals(checkpoint, scored.checkpointSha256)
    }

    @Test fun unsupportedGuessesFailEvenWhenAllKnownAnswersAreCorrect() {
        val responses = probes.map { it.expected ?: "Graz" }
        val audit = ContinuumIntelligenceAudit.score(responses, checkpoint, 600)
        assertEquals(20, audit.correctAnswers)
        assertEquals(5, audit.unsupportedClaims)
        assertEquals(0, audit.validAbstentions)
        assertFalse(audit.targetMet)
    }

    @Test fun partialAnswersAndEchoesAreNotCountedAsCorrect() {
        val replies = probes.map { p ->
            when {
                p.expected == null -> "weiß ich nicht"
                else -> p.expected + " und noch etwas"
            }
        }
        val audit = ContinuumIntelligenceAudit.score(replies, checkpoint, 600)
        assertEquals(0, audit.correctAnswers)
        assertEquals(0, audit.unsupportedClaims)
        assertFalse(audit.targetMet)
    }

    @Test fun immediateEndOfSequenceCannotFakeAnExplicitUnknownAnswer() {
        val responses = probes.map { it.expected ?: "" }
        val audit = ContinuumIntelligenceAudit.score(responses, checkpoint, 600)
        assertEquals(20, audit.correctAnswers)
        assertEquals(0, audit.unsupportedClaims)
        assertEquals(0, audit.validAbstentions)
        assertEquals(5, audit.noResponses)
        assertFalse(audit.targetMet)
        assertTrue(audit.summary().contains("Leere Antworten statt Enthaltung: 5"))
    }

    @Test fun missingGenerationMustNeverMasqueradeAsAbstention() {
        val replies = probes.map { it.expected ?: "" }.toMutableList<String?>()
        replies[0] = null
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumIntelligenceAudit.score(replies, checkpoint, 600)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumIntelligenceAudit.score(replies.dropLast(1), checkpoint, 600)
        }
    }

    @Test fun missingCheckpointIsReportedAndNeverCreatesDecoderFiles() {
        val dir = Files.createTempDirectory("continuum-intelligence-missing").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            val report = core.intelligenceAudit()
            assertEquals("UNAVAILABLE_NO_CHECKPOINT", report.status)
            assertTrue(report.rows.isEmpty())
            assertFalse(report.targetMet)
            assertFalse(java.io.File(dir, "context-decoder-paired.bundle").exists())
            assertFalse(java.io.File(dir, "context-decoder-paired.cdx").exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun cancellingImmediatelyDoesNotAccessOrAlterModel() {
        val dir = Files.createTempDirectory("continuum-intelligence-cancel").toFile()
        try {
            val report = ContinuumLearningCore(dir).intelligenceAudit { true }
            assertEquals("INTERRUPTED", report.status)
            assertTrue(report.rows.isEmpty())
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun readOnlyAuditDoesNotRecoverDamagedSwb1FromBackup() {
        val dir = Files.createTempDirectory("continuum-intelligence-no-recovery").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            val modelDir = java.io.File(dir, "learning-core-v2").apply { mkdirs() }
            val backup = TrainableSubwordEncoder().save()
            java.io.File(modelDir, "subword-v1.lkg").writeBytes(backup)
            val damaged = backup.copyOf().also { it[40] = (it[40].toInt() xor 0x55).toByte() }
            java.io.File(modelDir, "subword-v1.bin").writeBytes(damaged)
            fun files() = dir.walkTopDown().filter { it.isFile }.associate {
                it.relativeTo(dir).path to java.security.MessageDigest.getInstance("SHA-256")
                    .digest(it.readBytes()).joinToString("") { b -> "%02x".format(b.toInt() and 255) }
            }
            val before = files()
            val report = core.intelligenceAudit()
            assertEquals("Read-only audit must not restore weights or append recovery records", before, files())
            assertEquals("UNAVAILABLE_CORRUPT", report.status)
            assertFalse(report.targetMet)
        } finally { dir.deleteRecursively() }
    }

    @Test fun readOnlyPairedProbeDoesNotRecoverDamagedSwb1FromBackup() {
        val dir = Files.createTempDirectory("continuum-paired-no-recovery").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            val modelDir = java.io.File(dir, "learning-core-v2").apply { mkdirs() }
            val backup = TrainableSubwordEncoder().save()
            java.io.File(modelDir, "subword-v1.lkg").writeBytes(backup)
            val damaged = backup.copyOf().also { it[40] = (it[40].toInt() xor 0x55).toByte() }
            val active = java.io.File(modelDir, "subword-v1.bin").apply { writeBytes(damaged) }
            assertNull(core.experimentalPairedDecoderContinuation("Der Code ist NOVA.", "Wie heißt der Code?"))
            assertArrayEquals(damaged, active.readBytes())
            assertArrayEquals(backup, java.io.File(modelDir, "subword-v1.lkg").readBytes())
            assertEquals(setOf("subword-v1.bin", "subword-v1.lkg"), modelDir.list()!!.toSet())
        } finally { dir.deleteRecursively() }
    }

    @Test fun malformedModelIdentifiersAndProbeInputsAreRejected() {
        val answers = probes.map { it.expected ?: "" }
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumIntelligenceAudit.score(answers, "bad-sha", 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumIntelligenceAudit.score(answers, checkpoint, -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumIntelligenceAudit.score(
                listOf("yes", "yes"), checkpoint, 0,
                listOf(
                    ContinuumIntelligenceAudit.Probe("same", "hi", "Q?", "yes"),
                    ContinuumIntelligenceAudit.Probe("same", "hi", "Q?", "yes")
                )
            )
        }
    }
}
