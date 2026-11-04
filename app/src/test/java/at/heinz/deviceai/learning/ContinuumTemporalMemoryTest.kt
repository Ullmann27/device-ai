package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

/** M2: one coherent, time-aware owner memory over the canonical learning ledger plus session working memory. */
class ContinuumTemporalMemoryTest {
    private fun t(y: Int, m: Int = 1, d: Int = 1) =
        ZonedDateTime.of(y, m, d, 12, 0, 0, 0, ContinuumTemporalMemory.VIENNA).toInstant().toEpochMilli()
    private val now = t(2026, 11, 3)

    private fun fact(text: String, at: Long, status: LearningStatus = LearningStatus.CONFIRMED, updatedAt: Long = at,
                     annotation: String? = null) =
        LearningExperienceFactory.create(LearningExperienceType.USER_FACT, LearningProvenance.USER_CONFIRMED,
            LearningTrustClass.OWNER_CONFIRMED, status, text, annotation = annotation, now = at)
            .copy(updatedAt = updatedAt)

    private fun memory(vararg facts: LearningExperience, working: List<WorkingTurn> = emptyList()) =
        ContinuumTemporalMemory.fromLedger(facts.toList(), working)

    private fun ask(m: ContinuumTemporalMemory, q: String) = assertNotNull(q, m.answer(q, now)).let { m.answer(q, now)!! }

    private val wien = fact("Ich wohne in Wien.", t(2018, 3, 1))
    private val graz = fact("Ich bin nach Graz gezogen.", t(2022, 6, 1))

    @Test fun currentResidenceFollowsAnExplicitMove() {
        val a = ask(memory(wien, graz), "Wo wohne ich?")
        assertEquals(TemporalAnswerKind.ANSWER, a.kind)
        assertTrue(a.text, a.text.contains("Graz"))
        assertFalse(a.text, a.text.substringBefore("Beleg").contains("Wien"))
        assertEquals(listOf(graz.id), a.citedIds)
        assertEquals(setOf(MemoryTier.LONG_TERM), a.tiers)
    }

    @Test fun pastYearsAndTheOriginalValueStayRetrievable() {
        val m = memory(wien, graz)
        assertTrue(ask(m, "Wo wohnte ich 2020?").text.contains("Wien"))
        assertFalse(ask(m, "Wo wohnte ich 2020?").text.contains("Graz"))
        assertTrue(ask(m, "Wo habe ich ursprünglich gewohnt?").text.contains("Wien"))
        val year = ask(m, "Wo wohnte ich 2022?").text
        assertTrue(year, year.contains("Wien") && year.contains("Graz"))
        assertEquals(TemporalAnswerKind.NO_EVIDENCE, ask(m, "Wo wohnte ich 2010?").kind)
    }

    @Test fun explicitValidityRangesAreHonoured() {
        val m = memory(fact("Ich wohnte von 2010 bis 2015 in Innsbruck.", t(2026, 1, 5)))
        assertTrue(ask(m, "Wo wohnte ich 2012?").text.contains("Innsbruck"))
        assertEquals(TemporalAnswerKind.NO_EVIDENCE, ask(m, "Wo wohnte ich 2016?").kind)
        assertEquals(TemporalAnswerKind.NO_EVIDENCE, ask(m, "Wo wohne ich?").kind)
    }

    @Test fun sinceWhenUsesTheStatedStart() {
        val a = ask(memory(fact("Ich wohne seit März 2019 in Linz.", t(2020, 2, 2))), "Seit wann wohne ich in Linz?")
        assertTrue(a.text, a.text.contains("März 2019"))
    }

    @Test fun aCorrectionRetractsTheWrongValueButKeepsWhatWasBelievedThen() {
        val siemens = fact("Ich arbeite bei Siemens.", t(2020, 1, 10))
        val fix = fact("Korrektur: Ich arbeite nicht bei Siemens, sondern bei Infineon.", t(2021, 2, 1))
        val m = memory(siemens, fix)
        val current = ask(m, "Wo arbeite ich?").text
        assertTrue(current, current.contains("Infineon"))
        assertFalse(current, current.substringBefore("Beleg").contains("Siemens"))
        assertTrue(ask(m, "Wo arbeitete ich 2020?").text.contains("Infineon"))
        // Transaction time: before the correction the system believed Siemens.
        val before = m.valueAt("ich", "arbeit", t(2020, 6, 1), knownAtMillis = t(2020, 12, 1))
        assertEquals("Siemens", (before as TemporalResolution.Found).values.single().value)
    }

    @Test fun anUnmarkedContradictionAsksInsteadOfSilentlyOverwriting() {
        val a = ask(memory(fact("Ich wohne in Wien.", t(2023)), fact("Ich wohne in Graz.", t(2024))), "Wo wohne ich?")
        assertEquals(TemporalAnswerKind.CLARIFY, a.kind)
        assertTrue(a.text, a.text.contains("Wien") && a.text.contains("Graz"))
    }

    @Test fun aRevokedFactIsGoneNowButVisibleForEarlierKnowledge() {
        val revoked = fact("Ich wohne in Salzburg.", t(2019), LearningStatus.REVOKED, updatedAt = t(2025))
        val m = memory(revoked)
        assertEquals(TemporalAnswerKind.NO_EVIDENCE, ask(m, "Wo wohne ich?").kind)
        assertTrue(m.valueAt("ich", "wohnort", t(2020), knownAtMillis = t(2024)) is TemporalResolution.Found)
        assertEquals(TemporalResolution.NoEvidence, m.valueAt("ich", "wohnort", t(2020), knownAtMillis = now))
    }

    @Test fun aNegationEndsAValueWithoutInventingANewOne() {
        val m = memory(wien, fact("Ich wohne nicht mehr in Wien.", t(2024, 4, 1)))
        assertEquals(TemporalAnswerKind.NO_EVIDENCE, ask(m, "Wo wohne ich?").kind)
        assertTrue(ask(m, "Wo wohnte ich 2020?").text.contains("Wien"))
    }

    @Test fun hypotheticalStatementsNeverAnswerAsFacts() {
        val held = fact("Vielleicht ziehe ich nach Linz.", t(2025), LearningStatus.OBSERVED,
            annotation = "unbestaetigt:UNCERTAIN")
        val only = ask(memory(held), "Wo wohne ich?")
        assertEquals(TemporalAnswerKind.UNCONFIRMED_ONLY, only.kind)
        assertTrue(only.text, only.text.contains("nicht bestätigt"))
        assertTrue(only.citedIds.isEmpty())
        // Even a legacy CONFIRMED record of a conditional sentence never supplies a value.
        val legacy = fact("Wenn ich nach Berlin ziehe, wohne ich in Kreuzberg.", t(2025, 2))
        val mixed = ask(memory(wien, legacy), "Wo wohne ich?")
        assertEquals(TemporalAnswerKind.ANSWER, mixed.kind)
        assertTrue(mixed.text.contains("Wien"))
        assertFalse(mixed.text, mixed.text.contains("Kreuzberg") || mixed.text.contains("Berlin"))
    }

    @Test fun workingMemoryIsSessionScopedLabelledAndGated() {
        val turns = listOf(WorkingTurn("Ich wohne jetzt in Bregenz.", now - 60_000))
        val a = ask(memory(wien, working = turns), "Wo wohne ich?")
        assertTrue(a.text, a.text.contains("Bregenz") && a.text.contains("nur in diesem Gespräch"))
        assertEquals(setOf(MemoryTier.WORKING), a.tiers)
        assertTrue(ask(memory(wien), "Wo wohne ich?").text.contains("Wien"))
        val hypothetical = listOf(WorkingTurn("Angenommen, ich wohne in Rom.", now - 1_000))
        assertFalse(ask(memory(wien, working = hypothetical), "Wo wohne ich?").text.contains("Rom"))
    }

    @Test fun agesAreReportedAsOfTheStatementNotExtrapolated() {
        val a = ask(memory(fact("Meine Tochter Klara ist 29.", t(2024, 5, 1))), "Wie alt ist Klara?")
        assertTrue(a.text, a.text.contains("29") && a.text.contains("01.05.2024"))
    }

    @Test fun namedAttributesAndOtherPeopleAreSeparate() {
        val m = memory(fact("Mein Hund heißt Bello.", t(2021)), fact("Anna wohnt in Graz.", t(2022)))
        assertTrue(ask(m, "Wie heißt mein Hund?").text.contains("Bello"))
        assertTrue(ask(m, "Wo wohnt Anna?").text.contains("Graz"))
        assertEquals(TemporalAnswerKind.NO_EVIDENCE, ask(m, "Wo wohne ich?").kind)
    }

    @Test fun questionsOutsideTheBoundedGrammarAreNotClaimed() {
        val m = memory(wien)
        assertNull(m.answer("Erzähl mir einen Witz.", now))
        assertNull(m.answer("Was ist ein Schwarzes Loch?", now))
    }
}
