package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.ui.JarvisShellLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Right-edge clipping of the live head (owner report) and the chin reaching into the dialogue card. Packaged GLB,
 * independent linear-blend skinning ([GlbSkinningRig]) with the renderer's Neck/Head split, and the window the
 * renderer uses ([AvatarFraming]). Host arithmetic; the native Filament image on the Fold7 is a separate step.
 */
class AvatarFramingTest {
    private val rig = GlbSkinningRig.packaged()
    private val focus = AvatarGlbContract.validate(rig.bytes).focus

    /** Chin line of the packaged rig (30 % of the Neck→Head joint distance below the Head joint ≈ 0.2015 m). */
    private val chinY = rig.translation("Neck")[1] + rig.translation("Head")[1] * .70

    /** Every rendered face vertex from the chin line up, for each pose of the full mapper range. */
    private val posedFaces: List<List<DoubleArray>> by lazy {
        val rest = rig.skinned(emptyMap())
        GlbSkinningRig.fullRangePoses().map { (yaw, pitch) ->
            val posed = rig.turn(yaw, pitch, AvatarMorphMapper.NECK_SHARE.toDouble())
            posed.indices.flatMap { m ->
                (0 until posed[m].size / 3).filter { rest[m][it * 3 + 1] >= chinY }
                    .map { doubleArrayOf(posed[m][it * 3], posed[m][it * 3 + 1], posed[m][it * 3 + 2]) }
            }
        }
    }

    private data class Fit(val leftDp: Double, val rightDp: Double, val crownDp: Double, val dialogDp: Double)

    /** Overhangs in stage dp for a window; [dialog] = stage-local (right, top) of the dialogue card in dp. */
    private fun measure(stageW: Double, stageH: Double, left: Double, right: Double, halfHeight: Double,
                        centerY: Double, dialog: Pair<Double, Double>? = null): Fit {
        val dpPerMetre = stageH / (2 * halfHeight)
        var l = 0.0; var r = 0.0; var t = 0.0; var d = 0.0
        for (pose in posedFaces) for (p in pose) {
            val x = p[0] - focus.centerX
            l = maxOf(l, (left - x) * dpPerMetre); r = maxOf(r, (x - right) * dpPerMetre)
            t = maxOf(t, (p[1] - (centerY + halfHeight)) * dpPerMetre)
            if (dialog != null) {
                val sx = (x - left) * dpPerMetre; val sy = (centerY + halfHeight - p[1]) * dpPerMetre
                if (sx < dialog.first && sy > dialog.second) d = maxOf(d, dialog.first - sx)
            }
        }
        assertTrue(stageW > 0)
        return Fit(l, r, t, d)
    }

    private class Stage(val label: String, val box: JarvisShellLayout.Box,
                        val dialog: JarvisShellLayout.Box, val anchorX: Double, val closeUp: Double) {
        constructor(label: String, shell: JarvisShellLayout.Shell, box: JarvisShellLayout.Box) : this(
            label, box, requireNotNull(shell[JarvisShellLayout.Slot.DIALOG]),
            shell.headAnchorX.toDouble(), shell.headCloseUp.toDouble())
        val aspect get() = box.width.toDouble() / box.height
        /** Same normalization and 6 dp gap as ContinuumHomeView.dialogKeepOut. */
        val keepOut get() = AvatarFraming.KeepOut(((dialog.right + 6f - box.left) / box.width).toDouble(),
            ((dialog.top - 6f - box.top) / box.height).toDouble())
        // Compact Cinema puts the three live top cards entirely ABOVE the head and orb.
        // Do not fabricate an intersecting keep-out rectangle for a different screen row.
        // The original Fold overlap is still checked, including the historical regression.
        val dialogLocal: Pair<Double, Double>?
            get() = if (dialog.right <= box.left || dialog.left >= box.right ||
                dialog.bottom <= box.top || dialog.top >= box.bottom) null
            else (dialog.right - box.left).toDouble() to (dialog.top - box.top).toDouble()
    }

    private fun stages() = listOf(884f to 760f, 832f to 748f, 840f to 649f, 960f to 640f, 1280f to 800f, 1536f to 1024f)
        .mapNotNull { (w, h) -> JarvisShellLayout.create(w, h).let { s -> s.headStage?.let { Stage("${w.toInt()}x${h.toInt()}", s, it) } } }

    // Geometry frozen at 88dfd45139471463618673a1f827ee062e9bf6d5.
    private fun formerFoldStage() = Stage("former-884x760",
        JarvisShellLayout.Box(147.33334f, 40.286457f, 460.41666f, 422.5672f),
        JarvisShellLayout.Box(147.33334f, 275.6896f, 310.78125f, 419.6896f),
        .70f.toDouble(), .92f.toDouble())

    private fun Stage.window(keep: Boolean) = AvatarFraming.window(focus, aspect, anchorX,
        closeUp, if (keep) keepOut else null)

    private fun Stage.fit(w: AvatarFraming.Window) = measure(box.width.toDouble(), box.height.toDouble(),
        w.left, w.right, w.halfHeight, w.cameraCenterY, dialogLocal)

    @Test fun formerCentredFitCutTheFold7FaceAndCoveredTheDialogue() {
        // Root cause, recomputed from the former renderer formula (vertical = verticalHalfSpan · closeUp).
        val stage = formerFoldStage()
        val full = focus.verticalHalfSpan(stage.aspect); val v = full * stage.closeUp
        val h = v * stage.aspect; val a = stage.anchorX
        val former = stage.fit(AvatarFraming.Window(-2 * h * a, 2 * h * (1 - a), v, focus.centerY + (full - v), 1.0, a))
        assertTrue("Former Fold7 window must reproduce the right-edge cut: $former", former.rightDp > 20.0)
        assertTrue("Former Fold7 window let the chin enter the dialogue card: $former", former.dialogDp > 20.0)
    }

    /** The rest face (chin line up), exact skinned rest positions. */
    private val restFace: List<DoubleArray> by lazy {
        val rest = rig.skinned(emptyMap())
        rest.indices.flatMap { m ->
            (0 until rest[m].size / 3).filter { rest[m][it * 3 + 1] >= chinY }
                .map { doubleArrayOf(rest[m][it * 3], rest[m][it * 3 + 1], rest[m][it * 3 + 2]) }
        }
    }

    /** How far (dp) the rest face reaches into the dialogue card (stage-local right/top edge in dp). */
    private fun Stage.restDialogDp(w: AvatarFraming.Window): Double {
        val dpPerMetre = box.height / (2 * w.halfHeight)
        // Separate top-card rows cannot collide with the avatar silhouette.
        val (right, top) = dialogLocal ?: return 0.0
        var d = 0.0
        for (p in restFace) {
            val sx = (p[0] - focus.centerX - w.left) * dpPerMetre; val sy = (w.cameraCenterY + w.halfHeight - p[1]) * dpPerMetre
            if (sx < right && sy > top) d = maxOf(d, right - sx)
        }
        return d
    }

    @Test fun cockpitHeadStaysInsideEveryStageInEveryPose() {
        for (stage in stages()) {
            val w = stage.window(keep = true)
            val fit = stage.fit(w)
            assertEquals("${stage.label} left $fit", 0.0, fit.leftDp, 0.0)
            assertEquals("${stage.label} right $fit", 0.0, fit.rightDp, 0.0)
            assertEquals("${stage.label} crown $fit", 0.0, fit.crownDp, 0.0)
            assertTrue("${stage.label} anchor ${w.anchorX}", w.anchorX >= AvatarFraming.MIN_ANCHOR_X && w.anchorX < 1.0)
            assertEquals("${stage.label} resting face clears dialogue", 0.0, stage.restDialogDp(w), 0.0)
        }
    }

    @Test fun turningChinNeverEntersTheDialogueCard() {
        for (stage in stages()) {
            val fit = stage.fit(stage.window(keep = true))
            assertEquals("${stage.label} animated face overlaps dialogue: $fit", 0.0, fit.dialogDp, 0.0)
        }
    }

    @Test fun formerFold7StageKeepsALargerHeadByMovingItBeforeShrinkingIt() {
        val stage = formerFoldStage()
        val sizeOnly = stage.window(keep = false)
        val cockpit = stage.window(keep = true)
        assertTrue("${stage.label} size-only fit ${sizeOnly.headScale}", sizeOnly.headScale < .70)
        assertTrue("${stage.label} cockpit fit ${cockpit.headScale} at ${cockpit.anchorX}", cockpit.headScale > sizeOnly.headScale + .05)
        assertEquals("${stage.label} moving face clears dialogue", 0.0, stage.fit(cockpit).dialogDp, 0.0)
    }

    @Test fun withoutAKeepOutTheHeadNeverMovesSidewaysAndStillFitsAnyAspect() {
        for (aspect in listOf(.45, .6, .82, 1.0, 1.27, 1.6, 2.2)) for ((anchor, closeUp) in listOf(.70 to .92, .5 to 1.0)) {
            val w = AvatarFraming.window(focus, aspect, anchor, closeUp)
            assertEquals(anchor, w.anchorX, 0.0)
            val fit = measure(400.0 * aspect, 400.0, w.left, w.right, w.halfHeight, w.cameraCenterY)
            assertTrue("aspect $aspect anchor $anchor: $fit", fit.leftDp == 0.0 && fit.rightDp == 0.0 && fit.crownDp == 0.0)
        }
    }

    @Test fun unconstrainedRoomyStageKeepsCompositionAndCrownMarginNeverMoves() {
        val stage = stages().first { it.label == "1536x1024" }
        val wide = stage.window(keep = false)
        assertEquals("Unconstrained reference composition stays unchanged", 1.0, wide.headScale, 0.0)
        assertEquals(stage.anchorX, wide.anchorX, 1e-6)
        val fold = stages().first { it.label == "884x760" }
        val f = fold.window(keep = true)
        // The window top (crown margin) is the calibrated one; only the neck side grows.
        assertEquals(focus.centerY + focus.verticalHalfSpan(fold.aspect), f.cameraCenterY + f.halfHeight, 1e-12)
    }

    @Test fun cameraFlightKeepsEveryPosedFaceClearThroughoutThePath() {
        val sizes = listOf(884f to 760f, 840f to 649f, 412f to 915f, 915f to 412f,
            760f to 884f, 1536f to 1024f)
        for ((width, height) in sizes) for (font in listOf(1f, 2f)) {
            val shell = JarvisShellLayout.create(width, height, font)
            val box = shell.headStage ?: shell.headBounds ?: continue
            val stage = Stage("${width}x$height font=$font", shell, box)
            val path = AvatarCameraFraming(focus, stage.aspect, shell.headAnchorX.toDouble(),
                shell.headCloseUp.toDouble(), if (shell.headStage != null) stage.keepOut else null)
            for (direction in listOf(-1f, 0f, 1f)) for (progress in listOf(0f, .1f, .25f, .5f, .75f, .9f, 1f)) {
                val window = path.sample(progress, direction)
                val fit = stage.fit(window)
                assertEquals("${stage.label} p=$progress dir=$direction left", 0.0, fit.leftDp, .000001)
                assertEquals("${stage.label} p=$progress dir=$direction right", 0.0, fit.rightDp, .000001)
                assertEquals("${stage.label} p=$progress crown", 0.0, fit.crownDp, .000001)
                assertEquals("${stage.label} p=$progress dialogue", 0.0, fit.dialogDp, .000001)
                assertEquals(path.rest.cameraCenterY + path.rest.halfHeight,
                    window.cameraCenterY + window.halfHeight, 1e-12)
            }
        }
    }

    @Test fun cameraFlightWithTheExistingIdleDriftClearsTheDialogue() {
        for (stage in stages()) {
            val path = AvatarCameraFraming(focus, stage.aspect, stage.anchorX,
                stage.closeUp, stage.keepOut)
            for (progress in listOf(0f, .25f, .5f, .75f, 1f)) for (direction in listOf(-1f, 1f)) {
                val window = path.sample(progress, direction)
                for (pixels in listOf(-6.5, 6.5)) {
                    val dy = pixels * 2.0 * window.halfHeight / stage.box.height
                    val fit = stage.fit(window.copy(cameraCenterY = window.cameraCenterY - dy))
                    assertEquals("${stage.label} p=$progress drift=$pixels crown", 0.0, fit.crownDp, .000001)
                    assertEquals("${stage.label} p=$progress drift=$pixels dialogue", 0.0, fit.dialogDp, .000001)
                }
            }
        }
    }

    @Test fun fold7CameraPullsBackAndReturnsToTheExactOriginalWindow() {
        val stage = stages().first { it.label == "884x760" }
        val path = AvatarCameraFraming(focus, stage.aspect, stage.anchorX,
            stage.closeUp, stage.keepOut)
        for (direction in listOf(-1f, 0f, 1f)) {
            assertEquals(stage.window(true), path.sample(0f, direction))
            val far = path.sample(1f, direction)
            assertEquals("Eight percent retreat", path.rest.headScale * .92, far.headScale, 1e-10)
            assertTrue(kotlin.math.abs(far.anchorX - path.rest.anchorX) <= .025001)
            assertTrue(far.halfHeight > path.rest.halfHeight)
        }
    }

    @Test fun contractReachAndSilhouetteCoverTheSkinnedFace() {
        assertTrue("reach ${focus.reachRight} must exceed the rest half width", focus.reachRight > .089 && focus.reachLeft > .089)
        assertTrue("reach stays a head, not a shoulder", focus.reachRight < .13 && focus.reachLeft < .13)
        for (pose in posedFaces) for (p in pose) {
            assertTrue("posed x ${p[0]} beyond the contract reach", p[0] - focus.centerX in -focus.reachLeft..focus.reachRight)
        }
        val face = requireNotNull(focus.face)
        for (p in restFace) {
            val band = ((p[1] - face.bottom) / face.step).toInt().coerceIn(0, face.left.size - 1)
            assertTrue("rest silhouette band $band misses x ${p[0]}", p[0] - focus.centerX >= face.left[band])
        }
        for (pose in posedFaces) for (p in pose) {
            val band = ((p[1] - face.bottom) / face.step).toInt().coerceIn(0, face.left.size - 1)
            assertTrue("posed silhouette band $band misses x ${p[0]}", p[0] - focus.centerX >= face.left[band])
        }
    }
}
