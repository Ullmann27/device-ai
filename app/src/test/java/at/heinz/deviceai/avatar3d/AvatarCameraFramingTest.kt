package at.heinz.deviceai.avatar3d

import org.junit.Assert.*
import org.junit.Test

class AvatarCameraFramingTest {
    @Test fun safeEndpointsCannotHideAnUnsafeCornerCrossing() {
        val focus = AvatarGlbContract.Focus(0.0, 0.0, 0.0, .4, 1.0,
            face = AvatarGlbContract.FaceProfile(-.7776, .05, listOf(-.3132)))
        val keep = AvatarFraming.KeepOut(.55, .8)
        val path = AvatarCameraFraming(focus, 1.0, .7, 1.0, keep)
        assertEquals(.7, path.rest.anchorX, 1e-12)
        val finish = path.sample(1f, -1f)
        assertTrue("The unsafe requested pan is reduced", finish.anchorX > .6751)
        assertTrue("Some pan remains possible", finish.anchorX < .699)
        for (step in 0..1_000) {
            val window = path.sample(step / 1_000f, -1f)
            val x = (-.3132 - window.left) / (window.right - window.left)
            val y = (window.cameraCenterY + window.halfHeight + .7776) / (2.0 * window.halfHeight)
            assertFalse("Corner crossed at $step: $x,$y", x < .55 - 1e-9 && y > .8 + 1e-9)
        }
    }

    @Test fun roomyStagePermitsTheBoundedPan() {
        val focus = AvatarGlbContract.Focus(0.0, 0.0, 0.0, .2, .5)
        val path = AvatarCameraFraming(focus, 2.0, .5, 1.0)
        assertEquals(.475, path.sample(1f, -1f).anchorX, 1e-12)
        assertEquals(.525, path.sample(1f, 1f).anchorX, 1e-12)
        assertSame(path.rest, path.sample(0f, 1f))
    }

    @Test fun airbusProfilesPullBackAndMoveHeadAwayFromRightTerminal() {
        val focus = AvatarGlbContract.Focus(0.0, 0.0, 0.0, .2, .5)
        val medium = AvatarCameraFraming(focus, 2.0, .7, 1.0, profile =
            AvatarCameraFraming.Profile.MEDIUM_TERMINAL)
        val m = medium.sample(1f, 1f)
        assertEquals(.34, m.anchorX, 1e-6)
        assertEquals(medium.rest.headScale * .76, m.headScale, 1e-6)

        val full = AvatarCameraFraming(focus, 2.0, .7, 1.0, profile =
            AvatarCameraFraming.Profile.FULL_MISSION_BAY)
        val f = full.sample(1f, 1f)
        assertEquals(.28, f.anchorX, 1e-6)
        assertEquals(full.rest.headScale * .64, f.headScale, 1e-6)
        assertSame(full.rest, full.sample(0f, 1f))
    }

    @Test fun airbusRequestedPanStillYieldsToPoseAndDialogueSafety() {
        val focus = AvatarGlbContract.Focus(0.0, 0.0, 0.0, .4, 1.0,
            face = AvatarGlbContract.FaceProfile(-.7776, .05, listOf(-.3132)))
        val keep = AvatarFraming.KeepOut(.55, .8)
        val path = AvatarCameraFraming(focus, 1.0, .7, 1.0, keep,
            AvatarCameraFraming.Profile.MEDIUM_TERMINAL)
        val finish = path.sample(1f, 1f)
        assertTrue("unsafe Airbus target is bounded", finish.anchorX > .34)
        for (step in 0..1_000) {
            val window = path.sample(step / 1_000f, 1f)
            val x = (-.3132 - window.left) / (window.right - window.left)
            val y = (window.cameraCenterY + window.halfHeight + .7776) / (2.0 * window.halfHeight)
            assertFalse("Airbus path crossed keepout at $step: $x,$y",
                x < .55 - 1e-9 && y > .8 + 1e-9)
        }
    }

    @Test fun unreachableKeepOutCannotIntroduceCameraTravel() {
        val focus = AvatarGlbContract.Focus(0.0, 0.0, 0.0, .2, .5,
            face = AvatarGlbContract.FaceProfile(-.2, .1, listOf(-.2)))
        val path = AvatarCameraFraming(focus, 1.0, .5, 1.0, AvatarFraming.KeepOut(2.0, -1.0))
        for (direction in listOf(-1f, 0f, 1f)) {
            assertSame(path.rest, path.sample(1f, direction))
        }
    }

    @Test fun invalidProgressAndDirectionAreRejected() {
        val path = AvatarCameraFraming(AvatarGlbContract.Focus(0.0, 0.0, 0.0, .2, .5), 1.0, .5, 1.0)
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 2f)) {
            assertThrows(IllegalArgumentException::class.java) { path.sample(value, 0f) }
        }
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -2f, 2f)) {
            assertThrows(IllegalArgumentException::class.java) { path.sample(.5f, value) }
        }
    }
}
