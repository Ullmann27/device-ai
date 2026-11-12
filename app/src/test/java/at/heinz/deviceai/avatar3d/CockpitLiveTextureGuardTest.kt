package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.cognitive.*
import org.junit.Assert.*
import org.junit.Test

class CockpitLiveTextureGuardTest {
    @Test fun olderGpuImageCannotBecomeLiveForAnotherFrameOrSession() {
        val binding = LiveFrameBinding("task-one", 7L, "com.example.target", "at.heinz.deviceai",
            LiveFrameOrigin.PROJECTION_WINDOW_CROP)
        val current = LiveMonitorStatus(LiveMonitorState.LIVE, null, binding, null, 42L, 2L)
        assertTrue(CockpitLiveTexture.matches(current, binding, 42L))
        assertFalse(CockpitLiveTexture.matches(current, binding, 41L))
        assertFalse(CockpitLiveTexture.matches(current, binding.copy(captureSession = 8L), 42L))
        assertFalse(CockpitLiveTexture.matches(current, binding.copy(taskId = "other-task"), 42L))
        assertFalse(CockpitLiveTexture.matches(current, null, 42L))
        for (state in LiveMonitorState.entries.filter { it != LiveMonitorState.LIVE }) {
            assertFalse(CockpitLiveTexture.matches(current.copy(state = state), binding, 42L))
        }
    }
}
