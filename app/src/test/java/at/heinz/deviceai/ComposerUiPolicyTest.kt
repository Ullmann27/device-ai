package at.heinz.deviceai

import org.junit.Assert.*
import org.junit.Test

class ComposerUiPolicyTest {
    @Test fun unavailableModelAndBackendKeepDraft() {
        assertEquals(DraftSubmission.NEEDS_LOCAL_MODEL, ComposerUiPolicy.submission(AssistantRoute.MODEL_SETUP_REQUIRED, false, false, false))
        assertEquals(DraftSubmission.NEEDS_LOCAL_MODEL, ComposerUiPolicy.submission(AssistantRoute.LOCAL_MODEL, false, true, false))
        assertEquals(DraftSubmission.NEEDS_CLOUD_SETUP, ComposerUiPolicy.submission(AssistantRoute.CLOUD, false, false, false))
    }

    @Test fun allowedRoutesPermitSend() {
        assertEquals(DraftSubmission.READY, ComposerUiPolicy.submission(AssistantRoute.LOCAL_MODEL, true, false, false))
        assertEquals(DraftSubmission.READY, ComposerUiPolicy.submission(AssistantRoute.CLOUD, false, true, false))
        assertEquals(DraftSubmission.READY, ComposerUiPolicy.submission(AssistantRoute.MODEL_SETUP_REQUIRED, false, false, true))
    }

    @Test fun narrowKeyboardInsetsProtectComposer() {
        val v = ComposerUiPolicy.viewport(65, 44, 812, true, false)
        assertEquals(65, v.top)
        assertEquals(812, v.bottom)
        assertTrue(v.compactDashboard)
    }

    @Test fun keyboardDismissalRestoresDashboard() {
        assertEquals(44, ComposerUiPolicy.viewport(60, 44, 0, false, false).bottom)
        assertFalse(ComposerUiPolicy.viewport(60, 44, 0, false, false).compactDashboard)
        assertFalse(ComposerUiPolicy.viewport(60, 44, 812, true, true).compactDashboard)
        assertFalse(ComposerUiPolicy.viewport(60, 44, 44, true, false).compactDashboard)
    }
}
