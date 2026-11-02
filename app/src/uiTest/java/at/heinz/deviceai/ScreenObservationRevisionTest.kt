package at.heinz.deviceai

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAccessibilityService

/** Real service event/read code with synthetic framework nodes; no device or action authority. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [ScreenObservationRevisionTest.RootedService::class])
class ScreenObservationRevisionTest {
    @Implements(AccessibilityService::class)
    class RootedService : ShadowAccessibilityService() {
        companion object { var root: AccessibilityNodeInfo? = null }
        @Implementation protected fun getRootInActiveWindow(): AccessibilityNodeInfo? = root
    }
    private val service = ContinuumScreenObservationService()
    @After fun reset() { ScreenObservationBus.disconnect(service); RootedService.root = null }
    private fun prepare() {
        ScreenObservationBus.connect(service)
        RootedService.root = AccessibilityNodeInfo.obtain().apply {
            packageName = "com.android.settings"; text = "Akku 80 Prozent"; isVisibleToUser = true
        }
    }
    private fun contentChanged(target: ContinuumScreenObservationService = service, pkg: String? = null) {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        event.packageName = pkg
        try { target.onAccessibilityEvent(event) } finally { event.recycle() }
    }
    @Test fun contentEventInvalidatesRevisionWithoutInventingFreshLaunchEvidence() {
        prepare(); ScreenObservationBus.observed(service, "com.android.settings")
        val before = ScreenObservationBus.revision(); val epoch = ScreenObservationBus.grantEpoch()
        contentChanged()
        assertTrue(ScreenObservationBus.revision() > before)
        assertEquals(epoch, ScreenObservationBus.grantEpoch())
        assertNull("content change is no new foreground confirmation", ScreenObservationBus.awaitAfter(before, 1))
    }
    @Test fun contentChangeDuringReadFailsClosedInsteadOfReleasingStaleText() {
        prepare(); var delivered = false
        val failure = assertThrows(IllegalStateException::class.java) {
            ScreenObservationBus.read("com.android.settings") {
                if (!delivered) { delivered = true; contentChanged() }
            }
        }
        assertTrue(delivered)
        assertEquals("SCREEN_CHANGED_DURING_READ", failure.message)
    }
    @Test fun unknownWindowDuringReadInvalidatesSnapshotAndLaunchEvidence() {
        prepare(); ScreenObservationBus.observed(service, "com.android.settings")
        val before = ScreenObservationBus.revision(); var delivered = false; var checks = 0
        val failure = assertThrows(IllegalStateException::class.java) {
            ScreenObservationBus.read("com.android.settings") {
                if (++checks == 2) {
                    delivered = true
                    RootedService.root = null
                    val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOWS_CHANGED)
                    try { service.onAccessibilityEvent(event) } finally { event.recycle() }
                }
            }
        }
        assertTrue(delivered)
        assertEquals("SCREEN_CHANGED_DURING_READ", failure.message)
        assertNull(ScreenObservationBus.awaitAfter(before, 1))
    }
    @Test fun unknownWindowCannotLeaveOldForegroundConfirmation() {
        prepare()
        val before = ScreenObservationBus.revision()
        ScreenObservationBus.observed(service, "com.android.settings")
        RootedService.root = null
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        try { service.onAccessibilityEvent(event) } finally { event.recycle() }
        assertNull(ScreenObservationBus.awaitAfter(before, 1))
    }
    @Test fun unrelatedPackageUpdatesDoNotAbortStableTargetRead() {
        prepare(); var delivered = false
        val result = ScreenObservationBus.read("com.android.settings") {
            if (!delivered) {
                delivered = true
                contentChanged(pkg = "com.android.systemui")
                contentChanged(pkg = "com.example.otherpane")
            }
        }
        assertEquals(listOf("Akku 80 Prozent"), result.snapshot.texts)
    }
    @Test fun unrelatedThenTargetUpdateStillInvalidates() {
        prepare(); var delivered = false
        val failure = assertThrows(IllegalStateException::class.java) {
            ScreenObservationBus.read("com.android.settings") {
                if (!delivered) {
                    delivered = true
                    contentChanged(pkg = "com.example.otherpane")
                    contentChanged(pkg = "com.android.settings")
                }
            }
        }
        assertEquals("SCREEN_CHANGED_DURING_READ", failure.message)
    }
    @Test fun unknownOrMalformedPackageCannotBeIgnored() {
        for (pkg in listOf(null, "", " ", "not a package")) {
            prepare(); var delivered = false
            val failure = assertThrows(IllegalStateException::class.java) {
                ScreenObservationBus.read("com.android.settings") {
                    if (!delivered) { delivered = true; contentChanged(pkg = pkg) }
                }
            }
            assertEquals("SCREEN_CHANGED_DURING_READ", failure.message)
        }
    }
    @Test fun concurrentReaderIsRejectedWithoutCorruptingOriginalRead() {
        prepare(); var attempted = false
        val result = ScreenObservationBus.read("com.android.settings") {
            if (!attempted) {
                attempted = true
                val outcome = java.util.concurrent.atomic.AtomicReference<Throwable?>()
                val reader = Thread {
                    try { ScreenObservationBus.read("com.example.otherpane") {} }
                    catch (failure: Throwable) { outcome.set(failure) }
                }
                reader.start(); reader.join(1000)
                assertFalse("second reader must not block", reader.isAlive)
                assertEquals("ANOTHER_SCREEN_READ_ACTIVE", outcome.get()?.message)
            }
        }
        assertTrue(result.snapshot.complete)
    }
    @Test fun abortedReadClearsTargetInFinally() {
        for (reason in listOf("DEVICE_ACTION_CANCELLED", "SCREEN_READ_DEADLINE")) {
            prepare()
            val failure = assertThrows(IllegalStateException::class.java) {
                ScreenObservationBus.read("com.android.settings") { error(reason) }
            }
            assertEquals(reason, failure.message)
            prepare() // Each real traversal owns/recycles its framework root.
            assertTrue(ScreenObservationBus.read("com.android.settings") {}.snapshot.complete)
        }
    }
    @Test fun disconnectAndInterruptInvalidateEvenWithUnrelatedEvents() {
        for (disconnect in listOf(false, true)) {
            prepare(); var delivered = false
            val failure = assertThrows(IllegalStateException::class.java) {
                ScreenObservationBus.read("com.android.settings") {
                    if (!delivered) {
                        delivered = true
                        contentChanged(pkg = "com.example.otherpane")
                        if (disconnect) ScreenObservationBus.disconnect(service) else ScreenObservationBus.interrupt(service)
                    }
                }
            }
            assertEquals("SCREEN_CHANGED_DURING_READ", failure.message)
        }
    }
    @Test fun stableReadRetainsBoundedObservedText() {
        prepare()
        val result = ScreenObservationBus.read("com.android.settings") {}
        assertTrue(result.snapshot.complete)
        assertEquals(listOf("Akku 80 Prozent"), result.snapshot.texts)
    }
    @Test fun oldServiceAndNullEventsCannotInvalidateCurrentService() {
        prepare(); val old = ContinuumScreenObservationService()
        val before = ScreenObservationBus.revision()
        contentChanged(old); service.onAccessibilityEvent(null)
        assertEquals(before, ScreenObservationBus.revision())
    }
}
