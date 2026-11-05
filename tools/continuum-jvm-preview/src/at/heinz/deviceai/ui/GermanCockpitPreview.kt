package at.heinz.deviceai.ui

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Host preview of the German cockpit (Java2D, the production drawing code shared with the device):
 *  - static:    `<repo> <outDir> <headDir>` renders three window classes (59509 landscape, Fold7 cover, Fold7 inner);
 *  - animation: `<repo> <outDir> <headDir> anim <seconds> <fps>` renders numbered landscape frames.
 *
 * The live app renders the head with Filament inside the head stage. Here host renders of the packaged GLB with the
 * same anchored orthographic framing (tools/avatar-3d/web-review, views-stage.json; neutral, blink and viseme poses
 * from the real morph targets) are composited and masked with [JarvisHeadAura.fadeAlpha]. Energy streams, head
 * aura and orb come from production code at the same time value the avatar view uses.
 * HOST COMPOSITE: no Filament pixels, no device, empty data state.
 */
object GermanCockpitPreview {
    data class Window(val name: String, val w: Float, val h: Float, val density: Float)

    val windows = listOf(
        Window("landscape-59509", 960f, 640f, 1.6f),
        Window("fold7-cover-phone", 411f, 960f, 2.1f),
        Window("fold7-inner", 832f, 750f, 2.0f),
    )

    /** Same alpha mask as the Android head layer ([JarvisHeadAura.fadeAlpha], DST_IN), applied to the render. */
    fun masked(src: BufferedImage, stage: JarvisShellLayout.Box): BufferedImage {
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until src.height) for (x in 0 until src.width) {
            val argb = src.getRGB(x, y)
            val sx = (x + .5f) / src.width * stage.width; val sy = (y + .5f) / src.height * stage.height
            val a = ((argb ushr 24) * JarvisHeadAura.fadeAlpha(sx, sy, stage.width, stage.height)).toInt().coerceIn(0, 255)
            out.setRGB(x, y, (a shl 24) or (argb and 0xFFFFFF))
        }
        return out
    }

    /** Pose schedule of the animation (seconds): blinks and a short spoken phrase from real morph targets. */
    fun poseAt(t: Float): String {
        for (b in listOf(1.40f, 4.90f)) {
            val d = t - b
            if (d in 0.10f..0.20f) return "blink"
            if (d in 0f..0.30f) return "half-blink"
        }
        if (t in 2.4f..4.4f) return listOf("aa", "e", "o", "mm", "aa", "e", "neutral", "o")[((t - 2.4f) / .13f).toInt() % 8]
        return "neutral"
    }

    private val maskCache = HashMap<String, BufferedImage>()

    fun render(root: File, win: Window, heads: Map<String, BufferedImage>, t: Float, pose: String): BufferedImage {
        val shell = JarvisShellLayout.create(win.w, win.h)
        val img = BufferedImage((win.w * win.density).toInt(), (maxOf(win.h, shell.height) * win.density).toInt(),
            BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        val p = Java2DContinuumPainter(g, File(root, "app/src/main/assets")); p.scale(win.density, win.density)
        val state = ContinuumDashboardState()
        val content = JarvisShellContent.forState({ state }, { 0L }, grid = { shell.grid }, shell = { shell }).toMutableMap()
        val speaking = pose in setOf("aa", "e", "o", "mm")
        content[JarvisShellLayout.Slot.AVATAR] = JarvisShellRenderer.SlotContent { painter, box ->
            // Same order as ContinuumHologramView.onDraw: streams, aura, (native head), orb.
            painter.save(); painter.translate(box.left, box.top)
            JarvisEnergyFlows.draw(painter, box.width, box.height, t, if (speaking) .6f else .18f)
            painter.restore()
            val stage = shell.headStage ?: shell.headBounds ?: box
            JarvisHeadAura.draw(painter, stage, shell.headAnchorX, t)
            heads[pose]?.let { head ->
                val tr = java.awt.geom.AffineTransform()
                tr.translate(stage.left.toDouble(), stage.top.toDouble())
                tr.scale((stage.width / head.width).toDouble(), (stage.height / head.height).toDouble())
                g.drawImage(maskCache.getOrPut("${win.name}/$pose") { masked(head, stage) }, tr, null)
                painter.save(); painter.translate(stage.left, stage.top)
                JarvisHeadAura.drawLightStreams(painter, stage.width, stage.height)
                painter.restore()
            }
            val orbBox = shell.grid?.areas?.get(JarvisCockpitGrid.Area.ORB) ?: shell.orbBounds
            if (orbBox != null) {
                val r = ContinuumBrainOrbRenderer.radiusFor(orbBox.width, orbBox.height)
                val cy = orbBox.top + (orbBox.height - 2.44f * r) / 2f + r
                ContinuumBrainOrbRenderer().draw(painter, orbBox.centerX, cy, r,
                    ContinuumBrainOrbRenderer.Signals(phase = t * .14f, processing = speaking))
            }
        }
        JarvisShellRenderer.draw(p, shell, content, tiles = JarvisShellContent.tiles(state, 0L, subtitles = true))
        if (shell.height > win.h) {
            g.color = java.awt.Color(255, 80, 120, 200)
            g.drawLine(0, (win.h * win.density).toInt(), img.width, (win.h * win.density).toInt())
        }
        g.dispose()
        return img
    }

    @JvmStatic fun main(args: Array<String>) {
        val root = File(args[0]); val out = File(args[1]).also { it.mkdirs() }
        val heads = args.getOrNull(2)?.let { dir ->
            File(dir).listFiles { f -> f.extension == "png" }.orEmpty().associate { it.nameWithoutExtension to ImageIO.read(it) }
        }.orEmpty()
        if (args.getOrNull(3) == "anim") {
            val seconds = args[4].toFloat(); val fps = args[5].toInt()
            val win = windows.first()
            for (i in 0 until (seconds * fps).toInt()) {
                val t = i / fps.toFloat()
                ImageIO.write(render(root, win, heads, t, poseAt(t)), "png", File(out, "frame-%04d.png".format(i)))
            }
            println("wrote ${(seconds * fps).toInt()} frames at $fps fps")
            return
        }
        for (win in windows) {
            ImageIO.write(render(root, win, heads, 2.0f, "neutral"), "png", File(out, "${win.name}.png"))
            val shell = JarvisShellLayout.create(win.w, win.h)
            println("wrote ${win.name}.png profile=${shell.profile.widthClass} stage=${shell.headStage} face=${shell.headBounds}")
        }
    }
}
