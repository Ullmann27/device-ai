package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumHomeLayout.Box
import at.heinz.deviceai.ui.ContinuumHomeLayout.Layout
import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot

/**
 * Landscape Jarvis cockpit (owner references ref1 + ref3, 2026-11-04), built on Welten-Optik's
 * [JarvisShellLayout] (PR #479): the shell decides the column split per form factor, this object only
 * subdivides the shell slots into cockpit controls. The navigation rail stays the Activity's existing rail;
 * the shell is shifted so its NAV_RAIL ends exactly where that rail ends.
 *
 *  TOP_BAR            → BRAND · top tabs (Gedächtnis, Agenten, Bots, System, Netz, Lernstand) · SEARCH/NOTIFICATIONS/PROFILE
 *  COCKPIT_PRIMARY    → MODEL ("KI-Gehirn") · PROCESSES ("Aktuelle Prozesse") · LIVE_DIALOG
 *  AVATAR             → VIEW_* presentation pills · HERO = **avatar slot** (Jarvis Avatar plugs in via the avatar contract)
 *  COCKPIT_SECONDARY  → SYSTEM_STATUS · WORKERS ("Aktive Agenten")
 *  INPUT              → COMPOSER
 *  STATUS             → action bar: Denken · Recherchieren · Lernen · Entwickeln · Ausführen · Überprüfen
 *
 * One screen, no scrolling; every target ≥ 48 dp. Large type (> 1.3) and the compact presentation keep the
 * scrolling reference layout, which reserves more reading height.
 */
object ContinuumLandscapeCockpitLayout {
    /** The centre stage reserved for the avatar head. */
    val AVATAR_SLOT = Slot.HERO

    val topTabs = listOf(Slot.ACTION_MEMORY, Slot.ACTION_AGENTS, Slot.ACTION_BOTS, Slot.ACTION_DEVICE,
        Slot.LABEL_CONNECTED, Slot.LABEL_EVOLVING)
    val actionBar = listOf(Slot.LABEL_THINKING, Slot.LABEL_ANALYSING, Slot.LABEL_LEARNING, Slot.LABEL_DEVELOPING,
        Slot.ACTION_TASK, Slot.ACTION_VERIFY)
    val cards = listOf(Slot.MODEL, Slot.PROCESSES, Slot.LIVE_DIALOG, Slot.SYSTEM_STATUS, Slot.WORKERS)

    const val TARGET = 48f
    const val ACTION_BAR_HEIGHT = 56f

    /** Landscape, wide enough for three columns, normal type and the full (non-compact) presentation. */
    fun applies(width: Float, height: Float, wide: Boolean, fontScale: Float, compact: Boolean): Boolean =
        wide && !compact && fontScale <= 1.3f && height >= 480f && !ContinuumHomeLayout.isPortraitFold(width, height) &&
            width >= 720f

    fun create(width: Float, height: Float, fontScale: Float): Layout {
        val rail = ContinuumHomeLayout.railWidthDp(width)
        // Build the shell on a virtual width so that its own rail coincides with the real Activity rail.
        val probe = JarvisShellLayout.create(width, height, fontScale)
        val shellRail = probe[JarvisShellLayout.Slot.NAV_RAIL]?.right ?: 0f
        val shell = JarvisShellLayout.create((width - rail + shellRail).coerceAtLeast(240f), height, fontScale)
        val dx = rail - (shell[JarvisShellLayout.Slot.NAV_RAIL]?.right ?: 0f)
        fun s(slot: JarvisShellLayout.Slot): Box = requireNotNull(shell[slot]) { "shell lacks $slot" }
            .let { Box(it.left + dx, it.top, it.right + dx, it.bottom) }
        val g = JarvisDesign.Metrics.gap
        val boxes = linkedMapOf<Slot, Box>()
        val top = s(JarvisShellLayout.Slot.TOP_BAR)
        val margin = JarvisDesign.Metrics.margin
        // Column proportions come from the shell's FOLD_OPEN composition (ref5 = ref1/ref3 three columns), also on
        // tablets, so the cockpit keeps the reference arrangement while Welten-Optik owns the numbers.
        val ref = JarvisShellLayout.create(800f, 700f, fontScale)
        val refTop = requireNotNull(ref[JarvisShellLayout.Slot.TOP_BAR])
        fun frac(slot: JarvisShellLayout.Slot): Pair<Float, Float> = requireNotNull(ref[slot]).let {
            (it.left - refTop.left) / refTop.width to (it.right - refTop.left) / refTop.width
        }
        fun xs(f: Pair<Float, Float>) = top.left + top.width * f.first to top.left + top.width * f.second
        val (pl, pr) = xs(frac(JarvisShellLayout.Slot.COCKPIT_PRIMARY))
        val (al, ar) = xs(frac(JarvisShellLayout.Slot.AVATAR))
        val (sl, sr) = xs(frac(JarvisShellLayout.Slot.COCKPIT_SECONDARY))
        val (il, ir) = xs(frac(JarvisShellLayout.Slot.INPUT))
        val inputHeight = JarvisDesign.Metrics.inputBarHeight
        val bar = Box(top.left, height - margin - ACTION_BAR_HEIGHT, top.right, height - margin)
        val input = Box(il, bar.top - g - inputHeight, ir, bar.top - g)
        val contentTop = top.bottom
        val primary = Box(pl, contentTop, pr, input.top - g)
        val avatar = Box(al, contentTop, ar, input.top - g)
        // The right column reaches down to the action bar (the input sits under brain + avatar only).
        val secondary = Box(sl, contentTop, sr, bar.top - g)

        // Top bar
        val ty = top.top + (top.height - TARGET) / 2f
        val brandWidth = (top.width * .24f).coerceIn(150f, 240f)
        boxes[Slot.BRAND] = Box(top.left, ty, top.left + brandWidth, ty + TARGET)
        var x = top.right
        for (slot in listOf(Slot.PROFILE, Slot.NOTIFICATIONS, Slot.SEARCH)) {
            val l = x - TARGET
            boxes[slot] = Box(l, ty, l + TARGET, ty + TARGET); x = l - 6f
        }
        val tabsLeft = top.left + brandWidth + 8f
        val tabWidth = ((x - 2f - tabsLeft) - (topTabs.size - 1) * 4f) / topTabs.size
        topTabs.forEachIndexed { i, slot ->
            val l = tabsLeft + i * (tabWidth + 4f)
            boxes[slot] = Box(l, ty, l + tabWidth, ty + TARGET)
        }

        // Left column: brain 40 %, processes 30 %, dialog 30 %
        stack(boxes, primary, listOf(Slot.MODEL to .40f, Slot.PROCESSES to .30f, Slot.LIVE_DIALOG to .30f), g)
        // Right column: system 48 %, agents 52 %
        stack(boxes, secondary, listOf(Slot.SYSTEM_STATUS to .48f, Slot.WORKERS to .52f), g)

        // Centre: presentation pills on top, the avatar slot below them
        val pillWidth = (avatar.width - 2 * 6f) / 3f
        listOf(Slot.VIEW_AVATAR, Slot.VIEW_ORB, Slot.VIEW_COMPACT).forEachIndexed { i, slot ->
            val l = avatar.left + i * (pillWidth + 6f)
            boxes[slot] = Box(l, avatar.top, l + pillWidth, avatar.top + TARGET)
        }
        boxes[AVATAR_SLOT] = Box(avatar.left, avatar.top + TARGET + 6f, avatar.right, avatar.bottom)
        boxes[Slot.COMPOSER] = input

        val buttonWidth = (bar.width - (actionBar.size - 1) * 8f) / actionBar.size
        actionBar.forEachIndexed { i, slot ->
            val l = bar.left + i * (buttonWidth + 8f)
            boxes[slot] = Box(l, bar.top, l + buttonWidth, bar.bottom)
        }
        boxes[Slot.RAIL] = Box(0f, 0f, rail, height)
        return Layout(true, width, 1f, height, boxes, referenceCockpit = true, landscapeCockpit = true)
    }

    private fun stack(boxes: MutableMap<Slot, Box>, column: Box, parts: List<Pair<Slot, Float>>, gap: Float) {
        val room = column.height - gap * (parts.size - 1)
        var y = column.top
        for ((slot, share) in parts) {
            val h = room * share
            boxes[slot] = Box(column.left, y, column.right, y + h)
            y += h + gap
        }
    }
}
