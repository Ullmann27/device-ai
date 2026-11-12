package at.heinz.deviceai.ui

import at.heinz.deviceai.avatar3d.AvatarFraming
import at.heinz.deviceai.avatar3d.AvatarGlbContract
import at.heinz.deviceai.avatar3d.CockpitStageFrame
import at.heinz.deviceai.ui.JarvisShellLayout.Box
import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import org.json.JSONArray
import org.json.JSONObject

/** Exact layout and painter export. */
object WorkstationHostExport {
    private data class Sample(val label: String, val focus: Float, val timeMs: Float)
    private fun Box.json() = JSONObject().put("left", left).put("top", top)
        .put("right", right).put("bottom", bottom)

    private fun CockpitStageFrame.json() = JSONObject().put("width", width).put("height", height)
        .put("monitorLeft", monitorLeft).put("monitorTop", monitorTop)
        .put("monitorRight", monitorRight).put("monitorBottom", monitorBottom)
        .put("yawDegrees", yawDegrees).put("pitchDegrees", pitchDegrees)
        .put("depthOffset", depthOffset).put("distance", distance)
        .put("quad", JSONArray(quad().toList()))

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 3)
        val root = File(args[0])
        val out = File(args[1]).apply { mkdirs() }
        val video = args[2] == "video"
        val assets = File(root, "app/src/main/assets")
        val avatarBytes = File(assets, AvatarGlbContract.ASSET_PATH).readBytes()
        val avatarHash = MessageDigest.getInstance("SHA-256").digest(avatarBytes)
            .joinToString("") { "%02x".format(it) }
        check(avatarHash == AvatarGlbContract.ASSET_SHA256)
        val metadata = AvatarGlbContract.validate(avatarBytes)
        val focus = metadata.focus
        val frames = JSONArray()
        val budget = JarvisRenderQuality.resolve(true, false, 0, false, false)
        for ((name, width, height) in listOf(Triple("fold", 884, 760), Triple("phone", 411, 960))) {
            val shell = CinematicWorkstationLayout.create(width.toFloat(), height.toFloat())
            val scene = requireNotNull(shell[Slot.AVATAR])
            val monitor = requireNotNull(shell[Slot.WORK_PROCESS])
            val head = requireNotNull(shell.headBounds)
            val orbBox = requireNotNull(shell.orbBounds)
            val base = CockpitStageFrame(scene.width.toInt().toFloat(), scene.height.toInt().toFloat(),
                monitor.left - scene.left, monitor.top - scene.top,
                minOf(scene.width.toInt().toFloat(), monitor.right - scene.left),
                minOf(scene.height.toInt().toFloat(), monitor.bottom - scene.top))
            val framing = AvatarFraming.window(focus, head.width / head.height.toDouble(),
                shell.headAnchorX.toDouble(), shell.headCloseUp.toDouble())
            val layout = JSONObject().put("name", name).put("width", width).put("height", height)
                .put("contentHeight", shell.height).put("scene", scene.json()).put("head", head.json())
                .put("orb", orbBox.json()).put("avatarOpacity", CinematicWorkstationLayout.AVATAR_OPACITY)
                .put("avatarWindow", JSONObject().put("left", framing.left).put("right", framing.right)
                    .put("halfHeight", framing.halfHeight).put("cameraCenterY", framing.cameraCenterY))
                .put("avatarFocus", JSONObject().put("centerX", focus.centerX).put("centerZ", focus.centerZ)
                    .put("azimuthDegrees", focus.azimuthDegrees))
            File(out, "$name-layout.json").writeText(layout.toString(2))
            fun draw(filename: String, block: (ContinuumPainter) -> Unit) {
                val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                val graphics = image.createGraphics()
                val painter = Java2DContinuumPainter(graphics, assets)
                block(painter)
                graphics.dispose()
                ImageIO.write(image, "png", File(out, filename))
            }
            val content = shell.boxes.keys.associateWith { slot ->
                JarvisShellRenderer.SlotContent { painter, box ->
                    if (slot == Slot.BOTTOM_NAV) JarvisTiles.drawBottomNav(painter, box,
                        timeMs = 0L, fontScale = shell.profile.fontScale)
                    val text = when (slot) {
                        Slot.TOP_BAR -> "CONTINUUM · HOSTVORSCHAU"
                        Slot.DIALOG -> "3D-ARBEITSPLATZ"
                        Slot.CAMERA_STATUS -> "KAMERA AUS"
                        Slot.COCKPIT_SECONDARY -> "SZENENPRÜFUNG"
                        Slot.INPUT -> "Monitorbewegung · synthetischer Prüfablauf"
                        Slot.STATUS -> "Kein Livebild · keine Gerätemessung"
                        else -> ""
                    }
                    if (text.isNotEmpty()) painter.drawText(
                        JarvisShellRenderer.fit(painter, text, JarvisDesign.Type.caption, box.width - 16f),
                        box.left + 8f, box.top + 20f, JarvisDesign.Type.caption,
                        Fill.Solid(JarvisDesign.Colors.text))
                }
            }
            draw("$name-background.png") { p ->
                JarvisShellRenderer.draw(p, shell, content, elapsedMs = 2_400L, timeMs = 0L,
                    deferForeground = true, luxuryDepth = true, depthBudget = budget)
            }
            draw("$name-foreground.png") { p ->
                JarvisShellRenderer.drawForeground(p, shell, content)
            }
            val mask = BufferedImage(head.width.toInt(), head.height.toInt(), BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until mask.height) for (x in 0 until mask.width) {
                val alpha = (255f * JarvisHeadAura.fadeAlpha(x.toFloat(), y.toFloat(),
                    mask.width.toFloat(), mask.height.toFloat()) * CinematicWorkstationLayout.AVATAR_OPACITY).toInt()
                mask.setRGB(x, y, (alpha shl 24) or 0xffffff)
            }
            ImageIO.write(mask, "png", File(out, "$name-head-mask.png"))
            val forward = CockpitMonitorMotion.flight(0f, 0f, 1f)
            val returning = CockpitMonitorMotion.flight(1f, 0f, 0f)
            check(forward.durationMs == returning.durationMs)
            val duration = forward.durationMs
            val samples = mutableListOf(Sample("idle", 0f, 0f),
                Sample("transition", forward.sample(duration / 2f).focus, duration / 2f),
                Sample("working", 1f, duration))
            if (video && name == "fold") repeat(49) { i ->
                val localTime = (if (i <= 24) i else i - 24) * duration / 24f
                val state = if (i <= 24) forward.sample(localTime) else returning.sample(localTime)
                samples += Sample("sequence-%03d".format(i), state.focus, i * duration / 24f)
            }
            for ((label, progress, time) in samples) {
                val frame = CockpitMonitorMotion.pose(base, progress)
                check(frame.valid())
                val key = "$name-$label"
                draw("$key-orb.png") { p ->
                    p.save()
                    p.translate(orbBox.left, orbBox.top)
                    p.clipRect(0f, 0f, orbBox.width, orbBox.height)
                    val orb = ContinuumHologramRenderer().apply {
                        geometry = ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT
                        showBrand = false
                        layout(orbBox.width, orbBox.height)
                    }
                    orb.draw(p, ContinuumHologramRenderer.Frame.still(
                        if (progress > 0f) ContinuumHologramRenderer.Mode.THINKING else ContinuumHologramRenderer.Mode.READY,
                        time / 1_000f), transparentBackdrop = true)
                    p.restore()
                }
                frames.put(JSONObject().put("key", key).put("layout", name).put("phase", label)
                    .put("focus", progress).put("timeMs", time).put("frame", frame.json()))
            }
        }
        File(out, "scene-export.json").writeText(JSONObject().put("schema", "cockpit-host-review-v1")
            .put("inputClass", "SYNTHETIC_FOCUS_REPLAY")
            .put("motionDurationMs", CockpitMonitorMotion.DURATION_MS)
            .put("videoFramesPerSecond", 24_000.0 / CockpitMonitorMotion.DURATION_MS)
            .put("videoFrameCount", if (video) 49 else 0)
            .put("frames", frames).toString(2))
        println("Exported ${frames.length()} production poses; Java2D layers and original sphere.")
    }
}
