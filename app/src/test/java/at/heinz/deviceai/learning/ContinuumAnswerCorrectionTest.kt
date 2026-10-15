package at.heinz.deviceai.learning

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ContinuumAnswerCorrectionTest {
    private fun temp(name:String)=Files.createTempDirectory(name).toFile()

    @Test fun confirmedCorrectionSurvivesRestartAndAppliesOnlyToExactNormalizedQuestion() {
        val dir=temp("continuum-answer-correction")
        try {
            val core=ContinuumLearningCore(dir)
            val snapshot=core.modelSnapshot()
            val first=core.replyFromMemory("Warum ist die Farbe blau?")
            assertFalse(first.grounded)
            assertTrue(core.confirmAnswerCorrection(first.resolvedQuery,
                "Die Farbe entsteht durch Lichtstreuung."))
            val replay=ContinuumLearningCore(dir).replyFromMemory("  WARUM   ist die farbe blau?  ")
            assertTrue(replay.grounded)
            assertTrue(replay.answer.contains("ausdrücklich bestätigte Korrektur"))
            assertTrue(replay.answer.contains("Lichtstreuung"))
            assertEquals(1,replay.memoryIds.size)
            val distinct=core.replyFromMemory("Warum ist die Farbe rot?")
            assertFalse(distinct.grounded)
            assertFalse(distinct.answer.contains("Lichtstreuung"))
            assertArrayEquals(snapshot,core.modelSnapshot())
            assertEquals(0,core.status().experiences.intentLabels)
            assertEquals(0,core.status().experiences.audits)
        } finally { dir.deleteRecursively() }
    }

    @Test fun replacementIsSingleActiveEntryAndRevocationStopsReplay() {
        val dir=temp("continuum-answer-revision")
        try {
            val core=ContinuumLearningCore(dir)
            val question="Wie funktioniert die eigene Suchfunktion?"
            core.replyFromMemory(question)
            assertTrue(core.confirmAnswerCorrection(question,"Erste vom Eigentümer bestätigte Erklärung."))
            assertFalse(core.confirmAnswerCorrection(question,"Erste vom Eigentümer bestätigte Erklärung."))
            core.replyFromMemory(question)
            assertTrue(core.confirmAnswerCorrection(question,"Zweite ausdrücklich korrigierte Erklärung."))
            assertEquals(ContinuumCorrectionSummary(1,1),core.correctionSummary())
            val fresh=ContinuumLearningCore(dir).replyFromMemory(question)
            assertTrue(fresh.answer.contains("Zweite ausdrücklich korrigierte Erklärung"))
            assertFalse(fresh.answer.contains("Erste vom Eigentümer"))
            assertEquals(1,core.revokeAnswerCorrections())
            assertTrue("revocation purges prior corrected text from the dialogue file",
                core.dialogueHistory().isEmpty())
            assertEquals(0,core.revokeAnswerCorrections())
            val later=ContinuumLearningCore(dir).replyFromMemory(question)
            assertFalse(later.grounded)
            assertFalse(later.answer.contains("korrigierte Erklärung"))
            assertEquals(ContinuumCorrectionSummary(0,2),core.correctionSummary())
        } finally { dir.deleteRecursively() }
    }

    @Test fun staleConversationCannotBeCorrectedAndShortChatsStillWork() {
        val dir=temp("continuum-correction-stale")
        try {
            val core=ContinuumLearningCore(dir)
            core.replyFromMemory("Frage nach dem Spektrum?")
            core.replyFromMemory("Frage nach dem Schwerpunkt?")
            assertThrows(IllegalArgumentException::class.java) {
                core.confirmAnswerCorrection("Frage nach dem Spektrum?","Unzulässiger früherer Bezug")
            }
            assertEquals(0,core.correctionSummary().active)
            assertFalse(core.replyFromMemory("?").grounded)
            assertFalse(core.replyFromMemory("hi").grounded)
        } finally { dir.deleteRecursively() }
    }

    @Test fun correctionBudgetCompactsOnlyCorrectionsAndNeverOwnerFacts() {
        val dir=temp("continuum-correction-budget")
        try {
            // Force all revisions to share a clock tick; newest must still stay.
            val core=ContinuumLearningCore(dir) { 1000L }
            assertTrue(core.recordOwnerFact("Fakt: Indigo | ist | Farbton"))
            val initial=core.modelSnapshot()
            repeat(25) { i ->
                val question="Wie lautet die sichere Korrektur für Fall $i?"
                core.replyFromMemory(question)
                assertTrue(core.confirmAnswerCorrection(question,"Bestätigtes Ergebnis für Fall $i"))
            }
            val store=FileLearningExperienceStore(File(dir,"learning-core-v2"))
            val entries=store.all()
            assertEquals(1,entries.count { it.type==LearningExperienceType.USER_FACT })
            assertEquals(16,entries.count { it.type==LearningExperienceType.ANSWER_CORRECTION })
            assertEquals(17,entries.size)
            assertEquals(16,core.correctionSummary().active)
            assertNotNull(ContinuumAnswerCorrection.find(
                "Wie lautet die sichere Korrektur für Fall 24?",entries))
            assertNull(ContinuumAnswerCorrection.find(
                "Wie lautet die sichere Korrektur für Fall 0?",entries))
            assertArrayEquals(initial,core.modelSnapshot())
            assertEquals(1,core.activeOwnerFacts().size)
        } finally { dir.deleteRecursively() }
    }

    @Test fun fullLedgerFailsClosedWithoutEvictingOtherExperiences() {
        val dir=temp("continuum-correction-full")
        try {
            val core=ContinuumLearningCore(dir)
            val store=FileLearningExperienceStore(File(dir,"learning-core-v2"))
            repeat(FileLearningExperienceStore.MAX_RECORDS) { i ->
                val item=LearningExperienceFactory.create(
                    type=LearningExperienceType.COMMAND_MAPPING,
                    provenance=LearningProvenance.USER_CONFIRMED,
                    trust=LearningTrustClass.OWNER_CONFIRMED,
                    status=LearningStatus.CONFIRMED,
                    content="Testbefehl $i",annotation="get_battery_info",now=i.toLong()
                )
                assertTrue(store.append(item))
            }
            val original=store.all().map { it.id }
            val snapshot=core.modelSnapshot()
            val q="Kann das Gerät den Vollspeicherfall prüfen?"
            core.replyFromMemory(q)
            assertThrows(IllegalStateException::class.java) {
                core.confirmAnswerCorrection(q,"Darf keine alten Belege verdrängen.")
            }
            assertEquals(original,store.all().map { it.id })
            assertArrayEquals(snapshot,core.modelSnapshot())
        } finally { dir.deleteRecursively() }
    }

    @Test fun explicitReconfirmationAfterRevocationIsRequiredToRestoreOldAnswer() {
        val dir=temp("continuum-answer-reconfirm")
        try {
            val core=ContinuumLearningCore(dir) { 1000L }
            val question="Wie wird ein älterer Korrekturstand wieder bestätigt?"
            core.replyFromMemory(question)
            assertTrue(core.confirmAnswerCorrection(question,"Meine ursprüngliche erklärte Antwort."))
            assertEquals(1,core.revokeAnswerCorrections())
            assertNull(ContinuumAnswerCorrection.find(question,
                FileLearningExperienceStore(File(dir,"learning-core-v2")).all()))
            core.replyFromMemory(question)
            assertTrue(core.confirmAnswerCorrection(question,"Meine ursprüngliche erklärte Antwort."))
            assertTrue(core.replyFromMemory(question).grounded)
            assertEquals(ContinuumCorrectionSummary(1,0),core.correctionSummary())
        } finally { dir.deleteRecursively() }
    }

    @Test fun untrustedCorrectionLikeEvidenceIsNeverGivenOwnerAuthority() {
        val item=LearningExperienceFactory.create(
            type=LearningExperienceType.ANSWER_CORRECTION,
            provenance=LearningProvenance.REVIEWED_WEB_SOURCE,
            trust=LearningTrustClass.SOURCE_OBSERVATION,
            status=LearningStatus.CONFIRMED,
            content="Nicht vom Eigentümer bestätigt.",
            annotation=ContinuumAnswerCorrection.queryKey("Wie groß ist Jupiter?")
        )
        assertNull(ContinuumAnswerCorrection.find("Wie groß ist Jupiter?",listOf(item)))
    }
}
