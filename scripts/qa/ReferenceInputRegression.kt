import at.heinz.deviceai.ui.JarvisCockpitGrid
import at.heinz.deviceai.ui.JarvisShellLayout
import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import kotlin.math.abs

/** Pure layout checks; no Android pixels. */
fun main() {
    var checks = 0
    val failures = mutableListOf<String>()
    fun expect(ok: Boolean, detail: String) {
        checks++
        if (!ok) failures += detail
    }
    val sizes = listOf(884f to 760f, 864f to 1100f, 1024f to 768f,
        1280f to 800f, 1536f to 1024f, 840f to 560f,
        360f to 740f, 600f to 720f)
    for ((w, h) in sizes) for (font in listOf(1f, 1.3f, 2f)) {
        for (compact in listOf(false, true)) {
            val id = "${w}x$h font=$font compact=$compact"
            val s = JarvisShellLayout.create(w, h, font, compactPresentation = compact)
            val input = s[Slot.INPUT]
            expect(input != null, "$id MISSING_INPUT")
            expect(s.tileBoxes().size == 10, "$id TILE_COUNT")
            expect(s.viewportHeight == h, "$id VIEWPORT_CHANGED")
            expect(s.boxes.values.all { it.left.isFinite() && it.top.isFinite() &&
                it.right <= w + .02f && it.bottom <= s.height + .02f &&
                it.width > 0f && it.height > 0f }, "$id INVALID_BOUNDS")
            expect(s == JarvisShellLayout.create(w, h, font, compactPresentation = compact),
                "$id NONDETERMINISTIC_LAYOUT")
            if (input != null) {
                expect(input.height >= 48f, "$id INPUT_TOUCH_TOO_SMALL")
                expect(input.top >= s[Slot.AVATAR]!!.bottom - .02f, "$id INPUT_OVERLAPS_AVATAR")
                expect(input.top >= s[Slot.DIALOG]!!.bottom - .02f, "$id INPUT_OVERLAPS_DIALOG")
                expect(input.bottom <= s[Slot.TILES]!!.top - 4f, "$id INPUT_OVERLAPS_TILES")
                expect(input.width >= s[Slot.TILES]!!.width - .02f, "$id INPUT_TOO_NARROW")
            }
            if (s.grid != null) {
                val associations = mapOf(Slot.TILES to JarvisCockpitGrid.Area.TILES,
                    Slot.ROW_AGENTS to JarvisCockpitGrid.Area.ROW_AGENTS,
                    Slot.ROW_TASKS to JarvisCockpitGrid.Area.ROW_TASKS,
                    Slot.ROW_MEMORY to JarvisCockpitGrid.Area.ROW_MEMORY,
                    Slot.ROW_MODEL to JarvisCockpitGrid.Area.ROW_MODEL,
                    Slot.STATUS to JarvisCockpitGrid.Area.STATUS,
                    Slot.NAV_RAIL to JarvisCockpitGrid.Area.RAIL)
                expect(associations.all { (slot, area) -> s[slot] == s.grid!![area] },
                    "$id DRAW_AND_TOUCH_GEOMETRY_DIVERGED")
                expect(abs(s.grid!!.height - s.height) < .02f, "$id GRID_HEIGHT_MISMATCH")
                expect(s.headBounds != null && s.orbBounds != null, "$id HEAD_OR_ORB_REMOVED")
            }
        }
    }
    println("checks=$checks failures=${failures.size}")
    failures.forEach(::println)
    check(failures.isEmpty()) { "Reference composer regression failed" }
}
