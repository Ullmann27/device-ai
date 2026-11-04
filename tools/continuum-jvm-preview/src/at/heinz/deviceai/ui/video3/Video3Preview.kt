package at.heinz.deviceai.ui.video3

import at.heinz.deviceai.ui.Fill
import at.heinz.deviceai.ui.JarvisDesign
import at.heinz.deviceai.ui.Java2DContinuumPainter
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * JVM preview (Java2D, same drawing code as the device) for the Lückenfüller pieces.
 * args: repoRoot outDir [piece…]; pieces: user-recognized, alarm, calm, warp.
 * Preview stubs here are preview-only (no head drawn: HeadDrawer.NONE unless the avatar renderer is on the classpath).
 */
object Video3Preview {
    @JvmStatic fun main(args: Array<String>) {
        val root = File(args[0]); val out = File(args[1]).apply { mkdirs() }
        val pieces = if (args.size > 2) args.drop(2).toSet() else setOf("user-recognized", "jarvis-gruss", "alarm", "calm", "warp")
        fun shot(name: String, w: Float, h: Float, d: Float, draw: (Java2DContinuumPainter) -> Unit) {
            val img = BufferedImage((w * d).toInt(), (h * d).toInt(), BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val p = Java2DContinuumPainter(g, File(root, "app/src/main/assets")); p.scale(d, d)
            draw(p); g.dispose(); ImageIO.write(img, "png", File(out, "$name.png")); println("wrote $name.png")
        }
        if ("user-recognized" in pieces) {
            // ref5 crop 193 × 179 px ⇒ 124 × 115 dp; 1 dp = 1.556 px; rendered at 2 × the ref5 crop
            val pad = 6f
            for ((suffix, model) in listOf(
                "unknown" to OwnerRecognition.UNKNOWN,
                "recognized" to OwnerRecognition(OwnerRecognitionState.RECOGNIZED, "Geräte-Entsperrung", 0L))) {
                val card = JarvisUserCard().apply { render(model) }
                shot("user-recognized-$suffix", JarvisUserCard.REF_W + 2 * pad, JarvisUserCard.REF_H + 2 * pad, 2f * 193f / 124f) { p ->
                    p.fillRect(0f, 0f, 200f, 200f, Fill.Solid(JarvisDesign.Colors.background))
                    card.draw(p, pad, pad, pad + JarvisUserCard.REF_W, pad + JarvisUserCard.REF_H)
                }
            }
        }
        if ("jarvis-gruss" in pieces) {
            // ref5 box 275 × 165 px; 1 px = 0.642 dp; rendered at 2 × ref5 pixels
            val dp = .642f; val pad = 6f
            val cw = 275f * dp; val ch = 165f * dp
            val card = JarvisGreetingCard()
            for ((suffix, hour) in listOf("14h" to 14, "ohne-uhr" to null)) {
                shot("jarvis-gruss-$suffix", cw + 2 * pad, ch + 2 * pad, 2f / dp) { p ->
                    p.fillRect(0f, 0f, 400f, 400f, Fill.Solid(JarvisDesign.Colors.background))
                    card.draw(p, pad, pad, pad + cw, pad + ch, JarvisGreeting(hour))
                }
            }
        }
        val w = 1280f / 1.5f; val h = 720f / 1.5f   // 16:9 like video3.mp4 (1280 × 720 px)
        val finding = Video3AlarmInfo(Video3AlarmKind.SECURITY_FINDING, "Quelle fehlt", null, null)
        if ("alarm" in pieces) for (t in listOf(.25f, .5f, 1.5f)) {
            val s = Video3AlarmScene()
            shot("alarm-t%.2f".format(t), w, h, 1.5f) { p -> s.render(p, w, h, t, finding) }
        }
        if ("calm" in pieces) {
            val s = Video3CalmNotice()
            shot("calm-t1.5", w, h, 1.5f) { p -> s.render(p, w, h, 1.5f,
                Video3CalmNoticeInfo("Werkzeug", "Heinz, im System ist gerade ein Fehler aufgetreten", emptyList())) }
        }
        if ("warp" in pieces) for (t in listOf(.1f, .6f, 2f)) {
            val s = Video3WarpScene()
            shot("warp-t%.2f".format(t), w, h, 1.5f) { p -> s.render(p, w, h, t) }
        }
    }
}
