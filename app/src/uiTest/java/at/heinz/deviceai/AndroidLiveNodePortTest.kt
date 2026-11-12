package at.heinz.deviceai

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
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
    class ServiceShadow : ShadowAccessibilityService() {
        companion object {
            var target: AccessibilityNodeInfo? = null
            var ownPackage = "at.heinz.deviceai"
            var overlay = false
        }
        @Implementation public override fun getWindows(): List<AccessibilityWindowInfo> = (0..1).map { index ->
            AccessibilityWindowInfo.obtain().also { window ->
                shadowOf(window).apply {
                    setId(if (index == 0) 42 else 43)
                    setType(AccessibilityWindowInfo.TYPE_APPLICATION)
                    setActive(index == 1)
                    setFocused(index == 1)
                    setLayer(index + 1)
                    setBoundsInScreen(if (index == 0 || overlay) Rect(600, 0, 1200, 1000) else Rect(0, 0, 600, 1000))
                    setRoot(if (index == 0) target?.let { AccessibilityNodeInfo.obtain(it) }
                        else AccessibilityNodeInfo.obtain().apply { packageName = ownPackage })
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
        ScreenObservationBus.connect(service)
        access = ScreenObservationBus.liveAccess()!!
    }
    @After fun cleanup() {
        ScreenObservationBus.disconnect(service)
        ServiceShadow.target = null
        ServiceShadow.overlay = false
        NodeShadow.onAction = null
        NodeShadow.onRefresh = null
    }
    private fun port(timeoutMillis: Long = 2500) = AndroidLiveNodePort(access, task, "com.whatsapp", { 1L }, {},
        { check(allowed) { "OWNER_GRANT_REVOKED" } }, System.nanoTime() + timeoutMillis * 1_000_000)
    private fun execute(request: NodeActionRequest, port: AndroidLiveNodePort, journal: LiveActionJournal = LiveActionJournal(task)) =
        ScreenObservationBus.withLiveOperation(request.packageName) { VerifiedNodeAction(journal).execute(request, port) }
    private fun request(frame: NodeActionFrame) = NodeActionRequest(task, "search", "com.whatsapp", access.epoch,
        frame.stamp.revision, NodeActionKind.SET_TEXT, NodeSelector(label = "Suchen"), "Anna")
    private fun changed(type: Int = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
        val event = AccessibilityEvent.obtain(type)
        event.packageName = "com.whatsapp"
        try { service.onAccessibilityEvent(event) } finally { event.recycle() }
    }
    private fun listNode() = AccessibilityNodeInfo.obtain().apply {
        packageName = "com.whatsapp"; text = "Page one"; viewIdResourceName = "com.whatsapp:id/list"
        isVisibleToUser = true; isEnabled = true; isScrollable = true
        setBoundsInScreen(Rect(620, 160, 1180, 950))
    }
    private fun scrollRequest(frame: NodeActionFrame) = NodeActionRequest(task, "scroll", "com.whatsapp", access.epoch,
        frame.stamp.revision, NodeActionKind.SCROLL_FORWARD, NodeSelector(singleScrollable = true))
    private fun scrolled(source: AccessibilityNodeInfo, deltaY: Int = -1, y: Int = 0,
                         x: Int = 0, window: Int = 42, at: Long = SystemClock.uptimeMillis()) {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED)
        event.packageName = "com.whatsapp"; event.eventTime = at
        event.scrollDeltaX = -1; event.scrollDeltaY = deltaY
        event.scrollX = x; event.scrollY = y
        shadowOf(event).setWindowId(window)
        shadowOf(event).setSourceNode(source)
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
            ServiceShadow.target = field("Anna"); changed(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED); true
        }
        val result = execute(request(before), port)
        assertTrue(result.confirmed)
        assertEquals(1, calls)
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
        val observed = ScreenObservationBus.withLiveOperation(request.packageName) {
            VerifiedNodeClick.execute(request, true, port.clickPort(request) { _, _ -> dispatches++ })
        }
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
            ScreenObservationBus.withLiveOperation(request.packageName) {
                VerifiedNodeClick.execute(request, true, port.clickPort(request) { _, _ -> })
            }
        }
        assertEquals(0, calls)
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
        assertTrue(execute(request(before), port).confirmed)
        assertEquals(1, calls)
    }

    @Test fun acceptedWrongTextIsUnclearAndCannotRepeat() {
        val port = port(); val before = port.current(); val journal = LiveActionJournal(task)
        NodeShadow.onAction = { _, _ -> calls++; ServiceShadow.target = field("Andere"); changed(); true }
        assertEquals(EffectVerdict.UNCLEAR_EXPECTATION_NOT_MET, execute(request(before), port, journal).verdict)
        val newer = port.current()
        assertEquals("JOURNAL_BLOCKED_AFTER_UNCLEAR", execute(request(newer), port, journal).reason)
        assertEquals(1, calls)
    }

    @Test fun permissionRevokedAtFinalDispatchCheckSendsNoInput() {
        val port = port(); val before = port.current()
        NodeShadow.onAction = { _, _ -> calls++; true }
        NodeShadow.onRefresh = { allowed = false }
        assertFalse(execute(request(before), port).confirmed)
        assertEquals(0, calls)
    }

    @Test fun newlyOverlappingOwnWindowPreventsInput() {
        val port = port(); val before = port.current()
        NodeShadow.onAction = { _, _ -> calls++; true }
        NodeShadow.onRefresh = { ServiceShadow.overlay = true }
        assertFalse(execute(request(before), port).confirmed)
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
        assertFalse(execute(request(before), port).confirmed)
        assertEquals(0, calls)
    }

    @Test fun actualScrollRequiresMatchingSourceMotionAndClearsEvidence() {
        val container = listNode(); ServiceShadow.target = container
        val port = port(); val before = port.current(); val request = scrollRequest(before)
        NodeShadow.onAction = { action, _ ->
            calls++
            assertEquals(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, action)
            container.text = "Page two"
            scrolled(container, deltaY = 120)
            true
        }
        val result = execute(request, port)
        assertTrue(result.reason, result.confirmed)
        assertEquals(1, calls)
        assertNull(ScreenObservationBus.liveScrollEvidence(access, request, result.before!!))
    }

    @Test fun unrelatedStaleOrStationaryScrollCannotConfirmChangedTree() {
        for (case in listOf("other-node", "other-window", "old-event", "zero", "undefined")) {
            val container = listNode(); ServiceShadow.target = container
            val port = port(800); val before = port.current()
            val other = listNode()
            NodeShadow.onAction = { _, _ ->
                calls++; container.text = "Unrelated changed label"; changed()
                scrolled(if (case == "other-node") other else container,
                    deltaY = when (case) { "zero" -> 0; "undefined" -> -1; else -> 120 },
                    window = if (case == "other-window") 43 else 42,
                    at = if (case == "old-event") SystemClock.uptimeMillis() - 1000 else SystemClock.uptimeMillis())
                true
            }
            val result = execute(scrollRequest(before), port)
            assertFalse(case + ":" + result.reason, result.confirmed)
            other.recycle()
        }
        assertEquals(5, calls)
    }

    @Test fun unknownAbsolutePositionCannotSupplyMovement() {
        val container = listNode(); ServiceShadow.target = container
        val port = port(800); val before = port.current()
        NodeShadow.onAction = { _, _ ->
            calls++; container.text = "Page two"
            scrolled(container, x = -1, y = -1)
            scrolled(container, x = 0, y = 70)
            true
        }
        assertFalse(execute(scrollRequest(before), port).confirmed)
        assertEquals(1, calls)
    }

    @Test fun twoValidAbsolutePositionsSupplyMovement() {
        val container = listNode(); ServiceShadow.target = container
        val port = port(); val before = port.current()
        NodeShadow.onAction = { _, _ ->
            calls++; container.text = "Page two"
            scrolled(container, y = 10)
            scrolled(container, y = 70)
            true
        }
        val result = execute(scrollRequest(before), port)
        assertTrue(result.reason, result.confirmed)
        assertEquals(1, calls)
    }

    @Test fun revocationAfterMovementCannotConfirmScroll() {
        val container = listNode(); ServiceShadow.target = container
        val port = port(); val before = port.current()
        NodeShadow.onAction = { _, _ ->
            calls++; container.text = "Page two"; scrolled(container, deltaY = 120)
            ScreenObservationBus.interrupt(service)
            true
        }
        assertFalse(execute(scrollRequest(before), port).confirmed)
        assertEquals(1, calls)
    }
}
