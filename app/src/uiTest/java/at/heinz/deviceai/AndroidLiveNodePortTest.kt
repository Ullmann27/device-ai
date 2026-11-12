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
