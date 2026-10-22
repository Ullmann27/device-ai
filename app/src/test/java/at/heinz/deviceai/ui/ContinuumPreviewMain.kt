package at.heinz.deviceai.ui

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * JVM preview of the real Continuum drawing code (Java2D backend), for side-by-side comparison with the owner's
 * reference images. Not a device screenshot: rasteriser and font hinting differ slightly from Android's Skia.
 * Run: java -cp <jar> at.heinz.deviceai.ui.ContinuumPreviewMain <repoRoot> <outDir> [part]
 */
object ContinuumPreviewMain {
    /** Draws a whole start screen exactly as the activity composes it (one element per layout slot). */
    fun screen(assets: File, file: File, layout: ContinuumHomeLayout.Layout, heightDp: Float, density: Float,
               state: ContinuumDashboardState = ReferencePreviewData.state) {
        val image = BufferedImage((layout.width * density).toInt(), (heightDp * density).toInt(), BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        val p = Java2DContinuumPainter(g, assets)
        p.scale(density, density)
        ContinuumHomeScreen.drawBackground(p, layout.width, heightDp, layout)
        layout[ContinuumHomeLayout.Slot.HERO]?.let { box ->
            val renderer = ContinuumHologramRenderer()
            renderer.geometry = if (layout.wide) ContinuumHologramRenderer.Geometry.FOLD else ContinuumHologramRenderer.Geometry.PHONE
            renderer.layout(box.width, box.height)
            p.save(); p.translate(box.left, box.top)
            renderer.draw(p, ContinuumHologramRenderer.Frame.still())
            p.restore()
        }
        for ((slot, element) in ContinuumHomeScreen.elements(layout, 1f)) {
            val box = layout[slot] ?: continue
            p.save(); p.translate(box.left, box.top)
            element.draw(p, box.width, box.height, state)
            p.restore()
        }
        g.dispose()
        ImageIO.write(image, "png", file)
        println("wrote $file ${image.width}x${image.height}")
    }

    @JvmStatic fun main(args: Array<String>) {
        val root = File(args.getOrElse(0) { "." })
        val out = File(args.getOrElse(1) { "build/continuum-preview" }).also { it.mkdirs() }
        val part = args.getOrElse(2) { "hero" }
        val assets = File(root, "app/src/main/assets")
        val density = ContinuumTheme.REFERENCE_DENSITY
        if (part == "hero") {
            val wDp = 412f; val hDp = 307f
            val image = BufferedImage((wDp * density).toInt(), (hDp * density).toInt(), BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            g.color = java.awt.Color(ContinuumTheme.BACKGROUND, true)
            g.fillRect(0, 0, image.width, image.height)
            val painter = Java2DContinuumPainter(g, assets)
            painter.scale(density, density)
            val renderer = ContinuumHologramRenderer()
            renderer.layout(wDp, hDp)
            renderer.draw(painter, ContinuumHologramRenderer.Frame.still())
            g.dispose()
            ImageIO.write(image, "png", File(out, "hero.png"))
            println("wrote " + File(out, "hero.png") + " " + image.width + "x" + image.height)
        }
        if (part == "phone") screen(assets, File(out, "phone.png"), ContinuumHomeLayout.phone(412f), 861f, density)
        if (part == "fold") screen(assets, File(out, "fold.png"), ContinuumHomeLayout.wide(840f), 649f, 1294f / 840f)
    }
}
