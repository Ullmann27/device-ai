package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class OwnerLocationRevisionBarrierTest {
    private fun owner(text: String, at: Long) = LearningExperienceFactory.create(
        LearningExperienceType.USER_FACT, LearningProvenance.USER_CONFIRMED,
        LearningTrustClass.OWNER_CONFIRMED, LearningStatus.CONFIRMED, text, now=at)
    private fun reply(facts: List<LearningExperience>): ContinuumDialogueTurn {
        val dir=Files.createTempDirectory("synapse-location").toFile()
        try { return ContinuumMemoryDialogue(dir).reply(TrainableSubwordEncoder().save(), facts, "Wo wohnt Anna?", 100) }
        finally { dir.deleteRecursively() }
    }
    @Test fun newerMoveCannotLeaveAnOldResidenceInOrdinaryDialogueRetrieval() {
        for (change in listOf("Anna ist umgezogen.", "Anna zog weg.", "Anna zieht nach Wien.")) {
            val old=owner("Anna wohnt in Graz.",1)
            val turn=reply(listOf(old,owner(change,2)))
            assertFalse(change+": "+turn.answer,old.id in turn.memoryIds)
            assertFalse(change,turn.grounded)
            assertFalse(change,turn.answer.contains("Graz"))
        }
    }
    @Test fun equalTimeChangeAndRevokedChangeDoNotResurrectOldResidence() {
        val old=owner("Anna wohnt in Graz.",1)
        for (change in listOf(owner("Anna ist umgezogen.",1),
            owner("Anna ist umgezogen.",2).copy(status=LearningStatus.REVOKED,updatedAt=3)))
            assertFalse(reply(listOf(old,change)).grounded)
    }
    @Test fun laterExplicitResidenceAndDifferentPersonsRemainRetrievable() {
        val current=owner("Anna wohnt in Graz.",3)
        for (other in listOf(owner("Anna ist umgezogen.",1),owner("Ben ist umgezogen.",4),
            owner("Anna ist Ärztin.",4))) {
            val turn=reply(listOf(other,current))
            assertTrue(turn.answer, current.id in turn.memoryIds)
        }
    }
    @Test fun unconfirmedWebCannotInvalidateOwnerFactAndReadsDoNotMutateRecords() {
        val current=owner("Anna wohnt in Graz.",1)
        val web=LearningExperienceFactory.create(LearningExperienceType.SOURCE_TEXT,
            LearningProvenance.PUBLIC_WEB_SOURCE,LearningTrustClass.SOURCE_OBSERVATION,
            LearningStatus.OBSERVED,"Anna ist umgezogen.",sourceRef="https://example.org/source",now=2)
        val facts=listOf(current,web)
        val before=facts.map { it.copy() }
        assertTrue(current.id in reply(facts).memoryIds)
        assertEquals(before,facts)
    }
}
