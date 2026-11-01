package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class PersonalLearningPreviewCopyTest {
    @Test fun ownerObserved254ReviewOnlyRecordsAreNotReportedAsLostOrRestored() {
        val message = PersonalLearningPreviewCopy.explainReviewOnly(254)
        assertTrue(message.contains("in der Sicherung vorhanden): 254"))
        assertTrue(message.contains("löscht sie nicht"))
        assertTrue(message.contains("importiert sie nicht"))
        assertTrue(message.contains("KEINE vollständige Wiederherstellung"))
        assertTrue(message.contains("Ein Restore wurde nicht durchgeführt"))
    }

    @Test fun emptyReviewOnlyGroupIsStillReadOnlyAndNotAFullRestore() {
        val message = PersonalLearningPreviewCopy.explainReviewOnly(0)
        assertTrue(message.contains("vorhanden): 0"))
        assertTrue(message.contains("keine Zieldaten geschrieben"))
        assertTrue(message.contains("KEINE vollständige Wiederherstellung"))
    }

    @Test fun invalidNegativeCountIsRejected() {
        try {
            PersonalLearningPreviewCopy.explainReviewOnly(-1)
            fail("Negative preview count accepted")
        } catch (_: IllegalArgumentException) { }
    }
}
