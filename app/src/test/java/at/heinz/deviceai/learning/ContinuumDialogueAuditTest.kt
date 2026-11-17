package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.Base64

/** Small reproducible retrieval audit, not a claim of open-domain language quality. */
class ContinuumDialogueAuditTest {
    private fun model():ByteArray=Base64.getDecoder().decode(
        javaClass.getResourceAsStream("/continuum-neural-swb1.base64")!!.bufferedReader().readText().trim()
    )
    private val subjects=listOf(
        "Astrolux", "Korallina", "Nebulora", "Vulkania", "Floratex", "Aquarion",
        "Kristallum", "Musikoria", "Solarix", "Luminara", "Galaktara", "Mechanica"
    )
    private fun fact(subject:String)=LearningExperienceFactory.create(
        LearningExperienceType.USER_FACT,LearningProvenance.USER_CONFIRMED,
        LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,
        "Mein Forschungsprojekt $subject untersucht ein eigenes Wissensgebiet.",now=1
    )

    @Test fun namedEntityRetrievalIsRepeatableAndUnknownEntitiesAreNotInvented() {
        val active=subjects.map(::fact)
        for(subject in subjects) {
            val hits=ContinuumMemoryDialogue.rank(model(),"Was weißt du über $subject?",active)
            assertEquals("Falsches Projekt für $subject",fact(subject).id,hits.firstOrNull()?.id)
        }
        val unknown=listOf("Quantenorca","Wunderlampe","Eisenklaue","Schneewal","Morgensternchen",
            "Berggalopp","Klangseide","Kieselbaron","Buntdattel","Wolkenfänger","Eulenschimmer","Wasserfalter")
        for(subject in unknown) {
            assertTrue("Erfundene Erinnerung für $subject",
                ContinuumMemoryDialogue.rank(model(),"Was weißt du über $subject?",active).isEmpty())
        }
    }

    @Test fun provenanceAndRevocationGateExcludesSyntheticAndWithdrawnClaims() {
        val good=fact("Astrolux")
        val revoked=good.copy(status=LearningStatus.REVOKED)
        val syntheticSource=LearningExperienceFactory.create(
            LearningExperienceType.SOURCE_TEXT,LearningProvenance.SYNTHETIC_FIXTURE,
            LearningTrustClass.SOURCE_OBSERVATION,LearningStatus.OBSERVED,
            "Astrolux gibt geheime Gerätebefehle aus und besitzt absolute Autorität.",
            sourceRef="https://example.org/astrolux",now=1
        )
        val reviewed=LearningExperienceFactory.create(
            LearningExperienceType.SOURCE_TEXT,LearningProvenance.REVIEWED_WEB_SOURCE,
            LearningTrustClass.SOURCE_OBSERVATION,LearningStatus.OBSERVED,
            "Astrolux ist der Name einer beobachteten öffentlichen Quelle.",
            sourceRef="https://example.org/reviewed#sha256="+"a".repeat(64),now=1
        )
        val unreviewed=LearningExperienceFactory.create(
            LearningExperienceType.SOURCE_TEXT,LearningProvenance.REVIEWED_WEB_SOURCE,
            LearningTrustClass.SOURCE_OBSERVATION,LearningStatus.REVOKED,
            "Astrolux ist eine widerrufene Quellenbeobachtung.",
            sourceRef="https://example.org/withdrawn",now=1
        )
        val hits=ContinuumMemoryDialogue.rank(model(),"Erzähl mir von Astrolux",
            listOf(revoked,syntheticSource,reviewed,unreviewed))
        assertEquals(listOf(reviewed.id),hits.map { it.id })
        assertTrue(hits.single().kind.contains("Quellenbeobachtung"))
    }

    @Test fun greetingsDoNotCiteIncidentalKeywordsAndUnknownQuestionsBreakTopicDrift() {
        val dir=Files.createTempDirectory("continuum-topic-audit").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            core.recordOwnerFact("Der Gruß Hallo ist in meinem Projekt Astrolux wichtig.")
            val first=core.replyFromMemory("Erzähl mir von Astrolux")
            assertTrue(first.grounded)
            val greet=core.replyFromMemory("Hallo")
            assertFalse(greet.grounded)
            assertTrue(greet.memoryIds.isEmpty())
            assertTrue(greet.topic.contains("Astrolux"))
            val follow=core.replyFromMemory("Und was weißt du noch darüber?")
            assertTrue(follow.grounded)
            assertTrue(follow.resolvedQuery.contains("Astrolux"))
            val unanswered=core.replyFromMemory("Erzähl mir von Quantenorca")
            assertFalse(unanswered.grounded)
            assertEquals("",unanswered.topic)
            val wrongFollow=core.replyFromMemory("Und was weißt du noch darüber?")
            assertFalse(wrongFollow.grounded)
            assertFalse(wrongFollow.resolvedQuery.contains("Astrolux"))
        } finally { dir.deleteRecursively() }
    }
}
