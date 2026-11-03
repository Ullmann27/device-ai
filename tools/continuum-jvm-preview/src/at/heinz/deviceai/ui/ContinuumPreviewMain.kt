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
        val texts = ArrayList<Java2DContinuumPainter.TextBox>()
        p.textBoxes = texts
        p.scale(density, density)
        ContinuumHomeScreen.drawBackground(p, layout.width, heightDp, layout)
        layout[ContinuumHomeLayout.Slot.HERO]?.let { box ->
            val renderer = ContinuumHologramRenderer()
            renderer.geometry = when {
                !layout.adaptive && layout[ContinuumHomeLayout.Slot.WORKERS] != null -> ContinuumHologramRenderer.Geometry.FOLD_PORTRAIT
                layout.wide -> ContinuumHologramRenderer.Geometry.FOLD
                else -> ContinuumHologramRenderer.Geometry.PHONE
            }
            renderer.layout(box.width, box.height)
            p.save(); p.translate(box.left, box.top)
            // the hologram is a child view on the device and is clipped to its box
            p.clipRect(0f, 0f, box.width, box.height)
            renderer.draw(p, ContinuumHologramRenderer.Frame.still())
            p.restore()
        }
        for ((slot, element) in ContinuumHomeScreen.elements(layout, 1f)) {
            val box = layout[slot] ?: continue
            p.save(); p.translate(box.left, box.top); p.scale(layout.scale, layout.scale)
            element.draw(p, box.width / layout.scale, box.height / layout.scale, state)
            p.restore()
        }
        g.dispose()
        ImageIO.write(image, "png", file)
        // Drawn texts with their bounds in window dp: continuum-design-diff.py masks the live values with them (--texts);
        // dp, not pixels, so the same list also fits a device snapshot of the same window size.
        File(file.path.removeSuffix(".png") + ".texts.json").writeText(texts.joinToString(",\n", "[\n", "\n]\n") { t ->
            "{\"text\":\"" + t.text.replace("\\", "\\\\").replace("\"", "\\\"") + "\",\"boxDp\":[" +
                listOf(t.left, t.top, t.right, t.bottom).joinToString(",") { "%.2f".format(java.util.Locale.ROOT, it / density) } + "]}"
        })
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
        // what a fresh installation shows before anything was measured: every value "—", no sample numbers
        if (part == "phone-empty") screen(assets, File(out, "phone-empty.png"), ContinuumHomeLayout.phone(412f), 861f, density,
            ContinuumDashboardState())
        if (part == "fold-empty") screen(assets, File(out, "fold-empty.png"), ContinuumHomeLayout.wide(840f), 649f, 1294f / 840f,
            ContinuumDashboardState())
        // Any window size, chosen exactly like AgentActivity: size <widthDp> <heightDp> [empty]
        if (part == "size") {
            val w = args[3].toFloat(); val h = args[4].toFloat(); val empty = args.getOrNull(5) == "empty"
            val layout = if (ContinuumHomeLayout.isWide(w, h)) ContinuumHomeLayout.wide(w, h) else ContinuumHomeLayout.phone(w)
            screen(assets, File(out, "size-${w.toInt()}x${h.toInt()}${if (empty) "-empty" else ""}.png"), layout, h, 2f,
                if (empty) ContinuumDashboardState() else ReferencePreviewData.state)
        }
    }
}
