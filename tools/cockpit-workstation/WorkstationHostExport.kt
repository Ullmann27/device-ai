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
    private fun Box.viewBox() = Box(left.toInt().toFloat(), top.toInt().toFloat(),
        right.toInt().toFloat(), bottom.toInt().toFloat())

    private fun CockpitStageFrame.json() = JSONObject().put("width", width).put("height", height)
        .put("monitorLeft", monitorLeft).put("monitorTop", monitorTop)
        .put("monitorRight", monitorRight).put("monitorBottom", monitorBottom)
        .put("yawDegrees", yawDegrees).put("pitchDegrees", pitchDegrees)
        .put("depthOffset", depthOffset).put("distance", distance)
        .put("quad", JSONArray(quad().toList()))
        .put("contentBounds", JSONArray(listOf(CockpitStageFrame.CONTENT_LEFT, CockpitStageFrame.CONTENT_TOP,
            CockpitStageFrame.CONTENT_RIGHT, CockpitStageFrame.CONTENT_BOTTOM)))
        .put("contentDepth", CockpitStageFrame.CONTENT_DEPTH)

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 4)
        val root = File(args[0])
        val out = File(args[1]).apply { mkdirs() }
        val video = args[2] == "video"
        val draft = args[2] == "draft"
        val example = args[3] == "example"
        val fps = 20
        val videoDurationMs = 3_000f
        val videoFrames = (videoDurationMs * fps / 1_000f).toInt()
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
            val head = requireNotNull(shell.headBounds).viewBox()
            val orbBox = requireNotNull(shell.orbBounds).viewBox()
            val base = CockpitStageFrame(scene.width.toInt().toFloat(), scene.height.toInt().toFloat(),
                monitor.left - scene.left, monitor.top - scene.top,
                minOf(scene.width.toInt().toFloat(), monitor.right - scene.left),
                minOf(scene.height.toInt().toFloat(), monitor.bottom - scene.top))
            val framing = AvatarFraming.pullBack(
                AvatarFraming.window(focus, head.width / head.height.toDouble(),
                    shell.headAnchorX.toDouble(), shell.headCloseUp.toDouble()),
                CinematicWorkstationLayout.AVATAR_SCALE.toDouble())
            val slots = JSONObject()
            shell.boxes.forEach { (slot, box) -> slots.put(slot.name, box.json()) }
            val layout = JSONObject().put("name", name).put("width", width).put("height", height)
                .put("contentHeight", shell.height).put("scene", scene.json()).put("head", head.json())
                .put("orb", orbBox.json()).put("avatarOpacity", CinematicWorkstationLayout.AVATAR_OPACITY)
                .put("avatarPullBackScale", CinematicWorkstationLayout.AVATAR_SCALE)
                .put("slots", slots).put("sideMonitorsVisible", base.monitorWidth / base.width <= .70f)
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
            val content = shell.boxes.keys.filter { it != Slot.BOTTOM_NAV }.associateWith { slot ->
                JarvisShellRenderer.SlotContent { painter, box ->
                    when (slot) {
                        Slot.TOP_BAR -> {
                            CockpitWorkstationChrome.header(painter, box, shell.headerActionBoxes(),
                                notifications = 0, fontScale = shell.profile.fontScale)
                            return@SlotContent
                        }
                        Slot.INPUT -> {
                            CockpitWorkstationChrome.idleInput(painter, box, requireNotNull(shell.inputConversationBox()),
                                shell.dialogControlBoxes(), listening = false)
                            return@SlotContent
                        }
                        Slot.WORK_DOCUMENT -> {
                            CockpitWorkstationChrome.panelHeader(painter, box, "Dokument", ContinuumIcons.Icon.DOCUMENT)
                            if (example) HostExampleContent.files(painter, box)
                            return@SlotContent
                        }
                        Slot.WORK_DRAFT -> {
                            CockpitWorkstationChrome.panelHeader(painter, box, "Entwurf", ContinuumIcons.Icon.CODE)
                            if (example) HostExampleContent.result(painter, box)
                            return@SlotContent
                        }
                        else -> Unit
                    }
                    val text = when (slot) {
                        Slot.DIALOG -> "Device AI · Continuum"
                        Slot.CAMERA_STATUS -> "KAMERA AUS"
                        Slot.COCKPIT_SECONDARY -> "VORSCHAU"
                        Slot.STATUS -> if (example) "VORSCHAU · BEISPIELINHALT" else "HOSTVORSCHAU · kein Livebild"
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
            draw("$name-head-front.png") { p ->
                AvatarPresentation.drawFront(p, head, 0f, .18f, layered = true)
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
            val samples = mutableListOf(Sample("idle", 0f, 0f))
            if (!draft) samples += listOf(
                Sample("transition", forward.sample(duration / 2f).focus, duration / 2f),
                Sample("working", 1f, duration))
            if (video && name == "fold") repeat(videoFrames) { i ->
                val time = i * 1_000f / fps
                val departure = 500f
                val returningAt = videoDurationMs - departure - duration
                val state = when {
                    time < departure -> 0f
                    time < departure + duration -> forward.sample(time - departure).focus
                    time < returningAt -> 1f
                    time < returningAt + duration -> returning.sample(time - returningAt).focus
                    else -> 0f
                }
                samples += Sample("sequence-%03d".format(i), state, time)
            }
            for ((label, progress, time) in samples) {
                val frame = CockpitMonitorMotion.pose(base, progress)
                check(frame.valid())
                val key = "$name-$label"
                val energy = ContinuumHologramRenderer.Frame.energyOf(ContinuumHologramRenderer.Mode.READY)
                val orbFrame = ContinuumHologramRenderer.Frame.still(ContinuumHologramRenderer.Mode.READY,
                    HostOrbClock.sample(time / 1_000f, energy))
                draw("$key-orb.png") { p ->
                    p.save()
                    val flowTop = scene.height * .62f
                    p.clipRect(scene.left, scene.top + flowTop, scene.right, scene.bottom)
                    p.translate(scene.left, scene.top + flowTop)
                    JarvisEnergyFlows.draw(p, scene.width, scene.height - flowTop, time / 1_000f, orbFrame.energy)
                    p.restore()
                    JarvisHeadAura.draw(p, head, framing.anchorX.toFloat(), time / 1_000f,
                        headScale = framing.headScale.toFloat(), activityEnergy = orbFrame.energy,
                        referenceMode = true)
                    AvatarPresentation.drawBack(p, head, time / 1_000f, orbFrame.energy, layered = true)
                    p.save()
                    p.translate(orbBox.left, orbBox.top)
                    p.clipRect(0f, 0f, orbBox.width, orbBox.height)
                    val orb = ContinuumHologramRenderer().apply {
                        geometry = ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT
                        showBrand = AvatarPresentation.showOrbBrand(layered = true, headEnabled = true)
                        layout(orbBox.width, orbBox.height)
                    }
                    orb.draw(p, orbFrame, transparentBackdrop = true)
                    p.restore()
                }
                if (example) {
                    val cw = frame.monitorWidth * (CockpitStageFrame.CONTENT_RIGHT - CockpitStageFrame.CONTENT_LEFT)
                    val ch = frame.monitorHeight * (CockpitStageFrame.CONTENT_BOTTOM - CockpitStageFrame.CONTENT_TOP)
                    val image = BufferedImage((cw * 2).toInt(), (ch * 2).toInt(), BufferedImage.TYPE_INT_ARGB)
                    val graphics = image.createGraphics()
                    val painter = Java2DContinuumPainter(graphics, assets)
                    painter.scale(2f, 2f)
                    HostExampleContent.document(painter, cw, ch)
                    graphics.dispose()
                    ImageIO.write(image, "png", File(out, "$key-example.png"))
                }
                frames.put(JSONObject().put("key", key).put("layout", name).put("phase", label)
                    .put("focus", progress).put("timeMs", time).put("orbPhase", orbFrame.phase)
                    .put("frame", frame.json()))
            }
        }
        File(out, "scene-export.json").writeText(JSONObject().put("schema", "cockpit-host-review-v1")
            .put("inputClass", "SYNTHETIC_FOCUS_REPLAY").put("contentMode", if (example) "example" else "empty")
            .put("motionDurationMs", CockpitMonitorMotion.DURATION_MS)
            .put("videoFramesPerSecond", fps).put("videoDurationMs", if (video) videoDurationMs else 0f)
            .put("videoFrameCount", if (video) videoFrames else 0)
            .put("frames", frames).toString(2))
        println("Exported ${frames.length()} production poses; Java2D layers and original sphere.")
    }
}
