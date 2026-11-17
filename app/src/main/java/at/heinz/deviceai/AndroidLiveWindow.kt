package at.heinz.deviceai

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import at.heinz.deviceai.cognitive.*

internal fun Rect.liveRect() = LivePixelRect(left, top, right, bottom)

internal fun AccessibilityService.liveWindows(): List<LiveWindowRecord> {
    val native = windows ?: error("WINDOWS_UNAVAILABLE")
    try {
        return native.map { window ->
            val root = window.root
            val pkg = try { root?.packageName?.toString() }
            finally { @Suppress("DEPRECATION") root?.recycle() }
            LiveWindowRecord(window.id, pkg, when (window.type) {
                AccessibilityWindowInfo.TYPE_APPLICATION -> LiveWindowKind.APPLICATION
                AccessibilityWindowInfo.TYPE_INPUT_METHOD -> LiveWindowKind.INPUT_METHOD
                AccessibilityWindowInfo.TYPE_SYSTEM -> LiveWindowKind.SYSTEM
                else -> LiveWindowKind.UNKNOWN
            }, Rect().also(window::getBoundsInScreen).liveRect(), window.layer, window.isActive, window.isFocused)
        }
    } finally { native.forEach { @Suppress("DEPRECATION") it.recycle() } }
}

internal fun AccessibilityService.liveWindowSelection(targetPackage: String): LiveWindowSafety.Selection =
    LiveWindowSafety.select(liveWindows(), targetPackage, packageName) ?: error("TARGET_WINDOW_NOT_SAFE")

internal inline fun <T> AccessibilityService.withLiveWindow(
    targetPackage: String, expectedWindow: Int? = null, checkActive: () -> Unit,
    body: (AccessibilityNodeInfo, LiveWindowSafety.Selection) -> T
): T {
    checkActive()
    val selected = liveWindowSelection(targetPackage)
    check(expectedWindow == null || selected.target.id == expectedWindow) { "TARGET_WINDOW_CHANGED" }
    val native = windows ?: error("WINDOWS_UNAVAILABLE")
    try {
        val window = native.singleOrNull { it.id == selected.target.id } ?: error("TARGET_WINDOW_CHANGED")
        val root = window.root ?: error("TARGET_WINDOW_NO_ROOT")
        try {
            check(root.windowId == selected.target.id && root.packageName?.toString() == targetPackage) {
                "TARGET_WINDOW_CHANGED"
            }
            checkActive()
            return body(root, selected)
        } finally { @Suppress("DEPRECATION") root.recycle() }
    } finally { native.forEach { @Suppress("DEPRECATION") it.recycle() } }
}
