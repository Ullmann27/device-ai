package at.heinz.deviceai.ui

import org.junit.Assert.*
import org.junit.Test

class Command03BridgeContractTest {
    @Test fun multilineOwnerTextUsesTheExistingCompleteContextLimit() {
        assertNull(Command03BridgeContract.commandFailure("Erste Zeile\nZweite Zeile\r\n\tListe"))
        assertNull(Command03BridgeContract.commandFailure("a".repeat(11_500)))
        assertEquals("too-long", Command03BridgeContract.commandFailure("a".repeat(11_501)))
    }

    @Test fun utf8ByteLimitIsIndependentOfCharacterCount() {
        assertNull(Command03BridgeContract.commandFailure("ä".repeat(5_750)))
        assertEquals("too-long", Command03BridgeContract.commandFailure("ä".repeat(5_751)))
        assertEquals("too-long", Command03BridgeContract.commandFailure("😀".repeat(2_876)))
    }

    @Test fun unsupportedControlsAreRejectedWithoutTreatingParagraphsAsInvalid() {
        for (control in listOf('\u0000', '\u0007', '\u001b', '\u007f', '\u0085')) {
            assertEquals("invalid-input", Command03BridgeContract.commandFailure("Text${control}Text"))
        }
        assertEquals("invalid-input", Command03BridgeContract.commandFailure(" \r\n\t"))
        assertNull(Command03BridgeContract.commandFailure("Text\tText\nAbsatz"))
    }

    @Test fun unsuccessfulLongSubmissionStillHasAnEditableDraft() {
        val draft = "a".repeat(20_000)
        assertTrue(Command03BridgeContract.validDraft(draft))
        assertEquals("too-long", Command03BridgeContract.commandFailure(draft))
        assertTrue(Command03BridgeContract.validDraft(""))
        assertFalse(Command03BridgeContract.validDraft("ä".repeat(131_073)))
    }

    @Test fun completeStoredResponsesAreNotCutAtTheOldPreviewLimit() {
        val response = "Lange Antwort\n" + "x".repeat(32_000) + " Ende"
        assertEquals(response, Command03BridgeContract.messageText(response))
        val boundary = "x".repeat(Command03BridgeContract.MAX_MESSAGE_CHARACTERS - 1) + "😀"
        val shown = Command03BridgeContract.messageText(boundary)
        assertEquals(Command03BridgeContract.MAX_MESSAGE_CHARACTERS - 1, shown.length)
        assertFalse(shown.last().isHighSurrogate())
    }

    @Test fun requestAndSessionIdentitiesHaveUnambiguousShapes() {
        assertTrue(Command03BridgeContract.validSession("11111111-1111-4111-8111-111111111111"))
        assertFalse(Command03BridgeContract.validSession("-".repeat(36)))
        assertFalse(Command03BridgeContract.validSession("11111111-1111-4111-8111-111111111111/other"))
        assertTrue(Command03BridgeContract.validRequest("request-1_page2"))
        assertFalse(Command03BridgeContract.validRequest(""))
        assertFalse(Command03BridgeContract.validRequest("x".repeat(65)))
    }
}
