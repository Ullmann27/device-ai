package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LiveSupportBoundaryTest {
    private fun stamp(revision: Long, window: Int? = 42) =
        ObservationStamp("review-task", 3, revision, "com.whatsapp", window,
            "screen-$revision", revision * 1_000_000)

    @Test fun anotherWindowCannotConfirmSameTaskAction() {
        for (window in listOf(43, null)) {
            assertEquals(EffectVerdict.UNCLEAR_CONTEXT_CHANGED,
                ActionEffectVerifier.verify(stamp(10), true, stamp(11, window), true))
        }
    }

    @Test fun uncertaintyOverflowDoesNotForgetFirstAction() {
        val journal = LiveActionJournal("review-task", capacity = 2)
        for (i in 1..3) {
            val before = stamp(i.toLong())
            assertEquals(LiveActionJournal.Admission.ALLOWED, journal.admit("action-$i", before))
            journal.finish("action-$i", before, EffectVerdict.UNCLEAR_NO_NEW_OBSERVATION, null)
        }
        assertEquals(LiveActionJournal.Admission.BLOCKED_AFTER_UNCLEAR, journal.admit("action-1", stamp(10)))
        assertEquals(LiveActionJournal.Admission.BLOCKED_AFTER_UNCLEAR, journal.admit("action-new", stamp(11)))
    }

    @Test fun boundedConfirmedHistoryStillAllowsFreshWork() {
        val journal = LiveActionJournal("review-task", capacity = 2)
        for (i in 1..10) {
            val before = stamp(i.toLong() * 2)
            assertEquals(LiveActionJournal.Admission.ALLOWED, journal.admit("scroll", before))
            journal.finish("scroll", before, EffectVerdict.CONFIRMED, stamp(i.toLong() * 2 + 1))
        }
        assertEquals(LiveActionJournal.Admission.ALLOWED, journal.admit("scroll", stamp(30)))
    }
}
