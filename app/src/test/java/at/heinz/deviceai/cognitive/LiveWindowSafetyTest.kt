package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LiveWindowSafetyTest {
    private val own = "at.heinz.deviceai.continuumlocal"
    private val target = LiveWindowRecord(7, "com.whatsapp", LiveWindowKind.APPLICATION,
        LivePixelRect(600, 0, 1200, 1000), 3, false, false)
    private val cockpit = LiveWindowRecord(9, own, LiveWindowKind.APPLICATION,
        LivePixelRect(0, 0, 600, 1000), 4, true, true)

    @Test fun selectsActualTargetWhileCockpitHasFocus() {
        val result = LiveWindowSafety.select(listOf(target, cockpit), "com.whatsapp", own)!!
        assertEquals(7, result.target.id)
        assertTrue(result.permitsNode(LivePixelRect(620, 20, 800, 80)))
        assertFalse(result.permitsNode(LivePixelRect(200, 20, 400, 80)))
    }

    @Test fun ambiguousSameAppWindowsNeverSelectFirst() {
        assertNull(LiveWindowSafety.select(listOf(target, target.copy(id = 10, layer = 5)), "com.whatsapp", own))
    }

    @Test fun overlaysAndOwnMirrorAreRefused() {
        assertNull(LiveWindowSafety.select(listOf(cockpit), own, own))
        assertNull(LiveWindowSafety.select(listOf(target, cockpit.copy(bounds = target.bounds)), "com.whatsapp", own))
        assertNull(LiveWindowSafety.select(listOf(target, cockpit.copy(packageName = "org.other.app",
            bounds = target.bounds, kind = LiveWindowKind.UNKNOWN)), "com.whatsapp", own))
    }

    @Test fun keyboardPermitsOnlyUncoveredNodes() {
        val keyboard = cockpit.copy(packageName = "com.keyboard", kind = LiveWindowKind.INPUT_METHOD,
            bounds = LivePixelRect(0, 750, 1200, 1000))
        val result = LiveWindowSafety.select(listOf(target, cockpit, keyboard.copy(id = 11)), "com.whatsapp", own)!!
        assertTrue(result.permitsNode(LivePixelRect(620, 10, 800, 100)))
        assertFalse(result.permitsNode(LivePixelRect(620, 740, 800, 800)))
        assertNull(LiveWindowSafety.select(listOf(target, keyboard.copy(bounds = target.bounds)), "com.whatsapp", own))
    }

    @Test fun missingGeometryAndUnknownTargetsFailClosed() {
        assertNull(LiveWindowSafety.select(listOf(target.copy(bounds = LivePixelRect(0, 0, 0, 0))), "com.whatsapp", own))
        assertNull(LiveWindowSafety.select(listOf(target.copy(packageName = null)), "com.whatsapp", own))
        assertNull(LiveWindowSafety.select(emptyList(), "com.whatsapp", own))
    }

    @Test fun passiveSystemBarDoesNotBlockWholeAppButDialogDoes() {
        val status = cockpit.copy(id = 12, kind = LiveWindowKind.SYSTEM, packageName = "com.android.systemui",
            active = false, focused = false, bounds = LivePixelRect(0, 0, 1200, 40))
        val selected = LiveWindowSafety.select(listOf(target, cockpit, status), "com.whatsapp", own)!!
        assertFalse(selected.permitsNode(LivePixelRect(620, 0, 800, 30)))
        assertTrue(selected.permitsNode(LivePixelRect(620, 80, 800, 150)))
        assertNull(LiveWindowSafety.select(listOf(target, status.copy(active = true)), "com.whatsapp", own))
        assertNull(LiveWindowSafety.select(listOf(target, status.copy(bounds = LivePixelRect(700, 200, 1100, 600))), "com.whatsapp", own))
    }
}
