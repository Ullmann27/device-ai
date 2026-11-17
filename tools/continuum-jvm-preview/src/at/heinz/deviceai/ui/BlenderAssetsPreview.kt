package at.heinz.deviceai.ui

import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Host (JVM/Java2D) renders of the Blender assets in the real renderers: shell with assets on vs. off for the open
 * Fold7 and a portrait phone, and the brain orb (raster vs. vector). Same drawing code as the device; NOT a device
 * screenshot. WebP decoding on the JVM needs the TwelveMonkeys imageio-webp plugin on the classpath (preview only).
 * Args: <repoRoot> <outDir> <sha>
 */
object BlenderAssetsPreview {
    private fun render(root: File, w: Float, h: Float, d: Float, on: Boolean): BufferedImage {
        ContinuumBlenderAssets.enabled = on
        val img = BufferedImage((w * d).toInt(), (h * d).toInt(), BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        val p = Java2DContinuumPainter(g, File(root, "app/src/main/assets")); p.scale(d, d)
        val shell = JarvisShellLayout.create(w, h)
        val state = ContinuumDashboardState()
        JarvisShellRenderer.draw(p, shell, JarvisShellContent.forState({ state }, { 0L }, grid = { shell.grid }),
            tiles = JarvisShellContent.tiles(state, 0L, subtitles = true))
        g.dispose()
        return img
    }

    private fun orb(root: File, raster: Boolean, d: Float = 2.625f): BufferedImage {
        ContinuumBlenderAssets.enabled = true
        val w = 400f; val h = 480f
        val img = BufferedImage((w * d).toInt(), (h * d).toInt(), BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        val p = Java2DContinuumPainter(g, File(root, "app/src/main/assets")); p.scale(d, d)
        JarvisShellRenderer.background(p, w, h)
        val r = ContinuumBrainOrbRenderer.radiusFor(w - 24f, h - 50f)
        val cy = (h - 2.44f * r) / 2f + r
        ContinuumBrainOrbRenderer().apply { if (raster) rasterDensity = d }
            .draw(p, w / 2f, cy, r, ContinuumBrainOrbRenderer.Signals(pulse = .4f, recall = .3f, phase = .2f))
        g.dispose()
        return img
    }

    private fun pair(a: BufferedImage, b: BufferedImage, la: String, lb: String): BufferedImage {
        val out = BufferedImage(a.width + b.width + 24, maxOf(a.height, b.height) + 60, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.color = Color(8, 10, 16); g.fillRect(0, 0, out.width, out.height)
        g.drawImage(a, 0, 60, null); g.drawImage(b, a.width + 24, 60, null)
        g.color = Color.WHITE; g.font = Font(Font.SANS_SERIF, Font.BOLD, 30)
        g.drawString(la, 12, 42); g.drawString(lb, a.width + 36, 42)
        g.dispose(); return out
    }

    @JvmStatic fun main(args: Array<String>) {
        val root = File(args[0]); val out = File(args[1]).apply { mkdirs() }; val sha = args[2]
        val tag = "Host Java2D @${sha.take(8)}"
        fun write(img: BufferedImage, name: String) { ImageIO.write(img, "png", File(out, name)); println("wrote ${File(out, name)}") }
        val fOn = render(root, 884f, 760f, 2.625f, true); val fOff = render(root, 884f, 760f, 2.625f, false)
        write(fOn, "fold7-open-blender.png")
        write(pair(fOff, fOn, "Fold7 offen: Vektor (alt) – $tag", "Fold7 offen: Blender-Assets – $tag"), "fold7-open-vergleich.png")
        val pOn = render(root, 411f, 891f, 2.625f, true); val pOff = render(root, 411f, 891f, 2.625f, false)
        write(pOn, "phone-portrait-blender.png")
        write(pair(pOff, pOn, "Handy: Vektor – $tag", "Handy: Blender – $tag"), "phone-portrait-vergleich.png")
        write(pair(orb(root, false), orb(root, true), "Orb Vektor – $tag", "Orb Blender + Live-Signale – $tag"), "orb-vergleich.png")
        ContinuumBlenderAssets.enabled = true
    }
}
