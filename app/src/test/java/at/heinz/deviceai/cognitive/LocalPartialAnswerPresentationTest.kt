package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Code63: an answer cut at the output limit starts with the answer and ends with the way to continue. */
class LocalPartialAnswerPresentationTest {
    /** The exact blocker text BoundedAgentOrchestrator returns for an accepted partial answer. */
    private val limit = "Antwort unvollständig: " + LocalGenerationFailureCause.OUTPUT_LIMIT.ownerMessage

    @Test fun anOutputLimitedAnswerIsShownFirstAndOffersTheContinuation() {
        val shown = LocalPartialAnswerPresentation.present("Das Dokument behandelt drei Bereiche: erstens die Frist und zw",
            limit, continuationAvailable = true)
        assertTrue(shown.startsWith("Das Dokument behandelt drei Bereiche: erstens die Frist und zw …"))
        assertTrue(shown.endsWith(LocalPartialAnswerPresentation.CONTINUE_HINT))
        assertTrue(shown.contains("„weiter“"))
        // The technical blocker no longer precedes (and is no longer spoken before) the answer.
        assertFalse(shown.startsWith(limit))
    }

    @Test fun theGeneratedWordsAreNeverChangedOrCompleted() {
        val response = "Schritt eins ist erledigt. Schritt zwei beginnt mit"
        val shown = LocalPartialAnswerPresentation.present(response, limit, continuationAvailable = true)
        assertTrue(shown.startsWith("$response …\n\n"))
        val closed = LocalPartialAnswerPresentation.present("Schritt eins ist erledigt.", limit, continuationAvailable = true)
        assertTrue(closed.startsWith("Schritt eins ist erledigt.\n\n"))
        assertFalse(closed.contains("erledigt. …"))
    }

    @Test fun withoutARecordedContinuationNothingIsPromised() {
        val response = "Teilantwort ohne Sitzung"
        assertEquals("$limit\n\n$response",
            LocalPartialAnswerPresentation.present(response, limit, continuationAvailable = false))
    }

    @Test fun otherInterruptionsKeepTheirOwnReasonInFront() {
        val stalled = "Antwort unvollständig: " + LocalGenerationFailureCause.STALLED.ownerMessage
        assertEquals("$stalled\n\nTeil",
            LocalPartialAnswerPresentation.present("Teil", stalled, continuationAvailable = true))
        assertEquals("Antwort unvollständig.\n\nTeil",
            LocalPartialAnswerPresentation.present("Teil", null, continuationAvailable = true))
        val thermal = "Antwort unvollständig: " + LocalGenerationFailureCause.THERMAL_STOPPED.ownerMessage
        // A withheld partial text never promises a continuation either.
        val withheld = "Unvollständige Antwort zurückgehalten: " + LocalGenerationFailureCause.OUTPUT_LIMIT.ownerMessage +
            " Der Teiltext hat die lokale Qualitätsprüfung nicht bestanden."
        assertTrue(LocalPartialAnswerPresentation.present("Teil", withheld, true).startsWith(withheld))
        assertTrue(LocalPartialAnswerPresentation.present("Teil", thermal, true).startsWith(thermal))
    }
}
