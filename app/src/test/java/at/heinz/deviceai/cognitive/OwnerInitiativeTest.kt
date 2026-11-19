package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.OwnerInitiativeCommand.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one policy for everything the app says without being asked: three levels, caps per hour and day, the owner's
 * no remembered per kind and per thing, never while something runs – and the spoken switch for the level.
 */
class OwnerInitiativeTest {
    private var now = 1_763_542_800_000L
    private val store = InMemoryOwnerInitiativeStore()
    private val initiative = OwnerInitiative(store) { now }
    private fun minutes(count: Long) { now += count * 60_000L }

    @Test fun theLevelDecidesWhichKindsMayBeSuggestedAtAll() {
        assertEquals(OwnerInitiativeLevel.NORMAL, initiative.level)
        assertTrue(initiative.allows(OwnerSuggestionKind.RESUME_WORK, "e1"))
        assertTrue(initiative.allows(OwnerSuggestionKind.PDF_REPORT))
        assertFalse("context suggestions need „Aktiv“", initiative.allows(OwnerSuggestionKind.APPOINTMENT, "a1"))
        assertFalse(initiative.allows(OwnerSuggestionKind.OPEN_MAPS, "m1"))
        initiative.setLevel(OwnerInitiativeLevel.ACTIVE)
        OwnerSuggestionKind.entries.forEach { assertTrue(it.name, initiative.allows(it, "x")) }
        initiative.setLevel(OwnerInitiativeLevel.RESERVED)
        OwnerSuggestionKind.entries.forEach { assertFalse(it.name, initiative.allows(it, "x")) }
    }

    @Test fun neverWhileSomethingRuns() {
        initiative.setLevel(OwnerInitiativeLevel.ACTIVE)
        OwnerSuggestionKind.entries.forEach { assertFalse(it.name, initiative.allows(it, "x", running = true)) }
    }

    @Test fun aDeclinedKindRestsAndTheDeclinedThingRestsMuchLonger() {
        initiative.offered(OwnerSuggestionKind.RESUME_WORK, "e1")
        initiative.declined(OwnerSuggestionKind.RESUME_WORK, "e1")
        assertTrue(initiative.resting(OwnerSuggestionKind.RESUME_WORK))
        assertFalse(initiative.allows(OwnerSuggestionKind.RESUME_WORK, "e2"))
        assertTrue("another kind is not touched", initiative.allows(OwnerSuggestionKind.OPEN_MESSAGES, "m"))
        minutes(25 * 60)
        assertTrue("the kind is back after a day", initiative.allows(OwnerSuggestionKind.RESUME_WORK, "e2"))
        assertFalse("the very thing he declined is not", initiative.allows(OwnerSuggestionKind.RESUME_WORK, "e1"))
        minutes(31L * 24 * 60)
        assertTrue(initiative.allows(OwnerSuggestionKind.RESUME_WORK, "e1"))
    }

    @Test fun aNoToAQuestionHeAskedForOnlyRestsTheThingItself() {
        initiative.declined(OwnerSuggestionKind.RESUME_WORK, "e1", restKind = false)
        assertFalse(initiative.resting(OwnerSuggestionKind.RESUME_WORK))
        assertTrue(initiative.allows(OwnerSuggestionKind.RESUME_WORK, "e2"))
        assertFalse(initiative.allows(OwnerSuggestionKind.RESUME_WORK, "e1"))
    }

    @Test fun theSameThingIsNotOfferedAtEveryOpeningAndAtMostTwice() {
        initiative.offered(OwnerSuggestionKind.RESUME_WORK, "e1")
        minutes(120)
        assertFalse("not again two hours later", initiative.allows(OwnerSuggestionKind.RESUME_WORK, "e1"))
        assertTrue(initiative.allows(OwnerSuggestionKind.RESUME_WORK, "e2"))
        minutes(20 * 60)
        assertTrue("once more the next day", initiative.allows(OwnerSuggestionKind.RESUME_WORK, "e1"))
        initiative.offered(OwnerSuggestionKind.RESUME_WORK, "e1")
        minutes(48 * 60)
        assertFalse("ignored twice: that is an answer too", initiative.allows(OwnerSuggestionKind.RESUME_WORK, "e1"))
        // Accepted offers do not count against it.
        initiative.offered(OwnerSuggestionKind.RESUME_WORK, "e3")
        initiative.accepted(OwnerSuggestionKind.RESUME_WORK, "e3")
        minutes(21 * 60)
        initiative.offered(OwnerSuggestionKind.RESUME_WORK, "e3")
        initiative.accepted(OwnerSuggestionKind.RESUME_WORK, "e3")
        minutes(21 * 60)
        assertTrue(initiative.allows(OwnerSuggestionKind.RESUME_WORK, "e3"))
    }

    @Test fun thereIsACapPerHourAndPerDay() {
        // Normal: two unsolicited offers an hour, six a day.
        initiative.offered(OwnerSuggestionKind.OPEN_MESSAGES, "a")
        initiative.offered(OwnerSuggestionKind.BRIEFING)
        assertFalse(initiative.allows(OwnerSuggestionKind.RESUME_WORK, "e1"))
        assertTrue("a follow-up to a result he asked for has its own cap", initiative.allows(OwnerSuggestionKind.PDF_REPORT))
        minutes(61)
        assertTrue(initiative.allows(OwnerSuggestionKind.RESUME_WORK, "e1"))
        repeat(4) { index -> initiative.offered(OwnerSuggestionKind.OPEN_MESSAGES, "b$index"); minutes(61) }
        assertFalse("six in a day are enough", initiative.allows(OwnerSuggestionKind.RESUME_WORK, "e1"))
        minutes(24 * 60)
        assertTrue(initiative.allows(OwnerSuggestionKind.RESUME_WORK, "e1"))
        // Follow-ups: four an hour.
        repeat(4) { initiative.offered(OwnerSuggestionKind.PDF_REPORT) }
        assertFalse(initiative.allows(OwnerSuggestionKind.PDF_REPORT))
        assertFalse(initiative.allows(OwnerSuggestionKind.NEXT_STEPS))
    }

    @Test fun theMemoryIsBoundedSurvivesAReloadAndCanBeForgotten() {
        repeat(200) { index -> initiative.offered(OwnerSuggestionKind.OPEN_MESSAGES, "m$index"); minutes(1) }
        assertTrue(store.memory!!.length < 6_000)
        initiative.declined(OwnerSuggestionKind.BRIEFING)
        assertTrue(OwnerInitiative(store) { now }.resting(OwnerSuggestionKind.BRIEFING))
        initiative.forget()
        assertNull(store.memory)
        assertFalse(initiative.resting(OwnerSuggestionKind.BRIEFING))
        store.memory = "{broken"
        assertTrue("a broken memory is an empty one", initiative.allows(OwnerSuggestionKind.BRIEFING))
    }

    // ── the spoken switch ─────────────────────────────────────────────────────────────────────────────────

    @Test fun theOwnersSentencesSetTheLevel() {
        fun parse(text: String) = OwnerInitiativeCommand.parse(text, "Jarvis")
        assertEquals(Command.Step(up = false), parse("Sei zurückhaltender"))
        assertEquals(Command.Step(up = true), parse("Du darfst mehr vorschlagen"))
        assertEquals(Command.Set(OwnerInitiativeLevel.RESERVED), parse("Schlag mir nichts mehr vor"))
        assertEquals(Command.Set(OwnerInitiativeLevel.RESERVED), parse("Jarvis, sei bitte zurückhaltend"))
        assertEquals(Command.Set(OwnerInitiativeLevel.RESERVED), parse("schlag mir nix mehr vor"))
        assertEquals(Command.Set(OwnerInitiativeLevel.ACTIVE), parse("Initiative auf aktiv"))
        assertEquals(Command.Set(OwnerInitiativeLevel.NORMAL), parse("Du kannst mir wieder etwas vorschlagen"))
        assertEquals(Command.Step(up = false), parse("Weniger Vorschläge bitte"))
        assertEquals(Command.Step(up = true), parse("Schlag ruhig mehr vor"))
        assertEquals(OwnerInitiativeLevel.RESERVED, OwnerInitiativeCommand.target(Command.Step(up = false), OwnerInitiativeLevel.NORMAL))
        assertEquals(OwnerInitiativeLevel.RESERVED, OwnerInitiativeCommand.target(Command.Step(up = false), OwnerInitiativeLevel.RESERVED))
        assertEquals(OwnerInitiativeLevel.ACTIVE, OwnerInitiativeCommand.target(Command.Step(up = true), OwnerInitiativeLevel.ACTIVE))
        assertEquals(OwnerInitiativeLevel.NORMAL, OwnerInitiativeCommand.target(Command.Step(up = true), OwnerInitiativeLevel.RESERVED))
    }

    @Test fun anyOtherSentenceKeepsItsRoute() {
        for (other in listOf("Schlag mir ein Restaurant vor", "Was schlägst du vor?", "Sei leise", "Mach mir einen Vorschlag",
            "Schlag mir nichts mehr vor, sondern öffne das PDF", "Kannst du mehr vorschlagen?", "Vorschläge für das Wochenende", "Halt"))
            assertNull(other, OwnerInitiativeCommand.parse(other, "Jarvis"))
    }

    @Test fun theReplySaysWhatTheLevelMeansAndThatNothingStartsByItself() {
        assertTrue(OwnerInitiativeCommand.reply(OwnerInitiativeLevel.RESERVED, changed = true).contains("keine Angebote und keine Vorschläge"))
        assertTrue(OwnerInitiativeCommand.reply(OwnerInitiativeLevel.ACTIVE, changed = true).contains("Ich frage immer erst und mache nichts von selbst"))
        assertTrue(OwnerInitiativeCommand.reply(OwnerInitiativeLevel.NORMAL, changed = false).startsWith("Das ist schon so eingestellt"))
        OwnerInitiativeLevel.entries.forEach { level -> listOf(true, false).forEach { changed ->
            assertFalse(OwnerInitiativeCommand.reply(level, changed).contains("?"))
        } }
    }
}
