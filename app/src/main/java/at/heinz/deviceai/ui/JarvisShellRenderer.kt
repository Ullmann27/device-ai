package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisShellLayout.Box
import at.heinz.deviceai.ui.JarvisShellLayout.Shell
import at.heinz.deviceai.ui.JarvisShellLayout.Slot

/**
 * Draws a [Shell]: page background, glass cards per slot with the Muster-B fly-in (centre first,
 * left/right by side), and the slot content supplied by Jarvis Kern / Jarvis Avatar.
 * Slots without content get neutral placeholders (labels only, never invented values).
 * Coordinates in dp; the caller scales the painter by density.
 */
object JarvisShellRenderer {
    fun interface SlotContent { fun draw(p: ContinuumPainter, box: Box) }

    /** Slots drawn as bare stage (no glass card behind). */
    private val bare = setOf(Slot.AVATAR, Slot.TOP_BAR)

    /** @param elapsedMs time since the shell entrance started; large value = settled. */
    fun draw(p: ContinuumPainter, shell: Shell, content: Map<Slot, SlotContent> = emptyMap(),
             elapsedMs: Long = Long.MAX_VALUE / 4, animatorScale: Float = 1f) {
        background(p, shell.width, shell.height)
        val cardSlots = shell.boxes.keys.filter { it !in bare && it != Slot.TILES }
        val tiles = shell.tileBoxes()
        val cards = cardSlots.map { shell[it]!! } + tiles
        val cx = shell.width / 2f
        val ranks = JarvisMotion.centerFirstRanks(cards.map { it.centerX }, cx)
        // Avatar first (it is the stage), then cards centre-first.
        shell[Slot.AVATAR]?.let { box ->
            val dy = JarvisMotion.idleOffset(elapsedMs, animatorScale = animatorScale)
            p.save(); p.translate(0f, dy)
            (content[Slot.AVATAR] ?: SlotContent(::avatarPlaceholder)).draw(p, box)
            p.restore()
        }
        shell[Slot.TOP_BAR]?.let { (content[Slot.TOP_BAR] ?: SlotContent(::topBarPlaceholder)).draw(p, it) }
        cards.forEachIndexed { i, box ->
            val spec = JarvisMotion.panelSpecFor(box.centerX, cx)
            val f = JarvisMotion.frame(spec, elapsedMs - JarvisMotion.staggerStart(ranks[i], animatorScale = animatorScale), animatorScale)
            val slot = cardSlots.getOrNull(i)
            JarvisMotion.drawCard(p, f, box.left, box.top, box.right, box.bottom) { q ->
                val c = slot?.let { content[it] }
                if (c != null) c.draw(q, box) else placeholder(q, box, slot?.let(::label) ?: "Kachel ${i - cardSlots.size + 1}")
            }
        }
    }

    fun background(p: ContinuumPainter, w: Float, h: Float) {
        p.fillRect(0f, 0f, w, h, Fill.Linear(0f, 0f, 0f, h, intArrayOf(Colors.background, Colors.backgroundBottom)))
        JarvisNeon.halo(p, w / 2f, h * .3f, maxOf(w, h) * .5f, Colors.violet, .12f)
    }

    fun label(slot: Slot) = when (slot) {
        Slot.NAV_RAIL -> "Navigation"; Slot.BOTTOM_NAV -> "Navigation"; Slot.DIALOG -> "Jarvis – Dialog"
        Slot.INPUT -> "Message Continuum …"; Slot.COCKPIT_PRIMARY -> "Kern-Status"; Slot.COCKPIT_SECONDARY -> "Live-Feed / Module"
        Slot.ROW_AGENTS -> "Aktive Agenten"; Slot.ROW_TASKS -> "Aktueller Ablauf"; Slot.ROW_MEMORY -> "Gedächtnis-Aktivität"
        Slot.ROW_MODEL -> "Lokales Modell"; Slot.STATUS -> "System & Continuum Status"; else -> slot.name
    }

    private fun placeholder(p: ContinuumPainter, b: Box, text: String) {
        val style = JarvisDesign.Type.title
        val y = if (b.height < 70f) b.top + b.height / 2f + p.capHeight(style) / 2f else b.top + 14f + p.capHeight(style)
        JarvisNeon.text(p, ContinuumSurfaces.fit(p, text, style, b.width - 24f), b.left + 12f, y, style, Colors.text)
    }

    private fun topBarPlaceholder(p: ContinuumPainter, b: Box) {
        val s = JarvisDesign.Type.brand
        JarvisNeon.text(p, "CONTINUUM", b.centerX, b.top + b.height / 2f + p.capHeight(s) / 2f, s, Colors.cyan, TextAlign.CENTER)
    }

    /** Stand-in for Jarvis Avatar: head halo + orbit rings left, brain orb on pedestal right (ref4/ref5 composition). */
    private fun avatarPlaceholder(p: ContinuumPainter, b: Box) {
        val headX = b.left + b.width * .38f; val headY = b.top + b.height * .42f
        val r = minOf(b.width, b.height) * .3f
        JarvisNeon.halo(p, headX, headY, r * 1.6f, Colors.cyan, .22f)
        p.fillOval(headX - r * .55f, headY - r * .8f, headX + r * .55f, headY + r * .8f,
            Fill.Radial(headX, headY - r * .3f, r, intArrayOf(Argb.of(120, 60, 120, 255), Argb.of(30, 0x8A, 0x2B, 0xE2))))
        JarvisNeon.ring(p, headX, headY - r * .2f, r * 1.1f, r * .28f, 1.2f)
        JarvisNeon.ring(p, headX, headY + r * .1f, r * .95f, r * .22f, 1f)
        val orbX = b.left + b.width * .74f; val orbY = b.top + b.height * .62f; val o = r * .62f
        JarvisNeon.halo(p, orbX, orbY, o * 1.8f, Colors.violet, .3f)
        p.fillCircle(orbX, orbY, o, Fill.Radial(orbX - o * .3f, orbY - o * .3f, o * 1.2f, intArrayOf(Argb.of(160, 120, 220, 255), Argb.of(90, 30, 40, 140))))
        JarvisNeon.ring(p, orbX, orbY + o * 1.05f, o * 1.2f, o * .25f, 1.5f)
        val s = JarvisDesign.Type.caption
        JarvisNeon.text(p, "Avatar (Jarvis Avatar)", headX, headY + r * 1.05f, s, Colors.textSecondary, TextAlign.CENTER, JarvisDesign.Glow.OFF)
    }
}
