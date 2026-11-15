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
    @Test fun conversationStripContainsOriginalPosedFaceAndCompleteRestRings() {
        val vertices = GlbOrbGeometry().vertices(.4f)
        for (width in listOf(240f, 320f, 392f, 600f, 904f, 1280f)) {
            for (visibleHeight in listOf(24f, 128f, 260f)) {
                val shell = CinematicWorkstationLayout.conversationPresence(width, visibleHeight)
                val scene = shell[Slot.AVATAR]!!
                val head = shell.headBounds!!
                val orb = shell.orbBounds!!
                val frame = frame(shell)
                assertTrue(frame.valid())
                assertEquals(width * .5f, head.centerX, .001f)
                assertEquals(head.centerX, orb.centerX, .001f)
                val points = facePoints(shell, frame, window(shell))
                assertTrue("Original posed face remains in chat strip $width/$visibleHeight",
                    points.all { it.first >= 0f && it.first <= scene.width &&
                        it.second >= 0f && it.second <= visibleHeight })
                val matrix = CockpitWorldTransforms.orb(frame, orb.left, orb.top, orb.right, orb.bottom)
                    .map(Float::toDouble).toDoubleArray()
                val projected = vertices.map { vertex ->
                    val p = GlbOrbGeometry.transform(matrix, vertex.point)
                    frame.project(p[0].toFloat(), p[1].toFloat(), p[2].toFloat())
                }
                val top = projected.minOf { it.second }
                val bottom = projected.maxOf { it.second }
                assertTrue("Original rest rings fit chat strip $width/$visibleHeight: $top..$bottom",
                    projected.all { it.first >= 0f && it.first <= scene.width } &&
                        top >= 0f && bottom <= visibleHeight)
            }
        }
    }

    @Test fun conversationPresenceRetainsAValidMonitorFrameWithoutGhostControls() {
        for (width in profiles.map { it.first }) for (font in listOf(1f, 1.3f, 2f)) {
            for (height in listOf(24f, 96f, 128f, 220f, 260f, 320f)) {
                val shell = CinematicWorkstationLayout.conversationPresence(width, height, font)
                assertEquals(setOf(Slot.AVATAR, Slot.WORK_PROCESS), shell.boxes.keys)
                assertTrue(shell.workstation)
                assertNull(shell.grid)
                assertNull(shell.rail)
                assertTrue(shell.tileBoxes().isEmpty())
                assertTrue(shell.headerActionBoxes().isEmpty())
                assertTrue(shell.dialogControlBoxes().isEmpty())
                assertNull(shell.inputConversationBox())
                assertNull(JarvisShellContent.hitTarget(shell, JarvisTiles.reference,
                    width * .5f, height * .5f))
                val monitor = shell[Slot.WORK_PROCESS]!!
                assertTrue("Monitor stays below visible chat presence", monitor.top > height)
                assertTrue("Full native scene remains valid", frame(shell).valid())
                assertTrue(monitor.bottom <= shell.height)
                assertTrue(shell.headBounds!!.bottom <= height)
                assertEquals(height, shell.viewportHeight, 0f)
            }
        }
    }

    @Test fun originalSphereSurroundsTheSolidPosedHead() {
        val geometry = GlbOrbGeometry()
        // Command03 deliberately presents the unchanged authored orb in its fixed rest pose.
        val phases = listOf(.4f).associateWith { geometry.vertices(it) }
        for ((w, h) in profiles) for (font in listOf(1f, 1.3f, 2f)) {
            val shell = CinematicWorkstationLayout.create(w, h, font)
            val head = requireNotNull(shell.headBounds)
            val orb = requireNotNull(shell.orbBounds)
            val stage = shell[Slot.AVATAR]!!
            assertTrue(shell.workstation)
            assertEquals(head.centerX, orb.centerX, .01f)
            assertEquals("Presence is centered in the remaining content pane", stage.centerX, head.centerX, .01f)
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
                assertTrue("Actual sphere surrounds posed skin at $w/$h/$font: " +
                    "${sphere.min()}..${sphere.max()}, center=$center, required reach=${headReach * 1.25}",
                    sphere.min() < center - headReach * 1.25 &&
                    sphere.max() > center + headReach * 1.25)
                val left = projected.minOf { it.second.first }
                val right = projected.maxOf { it.second.first }
                assertTrue("Actual orb AABB remains in scene at $w/$h/$font/$phase: $left..$right of ${stage.width}",
                    left >= 0f && right <= stage.width)
                val top = projected.minOf { it.second.second }
                val bottom = projected.maxOf { it.second.second }
                assertTrue("Complete rest rings remain visible at $w/$h/$font: $top..$bottom of ${stage.height}",
                    top >= 0f && bottom <= stage.height)
            }
            assertTrue("Crown has room above", head.top > stage.top)
            assertEquals("Solid authored skin", 1f, CinematicWorkstationLayout.AVATAR_OPACITY, 0f)
            assertEquals("Single shoulder fit keeps the approved larger head", .90, window.headScale, .00001)
        }
    }

    @Test fun completeMonitorFlightClearsActualPosedFaceAndSideWindows() {
        for ((w, h) in profiles) for (font in listOf(1f, 1.3f, 2f)) {
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
                val lower = listOf(corners[5], corners[7]).max()
                assertTrue("Posed chin clearance $w/$font/$step: $top > $bottom", top >= bottom + 6.5)
                assertTrue("Monitor stays in scene at $w/$h/$font/$step: " +
                    "$left..$right / $top..$lower of ${scene.width} x ${scene.height}",
                    left >= 0f && right <= scene.width && top >= 0f && lower <= scene.height)
                shell[Slot.WORK_DOCUMENT]?.let {
                    assertTrue("Left instrument clears the entire flight at $w/$font/$step",
                        left > it.right - scene.left || top >= it.bottom - scene.top + 6.5f ||
                            lower <= it.top - scene.top - 6.5f)
                }
                shell[Slot.WORK_DRAFT]?.let {
                    assertTrue("Right instrument clears the entire flight at $w/$font/$step",
                        right < it.left - scene.left || top >= it.bottom - scene.top + 6.5f ||
                            lower <= it.top - scene.top - 6.5f)
                }
            }
        }
    }

    @Test fun compactBadgesRemainReadableAtMaximumFontScale() {
        for (width in listOf(600f, 768f, 904f, 1280f)) for (font in listOf(1f, 1.3f, 2f)) {
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

    @Test fun instrumentsFlankLargeStagesAndReflowBelowTheHeadOnSmallFold() {
        val fold = CinematicWorkstationLayout.create(904f, 760f)
        val scene = fold[Slot.AVATAR]!!
        val main = fold[Slot.WORK_PROCESS]!!
        val left = fold[Slot.WORK_DOCUMENT]!!
        val right = fold[Slot.WORK_DRAFT]!!
        assertEquals(left.width, right.width, .01f)
        assertEquals(left.top, right.top, .01f)
        assertEquals(left.bottom, right.bottom, .01f)
        assertTrue(left.width >= 120f && right.width >= 120f)
        assertTrue(left.height >= 208f && right.height >= 208f)
        assertTrue(main.left > left.right && main.right < right.left)
        assertTrue(scene.bottom - main.bottom >= 20f - .001f)
        for (width in listOf(600f, 768f)) {
            val smallFold = CinematicWorkstationLayout.create(width, 800f)
            val document = smallFold[Slot.WORK_DOCUMENT]!!
            val draft = smallFold[Slot.WORK_DRAFT]!!
            assertTrue(document.top > smallFold.headBounds!!.bottom)
            assertEquals(document.top, draft.top, .01f)
            assertTrue(document.width >= 120f && draft.width >= 120f)
            assertTrue(document.height >= 208f && draft.height >= 208f)
            assertTrue(smallFold[Slot.WORK_PROCESS]!!.top > document.bottom)
        }
        val phone = CinematicWorkstationLayout.create(392f, 850f)
        val document = phone[Slot.WORK_DOCUMENT]!!
        val draft = phone[Slot.WORK_DRAFT]!!
        assertTrue(document.top > phone.headBounds!!.bottom)
        assertEquals(document.top, draft.top, .01f)
        assertTrue(document.width >= 120f && draft.width >= 120f)
        assertTrue(phone[Slot.WORK_PROCESS]!!.width > phone[Slot.AVATAR]!!.width * .90f)
        val accessiblePhone = CinematicWorkstationLayout.create(320f, 500f, 2f)
        val first = accessiblePhone[Slot.WORK_DOCUMENT]!!
        val second = accessiblePhone[Slot.WORK_DRAFT]!!
        assertTrue(second.top > first.bottom)
        assertEquals(accessiblePhone[Slot.AVATAR]!!.width, first.width, .01f)
        assertEquals(first.width, second.width, .01f)
        assertTrue(accessiblePhone[Slot.WORK_PROCESS]!!.top > second.bottom)
    }

    @Test fun sevenNativeDestinationsSitAboveTheSceneWithSharedTouchGeometry() {
        for ((w, expectedRows) in listOf(392f to 2, 904f to 1)) {
            val shell = CinematicWorkstationLayout.create(w, 915f)
            val dock = requireNotNull(shell[Slot.BOTTOM_NAV])
            val cells = JarvisTiles.bottomNavItemBoxes(dock)
            assertEquals(7, cells.size)
            assertEquals(expectedRows, cells.map { it.top }.distinct().size)
            assertTrue(cells.all { it.width >= 72f && it.height >= 48f })
            assertEquals(listOf("chat", "files", "research", "agents", "memory", "device", "settings"),
                JarvisTiles.bottomNav.map { it.target })
            assertTrue(shell[Slot.TOP_BAR]!!.bottom <= dock.top)
            assertTrue(dock.bottom < shell[Slot.AVATAR]!!.top)
            assertTrue(shell[Slot.STATUS]!!.top > shell[Slot.AVATAR]!!.bottom)
            assertNull("One real Android composer stays outside the scrolling scene", shell[Slot.INPUT])
            assertNull(shell.inputConversationBox())
            assertTrue(shell.dialogControlBoxes().isEmpty())
        }
    }

    @Test fun formerDockFunctionsRemainReachable() {
        val routes = ContinuumWorkAreaNavigationSpec.moreEntries.map { it.target }
        assertTrue(routes.containsAll(CockpitGlassButtons.secondaryTargets))
        assertTrue(CinematicWorkstationLayout.create(412f, 915f).headerActionBoxes().containsKey("more"))
    }

    @Test fun resizingAndLargeTextKeepPositiveBoundsAndAllowScrolling() {
        for (width in profiles.map { it.first }) {
            for (font in listOf(1f, 1.3f, 2f)) for (height in listOf(260f, 500f, 900f)) {
                val shell = CinematicWorkstationLayout.create(width, height, font)
                assertTrue(shell.boxes.values.all { it.left >= 0 && it.top >= 0 &&
                    it.right <= width && it.bottom <= shell.height && it.width > 0 && it.height > 0 })
                assertTrue(shell[Slot.WORK_PROCESS]!!.top > shell.headBounds!!.top)
                assertTrue(CockpitGlassButtons.baseLabelStyle(font).size >= 12.5f)
                if (height <= 500f || font == 2f) assertTrue("Readable content scrolls instead of shrinking", shell.requiresVerticalScroll)
                val rail = shell[Slot.NAV_RAIL]
                assertEquals(width >= 600f, rail != null)
                rail?.let {
                    assertEquals(CinematicWorkstationLayout.chatRailWidth(width, font), it.width, .001f)
                    assertTrue(it.width in 140f..180f)
                    assertTrue(shell[Slot.TOP_BAR]!!.left >= it.right + 8f - .001f)
                    assertTrue(shell[Slot.AVATAR]!!.left >= it.right + 8f - .001f)
                }
            }
        }
    }

    @Test fun compactWorkstationKeepsShorterGeometryAndAllNativeRoutes() {
        fun overlaps(a: JarvisShellLayout.Box, b: JarvisShellLayout.Box) =
            a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
        for (width in profiles.map { it.first }) {
            for (height in listOf(260f, 760f, 1050f)) for (font in listOf(1f, 1.3f, 2f)) {
                val profile = "$width x $height font=$font"
                val regular = JarvisShellLayout.forContent(width, height, font, workstation = true)
                val compact = JarvisShellLayout.forContent(width, height, font,
                    compactPresentation = true, workstation = true)
                val sphere = requireNotNull(compact[Slot.AVATAR])
                assertTrue("$profile retains workstation navigation", compact.workstation)
                assertTrue("$profile content becomes shorter", compact.height < regular.height)
                assertTrue("$profile original sphere shrinks", sphere.height < regular[Slot.AVATAR]!!.height)
                assertEquals("$profile sphere is the actual renderer slot", compact.orbBounds, sphere)
                assertNull(compact.headBounds)
                assertNull(compact.headStage)
                assertNull(compact.protectedFaceBounds)
                assertNull(compact[Slot.WORK_PROCESS])
                assertNull(compact[Slot.TILES])
                assertNull(compact[Slot.INPUT])
                assertTrue("$profile no painted or invisible second composer", compact.dialogControlBoxes().isEmpty())
                assertEquals(regular[Slot.TOP_BAR], compact[Slot.TOP_BAR])
                assertEquals(regular[Slot.BOTTOM_NAV]!!.height, compact[Slot.BOTTOM_NAV]!!.height, .001f)
                val boxes = compact.boxes.values.toList()
                assertTrue("$profile content bounds", boxes.all { it.left >= 0f && it.top >= 0f &&
                    it.right <= width && it.bottom <= compact.height && it.width > 0f && it.height > 0f })
                for (i in boxes.indices) for (j in i + 1 until boxes.size)
                    assertFalse("$profile compact slots overlap", overlaps(boxes[i], boxes[j]))

                val dock = JarvisTiles.bottomNavItemBoxes(compact[Slot.BOTTOM_NAV]!!)
                assertEquals("$profile seven direct routes", 7, dock.size)
                assertTrue("$profile More remains reachable", compact.headerActionBoxes().containsKey("more"))
                val controls = compact.headerActionBoxes().values + compact.dialogControlBoxes() + dock +
                    listOfNotNull(compact[Slot.DIALOG], compact.inputConversationBox(),
                        compact[Slot.STATUS], compact[Slot.CAMERA_STATUS], compact[Slot.COCKPIT_SECONDARY])
                assertTrue("$profile minimum touch sizes", controls.all { it.width >= 48f && it.height >= 48f })
                for (i in controls.indices) for (j in i + 1 until controls.size)
                    assertFalse("$profile touch controls overlap", overlaps(controls[i], controls[j]))
                assertTrue(ContinuumWorkAreaNavigationSpec.moreEntries.map { it.target }
                    .containsAll(CockpitGlassButtons.secondaryTargets))
                assertEquals("$profile exact restoration", regular,
                    JarvisShellLayout.forContent(width, height, font, workstation = true))
            }
        }
    }

    private fun window(shell: JarvisShellLayout.Shell): AvatarFraming.Window {
        val head = shell.headBounds!!
        val dialog = shell[Slot.DIALOG]
        val keepOut = if (dialog == null || dialog.right <= head.left || dialog.left >= head.right ||
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
        private val profiles = listOf(240f to 500f, 320f to 740f, 360f to 800f, 392f to 850f,
            600f to 800f, 768f to 800f, 904f to 760f, 1280f to 800f)
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
