package at.heinz.deviceai

import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import at.heinz.deviceai.cognitive.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [AndroidLiveNodePortTest.ServiceShadow::class,
    AndroidLiveNodePortTest.NodeShadow::class, AndroidLiveNodePortTest.WindowShadow::class])
class AndroidLiveScrollTest {
    private lateinit var service: ContinuumScreenObservationService
    private lateinit var access: LiveObservationAccess
    private lateinit var list: AccessibilityNodeInfo
    private lateinit var row: AccessibilityNodeInfo
    private var calls = 0
    private var allowed = true

    private fun container() = AccessibilityNodeInfo.obtain(View(service)).apply {
        packageName = "com.whatsapp"
        viewIdResourceName = "com.whatsapp:id/list"
        isVisibleToUser = true; isEnabled = true; isScrollable = true
        setBoundsInScreen(Rect(620, 60, 1180, 900))
    }

    @Before fun setup() {
        service = Robolectric.buildService(ContinuumScreenObservationService::class.java).get()
        AndroidLiveNodePortTest.ServiceShadow.ownPackage = service.packageName
        AndroidLiveNodePortTest.ServiceShadow.overlay = false
        AndroidLiveNodePortTest.ServiceShadow.resetGeometry()
        list = container()
        row = AccessibilityNodeInfo.obtain(View(service)).apply {
            packageName = "com.whatsapp"; text = "Vorher"
            isVisibleToUser = true; isEnabled = true
            setBoundsInScreen(Rect(640, 100, 1160, 200))
        }
        shadowOf(list).addChild(row)
        AndroidLiveNodePortTest.ServiceShadow.target = list
        ScreenObservationBus.connect(service)
        access = ScreenObservationBus.liveAccess()!!
    }

    @After fun cleanup() {
        ScreenObservationBus.disconnect(service)
        AndroidLiveNodePortTest.ServiceShadow.target = null
        AndroidLiveNodePortTest.NodeShadow.onAction = null
        AndroidLiveNodePortTest.NodeShadow.onRefresh = null
    }

    private fun changed() {
        row.text = "Nachher"
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        event.packageName = "com.whatsapp"
        try { service.onAccessibilityEvent(event) } finally { event.recycle() }
    }

    private fun scrolled(source: AccessibilityNodeInfo = list, index: Int, time: Long = SystemClock.uptimeMillis(),
                         windowId: Int = 42) {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED)
        event.packageName = "com.whatsapp"; event.eventTime = time
        event.fromIndex = index; event.itemCount = 30
        shadowOf(event).setWindowId(windowId)
        shadowOf(event).setSourceNode(source)
        try { service.onAccessibilityEvent(event) } finally { event.recycle() }
    }

    private fun execute(dispatch: () -> Unit): NodeActionResult {
        val port = AndroidLiveNodePort(access, "task", "com.whatsapp", { 1 }, {},
            { check(allowed) { "OWNER_GRANT_REVOKED" } },
            deadlineNanos = System.nanoTime() + 800_000_000, captureSession = 7,
            submittedFrame = { LiveMonitorStatus(LiveMonitorState.LIVE, null,
                LiveFrameBinding("task", 7L, "com.whatsapp", service.packageName, LiveFrameOrigin.ACCESSIBILITY_WINDOW),
                LiveFrameMeta(7L, System.nanoTime(), 600, 1000, "com.whatsapp", 42, false,
                    sourceViewport = LiveTargetBounds(0f, 0f, 1f, 1f)), 1L, 0L) })
        val before = port.current()
        val request = NodeActionRequest("task", "scroll", "com.whatsapp", access.epoch,
            before.stamp.revision, NodeActionKind.SCROLL_FORWARD, NodeSelector(singleScrollable = true))
        AndroidLiveNodePortTest.NodeShadow.onAction = { action, _ ->
            calls++
            assertEquals(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, action)
            dispatch()
            true
        }
        return VerifiedNodeAction(LiveActionJournal("task")).execute(request, port)
    }

    @Test fun sameContainerIndexMovementConfirmsExactlyOneDispatch() {
        val result = execute { scrolled(index = 1); scrolled(index = 4); changed() }
        assertEquals(EffectVerdict.CONFIRMED, result.verdict)
        assertEquals(1, calls)
    }

    @Test fun badgeChangeWithoutScrollEventDoesNotConfirm() {
        assertEquals(EffectVerdict.UNCLEAR_EXPECTATION_NOT_MET, execute { changed() }.verdict)
        assertEquals(1, calls)
    }

    @Test fun movementOfDifferentContainerDoesNotConfirm() {
        val other = container()
        assertNotEquals(list, other)
        assertEquals(EffectVerdict.UNCLEAR_EXPECTATION_NOT_MET,
            execute { scrolled(other, 1); scrolled(other, 4); changed() }.verdict)
        assertEquals(1, calls)
    }

    @Test fun wrongWindowCannotSupplyScrollEvidence() {
        assertEquals(EffectVerdict.UNCLEAR_EXPECTATION_NOT_MET,
            execute { scrolled(index = 1, windowId = 99); scrolled(index = 4, windowId = 99); changed() }.verdict)
    }

    @Test fun queuedOldEventsCannotSupplyScrollEvidence() {
        val old = SystemClock.uptimeMillis() - 1
        assertEquals(EffectVerdict.UNCLEAR_EXPECTATION_NOT_MET,
            execute { scrolled(index = 1, time = old); scrolled(index = 4, time = old); changed() }.verdict)
    }

    @Test fun unchangedPositionDoesNotConfirm() {
        assertEquals(EffectVerdict.UNCLEAR_EXPECTATION_NOT_MET,
            execute { scrolled(index = 4); scrolled(index = 4); changed() }.verdict)
    }

    @Test fun revokedGrantPreventsScrollDispatch() {
        AndroidLiveNodePortTest.NodeShadow.onRefresh = { allowed = false }
        assertFalse(execute { scrolled(index = 1); scrolled(index = 4); changed() }.confirmed)
        assertEquals(0, calls)
    }
}
