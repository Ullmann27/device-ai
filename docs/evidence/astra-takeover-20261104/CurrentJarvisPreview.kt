package at.heinz.deviceai.ui

import at.heinz.deviceai.avatar.AvatarPoseReducer
import at.heinz.deviceai.avatar.ContinuumAvatarRenderer
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/** Production component preview of e19df153. This is not an Android Activity or GPU capture. */
object CurrentJarvisPreview {
    @JvmStatic fun main(args: Array<String>) {
        val root = File(args[0])
        val output = File(args[1]).also { it.parentFile.mkdirs() }
        val width = 884f
        val height = 760f
        val density = 2f
        val railWidth = (JarvisMenuRail.widthDp(width, height) * density).roundToInt() / density
        val image = BufferedImage((width * density).toInt(), (height * density).toInt(), BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        val painter = Java2DContinuumPainter(graphics, File(root, "app/src/main/assets"))
        painter.scale(density, density)
        val state = ContinuumDashboardState()
        JarvisShellRenderer.background(painter, width, height)
        JarvisMenuRailElement(device = JarvisDeviceStatus.EMPTY).draw(painter, railWidth, height, state)
        painter.save()
        painter.translate(railWidth, 0f)
        val shell = JarvisShellLayout.forContent(width - railWidth, height, externalRailWidthDp = railWidth)
        val grid = requireNotNull(shell.grid)
        val stage = requireNotNull(shell[JarvisShellLayout.Slot.AVATAR])
        val contents = JarvisShellContent.forState({ state }, { 0L }, grid = { shell.grid }).toMutableMap()
        contents[JarvisShellLayout.Slot.AVATAR] = JarvisShellRenderer.SlotContent { _, _ -> }
        JarvisShellRenderer.draw(painter, shell, contents, animatorScale = 0f,
            tiles = JarvisShellContent.tiles(state, 0L, subtitles = true), timeMs = 0L)

        // Match ContinuumHomeView.updateCockpitRegions pixel truncation and live child placement.
        val originX = (stage.left * density).toInt()
        val originY = (stage.top * density).toInt()
        fun local(area: JarvisCockpitGrid.Area): IntArray = grid[area].let {
            intArrayOf((it.left * density).toInt() - originX, (it.top * density).toInt() - originY,
                (it.right * density).toInt() - originX, (it.bottom * density).toInt() - originY)
        }
        val head = local(JarvisCockpitGrid.Area.HEAD)
        val owner = local(JarvisCockpitGrid.Area.USER_CARD)
        head[0] = maxOf(head[0], owner[2] + (6f * grid.unit * density).toInt())
        val orb = local(JarvisCockpitGrid.Area.ORB)
        val stageWidth = ((stage.right * density).toInt() - originX) / density
        val stageHeight = ((stage.bottom * density).toInt() - originY) / density
        painter.save()
        painter.translate(originX / density, originY / density)
        painter.clipRect(0f, 0f, stageWidth, stageHeight)
        // Same existing Canvas head fallback and original brain orb as ContinuumHologramView.onDraw.
        painter.save()
        painter.translate(head[0] / density, head[1] / density)
        ContinuumAvatarRenderer().draw(painter, (head[2] - head[0]) / density, (head[3] - head[1]) / density,
            AvatarPoseReducer().pose(0L), 0f, false)
        painter.restore()
        painter.save()
        painter.translate(orb[0] / density, orb[1] / density)
        val orbWidth = (orb[2] - orb[0]) / density
        val orbHeight = (orb[3] - orb[1]) / density
        painter.clipRect(0f, 0f, orbWidth, orbHeight)
        ContinuumHologramRenderer().apply {
            geometry = ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT
            showBrand = false
            layout(orbWidth, orbHeight)
        }.draw(painter, ContinuumHologramRenderer.Frame.still(ContinuumHologramRenderer.Mode.READY, 0f))
        painter.restore()
        painter.drawText("JARVIS", (head[0] + head[2]) / (2f * density),
            (head[1] + (head[3] - head[1]) * .96f) / density,
            TextStyle(FontFamily.DISPLAY, 700, 14f, letterSpacing = 2f), Fill.Solid(0xff9aefff.toInt()), TextAlign.CENTER)
        painter.restore()
        painter.restore()
        graphics.dispose()
        check(ImageIO.write(image, "png", output))
        println("Rendered $output")
        println("884x760dp at 2x; rail=$railWidth; stage=$stage; headPx=${head.contentToString()}; orbPx=${orb.contentToString()}")
    }
}
