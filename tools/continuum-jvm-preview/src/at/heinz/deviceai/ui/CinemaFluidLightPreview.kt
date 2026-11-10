package at.heinz.deviceai.ui

import at.heinz.deviceai.avatar.AvatarPoseReducer
import at.heinz.deviceai.avatar.ContinuumAvatarRenderer
import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Production painter, synthetic event timing. */
object CinemaFluidLightPreview {
    private val budget = JarvisRenderQuality.resolve(true, false, 0, false, false)
    private val avatar = ContinuumAvatarRenderer()
    private val pose = AvatarPoseReducer().pose(0L)
    private val orb = ContinuumHologramRenderer().apply {
        geometry = ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT
        showBrand = false
    }

    @JvmStatic fun main(args: Array<String>) {
        val root = File(args[0])
        val out = File(args[1]).apply { mkdirs() }
        val label = args[2]
        val video = args.getOrNull(3) == "video"
        for ((name, width, height) in listOf(Triple("fold", 884, 760), Triple("cover", 411, 960))) {
            val shell = JarvisShellLayout.create(width.toFloat(), height.toFloat())
            fun frame(path: File, time: Long, work: Boolean, exit: Boolean, caption: String) {
                val image = BufferedImage(width, height + 56, BufferedImage.TYPE_INT_RGB)
                val g = image.createGraphics()
                g.color = Color(0x040816)
                g.fillRect(0, 0, width, height + 56)
                val p = Java2DContinuumPainter(g, File(root, "app/src/main/assets"))
                val content = shell.boxes.keys.associateWith { slot ->
                    JarvisShellRenderer.SlotContent { painter, box ->
                        val text = when (slot) {
                            Slot.TOP_BAR -> "CONTINUUM · CINEMA"
                            Slot.CONTINUUM_HEADER -> "CONTINUUM"
                            Slot.DIALOG -> "JARVIS · VORSCHAU"
                            Slot.CAMERA_STATUS -> "KAMERA AUS"
                            Slot.COCKPIT_SECONDARY -> "MODULE"
                            Slot.WORK_PROCESS -> if (work) "LOKALE KI · DENKEN" else "ABLAUF"
                            Slot.WORK_DOCUMENT -> "DOKUMENT"
                            Slot.WORK_DRAFT -> "ERGEBNIS"
                            Slot.STATUS -> "Synthetische Ereignisse · keine Live-Messwerte"
                            else -> ""
                        }
                        if (text.isNotEmpty()) {
                            val style = JarvisDesign.Type.caption
                            painter.drawText(JarvisShellRenderer.fit(painter, text, style, box.width - 16f),
                                box.left + 8f, box.top + 19f, style, Fill.Solid(JarvisDesign.Colors.text))
                        }
                    }
                }
                JarvisShellRenderer.draw(p, shell, content, elapsedMs = time,
                    timeMs = time, deferForeground = true, luxuryDepth = true,
                    depthBudget = budget, workElapsedMs = time, clearIdle = work, exiting = exit)
                shell.headBounds?.let { b ->
                    p.save(); p.translate(b.left, b.top)
                    avatar.draw(p, b.width, b.height, pose, time.coerceAtLeast(0L) / 1000f, true)
                    p.restore()
                }
                shell.orbBounds?.let { b ->
                    p.save(); p.translate(b.left, b.top)
                    orb.layout(b.width, b.height)
                    orb.draw(p, ContinuumHologramRenderer.Frame.still(
                        if (work && !exit) ContinuumHologramRenderer.Mode.THINKING else ContinuumHologramRenderer.Mode.READY,
                        time.coerceAtLeast(0L) / 1000f), transparentBackdrop = true)
                    p.restore()
                }
                HostForegroundBoundary.draw(p, shell, content, time, work, exit, budget)
                g.color = Color(0x040816); g.fillRect(0, height, width, 56)
                g.font = Font(Font.SANS_SERIF, Font.BOLD, 12)
                g.color = Color(0x7CEBFF)
                g.drawString("$label · $caption", 8, height + 17)
                g.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
                g.color = Color.WHITE
                g.drawString("Java2D · Avatar stand-in · no AGSL / Filament / Fold7", 8, height + 35)
                g.dispose()
                path.parentFile.mkdirs()
                ImageIO.write(image, "png", path)
            }
            frame(File(out, "$name-idle.png"), 1600L, false, false, "IDLE")
            for (time in listOf(0L, 140L, 280L, 420L, 650L, 900L, 1180L, 1600L)) {
                frame(File(out, "$name-work-${time}ms.png"), time, true, false, "WORK $time ms")
            }
            for (time in listOf(0L, 140L, 280L, 420L, 650L, 900L, 1600L)) {
                frame(File(out, "$name-return-${time}ms.png"), time, true, true, "RETURN $time ms")
            }
            repeat(if (video) 144 else 0) { index ->
                val ms = index * 1000L / 24L
                val work = ms >= 500L
                val exit = ms >= 2600L
                val age = when { exit -> ms - 2600L; work -> ms - 500L; else -> 1600L }
                frame(File(out, "$name-sequence/%03d.png".format(index)), age, work, exit,
                    when { exit -> "RETURN $age ms"; work -> "WORK $age ms"; else -> "IDLE" })
            }
        }
        println("Production painter: 32 stills, ${if (video) 288 else 0} video frames; Java2D only.")
    }
}
