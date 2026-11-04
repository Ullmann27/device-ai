package at.heinz.deviceai.ui

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** JVM preview (Java2D, same drawing code as the device) of the Gedächtnis pieces at ref5 crop size. args: repoRoot outDir */
object GedaechtnisPreview {
    @JvmStatic fun main(args: Array<String>) {
        val root = File(args[0]); val out = File(args[1]).apply { mkdirs() }
        fun shot(name: String, w: Int, h: Int, draw: (ContinuumPainter) -> Unit) {
            val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            draw(Java2DContinuumPainter(g, File(root, "app/src/main/assets")))
            g.dispose(); ImageIO.write(img, "png", File(out, "$name.png")); println("wrote $name.png")
        }
        shot("orb", 300, 315) { p ->
            p.fillRect(0f, 0f, 300f, 315f, Fill.Linear(0f, 0f, 0f, 315f, intArrayOf(JarvisDesign.Colors.background, JarvisDesign.Colors.backgroundBottom)))
            ContinuumBrainOrbRenderer().draw(p, 190f, 140f, 110f)
        }
        // Vorschau ohne Gedächtnis-Anbindung: so sieht die Karte auf einem frischen Gerät aus (keine erfundenen Zahlen).
        shot("mem", 254, 167) { p ->
            p.fillRect(0f, 0f, 254f, 167f, Fill.Solid(JarvisDesign.Colors.background))
            p.scale(254f / JarvisMemoryActivityPanel.REF_W, 254f / JarvisMemoryActivityPanel.REF_W)
            JarvisMemoryActivityPanel.draw(p, 1f, 1f, JarvisMemoryActivityPanel.REF_W - 1f, JarvisMemoryActivityPanel.REF_H - 1f, MemoryActivitySnapshot.OFFLINE)
        }
    }
}
