package at.heinz.deviceai.ui

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Jarvis Kern: JVM preview of the ref5 grid on the open Fold7 (Java2D, same drawing code as the device) next to the
 * reference image: preview left, reference right. Empty state = no wired sources, so panels show "—"/"offline".
 * Args: <repoRoot> <out.png> <reference.png> [widthDp heightDp density]. Not a device screenshot.
 */
object KernCockpitGridPreview {
    @JvmStatic fun main(args: Array<String>) {
        val root = File(args[0]); val out = File(args[1]); val ref = ImageIO.read(File(args[2]))
        val w = args.getOrNull(3)?.toFloat() ?: 832f; val h = args.getOrNull(4)?.toFloat() ?: 750f
        val d = args.getOrNull(5)?.toFloat() ?: 2.625f
        val img = BufferedImage((w * d).toInt(), (h * d).toInt(), BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        val p = Java2DContinuumPainter(g, File(root, "app/src/main/assets")); p.scale(d, d)
        val shell = JarvisShellLayout.create(w, h)
        val state = ContinuumDashboardState()
        JarvisShellRenderer.draw(p, shell, JarvisShellContent.forState({ state }, { 0L }, grid = { shell.grid }),
            tiles = JarvisShellContent.tiles(state, 0L, subtitles = true))
        g.dispose()
        val th = img.height
        val rw = (ref.width * th.toFloat() / ref.height).toInt()
        val sheet = BufferedImage(img.width + 24 + rw, th, BufferedImage.TYPE_INT_RGB)
        val sg = sheet.createGraphics()
        sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        sg.drawImage(img, 0, 0, null)
        sg.drawImage(ref, img.width + 24, 0, rw, th, null)
        sg.dispose()
        out.parentFile?.mkdirs(); ImageIO.write(sheet, "png", out)
        ImageIO.write(img, "png", File(out.parentFile, out.nameWithoutExtension + "-nur-vorschau.png"))
        println("wrote $out")
    }
}
