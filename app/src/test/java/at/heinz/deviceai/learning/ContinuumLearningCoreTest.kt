package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ContinuumLearningCoreTest {
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
            assertArrayEquals(before,after)
            val status=core.status()
            assertEquals(1,status.experiences.intentLabels)
            assertEquals(1,status.experiences.replayPending)
        } finally { dir.deleteRecursively() }
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
