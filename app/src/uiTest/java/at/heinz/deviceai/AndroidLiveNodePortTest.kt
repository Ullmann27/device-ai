package at.heinz.deviceai

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAccessibilityNodeInfo
import org.robolectric.shadows.ShadowAccessibilityService
import org.robolectric.shadows.ShadowAccessibilityWindowInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [AndroidLiveNodePortTest.ServiceShadow::class,
    AndroidLiveNodePortTest.NodeShadow::class, AndroidLiveNodePortTest.WindowShadow::class])
class AndroidLiveNodePortTest {
    @Implements(AccessibilityService::class)
    open class ServiceShadow : ShadowAccessibilityService() {
        companion object {
            var target: AccessibilityNodeInfo? = null
            var ownPackage = "at.heinz.deviceai"
            var overlay = false
            var targetBounds = Rect(600, 0, 1200, 1000)
            var ownBounds = Rect(0, 0, 600, 1000)
            var imeBounds: Rect? = null
            fun resetGeometry() {
                targetBounds = Rect(600, 0, 1200, 1000)
                ownBounds = Rect(0, 0, 600, 1000)
                imeBounds = null
            }
        }
        @Implementation public override fun getWindows(): List<AccessibilityWindowInfo> = (0..if (imeBounds == null) 1 else 2).map { index ->
            AccessibilityWindowInfo.obtain().also { window ->
                shadowOf(window).apply {
                    setId(42 + index)
                    setType(if (index == 2) AccessibilityWindowInfo.TYPE_INPUT_METHOD else AccessibilityWindowInfo.TYPE_APPLICATION)
                    setActive(index == 1)
                    setFocused(index == 1)
                    setLayer(index + 2)
                    setBoundsInScreen(if (index == 2) imeBounds!! else if (index == 0 || overlay) targetBounds else ownBounds)
                    setRoot(if (index == 0) target?.let { AccessibilityNodeInfo.obtain(it) }
                        else AccessibilityNodeInfo.obtain().apply { packageName = if (index == 2) "com.keyboard" else ownPackage })
                }
            }
        }
        @Implementation protected fun getRootInActiveWindow(): AccessibilityNodeInfo =
            AccessibilityNodeInfo.obtain().apply { packageName = ownPackage }
    }
    @Implements(AccessibilityWindowInfo::class)
    class WindowShadow : ShadowAccessibilityWindowInfo()
    @Implements(AccessibilityNodeInfo::class)
    class NodeShadow : ShadowAccessibilityNodeInfo() {
        companion object {
            var onAction: ((Int, Bundle?) -> Boolean)? = null
            var onRefresh: (() -> Unit)? = null
        }
        @Implementation public override fun getWindowId() = 42
        @Implementation public override fun refresh(): Boolean { onRefresh?.invoke(); return true }
        @Implementation public override fun performAction(action: Int): Boolean = onAction?.invoke(action, null) ?: false
        @Implementation public override fun performAction(action: Int, arguments: Bundle?): Boolean =
            onAction?.invoke(action, arguments) ?: false
    }

    private lateinit var service: ContinuumScreenObservationService
    private lateinit var access: LiveObservationAccess
    private var calls = 0
    private var allowed = true
    private val task = "host-live-task"
    private fun field(text: String = "") = AccessibilityNodeInfo.obtain().apply {
        packageName = "com.whatsapp"; hintText = "Suchen"; this.text = text
        isVisibleToUser = true; isEnabled = true; isEditable = true
        setBoundsInScreen(Rect(620, 60, 1180, 150))
    }
    @Before fun setup() {
        service = Robolectric.buildService(ContinuumScreenObservationService::class.java).get()
        ServiceShadow.ownPackage = service.packageName
        ServiceShadow.target = field()
        ServiceShadow.overlay = false
        ServiceShadow.resetGeometry()
        ScreenObservationBus.connect(service)
        access = ScreenObservationBus.liveAccess()!!
    }
    @After fun cleanup() {
        ScreenObservationBus.disconnect(service)
        ServiceShadow.target = null
        ServiceShadow.overlay = false
        ServiceShadow.resetGeometry()
        NodeShadow.onAction = null
        NodeShadow.onRefresh = null
    }
    private fun port() = AndroidLiveNodePort(access, task, "com.whatsapp", { 1L }, {},
        { check(allowed) { "OWNER_GRANT_REVOKED" } })
    private fun request(frame: NodeActionFrame) = NodeActionRequest(task, "search", "com.whatsapp", access.epoch,
        frame.stamp.revision, NodeActionKind.SET_TEXT, NodeSelector(label = "Suchen"), "Anna")
    private fun changed() {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        event.packageName = "com.whatsapp"
        try { service.onAccessibilityEvent(event) } finally { event.recycle() }
    }

    @Test fun splitScreenUsesTargetWindowEvenWhenOwnAppFocused() {
        val frame = port().current()
        assertEquals("com.whatsapp", frame.stamp.packageName)
        assertEquals(42, frame.stamp.windowId)
        assertEquals("Suchen", frame.nodes.single().label)
        assertEquals(liveSha256(""), frame.nodes.single().textDigest)
    }

    @Test fun actualSetTextRequiresObservedExactDraft() {
        val port = port()
        val before = port.current()
        NodeShadow.onAction = { action, args ->
            calls++
            assertEquals(AccessibilityNodeInfo.ACTION_SET_TEXT, action)
            assertEquals("Anna", args!!.getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE))
            ServiceShadow.target = field("Anna"); changed(); true
        }
        val result = VerifiedNodeAction(LiveActionJournal(task)).execute(request(before), port)
        assertTrue(result.confirmed)
        assertEquals(1, calls)
    }

    private fun floatingIme(nodeBounds: Rect): LiveTargetBounds {
        ServiceShadow.targetBounds = Rect(1000, 0, 2000, 1000)
        ServiceShadow.ownBounds = Rect(0, 0, 1000, 1000)
        ServiceShadow.imeBounds = Rect(1200, 300, 1800, 600)
        ServiceShadow.target = field().apply { setBoundsInScreen(nodeBounds) }
        val selection = service.liveWindowSelection("com.whatsapp")
        assertTrue(selection.permitsNode(nodeBounds.liveRect()))
        val display = LivePixelRect(0, 0, 2000, 1000)
        val own = LiveWindowRecord(43, service.packageName, LiveWindowKind.APPLICATION,
            LivePixelRect(0, 0, 1000, 1000), 3, true, true)
        val crop = requireNotNull(ProjectionCropGeometry.crop(display, 2000, 1000, 2000, 1000, selection, own))
        assertEquals(LivePixelRect(1000, 600, 2000, 1000), crop)
        val viewport = requireNotNull(ProjectionCropGeometry.viewport(display, 2000, 1000, selection.target.bounds, crop))
        assertEquals(LiveTargetBounds(0f, .6f, 1f, 1f), viewport)
        return viewport
    }

    private fun submittedStatus(viewport: LiveTargetBounds) = LiveMonitorStatus(LiveMonitorState.LIVE, null,
        LiveFrameBinding(task, 9L, "com.whatsapp", service.packageName, LiveFrameOrigin.PROJECTION_WINDOW_CROP),
        LiveFrameMeta(9L, System.nanoTime(), 1000, 400, "com.whatsapp", 42, true, sourceViewport = viewport),
        8L, 0L)

    private fun capturedPort(frame: () -> LiveMonitorStatus?) = AndroidLiveNodePort(access, task, "com.whatsapp",
        { frame()?.sequence ?: 0L }, {}, { check(allowed) { "OWNER_GRANT_REVOKED" } },
        captureSession = 9L, submittedFrame = frame)

    @Test fun floatingImeExcludesVisibleNodeOutsideRenderedViewport() {
        val viewport = floatingIme(Rect(1300, 100, 1500, 200))
        val port = capturedPort { submittedStatus(viewport) }
        val before = port.current()
        assertTrue(before.nodes.single().visible)
        NodeShadow.onAction = { _, _ -> calls++; true }
        val failure = assertThrows(IllegalStateException::class.java) {
            port.performOnce(request(before), before.nodes.single())
        }
        assertEquals("TARGET_OUTSIDE_MONITOR_VIEWPORT", failure.message)
        assertEquals("Off-viewport input reached Android", 0, calls)
        assertFalse(port.inputAttempted)
    }

    @Test fun floatingImeRejectsTargetPartlyOutsideRenderedViewport() {
        val viewport = floatingIme(Rect(1850, 550, 1950, 650))
        val port = capturedPort { submittedStatus(viewport) }
        val before = port.current()
        assertTrue(before.nodes.single().visible)
        assertTrue(before.nodes.single().bounds!!.centerY >= viewport.top)
        NodeShadow.onAction = { _, _ -> calls++; true }
        val failure = assertThrows(IllegalStateException::class.java) {
            port.performOnce(request(before), before.nodes.single())
        }
        assertEquals("TARGET_OUTSIDE_MONITOR_VIEWPORT", failure.message)
        assertEquals(0, calls)
        assertFalse(port.inputAttempted)
    }

    @Test fun floatingImeAllowsAndVerifiesTargetInsideRenderedViewport() {
        val bounds = Rect(1300, 700, 1500, 800)
        val viewport = floatingIme(bounds)
        val port = capturedPort { submittedStatus(viewport) }
        val before = port.current()
        NodeShadow.onAction = { action, args ->
            calls++
            assertEquals(AccessibilityNodeInfo.ACTION_SET_TEXT, action)
            assertEquals("Anna", args!!.getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE))
            ServiceShadow.target = field("Anna").apply { setBoundsInScreen(bounds) }
            changed(); true
        }
        assertTrue(VerifiedNodeAction(LiveActionJournal(task)).execute(request(before), port).confirmed)
        assertEquals(1, calls)
    }

    @Test fun lostRenderReceiptAtFinalRefreshPreventsInput() {
        var status: LiveMonitorStatus? = submittedStatus(floatingIme(Rect(1300, 700, 1500, 800)))
        val port = capturedPort { status }
        val before = port.current()
        assertEquals(8L, before.stamp.frameSequence)
        NodeShadow.onRefresh = { status = null }
        NodeShadow.onAction = { _, _ -> calls++; true }
        val failure = assertThrows(IllegalStateException::class.java) {
            port.performOnce(request(before), before.nodes.single())
        }
        assertEquals("COMPUTER_RENDERED_SOURCE_REQUIRED", failure.message)
        assertEquals(0, calls)
        assertFalse(port.inputAttempted)
    }

    @Test fun searchClickWaitsForSemanticFieldWithDifferentHint() {
        ServiceShadow.target = field().apply {
            hintText = null; text = "Suchen"; isEditable = false; isClickable = true
            viewIdResourceName = "com.whatsapp:id/menuitem_search"
        }
        val port = port()
        val before = port.current()
        val request = ClickRequest("com.whatsapp", access.epoch, before.stamp.revision,
            "Suchen", "", expectedEditable = true, expectedSearchField = true)
        NodeShadow.onAction = { action, _ ->
            calls++
            assertEquals(AccessibilityNodeInfo.ACTION_CLICK, action)
            ServiceShadow.target = field().apply {
                hintText = "Fragen oder suchen"
                viewIdResourceName = "com.whatsapp:id/search_src_text"
            }
            changed(); true
        }
        var dispatches = 0
        val observed = VerifiedNodeClick.execute(request, true, port.clickPort(request) { _, _ -> dispatches++ })
        assertTrue(observed.revision > before.stamp.revision)
        assertEquals("Fragen oder suchen", observed.nodes.single().label)
        assertEquals("com.whatsapp:id/search_src_text", observed.nodes.single().viewId)
        assertTrue(request.matchesExpected(observed.nodes.single()))
        assertEquals(1, calls)
        assertEquals(1, dispatches)
    }

    @Test fun semanticClickStillChecksPermissionImmediatelyBeforeDispatch() {
        ServiceShadow.target = field().apply {
            hintText = null; text = "Suchen"; isEditable = false; isClickable = true
        }
        val port = port()
        val before = port.current()
        val request = ClickRequest("com.whatsapp", access.epoch, before.stamp.revision,
            "Suchen", "", expectedEditable = true, expectedSearchField = true)
        NodeShadow.onRefresh = { allowed = false }
        NodeShadow.onAction = { _, _ -> calls++; true }
        assertThrows(IllegalStateException::class.java) {
            VerifiedNodeClick.execute(request, true, port.clickPort(request) { _, _ -> })
        }
        assertEquals(0, calls)
    }

    @Test fun contactClickVerifiesObservedEnglishComposer() {
        ServiceShadow.target = field().apply {
            hintText = null; text = "Anna"; isEditable = false; isClickable = true
        }
        val port = port()
        val before = port.current()
        val request = ClickRequest("com.whatsapp", access.epoch, before.stamp.revision,
            "Anna", "", expectedEditable = true, expectedComposeField = true)
        NodeShadow.onAction = { action, _ ->
            calls++
            assertEquals(AccessibilityNodeInfo.ACTION_CLICK, action)
            ServiceShadow.target = field().apply { hintText = "Message" }
            changed(); true
        }
        val observed = VerifiedNodeClick.execute(request, true, port.clickPort(request) { _, _ -> })
        assertEquals(1, calls)
        assertTrue(observed.revision > before.stamp.revision)
        assertEquals("Message", observed.nodes.single().label)
        assertTrue(request.matchesExpected(observed.nodes.single()))
    }

    @Test fun intermediateRevisionWaitsForRealResultWithoutRepeatingInput() {
        val port = port(); val before = port.current()
        NodeShadow.onAction = { _, _ ->
            calls++
            changed()
            Thread {
                Thread.sleep(25)
                ServiceShadow.target = field("Anna")
                changed()
            }.apply { isDaemon = true; start() }
            true
        }
        assertTrue(VerifiedNodeAction(LiveActionJournal(task)).execute(request(before), port).confirmed)
        assertEquals(1, calls)
    }

    @Test fun acceptedWrongTextIsUnclearAndCannotRepeat() {
        val port = port(); val before = port.current(); val journal = LiveActionJournal(task)
        NodeShadow.onAction = { _, _ -> calls++; ServiceShadow.target = field("Andere"); changed(); true }
        val action = VerifiedNodeAction(journal)
        assertEquals(EffectVerdict.UNCLEAR_EXPECTATION_NOT_MET, action.execute(request(before), port).verdict)
        val newer = port.current()
        assertEquals("JOURNAL_BLOCKED_AFTER_UNCLEAR", action.execute(request(newer), port).reason)
        assertEquals(1, calls)
    }

    @Test fun permissionRevokedAtFinalDispatchCheckSendsNoInput() {
        val port = port(); val before = port.current()
        NodeShadow.onAction = { _, _ -> calls++; true }
        NodeShadow.onRefresh = { allowed = false }
        assertFalse(VerifiedNodeAction(LiveActionJournal(task)).execute(request(before), port).confirmed)
        assertEquals(0, calls)
    }

    @Test fun newlyOverlappingOwnWindowPreventsInput() {
        val port = port(); val before = port.current()
        NodeShadow.onAction = { _, _ -> calls++; true }
        NodeShadow.onRefresh = { ServiceShadow.overlay = true }
        assertFalse(VerifiedNodeAction(LiveActionJournal(task)).execute(request(before), port).confirmed)
        assertEquals(0, calls)
    }

    @Test fun labelsNeverContainDraftAndPasswordNeverSelected() {
        ServiceShadow.target = field("private draft")
        assertEquals("Suchen", port().current().nodes.single().label)
        ServiceShadow.target = field("secret").apply { isPassword = true }
        val port = port(); val before = port.current()
        assertEquals("", before.nodes.single().label)
        assertNull(before.nodes.single().textDigest)
        NodeShadow.onAction = { _, _ -> calls++; true }
        assertFalse(VerifiedNodeAction(LiveActionJournal(task)).execute(request(before), port).confirmed)
        assertEquals(0, calls)
    }
}
