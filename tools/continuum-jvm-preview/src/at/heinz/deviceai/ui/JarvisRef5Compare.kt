package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumIcons.Icon
import at.heinz.deviceai.ui.JarvisDesign.Colors
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * §11 comparison sheet: Java2D preview (left) next to the same ref5 crop (right), both in ref5 pixels.
 * Scale: 1 ref5 px = 0.642 dp (docs/JARVIS-REF5-FARBEN.md), so the preview draws with density 1 / 0.642.
 * Usage: JarvisRef5Compare <repoRoot> <ref5.png> <out.png> <komponenten|bloom-staub>
 */
object JarvisRef5Compare {
    private const val DENSITY = 1f / 0.642f
    private const val ZOOM = 2

    /** One row: ref5 crop box (x0, y0, x1, y1) and the preview drawn in dp relative to the crop origin. */
    private class Row(val box: IntArray, val draw: (ContinuumPainter) -> Unit)

    private fun icon(i: Icon) = JarvisNeon.IconPainter { p, l, t, s, c -> ContinuumIcons.draw(p, i, l + s / 2f, t + s / 2f, s, Fill.Solid(c)) }
    private fun dp(px: Int) = px / DENSITY

    private fun komponenten() = listOf(
        Row(intArrayOf(88, 190, 275, 305)) { p ->
            JarvisNeon.button(p, dp(9), dp(8), dp(177), dp(58), "Home", icon(Icon.HOME), active = true)
            JarvisNeon.button(p, dp(9), dp(67), dp(177), dp(117), "Chat", icon(Icon.CHAT))
        },
        Row(intArrayOf(282, 560, 552, 655)) { p ->
            JarvisNeon.tile(p, dp(6), dp(8), dp(264), dp(87), "Chat", listOf("Natural, multi-turn", "AI conversation"), icon(Icon.CHAT))
        },
        Row(intArrayOf(755, 100, 925, 148)) { p ->
            JarvisNeon.pill(p, dp(9), dp(6), "JARVIS Mode", icon(Icon.MODE), rim = Colors.violetRim)
        },
    )

    private fun bloomStaub() = listOf(
        Row(intArrayOf(88, 190, 275, 305)) { p ->
            JarvisNeon.button(p, dp(9), dp(8), dp(177), dp(58), "Home", icon(Icon.HOME), active = true)
        },
        Row(intArrayOf(1180, 150, 1448, 420)) { p -> JarvisShellRenderer.bokeh(p, dp(268), dp(270)) },
    )

    @JvmStatic fun main(args: Array<String>) {
        val root = File(args[0]); val ref = ImageIO.read(File(args[1])); val out = File(args[2])
        val rows = if (args.getOrElse(3) { "komponenten" } == "bloom-staub") bloomStaub() else komponenten()
        val gap = 8
        val w = rows.maxOf { it.box[2] - it.box[0] } * 2 + gap * 3
        val h = rows.sumOf { it.box[3] - it.box[1] + gap } + gap
        val sheet = BufferedImage(w * ZOOM, h * ZOOM, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        g.color = Color(40, 40, 40); g.fillRect(0, 0, sheet.width, sheet.height)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        var y = gap
        for (row in rows) {
            val (x0, y0, x1, y1) = row.box.toList(); val cw = x1 - x0; val ch = y1 - y0
            val prev = BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB)
            val pg = prev.createGraphics()
            val p = Java2DContinuumPainter(pg, File(root, "app/src/main/assets")); p.scale(DENSITY, DENSITY)
            p.fillRect(0f, 0f, cw / DENSITY, ch / DENSITY, Fill.Solid(Colors.background))
            row.draw(p); pg.dispose()
            g.drawImage(prev, gap * ZOOM, y * ZOOM, cw * ZOOM, ch * ZOOM, null)
            g.drawImage(ref.getSubimage(x0, y0, cw, ch), (gap * 2 + cw) * ZOOM, y * ZOOM, cw * ZOOM, ch * ZOOM, null)
            y += ch + gap
        }
        g.dispose(); out.parentFile.mkdirs(); ImageIO.write(sheet, "png", out); println("wrote $out")
    }
}
