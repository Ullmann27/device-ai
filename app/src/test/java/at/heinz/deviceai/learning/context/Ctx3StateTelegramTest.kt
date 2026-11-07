package at.heinz.deviceai.learning.context

import at.heinz.deviceai.learning.ContinuumTemporalMemory
import at.heinz.deviceai.learning.LearningExperience
import at.heinz.deviceai.learning.LearningExperienceFactory
import at.heinz.deviceai.learning.LearningExperienceType
import at.heinz.deviceai.learning.LearningProvenance
import at.heinz.deviceai.learning.LearningStatus
import at.heinz.deviceai.learning.LearningTrustClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ctx3StateTelegramTest {
    private fun fact(text: String, at: Long): LearningExperience =
        LearningExperienceFactory.create(
            LearningExperienceType.USER_FACT,
            LearningProvenance.USER_CONFIRMED,
            LearningTrustClass.OWNER_CONFIRMED,
            LearningStatus.CONFIRMED,
            text,
            now = at
        )

    @Test fun oldRetrievedSourceCannotResurrectSupersededCurrentValue() {
        val old = fact("Ich wohne in Wien.", 1_000L)
        val correction = fact("Korrektur: Ich wohne in Graz.", 2_000L)
        val memory = ContinuumTemporalMemory.fromLedger(listOf(old, correction))

        val telegram = Ctx3StateTelegram.compile(
            memory = memory,
            selectedSourceIds = setOf(old.id),
            nowMillis = 3_000L
        )

        assertTrue(telegram.text.contains("S=ich|P=wohnort|V=Graz"))
        assertFalse(telegram.text.contains("|V=Wien"))
        assertEquals(listOf(correction.id), telegram.citedSourceIds)
        assertEquals(0, telegram.conflictCount)
        assertEquals(1, telegram.keyCount)
        assertTrue(telegram.telegramBytes > 0)
        assertTrue(telegram.sha256.matches(Regex("[a-f0-9]{64}")))
    }

    @Test fun unmarkedContradictionIsPreservedInsteadOfSilentlyChoosingNewest() {
        val first = fact("Ich wohne in Wien.", 1_000L)
        val second = fact("Ich wohne in Graz.", 2_000L)
        val memory = ContinuumTemporalMemory.fromLedger(listOf(first, second))

        val telegram = Ctx3StateTelegram.compile(
            memory = memory,
            selectedSourceIds = setOf(second.id),
            nowMillis = 3_000L
        )

        assertEquals(1, telegram.conflictCount)
        assertTrue(telegram.text.contains("|CONFLICT="))
        assertTrue(telegram.citedSourceIds.containsAll(listOf(first.id, second.id)))
    }

    @Test fun emptySelectionProducesNoStateAndNoAccidentalGlobalDump() {
        val memory = ContinuumTemporalMemory.fromLedger(listOf(fact("Ich wohne in Wien.", 1_000L)))
        val telegram = Ctx3StateTelegram.compile(memory, emptySet(), 2_000L)

        assertEquals("", telegram.text)
        assertEquals(0, telegram.telegramBytes)
        assertEquals(0, telegram.keyCount)
        assertTrue(telegram.citedSourceIds.isEmpty())
    }
}
