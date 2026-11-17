package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.cognitive.*
import at.heinz.deviceai.ui.ComputerPanelState
import at.heinz.deviceai.ui.ContinuumDashboardState
import at.heinz.deviceai.ui.JarvisPresentationRecordingPainter
import at.heinz.deviceai.ui.JarvisShellLayout
import org.junit.Assert.*
import org.junit.Test

class CockpitPanelUploadStateTest {
    private val frame = CockpitStageFrame(852f, 480f, 213f, 259.2f, 639f, 427.2f)

    private fun content(task: String = "task", session: Long = 7L, revision: Long = 3L,
                        font: Float = 1f, density: Float = 1f): CockpitPanelContent {
        val reducer = CockpitWorkReducer().apply { bind(task, session) }
        val stages = listOf(CockpitWorkEvent.Stage.ACCEPTED, CockpitWorkEvent.Stage.DISPATCHED,
            CockpitWorkEvent.Stage.OBSERVED)
        stages.forEachIndexed { index, stage ->
            val now = 1_000_000L + index
            assertTrue(reducer.apply(CockpitWorkEvent(task, session, index + 1L, now, 1L, stage,
                if (index > 0) "step" else null,
                if (stage == CockpitWorkEvent.Stage.DISPATCHED) CockpitWorkEvent.Action.CLICK else null,
                observationRevision = if (index == 0) 0L else if (index == 1) 2L else revision,
                frameSequence = if (index == 2) 12L else 0L), now))
        }
        val computer = checkNotNull(ComputerPanelState.from(task, session,
            OwnerComputerIntent(ComputerApp.WHATSAPP, ComputerGoal.SEARCH, "Testkontakt"),
            reducer.snapshot(1_000_003L)))
        return checkNotNull(CockpitPanelContent.from(ContinuumDashboardState(computer = computer), density, font))
    }

    @Test fun textureUploadAloneNeverHidesForeground() {
        val content = content()
        val state = CockpitPanelUploadState()
        state.request(content, frame)
        val ticket = checkNotNull(state.begin())
        assertNull(state.begin())
        state.frameSubmitted()
        assertFalse(state.presented(content, frame))
        state.complete(ticket)
        assertTrue(state.drawable)
        assertFalse(state.presented(content, frame))
        state.frameSubmitted()
        assertTrue(state.presented(content, frame))
        state.frameFailed()
        assertFalse(state.presented(content, frame))
        assertTrue(state.drawable)
        state.frameSubmitted()
        assertTrue(state.presented(content, frame))
    }

    @Test fun repeatedVisibleRowsReuseTextureAcrossObservationRevisions() {
        val content = content()
        val next = content(revision = 4)
        assertEquals(content.key, next.key)
        val state = CockpitPanelUploadState()
        state.request(content, frame)
        state.complete(checkNotNull(state.begin()))
        state.frameSubmitted()
        assertTrue(state.presented(content, frame))
        assertFalse(state.presented(next, frame))
        val request = state.requested
        state.request(next, frame)
        assertSame(request, state.requested)
        assertTrue(state.drawable)
        assertNull(state.begin())
        assertFalse(state.presented(next, frame))
        state.frameSubmitted()
        assertTrue(state.presented(next, frame))
        assertFalse(state.presented(content, frame))
    }

    @Test fun pendingChangesCoalesceAndOldCompletionCannotReplaceSuccessor() {
        val first = content("first")
        val second = content("second")
        val third = content("third")
        val state = CockpitPanelUploadState()
        state.request(first, frame)
        val old = checkNotNull(state.begin())
        state.request(second, frame)
        assertNull(state.begin())
        state.request(third, frame)
        assertNull(state.begin())
        state.complete(old)
        assertFalse(state.drawable)
        val newest = checkNotNull(state.begin())
        assertEquals("third", newest.request.key.taskId)
        state.complete(old)
        assertSame(newest, state.inFlight)
        state.complete(newest)
        state.frameSubmitted()
        assertTrue(state.presented(third, frame))
        assertFalse(state.presented(first, frame))
        assertFalse(state.presented(second, frame))
    }

    @Test fun revocationAndClosedUploadsCannotRestorePresentation() {
        val content = content()
        val state = CockpitPanelUploadState()
        state.request(content, frame)
        val old = checkNotNull(state.begin())
        state.request(null, frame)
        state.complete(old)
        state.frameSubmitted()
        assertFalse(state.drawable)
        assertFalse(state.presented(content, frame))
        state.request(content(session = 8), frame)
        val newer = checkNotNull(state.begin())
        state.close()
        state.complete(newer)
        state.request(content, frame)
        state.frameSubmitted()
        assertNull(state.begin())
        assertNull(state.requested)
        assertFalse(state.presented(content, frame))
    }

    @Test fun densityFontAndSizeChangesRepaintButMonitorMotionDoesNot() {
        val content = content()
        val state = CockpitPanelUploadState()
        state.request(content, frame)
        state.complete(checkNotNull(state.begin()))
        state.frameSubmitted()
        val request = state.requested
        state.request(content, frame.copy(yawDegrees = 4f, depthOffset = 10f))
        assertSame(request, state.requested)
        assertNull(state.begin())
        assertTrue(state.presented(content, frame))
        state.request(content(font = 1.4f), frame)
        val font = checkNotNull(state.begin())
        state.complete(font)
        val resize = frame.copy(width = 853f)
        state.request(content(font = 1.4f), resize)
        assertNotNull(state.begin())
        assertFalse(state.presented(content, resize))
        assertNotEquals(content.key, content(density = 2f).key)
    }

    @Test fun phoneAndUnusableTextSizesKeepReadableForeground() {
        val content = content()
        val state = CockpitPanelUploadState()
        val phone = CockpitStageFrame(388f, 531f, 15.52f, 286.74f, 372.48f, 472.59f)
        state.request(content, phone)
        assertNull(state.requested)
        assertNull(state.begin())
        state.request(content(density = 3f), frame)
        assertNull(state.begin())
        state.request(content, frame.copy(width = Float.NaN))
        assertNull(state.begin())
        assertNull(CockpitPanelContent.from(ContinuumDashboardState(), 1f, 1f))
    }

    @Test fun texturesFitTheExistingAperturesAndStayBounded() {
        val content = content(density = 3f)
        val fold = CockpitStageFrame(2560f, 1600f, 640f, 800f, 1920f, 1440f)
        val state = CockpitPanelUploadState()
        state.request(content, fold)
        val request = checkNotNull(state.requested)
        assertEquals(2560f * .20f / 3f, request.logicalWidth, .001f)
        assertEquals(1600f * .30f / 3f, request.logicalHeight, .001f)
        assertTrue(request.width in 1..768 && request.height in 1..768)
        assertTrue(request.width.toLong() * request.height * 4L * 2L <= 768L * 768L * 8L)
    }

    @Test fun pagesReuseRealPlanAndResultRowsWithoutLiveSessionClaim() {
        val content = content()
        val painter = JarvisPresentationRecordingPainter()
        val box = JarvisShellLayout.Box(0f, 0f, 260f, 360f)
        content.draw(painter, box, false)
        content.draw(painter, box, true)
        val text = painter.texts.joinToString(" ")
        assertTrue(text.contains("Auftrag") && text.contains("Ergebnis"))
        assertTrue(text.contains("WhatsApp") && text.contains("Suche prüfen"))
        assertTrue(text.contains("noch nicht bestätigt"))
        assertFalse(text.contains("Auftragsziel bestätigt") || text.contains("Live-Sitzung"))
    }
}
