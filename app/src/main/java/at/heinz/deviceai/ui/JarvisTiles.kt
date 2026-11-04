package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumIcons.Icon
import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisShellLayout.Box

/**
 * Pure presentation model of an action tile (look code only, NOT a data interface).
 * Real values reach the tile through Jarvis Kern's interfaces → [displayText]; how is Kern's decision.
 */
data class JarvisTile(val target: String, val label: String, val subtitle: String, val icon: Icon,
                      val displayText: String? = null)

object JarvisTiles {
    /** The eight action tiles read from ref5 (Fold7 open), same order. */
    val reference = listOf(
        JarvisTile("chat", "Chat", "Natural, multi-turn AI conversation", Icon.CHAT),
        JarvisTile("memory", "Memory", "Long-term personal context", Icon.DATABASE),
        JarvisTile("logic", "Logic", "Reasoning, planning, problem solving", Icon.ATOM),
        JarvisTile("research", "Research", "Deep research with sources", Icon.SEARCH),
        JarvisTile("agents", "Agents", "Specialized AI teammates", Icon.USERS),
        JarvisTile("learning", "Learning", "Adapt, improve and build new skills", Icon.BOOK),
        JarvisTile("device", "Device Control", "Control your Android, apps and system", Icon.CPU),
        JarvisTile("system", "System Status", "Health, performance and model status", Icon.PULSE),
    )

    /** Draws tile content inside an already drawn glass card. */
    fun draw(p: ContinuumPainter, b: Box, tile: JarvisTile) {
        val iconSize = minOf(b.height * .5f, 30f)
        val ix = b.left + 10f + iconSize / 2f; val cy = b.top + b.height / 2f
        ContinuumIcons.draw(p, tile.icon, ix, cy, iconSize, Fill.Solid(Colors.cyan), iconSize / 13f * 2.6f, .18f)
        ContinuumIcons.draw(p, tile.icon, ix, cy, iconSize, Fill.Solid(Colors.cyan))
        val tx = ix + iconSize / 2f + 10f; val room = b.right - 10f - tx
        val title = JarvisDesign.Type.title; val sub = JarvisDesign.Type.caption
        p.drawText(JarvisShellRenderer.fit(p, tile.label, title, room), tx, cy - 2f, title, Fill.Solid(Colors.text))
        val second = tile.displayText ?: tile.subtitle
        p.drawText(JarvisShellRenderer.fit(p, second, sub, room), tx, cy + p.capHeight(sub) + 5f, sub,
            Fill.Solid(if (tile.displayText != null) Colors.cyanSoft else Colors.textSecondary))
        ContinuumIcons.draw(p, Icon.CHEVRON_RIGHT, b.right - 10f, cy, 10f, Fill.Solid(Colors.textSecondary), 1.1f)
    }
}
