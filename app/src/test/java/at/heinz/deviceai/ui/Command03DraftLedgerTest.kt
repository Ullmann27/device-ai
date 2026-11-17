package at.heinz.deviceai.ui

import org.junit.Assert.*
import org.junit.Test

class Command03DraftLedgerTest {
    private val scope = Command03Binding("11111111-1111-4111-8111-111111111111",
        "22222222-2222-4222-8222-222222222222", 4, "33333333-3333-4333-8333-333333333333")
    private fun edit(revision: Long, text: String = "Entwurf", start: Int = text.length, end: Int = text.length,
        binding: Command03Binding = scope) = Command03Draft(binding, text, start, end, revision)
    private fun ledger() = Command03DraftLedger().apply { bind(scope) }

    @Test fun laterAtomicTextAndSelectionAreAcceptedButOlderEditsNeverRollThemBack() {
        val ledger = ledger()
        assertTrue(ledger.accept(edit(3, "Neu", 1, 2), "Alt", 3, 3))
        assertEquals(3, ledger.acknowledged)
        assertFalse(ledger.accept(edit(2, "Alt"), "Neu", 1, 2))
        assertEquals(3, ledger.acknowledged)
    }

    @Test fun equalRevisionIsAnExactAtomicReplayIncludingBothSelectionEnds() {
        val ledger = ledger()
        assertTrue(ledger.accept(edit(1, "Text", 1, 3), "", 0, 0))
        assertTrue(ledger.accept(edit(1, "Text", 1, 3), "Text", 1, 3))
        assertFalse(ledger.accept(edit(1, "Text", 0, 3), "Text", 1, 3))
        assertFalse(ledger.accept(edit(1, "Text", 1, 4), "Text", 1, 3))
        assertFalse(ledger.accept(edit(1, "Neu!", 1, 3), "Text", 1, 3))
    }

    @Test fun nativeDictationOrClearDoesNotRequireOrForgeAnotherClientRevision() {
        val ledger = ledger()
        assertTrue(ledger.accept(edit(7, "Vorher"), "", 0, 0))
        assertFalse(ledger.accept(edit(7, "Vorher"), "Vorher diktiert", 14, 14))
        // The native dictation already placed the cursor at 14/14.
        assertTrue(ledger.accept(edit(7, "Vorher diktiert", 14, 14), "Vorher diktiert", 14, 14))
        assertTrue(ledger.accept(edit(7, ""), "", 0, 0))
        assertEquals(7, ledger.acknowledged)
    }

    @Test fun everyPageAndViewComponentFencesOldActionsEvenInTheSameSession() {
        val ledger = ledger()
        for (other in listOf(scope.copy(pageId = "44444444-4444-4444-8444-444444444444"),
            scope.copy(viewId = "55555555-5555-4555-8555-555555555555"), scope.copy(viewRevision = 5),
            scope.copy(sessionId = "66666666-6666-4666-8666-666666666666"))) {
            assertFalse(ledger.accept(edit(1, binding = other), "", 0, 0))
        }
        assertEquals(0, ledger.acknowledged)
    }

    @Test fun aNewViewStartsAtZeroAndReturningToTheOldBotDoesNotReviveItsReceipts() {
        val ledger = ledger()
        assertTrue(ledger.accept(edit(8), "", 0, 0))
        val returned = scope.copy(viewId = "77777777-7777-4777-8777-777777777777", viewRevision = 6)
        ledger.bind(returned)
        assertEquals(0, ledger.acknowledged)
        assertFalse(ledger.accept(edit(9), "", 0, 0))
        assertTrue(ledger.accept(edit(0, "", binding = returned), "", 0, 0))
        assertFalse(ledger.accept(edit(0, "Alte Seite", binding = returned), "", 0, 0))
    }

    @Test fun malformedUnsafeAndOversizeEditsCannotAdvanceTheAcknowledgement() {
        val ledger = ledger()
        for (bad in listOf(edit(-1), edit(Command03BridgeContract.MAX_SAFE_REVISION + 1),
            edit(1, "A", -1, 1), edit(1, "A", 1, 0), edit(1, "A", 0, 2),
            edit(1, "ä".repeat(131_073)), edit(1, binding = scope.copy(pageId = "-".repeat(36))))) {
            assertFalse(ledger.accept(bad, "", 0, 0))
            assertEquals(0, ledger.acknowledged)
        }
    }
}
