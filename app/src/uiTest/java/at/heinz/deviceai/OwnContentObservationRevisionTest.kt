package at.heinz.deviceai

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Code56 field fix, real ContinuumScreenObservationService event path: Continuum's own monitor label updates
 * (TYPE_WINDOW_CONTENT_CHANGED from its own package) must not advance the live observation revision, otherwise the
 * projection service rejects every frame it copies and the monitor stays „Verdeckt · Bildquelle nicht bestätigt“.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [ScreenObservationRevisionTest.RootedService::class])
class OwnContentObservationRevisionTest {
    private lateinit var service: ContinuumScreenObservationService
    private lateinit var own: String

    @Before fun connect() {
        service = Robolectric.buildService(ContinuumScreenObservationService::class.java).get()
        own = service.packageName
        assertTrue(own.isNotBlank())
        ScreenObservationBus.connect(service)
    }

    @After fun reset() { ScreenObservationBus.disconnect(service); ScreenObservationRevisionTest.RootedService.root = null }

    private fun send(type: Int, pkg: String?) {
        val event = AccessibilityEvent.obtain(type)
        event.packageName = pkg
        try { service.onAccessibilityEvent(event) } finally { event.recycle() }
    }

    @Test fun ownMonitorLabelUpdatesDoNotInvalidateTheLiveObservation() {
        val access = checkNotNull(ScreenObservationBus.liveAccess())
        val before = ScreenObservationBus.liveRevision(access)
        repeat(20) {
            send(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, own)
            send(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED, own)
        }
        assertEquals(before, ScreenObservationBus.liveRevision(access))
    }

    @Test fun foreignTargetAndUnknownSendersStillInvalidate() {
        val access = checkNotNull(ScreenObservationBus.liveAccess())
        var last = ScreenObservationBus.liveRevision(access)
        for (pkg in listOf("com.sec.android.app.myfiles", null, "not a package")) {
            send(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, pkg)
            val now = ScreenObservationBus.liveRevision(access)
            assertTrue("$pkg must advance", now > last)
            last = now
        }
    }

    @Test fun ownWindowStructureChangesStillInvalidate() {
        val access = checkNotNull(ScreenObservationBus.liveAccess())
        val before = ScreenObservationBus.liveRevision(access)
        val windowsBefore = ScreenObservationBus.liveWindowRevision(access)
        send(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, own)
        assertTrue(ScreenObservationBus.liveRevision(access) > before)
        assertTrue(ScreenObservationBus.liveWindowRevision(access) > windowsBefore)
    }

    @Test fun aReadOfContinuumItselfStillFailsClosedOnItsOwnChanges() {
        ScreenObservationRevisionTest.RootedService.root = AccessibilityNodeInfo.obtain().apply {
            packageName = own; text = "Continuum"; isVisibleToUser = true
        }
        var delivered = false
        val failure = assertThrows(IllegalStateException::class.java) {
            ScreenObservationBus.read(own) {
                if (!delivered) { delivered = true; send(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, own) }
            }
        }
        assertTrue(delivered)
        assertEquals("SCREEN_CHANGED_DURING_READ", failure.message)
    }
}
