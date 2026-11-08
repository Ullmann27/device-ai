package at.heinz.deviceai.ui

import java.awt.Color
import java.awt.GradientPaint
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Renders the production painter layers of the work-window construct ([HollywoodFuiCinematics] driven by
 * [HollywoodFuiConstruct]) at the phase timestamps of the law, in Fold-7-open and phone geometry.
 *
 * Java2D stand-in for the Android overlay: the perspective tilt is approximated with a shear (Java2D has no 3D
 * camera), the profile data (Android Canvas text) and the avatar are NOT drawn. Frames are evidence of the drawing
 * law, never of device frame times.
 */
object HollywoodFuiCinematicsPreview {
    private class Scene(val name: String, val widthDp: Int, val heightDp: Int, val box: HollywoodFuiCinematics.Box,
                        val originX: Float, val originY: Float)

    @JvmStatic fun main(args: Array<String>) {
        val root = File(args[0]); val out = File(args[1]).also { it.mkdirs() }
        val scale = 2f
        val scenes = listOf(
            // Fold 7 open: window right of the avatar face (face centre ≈ 330/210 dp), origin at the avatar core.
            Scene("fold", 884, 760, HollywoodFuiCinematics.Box(452f, 96f, 852f, 540f), 330f, 230f),
            // Phone portrait: window below the head, origin at the head.
            Scene("phone", 412, 915, HollywoodFuiCinematics.Box(20f, 380f, 392f, 860f), 206f, 200f)
        )
        val opening = listOf(0L, 70L, HollywoodFuiConstruct.ANCHOR_END_MS, 230L, HollywoodFuiConstruct.BRACKETS_END_MS,
            440L, HollywoodFuiConstruct.GRID_END_MS, HollywoodFuiConstruct.GLASS_END_MS, HollywoodFuiConstruct.DATA_END_MS, 2400L)
        val closing = listOf(60L, 120L, 220L, 300L, HollywoodFuiConstruct.CLOSE_END_MS)
        val budgets = listOf(
            "high" to HollywoodFuiCinematics.Budget.from(JarvisRenderQuality.resolve(true, false, 0, false, false)),
            "warm" to HollywoodFuiCinematics.Budget.from(JarvisRenderQuality.resolve(true, false, 2, false, true)),
            "low" to HollywoodFuiCinematics.Budget.from(JarvisRenderQuality.resolve(true, true, 3, false, false))
        )
        var count = 0
        for (scene in scenes) {
            for (t in opening) {
                render(root, out, scene, scale, "open-${t.toString().padStart(4, '0')}", HollywoodFuiConstruct.frame(t),
                    t, budgets[0].second, working = t >= HollywoodFuiConstruct.DATA_END_MS); count++
            }
            for (t in closing) {
                render(root, out, scene, scale, "close-${t.toString().padStart(4, '0')}",
                    HollywoodFuiConstruct.frame(3000L, t), 3000L + t, budgets[0].second, working = false); count++
            }
            for ((name, budget) in budgets.drop(1)) {
                render(root, out, scene, scale, "settled-$name", HollywoodFuiConstruct.frame(2400L), 2400L, budget, true); count++
            }
            render(root, out, scene, scale, "reduced-motion", HollywoodFuiConstruct.frame(0L, reducedMotion = true), 0L,
                budgets[0].second, working = false); count++
        }
        println("$count production-painter construct frames (Java2D stand-in, tilt approximated by shear; not device FPS)")
        println("budgets: " + budgets.joinToString { "${it.first}=${it.second}" })
    }

    private fun render(root: File, out: File, scene: Scene, scale: Float, label: String, frame: HollywoodFuiConstruct.Frame,
                       timeMs: Long, budget: HollywoodFuiCinematics.Budget, working: Boolean) {
        val image = BufferedImage((scene.widthDp * scale).toInt(), (scene.heightDp * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g.paint = GradientPaint(0f, 0f, Color(0x03, 0x07, 0x16), 0f, image.height.toFloat(), Color(0x05, 0x0B, 0x22))
        g.fillRect(0, 0, image.width, image.height)
        g.scale(scale.toDouble(), scale.toDouble())
        // Stand-in for the avatar core position (a soft cyan disc), so the beam has a visible origin.
        g.paint = Color(0x00, 0xE5, 0xFF, 40); g.fillOval((scene.originX - 48f).toInt(), (scene.originY - 48f).toInt(), 96, 96)
        g.paint = Color(0xAB, 0xF8, 0xFF, 160); g.fillOval((scene.originX - 5f).toInt(), (scene.originY - 5f).toInt(), 10, 10)

        val box = scene.box
        val side = if (scene.originX < box.centerX - 8f) -1f else if (scene.originX > box.centerX + 8f) 1f else 0f
        val saved = g.transform
        if (frame.tiltDegrees != 0f && side != 0f) {
            // Shear/scale stand-in for Camera.rotateY around the box centre (sign convention as on device: unverified).
            val radians = Math.toRadians((frame.tiltDegrees * side).toDouble())
            val t = AffineTransform()
            t.translate(box.centerX.toDouble(), box.centerY.toDouble())
            t.scale(Math.cos(radians), 1.0)
            t.shear(0.0, Math.sin(radians) * .35)
            t.translate(-box.centerX.toDouble(), -box.centerY.toDouble())
            g.transform(t)
        }
        val painter = Java2DContinuumPainter(g, File(root, "app/src/main/assets"))
        val cinematics = HollywoodFuiCinematics.Painterly(JarvisDesign.Colors.cyan)
        val bloom = budget.bloom && budget.animate
        cinematics.drawGlassLayers(painter, box, frame, 6f)
        cinematics.drawAnchor(painter, scene.originX, scene.originY, box, frame, bloom)
        painter.save(); painter.clipRect(box.left, box.top, box.right, box.bottom)
        cinematics.drawGridAndScan(painter, box, if (budget.animate) frame else frame.copy(scanEnabled = false), bloom)
        // Front glass of the real window (drawn by JarvisWorkWindow on device) as a dark pane so the stack reads.
        val dataAlpha = frame.data * frame.closeData
        if (dataAlpha > 0f) {
            painter.fillRoundRect(box.left, box.top, box.right, box.bottom, 6f,
                Fill.Solid(Argb.of(120, 0x04, 0x0C, 0x1E)), dataAlpha * .6f)
        }
        painter.restore()
        cinematics.drawMotesAndEnergy(painter, box, frame, timeMs, if (budget.animate) budget.motes else 0, 7,
            working, bloom, 6f)
        cinematics.drawBrackets(painter, box, frame.brackets * frame.closeFrame, bloom)
        g.transform = saved
        g.color = Color(0xAB, 0xF8, 0xFF, 200)
        g.font = g.font.deriveFont(9f)
        g.drawString("$label  anchor=%.2f brackets=%.2f grid=%.2f glass=%.2f data=%.2f tilt=%.1f° pulse=%.2f close=%.2f/%.2f/%.2f"
            .format(frame.anchor, frame.brackets, frame.grid, frame.glass, frame.data, frame.tiltDegrees, frame.pulse,
                frame.closeData, frame.closeGlass, frame.closeFrame), 8f, scene.heightDp - 8f)
        g.dispose()
        ImageIO.write(image, "png", File(out, "${scene.name}-$label.png"))
    }
}
