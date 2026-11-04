package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumIcons.Icon
import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisShellLayout.Box

/**
 * Pure presentation model of an action tile (look code only, NOT a data interface).
 * Real values reach the tile through Jarvis Kern's interfaces → [displayText]; how is Kern's decision.
 */
data class JarvisTile(val target: String, val label: String, val icon: Icon, val displayText: String? = null,
                      /** ref5 grid (Kern): description lines under the title; empty = classic tile. */
                      val subtitle: List<String> = emptyList())

object JarvisTiles {
    /** The eight action tiles of ref5 (same order), titles translated to German. No invented subtitles. */
    val reference = listOf(
        JarvisTile("chat", "Chat", Icon.CHAT),
        JarvisTile("memory", "Gedächtnis", Icon.DATABASE),
        JarvisTile("logic", "Logik", Icon.ATOM),
        JarvisTile("research", "Recherche", Icon.SEARCH),
        JarvisTile("agents", "Agenten", Icon.USERS),
        JarvisTile("learning", "Lernen", Icon.BOOK),
        JarvisTile("device", "Gerätesteuerung", Icon.CPU),
        JarvisTile("system", "Systemstatus", Icon.PULSE),
    )

    /** Draws tile content inside an already drawn glass card. */
    fun draw(p: ContinuumPainter, b: Box, tile: JarvisTile) {
        if (tile.subtitle.isNotEmpty()) return JarvisCockpitGridContent.drawTile(p, b, tile, tile.subtitle)
        if (b.width < 140f) return drawCompact(p, b, tile)
        val iconSize = minOf(b.height * .5f, 30f)
        val ix = b.left + 10f + iconSize / 2f; val cy = b.top + b.height / 2f
        ContinuumIcons.draw(p, tile.icon, ix, cy, iconSize, Fill.Solid(Colors.cyan), iconSize / 13f * 2.6f, .18f)
        ContinuumIcons.draw(p, tile.icon, ix, cy, iconSize, Fill.Solid(Colors.cyan))
        val tx = ix + iconSize / 2f + 10f; val room = b.right - 10f - tx
        val title = JarvisDesign.Type.title; val sub = JarvisDesign.Type.caption
        val value = tile.displayText
        if (value == null) p.drawText(JarvisShellRenderer.fit(p, tile.label, title, room), tx, cy + p.capHeight(title) / 2f, title, Fill.Solid(Colors.text))
        else {
            p.drawText(JarvisShellRenderer.fit(p, tile.label, title, room), tx, cy - 2f, title, Fill.Solid(Colors.text))
            p.drawText(JarvisShellRenderer.fit(p, value, sub, room), tx, cy + p.capHeight(sub) + 5f, sub, Fill.Solid(Colors.cyanSoft))
        }
        ContinuumIcons.draw(p, Icon.CHEVRON_RIGHT, b.right - 10f, cy, 10f, Fill.Solid(Colors.textSecondary), 1.1f)
    }

    /** Narrow tiles (Fold7 centre column): icon above label, no subtitle. */
    private fun drawCompact(p: ContinuumPainter, b: Box, tile: JarvisTile) {
        val title = JarvisDesign.Type.label
        val iconSize = minOf(b.height * .42f, 26f)
        val cx = b.centerX; val iy = b.top + b.height * .40f
        ContinuumIcons.draw(p, tile.icon, cx, iy, iconSize, Fill.Solid(Colors.cyan), iconSize / 13f * 2.6f, .18f)
        ContinuumIcons.draw(p, tile.icon, cx, iy, iconSize, Fill.Solid(Colors.cyan))
        p.drawText(JarvisShellRenderer.fit(p, tile.label, title, b.width - 12f), cx, b.bottom - 9f, title, Fill.Solid(Colors.text), TextAlign.CENTER)
        tile.displayText?.let { p.drawText(JarvisShellRenderer.fit(p, it, JarvisDesign.Type.caption, b.width - 12f), cx, b.top + 12f, JarvisDesign.Type.caption, Fill.Solid(Colors.cyanSoft), TextAlign.CENTER) }
    }

    /** Navigation entries read from ref5 (rail) and ref4 (bottom nav). Presentation only. */
    data class NavItem(val target: String, val label: String, val icon: Icon)
    val railNav = listOf(NavItem("home", "Start", Icon.HOME), NavItem("chat", "Chat", Icon.CHAT), NavItem("memory", "Gedächtnis", Icon.DATABASE),
        NavItem("logic", "Logik", Icon.ATOM), NavItem("research", "Recherche", Icon.SEARCH), NavItem("agents", "Agenten", Icon.CUBE),
        NavItem("tasks", "Aufgaben", Icon.CHECK_CIRCLE), NavItem("web", "Web", Icon.GLOBE), NavItem("learning", "Lernen", Icon.BOOK),
        NavItem("device", "Gerätesteuerung", Icon.CPU), NavItem("system", "Systemstatus", Icon.PULSE))
    val bottomNav = listOf(railNav[0], railNav[1], railNav[2], railNav[5], railNav[6], NavItem("device", "Gerät", Icon.CPU))

    fun drawRail(p: ContinuumPainter, b: Box, items: List<NavItem> = railNav, selected: Int = 0, labelled: Boolean = true) {
        val rowH = minOf(44f, (b.height - 16f) / items.size)
        val style = JarvisDesign.Type.nav
        items.forEachIndexed { i, it ->
            val top = b.top + 8f + i * rowH
            val cy = top + rowH / 2f
            if (i == selected) {
                p.fillRoundRect(b.left + 6f, top + 2f, b.right - 6f, top + rowH - 2f, 10f, Fill.Solid(Argb.of(60, 0, 255, 255)))
                p.strokeRoundRect(b.left + 6f, top + 2f, b.right - 6f, top + rowH - 2f, 10f, 1f, Fill.Solid(Colors.rim))
            }
            val ix = if (labelled) b.left + 22f else b.centerX
            ContinuumIcons.draw(p, it.icon, ix, cy, 18f, Fill.Solid(if (i == selected) Colors.cyan else Colors.textSecondary))
            if (labelled) p.drawText(JarvisShellRenderer.fit(p, it.label, style, b.right - ix - 24f), ix + 18f, cy + p.capHeight(style) / 2f, style,
                Fill.Solid(if (i == selected) Colors.text else Colors.textSecondary))
        }
    }

    fun drawBottomNav(p: ContinuumPainter, b: Box, items: List<NavItem> = bottomNav, selected: Int = 0) {
        val w = b.width / items.size; val style = JarvisDesign.Type.caption
        items.forEachIndexed { i, it ->
            val cx = b.left + w * i + w / 2f
            if (i == selected) p.fillRoundRect(cx - w / 2f + 4f, b.top + 6f, cx + w / 2f - 4f, b.bottom - 6f, 12f, Fill.Solid(Argb.of(60, 0, 255, 255)))
            val c = if (i == selected) Colors.cyan else Colors.textSecondary
            ContinuumIcons.draw(p, it.icon, cx, b.top + b.height * .4f, 20f, Fill.Solid(c))
            p.drawText(JarvisShellRenderer.fit(p, it.label, style, w - 6f), cx, b.bottom - 14f, style, Fill.Solid(c), TextAlign.CENTER)
        }
    }
}
