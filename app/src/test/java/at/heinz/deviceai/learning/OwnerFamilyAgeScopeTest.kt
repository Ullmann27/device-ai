package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Test

/** A production EWM2 regression: family roles must not erase a directly
 * confirmed person's age, and may not authorize somebody else's age. */
class OwnerFamilyAgeScopeTest {
    private fun owner(text: String, at: Long = 1L) = LearningExperienceFactory.create(
        LearningExperienceType.USER_FACT,
        LearningProvenance.USER_CONFIRMED,
        LearningTrustClass.OWNER_CONFIRMED,
        LearningStatus.CONFIRMED,
        text, now=at
    )

    private fun hits(vararg records: LearningExperience) = records.map {
        ContinuumMemoryHit(it.id, it.content, "Bestätigte Nutzererinnerung", null, 1.0, 1.0)
    }

    @Test fun directFamilyRoleMayGroundOnlyNamedPersonsAge() {
        val klara=owner("Meine Tochter Klara ist 29.")
        assertEquals(setOf(klara.id), OwnerAttributeEvidenceScope.select(
            "Wie alt ist Klara?", listOf(klara), hits(klara)))
        assertEquals(emptySet<String>(), OwnerAttributeEvidenceScope.select(
            "Wie alt ist Anna?", listOf(klara), hits(klara)))
    }

    @Test fun revocationAndUnconfirmedWebSourcesNeverBecomeFamilyAge() {
        val klara=owner("Meine Tochter Klara ist 29.")
        val revoked=klara.copy(status=LearningStatus.REVOKED)
        val web=LearningExperienceFactory.create(
            LearningExperienceType.SOURCE_TEXT,
            LearningProvenance.PUBLIC_WEB_SOURCE,
            LearningTrustClass.SOURCE_OBSERVATION,
            LearningStatus.OBSERVED,
            "Meine Tochter Klara ist 29.",
            sourceRef="https://example.org/untrusted", now=2L
        )
        assertEquals(emptySet<String>(), OwnerAttributeEvidenceScope.select(
            "Wie alt ist Klara?", listOf(revoked,web), emptyList()))
    }

    @Test fun newerDirectAgeReplacesOlderFamilyStatement() {
        val earlier=owner("Meine Tochter Klara ist 29.",1L)
        val latest=owner("Klara ist 30.",2L)
        assertEquals(setOf(latest.id), OwnerAttributeEvidenceScope.select(
            "Wie alt ist Klara?", listOf(earlier, latest), hits(earlier,latest)))
    }

    @Test fun reportedSpeechOrConditionalRoleIsNotEvidence() {
        val quoted=owner("Meine Tochter Klara ist 29, sagte Anna.")
        val conditional=owner("Meine Tochter Klara ist 29, falls sie den Test besteht.")
        assertEquals(emptySet<String>(),OwnerAttributeEvidenceScope.select(
            "Wie alt ist Klara?",listOf(quoted,conditional),hits(quoted,conditional)))
    }
}
