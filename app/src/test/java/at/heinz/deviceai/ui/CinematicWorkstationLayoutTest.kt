package at.heinz.deviceai.ui

import at.heinz.deviceai.ContinuumWorkAreaNavigationSpec
import at.heinz.deviceai.avatar3d.AvatarFraming
import at.heinz.deviceai.avatar3d.AvatarGlbContract
import at.heinz.deviceai.avatar3d.AvatarMorphMapper
import at.heinz.deviceai.avatar3d.CockpitStageFrame
import at.heinz.deviceai.avatar3d.CockpitWorldTransforms
import at.heinz.deviceai.avatar3d.GlbOrbGeometry
import at.heinz.deviceai.avatar3d.GlbSkinningRig
import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import org.junit.Assert.*
import org.junit.Test

class CinematicWorkstationLayoutTest {
    @Test fun originalSphereSurroundsTheSolidPosedHead() {
        val geometry = GlbOrbGeometry()
        val phases = listOf(.4f, 1.2192f, 2.0384f, 3.6768f).associateWith { geometry.vertices(it) }
        for ((w, h) in listOf(412f to 915f, 600f to 800f, 884f to 760f, 1280f to 800f)) {
            val shell = CinematicWorkstationLayout.create(w, h)
            val head = requireNotNull(shell.headBounds)
            val orb = requireNotNull(shell.orbBounds)
            val stage = shell[Slot.AVATAR]!!
            assertTrue(shell.workstation)
            assertEquals(head.centerX, orb.centerX, .01f)
            val window = window(shell)
            val frame = frame(shell)
            val points = facePoints(shell, frame, window)
            val center = head.centerX - stage.left
            val headReach = points.maxOf { kotlin.math.abs(it.first - center) }
            val matrix = CockpitWorldTransforms.orb(frame, orb.left - stage.left, orb.top - stage.top,
                orb.right - stage.left, orb.bottom - stage.top).map(Float::toDouble).toDoubleArray()
            for ((phase, vertices) in phases) {
                val projected = vertices.map { vertex ->
                    val p = GlbOrbGeometry.transform(matrix, vertex.point)
                    vertex.mesh to frame.project(p[0].toFloat(), p[1].toFloat(), p[2].toFloat())
                }
                val sphere = projected.filter { it.first == "orb-containment-shell" }.map { it.second.first }
                assertTrue("Actual sphere surrounds posed skin at $w", sphere.min() < center - headReach * 1.25 &&
                    sphere.max() > center + headReach * 1.25)
                val left = projected.minOf { it.second.first }
                val right = projected.maxOf { it.second.first }
                assertTrue("Actual orb AABB remains in scene at $w/$phase: $left..$right",
                    left >= 0f && right <= stage.width)
            }
            assertTrue("Crown has room above", head.top > stage.top)
            assertEquals("Solid authored skin", 1f, CinematicWorkstationLayout.AVATAR_OPACITY, 0f)
            assertEquals("Pull back once", .76, window.headScale, .00001)
        }
    }

    @Test fun completeMonitorFlightClearsActualPosedFaceAndSideWindows() {
        for ((w, h) in listOf(240f to 500f, 412f to 915f, 600f to 800f,
            884f to 760f, 1280f to 800f)) for (font in listOf(1f, 1.5f, 2f)) {
            val shell = CinematicWorkstationLayout.create(w, h, font)
            val scene = shell[Slot.AVATAR]!!
            val window = window(shell)
            val base = frame(shell)
            val bottom = facePoints(shell, base, window).maxOf { it.second }
            for (step in 0..100) {
                val frame = CockpitMonitorMotion.pose(base, step / 100f)
                val corners = frame.quad(0f, 0f, 1f, 1f)
                val left = listOf(corners[0], corners[6]).min()
                val right = listOf(corners[2], corners[4]).max()
                val top = listOf(corners[1], corners[3]).min()
                assertTrue("Posed chin clearance $w/$font/$step: $top > $bottom", top >= bottom + 6.5)
                assertTrue("Monitor stays in scene", left >= 0f && right <= scene.width)
                shell[Slot.WORK_DOCUMENT]?.let {
                    assertTrue("Left working window stays clear", left > it.right - scene.left)
                }
                shell[Slot.WORK_DRAFT]?.let {
                    assertTrue("Right working window stays clear", right < it.left - scene.left)
                }
            }
        }
    }

    @Test fun compactBadgesRemainReadableAtMaximumFontScale() {
        for (width in listOf(600f, 884f, 1280f)) for (font in listOf(1f, 1.5f, 2f)) {
            val shell = CinematicWorkstationLayout.create(width, 500f, font)
            val dialog = shell[Slot.DIALOG]!!
            val camera = shell[Slot.CAMERA_STATUS]!!
            val system = shell[Slot.COCKPIT_SECONDARY]!!
            assertEquals(74f * font, dialog.height, .001f)
            assertEquals(48f * font, camera.height, .001f)
            assertEquals(48f * font, system.height, .001f)
            assertTrue(camera.bottom + 8f <= system.top + .001f)
            assertTrue(system.bottom + 8f <= shell[Slot.WORK_PROCESS]!!.top + .001f)
            assertTrue(dialog.bottom + 8f <= shell[Slot.WORK_PROCESS]!!.top + .001f)
        }
    }

    @Test fun sideWindowsUseTheSameDeskAndStayAbsentOnPhone() {
        val fold = CinematicWorkstationLayout.create(884f, 760f)
        val scene = fold[Slot.AVATAR]!!
        val main = fold[Slot.WORK_PROCESS]!!
        val left = fold[Slot.WORK_DOCUMENT]!!
        val right = fold[Slot.WORK_DRAFT]!!
        assertEquals(left.width, right.width, .01f)
        assertEquals(left.top, right.top, .01f)
        assertEquals(left.bottom, right.bottom, .01f)
        assertTrue(left.width >= 160f && right.width >= 160f)
        assertTrue(main.width > left.width * 2f)
        assertTrue(scene.bottom - main.bottom >= scene.height * .10f)
        val phone = CinematicWorkstationLayout.create(412f, 915f)
        assertNull(phone[Slot.WORK_DOCUMENT])
        assertNull(phone[Slot.WORK_DRAFT])
        assertTrue(phone[Slot.WORK_PROCESS]!!.width > phone[Slot.AVATAR]!!.width * .90f)
    }

    @Test fun eightNativeDestinationsAreReadableOnPhoneAndFold() {
        for ((w, expectedRows) in listOf(412f to 2, 884f to 1)) {
            val shell = CinematicWorkstationLayout.create(w, 915f)
            val dock = requireNotNull(shell[Slot.BOTTOM_NAV])
            val cells = JarvisTiles.bottomNavItemBoxes(dock)
            assertEquals(8, cells.size)
            assertEquals(expectedRows, cells.map { it.top }.distinct().size)
            assertTrue(cells.all { it.width >= 72f && it.height >= 48f })
            assertEquals(8, JarvisTiles.bottomNav.map { it.target }.distinct().size)
            assertTrue(shell[Slot.INPUT]!!.bottom <= dock.top)
        }
    }

    @Test fun formerDockFunctionsRemainReachable() {
        val routes = ContinuumWorkAreaNavigationSpec.moreEntries.map { it.target }
        assertTrue(routes.containsAll(CockpitGlassButtons.secondaryTargets))
        assertTrue(CinematicWorkstationLayout.create(412f, 915f).headerActionBoxes().containsKey("more"))
    }

    @Test fun resizingAndLargeTextKeepPositiveBoundsAndAllowScrolling() {
        for (width in listOf(240f, 320f, 412f, 600f, 884f, 1280f)) {
            for (font in listOf(1f, 1.5f, 2f)) {
                val shell = CinematicWorkstationLayout.create(width, 500f, font)
                assertTrue(shell.boxes.values.all { it.left >= 0 && it.top >= 0 &&
                    it.right <= width && it.bottom <= shell.height && it.width > 0 && it.height > 0 })
                assertTrue(shell[Slot.WORK_PROCESS]!!.top > shell.headBounds!!.top)
                assertTrue(CockpitGlassButtons.baseLabelStyle(font).size >= 12.5f)
            }
        }
    }

    private fun window(shell: JarvisShellLayout.Shell): AvatarFraming.Window {
        val head = shell.headBounds!!
        val dialog = shell[Slot.DIALOG]!!
        val keepOut = if (dialog.right <= head.left || dialog.left >= head.right ||
            dialog.bottom <= head.top || dialog.top >= head.bottom) null
        else AvatarFraming.KeepOut(((dialog.right + 6f - head.left) / head.width).toDouble(),
            ((dialog.top - 6f - head.top) / head.height).toDouble())
        return AvatarFraming.pullBack(AvatarFraming.window(focus, head.width.toDouble() / head.height,
            shell.headAnchorX.toDouble(), shell.headCloseUp.toDouble(), keepOut),
            CinematicWorkstationLayout.AVATAR_SCALE.toDouble())
    }

    private fun frame(shell: JarvisShellLayout.Shell): CockpitStageFrame {
        val scene = shell[Slot.AVATAR]!!
        val monitor = shell[Slot.WORK_PROCESS]!!
        return CockpitStageFrame(scene.width, scene.height,
            monitor.left - scene.left, monitor.top - scene.top,
            monitor.right - scene.left, monitor.bottom - scene.top)
    }

    private fun facePoints(shell: JarvisShellLayout.Shell, frame: CockpitStageFrame,
                           window: AvatarFraming.Window): List<Pair<Float, Float>> {
        val scene = shell[Slot.AVATAR]!!
        val head = shell.headBounds!!
        val matrix = CockpitWorldTransforms.avatar(frame, head.left - scene.left, head.top - scene.top,
            head.right - scene.left, head.bottom - scene.top, window, focus)
        return posedFaces.map { p ->
            frame.project((matrix[0] * p[0] + matrix[4] * p[1] + matrix[8] * p[2] + matrix[12]).toFloat(),
                (matrix[1] * p[0] + matrix[5] * p[1] + matrix[9] * p[2] + matrix[13]).toFloat(),
                (matrix[2] * p[0] + matrix[6] * p[1] + matrix[10] * p[2] + matrix[14]).toFloat())
        }
    }

    companion object {
        private val rig = GlbSkinningRig.packaged()
        private val focus = AvatarGlbContract.validate(rig.bytes).focus
        private val chin = rig.translation("Neck")[1] + rig.translation("Head")[1] * .70
        private val rest = rig.skinned(emptyMap())
        private val posedFaces = GlbSkinningRig.fullRangePoses().flatMap { (yaw, pitch) ->
            val posed = rig.turn(yaw, pitch, AvatarMorphMapper.NECK_SHARE.toDouble())
            posed.indices.flatMap { mesh ->
                (0 until posed[mesh].size / 3).filter { rest[mesh][it * 3 + 1] >= chin }.map {
                    doubleArrayOf(posed[mesh][it * 3], posed[mesh][it * 3 + 1], posed[mesh][it * 3 + 2])
                }
            }
        }
    }
}
