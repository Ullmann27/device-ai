package at.heinz.deviceai.ui

import org.junit.Assert.*
import org.junit.Test

class ToolWorkSceneLeaseTest {
    private val key = ToolWorkSceneLease.Key("session", "scope", 1L, "call-1")

    @Test fun stopBeforePostedStartCannotReopenWindow() {
        val scene = ToolWorkSceneLease()
        assertFalse(scene.offer(key, "session", null, 2, true, 100))
        assertNull(scene.active)
    }

    @Test fun oldSessionGenerationAndBackgroundAreRejected() {
        val scene = ToolWorkSceneLease()
        assertFalse(scene.offer(key, "other", "scope", 1, true, 100))
        assertFalse(scene.offer(key, "session", "new-scope", 1, true, 100))
        assertFalse(scene.offer(key, "session", "scope", 2, true, 100))
        assertFalse(scene.offer(key, "session", "scope", 1, false, 100))
    }

    @Test fun delayedOldCallCannotReplaceOrFinishCurrentCall() {
        val scene = ToolWorkSceneLease()
        assertTrue(scene.offer(key, "session", "scope", 1, true, 100))
        val next = key.copy(callId = "call-2")
        assertTrue(scene.offer(next, "session", "scope", 1, true, 200))
        assertFalse(scene.ended(key))
        assertFalse(scene.offer(key, "session", "scope", 1, true, 100))
        assertEquals(next, scene.active)
        assertTrue(scene.running)
    }

    @Test fun completionAndDismissalNeverReopenSameCall() {
        val scene = ToolWorkSceneLease()
        assertTrue(scene.offer(key, "session", "scope", 1, true, 100))
        assertTrue(scene.ended(key))
        assertFalse(scene.running)
        scene.closed(key)
        assertFalse(scene.offer(key, "session", "scope", 1, true, 300))
    }

    @Test fun newScopeStartsAfterPriorScopeWasClosed() {
        val scene = ToolWorkSceneLease()
        scene.offer(key, "session", "scope", 1, true, 100)
        scene.clear()
        val next = key.copy(scope = "next", generation = 2)
        assertTrue(scene.offer(next, "session", "next", 2, true, 200))
        assertFalse(scene.current(key, "session", "next", 2, true))
    }
}
