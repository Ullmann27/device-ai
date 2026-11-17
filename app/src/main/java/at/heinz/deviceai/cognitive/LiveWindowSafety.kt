package at.heinz.deviceai.cognitive

internal data class LivePixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val valid get() = left < right && top < bottom
    fun intersects(other: LivePixelRect) = valid && other.valid &&
        left < other.right && right > other.left && top < other.bottom && bottom > other.top
    fun contains(other: LivePixelRect) = valid && other.valid && left <= other.left && top <= other.top &&
        right >= other.right && bottom >= other.bottom
}

internal enum class LiveWindowKind { APPLICATION, INPUT_METHOD, SYSTEM, UNKNOWN }

internal data class LiveWindowRecord(
    val id: Int, val packageName: String?, val kind: LiveWindowKind,
    val bounds: LivePixelRect, val layer: Int, val active: Boolean, val focused: Boolean
)

/** Exact window identity; focus alone cannot select a split-screen app. */
internal object LiveWindowSafety {
    data class Selection(val target: LiveWindowRecord, val obstructions: List<LiveWindowRecord>) {
        fun permitsNode(bounds: LivePixelRect): Boolean = target.bounds.contains(bounds) &&
            obstructions.none { it.bounds.intersects(bounds) }
    }

    fun select(windows: List<LiveWindowRecord>, targetPackage: String, ownPackage: String): Selection? {
        if (targetPackage == ownPackage || windows.isEmpty() || windows.size > 32) return null
        if (windows.any { it.id < 0 || !it.bounds.valid } || windows.map { it.id }.distinct().size != windows.size) return null
        val target = windows.singleOrNull {
            it.packageName == targetPackage && it.kind == LiveWindowKind.APPLICATION
        } ?: return null
        val above = windows.filter { it.id != target.id && it.layer >= target.layer && it.bounds.intersects(target.bounds) }
        // IME/system bars exclude their nodes, not the whole app.
        if (above.any { it.kind != LiveWindowKind.INPUT_METHOD && !isPassiveSystemBar(it, target.bounds) }) return null
        if (above.any { it.bounds.contains(target.bounds) }) return null
        return Selection(target, above)
    }

    private fun isPassiveSystemBar(window: LiveWindowRecord, target: LivePixelRect): Boolean {
        if (window.kind != LiveWindowKind.SYSTEM || window.active || window.focused) return false
        val box = window.bounds
        val thinHorizontal = box.bottom - box.top <= (target.bottom - target.top) * 0.15 &&
            (box.top <= target.top || box.bottom >= target.bottom)
        val thinVertical = box.right - box.left <= (target.right - target.left) * 0.15 &&
            (box.left <= target.left || box.right >= target.right)
        return thinHorizontal || thinVertical
    }
}
