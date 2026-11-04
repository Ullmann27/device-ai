package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AvatarLivingPipelineTest {
    private val names = AvatarGlbContract.TARGETS
    private fun weight(map: AvatarMorphMapper, name: String) = map.weights[names.indexOf(name)]
    private fun assetFocus(): AvatarGlbContract.Focus {
        val file = sequenceOf(File("src/main/assets/avatar3d/continuum-avatar.glb"),
            File("app/src/main/assets/avatar3d/continuum-avatar.glb")).first { it.isFile }
        return AvatarGlbContract.validate(file.readBytes()).focus
    }

    @Test fun idleAttachmentReachesTheSmileWithoutOpeningTheMouth() {
        val reducer = AvatarPoseReducer()
        AvatarDirector(clock = { 100L }).attach(AvatarSink(reducer::apply))
        val map = AvatarMorphMapper(names)
        map.update(reducer.pose(100), 0f, true)
        assertTrue("Idle smile must be subtle", weight(map, "Smile") in .05f.. .25f)
        assertEquals(0f, map.jawPitch, 0f)
        for (viseme in Viseme.entries) assertEquals(0f, weight(map, viseme.name), 0f)
    }

    @Test fun activeTasksNeverInheritTheIdleSmile() {
        val reducer = AvatarPoseReducer()
        val director = AvatarDirector(clock = { 100L })
        director.attach(AvatarSink(reducer::apply))
        val map = AvatarMorphMapper(names)
        val tasks = listOf(AvatarRuntimeSignals(listening = true), AvatarRuntimeSignals(busy = true),
            AvatarRuntimeSignals(researching = true), AvatarRuntimeSignals(busy = true, learning = true),
            AvatarRuntimeSignals(awaitingApproval = true), AvatarRuntimeSignals(blocked = true),
            AvatarRuntimeSignals(speaking = true))
        for (task in tasks) {
            director.onRuntime(AvatarRuntimeSignals())
            map.update(reducer.pose(100), 0f, true)
            director.onRuntime(task)
            map.update(reducer.pose(100), .1f, true)
            assertEquals(reducer.pose(100).state.name, 0f, weight(map, "Smile"), 0f)
        }
    }

    @Test fun admittedSpeechSuppressesSmileBeforeRuntimeCatchesUp() {
        val reducer = AvatarPoseReducer()
        val director = AvatarDirector(clock = { 100L })
        director.attach(AvatarSink(reducer::apply))
        val map = AvatarMorphMapper(names)
        map.update(reducer.pose(100), 0f, true)
        assertTrue(director.speechStarted("live", "Hallo"))
        map.update(reducer.pose(100), .1f, true)
        assertEquals(0f, weight(map, "Smile"), 0f)
    }

    @Test fun ownerGazePointsAtThePackagedPortraitCamera() {
        val focus = assetFocus()
        val reducer = AvatarPoseReducer()
        val director = AvatarDirector(clock = { 100L })
        director.attach(AvatarSink(reducer::apply))
        director.onRuntime(AvatarRuntimeSignals(listening = true))
        val map = AvatarMorphMapper(names, focus.azimuthDegrees)
        for (motion in listOf(true, false)) {
            map.update(reducer.pose(100), 0f, motion)
            assertEquals(focus.azimuthDegrees, (map.headYaw + map.eyeYaw).toDouble(), .001)
            assertEquals(0f, map.headPitch + map.eyePitch, .001f)
        }
    }

    @Test fun observedGazeStaysRelativeToThePortraitCamera() {
        val focus = assetFocus()
        var now = 100L
        val reducer = AvatarPoseReducer()
        val director = AvatarDirector(clock = { now })
        director.attach(AvatarSink(reducer::apply))
        val map = AvatarMorphMapper(names, focus.azimuthDegrees)
        for ((x, offset) in listOf(-1f to -25.0, 1f to 25.0)) {
            now += 10
            assertTrue(director.onObservedGaze(GazeTarget(x, 0f), 100, 0))
            map.update(reducer.pose(now), now / 1000f, true)
            assertEquals(focus.azimuthDegrees + offset, (map.headYaw + map.eyeYaw).toDouble(), .001)
        }
    }

    @Test fun negativeZeroClockBoundaryMatchesTheStart() {
        assertCycleStart(-0.0f)
    }

    @Test fun negativeFullClockCyclesMatchTheStart() {
        for (seconds in listOf(-460f, -920f, -4600f)) assertCycleStart(seconds)
    }

    private fun assertCycleStart(seconds: Float) {
        val actual = AvatarIdleMotion().also { it.update(seconds) }
        assertEquals(0f, actual.headYaw, 0f)
        assertEquals(0f, actual.headPitch, 0f)
        assertEquals(0f, actual.eyeYaw, 0f)
        assertEquals(0f, actual.eyePitch, 0f)
        assertEquals(0f, actual.blinkWeight, 0f)
    }
}
