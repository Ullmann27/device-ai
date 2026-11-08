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
                budgets[0].second, working = true, reduced = true); count++
            render(root, out, scene, scale, "fallback", HollywoodFuiConstruct.frame(900L), 900L,
                budgets[0].second, working = true, enabled = false); count++
            render(root, out, scene, scale, "idle", HollywoodFuiConstruct.frame(2400L), 2400L,
                budgets[0].second, working = false); count++
        }
        println("$count production-painter construct frames (Java2D stand-in, tilt approximated by shear; not device FPS)")
        println("budgets: " + budgets.joinToString { "${it.first}=${it.second}" })
    }

    private fun render(root: File, out: File, scene: Scene, scale: Float, label: String, frame: HollywoodFuiConstruct.Frame,
                       timeMs: Long, budget: HollywoodFuiCinematics.Budget, working: Boolean,
                       reduced: Boolean = !budget.animate, enabled: Boolean = true) {
        val image = BufferedImage((scene.widthDp * scale).toInt(), (scene.heightDp * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g.paint = GradientPaint(0f, 0f, Color(0x03, 0x07, 0x16), 0f, image.height.toFloat(), Color(0x05, 0x0B, 0x22))
        g.fillRect(0, 0, image.width, image.height)
        g.scale(scale.toDouble(), scale.toDouble())
        // A labelled geometry marker, not a replacement avatar or an alleged device capture.
        g.paint = Color(0xAB, 0xF8, 0xFF, 160)
        g.font = g.font.deriveFont(11f)
        g.drawString("Ursprung (kein Avatar gerendert)", 12f, scene.originY - 16f)
        g.drawLine((scene.originX - 4f).toInt(), scene.originY.toInt(), (scene.originX + 4f).toInt(), scene.originY.toInt())

        val box = scene.box
        val side = if (scene.originX < box.centerX - 8f) -1f else if (scene.originX > box.centerX + 8f) 1f else 0f
        val saved = g.transform
        val painter = Java2DContinuumPainter(g, File(root, "app/src/main/assets"))
        val cinematics = HollywoodFuiCinematics.Painterly(JarvisDesign.Colors.cyan)
        val bloom = budget.bloom && !reduced
        if (enabled) cinematics.drawAnchor(painter, scene.originX, scene.originY, box, frame, bloom)
        val open = if (reduced || !enabled) 1f else
            HollywoodFuiConstruct.flight(timeMs) * frame.closeFrame
        val source = HollywoodFuiCinematics.Box(scene.originX - 24f, scene.originY - 16f,
            scene.originX + 24f, scene.originY + 16f)
        g.translate(((source.left - box.left) * (1f - open)).toDouble(),
            ((source.top - box.top) * (1f - open)).toDouble())
        g.translate(box.left.toDouble(), box.top.toDouble())
        g.scale(((source.width + (box.width - source.width) * open) / box.width).toDouble(),
            ((source.height + (box.height - source.height) * open) / box.height).toDouble())
        g.translate(-box.left.toDouble(), -box.top.toDouble())
        val tilt = if (reduced || !enabled) 0f else HollywoodFuiConstruct.ENTRANCE_TILT_DEGREES *
            (1f - open) * (1f - open) * (1f - open) * side
        if (tilt != 0f) {
            // Shear/scale stand-in for Camera.rotateY around the box centre (sign convention as on device: unverified).
            val radians = Math.toRadians(tilt.toDouble())
            val t = AffineTransform()
            t.translate(box.left.toDouble(), box.top.toDouble())
            t.scale(Math.cos(radians), 1.0)
            t.shear(0.0, Math.sin(radians) * .35)
            t.translate(-box.left.toDouble(), -box.top.toDouble())
            g.transform(t)
        }
        if (enabled) cinematics.drawGlassLayers(painter, box, frame, 6f, budget.glassLayers)
        painter.save(); painter.clipRect(box.left, box.top, box.right, box.bottom)
        if (enabled) cinematics.drawGridAndScan(painter, box,
            frame.copy(grid = frame.grid * (1f - frame.data), scanEnabled = frame.scanEnabled && !reduced), bloom)
        painter.restore()
        if (enabled) {
            cinematics.drawMotesAndEnergy(painter, box, frame, timeMs,
                if (!reduced && (working || !frame.settled)) budget.motes else 0, 7,
                working && !reduced, bloom, 6f)
            cinematics.drawBrackets(painter, box, frame.brackets * frame.closeFrame, bloom)
        }
        // Front glass of the real window (drawn by JarvisWorkWindow on device) as a dark pane so the stack reads.
        val dataAlpha = frame.data * frame.closeData
        if (dataAlpha > 0f) {
            painter.fillRoundRect(box.left, box.top, box.right, box.bottom, 6f,
                Fill.Solid(Argb.of(240, 0x04, 0x0C, 0x1E)), dataAlpha)
            painter.drawText("NATIVER INHALT / HIER NICHT GERENDERT", box.left + 12f, box.top + 28f,
                TextStyle(FontFamily.TEXT, 400, 10f), Fill.Solid(JarvisDesign.Colors.text), alpha = dataAlpha)
        }
        g.transform = saved
        g.color = Color(0xAB, 0xF8, 0xFF, 200)
        g.font = g.font.deriveFont(9f)
        g.drawString("Java2D | KEIN Fold7-/Filament-Nachweis | native Inhalte fehlen", 8f, scene.heightDp - 24f)
        g.drawString("$label  anchor=%.2f brackets=%.2f grid=%.2f glass=%.2f data=%.2f tilt=%.1f° pulse=%.2f close=%.2f/%.2f/%.2f"
            .format(frame.anchor, frame.brackets, frame.grid, frame.glass, frame.data, frame.tiltDegrees, frame.pulse,
                frame.closeData, frame.closeGlass, frame.closeFrame), 8f, scene.heightDp - 8f)
        g.dispose()
        ImageIO.write(image, "png", File(out, "${scene.name}-$label.png"))
    }
}
