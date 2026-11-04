package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ContinuumLearningCoreTest {
    @Test fun bothOfflineStagesRunWithoutUserTrainingAndDoNotRepeat() {
        val dir=Files.createTempDirectory("continuum-dual-bootstrap").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            assertNotNull(core.bootstrapOfflineCurriculum())
            assertNotNull(core.bootstrapSemanticCurriculum())
            assertEquals(2,core.status().experiences.audits)
            assertEquals(0,core.status().experiences.intentLabels)
            assertNull(ContinuumLearningCore(dir).bootstrapOfflineCurriculum())
            assertNull(ContinuumLearningCore(dir).bootstrapSemanticCurriculum())
            assertEquals(2,core.status().experiences.audits)
        } finally { dir.deleteRecursively() }
    }

    @Test fun interruptedOfflineStagesAreRetriedInsteadOfCountedAsDone() {
        val dir=Files.createTempDirectory("continuum-interrupted-bootstrap").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            val before=core.modelSnapshot()
            // e.g. Android stopped the job or the app closed during training
            val stopped=core.bootstrapOfflineCurriculum { true }!!
            assertFalse(stopped.accepted)
            assertFalse(stopped.completedEvaluation)
            val semanticStopped=core.bootstrapSemanticCurriculum { true }!!
            assertFalse(semanticStopped.completedEvaluation)
            assertArrayEquals(before,core.modelSnapshot())
            assertEquals("no run-once marker for an interrupted run",0,core.status().experiences.audits)
            val later=ContinuumLearningCore(dir).bootstrapOfflineCurriculum()!!
            assertTrue(later.completedEvaluation)
            assertNotNull(ContinuumLearningCore(dir).bootstrapSemanticCurriculum())
            assertEquals(2,core.status().experiences.audits)
            assertNull(ContinuumLearningCore(dir).bootstrapOfflineCurriculum())
        } finally { dir.deleteRecursively() }
    }

    @Test fun damagedActiveWeightsAreRestoredFromLastKnownGoodOnceAndAudited() {
        val dir=Files.createTempDirectory("continuum-model-recovery").toFile()
        try {
            var now=500L
            val core=ContinuumLearningCore(dir) { now++ }
            assertTrue(core.bootstrapOfflineCurriculum()!!.accepted)
            val previous=core.modelHash()
            val semantic=core.bootstrapSemanticCurriculum()!!
            org.junit.Assume.assumeTrue("needs two saved states",semantic.accepted)
            val audits=core.status().experiences.audits
            val model=java.io.File(dir,"learning-core-v2/subword-v1.bin")
            val damaged=model.readBytes().also { it[40]=(it[40].toInt() xor 0x55).toByte() }
            model.writeBytes(damaged)
            // the last-known-good copy is the state before the semantic stage
            assertEquals(previous,core.modelHash())
            assertEquals(audits+1,core.status().experiences.audits)
            assertTrue(core.trainingAudits(1).single().startsWith("model-recovery · accepted"))
            val kept=java.io.File(dir,"learning-core-v2").listFiles { f -> f.name.startsWith("subword-v1.bin.corrupt-") }!!
            assertArrayEquals(damaged,kept.single().readBytes())
            // healed: later reads neither recover nor audit again
            assertEquals(previous,ContinuumLearningCore(dir).modelHash())
            assertEquals(audits+1,core.status().experiences.audits)
        } finally { dir.deleteRecursively() }
    }

    @Test fun semanticBootstrapIsAuditedIdempotentAndProtectsIntent() {
        val dir=Files.createTempDirectory("continuum-semantic-bootstrap").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            val before=core.modelSnapshot()
            val report=core.bootstrapSemanticCurriculum()!!
            assertEquals("offline-semantic-bootstrap",report.task)
            assertTrue(report.steps>0)
            assertEquals(0,core.status().experiences.sourceObservations)
            assertEquals(1,core.status().experiences.audits)
            if(report.accepted) assertFalse(before.contentEquals(core.modelSnapshot()))
            else assertArrayEquals(before,core.modelSnapshot())
            assertNull(ContinuumLearningCore(dir).bootstrapSemanticCurriculum())
        } finally { dir.deleteRecursively() }
    }

    @Test fun offlineBootstrapDoesNotModifyOwnerData() {
        val dir=Files.createTempDirectory("continuum-owner-bootstrap").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            assertTrue(core.recordOwnerFact("Meine bevorzugte Sprache ist Deutsch."))
            val before=core.modelSnapshot()
            assertNull(core.bootstrapOfflineCurriculum())
            assertArrayEquals(before,core.modelSnapshot())
            assertEquals(1,core.status().experiences.userFacts)
            assertEquals(0,core.status().experiences.audits)
        } finally { dir.deleteRecursively() }
    }

    @Test fun offlineBootstrapNeedsNoOwnerLessonsAndRunsOnlyOnce() {
        val dir=Files.createTempDirectory("continuum-autonomous-bootstrap").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            val before=core.modelSnapshot()
            val report=core.bootstrapOfflineCurriculum()!!
            assertEquals("offline-bootstrap",report.task)
            assertTrue(report.steps>0)
            assertEquals(0,core.status().experiences.intentLabels)
            assertEquals(0,core.status().experiences.userFacts)
            assertEquals(1,core.status().experiences.audits)
            if(report.accepted) assertFalse(before.contentEquals(core.modelSnapshot()))
            else assertArrayEquals(before,core.modelSnapshot())
            assertNull(core.bootstrapOfflineCurriculum())
            assertEquals(1,ContinuumLearningCore(dir).status().experiences.audits)
        } finally { dir.deleteRecursively() }
    }

    @Test fun confirmedIntentUsesReplayHoldoutAndPersistsOnlyAcceptedCandidate() {
        val dir=Files.createTempDirectory("continuum-learning-core").toFile()
        try {
            var now=100L
            val core=ContinuumLearningCore(dir) { now++ }
            val before=core.status()
            val report=core.teachIntent("Kannst du diese Frage beantworten?",1)
            val after=core.status()
            assertEquals("intent",report.task)
            assertTrue(report.steps>0)
            assertTrue(report.replayExamples>=1)
            assertTrue(report.trainingAfter.loss<report.trainingBefore.loss)
            assertTrue(report.holdoutAfter.correct>=report.holdoutBefore.correct)
            assertEquals(1,after.experiences.intentLabels)
            assertTrue(after.experiences.audits>=1)
            if(report.accepted) assertTrue(after.modelUpdates>before.modelUpdates)
            else assertEquals(before.modelUpdates,after.modelUpdates)
        } finally { dir.deleteRecursively() }
    }

    @Test fun cancelledRoundRollsBackWeightsButKeepsApprovedLessonForReplay() {
        val dir=Files.createTempDirectory("continuum-learning-rollback").toFile()
        try {
            var now=200L
            val core=ContinuumLearningCore(dir) { now++ }
            val before=core.modelSnapshot()
            val report=core.teachIntent("Warum ist der Himmel blau?",1) { true }
            val after=core.modelSnapshot()
            assertFalse(report.accepted)
            assertFalse("cancelled training is not a completed evaluation",report.completedEvaluation)
            assertArrayEquals(before,after)
            val status=core.status()
            assertEquals(1,status.experiences.intentLabels)
            assertEquals(1,status.experiences.replayPending)
        } finally { dir.deleteRecursively() }
    }

    @Test fun ownerCorrectionSupersedesConflictingIntentAndSurvivesRestart() {
        val dir=Files.createTempDirectory("continuum-intent-correction").toFile()
        try {
            var now=500L
            val core=ContinuumLearningCore(dir) { now++ }
            val weights=core.modelSnapshot()
            val original=core.teachIntent("Warum ist das so?",1) { true }
            val correction=core.teachIntent("  WARUM ist das so!  ",0) { true }
            val store=FileLearningExperienceStore(java.io.File(dir,"learning-core-v2"))
            assertEquals(listOf(correction.experienceIds.single()),
                store.replay(LearningExperienceType.INTENT_LABEL).map { it.id })
            assertEquals(LearningStatus.REVOKED,
                store.all().single { it.id==original.experienceIds.single() }.status)
            assertEquals("replay is measured after conflicting labels are superseded",1,correction.replayExamples)
            assertEquals(2,ContinuumLearningCore(dir).status().experiences.intentLabels)
            assertArrayEquals("cancellation persists the correction but never weights",weights,core.modelSnapshot())
        } finally { dir.deleteRecursively() }
    }

    @Test fun ownerCanExplicitlyRestoreAnEarlierIntentLabelWithoutReplayingItsReplacement() {
        val dir=Files.createTempDirectory("continuum-intent-reconfirmation").toFile()
        try {
            var now=500L
            val core=ContinuumLearningCore(dir) { now++ }
            val first=core.teachIntent("Warum ist das so?",1) { true }
            val correction=core.teachIntent("Warum ist das so?",0) { true }
            val restored=core.teachIntent("Warum ist das so?",1) { true }
            assertEquals(first.experienceIds,restored.experienceIds)
            val store=FileLearningExperienceStore(java.io.File(dir,"learning-core-v2"))
            assertEquals(first.experienceIds,
                store.replay(LearningExperienceType.INTENT_LABEL).map { it.id })
            assertEquals(LearningStatus.REVOKED,
                store.all().single { it.id==correction.experienceIds.single() }.status)
            assertEquals(1,restored.replayExamples)
        } finally { dir.deleteRecursively() }
    }

    @Test fun reconfirmedOldIntentParticipatesInBoundedReplay() {
        val dir=Files.createTempDirectory("continuum-intent-replay-refresh").toFile()
        try {
            val store=FileLearningExperienceStore(java.io.File(dir,"learning-core-v2"))
            val lessons=(0..70).map { i ->
                LearningExperienceFactory.create(LearningExperienceType.INTENT_LABEL,
                    LearningProvenance.USER_CONFIRMED,LearningTrustClass.OWNER_CONFIRMED,
                    LearningStatus.CONFIRMED,"Bestätigte Frage Nummer $i","1",now=i.toLong())
            }
            store.appendMissing(lessons)
            ContinuumLearningCore(dir) { 500L }.teachIntent(lessons.first().content,1) { true }
            val replay=FileLearningExperienceStore(java.io.File(dir,"learning-core-v2"))
                .replay(LearningExperienceType.INTENT_LABEL,64)
            assertEquals(64,replay.size)
            assertEquals("the latest owner instruction must be trained even if its id is old",
                lessons.first().id,replay.last().id)
        } finally { dir.deleteRecursively() }
    }

    @Test fun invalidIntentTextCannotPoisonPersistentReplay() {
        val dir=Files.createTempDirectory("continuum-invalid-intent").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            assertThrows(IllegalArgumentException::class.java) { core.teachIntent("!!!",1) }
            assertEquals("validation must happen before the lesson is persisted",0,
                core.status().experiences.intentLabels)
            assertEquals(1,core.teachIntent("Warum ist das so?",1) { true }.replayExamples)
        } finally { dir.deleteRecursively() }
    }

    @Test fun sourceTrainingIsSkippedWhenTheOwnerLedgerHasNoObservationCapacity() {
        for(ownerCount in listOf(FileLearningExperienceStore.MAX_RECORDS-1,FileLearningExperienceStore.MAX_RECORDS)) {
            val dir=Files.createTempDirectory("continuum-source-capacity").toFile()
            try {
                val core=ContinuumLearningCore(dir)
                core.syncOwnerFacts((0 until ownerCount).map { "Mein bestätigter Fakt $it" })
                val before=core.modelSnapshot()
                val sources=listOf(
                    MentorSource("Biologie","https://de.wikipedia.org/wiki/Fotosynthese",
                        "a".repeat(64),"Pflanzen nutzen Sonnenlicht und speichern Energie durch Fotosynthese."),
                    MentorSource("Technik","https://de.wikipedia.org/wiki/Elektromotor",
                        "b".repeat(64),"Ein Elektromotor wandelt elektrische Energie in mechanische Bewegung um.")
                )
                assertNull("never complete a source batch that could not be fully persisted",
                    core.learnFromSources(sources))
                assertArrayEquals(before,core.modelSnapshot())
                assertEquals(FileLearningExperienceStore.MAX_RECORDS-ownerCount,
                    core.status().experiences.sourceObservations)
            } finally { dir.deleteRecursively() }
        }
    }

    @Test fun sourceObservationsTrainRepresentationWithoutOwnerTrustOrIntentAuthority() {
        val dir=Files.createTempDirectory("continuum-learning-sources").toFile()
        try {
            var now=300L
            val core=ContinuumLearningCore(dir) { now++ }
            val sources=listOf(
                MentorSource("Biologie","https://de.wikipedia.org/wiki/Fotosynthese",
                    "a".repeat(64),"Pflanzen nutzen Sonnenlicht und speichern Energie durch Fotosynthese."),
                MentorSource("Technik","https://de.wikipedia.org/wiki/Elektromotor",
                    "b".repeat(64),"Ein Elektromotor wandelt elektrische Energie in mechanische Bewegung um.")
            )
            val report=core.learnFromSources(sources)!!
            assertEquals("semantic",report.task)
            assertEquals(2,core.status().experiences.sourceObservations)
            val raw=FileLearningExperienceStore(java.io.File(dir,"learning-core-v2")).all()
            assertTrue(raw.filter { it.type==LearningExperienceType.SOURCE_TEXT }.all {
                it.trust==LearningTrustClass.SOURCE_OBSERVATION &&
                    it.provenance==LearningProvenance.MENTOR_WIKIPEDIA
            })
            assertEquals(0,core.status().experiences.intentLabels)
        } finally { dir.deleteRecursively() }
    }

    @Test fun ownerFactsAndCommandsAreAuditableAndRevocableWithoutTrainingAuthority() {
        val dir=Files.createTempDirectory("continuum-learning-owner").toFile()
        try {
            var now=350L
            val core=ContinuumLearningCore(dir) { now++ }
            val before=core.modelSnapshot()
            assertTrue(core.recordOwnerFact("Fakt: Nova | ist | Mobilgerät"))
            assertTrue(core.recordCommandMapping("Prüfe meine Energie","get_battery_info"))
            assertEquals(1,core.status().experiences.userFacts)
            assertEquals(1,core.status().experiences.commandMappings)
            assertArrayEquals(before,core.modelSnapshot())
            assertEquals(1,core.revokeOwnerFact("Fakt: Nova | ist | Mobilgerät"))
            assertEquals(1,core.revokeAllCommandMappings())
            assertEquals(2,core.status().experiences.revoked)
            assertArrayEquals(before,core.modelSnapshot())
        } finally { dir.deleteRecursively() }
    }

    @Test fun modelAndExperienceStateSurviveFreshCoordinatorInstance() {
        val dir=Files.createTempDirectory("continuum-learning-persist").toFile()
        try {
            var now=400L
            val first=ContinuumLearningCore(dir) { now++ }
            first.teachIntent("Guten Abend zusammen",0)
            val snapshot=first.modelSnapshot()
            val stats=first.status().experiences
            val restored=ContinuumLearningCore(dir) { now++ }
            assertArrayEquals(snapshot,restored.modelSnapshot())
            assertEquals(stats,restored.status().experiences)
        } finally { dir.deleteRecursively() }
    }
}
