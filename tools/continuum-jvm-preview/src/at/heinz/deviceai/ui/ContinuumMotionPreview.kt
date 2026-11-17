package at.heinz.deviceai.ui

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Actual production renderer at READY timestamps, no synthetic measurements; Java2D, not device FPS evidence. */
object ContinuumMotionPreview {
    @JvmStatic fun main(args: Array<String>) {
        val root = File(args[0]); val out = File(args[1]).also { it.mkdirs() }
        for ((name, geometry) in listOf("phone" to ContinuumHologramRenderer.Geometry.PHONE,
                "fold" to ContinuumHologramRenderer.Geometry.FOLD)) {
            val renderer = ContinuumHologramRenderer()
            renderer.geometry = geometry
            val w = if (name == "phone") 412f else 410f
            val h = if (name == "phone") 307f else 291f
            renderer.layout(w, h)
            for (i in 0..24) {
                val seconds = i / 3f
                val frame = ContinuumHologramRenderer.Frame.still(phase = .4f + seconds * (.28f + .18f * .72f))
                val image = BufferedImage((w * 1.5f).toInt(), (h * 1.5f).toInt(), BufferedImage.TYPE_INT_ARGB)
                val g = image.createGraphics()
                g.color = java.awt.Color(ContinuumTheme.BACKGROUND, true); g.fillRect(0, 0, image.width, image.height)
                val p = Java2DContinuumPainter(g, File(root, "app/src/main/assets")); p.scale(1.5f, 1.5f)
                renderer.draw(p, frame)
                g.dispose()
                ImageIO.write(image, "png", File(out, "$name-${i.toString().padStart(2, '0')}.png"))
            }
        }
        println("50 production-renderer READY frames, 0..8 seconds, sampled at 3 Hz (not measured FPS)")
    }
}
