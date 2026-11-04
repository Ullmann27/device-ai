package at.heinz.deviceai.jarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class JarvisSessionHandoffTest {
    @Test fun sameBrainSessionSurvivesFourAuthorizedEndpointTransitions() {
        val handoff = JarvisSessionHandoff("owner-existing-session", JarvisActiveEndpoint.HOME_AMBIENT)
        val approved = JarvisEndpointHandoffGate { sessionId, _, _ -> sessionId == "owner-existing-session" }
        for (endpoint in listOf(JarvisActiveEndpoint.FOLD7_MOBILE_DIRECT,
            JarvisActiveEndpoint.VEHICLE_HANDSFREE_AUDIO, JarvisActiveEndpoint.PC_WORKSTATION,
            JarvisActiveEndpoint.HOME_AMBIENT)) {
            assertNotNull(handoff.request(endpoint, approved))
            assertEquals("owner-existing-session", handoff.current().existingSessionId)
        }
        assertEquals(4L, handoff.current().revision)
    }

    @Test fun rejectedOrExceptionalHandoffRetainsOriginalRouteAndRevision() {
        val handoff = JarvisSessionHandoff("single-session", JarvisActiveEndpoint.FOLD7_MOBILE_DIRECT)
        assertNull(handoff.request(JarvisActiveEndpoint.VEHICLE_HANDSFREE_AUDIO,
            JarvisEndpointHandoffGate { _, _, _ -> false }))
        assertNull(handoff.request(JarvisActiveEndpoint.PC_WORKSTATION,
            JarvisEndpointHandoffGate { _, _, _ -> error("not a trusted endpoint") }))
        assertEquals(JarvisActiveEndpoint.FOLD7_MOBILE_DIRECT, handoff.current().endpoint)
        assertEquals(0L, handoff.current().revision)
    }
}
