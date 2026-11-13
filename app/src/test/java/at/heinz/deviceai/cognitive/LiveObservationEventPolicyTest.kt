package at.heinz.deviceai.cognitive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveObservationEventPolicyTest {
    private val own = "at.heinz.deviceai.continuumlocal"

    @Test fun ownLabelUpdateDoesNotInvalidateForeignTarget() {
        assertTrue(LiveObservationEventPolicy.ignoreOwnContent(own, own, null))
        assertTrue(LiveObservationEventPolicy.ignoreOwnContent(own, own, "com.sec.android.app.myfiles"))
    }

    @Test fun foreignUnknownOrSelfReadEventsStillCount() {
        assertFalse(LiveObservationEventPolicy.ignoreOwnContent("com.sec.android.app.myfiles", own, null))
        assertFalse("missing sender stays uncertainty", LiveObservationEventPolicy.ignoreOwnContent(null, own, null))
        assertFalse("a read of Continuum itself must see its own changes",
            LiveObservationEventPolicy.ignoreOwnContent(own, own, own))
        assertFalse("unknown own package never exempts", LiveObservationEventPolicy.ignoreOwnContent(own, "", null))
        assertFalse("prefix is not identity", LiveObservationEventPolicy.ignoreOwnContent("$own.evil", own, null))
    }
}
