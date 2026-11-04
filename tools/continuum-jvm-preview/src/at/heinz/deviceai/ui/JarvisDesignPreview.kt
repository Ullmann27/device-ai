package at.heinz.deviceai.ui

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** JVM preview of the Jarvis shell (placeholders) for phone / Fold7 open / tablet; see scripts/jarvis-design-preview.sh. */
object JarvisDesignPreview {
    @JvmStatic fun main(args: Array<String>) {
        val root = File(args[0]); val out = File(args[1]).apply { mkdirs() }
        fun shot(name: String, w: Float, h: Float, d: Float, elapsed: Long = Long.MAX_VALUE / 4) {
            val img = BufferedImage((w * d).toInt(), (h * d).toInt(), BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            val p = Java2DContinuumPainter(g, File(root, "app/src/main/assets")); p.scale(d, d)
            JarvisShellRenderer.draw(p, JarvisShellLayout.create(w, h), elapsedMs = elapsed)
            g.dispose(); ImageIO.write(img, "png", File(out, "$name.png")); println("wrote $name.png")
        }
        shot("phone-hochformat-412x915", 412f, 915f, 1.5f)
        shot("fold7-offen-884x760", 884f, 760f, 1.2f)
        shot("tablet-quer-1280x800", 1280f, 800f, 1f)
        shot("phone-einflug-250ms", 412f, 915f, 1f, 250L)
    }
}
