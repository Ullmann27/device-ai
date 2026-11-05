package at.heinz.deviceai.ui

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Host preview of the German cockpit in the three owner reference window classes:
 *  - `landscape`: 59509 composition (960 x 640 dp),
 *  - `cover`: Fold7 folded / ordinary phone portrait (411 x 960 dp),
 *  - `inner`: Fold7 open (832 x 750 dp).
 *
 * Production drawing code only (JarvisShellLayout, JarvisShellRenderer, JarvisShellContent, ContinuumBrainOrbRenderer).
 * The live app renders the head through Filament inside the AVATAR slot; here an optional host render of the
 * packaged GLB (`head.png`, produced by tools/avatar-3d/web-review) is composited into the grid HEAD area so the
 * composition can be judged. This is a HOST COMPOSITE: no Filament pixels, no device, no live state.
 *
 * Args: <repoRoot> <outDir> [head.png]
 */
object GermanCockpitPreview {
    /** Removes the transparent margin of a host head render so it can be fitted to the HEAD area. */
    fun cropToAlpha(src: BufferedImage): BufferedImage {
        var minX = src.width; var minY = src.height; var maxX = -1; var maxY = -1
        for (y in 0 until src.height) for (x in 0 until src.width) if ((src.getRGB(x, y) ushr 24) > 8) {
            if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y
        }
        return if (maxX < 0) src else src.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    data class Window(val name: String, val w: Float, val h: Float, val density: Float)

    val windows = listOf(
        Window("landscape-59509", 960f, 640f, 1.6f),
        Window("fold7-cover-phone", 411f, 960f, 2.1f),
        Window("fold7-inner", 832f, 750f, 2.0f),
    )

    @JvmStatic fun main(args: Array<String>) {
        val root = File(args[0]); val out = File(args[1]).also { it.mkdirs() }
        val head = args.getOrNull(2)?.let { ImageIO.read(File(it)) }?.let(::cropToAlpha)
        for (win in windows) {
            val shell = JarvisShellLayout.create(win.w, win.h)
            // Scrolling compact layouts are captured over their full content height (viewport line drawn below).
            val img = BufferedImage((win.w * win.density).toInt(), (maxOf(win.h, shell.height) * win.density).toInt(),
                BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            val p = Java2DContinuumPainter(g, File(root, "app/src/main/assets")); p.scale(win.density, win.density)
            val state = ContinuumDashboardState()
            val content = JarvisShellContent.forState({ state }, { 0L }, grid = { shell.grid }, shell = { shell }).toMutableMap()
            content[JarvisShellLayout.Slot.AVATAR] = JarvisShellRenderer.SlotContent { painter, box ->
                val areas = shell.grid?.areas
                // Grid windows name HEAD/ORB areas; compact windows stack head over a chest orb inside one stage.
                val headBox = areas?.get(JarvisCockpitGrid.Area.HEAD) ?: shell.headBounds ?: box
                val orbBox = areas?.get(JarvisCockpitGrid.Area.ORB) ?: shell.orbBounds
                if (head != null) {
                    // Keep the render's aspect; anchor the bust to the bottom of the HEAD area like the native stage.
                    val scale = minOf(headBox.width / head.width, headBox.height / head.height)
                    val dw = head.width * scale; val dh = head.height * scale
                    val left = headBox.centerX - dw / 2f; val top = headBox.bottom - dh
                    // [g] already maps dp to pixels; this transform is concatenated in user (dp) space.
                    val t = java.awt.geom.AffineTransform()
                    t.translate(left.toDouble(), top.toDouble()); t.scale((dw / head.width).toDouble(), (dh / head.height).toDouble())
                    g.drawImage(head, t, null)
                }
                if (orbBox != null) {
                    val r = ContinuumBrainOrbRenderer.radiusFor(orbBox.width, orbBox.height)
                    val cy = orbBox.top + (orbBox.height - 2.44f * r) / 2f + r
                    ContinuumBrainOrbRenderer().draw(painter, orbBox.centerX, cy, r, ContinuumBrainOrbRenderer.Signals(phase = .2f))
                }
            }
            JarvisShellRenderer.draw(p, shell, content, tiles = JarvisShellContent.tiles(state, 0L, subtitles = true))
            if (shell.height > win.h) {
                g.color = java.awt.Color(255, 80, 120, 200)
                g.drawLine(0, (win.h * win.density).toInt(), img.width, (win.h * win.density).toInt())
            }
            g.dispose()
            ImageIO.write(img, "png", File(out, "${win.name}.png"))
            val areas = shell.grid?.areas
            println("wrote ${win.name}.png ${img.width}x${img.height} profile=${shell.profile.widthClass} " +
                "head=${areas?.get(JarvisCockpitGrid.Area.HEAD) ?: shell.headBounds} orb=${areas?.get(JarvisCockpitGrid.Area.ORB) ?: shell.orbBounds}")
        }
    }
}
