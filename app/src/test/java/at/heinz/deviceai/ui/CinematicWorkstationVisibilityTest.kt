package at.heinz.deviceai.ui

import at.heinz.deviceai.avatar3d.AvatarFraming
import at.heinz.deviceai.avatar3d.AvatarGlbContract
import at.heinz.deviceai.avatar3d.CockpitStageFrame
import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CinematicWorkstationVisibilityTest {
    @Test fun posedFaceAndCameraDriftClearFocusedMonitor() {
        for (width in listOf(240f, 320f, 360f, 412f, 600f, 640f, 700f, 720f, 884f, 1280f)) {
            for (height in listOf(500f, 760f, 960f)) for (font in listOf(1f, 1.5f, 2f)) {
                val shell = CinematicWorkstationLayout.create(width, height, font)
                val scene = requireNotNull(shell[Slot.AVATAR])
                val head = requireNotNull(shell.headBounds)
                val window = AvatarFraming.pullBack(AvatarFraming.window(focus, head.width / head.height.toDouble(),
                    shell.headAnchorX.toDouble(), shell.headCloseUp.toDouble()), CinematicWorkstationLayout.AVATAR_SCALE.toDouble())
                val faceBottom = head.top + head.height *
                    (window.cameraCenterY + window.halfHeight - requireNotNull(focus.face).bottom) /
                    (2.0 * window.halfHeight)
                for (frame in endpointFrames(shell)) {
                    val border = frame.quad(-.009f, -.009f, 1.009f, 1.009f)
                    val monitorTop = scene.top + minOf(border[1], border[3])
                    assertTrue("Face clipped: $width/$height/$font at $monitorTop < $faceBottom",
                        monitorTop >= faceBottom + 7.5)
                }
            }
        }
    }

    @Test fun packagedFootFitsInsideStageAtBothFocusEndpoints() {
        val bytes = asset("cinema3d/cockpit-workstation.glb").readBytes()
        val length = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(12)
        val nodes = JSONObject(String(bytes, 20, length, Charsets.UTF_8)).getJSONArray("nodes")
        val foot = (0 until nodes.length()).map(nodes::getJSONObject)
            .first { it.optString("name") == "stand-foot" }
        val position = foot.getJSONArray("translation")
        val scale = foot.getJSONArray("scale")
        val left = (.5 + position.getDouble(0) - scale.getDouble(0) * .5).toFloat()
        val right = (.5 + position.getDouble(0) + scale.getDouble(0) * .5).toFloat()
        val top = (.5 - position.getDouble(1) - scale.getDouble(1) * .5).toFloat()
        val bottom = (.5 - position.getDouble(1) + scale.getDouble(1) * .5).toFloat()
        for ((width, height) in listOf(320f to 500f, 360f to 760f, 412f to 960f, 600f to 500f,
            640f to 500f, 700f to 500f, 720f to 500f, 884f to 760f, 1280f to 800f)) {
            val shell = CinematicWorkstationLayout.create(width, height)
            val scene = requireNotNull(shell[Slot.AVATAR])
            for (frame in endpointFrames(shell)) {
                val bounds = frame.quad(left, top, right, bottom)
                assertTrue("Foot clipped: $width/$height", bounds.filterIndexed { i, _ -> i % 2 == 1 }
                    .all { it < scene.height })
            }
        }
    }

    @Test fun largeTextKeepsSidePanelsSeparate() {
        for (width in listOf(600f, 640f, 700f, 720f, 884f)) {
            for (font in listOf(1f, 1.5f, 2f)) {
                val shell = CinematicWorkstationLayout.create(width, 500f, font)
                assertNotNull(shell[Slot.COCKPIT_SECONDARY])
                assertTrue(shell[Slot.DIALOG]!!.bottom <= shell[Slot.WORK_DOCUMENT]!!.top)
            }
        }
    }

    @Test fun ordinaryViewportsDoNotGrow() {
        assertEquals(760f, CinematicWorkstationLayout.create(884f, 760f).height, .01f)
        assertEquals(960f, CinematicWorkstationLayout.create(412f, 960f).height, .01f)
    }

    @Test fun protectedRegionContainsOriginalPosedFace() {
        for (width in listOf(240f, 320f, 360f, 412f, 600f, 640f, 700f, 720f, 884f, 1280f)) {
            for (height in listOf(500f, 760f, 960f)) {
                val shell = CinematicWorkstationLayout.create(width, height)
                val head = requireNotNull(shell.headBounds)
                val safe = requireNotNull(shell.protectedFaceBounds)
                val window = AvatarFraming.pullBack(AvatarFraming.window(focus, head.width / head.height.toDouble(),
                    shell.headAnchorX.toDouble(), shell.headCloseUp.toDouble()), CinematicWorkstationLayout.AVATAR_SCALE.toDouble())
                val span = window.right - window.left
                val left = head.left + (-focus.reachLeft - window.left) / span * head.width
                val right = head.left + (focus.reachRight - window.left) / span * head.width
                val bottom = head.top + head.height *
                    (window.cameraCenterY + window.halfHeight - requireNotNull(focus.face).bottom) /
                    (2.0 * window.halfHeight)
                assertTrue(safe.left >= 0f && safe.right <= width)
                assertTrue(safe.left <= left && safe.right >= right)
                assertTrue(safe.bottom >= bottom + 6.5)
            }
        }
    }

    @Test fun footContactsGlassAtBothFocusEndpoints() {
        val bytes = asset("cinema3d/cockpit-workstation.glb").readBytes()
        val length = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(12)
        val array = JSONObject(String(bytes, 20, length, Charsets.UTF_8)).getJSONArray("nodes")
        val nodes = (0 until array.length()).map(array::getJSONObject).associateBy { it.optString("name") }
        fun edge(name: String, sign: Double): Double {
            val node = nodes.getValue(name)
            return node.getJSONArray("translation").getDouble(1) +
                sign * node.getJSONArray("scale").getDouble(1) * .5
        }
        val glassTop = edge("desk-glass", 1.0)
        val footBottom = edge("stand-foot", -1.0)
        assertEquals(edge("stand", -1.0), edge("stand-foot", 1.0), .000001)
        for ((width, height) in listOf(320f to 500f, 360f to 760f, 412f to 960f, 600f to 500f,
            640f to 500f, 700f to 500f, 720f to 500f, 884f to 760f, 1280f to 800f)) {
            val shell = CinematicWorkstationLayout.create(width, height)
            for (frame in endpointFrames(shell)) {
                val centerY = (frame.height - frame.monitorTop - frame.monitorBottom) * .5
                val contactY = centerY + footBottom * frame.monitorHeight
                assertEquals("Table contact: $width/$height", glassTop * frame.height, contactY, 1.0)
            }
        }
    }

    private fun endpointFrames(shell: JarvisShellLayout.Shell): List<CockpitStageFrame> {
        val scene = requireNotNull(shell[Slot.AVATAR])
        val monitor = requireNotNull(shell[Slot.WORK_PROCESS])
        val base = CockpitStageFrame(scene.width, scene.height,
            monitor.left - scene.left, monitor.top - scene.top,
            monitor.right - scene.left, monitor.bottom - scene.top)
        val focused = CockpitMonitorMotion.pose(base, 1f)
        return listOf(base, focused)
    }

    private companion object {
        fun asset(path: String): File = sequenceOf(File("src/main/assets", path),
            File("app/src/main/assets", path)).first(File::isFile)
        val focus by lazy { AvatarGlbContract.validate(asset(AvatarGlbContract.ASSET_PATH).readBytes()).focus }
    }
}
