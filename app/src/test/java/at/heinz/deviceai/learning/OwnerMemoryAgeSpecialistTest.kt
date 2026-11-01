package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/** Real ledger → existing SWB/EWM scope → dialogue. No model or session is a fact writer. */
class OwnerMemoryAgeSpecialistTest {
    private fun withCore(block: (ContinuumLearningCore) -> Unit) {
        val dir = Files.createTempDirectory("continuum-owner-age").toFile()
        var clock = 1000L
        try { block(ContinuumLearningCore(dir) { clock++ }) } finally { dir.deleteRecursively() }
    }

    @Test fun confirmedNamedAgeGetsExactValueAndOriginalMemoryIdReadOnly() = withCore { core ->
        core.confirmOwnerFact("Meine Tochter Klara ist 29.")
        core.confirmOwnerFact("Anna ist 42 Jahre alt.")
        val facts = core.activeOwnerFacts()
        val weights = core.modelSnapshot()
        val age = core.replyFromMemory("Wie alt ist Klara?", "age-a")
        assertTrue(age.answer, age.answer.startsWith("Aus deiner bestätigten Altersangabe: 29 Jahre\n"))
        val id = facts.single { it.content.startsWith("Meine Tochter") }.id
        assertTrue(age.answer, age.answer.contains("Beleg: Erinnerung $id"))
        assertTrue(id in age.memoryIds)
        assertTrue(age.answer.contains("kein automatisch fortgeschriebenes Alter"))
        assertTrue(core.replyFromMemory("Wie alt ist Anna?", "age-b").answer.startsWith("Aus deiner bestätigten Altersangabe: 42 Jahre\n"))
        assertEquals(facts, core.activeOwnerFacts())
        assertArrayEquals(weights, core.modelSnapshot())
    }

    @Test fun correctionUsesExistingLedgerChronologyAndRevocationNeverResurrectsOldAge() = withCore { core ->
        core.confirmOwnerFact("Klara ist 29.")
        core.confirmOwnerFact("Klara ist 30 Jahre alt.")
        assertTrue(core.replyFromMemory("Wie alt ist Klara?", "age-a").answer.startsWith("Aus deiner bestätigten Altersangabe: 30 Jahre\n"))
        assertEquals(1, core.revokeOwnerFact("Klara ist 30 Jahre alt."))
        assertFalse(core.replyFromMemory("Wie alt ist Klara?", "age-b").answer.contains("Aus deiner bestätigten Altersangabe"))
    }

    @Test fun newerNegatedFamilyAgeInvalidatesTheOldNamedValue() = withCore { core ->
        core.confirmOwnerFact("Meine Tochter Klara ist 29.")
        core.confirmOwnerFact("Meine Tochter Klara ist nicht 29.")
        assertFalse(core.replyFromMemory("Wie alt ist Klara?", "age-negation").answer.contains("Aus deiner bestätigten Altersangabe"))
    }

    @Test fun newerFamilyAgeWithUnitKeepsTheSameNamedHolder() = withCore { core ->
        core.confirmOwnerFact("Meine Tochter Klara ist 29.")
        core.confirmOwnerFact("Meine Tochter Klara ist 30 Jahre alt.")
        assertTrue(core.replyFromMemory("Wie alt ist Klara?", "age-unit").answer.startsWith("Aus deiner bestätigten Altersangabe: 30 Jahre\n"))
    }

    @Test fun identitiesAndSessionsCannotSupplyMissingNamedEvidence() = withCore { core ->
        core.confirmOwnerFact("Klara ist 29.")
        core.confirmOwnerFact("Klara Berg ist 35.")
        assertFalse(core.replyFromMemory("Wie alt ist Klara?", "age-a").answer.contains("Aus deiner bestätigten Altersangabe"))
        assertFalse(core.replyFromMemory("Wie alt ist Eva?", "age-b").answer.contains("Aus deiner bestätigten Altersangabe"))
        assertFalse(core.replyFromMemory("Und ihre?", "age-c").answer.contains("Aus deiner bestätigten Altersangabe"))
    }

    @Test fun numbersInOtherAttributesNegationsHistoryAndReportedSpeechAreNotAge() = withCore { core ->
        for (fact in listOf("Klara ist 29 Meter groß.", "Anna ist nicht 42.", "Eva war 31.",
            "Mira ist früher 20 gewesen.", "Otto ist 27, sagt Ben.", "Leo ist 25 und Ben ist 32.")) core.confirmOwnerFact(fact)
        for (name in listOf("Klara", "Anna", "Eva", "Mira", "Otto", "Leo"))
            assertFalse(name, core.replyFromMemory("Wie alt ist $name?", "age-$name").answer.contains("Aus deiner bestätigten Altersangabe"))
    }

    @Test fun scopeCannotElevateWebOrUnconfirmedRecordsEvenWithForgedRanking() {
        val web = LearningExperienceFactory.create(LearningExperienceType.SOURCE_TEXT,
            LearningProvenance.PUBLIC_WEB_SOURCE, LearningTrustClass.SOURCE_OBSERVATION,
            LearningStatus.OBSERVED, "Klara ist 29.", sourceRef="https://example.org/fact", now=1L)
        val unconfirmed = LearningExperienceFactory.create(LearningExperienceType.USER_FACT,
            LearningProvenance.USER_CONFIRMED, LearningTrustClass.OWNER_CONFIRMED,
            LearningStatus.OBSERVED, "Klara ist 29.", now=2L)
        for (e in listOf(web, unconfirmed)) {
            val hits = listOf(ContinuumMemoryHit(e.id, e.content, "Bestätigte Nutzererinnerung", null, 1.0, 1.0))
            assertTrue(OwnerAttributeEvidenceScope.select("Wie alt ist Klara?", listOf(e), hits).isNullOrEmpty())
        }
    }

    @Test fun equalTimeContradictionsNeverBecomeAnExactAge() {
        val dir = Files.createTempDirectory("continuum-age-tie").toFile()
        try {
            val core = ContinuumLearningCore(dir) { 1000L }
            core.confirmOwnerFact("Klara ist 29.")
            core.confirmOwnerFact("Klara ist 30.")
            assertFalse(core.replyFromMemory("Wie alt ist Klara?", "age-tie").answer.contains("Aus deiner bestätigten Altersangabe"))
        } finally { dir.deleteRecursively() }
    }
}
